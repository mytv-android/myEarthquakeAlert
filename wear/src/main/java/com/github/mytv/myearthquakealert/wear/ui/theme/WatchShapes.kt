package com.github.mytv.myearthquakealert.wear.ui.theme

import android.graphics.Matrix
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.pill
import androidx.graphics.shapes.rectangle
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import androidx.graphics.shapes.transformed
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import com.github.mytv.myearthquakealert.wear.data.WatchPhase
import com.github.mytv.myearthquakealert.wear.presentation.PhaseText
import kotlin.math.floor

/**
 * Expressive shapes of the watch design: one shape per monitoring phase, the loading morph
 * (cookie9 → pentagon → clover4 → gem → sunny → squircle, 650 ms each, back-out easing) for
 * the transitional phases, and the soft-burst intensity badge. All polygons are normalised
 * to the unit square.
 */
object WatchShapes {

    val Cookie9: RoundedPolygon = RoundedPolygon.star(9, radius = 1f, innerRadius = 0.86f, rounding = CornerRounding(0.18f), innerRounding = CornerRounding(0.18f)).normalized()
    val Pentagon: RoundedPolygon = RoundedPolygon(5, radius = 1f, rounding = CornerRounding(0.28f)).rotate(-90f).normalized()
    val Clover4: RoundedPolygon = RoundedPolygon.star(4, radius = 1f, innerRadius = 0.6f, rounding = CornerRounding(0.42f), innerRounding = CornerRounding(0.2f)).rotate(45f).normalized()
    val Gem: RoundedPolygon = RoundedPolygon(6, radius = 1f, rounding = CornerRounding(0.3f, smoothing = 0.6f)).rotate(30f).normalized()
    val Sunny: RoundedPolygon = RoundedPolygon.star(8, radius = 1f, innerRadius = 0.82f, rounding = CornerRounding(0.12f), innerRounding = CornerRounding(0.12f)).normalized()
    val Squircle: RoundedPolygon = RoundedPolygon.rectangle(width = 2f, height = 2f, rounding = CornerRounding(0.62f, smoothing = 1f)).normalized()
    val Triangle: RoundedPolygon = RoundedPolygon(3, radius = 1f, rounding = CornerRounding(0.24f)).rotate(-90f).normalized()
    val Pill: RoundedPolygon = RoundedPolygon.pill(width = 2f, height = 1.24f).normalized()
    val PillVertical: RoundedPolygon = RoundedPolygon.pill(width = 1.24f, height = 2f).normalized()
    val Circle: RoundedPolygon = RoundedPolygon.circle(numVertices = 12, radius = 1f).normalized()
    val SoftBurst: RoundedPolygon = RoundedPolygon.star(10, radius = 1f, innerRadius = 0.78f, rounding = CornerRounding(0.12f), innerRounding = CornerRounding(0.12f)).normalized()

    val Loader: List<RoundedPolygon> = listOf(Cookie9, Pentagon, Clover4, Gem, Sunny, Squircle)

    fun forPhase(phase: WatchPhase): RoundedPolygon = when (phase) {
        WatchPhase.MONITORING -> Cookie9
        WatchPhase.STANDALONE -> Gem
        WatchPhase.DEGRADED -> Clover4
        WatchPhase.LIMITED -> Triangle
        WatchPhase.PAUSED -> PillVertical
        WatchPhase.OFF -> Squircle
        WatchPhase.DISCONNECTED -> Pill
        WatchPhase.CONNECTING, WatchPhase.RECONNECTING, WatchPhase.WAITING -> Circle
    }

    fun isLoading(phase: WatchPhase): Boolean =
        phase == WatchPhase.CONNECTING || phase == WatchPhase.RECONNECTING || phase == WatchPhase.WAITING

    private fun RoundedPolygon.rotate(degrees: Float): RoundedPolygon =
        transformed(Matrix().apply { setRotate(degrees) })
}

/** Approximates the design's back-out easing. */
private val BackOut = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

private fun DrawScope.drawPolygonPath(path: android.graphics.Path, color: Color) {
    val m = Matrix().apply { setScale(size.width, size.height) }
    path.transform(m)
    drawPath(path.asComposePath(), color)
}

/** A unit-square polygon filled with [color] at this canvas' size. */
@Composable
fun PolygonShape(polygon: RoundedPolygon, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawPolygonPath(polygon.toPath(), color)
    }
}

/** The loading morph, looping through [WatchShapes.Loader] while slowly turning. */
@Composable
fun LoadingShape(color: Color, modifier: Modifier = Modifier) {
    val morphs = remember {
        val shapes = WatchShapes.Loader
        shapes.indices.map { Morph(shapes[it], shapes[(it + 1) % shapes.size]) }
    }
    val transition = rememberInfiniteTransition(label = "loader")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = morphs.size.toFloat(),
        animationSpec = infiniteRepeatable(tween(STEP_MS * morphs.size, easing = LinearEasing)),
        label = "morph",
    )
    Canvas(modifier) {
        val index = t.toInt().coerceIn(0, morphs.lastIndex)
        val progress = BackOut.transform((t - floor(t)).coerceIn(0f, 1f))
        rotate(t * 360f / morphs.size / 2f) {
            drawPolygonPath(morphs[index].toPath(progress), color)
        }
    }
}

private const val STEP_MS = 650

/** The phase shape (58 dp in the design) with the phase icon inside. */
@Composable
fun PhaseBadge(phase: WatchPhase, size: Dp = 58.dp, modifier: Modifier = Modifier) {
    val tone = StatusTones.of(phase)
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        if (WatchShapes.isLoading(phase)) {
            LoadingShape(tone.content, Modifier.size(size))
        } else {
            PolygonShape(WatchShapes.forPhase(phase), tone.content, Modifier.size(size))
        }
        Icon(
            painter = painterResource(PhaseText.icon(phase)),
            contentDescription = null,
            tint = tone.container,
            modifier = Modifier.size(size * 0.42f),
        )
    }
}

/** Soft-burst badge with the local intensity (design: 烈度 burst on the alert screen). */
@Composable
fun IntensityBurst(intensity: Double, size: Dp, modifier: Modifier = Modifier) {
    val color = Csis.color(intensity)
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        PolygonShape(WatchShapes.SoftBurst, color, Modifier.size(size))
        Text(
            text = (intensity + 0.5).toInt().coerceIn(0, 12).toString(),
            color = Csis.contentOn(color),
            fontSize = (size.value * 0.42f).sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

/** Rounded magnitude badge of a quake row, coloured by the local intensity. */
@Composable
fun MagnitudeBadge(magnitude: Double, intensity: Double, size: Dp = 34.dp, modifier: Modifier = Modifier) {
    val color = Csis.color(intensity)
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        PolygonShape(WatchShapes.Squircle, color, Modifier.size(size))
        Text(
            text = PhaseText.magnitude(magnitude),
            color = Csis.contentOn(color),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
