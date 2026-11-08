package com.github.mytv.myearthquakealert.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.ui.map.QuakeMarker
import com.github.mytv.myearthquakealert.ui.map.quakeDotRadiusDp
import com.github.mytv.myearthquakealert.ui.quakes.QuakeCatalog
import com.github.mytv.myearthquakealert.ui.quakes.QuakeUi
import com.github.mytv.myearthquakealert.ui.theme.EeqNumberStyles
import com.github.mytv.myearthquakealert.ui.theme.contentColorOn
import com.github.mytv.myearthquakealert.ui.theme.csisColor

/** Epicentral intensity in its CSIS colour (design .ibad). */
@Composable
fun IntensityBadge(
    intensity: Int,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    textStyle: TextStyle = EeqNumberStyles.badge,
) {
    val background = csisColor(intensity)
    val description = stringResource(R.string.ui_intensity_cd, intensity)
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.35f))
            .background(background)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(intensity.toString(), style = textStyle, color = contentColorOn(background))
    }
}

/** "{time} · 深 10 km · 距你 86 km" — the second line of a quake row. */
@Composable
fun quakeSubtitle(quake: QuakeUi, now: Long, timeOnly: Boolean = false): String {
    val time = if (timeOnly) QuakeCatalog.hhmm(quake.time) else agoText(quake.epochMillis, quake.time, now)
    val depth = formatDepth(quake.depthKm)
    return quake.distanceKm?.let { stringResource(R.string.ui_quake_sub, time, depth, formatKm(it)) }
        ?: stringResource(R.string.ui_quake_sub_nodist, time, depth)
}

/**
 * One earthquake in a segmented list (design .row + qrow): intensity badge, place, time /
 * depth / distance and the magnitude on the right. [selected] uses the secondary container.
 */
@Composable
fun QuakeRow(
    quake: QuakeUi,
    shape: Shape,
    now: Long,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    timeOnly: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val container = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
    val content = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
    val body: @Composable () -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            IntensityBadge(quake.intensity)
            Column(Modifier.weight(1f)) {
                Text(
                    quake.location,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    quakeSubtitle(quake, now, timeOnly),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                    color = if (selected) content.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            MagnitudeLabel(quake.magnitude, subdued = if (selected) content.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            shape = shape,
            color = container,
            contentColor = content,
            modifier = modifier.fillMaxWidth().tvFocusHighlight(shape),
        ) { body() }
    } else {
        Surface(shape = shape, color = container, contentColor = content, modifier = modifier.fillMaxWidth()) { body() }
    }
}

/** Small "M" over the large magnitude (design .qmag). */
@Composable
fun MagnitudeLabel(
    magnitude: Double,
    modifier: Modifier = Modifier,
    subdued: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    valueStyle: TextStyle = EeqNumberStyles.value.copy(fontSize = 20.sp, lineHeight = 22.sp),
) {
    Column(modifier, horizontalAlignment = Alignment.End) {
        Text("M", style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, lineHeight = 13.sp), color = subdued, textAlign = TextAlign.End)
        Text(formatMagnitude(magnitude), style = valueStyle)
    }
}

/** History dots for [com.github.mytv.myearthquakealert.ui.map.EarthquakeMap], newest first. */
fun List<QuakeUi>.toMarkers(): List<QuakeMarker> = mapIndexed { i, q ->
    QuakeMarker(
        id = q.key,
        latitude = q.latitude,
        longitude = q.longitude,
        colorArgb = csisColor(q.intensity).toArgb(),
        radiusDp = quakeDotRadiusDp(q.magnitude),
        halo = i == 0,
    )
}
