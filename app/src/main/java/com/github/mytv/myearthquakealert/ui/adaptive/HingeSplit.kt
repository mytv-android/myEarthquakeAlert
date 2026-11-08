package com.github.mytv.myearthquakealert.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Pixel bounds of the two panes along one axis: [startEnd] is where the first pane ends,
 * [endStart] where the second begins. Pure so it can be tested.
 */
internal data class SplitSpan(val startEnd: Int, val endStart: Int)

/**
 * Splits [size] px at a hinge spanning [hingeStart]..[hingeEnd] (local px). A zero-width fold
 * still gets a [minGap] gutter centred on it; without a usable hinge the split falls back to
 * [fallbackFraction].
 */
internal fun splitAtHinge(size: Int, hingeStart: Float?, hingeEnd: Float?, minGap: Int, fallbackFraction: Float): SplitSpan {
    if (hingeStart != null && hingeEnd != null && hingeEnd > 0f && hingeStart < size) {
        var a = hingeStart.coerceIn(0f, size.toFloat())
        var b = hingeEnd.coerceIn(0f, size.toFloat())
        if (b - a < minGap) {
            val c = (a + b) / 2f
            a = (c - minGap / 2f).coerceAtLeast(0f)
            b = (c + minGap / 2f).coerceAtMost(size.toFloat())
        }
        val s = a.roundToInt()
        val e = b.roundToInt()
        if (s > 0 && e < size) return SplitSpan(s, e)
    }
    val gap = minGap
    val s = ((size - gap) * fallbackFraction).roundToInt().coerceIn(0, size)
    return SplitSpan(s, (s + gap).coerceAtMost(size))
}

/**
 * Two panes side by side, the boundary exactly on a vertical [hinge] (window px) — the
 * 书本 and 双屏 layouts. Nothing is drawn under the hinge.
 */
@Composable
fun HingeSplitRow(
    hinge: Rect?,
    modifier: Modifier = Modifier,
    minGap: Dp = 24.dp,
    fallbackFraction: Float = 0.5f,
    start: @Composable () -> Unit,
    end: @Composable () -> Unit,
) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    Layout(
        contents = listOf({ Box { start() } }, { Box { end() } }),
        modifier = modifier.onPlaced { origin = it.positionInWindow() },
    ) { (startMeasurables, endMeasurables), constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val span = splitAtHinge(
            size = width,
            hingeStart = hinge?.left?.minus(origin.x),
            hingeEnd = hinge?.right?.minus(origin.x),
            minGap = minGap.roundToPx(),
            fallbackFraction = fallbackFraction,
        )
        val leading = startMeasurables.first().measure(Constraints.fixed(span.startEnd, height))
        val trailing = endMeasurables.first().measure(Constraints.fixed(max(0, width - span.endStart), height))
        layout(width, height) {
            leading.place(0, 0)
            trailing.place(span.endStart, 0)
        }
    }
}

/** Upper and lower panes split at a horizontal fold — the 桌面 (tabletop) layout. */
@Composable
fun HingeSplitColumn(
    hinge: Rect?,
    modifier: Modifier = Modifier,
    minGap: Dp = 0.dp,
    fallbackFraction: Float = 0.5f,
    top: @Composable () -> Unit,
    bottom: @Composable () -> Unit,
) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    Layout(
        contents = listOf({ Box { top() } }, { Box { bottom() } }),
        modifier = modifier.onPlaced { origin = it.positionInWindow() },
    ) { (topMeasurables, bottomMeasurables), constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val span = splitAtHinge(
            size = height,
            hingeStart = hinge?.top?.minus(origin.y),
            hingeEnd = hinge?.bottom?.minus(origin.y),
            minGap = minGap.roundToPx(),
            fallbackFraction = fallbackFraction,
        )
        val leading = topMeasurables.first().measure(Constraints.fixed(width, span.startEnd))
        val trailing = bottomMeasurables.first().measure(Constraints.fixed(width, max(0, height - span.endStart)))
        layout(width, height) {
            leading.place(0, 0)
            trailing.place(0, span.endStart)
        }
    }
}
