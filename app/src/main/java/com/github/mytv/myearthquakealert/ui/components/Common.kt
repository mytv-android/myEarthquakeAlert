@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.github.mytv.myearthquakealert.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.mytv.myearthquakealert.ui.adaptive.LayoutKind
import com.github.mytv.myearthquakealert.ui.adaptive.LocalResolvedLayout

/** Small rounded label tinted with the current content colour (design .pill). */
@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    color: Color = LocalContentColor.current,
) {
    Row(
        modifier = modifier
            .height(26.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(color.copy(alpha = 0.10f))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(15.dp))
        Text(text, color = color, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

/** Status dot (design .dot): ok / warn / error / off. */
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(9.dp).clip(CircleShape).background(color))
}

/** Explanatory note with a leading info icon (design .mon-warn). */
@Composable
fun InfoNote(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Info,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    action: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(containerColor)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
        Text(
            text,
            color = contentColor,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 18.sp),
            modifier = Modifier.weight(1f),
        )
        action?.invoke(this)
    }
}

/** Section title with an optional trailing link ("最近地震 · 全部", design .sect). */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    link: String? = null,
    onLink: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 36.dp).padding(start = 6.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (link != null && onLink != null) {
            Text(
                link,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .tvFocusHighlight(RoundedCornerShape(8.dp))
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button, onClick = onLink)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

/** Floating label on a map (design .mchip). */
@Composable
fun MapChip(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Surface(
        modifier = modifier.height(30.dp),
        shape = RoundedCornerShape(15.dp),
        color = containerColor,
        contentColor = contentColor,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Visible D-pad focus for the TV layout: a 3 dp primary outline and a slight scale-up.
 * A no-op on touch layouts. Place it before `clickable` / `focusable` so it observes the
 * focus of that element.
 */
fun Modifier.tvFocusHighlight(shape: Shape = RoundedCornerShape(20.dp)): Modifier = composed {
    val layout = LocalResolvedLayout.current
    if (!layout.isTelevision && layout.kind != LayoutKind.TV) return@composed this
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.03f else 1f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "tvFocusScale")
    val outline = MaterialTheme.colorScheme.primary
    this
        .onFocusChanged { focused = it.hasFocus }
        .scale(scale)
        .then(if (focused) Modifier.border(3.dp, outline, shape) else Modifier)
}
