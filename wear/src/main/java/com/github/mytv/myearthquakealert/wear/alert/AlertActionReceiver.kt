package com.github.mytv.myearthquakealert.wear.alert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** The 我已安全 action of the alert notification. */
class AlertActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_SAFE) WatchAlerts.dismiss(context)
    }

    companion object {
        const val ACTION_SAFE = "com.github.mytv.myearthquakealert.wear.action.SAFE"
    }
}
