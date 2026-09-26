package com.github.mytv.myearthquakealert.ui.adaptive

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.window.core.layout.WindowWidthSizeClass

enum class LayoutMode {
    COMPACT,
    MEDIUM,
    EXPANDED,
}

@Composable
fun currentLayoutMode(): LayoutMode {
    val widthSizeClass = currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass
    return when {
        widthSizeClass == WindowWidthSizeClass.COMPACT -> LayoutMode.COMPACT
        widthSizeClass == WindowWidthSizeClass.MEDIUM -> LayoutMode.MEDIUM
        else -> LayoutMode.EXPANDED
    }
}
