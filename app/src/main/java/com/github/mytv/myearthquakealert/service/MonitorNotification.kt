package com.github.mytv.myearthquakealert.service

import android.app.Notification
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.ui.navigation.Destination
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The ongoing monitoring notification (design §3 常驻通知): title and colour by phase (the
 * same words as every other surface), what the links are doing, uptime as a chronometer,
 * and the actions 暂停 1 小时 / 恢复 and 诊断.
 *
 * Reposted only when its content changes (phase, connected sources, pause end …) and at
 * most once per [MIN_INTERVAL_MS]; link chatter never makes it flicker. Main thread only.
 */
internal class MonitorNotification(private val context: Context) {

    private data class Content(
        val phase: MonitorPhase,
        val title: String,
        val text: String,
        val since: Long?,
        val paused: Boolean,
    )

    private val main = Handler(Looper.getMainLooper())
    private var posted: Content? = null
    private var pending: MonitorState? = null
    private var lastPostAt = 0L
    private val flush = Runnable { pending?.let { pending = null; post(it) } }

    /** The notification for [state], e.g. for startForeground. */
    fun build(state: MonitorState): Notification = build(content(state))

    /** Reposts the notification if [state] changes what it shows. */
    fun update(state: MonitorState) {
        val content = content(state)
        if (content == posted) return
        val wait = lastPostAt + MIN_INTERVAL_MS - SystemClock.elapsedRealtime()
        if (wait > 0 && content.phase == posted?.phase) {
            pending = state
            main.removeCallbacks(flush)
            main.postDelayed(flush, wait)
            return
        }
        post(state)
    }

    /** Marks [state] as shown (it was just passed to startForeground). */
    fun markPosted(state: MonitorState) {
        posted = content(state)
        lastPostAt = SystemClock.elapsedRealtime()
    }

    fun cancelPending() {
        main.removeCallbacks(flush)
        pending = null
        posted = null
    }

    private fun post(state: MonitorState) {
        val content = content(state)
        posted = content
        lastPostAt = SystemClock.elapsedRealtime()
        AppNotifications.notify(context, EewMonitorService.NOTIFICATION_ID, build(content))
    }

    private fun content(state: MonitorState): Content {
        val phase = state.phase
        val title = when (phase) {
            MonitorPhase.OFF -> context.getString(R.string.svc_phase_off)
            MonitorPhase.CONNECTING -> context.getString(R.string.svc_phase_connecting)
            MonitorPhase.MONITORING -> context.getString(R.string.svc_phase_monitoring)
            MonitorPhase.RECONNECTING -> context.getString(R.string.svc_phase_reconnecting)
            MonitorPhase.DEGRADED -> context.getString(R.string.svc_phase_degraded)
            MonitorPhase.LIMITED -> context.getString(R.string.svc_phase_limited)
            MonitorPhase.PAUSED -> context.getString(R.string.svc_phase_paused, clock(state.pauseUntil))
        }
        val sep = context.getString(R.string.svc_list_separator)
        val connected = state.sources.filter { it.link == LinkState.CONNECTED }.joinToString(sep) { it.source.label }
        val polling = state.sources.filter { it.link == LinkState.POLLING }.joinToString(sep) { it.source.label }
        val all = state.sources.joinToString(sep) { it.source.label }
        val text = when (phase) {
            MonitorPhase.OFF -> ""
            MonitorPhase.CONNECTING -> context.getString(R.string.svc_notif_connecting, all)
            MonitorPhase.MONITORING -> {
                val bootDelay = if (state.startOrigin == StartOrigin.BOOT) BootStartRecord.delaySecondsThisBoot(context) else null
                val base = context.getString(R.string.svc_notif_monitoring, connected)
                if (bootDelay != null) context.getString(R.string.svc_notif_boot, base, bootDelay) else base
            }
            MonitorPhase.RECONNECTING -> {
                val attempt = state.sources.maxOfOrNull { it.reconnectAttempt } ?: 0
                context.getString(R.string.svc_notif_reconnecting, attempt)
            }
            MonitorPhase.DEGRADED -> context.getString(R.string.svc_notif_degraded, polling)
            MonitorPhase.LIMITED -> context.getString(R.string.svc_notif_limited, missingLabels(state.missing))
            MonitorPhase.PAUSED -> context.getString(R.string.svc_notif_paused)
        }
        val since = if (phase == MonitorPhase.MONITORING || phase == MonitorPhase.DEGRADED) state.runningSince else null
        return Content(phase, title, text, since, phase == MonitorPhase.PAUSED)
    }

    private fun build(content: Content): Notification {
        val builder = NotificationCompat.Builder(context, AppNotifications.CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setColor(colorOf(content.phase))
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(content.since != null)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(AppNotifications.openAppIntent(context, Destination.OVERVIEW))
        if (content.since != null) {
            builder.setWhen(content.since).setUsesChronometer(true)
        }
        if (content.text.length > SHORT_TEXT) builder.setStyle(NotificationCompat.BigTextStyle().bigText(content.text))
        if (content.paused) {
            builder.addAction(
                R.drawable.ic_svc_play, context.getString(R.string.svc_action_resume),
                AppNotifications.serviceCommand(context, REQUEST_RESUME, EewMonitorService.commandIntent(context, EewMonitorService.ACTION_RESUME)),
            )
        } else if (content.phase != MonitorPhase.OFF) {
            builder.addAction(
                R.drawable.ic_svc_pause, context.getString(R.string.svc_action_pause_hour),
                AppNotifications.serviceCommand(
                    context, REQUEST_PAUSE,
                    EewMonitorService.commandIntent(context, EewMonitorService.ACTION_PAUSE)
                        .putExtra(EewMonitorService.EXTRA_PAUSE_DURATION, PAUSE_HOUR_MS),
                ),
            )
        }
        builder.addAction(
            R.drawable.ic_svc_diag, context.getString(R.string.svc_action_diagnostics),
            AppNotifications.openAppIntent(context, Destination.DIAGNOSTICS),
        )
        return builder.build()
    }

    private fun missingLabels(missing: Set<DeliveryRequirement>): String =
        missing.filter { it.required }.joinToString(context.getString(R.string.svc_list_separator)) { requirementLabel(context, it) }

    private fun clock(time: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(time))

    private fun colorOf(phase: MonitorPhase): Int = when (phase) {
        MonitorPhase.MONITORING -> COLOR_OK
        MonitorPhase.RECONNECTING, MonitorPhase.DEGRADED, MonitorPhase.PAUSED -> COLOR_WARN
        MonitorPhase.LIMITED -> COLOR_ERROR
        MonitorPhase.CONNECTING -> COLOR_PRIMARY
        MonitorPhase.OFF -> COLOR_OFF
    }

    companion object {
        private const val MIN_INTERVAL_MS = 2_000L
        private const val SHORT_TEXT = 40
        private const val REQUEST_PAUSE = 301
        private const val REQUEST_RESUME = 302
        const val PAUSE_HOUR_MS = 3_600_000L
        private val COLOR_OK = Color.rgb(0x2E, 0x7D, 0x32)
        private val COLOR_WARN = Color.rgb(0xEF, 0x6C, 0x00)
        private val COLOR_ERROR = Color.rgb(0xC6, 0x28, 0x28)
        private val COLOR_PRIMARY = Color.rgb(0x15, 0x65, 0xC0)
        private val COLOR_OFF = Color.rgb(0x75, 0x75, 0x75)

        /** Short name of a delivery requirement (通知权限, 悬浮窗 …). */
        fun requirementLabel(context: Context, requirement: DeliveryRequirement): String = context.getString(
            when (requirement) {
                DeliveryRequirement.NOTIFICATIONS -> R.string.svc_req_notifications
                DeliveryRequirement.OVERLAY -> R.string.svc_req_overlay
                DeliveryRequirement.FULL_SCREEN_INTENT -> R.string.svc_req_full_screen
                DeliveryRequirement.LOCATION -> R.string.svc_req_location
                DeliveryRequirement.BATTERY_OPTIMIZATION -> R.string.svc_req_battery
                DeliveryRequirement.DND_ACCESS -> R.string.svc_req_dnd
                DeliveryRequirement.AUTO_START -> R.string.svc_req_auto_start
            }
        )
    }
}
