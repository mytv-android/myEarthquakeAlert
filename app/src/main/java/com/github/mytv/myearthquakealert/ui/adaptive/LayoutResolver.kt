package com.github.mytv.myearthquakealert.ui.adaptive

import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowDpSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.github.mytv.myearthquakealert.MyEarthQuakeAlertApp
import com.github.mytv.myearthquakealert.data.model.LayoutOverride
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * A fold or hinge reported by Jetpack WindowManager, reduced to what the resolver needs.
 * [bounds] are window pixels.
 */
@Immutable
data class FoldInfo(
    val halfOpened: Boolean,
    val vertical: Boolean,
    val separating: Boolean,
    val occludesFully: Boolean,
    val bounds: Rect,
)

/** Everything the layout decision depends on; [LayoutResolver.resolve] is a pure function of it. */
@Immutable
data class LayoutInputs(
    val widthDp: Int,
    val heightDp: Int,
    val isTelevision: Boolean = false,
    val isTouch: Boolean = true,
    val folds: List<FoldInfo> = emptyList(),
    val settingsOverride: LayoutOverride = LayoutOverride.AUTO,
    val sessionOverride: LayoutOverride? = null,
)

/**
 * How the shell arranges the destinations around a fold or hinge. Postures only apply to the
 * automatic layout: a layout the user picked by hand is rendered as picked.
 */
enum class ShellArrangement {
    /** Navigation component of [ResolvedLayout.kind] + destination content. */
    STANDARD,
    /** 书本: two panes split exactly at the vertical fold. */
    BOOK_SPLIT,
    /** 桌面: map on the upper half, status and controls on the lower half. */
    TABLETOP_SPLIT,
    /** 双屏: one pane per screen, nothing under the hinge. */
    DUAL_SPAN,
    /** 双屏时只用一块屏: the start screen only. */
    DUAL_SINGLE,
}

/**
 * Layout decision (design §2 手动切换界面布局): 本次会话临时指定 › 设置中的持久指定 ›
 * 设备类型（电视）› 折叠姿态 › 窗口宽度级别.
 */
object LayoutResolver {
    const val MEDIUM_MIN_WIDTH_DP = 600
    const val EXPANDED_MIN_WIDTH_DP = 840

    fun widthKind(widthDp: Int): LayoutKind = when {
        widthDp < MEDIUM_MIN_WIDTH_DP -> LayoutKind.PHONE
        widthDp < EXPANDED_MIN_WIDTH_DP -> LayoutKind.MEDIUM
        else -> LayoutKind.TABLET
    }

    /** BOOK = half-opened vertical fold, TABLETOP = half-opened horizontal fold, DUAL = separating hinge while flat. */
    fun posture(folds: List<FoldInfo>): Pair<Posture, FoldInfo?> {
        val fold = folds.firstOrNull() ?: return Posture.FLAT to null
        return when {
            fold.halfOpened && fold.vertical -> Posture.BOOK to fold
            fold.halfOpened -> Posture.TABLETOP to fold
            fold.separating -> Posture.DUAL to fold
            else -> Posture.FLAT to fold
        }
    }

    fun autoKind(isTelevision: Boolean, posture: Posture, widthDp: Int): LayoutKind = when {
        isTelevision -> LayoutKind.TV
        // 书本姿态: navigation rail + list | detail on either side of the fold.
        posture == Posture.BOOK -> if (widthDp < MEDIUM_MIN_WIDTH_DP) LayoutKind.PHONE else LayoutKind.MEDIUM
        else -> widthKind(widthDp)
    }

    private fun LayoutOverride.toKind(): LayoutKind? = when (this) {
        LayoutOverride.AUTO -> null
        LayoutOverride.PHONE -> LayoutKind.PHONE
        LayoutOverride.TABLET -> LayoutKind.TABLET
        LayoutOverride.TV -> LayoutKind.TV
        LayoutOverride.KIOSK -> LayoutKind.KIOSK
    }

    fun resolve(inputs: LayoutInputs): ResolvedLayout {
        val (posture, fold) = posture(inputs.folds)
        val auto = autoKind(inputs.isTelevision, posture, inputs.widthDp)
        val session = inputs.sessionOverride
        val (kind, source) = when {
            // A session pick of "自动" also overrides a persistent pick, for this session.
            session == LayoutOverride.AUTO -> auto to LayoutSource.AUTO
            session != null -> session.toKind()!! to LayoutSource.SESSION
            inputs.settingsOverride != LayoutOverride.AUTO -> inputs.settingsOverride.toKind()!! to LayoutSource.SETTINGS
            else -> auto to LayoutSource.AUTO
        }
        val hinge = fold?.takeIf { it.separating || it.occludesFully }
        return ResolvedLayout(
            kind = kind,
            autoKind = auto,
            source = source,
            posture = posture,
            widthDp = inputs.widthDp,
            heightDp = inputs.heightDp,
            isTelevision = inputs.isTelevision,
            isTouch = inputs.isTouch,
            hingeBounds = hinge?.bounds,
            hingeIsVertical = hinge?.vertical ?: false,
        )
    }

    /** Applies the fold and dual-screen switches (跨铰链分栏 / 桌面模式预警分屏 / 双屏时只用一块屏). */
    fun arrangement(
        layout: ResolvedLayout,
        spanHinge: Boolean,
        tabletopSplit: Boolean,
        singleScreen: Boolean,
    ): ShellArrangement {
        if (layout.source != LayoutSource.AUTO) return ShellArrangement.STANDARD
        if (layout.kind == LayoutKind.TV || layout.kind == LayoutKind.KIOSK) return ShellArrangement.STANDARD
        val hinge = layout.hingeBounds ?: return ShellArrangement.STANDARD
        if (hinge == Rect.Zero) return ShellArrangement.STANDARD
        return when (layout.posture) {
            Posture.BOOK -> if (spanHinge && layout.hingeIsVertical) ShellArrangement.BOOK_SPLIT else ShellArrangement.STANDARD
            Posture.TABLETOP -> if (tabletopSplit && !layout.hingeIsVertical) ShellArrangement.TABLETOP_SPLIT else ShellArrangement.STANDARD
            Posture.DUAL -> when {
                singleScreen -> ShellArrangement.DUAL_SINGLE
                spanHinge -> ShellArrangement.DUAL_SPAN
                else -> ShellArrangement.STANDARD
            }
            Posture.FLAT -> ShellArrangement.STANDARD
        }
    }
}

/**
 * Resolves the effective layout: session override › 设置 › device type › fold posture ›
 * width class (design §2 手动切换界面布局). MainActivity provides the result through
 * [LocalResolvedLayout]; other activities (AlertActivity) call this directly and render
 * from [ResolvedLayout.autoKind] / [ResolvedLayout.posture] — alerts ignore overrides.
 *
 * @param settingsOverride the persistent 界面布局 choice when the caller already has it;
 *   null reads it from the settings repository.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun rememberResolvedLayout(settingsOverride: LayoutOverride? = null): ResolvedLayout {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val windowSize = currentWindowDpSize()

    val storedOverride by remember(context, settingsOverride) {
        val app = context.applicationContext as? MyEarthQuakeAlertApp
        when {
            settingsOverride != null -> flowOf(settingsOverride)
            app != null -> app.settingsRepository.settings.map { it.layoutOverride }
            else -> flowOf(LayoutOverride.AUTO)
        }
    }.collectAsState(initial = settingsOverride ?: LayoutOverride.AUTO)
    val session by SessionLayout.override.collectAsState()

    val activity = remember(context) { context.findActivity() }
    val folds by remember(activity) {
        if (activity == null) {
            emptyFlow()
        } else {
            WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity).map { info ->
                info.displayFeatures.filterIsInstance<FoldingFeature>().map { it.toFoldInfo() }
            }
        }
    }.collectAsState(initial = emptyList())

    val isTelevision = remember(context, configuration.uiMode) { isTelevision(context, configuration) }
    val isTouch = remember(context, configuration.touchscreen) {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN) &&
            configuration.touchscreen != Configuration.TOUCHSCREEN_NOTOUCH
    }

    val inputs = LayoutInputs(
        widthDp = windowSize.width.value.toInt(),
        heightDp = windowSize.height.value.toInt(),
        isTelevision = isTelevision,
        isTouch = isTouch,
        folds = folds,
        settingsOverride = storedOverride,
        sessionOverride = session,
    )
    return remember(inputs) { LayoutResolver.resolve(inputs) }
}

private fun FoldingFeature.toFoldInfo(): FoldInfo {
    val b = bounds
    return FoldInfo(
        halfOpened = state == FoldingFeature.State.HALF_OPENED,
        vertical = orientation == FoldingFeature.Orientation.VERTICAL,
        separating = isSeparating,
        occludesFully = occlusionType == FoldingFeature.OcclusionType.FULL,
        bounds = Rect(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat()),
    )
}

/** UI_MODE_TYPE_TELEVISION or a leanback device (design §2: 电视 = UI_MODE_TYPE_TELEVISION). */
fun isTelevision(context: Context, configuration: Configuration = context.resources.configuration): Boolean {
    val uiModeType = configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
    val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    return uiModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
        uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
}

internal fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
