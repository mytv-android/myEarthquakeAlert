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
import kotlin.math.roundToInt

/**
 * Notifications posted outside the monitoring service's own foreground notification:
 * the "monitoring did not start" notice after a reboot, and alerts raised before the
 * first unlock, when the overlay window cannot be shown.
 */
object AppNotifications {

    private const val TAG = "AppNotifications"
    private const val CHANNEL_STATUS = "eew_status"
    private const val CHANNEL_ALERT = "eew_alert"
    private const val ID_BOOT_NOTICE = 10
    private const val ID_LOCKED_ALERT = 11

    /** Tells the user monitoring stayed off after a reboot because start on boot is disabled. */
    fun showBootNotice(context: Context) {
        ensureChannels(context)
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val startIntent = EewMonitorService.startIntent(context)
        val start = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(context, ID_BOOT_NOTICE, startIntent, flags)
        } else {
            PendingIntent.getService(context, ID_BOOT_NOTICE, startIntent, flags)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.boot_notice_title))
            .setContentText(context.getString(R.string.boot_notice_text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.boot_notice_text)))
            .setContentIntent(openAppIntent(context))
            .addAction(0, context.getString(R.string.boot_notice_start), start)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .build()
        notify(context, ID_BOOT_NOTICE, notification)
    }

    fun cancelBootNotice(context: Context) {
        NotificationManagerCompat.from(context).cancel(ID_BOOT_NOTICE)
    }

    /** Heads-up alert for use before the first unlock, when the overlay cannot be drawn. */
    fun showLockedAlert(context: Context, alert: AlertData) {
        ensureChannels(context)
        val event = alert.event
        val title = context.getString(R.string.locked_alert_title, event.hypocenter, event.magnitude)
        val intensity = alert.localCsis.roundToInt()
        val seconds = alert.sWaveSeconds.roundToInt()
        val text = if (seconds > 0) {
            context.getString(R.string.locked_alert_text, intensity, seconds)
        } else {
            context.getString(R.string.locked_alert_text_arrived, intensity)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openAppIntent(context))
            .setTimeoutAfter(((alert.sWaveSeconds.coerceAtLeast(0.0) + 120) * 1000).toLong())
            .setAutoCancel(true)
            .build()
        notify(context, ID_LOCKED_ALERT, notification)
    }

    private fun openAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

    @SuppressLint("MissingPermission") // checked through areNotificationsEnabled()
    private fun notify(context: Context, id: Int, notification: Notification) {
        try {
            val manager = NotificationManagerCompat.from(context)
            if (manager.areNotificationsEnabled()) manager.notify(id, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission missing", e)
        }
    }

    private fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_STATUS,
                context.getString(R.string.notification_channel_status),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERT,
                context.getString(R.string.notification_channel_alert),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { lockscreenVisibility = Notification.VISIBILITY_PUBLIC }
        )
    }
}
