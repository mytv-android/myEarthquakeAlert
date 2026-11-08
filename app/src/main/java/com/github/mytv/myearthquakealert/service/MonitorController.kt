package com.github.mytv.myearthquakealert.service

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.SystemClock
import android.service.quicksettings.TileService
import android.util.Log
import androidx.core.content.ContextCompat
import com.github.mytv.myearthquakealert.MyEarthQuakeAlertApp
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.data.model.EewEvent
import com.github.mytv.myearthquakealert.data.model.UserLocation
import com.github.mytv.myearthquakealert.data.repository.UserSettings
import com.github.mytv.myearthquakealert.data.source.EewSource
import com.github.mytv.myearthquakealert.diagnostics.ClockSync
import com.github.mytv.myearthquakealert.diagnostics.Diagnostics
import com.github.mytv.myearthquakealert.diagnostics.HealthLevel
import com.github.mytv.myearthquakealert.diagnostics.HealthRecorder
import com.github.mytv.myearthquakealert.diagnostics.LogTags
import com.github.mytv.myearthquakealert.diagnostics.SelfTestRunner
import com.github.mytv.myearthquakealert.diagnostics.SlotStatus
import com.github.mytv.myearthquakealert.domain.DrillScenario
import com.github.mytv.myearthquakealert.domain.ReportTime
import com.github.mytv.myearthquakealert.domain.SeismicCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The monitoring pipeline inside [EewMonitorService] (design §3 服务状态机 / 保活与恢复机制):
 *
 * - one [SourceLink] per subscribed source, all at once; reports are de-duplicated by event
 *   id across sources ([ReportDeduper]) and handed to the [AlertPresenter];
 * - the phase shown everywhere, derived by [MonitorRules.derivePhase] from the links, the
 *   pause and the delivery requirements ([DeliveryChecks], refreshed at start, every minute
 *   and when the screen turns on), published in [MonitorStatus];
 * - network changes (default-network callback) reconnect at once instead of waiting for the
 *   heartbeat timeout; the location is refreshed every 15 min (manual or automatic); the clock
 *   offset every 30 min; health metrics every 5 s;
 * - pause / resume, drills and the self test.
 *
 * All state is confined to the service's main-thread [scope].
 */
internal class MonitorController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val host: Host,
) : SourceLink.Listener {

    interface Host {
        /** The published state changed (ongoing notification). */
        fun onStateChanged(state: MonitorState)

        /** See [AlertPresenter.Host.holdForeground]. */
        fun holdForeground(notificationId: Int, notification: Notification): Boolean

        /** The alert no longer needs the foreground it took through [holdForeground]. */
        fun releaseAlertForeground()

        /** The pause end changed (0 = not paused): (re)arm or cancel the resume alarm. */
        fun onPauseChanged(until: Long)

        /** Monitoring is off and no alert, drill or self test is running: the service may stop. */
        fun onIdle()
    }

    private val app = context.applicationContext as MyEarthQuakeAlertApp

    /** Latest settings; defaults until the first emission. */
    var settings: UserSettings = UserSettings()
        private set
    private var settingsLoaded = false

    /** Monitoring is on (also while paused). */
    var running = false
        private set
    private var runningSince: Long? = null
    private var startOrigin: StartOrigin? = null
    private var pauseUntil = 0L

    private val links = LinkedHashMap<EewSource, SourceLink>()
    private val deduper = ReportDeduper()
    private var latestReport: EewEvent? = null
    private var location: UserLocation? = null

    private var lastPhase: MonitorPhase? = null
    private var lastLink: SlotStatus? = null

    private val runJobs = ArrayList<Job>()
    private var pauseJob: Job? = null
    private var drillJob: Job? = null
    private var selfTestJob: Job? = null

    private val presenter = AlertPresenter(context, scope, object : AlertPresenter.Host {
        override fun settings(): UserSettings = settings
        override fun holdForeground(notificationId: Int, notification: Notification): Boolean =
            host.holdForeground(notificationId, notification)

        override fun onAlertIdle() {
            host.releaseAlertForeground()
            checkIdle()
        }
    })

    init {
        scope.launch { app.settingsRepository.settings.collect { onSettings(it) } }
        // UIs refresh the delivery checks from onResume; re-derive the phase when they change.
        scope.launch {
            MonitorStatus.state.map { it.missing }.distinctUntilChanged().collect { if (running) recompute() }
        }
        scope.launch {
            while (isActive) {
                publishHealth()
                delay(PUBLISH_MS)
            }
        }
    }

    // ── Commands ────────────────────────────────────────────────────────────

    /**
     * Turns monitoring on. [userInitiated] (switch, tile, notification, watch) also ends a
     * pause; automatic starts (boot, update, watchdog, sticky restart) keep it. [pauseUntil]
     * starts paused until then (a pause requested while the service was not running).
     */
    suspend fun start(origin: StartOrigin, userInitiated: Boolean, pauseUntil: Long? = null) {
        val s = currentSettings()
        if (running) {
            when {
                pauseUntil != null -> pause(pauseUntil)
                userInitiated && this.pauseUntil != 0L -> resume(origin)
            }
            return
        }
        running = true
        runningSince = System.currentTimeMillis()
        startOrigin = origin
        deduper.clear()
        Diagnostics.log(HealthLevel.INFO, LogTags.START, startMessage(origin))
        HealthRecorder.recordStart(origin.name)
        if (origin == StartOrigin.PAUSE_ENDED) AppNotifications.showPauseEnded(context)

        DeliveryChecks.refresh(context)
        startRunJobs()
        registerNetworkCallback()
        registerScreenReceiver()

        val now = System.currentTimeMillis()
        val until = pauseUntil ?: if (userInitiated) 0L else s.pauseUntil
        if (until > now) {
            if (pauseUntil != null) persistPauseUntil(until)
            applyPause(until, announce = true)
        } else {
            if (s.pauseUntil != 0L) persistPauseUntil(0L)
            host.onPauseChanged(0L)
            syncLinks()
        }
        recompute()
    }

    /** Turns monitoring off ([reason] goes to the log). */
    fun stop(reason: String) {
        if (!running) return
        running = false
        stopLinks()
        runJobs.forEach { it.cancel() }
        runJobs.clear()
        pauseJob?.cancel()
        pauseJob = null
        pauseUntil = 0L
        host.onPauseChanged(0L)
        unregisterNetworkCallback()
        unregisterScreenReceiver()
        Diagnostics.log(HealthLevel.INFO, LogTags.STOP, reason)
        HealthRecorder.recordStop(reason)
        runningSince = null
        startOrigin = null
        recompute()
        HealthRecorder.flushSnapshot()
        checkIdle()
    }

    /** Pauses until [until] (epoch millis); persisted, so it survives reboots and updates. */
    fun pause(until: Long) {
        if (!running) return
        if (until <= System.currentTimeMillis()) {
            resume(StartOrigin.USER)
            return
        }
        persistPauseUntil(until)
        applyPause(until, announce = true)
        recompute()
    }

    /** Ends the pause ([origin] PAUSE_ENDED when it ran out, which posts a silent notice). */
    fun resume(origin: StartOrigin) {
        if (!running || pauseUntil == 0L) return
        pauseUntil = 0L
        pauseJob?.cancel()
        pauseJob = null
        persistPauseUntil(0L)
        host.onPauseChanged(0L)
        Diagnostics.log(HealthLevel.OK, LogTags.RESUME, context.getString(R.string.svc_log_resume, origin.name, originLabel(origin)))
        if (origin == StartOrigin.PAUSE_ENDED) AppNotifications.showPauseEnded(context)
        syncLinks()
        recompute()
    }

    /** Replaces every socket now (watchdog found the heartbeat stalled). */
    fun reconnect(reason: String) {
        if (!running || pauseUntil != 0L) return
        Diagnostics.log(HealthLevel.WARN, LogTags.WATCHDOG, reason)
        links.values.forEach { it.reconnectNow(force = true, reason = reason) }
    }

    /**
     * Plays [scenario] through the presenter (演练): each scripted report at its time, on the
     * user's location (a stand-in when none is known yet).
     */
    fun drill(scenario: DrillScenario.Scenario) {
        drillJob?.cancel()
        val job = scope.launch {
            val resolved = currentLocation()
            val loc = if (resolved.isKnown) resolved else DRILL_STAND_IN
            val hypocenter = context.getString(
                when (scenario) {
                    DrillScenario.Scenario.LUSHAN -> R.string.svc_drill_hypocenter_lushan
                    DrillScenario.Scenario.NEAR -> R.string.svc_drill_hypocenter_near
                    DrillScenario.Scenario.FAR -> R.string.svc_drill_hypocenter_far
                }
            )
            val script = DrillScenario.script(
                scenario, loc.latitude, loc.longitude, settings.primarySource.name, hypocenter, ClockSync.now(),
            )
            val tier = if (scenario == DrillScenario.Scenario.FAR) AlertTier.REMIND else AlertTier.STRONG
            Diagnostics.log(HealthLevel.INFO, LogTags.DRILL, context.getString(R.string.svc_log_drill, scenario.id, hypocenter))
            val started = SystemClock.elapsedRealtime()
            for (report in script.reports) {
                val wait = report.delayMs - (SystemClock.elapsedRealtime() - started)
                if (wait > 0) delay(wait)
                presenter.present(report.event, loc, script.originAt, drillTier = tier)
            }
        }
        drillJob = job
        job.invokeOnCompletion { scope.launch { checkIdle() } }
    }

    /** Runs the 8-step self test; progress is published in [Diagnostics.selfTest]. */
    fun selfTest() {
        if (selfTestJob?.isActive == true) return
        val job = scope.launch {
            val s = currentSettings()
            SelfTestRunner(context, app).run(s, running = running && pauseUntil == 0L)
        }
        selfTestJob = job
        job.invokeOnCompletion { scope.launch { checkIdle() } }
    }

    fun muteAlert() = ActiveAlertHolder.mute()

    fun dismissAlert() = presenter.dismiss()

    /** Service shutdown: stops everything and publishes OFF. */
    fun release(reason: String) {
        drillJob?.cancel()
        selfTestJob?.cancel()
        stop(reason)
        presenter.release()
        publishHealth()
    }

    /** True while something besides monitoring keeps the service busy. */
    val busy: Boolean
        get() = drillJob?.isActive == true || selfTestJob?.isActive == true || presenter.hasAlert

    // ── Links and reports ───────────────────────────────────────────────────

    override fun onLinkChanged(link: SourceLink) {
        if (links[link.source] === link) recompute()
    }

    override fun onReport(link: SourceLink, event: EewEvent, latencyMs: Long?, fresh: Boolean, parseMs: Long, viaHttp: Boolean) {
        val receivedAt = System.currentTimeMillis()
        val verdict = deduper.accept(event, receivedAt)
        if (!fresh) {
            // History (e.g. the first HTTP fetch): fills 最近报文, never alerts.
            updateLatest(event)
            recompute()
            return
        }
        if (verdict == ReportDeduper.Verdict.DUPLICATE) return
        HealthRecorder.recordReport(link.source.name, latencyMs)
        val latency = latencyMs?.let { String.format(Locale.US, "%.1f s", it / 1000.0) } ?: "—"
        Diagnostics.log(
            HealthLevel.INFO, LogTags.REPORT,
            context.getString(
                if (event.isFinal) R.string.svc_log_report_final else R.string.svc_log_report,
                link.source.typeKey, event.reportNum, event.hypocenter, event.magnitude, latency,
            ) + if (viaHttp) " · HTTP" else "",
        )
        updateLatest(event)
        recompute()
        scope.launch { evaluate(event, parseMs, receivedAt) }
    }

    private suspend fun evaluate(event: EewEvent, parseMs: Long, receivedAt: Long) {
        val loc = currentLocation()
        if (!loc.isKnown) {
            Diagnostics.log(HealthLevel.ERROR, LogTags.ALERT, context.getString(R.string.svc_log_no_location, event.hypocenter, event.magnitude))
            return
        }
        val outcome = presenter.present(event, loc, ReportTime.parseCst(event.originTime))
        HealthRecorder.recordPipeline(parseMs, outcome.evaluateMs, System.currentTimeMillis() - receivedAt)
    }

    private fun updateLatest(event: EewEvent) {
        val current = latestReport
        if (current == null || current.eventId == event.eventId ||
            (ReportTime.parseCst(event.reportTime) ?: 0L) >= (ReportTime.parseCst(current.reportTime) ?: 0L)
        ) {
            if (current == null || current.eventId != event.eventId || event.reportNum >= current.reportNum) latestReport = event
        }
    }

    private fun syncLinks() {
        val wanted = if (running && pauseUntil == 0L) settings.enabledSources else emptySet()
        val removed = links.keys.filter { it !in wanted }
        removed.forEach { links.remove(it)?.stop() }
        EewSource.entries.filter { it in wanted && it !in links }.forEach { source ->
            val link = SourceLink(source, context, scope, this)
            links[source] = link
            link.start()
        }
        // Keep enum order (stable rows in 诊断 › 数据链路).
        val ordered = EewSource.entries.mapNotNull { s -> links[s]?.let { s to it } }
        links.clear()
        ordered.forEach { (s, l) -> links[s] = l }
    }

    private fun stopLinks() {
        val all = links.values.toList()
        links.clear()
        all.forEach { it.stop() }
    }

    // ── Pause ───────────────────────────────────────────────────────────────

    private fun applyPause(until: Long, announce: Boolean) {
        pauseUntil = until
        stopLinks()
        pauseJob?.cancel()
        // In-process timer for the awake case; the service's alarm covers Doze and process death.
        pauseJob = scope.launch {
            delay((until - System.currentTimeMillis()).coerceAtLeast(0L))
            resume(StartOrigin.PAUSE_ENDED)
        }
        host.onPauseChanged(until)
        if (announce) {
            Diagnostics.log(HealthLevel.WARN, LogTags.PAUSE, context.getString(R.string.svc_log_pause, formatClock(until)))
        }
    }

    private fun persistPauseUntil(until: Long) {
        app.appScope.launch { app.settingsRepository.updatePauseUntil(until) }
    }

    // ── Settings ────────────────────────────────────────────────────────────

    private suspend fun currentSettings(): UserSettings {
        if (!settingsLoaded) {
            settings = try {
                app.settingsRepository.settings.first()
            } catch (e: Exception) {
                Log.w(TAG, "settings unreadable", e)
                settings
            }
            settingsLoaded = true
        }
        return settings
    }

    private fun onSettings(new: UserSettings) {
        val old = settings
        val first = !settingsLoaded
        settings = new
        settingsLoaded = true
        if (!running || first) return
        if (old.serviceEnabled && !new.serviceEnabled) {
            // Switched off by a writer that did not stop the service (e.g. another surface).
            stop(context.getString(R.string.svc_stop_settings))
            return
        }
        // Only a change of the stored value counts, never an in-memory pause not yet persisted.
        if (old.pauseUntil != new.pauseUntil && new.pauseUntil != pauseUntil) {
            if (new.isPaused()) {
                applyPause(new.pauseUntil, announce = true)
            } else if (pauseUntil != 0L) {
                resume(StartOrigin.USER)
            }
        }
        if (old.enabledSources != new.enabledSources) {
            Diagnostics.log(HealthLevel.INFO, LogTags.SYSTEM, context.getString(R.string.svc_log_sources, new.enabledSources.joinToString { it.typeKey }))
            syncLinks()
        }
        if (old.locationMode != new.locationMode || old.manualLatitude != new.manualLatitude ||
            old.manualLongitude != new.manualLongitude || old.manualLocationName != new.manualLocationName
        ) {
            scope.launch { refreshLocation(maxFixAgeMs = 0L) }
        }
        recompute()
    }

    // ── Phase ───────────────────────────────────────────────────────────────

    private fun recompute() {
        val now = System.currentTimeMillis()
        val paused = running && pauseUntil > now
        val sources = links.values.map { it.health }
        val missing = MonitorStatus.state.value.missing
        val phase = MonitorRules.derivePhase(running, paused, sources, missing)
        val link = MonitorRules.linkStatus(running, paused, sources)
        MonitorStatus.update { st ->
            st.copy(
                phase = phase,
                phaseSince = if (st.phase != phase || st.phaseSince == 0L) now else st.phaseSince,
                runningSince = runningSince,
                pauseUntil = if (paused) pauseUntil else 0L,
                sources = sources,
                latestReport = latestReport ?: st.latestReport,
                startOrigin = startOrigin,
                detectorProcessStartedAt = app.processStartedAt,
            )
        }
        if (phase != lastPhase) {
            lastPhase = phase
            HealthRecorder.recordPhase(phase)
            requestTileUpdate()
        }
        if (link != lastLink) {
            lastLink = link
            HealthRecorder.recordLink(link)
        }
        host.onStateChanged(MonitorStatus.state.value)
    }

    private fun checkIdle() {
        if (!running && !busy) host.onIdle()
    }

    private fun publishHealth() {
        HealthRecorder.publish(context, if (running) MonitorStatus.state.value else null, location)
    }

    private fun requestTileUpdate() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        try {
            TileService.requestListeningState(context, ComponentName(context, MonitorTileService::class.java))
        } catch (e: Exception) {
            Log.w(TAG, "tile update: ${e.message}")
        }
    }

    // ── Periodic work while running ─────────────────────────────────────────

    private fun startRunJobs() {
        runJobs += scope.launch {
            while (isActive) {
                val sample = withContext(Dispatchers.IO) { ClockSync.sync(context, app.wolfxApi) }
                delay(if (sample != null) ClockSync.INTERVAL_MS else CLOCK_RETRY_MS)
            }
        }
        runJobs += scope.launch {
            while (isActive) {
                refreshLocation(maxFixAgeMs = LOCATION_MAX_AGE_MS)
                delay(LOCATION_REFRESH_MS)
            }
        }
        runJobs += scope.launch {
            while (isActive) {
                delay(CHECKS_MS)
                DeliveryChecks.refresh(context)
            }
        }
    }

    private suspend fun currentLocation(): UserLocation {
        location?.let { if (it.isKnown) return it }
        return refreshLocation(maxFixAgeMs = null)
    }

    private suspend fun refreshLocation(maxFixAgeMs: Long?): UserLocation {
        val s = currentSettings()
        val resolved = try {
            withContext(Dispatchers.IO) { app.locationProvider.resolve(s, maxFixAgeMs) }
        } catch (e: Exception) {
            Log.w(TAG, "location failed", e)
            location ?: UserLocation(0.0, 0.0, UserLocation.SOURCE_NONE)
        }
        val previous = location
        location = resolved
        val moved = previous == null || previous.source != resolved.source ||
            SeismicCalculator.haversineDistance(previous.latitude, previous.longitude, resolved.latitude, resolved.longitude) > 1.0
        if (moved) {
            val accuracy = resolved.accuracyM?.let { " ±${it.toInt()} m" } ?: ""
            val label = if (resolved.source == UserLocation.SOURCE_MANUAL && resolved.name.isNotBlank()) " ${resolved.name}" else ""
            Diagnostics.log(
                if (resolved.isKnown) HealthLevel.INFO else HealthLevel.WARN, LogTags.LOCATION,
                context.getString(R.string.svc_log_location, resolved.source + label + accuracy),
            )
        }
        return resolved
    }

    // ── Network ─────────────────────────────────────────────────────────────

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var currentNetwork: Network? = null
    private var networkSeen = false

    private fun registerNetworkCallback() {
        if (networkCallback != null) return
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                scope.launch { onNetworkAvailable(cm, network) }
            }

            override fun onLost(network: Network) {
                scope.launch { onNetworkLost(network) }
            }
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                cm.registerDefaultNetworkCallback(callback)
            } else {
                cm.registerNetworkCallback(
                    NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),
                    callback,
                )
            }
            networkCallback = callback
        } catch (e: Exception) {
            Log.w(TAG, "network callback unavailable", e)
        }
    }

    private fun unregisterNetworkCallback() {
        val callback = networkCallback ?: return
        networkCallback = null
        currentNetwork = null
        networkSeen = false
        try {
            context.getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(callback)
        } catch (_: Exception) {
        }
    }

    private fun onNetworkAvailable(cm: ConnectivityManager, network: Network) {
        if (networkCallback == null) return
        // API 23 reports every internet network; only the default one matters.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N && cm.activeNetwork != network) return
        val previous = currentNetwork
        currentNetwork = network
        val initial = !networkSeen
        networkSeen = true
        when {
            initial -> links.values.forEach { it.reconnectNow(force = false, reason = "") }
            previous == null -> {
                Diagnostics.log(HealthLevel.OK, LogTags.NETWORK, context.getString(R.string.svc_log_network_back))
                links.values.forEach { it.reconnectNow(force = false, reason = "") }
            }
            previous != network -> {
                val reason = context.getString(R.string.svc_reason_network_changed)
                Diagnostics.log(HealthLevel.INFO, LogTags.NETWORK, context.getString(R.string.svc_log_network_changed))
                links.values.forEach { it.reconnectNow(force = true, reason = reason) }
            }
        }
    }

    private fun onNetworkLost(network: Network) {
        if (networkCallback == null || network != currentNetwork) return
        currentNetwork = null
        val reason = context.getString(R.string.svc_reason_network_lost)
        Diagnostics.log(HealthLevel.WARN, LogTags.NETWORK, context.getString(R.string.svc_log_network_lost))
        links.values.forEach { it.onNetworkLost(reason) }
    }

    // ── Screen on ───────────────────────────────────────────────────────────

    private var screenReceiver: BroadcastReceiver? = null

    private fun registerScreenReceiver() {
        if (screenReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.action != Intent.ACTION_SCREEN_ON) return
                DeliveryChecks.refresh(context)
                links.values.filter { !it.isConnected }.forEach { it.reconnectNow(force = false, reason = "") }
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(Intent.ACTION_SCREEN_ON), ContextCompat.RECEIVER_NOT_EXPORTED)
        screenReceiver = receiver
    }

    private fun unregisterScreenReceiver() {
        val receiver = screenReceiver ?: return
        screenReceiver = null
        try {
            context.unregisterReceiver(receiver)
        } catch (_: Exception) {
        }
    }

    // ── Text ────────────────────────────────────────────────────────────────

    /** START log line: begins with the origin name (设置 › 最近启动 filters on it). */
    private fun startMessage(origin: StartOrigin): String {
        val label = originLabel(origin)
        val bootDelay = if (origin == StartOrigin.BOOT) BootStartRecord.delaySecondsThisBoot(context) else null
        return if (bootDelay != null) {
            context.getString(R.string.svc_log_start_boot, origin.name, label, bootDelay)
        } else {
            context.getString(R.string.svc_log_start, origin.name, label)
        }
    }

    private fun originLabel(origin: StartOrigin): String = context.getString(originLabelRes(origin))

    private fun formatClock(time: Long): String = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(time))

    companion object {
        private const val TAG = "MonitorController"
        private const val PUBLISH_MS = 5_000L
        private const val CHECKS_MS = 60_000L
        private const val CLOCK_RETRY_MS = 2 * 60_000L
        private const val LOCATION_REFRESH_MS = 15 * 60_000L
        private const val LOCATION_MAX_AGE_MS = 30 * 60_000L

        /** Used by drills when no location is known yet: 成都. */
        private val DRILL_STAND_IN = UserLocation(30.66, 104.07, UserLocation.SOURCE_MANUAL)

        /** Short label of a start origin (also used by the settings page through the log). */
        fun originLabelRes(origin: StartOrigin): Int = when (origin) {
            StartOrigin.USER -> R.string.svc_origin_user
            StartOrigin.BOOT -> R.string.svc_origin_boot
            StartOrigin.PACKAGE_REPLACED -> R.string.svc_origin_package_replaced
            StartOrigin.WATCHDOG -> R.string.svc_origin_watchdog
            StartOrigin.STICKY_RESTART -> R.string.svc_origin_sticky_restart
            StartOrigin.PAUSE_ENDED -> R.string.svc_origin_pause_ended
            StartOrigin.TILE -> R.string.svc_origin_tile
            StartOrigin.NOTIFICATION -> R.string.svc_origin_notification
            StartOrigin.WATCH -> R.string.svc_origin_watch
        }
    }
}
