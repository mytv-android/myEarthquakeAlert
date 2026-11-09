package com.github.mytv.myearthquakealert.ui.overview

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.github.mytv.myearthquakealert.MyEarthQuakeAlertApp
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.domain.DrillScenario
import com.github.mytv.myearthquakealert.service.DeliveryChecks
import com.github.mytv.myearthquakealert.service.DeliveryRequirement
import com.github.mytv.myearthquakealert.service.EewMonitorService
import com.github.mytv.myearthquakealert.service.MonitorPhase
import com.github.mytv.myearthquakealert.service.MonitorStatus
import com.github.mytv.myearthquakealert.ui.adaptive.findActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Commands the UI sends to the monitoring service (design §3: the UI never detects, it
 * only shows state and sends START / STOP / PAUSE / RESUME / DRILL). Settings are written
 * first so the service — and a reboot — see the user's choice.
 */
@Stable
class MonitorActions internal constructor(
    private val context: Context,
    private val scope: CoroutineScope,
    private val app: MyEarthQuakeAlertApp?,
    private val requestNotifications: () -> Unit,
    private val requestLocation: (thenDrill: Boolean) -> Unit,
) {
    /**
     * 开启监测: allowed with missing permissions — the service then reports LIMITED. The
     * notification permission (needed for the alerts themselves) is asked for right away.
     */
    fun turnOn() {
        scope.launch {
            app?.settingsRepository?.updatePauseUntil(0L)
            app?.settingsRepository?.updateServiceEnabled(true)
            EewMonitorService.start(context)
        }
        if (Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS)) requestNotifications()
    }

    /** 完全关闭: no detection at all until switched on again. */
    fun turnOff() {
        scope.launch {
            app?.settingsRepository?.updateServiceEnabled(false)
            app?.settingsRepository?.updatePauseUntil(0L)
            EewMonitorService.stop(context)
        }
    }

    fun pause(untilEpochMillis: Long) {
        scope.launch {
            app?.settingsRepository?.updatePauseUntil(untilEpochMillis)
            EewMonitorService.pause(context, untilEpochMillis)
        }
    }

    fun resume() {
        scope.launch {
            app?.settingsRepository?.updatePauseUntil(0L)
            val running = MonitorStatus.state.value.phase == MonitorPhase.PAUSED
            if (!running || !EewMonitorService.resume(context)) EewMonitorService.start(context)
        }
    }

    fun setBootStart(enabled: Boolean) {
        scope.launch { app?.settingsRepository?.updateBootStart(enabled) }
    }

    /** 修复: the runtime prompt for LOCATION / NOTIFICATIONS, the system screen otherwise. */
    fun fix(requirement: DeliveryRequirement) {
        when {
            requirement == DeliveryRequirement.LOCATION -> requestLocation(false)
            requirement == DeliveryRequirement.NOTIFICATIONS && Build.VERSION.SDK_INT >= 33 &&
                !granted(Manifest.permission.POST_NOTIFICATIONS) -> requestNotifications()
            else -> if (!DeliveryChecks.fix(context, requirement)) openAppDetails(context)
        }
    }

    /**
     * 模拟演练: runs in the service through the real alert path. Needs the overlay (the
     * alert is drawn over other apps) and the location (distance and local intensity).
     */
    fun drill(scenario: DrillScenario.Scenario = DrillScenario.Scenario.LUSHAN) {
        if (!Settings.canDrawOverlays(context)) {
            Toast.makeText(context, context.getString(R.string.overlay_permission_required), Toast.LENGTH_LONG).show()
            DeliveryChecks.fix(context, DeliveryRequirement.OVERLAY)
            return
        }
        if (!granted(Manifest.permission.ACCESS_COARSE_LOCATION) && !granted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            requestLocation(true)
            return
        }
        if (!EewMonitorService.drill(context, scenario)) {
            Toast.makeText(context, context.getString(R.string.drill_failed), Toast.LENGTH_LONG).show()
        }
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

internal fun openAppDetails(context: Context) {
    try {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (_: Exception) {
        // No settings app (some TV boxes): nothing else to open.
    }
}

/**
 * [MonitorActions] bound to this composition's activity-result launchers.
 * [onLocationGranted] runs after the location permission is granted (to look the position up).
 */
@Composable
fun rememberMonitorActions(onLocationGranted: () -> Unit = {}): MonitorActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = context.applicationContext as? MyEarthQuakeAlertApp
    val onGranted by rememberUpdatedState(onLocationGranted)
    var drillAfterLocation by remember { mutableStateOf(false) }
    val activity = remember(context) { context.findActivity() }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        DeliveryChecks.refresh(context)
        val permanentlyDenied = !granted && Build.VERSION.SDK_INT >= 33 && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
        if (permanentlyDenied) DeliveryChecks.fix(context, DeliveryRequirement.NOTIFICATIONS)
    }

    var actions: MonitorActions? = null
    val locationRequiredText = stringResource(R.string.location_permission_required)
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        DeliveryChecks.refresh(context)
        val ok = result.values.any { it }
        if (ok) {
            onGranted()
            if (drillAfterLocation) actions?.drill()
        } else {
            Toast.makeText(context, locationRequiredText, Toast.LENGTH_LONG).show()
            val permanentlyDenied = activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_COARSE_LOCATION)
            if (permanentlyDenied && !drillAfterLocation) openAppDetails(context)
        }
        drillAfterLocation = false
    }

    val created = remember(context, scope, app) {
        MonitorActions(
            context = context,
            scope = scope,
            app = app,
            requestNotifications = {
                if (Build.VERSION.SDK_INT >= 33) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            requestLocation = { thenDrill ->
                drillAfterLocation = thenDrill
                locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            },
        )
    }
    actions = created
    return created
}
