package com.github.mytv.myearthquakealert.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.github.mytv.myearthquakealert.MyEarthQuakeAlertApp
import com.github.mytv.myearthquakealert.data.repository.SettingsRepository
import com.github.mytv.myearthquakealert.service.EewMonitorService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON") {
            return
        }

        scope.launch {
            val app = context.applicationContext as MyEarthQuakeAlertApp
            val settings = app.settingsRepository.settings.first()
            if (settings.serviceEnabled) {
                EewMonitorService.start(context)
            }
        }
    }
}
