package com.github.mytv.myearthquakealert.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

// ── CSIS intensity scale (2.0) ──────────────────────────────────────────────
// Low intensities are light cool tones that stay visible on dark maps; from 4
// (felt by most people) the scale turns warm and deepens step by step.
val Csis0 = Color(0xFFC9D1D9)
val Csis1 = Color(0xFFA9C7DD)
val Csis2 = Color(0xFF7FB3D5)
val Csis3 = Color(0xFF4FA88E)
val Csis4 = Color(0xFFE8C300)
val Csis5 = Color(0xFFF7A21B)
val Csis6 = Color(0xFFF07B13)
val Csis7 = Color(0xFFE3501F)
val Csis8 = Color(0xFFCC2A2A)
val Csis9 = Color(0xFFA31631)
val Csis10 = Color(0xFF7A1250)
val Csis11 = Color(0xFF521473)
val Csis12 = Color(0xFF2E0B48)

fun csisColor(intensity: Double): Color {
    return when {
        intensity < 0.5 -> Csis0
        intensity < 1.5 -> Csis1
        intensity < 2.5 -> Csis2
        intensity < 3.5 -> Csis3
        intensity < 4.5 -> Csis4
        intensity < 5.5 -> Csis5
        intensity < 6.5 -> Csis6
        intensity < 7.5 -> Csis7
        intensity < 8.5 -> Csis8
        intensity < 9.5 -> Csis9
        intensity < 10.5 -> Csis10
        intensity < 11.5 -> Csis11
        else -> Csis12
    }
}

// ── Status colors (1.0 chip; 2.0 surfaces use StatusColors) ─────────────────
val ConnectionGreen = Color(0xFF4CAF50)
val ConnectingYellow = Color(0xFFFFC107)
val DisconnectedRed = Color(0xFFF44336)

// ── Seismic wave / alert chrome ────────────────────────────────────────────
val PWaveBlue = Color(0xFF2196F3)
val SWaveRed = Color(0xFFF44336)

/** Alert accent red — full-bleed header bars, epicenter marks. */
val AlertRed = Color(0xFFE60012)

// ── Broadcast-style alert palette (fixed colors, theme-independent) ────────
/** NHK-style panel white for the source-info band. */
val BroadcastWhite = Color(0xFFFFFFFF)
val BroadcastInk = Color(0xFF16191E)
val BroadcastInkSoft = Color(0xFF5C6570)
/** Deep broadcast blue for the countdown band. */
val BroadcastBlue = Color(0xFF0F5FA6)
/** NHK-style caution yellow — tags, highlight zones. */
val CautionYellow = Color(0xFFFFE100)

private val CsisScale = listOf(Csis0, Csis1, Csis2, Csis3, Csis4, Csis5, Csis6, Csis7, Csis8, Csis9, Csis10, Csis11, Csis12)

/** CSIS colour of a whole intensity degree (0–12). */
fun csisColor(intensity: Int): Color = CsisScale[intensity.coerceIn(0, 12)]

/** Readable text colour on top of [background] (dark ink on light CSIS tones, white otherwise). */
fun contentColorOn(background: Color): Color =
    if (background.luminance() > 0.33f) Color(0xFF1A1C1E) else Color.White

/** Roman numerals used for intensity degrees (design: "Ⅴ 度"). */
private val Roman = listOf("0", "Ⅰ", "Ⅱ", "Ⅲ", "Ⅳ", "Ⅴ", "Ⅵ", "Ⅶ", "Ⅷ", "Ⅸ", "Ⅹ", "Ⅺ", "Ⅻ")

fun romanIntensity(intensity: Int): String = Roman[intensity.coerceIn(0, 12)]

// ── Map marks (fixed, theme-independent) ─────────────────────────────────────
/** User position dot ("你的位置"). */
val UserDotBlue = Color(0xFF1E88E5)
/** Neutral land/sea background behind the offline outline. */
val OfflineMapLight = Color(0xFFE6EAF0)
val OfflineMapDark = Color(0xFF1B2A38)
