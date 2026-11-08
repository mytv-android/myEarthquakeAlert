package com.github.mytv.myearthquakealert.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic status colours that Material's scheme lacks (design §9 颜色角色): ok (监测中),
 * warn (重连 / 备用通道 / 暂停). Error and off use colorScheme.error / surfaceContainerHighest.
 */
@Immutable
data class StatusColors(
    val ok: Color,
    val onOk: Color,
    val okContainer: Color,
    val onOkContainer: Color,
    val warn: Color,
    val onWarn: Color,
    val warnContainer: Color,
    val onWarnContainer: Color,
)

val LightStatusColors = StatusColors(
    ok = Color(0xFF1D7748), onOk = Color.White,
    okContainer = Color(0xFFC9EED6), onOkContainer = Color(0xFF00210F),
    warn = Color(0xFF835800), onWarn = Color.White,
    warnContainer = Color(0xFFFFE2A6), onWarnContainer = Color(0xFF2A1800),
)

val DarkStatusColors = StatusColors(
    ok = Color(0xFF86D8A8), onOk = Color(0xFF00391D),
    okContainer = Color(0xFF0F4D30), onOkContainer = Color(0xFFC9EED6),
    warn = Color(0xFFF5C25B), onWarn = Color(0xFF452B00),
    warnContainer = Color(0xFF5A3E00), onWarnContainer = Color(0xFFFFE2A6),
)

val LocalStatusColors = staticCompositionLocalOf { LightStatusColors }

/** `MaterialTheme.status.ok` etc. Provided by [MyEarthQuakeAlertTheme]. */
val MaterialTheme.status: StatusColors
    @Composable @ReadOnlyComposable get() = LocalStatusColors.current
