package com.github.mytv.myearthquakealert.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.github.mytv.myearthquakealert.MainActivity
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.ui.navigation.Destination

/**
 * Notification channels and the notices posted outside the alert itself: "monitoring did
 * not start" after a reboot, "monitoring stopped, tap to resume" when a restart is not
 * allowed, and the silent "pause ended" notice. Alert notifications are built by
 * [AlertPresenter].
 *
 * Channels (importance never changes after creation, so each has one job):
 * - [CHANNEL_MONITOR] LOW — the ongoing monitoring notification.
 * - [CHANNEL_STATUS] DEFAULT — notices that need the user (boot notice, stopped).
 * - [CHANNEL_ALERT] HIGH, system sound — REMIND alerts (heads-up / full screen when locked).
 * - [CHANNEL_ALARM] HIGH, silent — STRONG alerts and drills; the service plays the alarm on
 *   the alarm stream itself, so it can follow the 越过勿扰 setting.
 * - [CHANNEL_LIVE] LOW, silent — the Live Update countdown while a card or full-screen alert
 *   is already on screen.
 * - [CHANNEL_INFO] LOW — alert summaries, pause-ended notice, self-test probe.
 */
object AppNotifications {

    private const val TAG = "AppNotifications"
    const val CHANNEL_MONITOR = "eew_monitor"
    const val CHANNEL_STATUS = "eew_status"
    const val CHANNEL_ALERT = "eew_alert"
    const val CHANNEL_ALARM = "eew_alarm"
    const val CHANNEL_LIVE = "eew_live"
    const val CHANNEL_INFO = "eew_info"
    private const val LEGACY_CHANNEL_OVERLAY = "alert_overlay"

    const val ID_BOOT_NOTICE = 10
    const val ID_ALERT = 11
    const val ID_ALERT_SUMMARY = 12
    const val ID_STOPPED = 14
    const val ID_PAUSE_ENDED = 15
    const val ID_SELF_TEST = 16

    @Volatile private var channelsReady = false

    /** Tells the user monitoring stayed off after a reboot because start on boot is disabled. */
    fun showBootNotice(context: Context) {
        ensureChannels(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.boot_notice_title))
            .setContentText(context.getString(R.string.boot_notice_text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.boot_notice_text)))
            .setContentIntent(openAppIntent(context, Destination.SETTINGS))
            .addAction(R.drawable.ic_svc_play, context.getString(R.string.boot_notice_start), startServiceIntent(context, ID_BOOT_NOTICE, StartOrigin.NOTIFICATION))
            .addAction(R.drawable.ic_svc_diag, context.getString(R.string.svc_action_settings), openAppIntent(context, Destination.SETTINGS))
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .build()
        notify(context, ID_BOOT_NOTICE, notification)
    }

    fun cancelBootNotice(context: Context) {
        NotificationManagerCompat.from(context).cancel(ID_BOOT_NOTICE)
        NotificationManagerCompat.from(context).cancel(ID_STOPPED)
    }

    /**
     * Monitoring should run but the system did not allow a background restart (Android 12+
     * foreground-service limits): a tap is a user action, which may start it.
     */
    fun showStoppedNotice(context: Context) {
        ensureChannels(context)
        val start = startServiceIntent(context, ID_STOPPED, StartOrigin.NOTIFICATION)
        val notification = NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.svc_stopped_title))
            .setContentText(context.getString(R.string.svc_stopped_text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.svc_stopped_text)))
            .setContentIntent(start)
            .addAction(R.drawable.ic_svc_play, context.getString(R.string.svc_action_resume), start)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .build()
        notify(context, ID_STOPPED, notification)
    }

    /** Silent notice that a pause ended and monitoring resumed (design: 到点自动恢复并发一条静默通知). */
    fun showPauseEnded(context: Context) {
        ensureChannels(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_INFO)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.svc_pause_ended_title))
            .setContentText(context.getString(R.string.svc_pause_ended_text))
            .setContentIntent(openAppIntent(context, Destination.OVERVIEW))
            .setSilent(true)
            .setAutoCancel(true)
            .setTimeoutAfter(6 * 3_600_000L)
            .build()
        notify(context, ID_PAUSE_ENDED, notification)
    }

    /** Opens MainActivity at [destination] (Destination.EXTRA), e.g. the 诊断 action. */
    fun openAppIntent(context: Context, destination: Destination): PendingIntent =
        PendingIntent.getActivity(
            context,
            REQUEST_OPEN_BASE + destination.ordinal,
            Intent(context, MainActivity::class.java)
                .putExtra(Destination.EXTRA, destination.route)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** Starts monitoring from a notification tap (a user action, so a foreground start is allowed). */
    fun startServiceIntent(context: Context, requestCode: Int, origin: StartOrigin): PendingIntent {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val intent = EewMonitorService.startIntent(context, origin)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(context, requestCode, intent, flags)
        } else {
            PendingIntent.getService(context, requestCode, intent, flags)
        }
    }

    /** A command to the (running) monitoring service, e.g. pause, mute. */
    fun serviceCommand(context: Context, requestCode: Int, intent: Intent): PendingIntent =
        PendingIntent.getService(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    @SuppressLint("MissingPermission") // checked through areNotificationsEnabled()
    fun notify(context: Context, id: Int, notification: Notification) {
        try {
            val manager = NotificationManagerCompat.from(context)
            if (manager.areNotificationsEnabled()) manager.notify(id, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission missing", e)
        }
    }

    fun cancel(context: Context, id: Int) {
        NotificationManagerCompat.from(context).cancel(id)
    }

    /** Creates every channel once per process. */
    fun ensureChannels(context: Context) {
        if (channelsReady || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_MONITOR, context.getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_STATUS, context.getString(R.string.notification_channel_status), NotificationManager.IMPORTANCE_DEFAULT)
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERT, context.getString(R.string.notification_channel_alert), NotificationManager.IMPORTANCE_HIGH).apply {
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ALARM, context.getString(R.string.svc_channel_alarm), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.svc_channel_alarm_desc)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null) // played by AlertEffects on the alarm stream
                enableVibration(false) // vibrated by AlertEffects with alarm attributes
                // Only honoured for apps with notification-policy access; harmless otherwise.
                setBypassDnd(true)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_LIVE, context.getString(R.string.svc_channel_live), NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_INFO, context.getString(R.string.svc_channel_info), NotificationManager.IMPORTANCE_LOW)
        )
        manager.deleteNotificationChannel(LEGACY_CHANNEL_OVERLAY)
        channelsReady = true
    }

    private const val REQUEST_OPEN_BASE = 100
}
