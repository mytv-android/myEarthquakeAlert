package com.github.mytv.myearthquakealert.service

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.github.mytv.myearthquakealert.MyEarthQuakeAlertApp
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.diagnostics.Diagnostics
import com.github.mytv.myearthquakealert.diagnostics.HealthLevel
import com.github.mytv.myearthquakealert.diagnostics.LogTags
import com.github.mytv.myearthquakealert.util.deviceProtectedContext
import com.github.mytv.myearthquakealert.util.isUserUnlocked
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * 看门狗 (design §3): every 15 minutes, while monitoring is switched on, checks that the
 * service is alive and that its sockets still deliver frames.
 *
 * - Service dead → restart it ([StartOrigin.WATCHDOG]) — but only if it already started in
 *   this boot (Settings.Global.BOOT_COUNT, [BootStartRecord.serviceStartedThisBoot]), so
 *   switching off 开机自动启动 is never bypassed. Where Android 12+ refuses the background
 *   foreground start, a 「监测已停止，点按恢复」 notification lets the user restart it.
 * - Alive but no frame for [STALL_MS] on any active source → force a reconnect.
 *
 * Each action is written to the diagnostics log; run times feed 看门狗 in the metrics.
 * WorkManager keeps its database in credential-encrypted storage, so scheduling only
 * happens after the first unlock ([ensure] / [cancel] are no-ops before).
 */
class WatchdogWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as MyEarthQuakeAlertApp
        val now = System.currentTimeMillis()
        prefs(app).edit().putLong(KEY_LAST_RUN, now).putLong(KEY_NEXT_RUN, now + PERIOD_MS).apply()
        val settings = try {
            app.settingsRepository.settings.first()
        } catch (e: Exception) {
            Log.w(TAG, "settings unreadable", e)
            return Result.success()
        }
        if (!settings.serviceEnabled) {
            cancel(app)
            return Result.success()
        }
        if (!EewMonitorService.isMonitoring) {
            if (!BootStartRecord.serviceStartedThisBoot(app)) {
                // Never started in this boot: start on boot is off or the boot broadcast was blocked.
                return Result.success()
            }
            restart(app)
            return Result.success()
        }
        val state = MonitorStatus.state.value
        if (state.isActive) {
            val lastFrame = state.sources.mapNotNull { it.lastFrameAt }.maxOrNull()
            val stalled = state.sources.isNotEmpty() && (lastFrame == null || now - lastFrame > STALL_MS)
            if (stalled && (state.runningSince ?: now) < now - STALL_MS) {
                val seconds = lastFrame?.let { (now - it) / 1000 } ?: ((now - (state.runningSince ?: now)) / 1000)
                EewMonitorService.reconnect(app, app.getString(R.string.svc_log_watchdog_stall, seconds))
            }
        }
        return Result.success()
    }

    private fun restart(app: Context) {
        try {
            ContextCompat.startForegroundService(app, EewMonitorService.startIntent(app, StartOrigin.WATCHDOG))
            Diagnostics.log(HealthLevel.WARN, LogTags.WATCHDOG, app.getString(R.string.svc_log_watchdog_restart))
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException (Android 12+, no exemption).
            Diagnostics.log(HealthLevel.ERROR, LogTags.WATCHDOG, app.getString(R.string.svc_log_watchdog_denied))
            AppNotifications.showStoppedNotice(app)
        }
    }

    companion object {
        private const val TAG = "WatchdogWorker"
        private const val NAME = "eew_watchdog"
        private const val PREFS = "watchdog"
        private const val KEY_LAST_RUN = "last_run"
        private const val KEY_NEXT_RUN = "next_run"
        private const val PERIOD_MS = 15 * 60_000L
        /** No frame for this long on a live link is a stall (feeds beat every 60 s, we ping every 30 s). */
        const val STALL_MS = 3 * 60_000L

        /** Schedules the periodic check (kept if already scheduled). */
        fun ensure(context: Context) {
            if (!context.isUserUnlocked()) return
            try {
                val request = PeriodicWorkRequestBuilder<WatchdogWorker>(15, TimeUnit.MINUTES).build()
                WorkManager.getInstance(context.applicationContext)
                    .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
                val p = prefs(context)
                if (!p.contains(KEY_NEXT_RUN)) p.edit().putLong(KEY_NEXT_RUN, System.currentTimeMillis() + PERIOD_MS).apply()
            } catch (e: Exception) {
                Log.w(TAG, "cannot schedule", e)
            }
        }

        /** Cancels the check (monitoring switched off). */
        fun cancel(context: Context) {
            prefs(context).edit().remove(KEY_NEXT_RUN).apply()
            if (!context.isUserUnlocked()) return
            try {
                WorkManager.getInstance(context.applicationContext).cancelUniqueWork(NAME)
            } catch (e: Exception) {
                Log.w(TAG, "cannot cancel", e)
            }
        }

        /** Epoch millis of the last run, or null. */
        fun lastRunAt(context: Context): Long? = prefs(context).let { if (it.contains(KEY_LAST_RUN)) it.getLong(KEY_LAST_RUN, 0L) else null }

        /** Expected next run (WorkManager may run it later in Doze), or null when not scheduled. */
        fun nextRunAt(context: Context): Long? = prefs(context).let { if (it.contains(KEY_NEXT_RUN)) it.getLong(KEY_NEXT_RUN, 0L) else null }

        private fun prefs(context: Context): SharedPreferences =
            context.deviceProtectedContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }
}
