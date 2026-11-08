package com.github.mytv.myearthquakealert.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

val EeqTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)

object EeqEmphasizedTypography {
    val headlineLarge = EeqTypography.headlineLarge.copy(fontWeight = FontWeight.Bold)
    val titleMedium = EeqTypography.titleMedium.copy(fontWeight = FontWeight.Bold)
    val bodyLarge = EeqTypography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
    val labelLarge = EeqTypography.labelLarge.copy(fontWeight = FontWeight.Bold)
}

/**
 * Number styles (design §9 字体 · 数值): extra-bold with tabular figures so values do not
 * jitter while they update.
 */
object EeqNumberStyles {
    private const val TABULAR = "tnum"
    /** Magnitude in lists ("M5.3"), tile values ("18 s"). */
    val value = EeqTypography.titleLarge.copy(fontWeight = FontWeight.ExtraBold, fontFeatureSettings = TABULAR)
    /** Intensity badges. */
    val badge = EeqTypography.titleMedium.copy(fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, fontFeatureSettings = TABULAR)
    /** Large clock of the kiosk / TV header. */
    val clock = EeqTypography.displayMedium.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = TABULAR)
    val tabular = EeqTypography.bodyMedium.copy(fontFeatureSettings = TABULAR)
}

/** Scales every size of [this] typography (10-foot TV type is ~12 % larger). */
fun Typography.scaled(factor: Float): Typography {
    fun TextStyle.s() = copy(fontSize = fontSize * factor, lineHeight = lineHeight.scale(factor))
    return Typography(
        displayLarge = displayLarge.s(), displayMedium = displayMedium.s(), displaySmall = displaySmall.s(),
        headlineLarge = headlineLarge.s(), headlineMedium = headlineMedium.s(), headlineSmall = headlineSmall.s(),
        titleLarge = titleLarge.s(), titleMedium = titleMedium.s(), titleSmall = titleSmall.s(),
        bodyLarge = bodyLarge.s(), bodyMedium = bodyMedium.s(), bodySmall = bodySmall.s(),
        labelLarge = labelLarge.s(), labelMedium = labelMedium.s(), labelSmall = labelSmall.s(),
    )
}

private fun TextUnit.scale(factor: Float): TextUnit = if (this == TextUnit.Unspecified) this else this * factor

/** Typography of the TV layout (design §6 电视 · 10 英尺字号). */
val EeqTvTypography = EeqTypography.scaled(1.12f)
