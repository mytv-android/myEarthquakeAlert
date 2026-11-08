package com.github.mytv.myearthquakealert.ui.alert

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.service.AlertData
import com.github.mytv.myearthquakealert.ui.adaptive.Posture
import com.github.mytv.myearthquakealert.ui.adaptive.ResolvedLayout
import com.github.mytv.myearthquakealert.ui.map.EewMapStyle
import com.github.mytv.myearthquakealert.ui.settings.components.rememberFormats
import kotlin.math.min

/** Callbacks and state shared by every full-screen arrangement. */
internal class AlertActions(
    val muted: Boolean,
    val onSafe: () -> Unit,
    val onMute: () -> Unit,
    val onClose: () -> Unit,
    val onDetails: () -> Unit,
    /** Set on TV and keyboard devices: 我已安全 takes the initial focus. */
    val focus: FocusRequester?,
)

/**
 * Full-screen alert (STRONG tier, lock screen, AlertActivity and wide overlay windows).
 * Arrangement follows the device: phone portrait (prototype scrAlertPhone), wide and
 * dual-screen (scrDuo: map on one side, countdown and actions on the other, split at a
 * vertical hinge), tabletop (scrTabletop: map and countdown above a horizontal hinge,
 * big buttons below) and TV (wide with large type and D-pad focus). Sixty seconds after
 * the S wave arrived it turns into the summary card.
 */
@Composable
internal fun AlertFullScreen(
    alert: AlertData,
    timeline: AlertTimeline,
    muted: Boolean,
    layout: ResolvedLayout,
    mapStyle: EewMapStyle,
    intenseThreshold: Int,
    tabletopSplit: Boolean,
    onSafe: () -> Unit,
    onMute: () -> Unit,
    onClose: () -> Unit,
    onDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val wantsFocus = layout.isTelevision || !layout.isTouch
    val focus = remember { FocusRequester() }
    val actions = AlertActions(muted, onSafe, onMute, onClose, onDetails, if (wantsFocus) focus else null)
    val summary = timeline.summaryDue
    val tabletop = layout.posture == Posture.TABLETOP && tabletopSplit && !layout.hingeIsVertical

    Box(
        modifier
            .fillMaxSize()
            .background(AlertColors.Deep),
    ) {
        when {
            summary -> AlertSummary(alert, timeline, actions, layout.isTelevision)
            tabletop -> TabletopAlert(alert, timeline, actions, mapStyle, intenseThreshold, layout)
            else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                val verticalHinge = layout.hingeBounds != null && layout.hingeIsVertical
                if (layout.isTelevision || verticalHinge || maxWidth > maxHeight) {
                    WideAlert(alert, timeline, actions, mapStyle, intenseThreshold, layout)
                } else {
                    PhoneAlert(alert, timeline, actions, mapStyle, intenseThreshold, maxHeight)
                }
            }
        }
    }

    if (wantsFocus) {
        LaunchedEffect(summary, tabletop) {
            // 我已安全 exists in every arrangement; it is attached once this composition applies.
            runCatching { focus.requestFocus() }
        }
    }
}

// ---------------------------------------------------------------- shared pieces

/** Red head with the tags line and 紧急地震速报 (prototype `.al-head`), drawn under the status bar. */
@Composable
private fun AlertHead(alert: AlertData, modifier: Modifier = Modifier, scale: Float = 1f) {
    Column(
        modifier
            .fillMaxWidth()
            .background(AlertColors.Red)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(start = 22.dp, end = 22.dp, top = 6.dp, bottom = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (alert.isSimulation) DrillTag(height = 22.dp * scale, fontSize = 12.sp * scale)
            Text(
                sourceReportLabel(alert.event),
                color = AlertColors.TagText,
                fontSize = 13.sp * scale,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            stringResource(R.string.alert_title),
            color = Color.White,
            fontSize = 31.sp * scale,
            lineHeight = 36.sp * scale,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.06.em,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** 我已安全 (flex) · 静音 · 关闭 (prototype `.al-act`). */
@Composable
private fun ActionRow(actions: AlertActions, modifier: Modifier = Modifier, height: Dp = 56.dp, fontSize: TextUnit = 16.sp) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        SafeButton(
            onClick = actions.onSafe,
            modifier = Modifier.weight(1f),
            height = height,
            fontSize = fontSize,
            focusRequester = actions.focus,
        )
        MuteButton(muted = actions.muted, onClick = actions.onMute, size = height)
        OutlineAlertButton(
            label = stringResource(R.string.pg_alert_close),
            onClick = actions.onClose,
            height = height,
            fontSize = fontSize,
        )
    }
}

private fun ringStroke(ring: Dp): Dp = ring * (12f / 206f)

// ---------------------------------------------------------------- phone portrait

@Composable
private fun PhoneAlert(
    alert: AlertData,
    timeline: AlertTimeline,
    actions: AlertActions,
    mapStyle: EewMapStyle,
    intenseThreshold: Int,
    height: Dp,
) {
    val ring = when {
        height < 600.dp -> 150.dp
        height < 700.dp -> 180.dp
        else -> 206.dp
    }
    Column(Modifier.fillMaxSize()) {
        AlertHead(alert)
        Column(
            Modifier
                .weight(1f)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .padding(start = 16.dp, end = 16.dp, top = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                CountdownRing(timeline, ring, ringStroke(ring))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    IntensityStat(alert, if (ring < 180.dp) 52.dp else 66.dp)
                    DistanceStat(alert)
                }
            }
            EpicenterRow(alert, Modifier.fillMaxWidth())
            AlertMap(
                alert = alert,
                timeline = timeline,
                style = mapStyle,
                intenseThreshold = intenseThreshold,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .heightIn(min = 100.dp),
            )
            GuideChips()
        }
        ActionRow(
            actions,
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
        )
    }
}

// ---------------------------------------------------------------- wide, dual screen, TV

@Composable
private fun WideAlert(
    alert: AlertData,
    timeline: AlertTimeline,
    actions: AlertActions,
    mapStyle: EewMapStyle,
    intenseThreshold: Int,
    layout: ResolvedLayout,
) {
    val tv = layout.isTelevision
    val scale = if (tv) 1.25f else 1f
    val density = LocalDensity.current
    val hinge = layout.hingeBounds?.takeIf { layout.hingeIsVertical }
    Row(Modifier.fillMaxSize()) {
        // Map side: head + map with the guide chips over it.
        val mapSide = if (hinge != null) {
            Modifier.width(with(density) { hinge.left.toDp() })
        } else {
            Modifier.weight(1.1f)
        }
        Column(mapSide.fillMaxHeight()) {
            AlertHead(alert, scale = scale)
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Bottom))
                    .padding(16.dp * scale),
            ) {
                AlertMap(
                    alert = alert,
                    timeline = timeline,
                    style = mapStyle,
                    intenseThreshold = intenseThreshold,
                    modifier = Modifier.fillMaxSize(),
                    fitPaddingDp = if (tv) 72 else 56,
                )
                GuideChips(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp),
                    scale = scale,
                    background = AlertColors.Surface.copy(alpha = 0.92f),
                )
            }
        }
        if (hinge != null) Spacer(Modifier.width(with(density) { hinge.width.toDp() }))

        // Countdown side.
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.End + WindowInsetsSides.Bottom))
                .padding(horizontal = 22.dp * scale, vertical = 16.dp),
        ) {
            val buttonHeight = if (tv) 64.dp else 56.dp
            val stacked = maxHeight >= 560.dp
            val reserved = buttonHeight + 56.dp * scale + 56.dp
            val ring = if (stacked) {
                min(250f, min(maxWidth.value * 0.8f, maxHeight.value * 0.45f)).dp
            } else {
                min(260f, min(maxWidth.value * 0.5f, (maxHeight - reserved).value)).coerceAtLeast(120f).dp
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight),
                verticalArrangement = Arrangement.spacedBy(if (stacked) 18.dp else 14.dp, Alignment.CenterVertically),
            ) {
                if (stacked) {
                    CountdownRing(timeline, ring, ringStroke(ring), Modifier.align(Alignment.CenterHorizontally))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IntensityStat(alert, 64.dp * scale, Modifier.weight(1f), scale = scale)
                        DistanceStat(alert, scale = scale, alignEnd = true)
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        CountdownRing(timeline, ring, ringStroke(ring))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            IntensityStat(alert, (if (ring < 180.dp) 52.dp else 66.dp) * scale, scale = scale)
                            DistanceStat(alert, scale = scale)
                        }
                    }
                }
                EpicenterRow(alert, Modifier.fillMaxWidth(), scale = scale)
                ActionRow(actions, Modifier.fillMaxWidth(), height = buttonHeight, fontSize = 16.sp * scale)
            }
        }
    }
}

// ---------------------------------------------------------------- tabletop

@Composable
private fun TabletopAlert(
    alert: AlertData,
    timeline: AlertTimeline,
    actions: AlertActions,
    mapStyle: EewMapStyle,
    intenseThreshold: Int,
    layout: ResolvedLayout,
) {
    val density = LocalDensity.current
    val hinge = layout.hingeBounds?.takeIf { !layout.hingeIsVertical }
    val level = csisLevel(alert.localCsis)
    Column(Modifier.fillMaxSize()) {
        // Upper half: map + countdown panel.
        val upper = if (hinge != null) Modifier.height(with(density) { hinge.top.toDp() }) else Modifier.weight(1f)
        Box(upper.fillMaxWidth()) {
            Row(Modifier.fillMaxSize()) {
                AlertMap(
                    alert = alert,
                    timeline = timeline,
                    style = mapStyle,
                    intenseThreshold = intenseThreshold,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    shape = RectangleShape,
                    border = null,
                    legend = false,
                    fitPaddingDp = 40,
                )
                BoxWithConstraints(
                    Modifier
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.End))
                        .padding(start = 8.dp, end = 14.dp, top = 14.dp, bottom = 14.dp)
                        .width(204.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(30.dp))
                        .background(AlertColors.Deep.copy(alpha = 0.9f)),
                    contentAlignment = Alignment.Center,
                ) {
                    val ring = min(180f, (maxHeight - 24.dp).value).coerceAtLeast(110f).dp
                    CountdownRing(timeline, ring, ring * (11f / 180f))
                }
            }
            Row(
                Modifier
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Start))
                    .padding(start = 16.dp, top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (alert.isSimulation) DrillTag()
                Text(
                    stringResource(R.string.alert_title) + " · " + reportLabel(alert.event),
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        fontSize = 18.sp,
                        shadow = Shadow(Color.Black.copy(alpha = 0.8f), Offset(0f, 2f), blurRadius = 8f),
                    ),
                )
            }
        }
        if (hinge != null) Spacer(Modifier.height(with(density) { hinge.height.toDp() }))

        // Lower half: intensity line, big buttons, guide.
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(horizontal = 24.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
            ) {
                Row(
                    Modifier.semantics(mergeDescendants = true) {},
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    IntensityBurst(level, 60.dp)
                    Column {
                        Text(
                            stringResource(R.string.pg_alert_intensity_distance, level, kmText(alert.distanceKm)),
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        val detail = buildList {
                            add(
                                listOf(alert.event.hypocenter, magnitudeText(alert.event.magnitude))
                                    .filter { it.isNotBlank() }
                                    .joinToString(" ")
                            )
                            depthLabel(alert.event)?.let { add(it) }
                            intensityDescription(level).takeIf { it.isNotBlank() }?.let { add(it) }
                        }.joinToString(" · ")
                        Text(detail, color = AlertColors.StatValue, fontSize = 14.sp)
                    }
                }
                ActionRow(actions, Modifier.fillMaxWidth(), height = 72.dp, fontSize = 19.sp)
                GuideChips()
            }
        }
    }
}

// ---------------------------------------------------------------- summary

/**
 * 60 s after the S wave: the summary card with the final parameters and the estimated
 * intensity, 我已安全 and 查看详情.
 */
@Composable
private fun AlertSummary(alert: AlertData, timeline: AlertTimeline, actions: AlertActions, tv: Boolean) {
    val scale = if (tv) 1.2f else 1f
    val formats = rememberFormats()
    val arrival = formats.clockSeconds(timeline.sArrivalAt)
    val lead = timeline.leadSeconds
    Column(Modifier.fillMaxSize()) {
        AlertHead(alert, scale = scale)
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Column(
                    Modifier
                        .widthIn(max = 560.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Column {
                        Text(
                            stringResource(R.string.pg_alert_summary_title),
                            color = Color.White,
                            fontSize = 26.sp * scale,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            if (lead > 0) {
                                stringResource(R.string.pg_alert_summary_sub, arrival, lead)
                            } else {
                                stringResource(R.string.pg_alert_summary_sub_late, arrival)
                            },
                            color = AlertColors.StatKey,
                            fontSize = 14.sp * scale,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    SummaryCard(alert, lead, scale)
                    Text(
                        stringResource(R.string.pg_alert_summary_hint),
                        color = AlertColors.StatValue,
                        fontSize = 13.sp * scale,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        SafeButton(
                            onClick = actions.onSafe,
                            modifier = Modifier.weight(1f),
                            height = if (tv) 64.dp else 56.dp,
                            fontSize = 16.sp * scale,
                            focusRequester = actions.focus,
                        )
                        OutlineAlertButton(
                            label = stringResource(R.string.pg_alert_view_details),
                            onClick = actions.onDetails,
                            height = if (tv) 64.dp else 56.dp,
                            fontSize = 16.sp * scale,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(alert: AlertData, lead: Int, scale: Float) {
    val event = alert.event
    val level = csisLevel(alert.localCsis)
    val formats = rememberFormats()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(AlertColors.Surface)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            IntensityBurst(level, 64.dp * scale)
            Column {
                Text(
                    stringResource(R.string.pg_alert_intensity_estimate, estimateText(alert.localCsis)),
                    color = AlertColors.StatKey,
                    fontSize = 13.sp * scale,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    intensityDescription(level),
                    color = Color.White,
                    fontSize = 19.sp * scale,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(AlertColors.Line),
        )
        if (event.hypocenter.isNotBlank()) SummaryRow(stringResource(R.string.pg_alert_sum_epicenter), event.hypocenter, scale)
        SummaryRow(stringResource(R.string.pg_alert_sum_magnitude), magnitudeText(event.magnitude), scale)
        depthText(event.depth)?.let { SummaryRow(stringResource(R.string.pg_alert_sum_depth), stringResource(R.string.pg_alert_km, it), scale) }
        SummaryRow(stringResource(R.string.pg_alert_sum_origin), formats.clockSeconds(displayOrigin(alert)), scale)
        SummaryRow(stringResource(R.string.pg_alert_sum_distance), stringResource(R.string.pg_alert_km, kmText(alert.distanceKm)), scale)
        SummaryRow(stringResource(R.string.pg_alert_sum_report), sourceReportLabel(event), scale)
        if (lead > 0) SummaryRow(stringResource(R.string.pg_alert_sum_lead), stringResource(R.string.pg_alert_seconds_value, lead), scale)
    }
}

@Composable
private fun SummaryRow(label: String, value: String, scale: Float) {
    Row(
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(label, color = AlertColors.StatKey, fontSize = 14.sp * scale, fontWeight = FontWeight.SemiBold)
        Text(
            value,
            color = Color.White,
            fontSize = 15.sp * scale,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}
