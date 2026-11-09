package com.github.mytv.myearthquakealert.wear.alert

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.github.mytv.myearthquakealert.wear.R
import com.github.mytv.myearthquakealert.wear.WearApp
import com.github.mytv.myearthquakealert.wear.data.WatchAlert
import com.github.mytv.myearthquakealert.wear.data.WatchStore
import com.github.mytv.myearthquakealert.wear.data.WireAlert
import com.github.mytv.myearthquakealert.wear.domain.Countdown
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Presents alerts on the watch (design §6 手表: 全屏倒计时 + 强振动, 盖在表盘之上).
 *
 * - STRONG (and drills): a full-screen-intent notification opening [AlertActivity] over the
 *   watch face, plus a repeating strong vibration until shortly after the S wave.
 * - REMIND: a heads-up notification and a short vibration; tapping it opens the countdown.
 *
 * The same entry points serve the phone's forwarded alerts and standalone monitoring.
 */
object WatchAlerts {

    private const val TAG = "WatchAlerts"
    const val CHANNEL_ALARM = "eew_alarm"
    const val CHANNEL_ALERT = "eew_alert"
    const val CHANNEL_MONITOR = "eew_monitor"
    const val NOTIFICATION_ALERT = 100

    private val main = Handler(Looper.getMainLooper())
    private val stopVibration = Runnable { vibratorOrNull?.cancel() }
    private val expire = Runnable { expireIfDone() }
    @Volatile private var vibratorOrNull: Vibrator? = null
    @Volatile private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        ensureChannels(context)
        expireIfDone()
    }

    private fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val alarm = NotificationChannel(CHANNEL_ALARM, context.getString(R.string.channel_alarm), NotificationManager.IMPORTANCE_HIGH).apply {
            description = context.getString(R.string.channel_alarm_desc)
            // The strong vibration is driven by WatchAlerts itself (repeating, alarm usage).
            enableVibration(false)
            setSound(null, null)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)
        }
        val alert = NotificationChannel(CHANNEL_ALERT, context.getString(R.string.channel_alert), NotificationManager.IMPORTANCE_HIGH).apply {
            description = context.getString(R.string.channel_alert_desc)
            enableVibration(true)
            vibrationPattern = REMIND_PATTERN
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        val monitor = NotificationChannel(CHANNEL_MONITOR, context.getString(R.string.channel_monitor), NotificationManager.IMPORTANCE_LOW).apply {
            description = context.getString(R.string.channel_monitor_desc)
            setShowBadge(false)
        }
        nm.createNotificationChannels(listOf(alarm, alert, monitor))
    }

    /** An alert forwarded by the phone (message, or DataItem when the message was missed). */
    fun onPhoneAlert(context: Context, wire: WireAlert, receivedAt: Long) {
        val alert = Countdown.fromPhone(wire, receivedAt, WatchStore.alert.value)
        present(context, alert)
    }

    fun onPhoneAlertEnded(context: Context, eventId: String) {
        val current = WatchStore.alert.value ?: return
        if (current.eventId != eventId || !current.viaPhone) return
        close(context, eventId)
    }

    /**
     * Shows [alert], or updates the alert in progress with a further report. A further
     * report alerts again only when the event became STRONG; a weaker event never replaces
     * a stronger alert still counting down; a 我已安全 event stays silent unless stronger.
     */
    fun present(context: Context, alert: WatchAlert) {
        val app = context.applicationContext
        appContext = app
        val now = System.currentTimeMillis()
        if (Countdown.snapshot(alert, now).stage == Countdown.Stage.EXPIRED) return
        val current = WatchStore.alert.value?.takeIf { Countdown.snapshot(it, now).stage != Countdown.Stage.EXPIRED }
        val sameEvent = current?.eventId == alert.eventId
        // Caught up after the shaking was over (watch was away): nothing to warn about.
        if (!sameEvent && !alert.drill && Countdown.isLate(alert, now)) return
        if (current != null && !sameEvent && current.strong && !alert.strong &&
            Countdown.snapshot(current, now).stage == Countdown.Stage.COUNTDOWN
        ) {
            Log.i(TAG, "kept ${current.eventId}; weaker ${alert.eventId} not shown")
            return
        }
        val escalated = sameEvent && alert.strong && current?.strong == false
        if (WatchStore.dismissedEventId.value == alert.eventId && !escalated) {
            return
        }
        WatchStore.setAlert(alert)
        val alertAgain = !sameEvent || escalated
        notify(app, alert, alertAgain)
        if (alertAgain) {
            vibrate(app, alert, now)
            if (WearApp.isForeground) {
                try {
                    app.startActivity(AlertActivity.intent(app).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) {
                    Log.w(TAG, "alert screen not opened", e)
                }
            }
        }
        scheduleExpiry(alert, now)
    }

    /** 我已安全: closes the alert; later reports of the event stay silent unless stronger. */
    fun dismiss(context: Context) {
        val current = WatchStore.alert.value ?: return
        WatchStore.markDismissed(current.eventId)
        close(context, current.eventId)
    }

    private fun close(context: Context, eventId: String) {
        main.removeCallbacks(stopVibration)
        vibratorOrNull?.cancel()
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ALERT)
        WatchStore.clearAlert(eventId)
    }

    /** Drops an alert whose stages are all over (also called at app start). */
    fun expireIfDone() {
        val current = WatchStore.alert.value ?: return
        if (Countdown.snapshot(current, System.currentTimeMillis()).stage == Countdown.Stage.EXPIRED) {
            appContext?.let { NotificationManagerCompat.from(it).cancel(NOTIFICATION_ALERT) }
            WatchStore.clearAlert(current.eventId)
        }
    }

    private fun scheduleExpiry(alert: WatchAlert, now: Long) {
        main.removeCallbacks(expire)
        val at = alert.sArrivalAt + Countdown.CLOSE_AFTER_MS - now
        main.postDelayed(expire, at.coerceAtLeast(0L) + 500L)
    }

    private fun notify(context: Context, alert: WatchAlert, alertAgain: Boolean) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "notifications not allowed; alert ${alert.eventId} shown only in the app")
            return
        }
        val open = PendingIntent.getActivity(
            context, 1, AlertActivity.intent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val dismiss = PendingIntent.getBroadcast(
            context, 2, Intent(context, AlertActionReceiver::class.java).setAction(AlertActionReceiver.ACTION_SAFE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val now = System.currentTimeMillis()
        val seconds = Countdown.snapshot(alert, now).sSeconds
        val title = buildString {
            if (alert.drill) append(context.getString(R.string.alert_drill_prefix))
            append(context.getString(R.string.alert_title, alert.place, magnitude(alert.magnitude)))
        }
        val text = if (seconds > 0) {
            context.getString(R.string.alert_text_countdown, seconds, alert.localCsis.roundToInt(), alert.distanceKm.roundToInt())
        } else {
            context.getString(R.string.alert_text_arrived, alert.localCsis.roundToInt(), alert.distanceKm.roundToInt())
        }
        val builder = NotificationCompat.Builder(context, if (alert.strong) CHANNEL_ALARM else CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_earthquake)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .setOngoing(alert.strong)
            .setAutoCancel(!alert.strong)
            .setOnlyAlertOnce(!alertAgain)
            .setWhen(alert.sArrivalAt)
            .setUsesChronometer(seconds > 0)
            .setChronometerCountDown(true)
            .setTimeoutAfter((alert.sArrivalAt + Countdown.CLOSE_AFTER_MS - now).coerceAtLeast(1_000L))
            .addAction(R.drawable.ic_check, context.getString(R.string.alert_safe), dismiss)
        if (alert.strong) builder.setFullScreenIntent(open, true)
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ALERT, builder.build())
        } catch (e: SecurityException) {
            Log.w(TAG, "alert notification refused", e)
        }
    }

    private fun vibrate(context: Context, alert: WatchAlert, now: Long) {
        val vibrator = vibrator(context) ?: return
        vibratorOrNull = vibrator
        main.removeCallbacks(stopVibration)
        try {
            if (alert.strong) {
                val runFor = Countdown.vibrationRemainingMs(alert, now).coerceIn(MIN_STRONG_MS, MAX_STRONG_MS)
                play(vibrator, VibrationEffect.createWaveform(STRONG_PATTERN, STRONG_AMPLITUDES, 0))
                main.postDelayed(stopVibration, runFor)
            } else {
                play(vibrator, VibrationEffect.createWaveform(REMIND_PATTERN, -1))
            }
        } catch (e: Exception) {
            Log.w(TAG, "vibration failed", e)
        }
    }

    @Suppress("DEPRECATION")
    private fun play(vibrator: Vibrator, effect: VibrationEffect) {
        if (Build.VERSION.SDK_INT >= 33) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            vibrator.vibrate(
                effect,
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrator(context: Context): Vibrator? = try {
        if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }?.takeIf { it.hasVibrator() }
    } catch (e: Exception) {
        null
    }

    fun magnitude(m: Double): String = String.format(Locale.ROOT, "%.1f", m)

    private const val MIN_STRONG_MS = 5_000L
    private const val MAX_STRONG_MS = 90_000L
    private val STRONG_PATTERN = longArrayOf(0, 500, 250)
    private val STRONG_AMPLITUDES = intArrayOf(0, 255, 0)
    private val REMIND_PATTERN = longArrayOf(0, 400, 200, 400)
}
