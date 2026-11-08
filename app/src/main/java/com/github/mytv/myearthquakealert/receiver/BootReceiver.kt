package com.github.mytv.myearthquakealert.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.github.mytv.myearthquakealert.MyEarthQuakeAlertApp
import com.github.mytv.myearthquakealert.data.repository.SettingsStore
import com.github.mytv.myearthquakealert.service.AppNotifications
import com.github.mytv.myearthquakealert.service.BootStartRecord
import com.github.mytv.myearthquakealert.service.EewMonitorService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Starts the monitoring service straight from system broadcasts, without any UI.
 *
 * - Boot (LOCKED_BOOT_COMPLETED before unlock, then BOOT_COMPLETED / QUICKBOOT_POWERON):
 *   starts only when both "Background monitoring" and "Start on boot" are on. With start
 *   on boot off, posts a "monitoring is not running" notice instead.
 * - App update (MY_PACKAGE_REPLACED): restores monitoring if it was on. This is not a
 *   boot, so the start-on-boot switch does not apply.
 *
 * Several boot broadcasts may arrive; starting is idempotent.
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
                when {
                    !settings.serviceEnabled -> Unit
                    !isBoot -> EewMonitorService.start(context)
                    settings.bootStart -> {
                        EewMonitorService.start(context)
                        BootStartRecord.recordAutoStart(context)
                    }
                    else -> AppNotifications.showBootNotice(context)
                }
                Log.i(TAG, "$action: monitoring=${settings.serviceEnabled} bootStart=${settings.bootStart}")
            } catch (e: Exception) {
                // e.g. ForegroundServiceStartNotAllowedException for non-exempt broadcasts.
                Log.e(TAG, "$action: failed to start monitoring", e)
            } finally {
                pendingResult.finish()
            }
        }
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
