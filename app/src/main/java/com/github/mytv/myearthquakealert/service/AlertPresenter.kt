package com.github.mytv.myearthquakealert.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.data.model.EewEvent
import com.github.mytv.myearthquakealert.data.model.UserLocation
import com.github.mytv.myearthquakealert.data.repository.UserSettings
import com.github.mytv.myearthquakealert.diagnostics.ClockSync
import com.github.mytv.myearthquakealert.diagnostics.Diagnostics
import com.github.mytv.myearthquakealert.diagnostics.HealthLevel
import com.github.mytv.myearthquakealert.diagnostics.HealthRecorder
import com.github.mytv.myearthquakealert.diagnostics.LogTags
import com.github.mytv.myearthquakealert.domain.AlertEvaluator
import com.github.mytv.myearthquakealert.domain.SeismicCalculator
import com.github.mytv.myearthquakealert.ui.alert.AlertActivity
import com.github.mytv.myearthquakealert.ui.navigation.Destination
import com.github.mytv.myearthquakealert.util.isUserUnlocked
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Decides the tier of each report and presents it (design §4 预警体验 / 一次预警的时间线).
 *
 * | tier   | device in use (unlocked, screen on) | locked / screen off  | TV                  | before first unlock |
 * |--------|-------------------------------------|----------------------|---------------------|---------------------|
 * | RECORD | history + silent notification       | same                 | same                | same                |
 * | REMIND | floating card + Live Update + chime | full screen (FSI)    | subtitle overlay    | heads-up            |
 * | STRONG | full-screen activity + alarm + TTS  | full screen (FSI)    | full-screen activity| heads-up + alarm    |
 *
 * Without the overlay permission the card / direct activity fall back to a heads-up or
 * full-screen-intent notification. Every REMIND/STRONG alert also carries a Live Update
 * (Android 16 promoted ongoing notification with a P/S progress track; a progress bar and
 * chronometer countdown below).
 *
 * Further reports of the same event update numbers silently; only a tier escalation alerts
 * again. Muting ([ActiveAlertHolder.mute]) stops sound, vibration and speech at once.
 * 60 s after the S wave the alert turns into a summary. Main thread only.
 */
internal class AlertPresenter(
    private val context: Context,
    private val scope: CoroutineScope,
    private val host: Host,
) {
    interface Host {
        fun settings(): UserSettings

        /**
         * Offers the alert notification as the service's foreground notification. When
         * monitoring is off (a drill), the service enters the foreground with it and returns
         * true (the notification is then posted); otherwise returns false.
         */
        fun holdForeground(notificationId: Int, notification: Notification): Boolean

        /** No alert is sounding or counting down any more. */
        fun onAlertIdle()
    }

    data class Outcome(val tier: AlertTier, val evaluateMs: Long, val presentMs: Long, val presented: Boolean)

    private enum class Surface(val label: Int) {
        OVERLAY(R.string.svc_surface_overlay),
        ACTIVITY(R.string.svc_surface_activity),
        FULL_SCREEN(R.string.svc_surface_full_screen),
        HEADS_UP(R.string.svc_surface_heads_up),
        LOCKED_NOTIFICATION(R.string.svc_surface_locked),
    }

    private inner class Active(var data: AlertData, val drill: Boolean) {
        var surface = Surface.HEADS_UP
        var channel = AppNotifications.CHANNEL_ALERT
        var audible = false
        var finished = false
        var job: Job? = null
        var lastCountdownSecond = Int.MAX_VALUE
        var arrivalAnnounced = false
        var alertAgain = true
    }

    private var active: Active? = null
    /** Events the user dismissed, with the tier they had then (small, newest last). */
    private val dismissed = LinkedHashMap<String, AlertTier>()
    private val effects = AlertEffects(context)
    private var wakeLock: PowerManager.WakeLock? = null

    /** An alert is counting down or sounding. */
    val isPresenting: Boolean get() = active?.let { !it.finished } ?: false

    /** An alert (or its summary) is still held for the UI. */
    val hasAlert: Boolean get() = active != null

    init {
        scope.launch { ActiveAlertHolder.muted.collect { if (it) onMuted() } }
        scope.launch { ActiveAlertHolder.activeAlert.collect { if (it == null) onHolderCleared() } }
    }

    /**
     * Evaluates [event] at [location] and presents it. [originServerAt] is the origin time
     * on the corrected (server) clock; null uses now. A drill passes its [drillTier] (STRONG,
     * or REMIND for the distant scenario), which replaces the evaluated tier; drills are
     * marked 演练 everywhere and never written to the history.
     */
    fun present(event: EewEvent, location: UserLocation, originServerAt: Long?, drillTier: AlertTier? = null): Outcome {
        val t0 = System.nanoTime()
        val drill = drillTier != null
        val settings = host.settings()
        val distance = SeismicCalculator.haversineDistance(location.latitude, location.longitude, event.latitude, event.longitude)
        val depth = event.depth ?: DEFAULT_DEPTH_KM
        val arrival = SeismicCalculator.calcWaveArrival(depth, distance)
        val csis = SeismicCalculator.calcLocalIntensity(event.magnitude, depth, distance)
        val tier = drillTier ?: AlertEvaluator.tierOf(
            csis, event.magnitude, settings.actionMinMagnitude, settings.actionMinIntensity, settings.intenseThreshold,
        )
        val evaluateMs = nanosToMs(System.nanoTime() - t0)
        val t1 = System.nanoTime()

        // Origin on the local clock: the corrected origin shifted by the measured skew.
        val skew = System.currentTimeMillis() - ClockSync.now()
        val originLocal = (originServerAt ?: ClockSync.now()) + skew
        if (!drill) EewHistory.record(event, tier, csis, distance)

        val current = active
        if (current != null && current.data.event.eventId == event.eventId) {
            update(current, event, arrival, csis, distance, tier, originLocal, settings)
            return Outcome(current.data.tier, evaluateMs, nanosToMs(System.nanoTime() - t1), true)
        }
        val dismissedTier = dismissed[event.eventId]
        if (dismissedTier != null && tier <= dismissedTier) {
            // 我已安全 already: later reports of the event update the history only, unless it gets stronger.
            return Outcome(tier, evaluateMs, nanosToMs(System.nanoTime() - t1), false)
        }
        if (tier == AlertTier.RECORD) {
            // 记录: only written to the 地震 list (EewHistory above).
            log(HealthLevel.INFO, event, tier, csis, distance, context.getString(R.string.svc_surface_record), drill)
            return Outcome(tier, evaluateMs, nanosToMs(System.nanoTime() - t1), false)
        }
        val sArrivalLocal = originLocal + (arrival.sWaveSeconds * 1000).toLong()
        if (!drill && System.currentTimeMillis() > sArrivalLocal + SUMMARY_AFTER_ARRIVAL_MS) {
            // Delivered after the shaking was over (late report, e.g. after a reconnect): history only.
            Diagnostics.log(
                HealthLevel.WARN, LogTags.ALERT,
                context.getString(R.string.svc_log_alert_late, event.hypocenter, event.magnitude, tier.name),
            )
            return Outcome(tier, evaluateMs, nanosToMs(System.nanoTime() - t1), false)
        }
        if (current != null && !current.finished && current.data.tier > tier) {
            // A weaker event must not interrupt a stronger alert in progress.
            Diagnostics.log(HealthLevel.INFO, LogTags.ALERT, context.getString(R.string.svc_log_alert_kept, event.hypocenter, event.magnitude))
            return Outcome(tier, evaluateMs, nanosToMs(System.nanoTime() - t1), false)
        }
        current?.let { retire(it) }

        ActiveAlertHolder.resetMute() // a new alert starts unmuted
        val data = AlertData(
            event = event,
            userLatitude = location.latitude,
            userLongitude = location.longitude,
            pWaveSeconds = arrival.pWaveSeconds,
            sWaveSeconds = arrival.sWaveSeconds,
            localCsis = csis,
            distanceKm = distance,
            isSimulation = drill,
            tier = tier,
            presentedAt = System.currentTimeMillis(),
            originAt = originLocal,
        )
        val alert = Active(data, drill)
        active = alert
        ActiveAlertHolder.showAlert(data)
        showSurfaces(alert, settings)
        startEffects(alert, settings)
        acquireWakeLock(data)
        alert.job = scope.launch { countdown(alert) }

        HealthRecorder.recordAlert(event.eventId, tier.name, drill)
        log(if (tier == AlertTier.STRONG) HealthLevel.ERROR else HealthLevel.WARN, event, tier, csis, distance, context.getString(alert.surface.label), drill)
        return Outcome(tier, evaluateMs, nanosToMs(System.nanoTime() - t1), true)
    }

    /** 我已安全 from the notification. */
    fun dismiss() {
        ActiveAlertHolder.dismissAlert()
        AppNotifications.cancel(context, AppNotifications.ID_ALERT_SUMMARY)
    }

    /** Service shutdown. */
    fun release() {
        val a = active
        active = null
        a?.job?.cancel()
        effects.release()
        releaseWakeLock()
        if (a != null) {
            AppNotifications.cancel(context, AppNotifications.ID_ALERT)
            if (!a.finished) ActiveAlertHolder.dismissAlert()
        }
    }

    // ── Presentation ────────────────────────────────────────────────────────

    private fun update(
        a: Active,
        event: EewEvent,
        arrival: SeismicCalculator.WaveArrival,
        csis: Double,
        distance: Double,
        tier: AlertTier,
        originLocal: Long,
        settings: UserSettings,
    ) {
        // Reports may be evaluated out of order (location lookups); never step back.
        if (event.reportNum < a.data.event.reportNum) return
        val newTier = if (tier > a.data.tier) tier else a.data.tier
        val escalated = newTier > a.data.tier
        a.data = a.data.copy(
            event = event,
            pWaveSeconds = arrival.pWaveSeconds,
            sWaveSeconds = arrival.sWaveSeconds,
            localCsis = csis,
            distanceKm = distance,
            tier = newTier,
            originAt = originLocal,
        )
        if (ActiveAlertHolder.activeAlert.value != null) ActiveAlertHolder.showAlert(a.data)
        if (a.finished) {
            postSummary(a)
            return
        }
        if (escalated) {
            if (a.surface == Surface.OVERLAY) AlertOverlayService.hide(context)
            showSurfaces(a, settings)
            startEffects(a, settings)
            HealthRecorder.recordAlert(event.eventId, newTier.name, a.drill)
            log(HealthLevel.ERROR, event, newTier, csis, distance, context.getString(a.surface.label), a.drill)
        } else {
            a.alertAgain = false
            postAlertNotification(a)
        }
    }

    private fun showSurfaces(a: Active, settings: UserSettings) {
        val tier = a.data.tier
        val unlocked = context.isUserUnlocked()
        val tv = DeviceState.isTv(context)
        val inUse = DeviceState.inUse(context)
        val overlayOk = Settings.canDrawOverlays(context)
        a.surface = when {
            !unlocked -> Surface.LOCKED_NOTIFICATION
            tier == AlertTier.REMIND && (tv || inUse) -> if (overlayOk) Surface.OVERLAY else Surface.HEADS_UP
            tier == AlertTier.REMIND -> Surface.FULL_SCREEN
            (tv || inUse) && overlayOk -> Surface.ACTIVITY
            else -> Surface.FULL_SCREEN
        }
        when (a.surface) {
            Surface.OVERLAY -> if (!AlertOverlayService.show(context)) a.surface = Surface.HEADS_UP
            Surface.ACTIVITY -> if (!launchAlertActivity()) a.surface = Surface.FULL_SCREEN
            else -> Unit
        }
        a.channel = when (a.surface) {
            Surface.OVERLAY, Surface.ACTIVITY -> AppNotifications.CHANNEL_LIVE
            else -> if (tier == AlertTier.STRONG) AppNotifications.CHANNEL_ALARM else AppNotifications.CHANNEL_ALERT
        }
        if (a.channel == AppNotifications.CHANNEL_ALARM && settings.alarmBypassDnd && !a.drill) refreshAlarmChannel()
        a.alertAgain = true
        postAlertNotification(a)
    }

    private fun startEffects(a: Active, settings: UserSettings) {
        if (ActiveAlertHolder.muted.value) return
        when (a.data.tier) {
            AlertTier.STRONG -> {
                // Drills never bypass Do Not Disturb (design: 演练默认不越过勿扰).
                val bypass = settings.alarmBypassDnd && !a.drill
                a.audible = bypass || !effects.dndActive()
                if (!a.audible) {
                    Diagnostics.log(HealthLevel.WARN, LogTags.ALERT, context.getString(R.string.svc_log_alert_dnd))
                    return
                }
                effects.startAlarm()
                if (settings.ttsEnabled) effects.speak { announcement(a) }
            }
            AlertTier.REMIND -> {
                // The live channel is silent: play the notification sound ourselves.
                effects.chime(withSound = a.channel == AppNotifications.CHANNEL_LIVE)
                a.audible = false
            }
            AlertTier.RECORD -> Unit
        }
    }

    private fun announcement(a: Active): String {
        val remaining = ceil(a.data.sRemainingSeconds()).toInt()
        val intensity = a.data.localCsis.roundToInt()
        return when {
            remaining <= 0 -> context.getString(if (a.drill) R.string.svc_tts_arrived_drill else R.string.svc_tts_arrived)
            a.drill -> context.getString(R.string.svc_tts_announce_drill, remaining, intensity)
            else -> context.getString(R.string.svc_tts_announce, remaining, intensity)
        }
    }

    private suspend fun countdown(a: Active) {
        while (active === a && !a.finished) {
            val now = System.currentTimeMillis()
            val remaining = ceil((a.data.sArrivalAt - now) / 1000.0).toInt()
            if (a.audible && a.data.tier == AlertTier.STRONG && !ActiveAlertHolder.muted.value) {
                val settings = host.settings()
                if (remaining in 1..FINAL_COUNTDOWN_S && remaining < a.lastCountdownSecond) {
                    a.lastCountdownSecond = remaining
                    if (settings.ttsEnabled && effects.speechAvailable) effects.speak { remaining.toString() } else effects.beep()
                }
                if (remaining <= 0 && !a.arrivalAnnounced) {
                    a.arrivalAnnounced = true
                    if (settings.ttsEnabled) effects.speak { announcement(a) }
                }
                val alarmOver = now > a.data.sArrivalAt + ALARM_AFTER_ARRIVAL_MS ||
                    // A drill far from the epicenter can count down for minutes: keep its alarm short.
                    (a.drill && now - a.data.presentedAt > DRILL_ALARM_MAX_MS)
                if (alarmOver && effects.alarmPlaying) effects.stopAlarm()
            }
            if (now >= a.data.sArrivalAt + SUMMARY_AFTER_ARRIVAL_MS) {
                finish(a)
                return
            }
            a.alertAgain = false
            postAlertNotification(a)
            delay(1_000L - now % 1_000L)
        }
    }

    /** 60 s after the S wave: stop everything audible and leave a summary. */
    private fun finish(a: Active) {
        a.finished = true
        a.job = null
        effects.stopAll()
        releaseWakeLock()
        AppNotifications.cancel(context, AppNotifications.ID_ALERT)
        postSummary(a)
        val eventId = a.data.event.eventId
        // The UI shows the summary card until dismissed; clear it eventually if nobody does.
        scope.launch {
            delay(HOLDER_CLEAR_AFTER_FINISH_MS)
            if (active === a && ActiveAlertHolder.activeAlert.value?.event?.eventId == eventId) ActiveAlertHolder.dismissAlert()
        }
        host.onAlertIdle()
    }

    /** Replaced by a newer, at least as strong event. */
    private fun retire(a: Active) {
        a.job?.cancel()
        a.finished = true
        effects.stopAll()
        releaseWakeLock()
        AppNotifications.cancel(context, AppNotifications.ID_ALERT)
        if (a.surface == Surface.OVERLAY) AlertOverlayService.hide(context)
    }

    private fun onMuted() {
        val a = active ?: return
        effects.stopAll()
        if (a.audible) Diagnostics.log(HealthLevel.INFO, LogTags.ALERT, context.getString(R.string.svc_log_alert_muted))
        a.audible = false
        if (!a.finished) {
            a.alertAgain = false
            postAlertNotification(a)
        }
    }

    private fun onHolderCleared() {
        val a = active ?: return
        // StateFlow conflation: act only if the alert is still gone now.
        if (ActiveAlertHolder.activeAlert.value != null) return
        active = null
        dismissed.remove(a.data.event.eventId)
        dismissed[a.data.event.eventId] = a.data.tier
        while (dismissed.size > MAX_DISMISSED) dismissed.remove(dismissed.keys.first())
        a.job?.cancel()
        effects.stopAll()
        releaseWakeLock()
        if (!a.finished) AppNotifications.cancel(context, AppNotifications.ID_ALERT)
        host.onAlertIdle()
    }

    private fun launchAlertActivity(): Boolean = try {
        // Allowed from the background because the user granted the overlay permission.
        context.startActivity(alertActivityIntent())
        true
    } catch (e: Exception) {
        Diagnostics.log(HealthLevel.WARN, LogTags.ALERT, "AlertActivity: ${e.message}")
        false
    }

    private fun alertActivityIntent() = Intent(context, AlertActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)

    // ── Notifications ───────────────────────────────────────────────────────

    private fun postAlertNotification(a: Active) {
        AppNotifications.ensureChannels(context)
        val notification = buildAlertNotification(a)
        if (!host.holdForeground(AppNotifications.ID_ALERT, notification)) {
            AppNotifications.notify(context, AppNotifications.ID_ALERT, notification)
        }
    }

    @SuppressLint("InlinedApi")
    private fun buildAlertNotification(a: Active): Notification {
        val d = a.data
        val event = d.event
        val now = System.currentTimeMillis()
        val remaining = ceil((d.sArrivalAt - now) / 1000.0).toInt()
        val arrived = remaining <= 0
        val intensity = d.localCsis.roundToInt()
        val title = if (arrived) context.getString(R.string.svc_alert_arrived) else context.getString(R.string.svc_alert_countdown, remaining)
        val body = context.getString(R.string.svc_alert_body, intensity.toString(), event.hypocenter, event.magnitude, d.distanceKm.roundToInt())
        val sub = reportLabel(a)
        val unlocked = context.isUserUnlocked()
        val contentIntent = if (unlocked) {
            PendingIntent.getActivity(context, REQUEST_ALERT_CONTENT, alertActivityIntent(), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        } else {
            AppNotifications.openAppIntent(context, Destination.OVERVIEW)
        }
        val totalTenths = ((d.sArrivalAt - d.presentedAt) / 100L).coerceAtLeast(1L).toInt()
        val doneTenths = ((now - d.presentedAt) / 100L).coerceIn(0L, totalTenths.toLong()).toInt()

        val builder = NotificationCompat.Builder(context, a.channel)
            .setSmallIcon(R.drawable.ic_svc_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setSubText(sub)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(if (a.channel == AppNotifications.CHANNEL_LIVE) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColor(ALERT_COLOR)
            .setOngoing(true)
            .setOnlyAlertOnce(!a.alertAgain)
            .setShowWhen(true)
            .setWhen(d.sArrivalAt)
            .setUsesChronometer(!arrived)
            .setChronometerCountDown(!arrived)
            .setProgress(totalTenths, if (arrived) totalTenths else doneTenths, false)
            .setContentIntent(contentIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(
                R.drawable.ic_svc_check, context.getString(R.string.svc_action_safe),
                AppNotifications.serviceCommand(context, REQUEST_SAFE, EewMonitorService.commandIntent(context, EewMonitorService.ACTION_ALERT_DISMISS)),
            )
        if (a.audible && !ActiveAlertHolder.muted.value) {
            builder.addAction(
                R.drawable.ic_svc_mute, context.getString(R.string.svc_action_mute),
                AppNotifications.serviceCommand(context, REQUEST_MUTE, EewMonitorService.commandIntent(context, EewMonitorService.ACTION_ALERT_MUTE)),
            )
        }
        if (a.surface == Surface.FULL_SCREEN && unlocked) {
            builder.setFullScreenIntent(
                PendingIntent.getActivity(context, REQUEST_FULL_SCREEN, alertActivityIntent(), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
                true,
            )
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O && a.alertAgain && a.data.tier == AlertTier.REMIND && a.channel != AppNotifications.CHANNEL_LIVE) {
            builder.setDefaults(NotificationCompat.DEFAULT_SOUND)
        }
        val notification = builder.build()
        return if (Build.VERSION.SDK_INT >= 36) promote(notification, d, now, arrived, remaining) else notification
    }

    /**
     * Android 16 Live Update: a promoted ongoing notification with a ProgressStyle track from
     * the moment of the alert to the S-wave arrival, with a point at the P-wave arrival, and
     * the remaining seconds as the status-bar chip text.
     */
    @SuppressLint("NewApi")
    private fun promote(notification: Notification, d: AlertData, now: Long, arrived: Boolean, remaining: Int): Notification = try {
        val total = ((d.sArrivalAt - d.presentedAt) / 100L).coerceAtLeast(1L).toInt()
        val done = if (arrived) total else ((now - d.presentedAt) / 100L).coerceIn(0L, total.toLong()).toInt()
        val style = Notification.ProgressStyle()
            .setStyledByProgress(true)
            .setProgressSegments(listOf(Notification.ProgressStyle.Segment(total).setColor(ALERT_COLOR)))
            .setProgress(done)
        // The bar ends at the S-wave arrival; a point marks the P-wave arrival when it is still ahead.
        val pAt = ((d.pArrivalAt - d.presentedAt) / 100L).toInt()
        if (pAt in 1 until total) style.setProgressPoints(listOf(Notification.ProgressStyle.Point(pAt).setColor(P_WAVE_COLOR)))
        Notification.Builder.recoverBuilder(context, notification)
            .setStyle(style)
            .setRequestPromotedOngoing(true)
            .setShortCriticalText(
                if (arrived) context.getString(R.string.svc_alert_short_arrived) else context.getString(R.string.svc_alert_short, remaining)
            )
            .build()
    } catch (e: Exception) {
        notification
    }

    private fun reportLabel(a: Active): String {
        val event = a.data.event
        val report = if (event.isFinal) {
            context.getString(R.string.svc_alert_sub_final, event.reportNum)
        } else {
            context.getString(R.string.svc_alert_sub_report, event.reportNum)
        }
        return if (a.drill) context.getString(R.string.svc_alert_drill_prefix, report) else report
    }

    private fun postSummary(a: Active) {
        AppNotifications.ensureChannels(context)
        val d = a.data
        val title = context.getString(
            if (a.drill) R.string.svc_alert_summary_title_drill else R.string.svc_alert_summary_title,
            d.event.hypocenter, d.event.magnitude,
        )
        val text = context.getString(R.string.svc_alert_summary_text, reportLabel(a), d.localCsis.roundToInt().toString(), d.distanceKm.roundToInt())
        val notification = NotificationCompat.Builder(context, AppNotifications.CHANNEL_INFO)
            .setSmallIcon(R.drawable.ic_svc_alert)
            .setContentTitle(title)
            .setContentText(text)
            .setColor(ALERT_COLOR)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(AppNotifications.openAppIntent(context, Destination.QUAKES))
            .setSilent(true)
            .setAutoCancel(true)
            .setTimeoutAfter(SUMMARY_TIMEOUT_MS)
            .build()
        AppNotifications.notify(context, AppNotifications.ID_ALERT_SUMMARY, notification)
    }

    /** Re-submits the alarm channel so bypass-DND takes effect once policy access is granted. */
    private fun refreshAlarmChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(android.app.NotificationManager::class.java) ?: return
        if (!manager.isNotificationPolicyAccessGranted) return
        val channel = manager.getNotificationChannel(AppNotifications.CHANNEL_ALARM) ?: return
        if (!channel.canBypassDnd()) {
            channel.setBypassDnd(true)
            manager.createNotificationChannel(channel)
        }
    }

    private fun log(level: HealthLevel, event: EewEvent, tier: AlertTier, csis: Double, distance: Double, surface: String, drill: Boolean) {
        val tierLabel = if (drill) "${tier.name}/DRILL" else tier.name
        Diagnostics.log(
            level, LogTags.ALERT,
            context.getString(R.string.svc_log_alert, tierLabel, event.hypocenter, event.magnitude, csis, distance.roundToInt(), surface),
        )
    }

    private fun acquireWakeLock(data: AlertData) {
        releaseWakeLock()
        val pm = context.getSystemService(PowerManager::class.java) ?: return
        val timeout = (data.sArrivalAt - System.currentTimeMillis()).coerceAtLeast(0L) + SUMMARY_AFTER_ARRIVAL_MS + 10_000L
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "myearthquakealert:alert").apply {
            setReferenceCounted(false)
            acquire(timeout)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun nanosToMs(nanos: Long): Long = (nanos + 500_000L) / 1_000_000L

    companion object {
        private const val DEFAULT_DEPTH_KM = 10.0
        private const val FINAL_COUNTDOWN_S = 10
        private const val DRILL_ALARM_MAX_MS = 20_000L
        private const val MAX_DISMISSED = 20
        private const val ALARM_AFTER_ARRIVAL_MS = 30_000L
        private const val SUMMARY_AFTER_ARRIVAL_MS = 60_000L
        private const val HOLDER_CLEAR_AFTER_FINISH_MS = 9 * 60_000L
        private const val SUMMARY_TIMEOUT_MS = 2 * 3_600_000L
        private const val REQUEST_ALERT_CONTENT = 201
        private const val REQUEST_FULL_SCREEN = 202
        private const val REQUEST_SAFE = 203
        private const val REQUEST_MUTE = 204
        private val ALERT_COLOR = Color.rgb(0xD3, 0x2F, 0x2F)
        private val P_WAVE_COLOR = Color.rgb(0x1E, 0x88, 0xE5)
    }
}
