package com.github.mytv.myearthquakealert.update

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.github.mytv.myearthquakealert.MainActivity
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.data.model.UpdateInfo
import com.github.mytv.myearthquakealert.service.ActiveAlertHolder
import com.github.mytv.myearthquakealert.service.AppNotifications
import com.github.mytv.myearthquakealert.ui.navigation.Destination

/**
 * Notifications of 应用内更新, on their own LOW channel so they never sound: 发现新版本,
 * download progress (the worker's foreground notification), ready to install, continue
 * installing, and a failed background download. None is posted while an alert is active
 * (design §8 与监测衔接: 预警进行中不弹任何更新提示).
 */
object UpdateNotifications {
    const val CHANNEL = "app_update"
    const val ID_AVAILABLE = 20
    const val ID_PROGRESS = 21
    const val ID_READY = 22
    const val ID_CONFIRM = 23
    const val ID_FAILED = 24

    private const val REQUEST_OPEN = 200
    private const val REQUEST_CONFIRM = 201

    /** Settings sub-page key of 关于与更新. */
    const val PAGE_UPDATE = "update"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.upd_channel_name), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.upd_channel_desc)
                setShowBadge(true)
            }
        )
    }

    private val alertActive: Boolean get() = ActiveAlertHolder.activeAlert.value != null

    /** 发现新版本 → 设置 › 关于与更新. Returns false when it was held back. */
    fun showAvailable(context: Context, info: UpdateInfo): Boolean {
        if (alertActive) return false
        ensureChannel(context)
        val channel = context.getString(if (info.prerelease) R.string.pg_channel_preview else R.string.pg_channel_stable)
        val text = context.getString(R.string.upd_notify_available_text, channel)
        val notification = builder(context)
            .setContentTitle(context.getString(R.string.upd_notify_available_title, info.versionName))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(listOf(text).plus(info.notes.filter { it.isNotBlank() }.map { "• $it" }).joinToString("\n")))
            .setContentIntent(openUpdatePage(context))
            .setAutoCancel(true)
            .build()
        AppNotifications.notify(context, ID_AVAILABLE, notification)
        return true
    }

    /** The download worker's foreground notification. [total] 0 means unknown. */
    fun progress(context: Context, info: UpdateInfo, downloaded: Long, total: Long, verifying: Boolean, pause: PendingIntent?): Notification {
        ensureChannel(context)
        val percent = if (total > 0) (downloaded * 100 / total).toInt().coerceIn(0, 100) else 0
        val b = builder(context)
            .setContentTitle(context.getString(R.string.upd_notify_downloading, info.versionName))
            .setContentIntent(openUpdatePage(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        if (verifying) {
            b.setContentText(context.getString(R.string.pg_update_verifying_desc)).setProgress(0, 0, true)
        } else {
            b.setContentText(context.getString(R.string.pg_update_progress, percent, formatFileSize(downloaded), formatFileSize(total)))
                .setProgress(100, percent, total <= 0)
            if (pause != null) b.addAction(R.drawable.ic_svc_pause, context.getString(R.string.upd_pause), pause)
        }
        return b.build()
    }

    fun showReady(context: Context, info: UpdateInfo) {
        if (alertActive) return
        ensureChannel(context)
        AppNotifications.cancel(context, ID_FAILED)
        val notification = builder(context)
            .setContentTitle(context.getString(R.string.upd_notify_ready_title, info.versionName))
            .setContentText(context.getString(R.string.upd_notify_ready_text))
            .setContentIntent(openUpdatePage(context))
            .addAction(R.drawable.ic_svc_check, context.getString(R.string.update_install), openUpdatePage(context))
            .setAutoCancel(true)
            .build()
        AppNotifications.notify(context, ID_READY, notification)
    }

    fun showFailed(context: Context, info: UpdateInfo, message: String) {
        if (alertActive) return
        ensureChannel(context)
        val notification = builder(context)
            .setContentTitle(context.getString(R.string.upd_err_title_download))
            .setContentText(message)
            .setSubText(info.versionName)
            .setContentIntent(openUpdatePage(context))
            .setAutoCancel(true)
            .build()
        AppNotifications.notify(context, ID_FAILED, notification)
    }

    /** The system asks to confirm the installation while the app is not in front. */
    fun showConfirm(context: Context, versionName: String, confirm: Intent) {
        if (alertActive) return
        ensureChannel(context)
        val intent = PendingIntent.getActivity(
            context,
            REQUEST_CONFIRM,
            confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = builder(context)
            .setContentTitle(context.getString(R.string.upd_notify_confirm_title, versionName))
            .setContentText(context.getString(R.string.upd_notify_confirm_text))
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
        AppNotifications.notify(context, ID_CONFIRM, notification)
    }

    fun cancel(context: Context, vararg ids: Int) {
        ids.forEach { AppNotifications.cancel(context, it) }
    }

    /** MainActivity at 设置 › 关于与更新. */
    fun openUpdatePage(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            REQUEST_OPEN,
            Intent(context, MainActivity::class.java)
                .putExtra(Destination.EXTRA, Destination.SETTINGS.route)
                .putExtra(Destination.EXTRA_PAGE, PAGE_UPDATE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun builder(context: Context): NotificationCompat.Builder =
        NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
}
