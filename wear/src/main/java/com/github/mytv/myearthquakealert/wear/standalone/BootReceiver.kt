package com.github.mytv.myearthquakealert.wear.standalone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.github.mytv.myearthquakealert.wear.data.WatchSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Restarts 独立监测 after a reboot or an app update when the setting is on. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (WatchSettings.current(app).standaloneEnabled) StandaloneMonitorService.start(app)
            } finally {
                pending.finish()
            }
        }
    }
}
