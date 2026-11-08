package com.github.mytv.myearthquakealert.ui.alert

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.mytv.myearthquakealert.data.model.EewEvent
import com.github.mytv.myearthquakealert.domain.AlertEvaluator
import com.github.mytv.myearthquakealert.domain.SeismicCalculator
import com.github.mytv.myearthquakealert.service.ActiveAlertHolder
import com.github.mytv.myearthquakealert.service.AlertData
import com.github.mytv.myearthquakealert.service.AlertTier
import com.github.mytv.myearthquakealert.ui.adaptive.LayoutKind
import com.github.mytv.myearthquakealert.ui.adaptive.LocalResolvedLayout
import com.github.mytv.myearthquakealert.ui.adaptive.Posture
import com.github.mytv.myearthquakealert.ui.adaptive.ResolvedLayout
import com.github.mytv.myearthquakealert.ui.map.EewMapStyle
import com.github.mytv.myearthquakealert.ui.theme.MyEarthQuakeAlertTheme

/**
 * Earthquake alert surface (design §4 预警体验). Picks a presentation from the tier and
 * the device — floating card, full screen or TV subtitle bar — and runs the countdown
 * from the origin time (corrected by the NTP offset) to the S-wave arrival.
 *
 * 静音 calls [ActiveAlertHolder.mute]; 我已安全 and 关闭 call [onDismiss]; Back dismisses
 * only when [allowBackDismiss] (it is consumed either way where a back dispatcher exists).
 *
 * @param wideLayout the host window is wide (landscape overlay); a STRONG alert then takes
 *   over the full screen, otherwise it is shown as the card.
 * @param presentation forces a presentation; AlertActivity always uses FULL_SCREEN.
 * @param layout device layout (posture, hinge, TV); defaults to [LocalResolvedLayout]
 *   with TV detection, for hosts without the app shell (the overlay service).
 * @param tabletopSplit 设置 › 桌面姿态分屏: split at a horizontal hinge in tabletop posture.
 * @param onOpenDetails 详情 / 查看详情; opens the app when null.
 */
@Composable
fun AlertOverlay(
    alertData: AlertData,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    allowBackDismiss: Boolean = true,
    intenseThreshold: Int = 5,
    mapStyle: EewMapStyle = EewMapStyle.AMAP,
    wideLayout: Boolean = true,
    presentation: AlertPresentation? = null,
    layout: ResolvedLayout? = null,
    tabletopSplit: Boolean = true,
    onOpenDetails: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val shellLayout = LocalResolvedLayout.current
    val resolved = layout ?: remember(context, shellLayout) {
        if (!shellLayout.isTelevision && isTelevisionDevice(context)) {
            shellLayout.copy(isTelevision = true, isTouch = false)
        } else {
            shellLayout
        }
    }
    val tier = if (AlertEvaluator.isIntense(alertData.localCsis, intenseThreshold)) AlertTier.STRONG else alertData.tier
    val mode = presentation ?: alertPresentation(tier, wideLayout, resolved.isTelevision)
    val timeline by rememberAlertTimeline(alertData)
    val muted by ActiveAlertHolder.muted.collectAsState()
    val openDetails = onOpenDetails ?: { openAppFromAlert(context) }
    val onMute = { ActiveAlertHolder.mute() }

    if (LocalOnBackPressedDispatcherOwner.current != null) {
        BackHandler { if (allowBackDismiss) onDismiss() }
    }

    when (mode) {
        AlertPresentation.CARD -> {
            val focus = remember { FocusRequester() }
            val wantsFocus = resolved.isTelevision || !resolved.isTouch
            AlertCard(
                alert = alertData,
                timeline = timeline,
                muted = muted,
                showMute = tier == AlertTier.STRONG,
                mapStyle = mapStyle,
                intenseThreshold = intenseThreshold,
                onSafe = onDismiss,
                onClose = onDismiss,
                onDetails = openDetails,
                onMute = onMute,
                modifier = modifier,
                focusRequester = if (wantsFocus) focus else null,
            )
            if (wantsFocus) LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        }
        AlertPresentation.FULL_SCREEN -> AlertFullScreen(
            alert = alertData,
            timeline = timeline,
            muted = muted,
            layout = resolved,
            mapStyle = mapStyle,
            intenseThreshold = intenseThreshold,
            tabletopSplit = tabletopSplit,
            onSafe = onDismiss,
            onMute = onMute,
            onClose = onDismiss,
            onDetails = {
                openDetails()
                onDismiss()
            },
            modifier = modifier,
        )
        AlertPresentation.TV_TICKER -> AlertTicker(
            alert = alertData,
            timeline = timeline,
            mapStyle = mapStyle,
            intenseThreshold = intenseThreshold,
            allowBackDismiss = allowBackDismiss,
            onDismiss = onDismiss,
            modifier = modifier,
        )
    }
}

// ---------------------------------------------------------------- previews

private fun previewAlert(tier: AlertTier, elapsedSeconds: Double, final: Boolean = false): AlertData {
    val depth = 17.0
    val distance = 110.0
    val p = SeismicCalculator.calcWaveArrival(depth, distance)
    val presentedAt = System.currentTimeMillis() - (elapsedSeconds * 1000).toLong()
    return AlertData(
        event = EewEvent(
            id = "preview-2",
            eventId = "preview",
            source = "CENC",
            reportTime = "",
            reportNum = if (final) 3 else 2,
            originTime = "",
            hypocenter = "四川雅安市芦山县",
            latitude = 30.37,
            longitude = 102.94,
            magnitude = 6.1,
            depth = depth,
            maxIntensity = 8.0,
            isFinal = final,
        ),
        userLatitude = 30.66,
        userLongitude = 104.07,
        pWaveSeconds = p.pWaveSeconds,
        sWaveSeconds = p.sWaveSeconds,
        localCsis = SeismicCalculator.calcLocalIntensity(6.1, depth, distance),
        distanceKm = distance,
        isSimulation = true,
        tier = tier,
        presentedAt = presentedAt,
    )
}

@Preview(name = "Full screen · phone", widthDp = 412, heightDp = 892)
@Composable
private fun AlertPhonePreview() {
    MyEarthQuakeAlertTheme(darkTheme = true) {
        AlertOverlay(previewAlert(AlertTier.STRONG, 8.0), onDismiss = {}, presentation = AlertPresentation.FULL_SCREEN)
    }
}

@Preview(name = "Full screen · arrived", widthDp = 412, heightDp = 892)
@Composable
private fun AlertArrivedPreview() {
    MyEarthQuakeAlertTheme(darkTheme = true) {
        AlertOverlay(previewAlert(AlertTier.STRONG, 40.0), onDismiss = {}, presentation = AlertPresentation.FULL_SCREEN)
    }
}

@Preview(name = "Full screen · summary", widthDp = 412, heightDp = 892)
@Composable
private fun AlertSummaryPreview() {
    MyEarthQuakeAlertTheme(darkTheme = true) {
        AlertOverlay(previewAlert(AlertTier.STRONG, 120.0, final = true), onDismiss = {}, presentation = AlertPresentation.FULL_SCREEN)
    }
}

@Preview(name = "Full screen · wide", widthDp = 1280, heightDp = 800)
@Composable
private fun AlertWidePreview() {
    MyEarthQuakeAlertTheme(darkTheme = true) {
        AlertOverlay(
            previewAlert(AlertTier.STRONG, 8.0),
            onDismiss = {},
            presentation = AlertPresentation.FULL_SCREEN,
            layout = ResolvedLayout(kind = LayoutKind.TABLET, autoKind = LayoutKind.TABLET, widthDp = 1280, heightDp = 800),
        )
    }
}

@Preview(name = "Full screen · tabletop", widthDp = 720, heightDp = 640)
@Composable
private fun AlertTabletopPreview() {
    MyEarthQuakeAlertTheme(darkTheme = true) {
        AlertOverlay(
            previewAlert(AlertTier.STRONG, 8.0),
            onDismiss = {},
            presentation = AlertPresentation.FULL_SCREEN,
            layout = ResolvedLayout(kind = LayoutKind.MEDIUM, posture = Posture.TABLETOP, widthDp = 720, heightDp = 640),
        )
    }
}

@Preview(name = "Card", widthDp = 412, heightDp = 420)
@Composable
private fun AlertCardPreview() {
    MyEarthQuakeAlertTheme(darkTheme = true) {
        Box(Modifier.padding(12.dp)) {
            AlertOverlay(previewAlert(AlertTier.REMIND, 6.0), onDismiss = {}, wideLayout = false)
        }
    }
}

@Preview(name = "TV ticker", widthDp = 960, heightDp = 540)
@Composable
private fun AlertTickerPreview() {
    MyEarthQuakeAlertTheme(darkTheme = true) {
        Box(Modifier.fillMaxSize()) {
            AlertOverlay(
                previewAlert(AlertTier.REMIND, 6.0),
                onDismiss = {},
                presentation = AlertPresentation.TV_TICKER,
                layout = ResolvedLayout(kind = LayoutKind.TV, autoKind = LayoutKind.TV, isTelevision = true, isTouch = false),
            )
        }
    }
}
