package com.github.mytv.myearthquakealert.ui.settings.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.mytv.myearthquakealert.service.MonitorPhase
import com.github.mytv.myearthquakealert.ui.components.StatusShape
import com.github.mytv.myearthquakealert.ui.theme.status

/** Container / content colours of the monitoring card for each phase (prototype `.mon.*`). */
@Composable
fun phaseContainer(phase: MonitorPhase): Pair<Color, Color> {
    val c = MaterialTheme.colorScheme
    val s = MaterialTheme.status
    return when (phase) {
        MonitorPhase.MONITORING -> s.okContainer to s.onOkContainer
        MonitorPhase.CONNECTING -> c.primaryContainer to c.onPrimaryContainer
        MonitorPhase.RECONNECTING, MonitorPhase.DEGRADED, MonitorPhase.PAUSED -> s.warnContainer to s.onWarnContainer
        MonitorPhase.LIMITED -> c.errorContainer to c.onErrorContainer
        MonitorPhase.OFF -> c.surfaceContainerHigh to c.onSurface
    }
}

/**
 * The monitoring card: status shape, phase title and subtitle, the 后台持续监测 switch and
 * an optional meta line (pills or an explanation) underneath.
 */
@Composable
fun MonitorCard(
    phase: MonitorPhase,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    shapeSize: Dp = 52.dp,
    meta: (@Composable () -> Unit)? = null,
) {
    val (container, content) = phaseContainer(phase)
    val animated by animateColorAsState(container, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "monitorCard")
    Surface(shape = RoundedCornerShape(28.dp), color = animated, contentColor = content, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                StatusShape(phase, size = shapeSize)
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = content.copy(alpha = 0.82f))
                }
                SettingSwitch(checked = checked, onCheckedChange = onCheckedChange)
            }
            meta?.invoke()
        }
    }
}

/** Translucent pill on a coloured card ("心跳 18 秒前"). */
@Composable
fun MetaPill(icon: ImageVector, text: String) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MetaPills(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
}
