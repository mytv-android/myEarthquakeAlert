package com.github.mytv.myearthquakealert.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CrisisAlert
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.KeyboardReturn
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.mytv.myearthquakealert.BuildConfig
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.service.BootStartRecord
import com.github.mytv.myearthquakealert.service.DeliveryRequirement
import com.github.mytv.myearthquakealert.ui.about.AboutViewModel
import com.github.mytv.myearthquakealert.ui.adaptive.SessionLayout
import com.github.mytv.myearthquakealert.ui.components.SegmentedGroup
import com.github.mytv.myearthquakealert.ui.components.Subhead
import com.github.mytv.myearthquakealert.ui.settings.components.NavRow
import com.github.mytv.myearthquakealert.ui.settings.components.NavSwitchRow
import com.github.mytv.myearthquakealert.ui.settings.components.Pill
import com.github.mytv.myearthquakealert.ui.settings.components.SettingsList
import com.github.mytv.myearthquakealert.ui.settings.components.SwitchRow
import com.github.mytv.myearthquakealert.ui.settings.components.displayPhase

/** Requirements the 开启后台监测 check lists; turning monitoring on asks first when one is missing. */
internal val StartCheckRequirements = listOf(
    DeliveryRequirement.NOTIFICATIONS,
    DeliveryRequirement.OVERLAY,
    DeliveryRequirement.FULL_SCREEN_INTENT,
    DeliveryRequirement.BATTERY_OPTIMIZATION,
    DeliveryRequirement.LOCATION,
)

/** 后台持续监测 switch: on asks the start check when something is missing; off offers pause first. */
internal fun SettingsEnv.onMonitorSwitch(on: Boolean, onShowDialog: (SettingsDialog) -> Unit) {
    when {
        !on -> onShowDialog(SettingsDialog.PAUSE_OR_OFF)
        settings.serviceEnabled && pauseUntil > now -> actions.resume()
        StartCheckRequirements.any { it in missing } -> onShowDialog(SettingsDialog.START_CHECK)
        else -> actions.startMonitoring()
    }
}

/** 设置 root list (prototype scrSettings). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsRoot(
    env: SettingsEnv,
    selected: SettingsPage?,
    onOpen: (SettingsPage) -> Unit,
    onOpenDiagnostics: () -> Unit,
    onShowDialog: (SettingsDialog) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.tab_settings)) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        SettingsList(contentPadding = padding) {
            item(key = "monitor") { MonitorGroup(env, selected, onOpen, onShowDialog) }
            item(key = "alerts") { AlertGroup(env, selected, onOpen) }
            item(key = "display") { DisplayGroup(env, selected, onOpen, onShowDialog) }
            item(key = "location") { LocationGroup(env, selected, onOpen) }
            item(key = "tools") { ToolsGroup(env, onOpenDiagnostics, onShowDialog) }
            item(key = "about") { AboutGroup(env, selected, onOpen) }
        }
    }
}

@Composable
private fun MonitorGroup(env: SettingsEnv, selected: SettingsPage?, onOpen: (SettingsPage) -> Unit, onShowDialog: (SettingsDialog) -> Unit) {
    val settings = env.settings
    val formats = env.formats
    val context = LocalContext.current
    val phase = displayPhase(env.monitor, settings, env.now)
    val bootDelay = remember(env.monitor.runningSince, env.metrics.bootStartDelaySec) {
        env.metrics.bootStartDelaySec ?: BootStartRecord.delaySecondsThisBoot(context)
    }
    val paused = env.pauseUntil > env.now
    Column {
        Subhead(stringResource(R.string.pg_group_monitor))
        SegmentedGroup(4) { index, shape ->
            when (index) {
                0 -> NavSwitchRow(
                    title = stringResource(R.string.pg_monitor),
                    shape = shape,
                    icon = Icons.Filled.Sensors,
                    subtitle = formats.phaseShort(phase, env.pauseUntil, env.now),
                    checked = settings.serviceEnabled,
                    onCheckedChange = { env.onMonitorSwitch(it, onShowDialog) },
                    onClick = { onOpen(SettingsPage.MONITOR) },
                    selected = selected == SettingsPage.MONITOR,
                )
                1 -> NavSwitchRow(
                    title = stringResource(R.string.boot_start),
                    shape = shape,
                    icon = Icons.Filled.RestartAlt,
                    subtitle = bootSubtitle(settings, bootDelay),
                    checked = settings.bootStart && settings.serviceEnabled,
                    onCheckedChange = { on -> env.actions.edit { updateBootStart(on) } },
                    onClick = { onOpen(SettingsPage.BOOT) },
                    selected = selected == SettingsPage.BOOT,
                    switchEnabled = settings.serviceEnabled,
                )
                2 -> NavRow(
                    title = stringResource(R.string.pg_sources),
                    shape = shape,
                    icon = Icons.Filled.Public,
                    subtitle = formats.sourcesSummary(settings.enabledSources),
                    onClick = { onOpen(SettingsPage.SOURCES) },
                    selected = selected == SettingsPage.SOURCES,
                )
                else -> NavRow(
                    title = stringResource(R.string.pg_pause),
                    shape = shape,
                    icon = Icons.Filled.Pause,
                    subtitle = when {
                        !settings.serviceEnabled -> stringResource(R.string.boot_start_unavailable)
                        paused -> stringResource(R.string.pg_pause_until_short, formats.clockWithDay(env.pauseUntil, env.now))
                        else -> stringResource(R.string.pg_not_paused)
                    },
                    enabled = settings.serviceEnabled,
                    onClick = { onShowDialog(SettingsDialog.PAUSE) },
                    trailing = null,
                )
            }
        }
    }
}

@Composable
private fun AlertGroup(env: SettingsEnv, selected: SettingsPage?, onOpen: (SettingsPage) -> Unit) {
    val settings = env.settings
    val actions = env.actions
    val dndMissing = DeliveryRequirement.DND_ACCESS in env.missing
    Column {
        Subhead(stringResource(R.string.pg_group_alerts))
        SegmentedGroup(5) { index, shape ->
            when (index) {
                0 -> NavRow(
                    title = stringResource(R.string.pg_alert_thresholds),
                    shape = shape,
                    icon = Icons.Filled.Tune,
                    subtitle = alertThresholdSummary(settings),
                    onClick = { onOpen(SettingsPage.ALERTS) },
                    selected = selected == SettingsPage.ALERTS,
                )
                1 -> NavRow(
                    title = stringResource(R.string.pg_strong_threshold),
                    shape = shape,
                    icon = Icons.Filled.CrisisAlert,
                    subtitle = stringResource(R.string.pg_strong_summary, settings.intenseThreshold),
                    onClick = { onOpen(SettingsPage.ALERTS) },
                )
                2 -> SwitchRow(
                    title = stringResource(R.string.pg_bypass_dnd),
                    shape = shape,
                    icon = Icons.Filled.DoNotDisturbOn,
                    subtitle = stringResource(if (settings.alarmBypassDnd && dndMissing) R.string.pg_bypass_dnd_needs else R.string.pg_bypass_dnd_desc),
                    checked = settings.alarmBypassDnd,
                    onCheckedChange = { on ->
                        actions.edit { updateAlarmBypassDnd(on) }
                        if (on && dndMissing) actions.fix(DeliveryRequirement.DND_ACCESS)
                    },
                )
                3 -> SwitchRow(
                    title = stringResource(R.string.pg_tts),
                    shape = shape,
                    icon = Icons.Filled.GraphicEq,
                    subtitle = stringResource(R.string.pg_tts_desc),
                    checked = settings.ttsEnabled,
                    onCheckedChange = { on -> actions.edit { updateTtsEnabled(on) } },
                )
                else -> SwitchRow(
                    title = stringResource(R.string.pg_back_dismiss),
                    shape = shape,
                    icon = Icons.Filled.KeyboardReturn,
                    subtitle = stringResource(R.string.pg_back_dismiss_desc),
                    checked = settings.allowDismissWithBack,
                    onCheckedChange = { on -> actions.edit { updateAllowDismissWithBack(on) } },
                )
            }
        }
    }
}

@Composable
private fun DisplayGroup(env: SettingsEnv, selected: SettingsPage?, onOpen: (SettingsPage) -> Unit, onShowDialog: (SettingsDialog) -> Unit) {
    val settings = env.settings
    val session by SessionLayout.override.collectAsStateWithLifecycle()
    Column {
        Subhead(stringResource(R.string.pg_group_display))
        SegmentedGroup(2) { index, shape ->
            when (index) {
                0 -> NavRow(
                    title = stringResource(R.string.pg_theme),
                    shape = shape,
                    icon = Icons.Filled.DarkMode,
                    subtitle = themeLabel(settings.themeMode),
                    onClick = { onShowDialog(SettingsDialog.THEME) },
                    trailing = null,
                )
                else -> NavRow(
                    title = stringResource(R.string.pg_layout),
                    shape = shape,
                    icon = Icons.Filled.Dashboard,
                    subtitle = layoutSummary(settings, env.layout, session),
                    onClick = { onOpen(SettingsPage.LAYOUT) },
                    selected = selected == SettingsPage.LAYOUT,
                )
            }
        }
        // 地图样式 and 离线地图 as their own block of the group.
        Spacer(Modifier.height(12.dp))
        MapRows(env, onShowDialog)
    }
}

@Composable
private fun LocationGroup(env: SettingsEnv, selected: SettingsPage?, onOpen: (SettingsPage) -> Unit) {
    Column {
        Subhead(stringResource(R.string.pg_group_location))
        SegmentedGroup(1) { _, shape ->
            NavRow(
                title = stringResource(R.string.pg_location_source),
                shape = shape,
                icon = Icons.Filled.MyLocation,
                subtitle = locationSummary(env.settings, env.metrics),
                onClick = { onOpen(SettingsPage.LOCATION) },
                selected = selected == SettingsPage.LOCATION,
            )
        }
    }
}

@Composable
private fun ToolsGroup(env: SettingsEnv, onOpenDiagnostics: () -> Unit, onShowDialog: (SettingsDialog) -> Unit) {
    val phase = displayPhase(env.monitor, env.settings, env.now)
    Column {
        Subhead(stringResource(R.string.pg_group_tools))
        SegmentedGroup(3) { index, shape ->
            when (index) {
                0 -> NavRow(
                    title = stringResource(R.string.pg_drill_title),
                    shape = shape,
                    icon = Icons.Filled.Science,
                    subtitle = stringResource(R.string.pg_drill_summary),
                    onClick = { onShowDialog(SettingsDialog.DRILL) },
                    trailing = null,
                )
                1 -> NavRow(
                    title = stringResource(R.string.pg_diagnostics),
                    shape = shape,
                    icon = Icons.Filled.MonitorHeart,
                    subtitle = diagnosticsSummary(phase, env.formats.phaseShort(phase, env.pauseUntil, env.now)),
                    onClick = onOpenDiagnostics,
                )
                else -> NavRow(
                    title = stringResource(R.string.pg_export),
                    shape = shape,
                    icon = Icons.Filled.IosShare,
                    subtitle = stringResource(R.string.pg_export_desc),
                    onClick = env.actions::exportDiagnostics,
                    trailing = null,
                )
            }
        }
    }
}

@Composable
private fun AboutGroup(env: SettingsEnv, selected: SettingsPage?, onOpen: (SettingsPage) -> Unit) {
    val state by env.updateViewModel.updateState.collectAsStateWithLifecycle()
    val known by env.updateViewModel.knownUpdate.collectAsStateWithLifecycle()
    val newVersion = (state as? AboutViewModel.UpdateState.Available)?.updateInfo?.versionName ?: known?.versionName
    Column {
        Subhead(stringResource(R.string.pg_group_about))
        SegmentedGroup(2) { index, shape ->
            if (index == 0) {
                NavRow(
                    title = stringResource(R.string.pg_about_update),
                    shape = shape,
                    icon = Icons.Filled.SystemUpdate,
                    subtitle = if (newVersion != null) {
                        stringResource(R.string.pg_about_version_new, BuildConfig.VERSION_NAME, newVersion)
                    } else {
                        stringResource(R.string.pg_about_version_current, BuildConfig.VERSION_NAME)
                    },
                    onClick = { onOpen(SettingsPage.UPDATE) },
                    selected = selected == SettingsPage.UPDATE,
                    badge = if (newVersion != null) {
                        {
                            Pill(
                                stringResource(R.string.pg_update_badge),
                                MaterialTheme.colorScheme.errorContainer,
                                MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    } else {
                        null
                    },
                )
            } else {
                NavRow(
                    title = stringResource(R.string.pg_about_licenses_sources),
                    shape = shape,
                    icon = Icons.Filled.Info,
                    subtitle = stringResource(R.string.pg_about_licenses_desc),
                    onClick = { onOpen(SettingsPage.ABOUT) },
                    selected = selected == SettingsPage.ABOUT,
                )
            }
        }
    }
}
