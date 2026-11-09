package com.github.mytv.myearthquakealert.widget

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProvider
import androidx.glance.color.ColorProviders
import androidx.glance.material3.ColorProviders as material3Providers
import androidx.glance.unit.ColorProvider
import com.github.mytv.myearthquakealert.data.model.ThemeMode
import com.github.mytv.myearthquakealert.service.MonitorPhase
import com.github.mytv.myearthquakealert.ui.theme.DarkStatusColors
import com.github.mytv.myearthquakealert.ui.theme.EeqDarkColorScheme
import com.github.mytv.myearthquakealert.ui.theme.EeqLightColorScheme
import com.github.mytv.myearthquakealert.ui.theme.LightStatusColors
import com.github.mytv.myearthquakealert.ui.theme.StatusColors

/**
 * The app's fixed slate-blue schemes (never wallpaper colours) as Glance colour providers,
 * day / night resolved by the launcher. 设置 › 显示 › 主题 forced light or dark applies to the
 * widget too; 跟随系统 follows the launcher's night mode.
 */
internal class WidgetPalette(themeMode: ThemeMode) {
    private val day: ColorScheme = if (themeMode == ThemeMode.DARK) EeqDarkColorScheme else EeqLightColorScheme
    private val night: ColorScheme = if (themeMode == ThemeMode.LIGHT) EeqLightColorScheme else EeqDarkColorScheme
    private val dayStatus: StatusColors = if (themeMode == ThemeMode.DARK) DarkStatusColors else LightStatusColors
    private val nightStatus: StatusColors = if (themeMode == ThemeMode.LIGHT) LightStatusColors else DarkStatusColors

    /** For GlanceTheme. */
    val providers: ColorProviders = material3Providers(light = day, dark = night)

    val background = of { it.surfaceContainerLow }
    val onSurface = of { it.onSurface }
    val onSurfaceVariant = of { it.onSurfaceVariant }
    val primary = of { it.primary }
    val tonal = of { it.secondaryContainer }
    val onTonal = of { it.onSecondaryContainer }

    /** Container colour of the status shape for [phase] (same mapping as StatusShape). */
    fun phaseContainer(phase: MonitorPhase): ColorProvider = when (phase) {
        MonitorPhase.MONITORING -> status { it.ok }
        MonitorPhase.CONNECTING -> of { it.primary }
        MonitorPhase.RECONNECTING, MonitorPhase.DEGRADED, MonitorPhase.PAUSED -> status { it.warn }
        MonitorPhase.LIMITED -> of { it.error }
        MonitorPhase.OFF -> of { it.surfaceContainerHighest }
    }

    /** Icon colour on [phaseContainer]. */
    fun phaseContent(phase: MonitorPhase): ColorProvider = when (phase) {
        MonitorPhase.MONITORING -> status { it.onOk }
        MonitorPhase.CONNECTING -> of { it.onPrimary }
        MonitorPhase.RECONNECTING, MonitorPhase.DEGRADED, MonitorPhase.PAUSED -> status { it.onWarn }
        MonitorPhase.LIMITED -> of { it.onError }
        MonitorPhase.OFF -> of { it.onSurfaceVariant }
    }

    /** Text colour for the phase title: the status colour where it reads on the background. */
    fun phaseText(phase: MonitorPhase): ColorProvider = when (phase) {
        MonitorPhase.MONITORING -> status { it.ok }
        MonitorPhase.RECONNECTING, MonitorPhase.DEGRADED, MonitorPhase.PAUSED -> status { it.warn }
        MonitorPhase.LIMITED -> of { it.error }
        MonitorPhase.CONNECTING, MonitorPhase.OFF -> onSurface
    }

    private inline fun of(role: (ColorScheme) -> Color): ColorProvider = ColorProvider(day = role(day), night = role(night))

    private inline fun status(role: (StatusColors) -> Color): ColorProvider =
        ColorProvider(day = role(dayStatus), night = role(nightStatus))
}
