package com.github.mytv.myearthquakealert.ui.components

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import com.github.mytv.myearthquakealert.service.MonitorPhase
import com.github.mytv.myearthquakealert.ui.theme.status

/**
 * Shape + colour + icon of a monitoring phase (design §3 状态的视觉编码). Phases are told
 * apart by shape alone, so colour-blind users can read them too.
 */
@Immutable
data class PhaseStyle(
    val polygon: RoundedPolygon,
    val container: Color,
    val content: Color,
    val icon: ImageVector?,
    val spinning: Boolean,
    val loading: Boolean,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun phaseStyle(phase: MonitorPhase): PhaseStyle {
    val s = MaterialTheme.status
    val c = MaterialTheme.colorScheme
    return when (phase) {
        MonitorPhase.MONITORING -> PhaseStyle(MaterialShapes.Cookie9Sided, s.ok, s.onOk, Icons.Filled.Sensors, spinning = true, loading = false)
        MonitorPhase.CONNECTING -> PhaseStyle(MaterialShapes.Cookie9Sided, c.primary, c.onPrimary, null, spinning = false, loading = true)
        MonitorPhase.RECONNECTING -> PhaseStyle(MaterialShapes.Clover4Leaf, s.warn, s.onWarn, Icons.Filled.Sync, spinning = false, loading = true)
        MonitorPhase.DEGRADED -> PhaseStyle(MaterialShapes.Gem, s.warn, s.onWarn, Icons.Filled.SyncProblem, spinning = false, loading = false)
        MonitorPhase.LIMITED -> PhaseStyle(MaterialShapes.Triangle, c.error, c.onError, Icons.Filled.Warning, spinning = false, loading = false)
        MonitorPhase.PAUSED -> PhaseStyle(MaterialShapes.Pill, s.warn, s.onWarn, Icons.Filled.Pause, spinning = false, loading = false)
        MonitorPhase.OFF -> PhaseStyle(MaterialShapes.Square, c.surfaceContainerHighest, c.onSurfaceVariant, Icons.Filled.PowerSettingsNew, spinning = false, loading = false)
    }
}

/**
 * The status shape used on the overview card, diagnostics header, settings, TV and
 * kiosk. MONITORING slowly rotates (unless animations are off); CONNECTING and
 * RECONNECTING show the M3E morphing loading indicator.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StatusShape(
    phase: MonitorPhase,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
) {
    val style = phaseStyle(phase)
    val container by animateColorAsState(style.container, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "statusContainer")
    val animationsOn = rememberAnimationsEnabled()
    val rotation = if (style.spinning && animationsOn) {
        val transition = rememberInfiniteTransition(label = "statusSpin")
        val angle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Restart),
            label = "statusAngle",
        )
        angle
    } else {
        0f
    }
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        if (style.loading) {
            LoadingIndicator(modifier = Modifier.fillMaxSize(), color = container)
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .rotate(rotation)
                    .clip(style.polygon.toShape())
                    .background(container)
            )
        }
        style.icon?.let {
            Icon(it, contentDescription = null, tint = if (style.loading) MaterialTheme.colorScheme.surface else style.content, modifier = Modifier.size(size * 0.42f))
        }
    }
}

/** False when the user turned animations off (开发者选项 / 无障碍 › 移除动画). */
@Composable
fun rememberAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
}
