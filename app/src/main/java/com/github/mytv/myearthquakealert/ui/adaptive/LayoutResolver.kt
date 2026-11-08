package com.github.mytv.myearthquakealert.ui.adaptive

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.window.core.layout.WindowWidthSizeClass

/**
 * Resolves the effective layout: session override › 设置 › device type › fold posture ›
 * width class (design §2 手动切换界面布局). MainActivity provides the result through
 * [LocalResolvedLayout]; other activities (AlertActivity) call this directly.
 */
@Composable
fun rememberResolvedLayout(): ResolvedLayout {
    val width = currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass
    val kind = when (width) {
        WindowWidthSizeClass.COMPACT -> LayoutKind.PHONE
        WindowWidthSizeClass.MEDIUM -> LayoutKind.MEDIUM
        else -> LayoutKind.TABLET
    }
    return ResolvedLayout(kind = kind, autoKind = kind)
}
