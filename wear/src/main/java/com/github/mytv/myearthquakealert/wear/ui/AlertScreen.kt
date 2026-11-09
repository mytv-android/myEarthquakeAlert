package com.github.mytv.myearthquakealert.wear.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.github.mytv.myearthquakealert.wear.R
import com.github.mytv.myearthquakealert.wear.data.WatchAlert
import com.github.mytv.myearthquakealert.wear.domain.Countdown
import com.github.mytv.myearthquakealert.wear.presentation.PhaseText
import com.github.mytv.myearthquakealert.wear.ui.theme.IntensityBurst
import com.github.mytv.myearthquakealert.wear.ui.theme.WatchColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 手表 › 预警 (design scrWAlert): the wavy countdown ring along the screen edge (yellow, full
 * when presented, empty when the S wave arrives; then a blinking red ring), the seconds, the
 * local intensity burst and distance, the place and magnitude (演练 for drills), and the
 * red 我已安全 edge button. Tapping the middle opens the map.
 */
@Composable
fun AlertScreen(alert: WatchAlert, onSafe: () -> Unit, onMap: () -> Unit, onExpired: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis { now = System.currentTimeMillis() }
        }
    }
    val snap = Countdown.snapshot(alert, now)
    LaunchedEffect(snap.stage) { if (snap.stage == Countdown.Stage.EXPIRED) onExpired() }

    Box(Modifier.fillMaxSize().background(WatchColors.AlertBackground), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) { drawRing(snap, now) }
        Column(
            modifier = Modifier
                .padding(horizontal = 30.dp)
                .padding(bottom = 26.dp)
                .clickable(onClick = onMap),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                stringResource(if (snap.arrived) R.string.alert_label_waves else R.string.alert_label_s),
                color = WatchColors.AlertLabel,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
            if (snap.arrived) {
                Text(
                    stringResource(R.string.alert_arrived),
                    color = WatchColors.Arrived,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        snap.sSeconds.toString(),
                        color = WatchColors.Text,
                        fontSize = 52.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Text(
                        stringResource(R.string.alert_seconds),
                        color = WatchColors.Text,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(start = 2.dp, bottom = 10.dp),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IntensityBurst(alert.localCsis, 26.dp)
                Text(
                    stringResource(R.string.alert_intensity_distance, alert.localCsis.roundToInt().coerceIn(0, 12), alert.distanceKm.roundToInt()),
                    color = WatchColors.Text,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "${PhaseText.shortPlace(alert.place)} M${PhaseText.magnitude(alert.magnitude)}",
                    color = WatchColors.Sub,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                if (alert.drill) {
                    Text(
                        stringResource(R.string.alert_drill),
                        color = WatchColors.OnRedEdge,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(WatchColors.RedEdge)
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
                Icon(painterResource(R.drawable.ic_map), contentDescription = stringResource(R.string.alert_open_map), tint = WatchColors.Sub, modifier = Modifier.size(14.dp))
            }
        }
        EdgeButton(
            onClick = onSafe,
            modifier = Modifier.align(Alignment.BottomCenter),
            buttonSize = EdgeButtonSize.Small,
            colors = ButtonDefaults.buttonColors(containerColor = WatchColors.RedEdge, contentColor = WatchColors.OnRedEdge),
        ) {
            Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.alert_safe), modifier = Modifier.padding(start = 4.dp), maxLines = 1)
        }
    }
}

/** Ring geometry of the design (228 px reference screen). */
private const val REF_SIZE = 228f
private const val REF_STROKE = 10f
private const val TAU = (2 * PI).toFloat()
private const val PHASE_SPEED = 2.4f

private fun DrawScope.drawRing(snap: Countdown.Snapshot, now: Long) {
    val s = size.minDimension
    val k = s / REF_SIZE
    val sw = REF_STROKE * k
    val amp = sw * 0.24f
    val r = s / 2f - sw / 2f - amp - 1f * k
    val c = Offset(size.width / 2f, size.height / 2f)
    val stroke = Stroke(width = sw, cap = StrokeCap.Round)

    if (snap.arrived) {
        // Arrived: the whole track turns red and blinks.
        val on = (now / 500L) % 2L == 0L
        drawCircle(WatchColors.Arrived.copy(alpha = if (on) 1f else 0.35f), radius = r, center = c, style = Stroke(width = sw))
        return
    }
    val frac = snap.fraction
    val gap = sw * 1.1f / r
    val sweep = frac * TAU
    // Track: the rest of the circle, after a gap on both sides.
    if (frac < 1f) {
        val trackStart = sweep + gap
        val trackSweep = TAU - sweep - 2 * gap
        if (trackSweep > 0f) {
            drawArc(
                color = WatchColors.RingTrack,
                startAngle = -90f + Math.toDegrees(trackStart.toDouble()).toFloat(),
                sweepAngle = Math.toDegrees(trackSweep.toDouble()).toFloat(),
                useCenter = false,
                topLeft = Offset(c.x - r, c.y - r),
                size = Size(2 * r, 2 * r),
                style = stroke,
            )
        }
    }
    if (sweep <= 0.001f) return
    val waves = max(6, (TAU * r / (sw * 3.4f)).roundToInt())
    val phase = (now % 100_000L) / 1000f * PHASE_SPEED
    val steps = max(24, (sweep / TAU * 360f * 2f).toInt())
    val path = Path()
    for (i in 0..steps) {
        val t = sweep * i / steps
        val radius = r + amp * sin(waves * t - phase)
        val angle = t - PI.toFloat() / 2f
        val x = c.x + radius * cos(angle)
        val y = c.y + radius * sin(angle)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, WatchColors.RingYellow, style = stroke)
}
