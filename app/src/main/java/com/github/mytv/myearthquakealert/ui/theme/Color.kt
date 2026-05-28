package com.github.mytv.myearthquakealert.ui.theme

import androidx.compose.ui.graphics.Color

val Csis0 = Color(0xFFCCCCCC)
val Csis1 = Color(0xFF6A7828)
val Csis2 = Color(0xFF4A7A2E)
val Csis3 = Color(0xFF2E8B57)
val Csis4 = Color(0xFFE8C800)
val Csis5 = Color(0xFFF5A623)
val Csis6 = Color(0xFFF57C00)
val Csis7 = Color(0xFFE64A19)
val Csis8 = Color(0xFFD32F2F)
val Csis9 = Color(0xFFB71C1C)
val Csis10 = Color(0xFF880E4F)
val Csis11 = Color(0xFF4A148C)
val Csis12 = Color(0xFF8B0000)

val ConnectionGreen = Color(0xFF4CAF50)
val ConnectingYellow = Color(0xFFFFC107)
val DisconnectedRed = Color(0xFFF44336)

val PWaveBlue = Color(0xFF2196F3)
val SWaveRed = Color(0xFFF44336)

val AlertRed = Color(0xFFE60012)
val AlertBlue = Color(0xFF1565C0)
val AlertMapBackground = Color(0xFF212121)

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
