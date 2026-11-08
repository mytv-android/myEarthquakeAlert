package com.github.mytv.myearthquakealert.ui.diagnostics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.mytv.myearthquakealert.diagnostics.AvailabilitySlot
import com.github.mytv.myearthquakealert.diagnostics.SlotStatus
import com.github.mytv.myearthquakealert.ui.theme.status

/** Number of 15-minute cells in the 24 h availability bar. */
const val AvailabilitySlots = 96

/**
 * 24 h availability bar (prototype `.avb`): 96 cells of 15 minutes, oldest first. Missing
 * cells (fewer published than 96) render as "off" on the left.
 */
@Composable
fun AvailabilityBar(
    slots: List<AvailabilitySlot>,
    modifier: Modifier = Modifier,
    height: Dp = 22.dp,
    description: String? = null,
) {
    val ok = MaterialTheme.status.ok
    val warn = MaterialTheme.status.warn
    val err = MaterialTheme.colorScheme.error
    val off = MaterialTheme.colorScheme.outlineVariant
    val cells = slots.takeLast(AvailabilitySlots)
    val padding = AvailabilitySlots - cells.size
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { if (description != null) contentDescription = description },
    ) {
        val gap = 1.5.dp.toPx()
        val w = (size.width - gap * (AvailabilitySlots - 1)) / AvailabilitySlots
        val r = CornerRadius(2.dp.toPx(), 2.dp.toPx())
        for (i in 0 until AvailabilitySlots) {
            val status = if (i < padding) SlotStatus.OFF else cells[i - padding].status
            val color = when (status) {
                SlotStatus.UP -> ok
                SlotStatus.DEGRADED -> warn
                SlotStatus.DOWN -> err
                SlotStatus.OFF -> off
            }
            drawRoundRect(color, topLeft = Offset(i * (w + gap), 0f), size = Size(w, size.height), cornerRadius = r)
        }
    }
}

/** Time labels under the availability bar: start, two intermediate times and 现在. */
@Composable
fun AvailabilityLabels(labels: List<String>, color: Color, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        labels.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = color) }
    }
}

/**
 * Heartbeat sparkline (prototype spark()): recent gaps in seconds, a dashed line at the
 * 60 s threshold, the area under the curve tinted, the latest sample dotted.
 */
@Composable
fun Sparkline(
    samples: List<Int>,
    color: Color,
    thresholdColor: Color,
    modifier: Modifier = Modifier,
    threshold: Int = 60,
) {
    Canvas(modifier.fillMaxWidth().height(28.dp)) {
        val top = maxOf(70f, (samples.maxOrNull() ?: 0) * 1.1f)
        val y = { v: Float -> size.height - (v / top) * size.height }
        val ty = y(threshold.toFloat())
        drawLine(
            thresholdColor,
            Offset(0f, ty),
            Offset(size.width, ty),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
        )
        if (samples.size < 2) return@Canvas
        val step = size.width / (samples.size - 1)
        val line = Path()
        samples.forEachIndexed { i, v ->
            val px = i * step
            val py = y(v.toFloat())
            if (i == 0) line.moveTo(px, py) else line.lineTo(px, py)
        }
        val area = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(area, color.copy(alpha = 0.14f))
        drawPath(line, color, style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(color, radius = 2.4.dp.toPx(), center = Offset(size.width, y(samples.last().toFloat())))
    }
}

/** A metric tile (prototype `.mtile`): label, large value with unit, detail and a status dot. */
@Composable
fun MetricTile(
    icon: ImageVector,
    label: String,
    value: String,
    detail: String,
    level: MetricLevel,
    modifier: Modifier = Modifier,
    unit: String? = null,
    levelLabel: String? = null,
    extra: (@Composable () -> Unit)? = null,
) {
    val colors = levelColors(level)
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.container,
        contentColor = colors.content,
        modifier = modifier.semantics(mergeDescendants = true) { if (levelLabel != null) contentDescription = "$label $value${unit.orEmpty()} $levelLabel" },
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.content.copy(alpha = 0.75f))
                Spacer(Modifier.width(5.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.content.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Box(Modifier.size(8.dp).clip(CircleShape).background(colors.accent))
            }
            Text(
                buildAnnotatedString {
                    append(value)
                    if (unit != null) {
                        withStyle(SpanStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.content.copy(alpha = 0.7f))) {
                            append(" ")
                            append(unit)
                        }
                    }
                },
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = colors.content.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (extra != null) {
                Spacer(Modifier.height(6.dp))
                extra()
            }
        }
    }
}

/** One node of the 数据链路 strip (prototype `.pipe .nd`). */
data class PipeNode(val icon: ImageVector, val label: String, val value: String, val level: MetricLevel)

@Composable
fun PipelineStrip(nodes: List<PipeNode>, modifier: Modifier = Modifier) {
    val arrow = MaterialTheme.colorScheme.outlineVariant
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 12.dp), verticalAlignment = Alignment.Top) {
            nodes.forEachIndexed { index, node ->
                if (index > 0) {
                    Box(Modifier.padding(top = 16.dp).width(10.dp).height(2.dp).background(arrow))
                }
                val colors = when (node.level) {
                    MetricLevel.GOOD -> MaterialTheme.status.okContainer to MaterialTheme.status.onOkContainer
                    MetricLevel.WARN -> MaterialTheme.status.warnContainer to MaterialTheme.status.onWarnContainer
                    MetricLevel.BAD -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
                    MetricLevel.UNKNOWN -> MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurfaceVariant
                }
                Column(
                    Modifier.weight(1f).semantics(mergeDescendants = true) {},
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Surface(shape = RoundedCornerShape(12.dp), color = colors.first, contentColor = colors.second, modifier = Modifier.size(34.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(node.icon, contentDescription = null, modifier = Modifier.size(19.dp)) }
                    }
                    Text(node.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(
                        node.value,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Coloured status dot of a source row. */
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(10.dp).clip(CircleShape).background(color))
}
