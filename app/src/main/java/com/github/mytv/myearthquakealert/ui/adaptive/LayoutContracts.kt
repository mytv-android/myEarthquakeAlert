package com.github.mytv.myearthquakealert.ui.adaptive

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect
import com.github.mytv.myearthquakealert.data.model.LayoutOverride
import kotlinx.coroutines.flow.MutableStateFlow

/** The layout the app shell renders (design §2 各形态的导航与布局). */
enum class LayoutKind {
    /** Single column + ShortNavigationBar (< 600 dp, fold outer screen). */
    PHONE,
    /** Navigation rail + two panes (600–840 dp, landscape phone, book posture). */
    MEDIUM,
    /** WideNavigationRail + map + 360 dp side pane (≥ 840 dp). */
    TABLET,
    /** 10-foot UI: navigation drawer, large type, D-pad focus. */
    TV,
    /** Full-screen map + clock + status + latest-quake ticker, screen kept on. */
    KIOSK,
}

/** Fold / dual-screen posture from Jetpack WindowManager's FoldingFeature. */
enum class Posture { FLAT, BOOK, TABLETOP, DUAL }

/** Where the effective layout came from (shown in 界面布局 as "当前自动判定"). */
enum class LayoutSource { AUTO, SETTINGS, SESSION }

@Immutable
data class ResolvedLayout(
    val kind: LayoutKind = LayoutKind.PHONE,
    /** What AUTO would pick on this device right now. */
    val autoKind: LayoutKind = LayoutKind.PHONE,
    val source: LayoutSource = LayoutSource.AUTO,
    val posture: Posture = Posture.FLAT,
    val widthDp: Int = 0,
    val heightDp: Int = 0,
    val isTelevision: Boolean = false,
    val isTouch: Boolean = true,
    /** Hinge / fold bounds in window pixels when separating or occluding. */
    val hingeBounds: Rect? = null,
    val hingeIsVertical: Boolean = false,
)

/** Provided by the app shell; read by settings (界面布局) and screens that adapt. */
val LocalResolvedLayout = staticCompositionLocalOf { ResolvedLayout() }

/**
 * 概览 › 菜单 › 切换布局: applies for this session only and is forgotten when the
 * process ends. Highest priority in LayoutResolver.
 */
object SessionLayout {
    val override = MutableStateFlow<LayoutOverride?>(null)
}
