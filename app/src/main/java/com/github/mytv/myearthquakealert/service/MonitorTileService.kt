package com.github.mytv.myearthquakealert.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.github.mytv.myearthquakealert.MainActivity
import com.github.mytv.myearthquakealert.MyEarthQuakeAlertApp
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.ui.navigation.Destination
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 快捷设置磁贴 (design §3: 通知栏与快捷设置磁贴一键开关). Active while monitoring is on; the
 * subtitle carries the phase in the same words as every other surface. A tap switches
 * monitoring on, or off (ending a pause counts as on). Stopping from the lock screen asks
 * for an unlock first. Android 7.0+.
 */
@RequiresApi(Build.VERSION_CODES.N)
class MonitorTileService : TileService() {

    private var scope: CoroutineScope? = null
    private var observer: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = s
        observer = s.launch { MonitorStatus.state.collect { render(it) } }
    }

    override fun onStopListening() {
        observer?.cancel()
        scope?.cancel()
        scope = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val state = MonitorStatus.state.value
        when {
            state.phase == MonitorPhase.PAUSED -> EewMonitorService.resume(this)
            state.phase != MonitorPhase.OFF -> if (isLocked) unlockAndRun { stopMonitoring() } else stopMonitoring()
            else -> startMonitoring()
        }
    }

    private fun stopMonitoring() {
        EewMonitorService.stop(this)
        val app = applicationContext as MyEarthQuakeAlertApp
        app.appScope.launch { app.settingsRepository.updateServiceEnabled(false) }
    }

    /**
     * A tile tap is a user action, which normally allows the foreground start; where the
     * system still refuses it, the app opens on the overview instead.
     */
    private fun startMonitoring() {
        try {
            ContextCompat.startForegroundService(this, EewMonitorService.startIntent(this, StartOrigin.TILE))
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Tile start refused, opening the app", e)
            openApp()
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .putExtra(Destination.EXTRA, Destination.OVERVIEW.route)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun render(state: MonitorState) {
        val tile = qsTile ?: return
        tile.state = if (state.phase == MonitorPhase.OFF) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        tile.label = getString(R.string.svc_tile_label)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_svc_tile)
        val subtitle = when (state.phase) {
            MonitorPhase.OFF -> getString(R.string.svc_tile_off)
            MonitorPhase.CONNECTING -> getString(R.string.svc_phase_connecting)
            MonitorPhase.MONITORING -> getString(R.string.svc_phase_monitoring)
            MonitorPhase.RECONNECTING -> getString(R.string.svc_phase_reconnecting)
            MonitorPhase.DEGRADED -> getString(R.string.svc_phase_degraded)
            MonitorPhase.LIMITED -> getString(R.string.svc_phase_limited)
            MonitorPhase.PAUSED -> getString(
                R.string.svc_phase_paused,
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(state.pauseUntil)),
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = subtitle
        } else {
            tile.contentDescription = "${tile.label} · $subtitle"
        }
        tile.updateTile()
    }

    private companion object {
        const val TAG = "MonitorTileService"
    }
}
