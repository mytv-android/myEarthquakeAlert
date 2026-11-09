package com.github.mytv.myearthquakealert.wear

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.github.mytv.myearthquakealert.service.AppNotifications
import com.github.mytv.myearthquakealert.service.EewMonitorService
import com.github.mytv.myearthquakealert.service.MonitorPhase
import com.github.mytv.myearthquakealert.service.MonitorState
import com.github.mytv.myearthquakealert.service.MonitorStatus
import com.github.mytv.myearthquakealert.service.StartOrigin
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Remote control of the phone's monitoring from the watch (design §6 手表: EdgeButton 远程暂停
 * 手机端监测). Receives [WearPaths.CMD_PAUSE] / [WearPaths.CMD_RESUME] / [WearPaths.CMD_START],
 * runs them through [EewMonitorService] with origin [StartOrigin.WATCH] and answers the
 * sending watch with a [WireCommandResult].
 *
 * The callback runs on a Data Layer worker thread and may block, which keeps the binding
 * (and the process) alive until the command has been checked. Android 12+ may refuse to
 * start the monitoring service from the background: the watch is told so, and the phone
 * shows its "监测未启动" notice whose tap starts it.
 */
class WearCommandService : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        val path = event.path ?: return
        if (!path.startsWith(WearPaths.CMD_PREFIX)) return
        val result = try {
            runBlocking { execute(applicationContext, path, WearCodec.decodeCommand(event.data)) }
        } catch (e: Exception) {
            Log.w(TAG, "command $path failed", e)
            failure(path, WireCommandResult.ERR_FAILED)
        }
        Log.i(TAG, "command $path from ${event.sourceNodeId}: ok=${result.ok} ${result.error.orEmpty()}")
        WearBridge.reply(applicationContext, event.sourceNodeId, result)
    }

    private suspend fun execute(context: Context, path: String, command: WireCommand?): WireCommandResult {
        val now = MonitorStatus.state.value
        return when (path) {
            WearPaths.CMD_PAUSE -> pause(context, path, now, command?.durationMs ?: WearPaths.DEFAULT_PAUSE_MS)
            WearPaths.CMD_RESUME -> if (now.phase == MonitorPhase.OFF) start(context, path, now) else resume(context, path, now)
            WearPaths.CMD_START -> start(context, path, now)
            else -> failure(path, WireCommandResult.ERR_INVALID)
        }
    }

    private suspend fun pause(context: Context, path: String, state: MonitorState, requestedMs: Long): WireCommandResult {
        if (state.phase == MonitorPhase.OFF) return failure(path, WireCommandResult.ERR_OFF)
        val duration = requestedMs.coerceIn(MIN_PAUSE_MS, MAX_PAUSE_MS)
        val until = System.currentTimeMillis() + duration
        if (!EewMonitorService.pause(context, until)) return failure(path, WireCommandResult.ERR_BACKGROUND)
        val reached = awaitState { it.phase == MonitorPhase.PAUSED && it.pauseUntil >= until - 1_000L }
            ?: return failure(path, WireCommandResult.ERR_TIMEOUT)
        return success(path, reached)
    }

    private suspend fun resume(context: Context, path: String, state: MonitorState): WireCommandResult {
        if (state.phase != MonitorPhase.PAUSED) return success(path, state)
        val intent = EewMonitorService.commandIntent(context, EewMonitorService.ACTION_RESUME)
            .putExtra(EewMonitorService.EXTRA_ORIGIN, StartOrigin.WATCH.name)
        if (!startService(context, intent)) return failure(path, WireCommandResult.ERR_BACKGROUND)
        val reached = awaitState { it.phase != MonitorPhase.PAUSED }
            ?: return failure(path, WireCommandResult.ERR_TIMEOUT)
        return if (reached.phase == MonitorPhase.OFF) failure(path, WireCommandResult.ERR_OFF) else success(path, reached)
    }

    private suspend fun start(context: Context, path: String, state: MonitorState): WireCommandResult {
        if (state.phase != MonitorPhase.OFF) {
            return if (state.phase == MonitorPhase.PAUSED) resume(context, path, state) else success(path, state)
        }
        try {
            ContextCompat.startForegroundService(context, EewMonitorService.startIntent(context, StartOrigin.WATCH))
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException: a tap on the phone's notice may start it.
            Log.w(TAG, "background start refused", e)
            AppNotifications.showStoppedNotice(context)
            return failure(path, WireCommandResult.ERR_BACKGROUND)
        } catch (e: SecurityException) {
            Log.w(TAG, "start refused", e)
            return failure(path, WireCommandResult.ERR_FAILED)
        }
        // The service may still be refused the foreground (it then posts the notice itself).
        val reached = awaitState { it.phase != MonitorPhase.OFF }
            ?: return failure(path, WireCommandResult.ERR_BACKGROUND)
        return success(path, reached)
    }

    private fun startService(context: Context, intent: Intent): Boolean = try {
        context.startService(intent) != null
    } catch (e: IllegalStateException) {
        false
    } catch (e: SecurityException) {
        false
    }

    private suspend fun awaitState(predicate: (MonitorState) -> Boolean): MonitorState? =
        withTimeoutOrNull(AWAIT_MS) { MonitorStatus.state.first(predicate) }

    private fun success(path: String, state: MonitorState) = WireCommandResult(
        command = path,
        ok = true,
        phase = state.phase.name,
        pauseUntil = state.pauseUntil,
        sentAt = System.currentTimeMillis(),
    )

    private fun failure(path: String, error: String) = WireCommandResult(
        command = path,
        ok = false,
        error = error,
        phase = MonitorStatus.state.value.phase.name,
        pauseUntil = MonitorStatus.state.value.pauseUntil,
        sentAt = System.currentTimeMillis(),
    )

    private companion object {
        const val TAG = "WearCommandService"
        const val AWAIT_MS = 6_000L
        const val MIN_PAUSE_MS = 60_000L
        const val MAX_PAUSE_MS = 24 * 3_600_000L
    }
}
