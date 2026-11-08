package com.github.mytv.myearthquakealert.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.Stable
import com.github.mytv.myearthquakealert.MyEarthQuakeAlertApp
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.data.repository.SettingsRepository
import com.github.mytv.myearthquakealert.diagnostics.DiagnosticsExport
import com.github.mytv.myearthquakealert.domain.DrillScenario
import com.github.mytv.myearthquakealert.service.DeliveryChecks
import com.github.mytv.myearthquakealert.service.DeliveryRequirement
import com.github.mytv.myearthquakealert.service.EewMonitorService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.Calendar

/** Settings sub-pages; [key] is what `SettingsScreen(openPage = …)` accepts. */
internal enum class SettingsPage(val key: String) {
    MONITOR("monitor"),
    BOOT("boot"),
    SOURCES("sources"),
    ALERTS("alerts"),
    LOCATION("location"),
    LAYOUT("layout"),
    UPDATE("update"),
    ABOUT("about");

    companion object {
        fun fromKey(key: String?): SettingsPage? = entries.firstOrNull { it.key == key }
    }
}

/**
 * Commands the settings pages send. Settings are written to [SettingsRepository]; the
 * monitoring service is only driven through its companion commands — the UI never detects.
 */
@Stable
internal class SettingsActions(
    private val context: Context,
    private val scope: CoroutineScope,
    private val requestNotificationPermission: () -> Unit,
    private val requestLocationPermission: () -> Unit,
) {
    private val app = context.applicationContext as MyEarthQuakeAlertApp
    val repository: SettingsRepository get() = app.settingsRepository

    fun edit(block: suspend SettingsRepository.() -> Unit) {
        scope.launch { repository.block() }
    }

    /** 后台持续监测 on. Missing optional permissions only put the service in LIMITED. */
    fun startMonitoring() {
        if (DeliveryRequirement.NOTIFICATIONS in DeliveryChecks.missing(context)) requestNotificationPermission()
        scope.launch {
            repository.updatePauseUntil(0L)
            repository.updateServiceEnabled(true)
            try {
                EewMonitorService.start(context)
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Cannot start monitoring", e)
                toast(R.string.pg_start_failed)
            }
        }
    }

    /** 完全关闭: no detection until the switch is turned on again. */
    fun stopMonitoring() {
        scope.launch {
            repository.updateServiceEnabled(false)
            repository.updatePauseUntil(0L)
            EewMonitorService.stop(context)
        }
    }

    /** Pauses until [untilMs]; the service schedules the automatic resume. */
    fun pause(untilMs: Long) {
        scope.launch {
            repository.updatePauseUntil(untilMs)
            if (!EewMonitorService.pause(context, untilMs)) toast(R.string.pg_command_failed)
        }
    }

    fun resume() {
        scope.launch {
            repository.updatePauseUntil(0L)
            if (!EewMonitorService.resume(context)) toast(R.string.pg_command_failed)
        }
    }

    fun drill(scenario: DrillScenario.Scenario) {
        if (!Settings.canDrawOverlays(context)) {
            toast(R.string.overlay_permission_required)
            DeliveryChecks.fix(context, DeliveryRequirement.OVERLAY)
            return
        }
        if (!EewMonitorService.drill(context, scenario)) toast(R.string.drill_failed)
    }

    fun exportDiagnostics() {
        try {
            DiagnosticsExport.share(context)
        } catch (e: Exception) {
            Log.w(TAG, "Diagnostics export failed", e)
            toast(R.string.pg_export_failed)
        }
    }

    /** Opens the screen that fixes [requirement]; runtime permissions are requested in place. */
    fun fix(requirement: DeliveryRequirement) {
        when (requirement) {
            DeliveryRequirement.LOCATION -> requestLocationPermission()
            DeliveryRequirement.NOTIFICATIONS -> if (!DeliveryChecks.fix(context, requirement)) requestNotificationPermission()
            else -> if (!DeliveryChecks.fix(context, requirement)) toast(R.string.pg_open_settings_failed)
        }
    }

    fun openUrl(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            toast(R.string.pg_no_browser)
        }
    }

    fun toast(message: Int) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val TAG = "SettingsActions"

        /** 暂停 1 小时. */
        fun oneHourFrom(now: Long): Long = now + 60 * 60 * 1000L

        /** 暂停到明天 08:00 (today 08:00 when it is still before then). */
        fun nextMorning(now: Long): Long {
            val cal = Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, 8)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_YEAR, 1)
            return cal.timeInMillis
        }
    }
}
