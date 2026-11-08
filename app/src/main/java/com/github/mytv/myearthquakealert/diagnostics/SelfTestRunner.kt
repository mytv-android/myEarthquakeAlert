package com.github.mytv.myearthquakealert.diagnostics

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.github.mytv.myearthquakealert.MyEarthQuakeAlertApp
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.data.model.UserLocation
import com.github.mytv.myearthquakealert.data.repository.UserSettings
import com.github.mytv.myearthquakealert.data.source.EewFrame
import com.github.mytv.myearthquakealert.data.source.EewParser
import com.github.mytv.myearthquakealert.data.source.EewSource
import com.github.mytv.myearthquakealert.data.websocket.EewWebSocketClient
import com.github.mytv.myearthquakealert.domain.AlertEvaluator
import com.github.mytv.myearthquakealert.domain.ReportTime
import com.github.mytv.myearthquakealert.domain.SeismicCalculator
import com.github.mytv.myearthquakealert.service.AppNotifications
import com.github.mytv.myearthquakealert.service.DeliveryChecks
import com.github.mytv.myearthquakealert.service.DeliveryRequirement
import com.github.mytv.myearthquakealert.service.LinkState
import com.github.mytv.myearthquakealert.service.MonitorStatus
import com.github.mytv.myearthquakealert.util.AutoStartSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 端到端自检 (design §5): the eight [SelfTestStep]s in order, each with a real check, a
 * detail line and, when it fails, the [FixAction] that resolves it. Progress is published
 * step by step in [Diagnostics.selfTest]. Works with monitoring on (reads the live links)
 * or off (probes each source with a short-lived socket). Nothing audible: the presentation
 * step posts and withdraws a silent notification.
 */
class SelfTestRunner(private val context: Context, private val app: MyEarthQuakeAlertApp) {

    private data class Check(val status: StepStatus, val detail: String, val fix: FixAction? = null)

    private var location: UserLocation? = null

    /** Runs all steps; [running] = monitoring is on and not paused. */
    suspend fun run(settings: UserSettings, running: Boolean): SelfTestRun {
        var run = SelfTestRun(startedAt = System.currentTimeMillis())
        Diagnostics.setSelfTest(run)
        Diagnostics.log(HealthLevel.INFO, LogTags.SELF_TEST, context.getString(R.string.svc_st_started))
        for (step in SelfTestStep.entries) {
            run = run.with(SelfTestStepResult(step, StepStatus.RUNNING))
            Diagnostics.setSelfTest(run)
            val check = try {
                withTimeout(STEP_TIMEOUT_MS) { check(step, settings, running) }
            } catch (e: TimeoutCancellationException) {
                Check(StepStatus.FAIL, context.getString(R.string.svc_st_timeout, STEP_TIMEOUT_MS / 1000))
            } catch (e: CancellationException) {
                Diagnostics.setSelfTest(run.copy(finishedAt = System.currentTimeMillis()))
                throw e
            } catch (e: Exception) {
                Check(StepStatus.FAIL, e.message ?: e.javaClass.simpleName)
            }
            run = run.with(SelfTestStepResult(step, check.status, check.detail, check.fix))
            Diagnostics.setSelfTest(run)
        }
        run = run.copy(finishedAt = System.currentTimeMillis())
        Diagnostics.setSelfTest(run)
        val pass = run.steps.count { it.status == StepStatus.PASS }
        val warn = run.steps.count { it.status == StepStatus.WARN }
        val fail = run.steps.count { it.status == StepStatus.FAIL }
        Diagnostics.log(
            when {
                fail > 0 -> HealthLevel.ERROR
                warn > 0 -> HealthLevel.WARN
                else -> HealthLevel.OK
            },
            LogTags.SELF_TEST, context.getString(R.string.svc_st_finished, pass, warn, fail),
        )
        return run
    }

    private fun SelfTestRun.with(result: SelfTestStepResult) =
        copy(steps = steps.map { if (it.step == result.step) result else it })

    private suspend fun check(step: SelfTestStep, settings: UserSettings, running: Boolean): Check = when (step) {
        SelfTestStep.NETWORK -> network()
        SelfTestStep.SOURCES -> sources(settings, running)
        SelfTestStep.CLOCK -> clock()
        SelfTestStep.LOCATION -> location(settings)
        SelfTestStep.PERMISSIONS -> permissions()
        SelfTestStep.SYSTEM_LIMITS -> systemLimits()
        SelfTestStep.PIPELINE -> pipeline(settings)
        SelfTestStep.PRESENTATION -> presentation()
    }

    // ── 1 网络可达 ───────────────────────────────────────────────────────────

    private suspend fun network(): Check {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val network = cm?.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }
        if (caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return Check(StepStatus.FAIL, context.getString(R.string.svc_st_network_none), FixAction.NETWORK_SETTINGS)
        }
        val transport = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> context.getString(R.string.svc_st_transport_ethernet)
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> context.getString(R.string.svc_st_transport_cellular)
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> context.getString(R.string.svc_st_transport_other)
        }
        val t0 = SystemClock.elapsedRealtime()
        val reachable = try {
            withContext(Dispatchers.IO) { app.wolfxApi.getNtp() }
            true
        } catch (e: Exception) {
            false
        }
        val rtt = SystemClock.elapsedRealtime() - t0
        if (!reachable) {
            return Check(StepStatus.FAIL, context.getString(R.string.svc_st_network_unreachable, transport), FixAction.NETWORK_SETTINGS)
        }
        if (HealthRecorder.dataSaverOn(context) == true) {
            return Check(StepStatus.WARN, context.getString(R.string.svc_st_network_data_saver, transport, rtt), FixAction.NETWORK_SETTINGS)
        }
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
            return Check(StepStatus.WARN, context.getString(R.string.svc_st_network_unvalidated, transport, rtt))
        }
        return Check(StepStatus.PASS, context.getString(R.string.svc_st_network_ok, transport, rtt))
    }

    // ── 2 数据源连接 ─────────────────────────────────────────────────────────

    private suspend fun sources(settings: UserSettings, running: Boolean): Check {
        val wanted = EewSource.entries.filter { it in settings.enabledSources }
        val sep = context.getString(R.string.svc_list_separator)
        if (running) {
            val live = MonitorStatus.state.value.sources
            val connected = live.filter { it.link == LinkState.CONNECTED }
            val polling = live.filter { it.link == LinkState.POLLING }
            val detail = live.joinToString(sep) { "${it.source.typeKey} ${linkLabel(it.link)}" }
            return when {
                connected.size == wanted.size && wanted.isNotEmpty() -> Check(StepStatus.PASS, detail)
                connected.isNotEmpty() || polling.isNotEmpty() -> Check(StepStatus.WARN, detail)
                else -> Check(StepStatus.FAIL, detail.ifEmpty { context.getString(R.string.svc_st_sources_none) }, FixAction.NETWORK_SETTINGS)
            }
        }
        val results = coroutineScope { wanted.map { source -> async { source to probe(source) } }.awaitAll() }
        val ok = results.count { it.second != null }
        val detail = results.joinToString(sep) { (source, ms) ->
            if (ms != null) "${source.typeKey} $ms ms" else "${source.typeKey} ${context.getString(R.string.svc_st_failed)}"
        }
        return when {
            ok == results.size && ok > 0 -> Check(StepStatus.PASS, detail)
            ok > 0 -> Check(StepStatus.WARN, detail)
            else -> Check(StepStatus.FAIL, detail, FixAction.NETWORK_SETTINGS)
        }
    }

    private fun linkLabel(link: LinkState): String = context.getString(
        when (link) {
            LinkState.CONNECTED -> R.string.svc_link_connected
            LinkState.POLLING -> R.string.svc_link_polling
            LinkState.RECONNECTING -> R.string.svc_link_reconnecting
            LinkState.CONNECTING -> R.string.svc_link_connecting
            LinkState.DISCONNECTED -> R.string.svc_link_disconnected
        }
    )

    /** Opens a socket to [source] and waits for the first frame (the pong to our ping); handshake ms or null. */
    private suspend fun probe(source: EewSource): Long? {
        val client = EewWebSocketClient()
        return try {
            client.connect(source)
            withTimeoutOrNull(PROBE_TIMEOUT_MS) {
                var handshake: Long? = null
                var result: Long? = null
                var done = false
                while (!done) {
                    when (val event = client.events.receive()) {
                        is EewWebSocketClient.Event.Opened -> {
                            handshake = event.handshakeMs
                            client.send("ping")
                        }
                        is EewWebSocketClient.Event.Message -> {
                            result = handshake ?: 0L
                            done = true
                        }
                        is EewWebSocketClient.Event.Closed, is EewWebSocketClient.Event.Failure -> done = true
                    }
                }
                result
            }
        } finally {
            client.disconnect(1000, "self test")
        }
    }

    // ── 3 时钟偏差 ───────────────────────────────────────────────────────────

    private suspend fun clock(): Check {
        val sample = withContext(Dispatchers.IO) { ClockSync.sync(context, app.wolfxApi) }
            ?: return Check(StepStatus.WARN, context.getString(R.string.svc_st_clock_unreachable))
        val offset = ClockSync.formatSeconds(sample.offsetMs)
        return when {
            abs(sample.offsetMs) <= ClockSync.OK_MS -> Check(StepStatus.PASS, context.getString(R.string.svc_st_clock_ok, offset, sample.roundTripMs))
            abs(sample.offsetMs) <= ClockSync.WARN_MS -> Check(StepStatus.WARN, context.getString(R.string.svc_st_clock_warn, offset), FixAction.DATE_SETTINGS)
            else -> Check(StepStatus.FAIL, context.getString(R.string.svc_st_clock_fail, offset), FixAction.DATE_SETTINGS)
        }
    }

    // ── 4 定位 ───────────────────────────────────────────────────────────────

    private suspend fun location(settings: UserSettings): Check {
        val loc = withContext(Dispatchers.IO) { app.locationProvider.resolve(settings, LOCATION_MAX_AGE_MS) }
        location = loc
        val coords = String.format(Locale.US, "%.2f, %.2f", loc.latitude, loc.longitude)
        val missingPermission = DeliveryRequirement.LOCATION in DeliveryChecks.missing(context)
        return when (loc.source) {
            UserLocation.SOURCE_MANUAL -> Check(StepStatus.PASS, context.getString(R.string.svc_st_location_manual, loc.name.ifBlank { coords }))
            UserLocation.SOURCE_GPS, UserLocation.SOURCE_NETWORK -> {
                val ageMin = ((loc.ageMs() ?: 0L) / 60_000L).toInt()
                val accuracy = loc.accuracyM?.roundToInt()?.let { "±$it m" } ?: "—"
                val detail = context.getString(R.string.svc_st_location_device, loc.source, accuracy, ageMin)
                if ((loc.ageMs() ?: Long.MAX_VALUE) <= LOCATION_MAX_AGE_MS) Check(StepStatus.PASS, detail) else Check(StepStatus.WARN, detail)
            }
            UserLocation.SOURCE_IP -> Check(
                StepStatus.WARN, context.getString(R.string.svc_st_location_ip, coords),
                if (missingPermission) FixAction.LOCATION_PERMISSION else null,
            )
            else -> Check(StepStatus.FAIL, context.getString(R.string.svc_st_location_none), FixAction.LOCATION_PERMISSION)
        }
    }

    // ── 5 通知、悬浮窗、全屏通知 ─────────────────────────────────────────────

    private fun permissions(): Check {
        DeliveryChecks.refresh(context)
        val missing = DeliveryChecks.missing(context)
        return when {
            DeliveryRequirement.NOTIFICATIONS in missing ->
                Check(StepStatus.FAIL, context.getString(R.string.svc_st_perm_notifications), FixAction.NOTIFICATION_SETTINGS)
            DeliveryRequirement.OVERLAY in missing ->
                Check(StepStatus.FAIL, context.getString(R.string.svc_st_perm_overlay), FixAction.OVERLAY_SETTINGS)
            DeliveryRequirement.FULL_SCREEN_INTENT in missing ->
                Check(StepStatus.WARN, context.getString(R.string.svc_st_perm_full_screen), FixAction.FULL_SCREEN_SETTINGS)
            else -> Check(StepStatus.PASS, context.getString(R.string.svc_st_perm_ok))
        }
    }

    // ── 6 电池优化、待机分组 ─────────────────────────────────────────────────

    private fun systemLimits(): Check {
        val exempt = HealthRecorder.batteryExempt(context)
        val bucket = HealthRecorder.standbyBucket(context)
        val bucketLabel = bucket ?: "—"
        return when {
            bucket == "RESTRICTED" ->
                Check(StepStatus.FAIL, context.getString(R.string.svc_st_limits_restricted), FixAction.BATTERY_OPTIMIZATION)
            exempt == false ->
                Check(StepStatus.WARN, context.getString(R.string.svc_st_limits_battery, bucketLabel), FixAction.BATTERY_OPTIMIZATION)
            bucket == "RARE" ->
                Check(StepStatus.WARN, context.getString(R.string.svc_st_limits_rare), FixAction.BATTERY_OPTIMIZATION)
            AutoStartSettings.isRestricted() ->
                Check(StepStatus.WARN, context.getString(R.string.svc_st_limits_auto_start, bucketLabel), FixAction.AUTO_START)
            else -> Check(StepStatus.PASS, context.getString(R.string.svc_st_limits_ok, bucketLabel))
        }
    }

    // ── 7 注入测试报文走完解析 → 评估 ────────────────────────────────────────

    private fun pipeline(settings: UserSettings): Check {
        val loc = location?.takeIf { it.isKnown } ?: app.locationProvider.cached()?.takeIf { it.isKnown } ?: PIPELINE_STAND_IN
        val epicenter = SeismicCalculator.offsetPoint(loc.latitude, loc.longitude, 45.0, 120.0)
        val now = ClockSync.now()
        val frame = buildJsonObject {
            put("type", EewSource.CENC.typeKey)
            put("ID", "selftest")
            put("EventID", "SELFTEST-$now")
            put("ReportTime", ReportTime.formatCst(now))
            put("ReportNum", 1)
            put("OriginTime", ReportTime.formatCst(now - 8_000L))
            put("HypoCenter", "SELFTEST")
            put("Latitude", epicenter.latitude)
            put("Longitude", epicenter.longitude)
            put("Magnitude", 5.5)
            put("Depth", 10.0)
            put("MaxIntensity", 7.0)
        }.toString()
        val t0 = System.nanoTime()
        val parsed = EewParser.parse(frame, EewSource.CENC)
        val t1 = System.nanoTime()
        val event = (parsed as? EewFrame.Report)?.event
            ?: return Check(StepStatus.FAIL, context.getString(R.string.svc_st_pipeline_fail, parsed.toString()))
        val distance = SeismicCalculator.haversineDistance(loc.latitude, loc.longitude, event.latitude, event.longitude)
        val depth = event.depth ?: 10.0
        val arrival = SeismicCalculator.calcWaveArrival(depth, distance)
        val csis = SeismicCalculator.calcLocalIntensity(event.magnitude, depth, distance)
        val tier = AlertEvaluator.tierOf(csis, event.magnitude, settings.actionMinMagnitude, settings.actionMinIntensity, settings.intenseThreshold)
        val t2 = System.nanoTime()
        val parseUs = (t1 - t0) / 1_000L
        val evaluateUs = (t2 - t1) / 1_000L
        if (arrival.sWaveSeconds <= 0.0 || csis.isNaN()) {
            return Check(StepStatus.FAIL, context.getString(R.string.svc_st_pipeline_fail, "S=${arrival.sWaveSeconds} CSIS=$csis"))
        }
        return Check(
            StepStatus.PASS,
            context.getString(
                R.string.svc_st_pipeline_ok,
                String.format(Locale.US, "%.1f", parseUs / 1000.0),
                String.format(Locale.US, "%.1f", evaluateUs / 1000.0),
                String.format(Locale.US, "%.1f", csis),
                tier.name,
            ),
        )
    }

    // ── 8 预警呈现通道（静默） ───────────────────────────────────────────────

    private suspend fun presentation(): Check {
        val manager = context.getSystemService(NotificationManager::class.java)
            ?: return Check(StepStatus.FAIL, context.getString(R.string.svc_st_present_blocked), FixAction.NOTIFICATION_SETTINGS)
        AppNotifications.ensureChannels(context)
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return Check(StepStatus.FAIL, context.getString(R.string.svc_st_present_blocked), FixAction.NOTIFICATION_SETTINGS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val blocked = listOf(AppNotifications.CHANNEL_ALERT, AppNotifications.CHANNEL_ALARM).any { id ->
                manager.getNotificationChannel(id)?.importance == NotificationManager.IMPORTANCE_NONE
            }
            if (blocked) return Check(StepStatus.FAIL, context.getString(R.string.svc_st_present_channel), FixAction.NOTIFICATION_SETTINGS)
        }
        // Silent probe: post, check it is listed, withdraw.
        val probe = NotificationCompat.Builder(context, AppNotifications.CHANNEL_INFO)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.svc_st_probe_title))
            .setSilent(true)
            .setTimeoutAfter(PROBE_NOTIFICATION_MS)
            .build()
        AppNotifications.notify(context, AppNotifications.ID_SELF_TEST, probe)
        delay(PROBE_SETTLE_MS)
        val shown = try {
            manager.activeNotifications.any { it.id == AppNotifications.ID_SELF_TEST }
        } catch (e: Exception) {
            true // not queryable on this device; posting did not throw
        }
        AppNotifications.cancel(context, AppNotifications.ID_SELF_TEST)
        if (!shown) return Check(StepStatus.FAIL, context.getString(R.string.svc_st_present_blocked), FixAction.NOTIFICATION_SETTINGS)

        val audio = context.getSystemService(AudioManager::class.java)
        val alarmVolume = audio?.getStreamVolume(AudioManager.STREAM_ALARM)
        val missing = DeliveryChecks.missing(context)
        return when {
            alarmVolume == 0 -> Check(StepStatus.WARN, context.getString(R.string.svc_st_present_volume))
            DeliveryRequirement.OVERLAY in missing ->
                Check(StepStatus.WARN, context.getString(R.string.svc_st_present_no_overlay), FixAction.OVERLAY_SETTINGS)
            DeliveryRequirement.FULL_SCREEN_INTENT in missing ->
                Check(StepStatus.WARN, context.getString(R.string.svc_st_present_no_fsi), FixAction.FULL_SCREEN_SETTINGS)
            else -> Check(StepStatus.PASS, context.getString(R.string.svc_st_present_ok))
        }
    }

    private companion object {
        const val STEP_TIMEOUT_MS = 30_000L
        const val PROBE_TIMEOUT_MS = 12_000L
        const val PROBE_SETTLE_MS = 300L
        const val PROBE_NOTIFICATION_MS = 10_000L
        const val LOCATION_MAX_AGE_MS = 30 * 60_000L
        /** Where the pipeline check evaluates when no location is known: 成都. */
        val PIPELINE_STAND_IN = UserLocation(30.66, 104.07, UserLocation.SOURCE_MANUAL)
    }
}
