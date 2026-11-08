package com.github.mytv.myearthquakealert.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Segmented list (design §6 手机): items of a group touch with a 2 dp gap, the group's
 * first and last items have large corners and inner items small ones — no dividers.
 */
object SegmentedDefaults {
    val Large = 20.dp
    val Small = 4.dp
    val Gap = 2.dp

    fun shape(index: Int, count: Int): Shape = RoundedCornerShape(
        topStart = if (index == 0) Large else Small,
        topEnd = if (index == 0) Large else Small,
        bottomStart = if (index == count - 1) Large else Small,
        bottomEnd = if (index == count - 1) Large else Small,
    )
}

/** A group of [count] segmented items; [item] receives its index and corner shape. */
@Composable
fun SegmentedGroup(
    count: Int,
    modifier: Modifier = Modifier,
    item: @Composable (index: Int, shape: Shape) -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(SegmentedDefaults.Gap)) {
        repeat(count) { item(it, SegmentedDefaults.shape(it, count)) }
    }
}

/** Convenience for a static list of rows. */
@Composable
fun SegmentedGroup(
    rows: List<@Composable (Shape) -> Unit>,
    modifier: Modifier = Modifier,
) = SegmentedGroup(rows.size, modifier) { i, shape -> rows[i](shape) }

/** Small section heading above a segmented group ("监测", "预警" …). */
@Composable
fun Subhead(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

/**
 * One row of a segmented group: leading icon in a tonal circle, title, optional subtitle
 * and trailing content (switch, value, chevron). Disabled rows render at 45 % opacity.
 */
@Composable
fun SegmentedRow(
    title: String,
    shape: Shape,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(22.dp))
                    }
                }
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                if (!subtitle.isNullOrEmpty()) {
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(12.dp))
                trailing()
            }
        }
    }
    val rowModifier = modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.45f)
    if (onClick != null) {
        Surface(onClick = onClick, enabled = enabled, shape = shape, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = rowModifier) { content() }
    } else {
        Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = rowModifier) { content() }
    }
}
