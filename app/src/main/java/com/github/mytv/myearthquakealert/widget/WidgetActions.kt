package com.github.mytv.myearthquakealert.widget

import android.app.KeyguardManager
import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.github.mytv.myearthquakealert.service.EewMonitorService
import com.github.mytv.myearthquakealert.service.StartOrigin

private const val TAG = "WidgetActions"
private const val HOUR_MS = 3_600_000L

/**
 * 暂停 1 小时. A widget tap lets the app start or command its foreground service; failures are
 * logged and the widget simply keeps showing the real state. Refused while the device is
 * locked (lock-screen widget hosts never show the button, this is the second guard).
 */
class PauseHourAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        if (context.getSystemService(KeyguardManager::class.java)?.isDeviceLocked == true) {
            Log.i(TAG, "pause refused: device locked")
            return
        }
        runCommand("pause") { EewMonitorService.pauseFor(context, HOUR_MS) }
        WidgetUpdater.requestUpdate()
    }
}

/** 恢复: ends the pause; if the service is gone, starts monitoring (a user start clears the pause). */
class ResumeAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        runCommand("resume") {
            EewMonitorService.resume(context) || run {
                EewMonitorService.start(context, StartOrigin.USER)
                true
            }
        }
        WidgetUpdater.requestUpdate()
    }
}

/** 开启: switches monitoring on, as the overview switch does. */
class TurnOnAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        runCommand("start") {
            EewMonitorService.start(context, StartOrigin.USER)
            true
        }
        WidgetUpdater.requestUpdate()
    }
}

private inline fun runCommand(name: String, command: () -> Boolean) {
    try {
        if (!command()) Log.w(TAG, "$name: the service did not accept the command")
    } catch (e: Exception) {
        Log.w(TAG, "$name failed", e)
    }
}
