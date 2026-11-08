@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.github.mytv.myearthquakealert.ui.overview

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ButtonGroupScope
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.data.repository.UserSettings
import com.github.mytv.myearthquakealert.data.source.EewSource
import com.github.mytv.myearthquakealert.diagnostics.HealthMetrics
import com.github.mytv.myearthquakealert.service.LinkState
import com.github.mytv.myearthquakealert.service.MonitorPhase
import com.github.mytv.myearthquakealert.service.MonitorState
import com.github.mytv.myearthquakealert.ui.components.InfoNote
import com.github.mytv.myearthquakealert.ui.components.Pill
import com.github.mytv.myearthquakealert.ui.components.StatusDot
import com.github.mytv.myearthquakealert.ui.components.StatusShape
import com.github.mytv.myearthquakealert.ui.components.ageText
import com.github.mytv.myearthquakealert.ui.components.durationText
import com.github.mytv.myearthquakealert.ui.components.feedName
import com.github.mytv.myearthquakealert.ui.components.formatSeconds
import com.github.mytv.myearthquakealert.ui.components.formatSignedSeconds
import com.github.mytv.myearthquakealert.ui.components.linkColor
import com.github.mytv.myearthquakealert.ui.components.monitorTexts
import com.github.mytv.myearthquakealert.ui.components.phaseCardColors
import com.github.mytv.myearthquakealert.ui.components.rememberNow
import com.github.mytv.myearthquakealert.ui.components.requirementShort
import com.github.mytv.myearthquakealert.ui.components.tvFocusHighlight

/**
 * 后台监测 status card (design .mon): phase shape + title + switch, live health pills,
 * one chip per subscribed source and the actions (暂停 / 立即恢复 / 开启监测, 诊断, 修复).
 */
@Composable
fun MonitorStatusCard(
    phase: MonitorPhase,
    state: MonitorState,
    settings: UserSettings,
    metrics: HealthMetrics,
    onSwitch: (on: Boolean) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDiagnostics: () -> Unit,
    onFix: () -> Unit,
    modifier: Modifier = Modifier,
    shapeSize: Dp = 56.dp,
    showActions: Boolean = true,
) {
    val now = rememberNow(5_000L)
    val texts = monitorTexts(phase, state, settings, now)
    val colors = phaseCardColors(phase)
    val spec = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
    val container by animateColorAsState(colors.container, spec, label = "monContainer")
    val content by animateColorAsState(colors.content, spec, label = "monContent")
    val switchLabel = stringResource(R.string.ui_monitor_switch)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = container,
        contentColor = content,
    ) {
        Column(
            Modifier.padding(16.dp).animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                StatusShape(phase, size = shapeSize)
                Column(Modifier.weight(1f)) {
                    Text(
                        texts.title,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold),
                    )
                    Text(
                        texts.subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = LocalContentColor.current.copy(alpha = 0.82f),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Switch(
                    checked = phase != MonitorPhase.OFF,
                    onCheckedChange = onSwitch,
                    modifier = Modifier.tvFocusHighlight(RoundedCornerShape(16.dp)).semantics { contentDescription = switchLabel },
                    thumbContent = if (phase != MonitorPhase.OFF) {
                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
                    } else {
                        null
                    },
                )
            }

            MonitorMeta(phase, state, settings, metrics, now, onFix)

            if (showActions) {
                MonitorButtons(phase, content, container, onSwitch, onPause, onResume, onDiagnostics, onFix)
            }
        }
    }
}

@Composable
private fun MonitorMeta(
    phase: MonitorPhase,
    state: MonitorState,
    settings: UserSettings,
    metrics: HealthMetrics,
    now: Long,
    onFix: () -> Unit,
) {
    when (phase) {
        MonitorPhase.OFF -> InfoNote(
            stringResource(R.string.ui_state_off_note),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        )
        MonitorPhase.LIMITED -> {
            val missing = state.missing.sortedBy { it.ordinal }
            InfoNote(
                if (missing.isEmpty()) {
                    stringResource(R.string.ui_state_limited_note_generic)
                } else {
                    stringResource(R.string.ui_state_limited_note, missing.map { requirementShort(it) }.joinToString(stringResource(R.string.ui_list_separator)))
                },
                icon = Icons.Outlined.Warning,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            )
            SourceChips(state)
        }
        MonitorPhase.PAUSED -> {
            val until = settings.pauseUntil.takeIf { it > 0 } ?: state.pauseUntil
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (until > now) Pill(stringResource(R.string.ui_pill_remaining, durationText(until - now)), icon = Icons.Outlined.Schedule)
                Pill(stringResource(R.string.ui_pill_resume_any_time), icon = Icons.Outlined.RestartAlt)
            }
        }
        MonitorPhase.CONNECTING -> {
            val feeds = EewSource.entries.filter { it in settings.enabledSources }.joinToString(" · ") { it.feedName }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(feeds, icon = Icons.Outlined.Sensors)
            }
        }
        MonitorPhase.MONITORING, MonitorPhase.RECONNECTING, MonitorPhase.DEGRADED -> {
            val lastFrame = state.sources.mapNotNull { it.lastFrameAt }.maxOrNull()
            val heartbeatAge = lastFrame?.let { now - it } ?: metrics.heartbeatAgeMs
            val latency = metrics.latencyP50Ms ?: state.sources.mapNotNull { it.lastLatencyMs }.minOrNull()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (phase != MonitorPhase.MONITORING && state.sources.isNotEmpty()) {
                    Pill(stringResource(R.string.ui_pill_connected, state.connectedCount, state.sources.size), icon = Icons.Outlined.Sensors)
                }
                heartbeatAge?.let { Pill(stringResource(R.string.ui_pill_heartbeat, ageText(it)), icon = Icons.Outlined.MonitorHeart) }
                latency?.let { Pill(stringResource(R.string.ui_pill_latency, formatSeconds(it)), icon = Icons.Outlined.Speed) }
                metrics.clockOffsetMs?.let { Pill(stringResource(R.string.ui_pill_clock, formatSignedSeconds(it)), icon = Icons.Outlined.Schedule) }
            }
            SourceChips(state)
        }
    }
}

/** One chip per subscribed source with its link state (design: 列表标注来源). */
@Composable
private fun SourceChips(state: MonitorState) {
    val sources = state.sources.ifEmpty { return }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        sources.sortedBy { it.source.ordinal }.forEach { health ->
            val link = stringResource(
                when (health.link) {
                    LinkState.CONNECTED -> R.string.connection_connected
                    LinkState.CONNECTING -> R.string.connection_connecting
                    LinkState.RECONNECTING -> R.string.ui_link_reconnecting
                    LinkState.POLLING -> R.string.ui_link_polling
                    LinkState.DISCONNECTED -> R.string.connection_disconnected
                },
            )
            val color = LocalContentColor.current
            Surface(
                shape = RoundedCornerShape(13.dp),
                color = color.copy(alpha = 0.08f),
                contentColor = color,
                modifier = Modifier.semantics { contentDescription = "${health.source.label} $link" },
            ) {
                Row(
                    Modifier.heightIn(min = 26.dp).padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    StatusDot(linkColor(health.link))
                    Text(health.source.label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun MonitorButtons(
    phase: MonitorPhase,
    content: Color,
    container: Color,
    onSwitch: (Boolean) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDiagnostics: () -> Unit,
    onFix: () -> Unit,
) {
    val primary = ButtonDefaults.buttonColors(containerColor = content, contentColor = container)
    val tonal = ButtonDefaults.buttonColors(containerColor = content.copy(alpha = 0.12f), contentColor = content)
    val turnOn = stringResource(R.string.ui_turn_on)
    val resume = stringResource(R.string.ui_resume_now)
    val pause = stringResource(R.string.ui_pause)
    val fix = stringResource(R.string.ui_fix)
    val diagnostics = stringResource(R.string.ui_diagnostics)
    ButtonGroup(
        overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        when (phase) {
            MonitorPhase.OFF -> action(turnOn, Icons.Outlined.PlayArrow, primary) { onSwitch(true) }
            MonitorPhase.PAUSED -> action(resume, Icons.Outlined.PlayArrow, primary, onResume)
            MonitorPhase.LIMITED -> {
                action(fix, Icons.Outlined.Build, primary, onFix)
                action(pause, Icons.Outlined.Pause, tonal, onPause)
            }
            else -> action(pause, Icons.Outlined.Pause, tonal, onPause)
        }
        action(diagnostics, Icons.Outlined.MonitorHeart, tonal, onDiagnostics)
    }
}

private fun ButtonGroupScope.action(
    label: String,
    icon: ImageVector,
    colors: androidx.compose.material3.ButtonColors,
    onClick: () -> Unit,
) {
    customItem(
        buttonGroupContent = {
            val interaction = remember { MutableInteractionSource() }
            Button(
                onClick = onClick,
                colors = colors,
                interactionSource = interaction,
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier
                    .weight(1f)
                    .animateWidth(interaction)
                    .heightIn(min = 40.dp)
                    .tvFocusHighlight(RoundedCornerShape(20.dp)),
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(label, modifier = Modifier.padding(start = 6.dp), maxLines = 1)
            }
        },
        menuContent = { menu ->
            DropdownMenuItem(
                text = { Text(label) },
                leadingIcon = { Icon(icon, contentDescription = null) },
                onClick = {
                    onClick()
                    menu.dismiss()
                },
            )
        },
    )
}
