package com.github.mytv.myearthquakealert.ui.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.NotificationsPaused
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.data.model.LocationMode
import com.github.mytv.myearthquakealert.service.BootStartRecord
import com.github.mytv.myearthquakealert.service.DeliveryRequirement
import com.github.mytv.myearthquakealert.service.LinkState
import com.github.mytv.myearthquakealert.service.MonitorPhase
import com.github.mytv.myearthquakealert.service.StartOrigin
import com.github.mytv.myearthquakealert.ui.components.SegmentedGroup
import com.github.mytv.myearthquakealert.ui.components.SegmentedRow
import com.github.mytv.myearthquakealert.ui.components.Subhead
import com.github.mytv.myearthquakealert.ui.settings.components.InfoNote
import com.github.mytv.myearthquakealert.ui.settings.components.InfoRow
import com.github.mytv.myearthquakealert.ui.settings.components.MetaPill
import com.github.mytv.myearthquakealert.ui.settings.components.MetaPills
import com.github.mytv.myearthquakealert.ui.settings.components.MonitorCard
import com.github.mytv.myearthquakealert.ui.settings.components.NavRow
import com.github.mytv.myearthquakealert.ui.settings.components.NavSwitchRow
import com.github.mytv.myearthquakealert.ui.settings.components.Pill
import com.github.mytv.myearthquakealert.ui.settings.components.SettingsPageScaffold
import com.github.mytv.myearthquakealert.ui.settings.components.SwitchRow
import com.github.mytv.myearthquakealert.ui.settings.components.displayPhase
import com.github.mytv.myearthquakealert.ui.settings.components.focusHighlight
import com.github.mytv.myearthquakealert.ui.settings.components.originIcon
import com.github.mytv.myearthquakealert.ui.settings.components.selectedTint
import com.github.mytv.myearthquakealert.ui.settings.components.startHistory
import com.github.mytv.myearthquakealert.ui.theme.status
import com.github.mytv.myearthquakealert.util.AutoStartSettings
import java.util.Locale

/** Seconds from boot to the automatic start in this boot, from the service metrics or the record. */
@Composable
private fun rememberBootDelay(env: SettingsEnv): Long? {
    val context = LocalContext.current
    return remember(env.monitor.runningSince, env.metrics.bootStartDelaySec) {
        env.metrics.bootStartDelaySec ?: BootStartRecord.delaySecondsThisBoot(context)
    }
}

private fun manufacturerName(): String =
    Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }

/** Requirements listed under 送达检查, in the order of the prototype's start sheet. */
private fun deliveryItems(env: SettingsEnv): List<DeliveryRequirement> = buildList {
    add(DeliveryRequirement.NOTIFICATIONS)
    add(DeliveryRequirement.OVERLAY)
    if (Build.VERSION.SDK_INT >= 34) add(DeliveryRequirement.FULL_SCREEN_INTENT)
    add(DeliveryRequirement.BATTERY_OPTIMIZATION)
    if (env.settings.locationMode == LocationMode.AUTO) add(DeliveryRequirement.LOCATION)
    if (env.settings.alarmBypassDnd) add(DeliveryRequirement.DND_ACCESS)
}

/** 设置 › 后台监测 (prototype scrMonSet). */
@Composable
internal fun MonitorPage(
    env: SettingsEnv,
    onBack: (() -> Unit)?,
    onOpenPage: (SettingsPage) -> Unit,
    onOpenDiagnostics: () -> Unit,
    onShowDialog: (SettingsDialog) -> Unit,
) {
    val settings = env.settings
    val formats = env.formats
    val now = env.now
    val phase = displayPhase(env.monitor, settings, now)
    val bootDelay = rememberBootDelay(env)
    val restricted = remember { AutoStartSettings.isRestricted() }
    val starts = remember(env.log) { startHistory(env.log) }
    val delivery = deliveryItems(env)
    val pending = delivery.count { it in env.missing }
    val paused = env.pauseUntil > now

    SettingsPageScaffold(title = stringResource(R.string.pg_monitor_page), onBack = onBack) {
        item(key = "card") {
            MonitorCard(
                phase = phase,
                title = formats.phaseTitle(phase, env.pauseUntil, now),
                subtitle = formats.phaseSubtitle(phase, env.monitor, settings, now),
                checked = settings.serviceEnabled,
                onCheckedChange = { env.onMonitorSwitch(it, onShowDialog) },
                shapeSize = 50.dp,
                meta = { MonitorMeta(env, phase) },
            )
        }
        item(key = "start") {
            Column {
                Subhead(stringResource(R.string.pg_group_start))
                SegmentedGroup(3) { index, shape ->
                    when (index) {
                        0 -> NavSwitchRow(
                            title = stringResource(R.string.boot_start),
                            shape = shape,
                            icon = Icons.Filled.RestartAlt,
                            subtitle = bootSubtitle(settings, bootDelay),
                            checked = settings.bootStart && settings.serviceEnabled,
                            onCheckedChange = { on -> env.actions.edit { updateBootStart(on) } },
                            onClick = { onOpenPage(SettingsPage.BOOT) },
                            switchEnabled = settings.serviceEnabled,
                        )
                        1 -> NavRow(
                            title = stringResource(R.string.pg_direct_boot),
                            shape = shape,
                            icon = Icons.Filled.LockOpen,
                            subtitle = stringResource(R.string.pg_direct_boot_desc),
                            onClick = { onOpenPage(SettingsPage.BOOT) },
                            trailing = { OkIcon() },
                        )
                        else -> NavRow(
                            title = stringResource(R.string.pg_autostart_perm),
                            shape = shape,
                            icon = Icons.Filled.RocketLaunch,
                            subtitle = if (restricted) {
                                stringResource(R.string.pg_autostart_restricted, manufacturerName())
                            } else {
                                stringResource(R.string.pg_autostart_ok)
                            },
                            onClick = { if (restricted) env.actions.fix(DeliveryRequirement.AUTO_START) else onOpenPage(SettingsPage.BOOT) },
                            trailing = if (restricted) null else ({ OkIcon() }),
                        )
                    }
                }
            }
        }
        item(key = "note") { InfoNote(stringResource(R.string.pg_monitor_note)) }
        item(key = "delivery") {
            Column {
                Subhead(
                    stringResource(R.string.pg_group_delivery),
                    trailing = {
                        Text(
                            if (pending > 0) stringResource(R.string.pg_delivery_pending, pending) else stringResource(R.string.pg_delivery_all_ok),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (pending > 0) MaterialTheme.colorScheme.error else MaterialTheme.status.ok,
                        )
                    },
                )
                SegmentedGroup(delivery.size) { index, shape ->
                    val requirement = delivery[index]
                    SegmentedRow(
                        title = formats.requirementLabel(requirement),
                        subtitle = formats.requirementDescription(requirement),
                        icon = requirementIcon(requirement),
                        shape = shape,
                        trailing = { RequirementStatus(requirement, requirement !in env.missing, env.actions::fix) },
                    )
                }
            }
        }
        item(key = "pause") {
            Column {
                Subhead(stringResource(R.string.pg_pause))
                SegmentedGroup(1) { _, shape ->
                    NavRow(
                        title = stringResource(if (paused) R.string.pg_phase_paused else R.string.pg_pause),
                        shape = shape,
                        icon = Icons.Filled.Pause,
                        subtitle = when {
                            !settings.serviceEnabled -> stringResource(R.string.boot_start_unavailable)
                            paused -> stringResource(
                                R.string.pg_pause_row_paused,
                                formats.clockWithDay(env.pauseUntil, now),
                                formats.duration(env.pauseUntil - now),
                            )
                            else -> stringResource(R.string.pg_pause_row_idle)
                        },
                        enabled = settings.serviceEnabled,
                        onClick = { onShowDialog(SettingsDialog.PAUSE) },
                        trailing = if (paused) {
                            {
                                FilledTonalButton(onClick = env.actions::resume, modifier = Modifier.focusHighlight(RoundedCornerShape(50))) {
                                    Text(stringResource(R.string.pg_resume_now))
                                }
                            }
                        } else {
                            null
                        },
                    )
                }
            }
        }
        item(key = "starts") {
            Column {
                Subhead(stringResource(R.string.pg_group_recent_starts))
                if (starts.isEmpty()) {
                    SegmentedGroup(1) { _, shape ->
                        InfoRow(
                            title = stringResource(R.string.pg_no_starts),
                            shape = shape,
                            icon = Icons.Filled.Schedule,
                            subtitle = stringResource(R.string.pg_no_starts_desc),
                        )
                    }
                } else {
                    SegmentedGroup(starts.size) { index, shape ->
                        val record = starts[index]
                        val time = formats.dayTime(record.time, now)
                        InfoRow(
                            title = formats.originLabel(record.origin),
                            shape = shape,
                            icon = originIcon(record.origin),
                            subtitle = if (record.detail.isNotBlank()) "$time · ${record.detail}" else time,
                        )
                    }
                }
            }
        }
        item(key = "diagnostics") {
            SegmentedGroup(1) { _, shape ->
                NavRow(
                    title = stringResource(R.string.pg_open_diagnostics),
                    shape = shape,
                    icon = Icons.Filled.MonitorHeart,
                    subtitle = stringResource(R.string.pg_open_diagnostics_desc),
                    onClick = onOpenDiagnostics,
                )
            }
        }
    }
}

@Composable
private fun OkIcon() {
    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.status.ok, modifier = Modifier.size(24.dp))
}

/** The meta line of the monitoring card (prototype monCard). */
@Composable
private fun MonitorMeta(env: SettingsEnv, phase: MonitorPhase) {
    val formats = env.formats
    val metrics = env.metrics
    val now = env.now
    when (phase) {
        MonitorPhase.OFF -> Text(
            stringResource(R.string.pg_monitor_off_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MonitorPhase.MONITORING -> {
            val heartbeat = metrics.heartbeatAgeMs
            val latency = metrics.latencyP50Ms
            val clock = metrics.clockOffsetMs
            if (heartbeat != null || latency != null || clock != null) {
                MetaPills {
                    if (heartbeat != null) MetaPill(Icons.Filled.MonitorHeart, stringResource(R.string.pg_meta_heartbeat, formats.ago(now - heartbeat, now)))
                    if (latency != null) MetaPill(Icons.Filled.Speed, stringResource(R.string.pg_meta_latency, formats.seconds(latency)))
                    if (clock != null) MetaPill(Icons.Filled.Schedule, stringResource(R.string.pg_meta_clock, formats.signedSeconds(clock)))
                }
            }
        }
        MonitorPhase.PAUSED -> MetaPills {
            if (env.pauseUntil > now) MetaPill(Icons.Filled.Schedule, stringResource(R.string.pg_meta_remaining, formats.duration(env.pauseUntil - now)))
            MetaPill(Icons.Filled.RestartAlt, stringResource(R.string.pg_meta_resume_any))
        }
        MonitorPhase.CONNECTING, MonitorPhase.RECONNECTING -> MetaPills {
            val ids = env.settings.enabledSources.sortedBy { it.ordinal }.joinToString(" · ") { it.wsUrl.substringAfterLast('/') }
            MetaPill(Icons.Filled.Sensors, ids)
        }
        MonitorPhase.DEGRADED -> {
            val polling = env.monitor.sources.filter { it.link == LinkState.POLLING }
            if (polling.isNotEmpty()) {
                MetaPills {
                    MetaPill(
                        Icons.Filled.SyncProblem,
                        stringResource(R.string.pg_meta_polling, polling.joinToString(stringResource(R.string.pg_list_separator)) { formats.sourceShortName(it.source) }),
                    )
                }
            }
        }
        MonitorPhase.LIMITED -> {
            val count = env.missing.count { it.required }
            if (count > 0) MetaPills { MetaPill(Icons.Filled.Warning, stringResource(R.string.pg_delivery_pending, count)) }
        }
    }
}

/** The five rows of the 重启后 table (design §3 开机自动启动). */
private enum class BootCase(val title: Int, val description: Int) {
    ON_ON(R.string.pg_boot_case_on_on, R.string.pg_boot_case_on_on_desc),
    ON_OFF(R.string.pg_boot_case_on_off, R.string.pg_boot_case_on_off_desc),
    PAUSED_ON(R.string.pg_boot_case_paused_on, R.string.pg_boot_case_paused_on_desc),
    PAUSED_OFF(R.string.pg_boot_case_paused_off, R.string.pg_boot_case_paused_off_desc),
    OFF(R.string.pg_boot_case_off, R.string.pg_boot_case_off_desc),
}

private fun bootCase(env: SettingsEnv): BootCase {
    val settings = env.settings
    val paused = env.pauseUntil > env.now
    return when {
        !settings.serviceEnabled -> BootCase.OFF
        paused && settings.bootStart -> BootCase.PAUSED_ON
        paused -> BootCase.PAUSED_OFF
        settings.bootStart -> BootCase.ON_ON
        else -> BootCase.ON_OFF
    }
}

/** 设置 › 开机自动启动 (prototype scrBootLock + design §3 开机自动启动). */
@Composable
internal fun BootPage(env: SettingsEnv, onBack: (() -> Unit)?) {
    val settings = env.settings
    val formats = env.formats
    val bootDelay = rememberBootDelay(env)
    val restricted = remember { AutoStartSettings.isRestricted() }
    val case = bootCase(env)

    SettingsPageScaffold(title = stringResource(R.string.pg_boot_page), onBack = onBack) {
        item(key = "switch") {
            SegmentedGroup(2) { index, shape ->
                if (index == 0) {
                    SwitchRow(
                        title = stringResource(R.string.boot_start),
                        shape = shape,
                        icon = Icons.Filled.RestartAlt,
                        subtitle = bootSubtitle(settings, bootDelay),
                        checked = settings.bootStart && settings.serviceEnabled,
                        onCheckedChange = { on -> env.actions.edit { updateBootStart(on) } },
                        enabled = settings.serviceEnabled,
                    )
                } else {
                    val origin = env.monitor.startOrigin
                    val blocked = bootDelay == null && settings.serviceEnabled && settings.bootStart && restricted
                    InfoRow(
                        title = stringResource(R.string.pg_boot_this_boot),
                        shape = shape,
                        icon = Icons.Filled.Schedule,
                        subtitle = when {
                            bootDelay != null -> stringResource(R.string.pg_boot_this_auto, bootDelay)
                            blocked -> stringResource(R.string.pg_boot_this_blocked)
                            env.monitor.runningSince != null && origin != null && origin != StartOrigin.BOOT ->
                                stringResource(R.string.pg_boot_this_running, formats.originLabel(origin))
                            else -> stringResource(R.string.pg_boot_this_none)
                        },
                        trailing = if (blocked) {
                            {
                                FilledTonalButton(
                                    onClick = { env.actions.fix(DeliveryRequirement.AUTO_START) },
                                    modifier = Modifier.focusHighlight(RoundedCornerShape(50)),
                                ) { Text(stringResource(R.string.autostart_open)) }
                            }
                        } else {
                            null
                        },
                    )
                }
            }
        }
        item(key = "lock") {
            Column {
                Subhead(stringResource(R.string.pg_lock_preview))
                LockScreenPreview(env, case, bootDelay)
            }
        }
        item(key = "cases") {
            Column {
                Subhead(stringResource(R.string.pg_group_reboot))
                SegmentedGroup(BootCase.entries.size) { index, shape ->
                    val row = BootCase.entries[index]
                    val current = row == case
                    SegmentedRow(
                        title = stringResource(row.title),
                        subtitle = stringResource(row.description),
                        shape = shape,
                        modifier = Modifier.selectedTint(current, shape),
                        trailing = if (current) {
                            { Pill(stringResource(R.string.pg_current), MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer) }
                        } else {
                            null
                        },
                    )
                }
            }
        }
        item(key = "direct") {
            Column {
                Subhead(stringResource(R.string.pg_group_direct_boot))
                SegmentedGroup(1) { _, shape ->
                    InfoRow(
                        title = stringResource(R.string.pg_direct_boot),
                        shape = shape,
                        icon = Icons.Filled.LockOpen,
                        subtitle = stringResource(R.string.pg_direct_boot_long),
                        trailing = { OkIcon() },
                    )
                }
            }
        }
        item(key = "limits") {
            Column {
                Subhead(stringResource(R.string.pg_group_system_limits))
                SegmentedGroup(2) { index, shape ->
                    if (index == 0) {
                        SegmentedRow(
                            title = stringResource(R.string.pg_autostart_perm),
                            subtitle = if (restricted) stringResource(R.string.autostart_hint) else stringResource(R.string.pg_autostart_ok),
                            icon = Icons.Filled.RocketLaunch,
                            shape = shape,
                            trailing = {
                                if (restricted) {
                                    FilledTonalButton(
                                        onClick = { env.actions.fix(DeliveryRequirement.AUTO_START) },
                                        modifier = Modifier.focusHighlight(RoundedCornerShape(50)),
                                    ) { Text(stringResource(R.string.autostart_open)) }
                                } else {
                                    OkIcon()
                                }
                            },
                        )
                    } else {
                        val requirement = DeliveryRequirement.BATTERY_OPTIMIZATION
                        SegmentedRow(
                            title = formats.requirementLabel(requirement),
                            subtitle = formats.requirementDescription(requirement),
                            icon = requirementIcon(requirement),
                            shape = shape,
                            trailing = { RequirementStatus(requirement, requirement !in env.missing, env.actions::fix) },
                        )
                    }
                }
            }
        }
        item(key = "watchdog") { InfoNote(stringResource(R.string.pg_watchdog_note), icon = Icons.Filled.HealthAndSafety) }
        item(key = "update") { InfoNote(stringResource(R.string.pg_update_resume_note), icon = Icons.Filled.SystemUpdate) }
    }
}

/** What the lock screen shows after a reboot, before the app is opened (prototype scrBootLock). */
@Composable
private fun LockScreenPreview(env: SettingsEnv, case: BootCase, bootDelay: Long?) {
    val formats = env.formats
    val ink = Color(0xFFE4E7EB)
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .background(Brush.verticalGradient(listOf(Color(0xFF233142), Color(0xFF0E1217))))
                .padding(horizontal = 16.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = ink, modifier = Modifier.size(20.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(formats.clock(env.now), color = ink, fontSize = 52.sp, fontWeight = FontWeight.Light, lineHeight = 56.sp)
                Text(stringResource(R.string.pg_lock_preview_caption), color = ink.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium)
            }
            LockNotification(env, case, bootDelay)
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Fingerprint, contentDescription = null, tint = ink.copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
                Spacer(Modifier.weight(1f))
                Icon(Icons.Filled.PhotoCamera, contentDescription = null, tint = ink.copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun LockNotification(env: SettingsEnv, case: BootCase, bootDelay: Long?) {
    val formats = env.formats
    val paper = Color(0xFFF4F6F8)
    val text = Color(0xFF191C20)
    val muted = Color(0xFF41484D)
    val status = MaterialTheme.status
    Surface(shape = RoundedCornerShape(22.dp), color = paper, contentColor = text, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (case == BootCase.OFF) {
                Text(stringResource(R.string.pg_lock_off), style = MaterialTheme.typography.bodyMedium, color = muted)
                return@Column
            }
            val warn = case != BootCase.ON_ON
            val badge = if (warn) status.warn else status.ok
            val onBadge = if (warn) status.onWarn else status.onOk
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(20.dp).background(badge, CircleShape), contentAlignment = Alignment.Center) {
                    when (case) {
                        BootCase.ON_ON -> Icon(painterResource(R.drawable.ic_pg_earthquake), contentDescription = null, tint = onBadge, modifier = Modifier.size(13.dp))
                        BootCase.PAUSED_ON -> Icon(Icons.Filled.Pause, contentDescription = null, tint = onBadge, modifier = Modifier.size(13.dp))
                        else -> Icon(Icons.Filled.NotificationsPaused, contentDescription = null, tint = onBadge, modifier = Modifier.size(13.dp))
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    if (case == BootCase.ON_ON || case == BootCase.PAUSED_ON) {
                        stringResource(R.string.pg_lock_app_monitor, stringResource(R.string.app_name))
                    } else {
                        stringResource(R.string.app_name)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = muted,
                )
            }
            when (case) {
                BootCase.ON_ON -> {
                    Text(
                        if (bootDelay != null) stringResource(R.string.pg_lock_on_title, bootDelay) else stringResource(R.string.pg_lock_on_title_generic),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(stringResource(R.string.pg_lock_on_body, env.settings.enabledSources.size), style = MaterialTheme.typography.bodySmall, color = muted)
                }
                BootCase.PAUSED_ON -> {
                    Text(
                        stringResource(R.string.pg_lock_paused_title, formats.clockWithDay(env.pauseUntil, env.now)),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(stringResource(R.string.pg_lock_paused_body), style = MaterialTheme.typography.bodySmall, color = muted)
                }
                else -> {
                    Text(stringResource(R.string.boot_notice_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.boot_notice_text), style = MaterialTheme.typography.bodySmall, color = muted)
                    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Text(stringResource(R.string.boot_notice_start), style = MaterialTheme.typography.labelLarge, color = Color(0xFF0F5FA6))
                        Text(stringResource(R.string.tab_settings), style = MaterialTheme.typography.labelLarge, color = Color(0xFF0F5FA6))
                    }
                }
            }
        }
    }
}
