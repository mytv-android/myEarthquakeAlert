package com.github.mytv.myearthquakealert.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.github.mytv.myearthquakealert.MyEarthQuakeAlertApp
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.data.repository.SettingsStore
import com.github.mytv.myearthquakealert.diagnostics.Diagnostics
import com.github.mytv.myearthquakealert.diagnostics.HealthLevel
import com.github.mytv.myearthquakealert.diagnostics.LogTags
import com.github.mytv.myearthquakealert.service.AppNotifications
import com.github.mytv.myearthquakealert.service.EewMonitorService
import com.github.mytv.myearthquakealert.service.StartOrigin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Starts the monitoring service straight from system broadcasts, without any UI
 * (design §3 开机自动启动, the restart table):
 *
 * | 后台监测 | 开机自动启动 | after a reboot |
 * |---------|------------|----------------|
 * | on      | on         | start the service right away (before the first unlock too) |
 * | on      | off        | do not start (nor will the watchdog); post 「地震监测未启动」 with 立即启动 |
 * | paused  | on         | start it in PAUSED: links stay closed, the resume alarm (cleared by the reboot) is re-armed; an expired pause starts monitoring at once |
 * | paused  | off        | as on / off |
 *
 * A paused service still runs, in the foreground, so the resume at the end of the pause
 * does not need a background foreground-service start (refused for inexact alarms on
 * Android 12+); the boot broadcast is exempt from that limit.
 *
 * App update (MY_PACKAGE_REPLACED): restores monitoring if it was on (paused or not). This
 * is not a boot, so the start-on-boot switch does not apply.
 *
 * Several boot broadcasts may arrive (locked boot, boot, quick boot); starting is idempotent.
 */
class BootReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val isBoot = action in BOOT_ACTIONS
        if (!isBoot && action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        // Before the first unlock only device-protected storage is readable. Without a
        // settings file there yet, wait for BOOT_COMPLETED rather than read defaults.
        if (!SettingsStore.isReadable(context)) {
            Log.i(TAG, "$action: settings not readable before unlock, waiting for BOOT_COMPLETED")
            return
        }

        val pendingResult = goAsync()
        scope.launch {
            try {
                val app = context.applicationContext as MyEarthQuakeAlertApp
                val settings = app.settingsRepository.settings.first()
                val paused = settings.isPaused()
                when {
                    !settings.serviceEnabled -> Unit
                    !isBoot -> {
                        EewMonitorService.start(context, StartOrigin.PACKAGE_REPLACED)
                        log(context, R.string.svc_log_boot_update)
                    }
                    settings.bootStart -> {
                        if (EewMonitorService.isMonitoring) return@launch // second boot broadcast
                        EewMonitorService.start(context, StartOrigin.BOOT)
                        log(context, if (paused) R.string.svc_log_boot_paused else R.string.svc_log_boot_start)
                    }
                    else -> {
                        AppNotifications.showBootNotice(context)
                        log(context, R.string.svc_log_boot_notice)
                    }
                }
                Log.i(TAG, "$action: monitoring=${settings.serviceEnabled} bootStart=${settings.bootStart} paused=$paused")
            } catch (e: Exception) {
                // e.g. ForegroundServiceStartNotAllowedException for non-exempt broadcasts.
                Log.e(TAG, "$action: failed to start monitoring", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun log(context: Context, message: Int) {
        Diagnostics.log(HealthLevel.INFO, LogTags.BOOT, context.getString(message))
    }

    private companion object {
        const val TAG = "BootReceiver"
        val BOOT_ACTIONS = setOf(
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
        )
    }
}
