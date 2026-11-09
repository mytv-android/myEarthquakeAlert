package com.github.mytv.myearthquakealert.wear.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import com.github.mytv.myearthquakealert.wear.data.WatchPhase

/** Watch design tokens (fx-dark / watch of the 2.0 design). */
object WatchColors {
    val Background = Color(0xFF000000)
    val Text = Color(0xFFE8EAED)
    val Sub = Color(0xFFAEB4BB)
    val Chip = Color(0xFF1F2429)
    val EdgeButton = Color(0xFFA8C8F0)
    val OnEdgeButton = Color(0xFF0B2440)
    val RedEdge = Color(0xFFFFB4AB)
    val OnRedEdge = Color(0xFF5C0008)
    val AlertBackground = Color(0xFF1A0A0B)
    val AlertLabel = Color(0xFFFFC9C4)
    val RingYellow = Color(0xFFFFE100)
    val RingTrack = Color(0x29FFFFFF)
    val Arrived = Color(0xFFFF6B5E)
    val PWave = Color(0xFF2196F3)
    val SWave = Color(0xFFF44336)
    val AlertRed = Color(0xFFE60012)
    val UserDot = Color(0xFF1E88E5)
    val Outline = Color(0xFF8B9198)
    val OutlineVariant = Color(0xFF41474D)
}

/** Container / content pair of a status tone. */
@Immutable
data class Tone(val container: Color, val content: Color)

object StatusTones {
    val Ok = Tone(Color(0xFF00391D), Color(0xFF86D8A8))
    val Warn = Tone(Color(0xFF452B00), Color(0xFFF5C25B))
    val Err = Tone(Color(0xFF601410), Color(0xFFF2B8B5))
    val Primary = Tone(Color(0xFF174A73), Color(0xFFD2E4F7))
    val Off = Tone(Color(0xFF2F363E), Color(0xFFC1C7CE))

    /** The vivid colour of the phase shape (the content colour of its tone). */
    fun of(phase: WatchPhase): Tone = when (phase) {
        WatchPhase.MONITORING, WatchPhase.STANDALONE -> Ok
        WatchPhase.CONNECTING, WatchPhase.RECONNECTING, WatchPhase.DEGRADED, WatchPhase.LIMITED, WatchPhase.WAITING -> Warn
        WatchPhase.PAUSED -> Primary
        WatchPhase.DISCONNECTED -> Err
        WatchPhase.OFF -> Off
    }
}

/** CSIS 2.0 intensity scale, copied from `:app` `ui.theme.Color` (keep in sync). */
object Csis {
    private val scale = listOf(
        Color(0xFFC9D1D9), Color(0xFFA9C7DD), Color(0xFF7FB3D5), Color(0xFF4FA88E),
        Color(0xFFE8C300), Color(0xFFF7A21B), Color(0xFFF07B13), Color(0xFFE3501F),
        Color(0xFFCC2A2A), Color(0xFFA31631), Color(0xFF7A1250), Color(0xFF521473),
        Color(0xFF2E0B48),
    )

    fun color(intensity: Double): Color = scale[(intensity + 0.5).toInt().coerceIn(0, 12)]

    /** Readable text on a CSIS colour (dark ink on light tones, white otherwise). */
    fun contentOn(background: Color): Color = if (background.luminance() > 0.33f) Color(0xFF1A1C1E) else Color.White

    /** Intensity colour of a magnitude badge in the quake list (magnitude ≈ intensity scale). */
    fun forMagnitude(magnitude: Double): Color = color(magnitude)
}

private val WatchColorScheme = ColorScheme(
    primary = Color(0xFF9BCBF9),
    primaryDim = Color(0xFF7FB0DD),
    primaryContainer = Color(0xFF174A73),
    onPrimary = Color(0xFF00325A),
    onPrimaryContainer = Color(0xFFD2E4F7),
    secondary = Color(0xFFB9C8DA),
    secondaryDim = Color(0xFF9DACBE),
    secondaryContainer = Color(0xFF394857),
    onSecondary = Color(0xFF233240),
    onSecondaryContainer = Color(0xFFD5E3F1),
    surfaceContainerLow = Color(0xFF14181C),
    surfaceContainer = WatchColors.Chip,
    surfaceContainerHigh = Color(0xFF2A3036),
    onSurface = WatchColors.Text,
    onSurfaceVariant = WatchColors.Sub,
    outline = WatchColors.Outline,
    outlineVariant = WatchColors.OutlineVariant,
    background = WatchColors.Background,
    onBackground = WatchColors.Text,
    error = Color(0xFFF2B8B5),
    errorDim = Color(0xFFE09A96),
    errorContainer = Color(0xFF8C1D18),
    onError = Color(0xFF601410),
    onErrorContainer = Color(0xFFF9DEDC),
)

@Composable
fun WatchTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = WatchColorScheme, content = content)
}
