package com.github.mytv.myearthquakealert.ui.alert

import android.content.Context
import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.BackHand
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.data.model.EewEvent
import com.github.mytv.myearthquakealert.data.source.EewSource
import com.github.mytv.myearthquakealert.domain.SeismicCalculator
import com.github.mytv.myearthquakealert.service.AlertData
import com.github.mytv.myearthquakealert.ui.components.rememberAnimationsEnabled
import com.github.mytv.myearthquakealert.ui.map.EarthquakeMap
import com.github.mytv.myearthquakealert.ui.map.EarthquakeMapMode
import com.github.mytv.myearthquakealert.ui.map.EewMapStyle
import com.github.mytv.myearthquakealert.ui.map.MapPoint
import com.github.mytv.myearthquakealert.ui.settings.components.rememberFormats
import com.github.mytv.myearthquakealert.ui.theme.AlertRed
import com.github.mytv.myearthquakealert.ui.theme.CautionYellow
import com.github.mytv.myearthquakealert.ui.theme.PWaveBlue
import com.github.mytv.myearthquakealert.ui.theme.SWaveRed
import com.github.mytv.myearthquakealert.ui.theme.csisColor
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

/** Alert palette (prototype tokens --alert, --al-deep, --al-surf, --al-line, --al-yel). */
internal object AlertColors {
    val Red = AlertRed
    val Deep = Color(0xFF1A0A0B)
    val Surface = Color(0xFF2B1416)
    val Line = Color(0xFF5A2A2D)
    val Yellow = CautionYellow
    val OnYellow = Color(0xFF1A1300)
    val TagText = Color(0xFFFFE3E1)
    val CountdownLabel = Color(0xFFFFD7D3)
    val StatKey = Color(0xFFFFC9C4)
    val StatValue = Color(0xFFF6DCD9)
    val Soft = Color(0xFFFFE9E7)
    val CardLabel = Color(0xFFFFD0CB)
    val CardText = Color(0xFFFFE3E0)
    val SWaveLight = Color(0xFFFF6B5E)
    val Track = Color.White.copy(alpha = 0.16f)
    val LineTrack = Color.White.copy(alpha = 0.2f)
    val Outline = Color.White.copy(alpha = 0.6f)
    val DarkText = Color(0xFF1A1C1E)
    val TvInkSoft = Color(0xFF4B545E)
    val TvFooter = Color(0xFFD9DDE2)
    val MapPlaceholder = Color(0xFF2A2F36)
}

/** Current text style with tabular digits (`font-variant-numeric: tabular-nums`). */
@Composable
internal fun tabularStyle(): TextStyle = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")

/** A text size that ignores the font scale, for numbers drawn inside fixed-size shapes. */
@Composable
internal fun fixedSp(size: Dp): TextUnit = with(LocalDensity.current) { size.toSp() }

// ---------------------------------------------------------------- texts

internal fun magnitudeText(magnitude: Double): String = String.format(Locale.ROOT, "M%.1f", magnitude)

internal fun kmText(km: Double): String = km.roundToInt().toString()

internal fun depthText(depth: Double?): String? = depth?.let {
    if (it % 1.0 == 0.0) it.toInt().toString() else String.format(Locale.ROOT, "%.1f", it)
}

internal fun estimateText(csis: Double): String = String.format(Locale.ROOT, "%.1f", csis)

/** "第 2 报" / "第 3 报 · 终报". */
@Composable
internal fun reportLabel(event: EewEvent): String =
    stringResource(if (event.isFinal) R.string.pg_alert_report_final else R.string.pg_alert_report, event.reportNum)

/** Display name of the publishing source ("中国地震台网"). */
@Composable
internal fun sourceLabel(event: EewEvent): String {
    val formats = rememberFormats()
    return remember(event.source, formats) {
        EewSource.entries.firstOrNull { it.name == event.source }?.let { formats.sourceName(it) } ?: event.source
    }
}

/** "中国地震台网 · 第 2 报". */
@Composable
internal fun sourceReportLabel(event: EewEvent): String =
    listOf(sourceLabel(event), reportLabel(event)).filter { it.isNotBlank() }.joinToString(" · ")

/** CSIS description of an integer level (室内多数人有感 …). */
@Composable
internal fun intensityDescription(level: Int): String = stringArrayResource(R.array.pg_csis_desc).getOrElse(level) { "" }

/** "深 17 km", or null when the depth is unknown. */
@Composable
internal fun depthLabel(event: EewEvent): String? = depthText(event.depth)?.let { stringResource(R.string.pg_alert_depth, it) }

/** "芦山 · M6.0 · 深 17 km · 17:00:08 发震" with the magnitude emphasised. */
@Composable
internal fun epicenterText(event: EewEvent, originAt: Long?, emphasis: Color = Color.White): AnnotatedString {
    val formats = rememberFormats()
    val depth = depthLabel(event)
    val origin = originAt?.let { stringResource(R.string.pg_alert_origin, formats.clockSeconds(it)) }
    return buildAnnotatedString {
        if (event.hypocenter.isNotBlank()) {
            append(event.hypocenter)
            append(" · ")
        }
        withStyle(SpanStyle(color = emphasis, fontWeight = FontWeight.Bold)) { append(magnitudeText(event.magnitude)) }
        depth?.let { append(" · "); append(it) }
        origin?.let { append(" · "); append(it) }
    }
}

/** Origin time to show: the parsed report time, or the presentation time for drills. */
internal fun displayOrigin(alert: AlertData): Long = alertZero(alert, correctedTime(alert.presentedAt))

/** Opens the app (地震 list and details) from an alert surface; works from a service window too. */
internal fun openAppFromAlert(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    try {
        context.startActivity(intent)
    } catch (e: RuntimeException) {
        // Background activity start refused: the alert stays and the user can open the app manually.
    }
}

// ---------------------------------------------------------------- shapes and badges

/** CSIS badge: a soft burst filled with the intensity colour (prototype burst()). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun IntensityBurst(level: Int, size: Dp, modifier: Modifier = Modifier) {
    val animations = rememberAnimationsEnabled()
    val target = csisColor(level.toDouble())
    val color by animateColorAsState(target, animationSpec = if (animations) tween(400) else snap(), label = "burst")
    val onColor = if (target.luminance() > 0.33f) AlertColors.DarkText else Color.White
    val textSize = fixedSp(size * 0.5f)
    Box(
        modifier
            .size(size)
            .clip(MaterialShapes.SoftBurst.toShape())
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            level.toString(),
            color = onColor,
            fontSize = textSize,
            lineHeight = textSize,
            fontWeight = FontWeight.Black,
            maxLines = 1,
        )
    }
}

/** Yellow 演练 tag (prototype `.tag.yel`). */
@Composable
internal fun DrillTag(modifier: Modifier = Modifier, height: Dp = 22.dp, fontSize: TextUnit = 12.sp) {
    Box(
        modifier
            .heightIn(min = height)
            .clip(RoundedCornerShape(6.dp))
            .background(AlertColors.Yellow)
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(R.string.pg_alert_drill),
            color = AlertColors.OnYellow,
            fontSize = fontSize,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.04.em,
            maxLines = 1,
        )
    }
}

/** Toggles every half second while [enabled] (CSS `blink 1s steps(2)`); constantly on otherwise. */
@Composable
internal fun rememberBlink(enabled: Boolean): Boolean {
    var on by remember { mutableStateOf(true) }
    LaunchedEffect(enabled) {
        on = true
        if (!enabled) return@LaunchedEffect
        while (true) {
            delay(500)
            on = !on
        }
    }
    return on
}

// ---------------------------------------------------------------- countdown

/**
 * S-wave countdown ring (prototype ringSvg): a wavy yellow indicator that depletes from the
 * moment the alert was shown to the S-wave arrival. When the S wave has arrived the track
 * turns light red and blinks, and the centre reads 地震波 / 已到达 / 注意避险.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CountdownRing(
    timeline: AlertTimeline,
    size: Dp,
    strokeWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val animations = rememberAnimationsEnabled()
    val arrived = timeline.sArrived
    val blinkOn = rememberBlink(arrived && animations)
    val density = LocalDensity.current
    val stroke = remember(strokeWidth, density) { Stroke(width = with(density) { strokeWidth.toPx() }, cap = StrokeCap.Round) }
    val trackStroke = remember(strokeWidth, density) { Stroke(width = with(density) { strokeWidth.toPx() } * 0.5f, cap = StrokeCap.Round) }
    val trackColor = when {
        !arrived -> AlertColors.Track
        blinkOn -> AlertColors.SWaveLight
        else -> AlertColors.SWaveLight.copy(alpha = 0.35f)
    }
    val description = if (arrived) {
        stringResource(R.string.pg_alert_arrived_desc)
    } else {
        stringResource(R.string.pg_alert_countdown_desc, timeline.sCountdown)
    }
    Box(
        modifier
            .size(size)
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        CircularWavyProgressIndicator(
            progress = { timeline.fraction },
            modifier = Modifier.fillMaxSize(),
            color = AlertColors.Yellow,
            trackColor = trackColor,
            stroke = stroke,
            trackStroke = trackStroke,
            gapSize = WavyProgressIndicatorDefaults.CircularIndicatorTrackGapSize,
            amplitude = if (animations) WavyProgressIndicatorDefaults.indicatorAmplitude else { _ -> 0f },
            wavelength = WavyProgressIndicatorDefaults.CircularWavelength,
            waveSpeed = if (animations) WavyProgressIndicatorDefaults.CircularWavelength else 0.dp,
        )
        CountdownTexts(
            timeline = timeline,
            numberSize = size * 0.447f,
            arrivedSize = size * 0.165f,
            labelSize = (size * 0.063f).coerceAtLeast(11.dp),
        )
    }
}

/** Label, number and P-wave line in the middle of the ring (fixed sizes: the ring does not scale with fonts). */
@Composable
private fun CountdownTexts(timeline: AlertTimeline, numberSize: Dp, arrivedSize: Dp, labelSize: Dp) {
    val arrived = timeline.sArrived
    val label = fixedSp(labelSize)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            stringResource(if (arrived) R.string.pg_alert_waves else R.string.pg_alert_s_arrival),
            color = AlertColors.CountdownLabel,
            fontSize = label,
            lineHeight = label * 1.3f,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        if (arrived) {
            val text = fixedSp(arrivedSize)
            Text(
                stringResource(R.string.pg_alert_arrived),
                color = Color.White,
                fontSize = text,
                lineHeight = text * 1.2f,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.02.em,
                maxLines = 1,
            )
        } else {
            CountdownNumber(timeline.sCountdown, numberSize, numberSize * 0.196f, Color.White)
        }
        Text(
            when {
                arrived -> stringResource(R.string.pg_alert_take_cover)
                timeline.pArrived -> stringResource(R.string.pg_alert_p_arrived)
                else -> stringResource(R.string.pg_alert_p_in, timeline.pCountdown)
            },
            color = AlertColors.CountdownLabel,
            fontSize = fixedSp(labelSize * 0.92f),
            fontWeight = if (arrived) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** "19秒": tabular digits with a small unit (prototype `.al-num small`). */
@Composable
internal fun CountdownNumber(seconds: Int, numberSize: Dp, unitSize: Dp, color: Color, modifier: Modifier = Modifier) {
    val number = fixedSp(numberSize)
    val unit = fixedSp(unitSize)
    val suffix = stringResource(R.string.pg_alert_seconds)
    Text(
        buildAnnotatedString {
            append(seconds.toString())
            withStyle(SpanStyle(fontSize = unit, fontWeight = FontWeight.Bold, letterSpacing = 0.sp)) {
                append(' ')
                append(suffix)
            }
        },
        color = color,
        fontSize = number,
        lineHeight = number * 0.98f,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.03).em,
        style = tabularStyle(),
        maxLines = 1,
        modifier = modifier,
    )
}

/** Countdown label for the compact surfaces (card, TV ticker). */
@Composable
internal fun countdownLabel(timeline: AlertTimeline): String =
    stringResource(if (timeline.sArrived) R.string.pg_alert_waves else R.string.pg_alert_s_arrival)

/** "P 波已到达" / "P 波 3 秒后" / "注意避险". */
@Composable
internal fun pWaveLabel(timeline: AlertTimeline): String = when {
    timeline.sArrived -> stringResource(R.string.pg_alert_take_cover)
    timeline.pArrived -> stringResource(R.string.pg_alert_p_arrived)
    else -> stringResource(R.string.pg_alert_p_in, timeline.pCountdown)
}

// ---------------------------------------------------------------- side stats

/** Burst + "本地烈度 · 估计 4.1" + description (prototype `.al-stat`). */
@Composable
internal fun IntensityStat(alert: AlertData, burstSize: Dp, modifier: Modifier = Modifier, scale: Float = 1f) {
    val level = csisLevel(alert.localCsis)
    Column(modifier.semantics(mergeDescendants = true) {}) {
        IntensityBurst(level, burstSize)
        Text(
            stringResource(R.string.pg_alert_intensity_estimate, estimateText(alert.localCsis)),
            color = AlertColors.StatKey,
            fontSize = 12.sp * scale,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(intensityDescription(level), color = AlertColors.StatValue, fontSize = 12.sp * scale)
    }
}

/** "110 km" + 距震中 (prototype `.al-big`). */
@Composable
internal fun DistanceStat(alert: AlertData, modifier: Modifier = Modifier, scale: Float = 1f, alignEnd: Boolean = false) {
    Column(modifier.semantics(mergeDescendants = true) {}, horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(
            buildAnnotatedString {
                append(kmText(alert.distanceKm))
                withStyle(SpanStyle(fontSize = 15.sp * scale, fontWeight = FontWeight.Bold)) {
                    append(' ')
                    append("km")
                }
            },
            color = Color.White,
            fontSize = 40.sp * scale,
            lineHeight = 42.sp * scale,
            fontWeight = FontWeight.ExtraBold,
            style = tabularStyle(),
            maxLines = 1,
        )
        Text(
            stringResource(R.string.pg_alert_from_epicenter),
            color = AlertColors.StatKey,
            fontSize = 12.sp * scale,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Epicenter line on the surface colour (prototype `.al-epi`). */
@Composable
internal fun EpicenterRow(alert: AlertData, modifier: Modifier = Modifier, scale: Float = 1f, showOrigin: Boolean = true) {
    Row(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(AlertColors.Surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            painterResource(R.drawable.ic_pg_earthquake),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(20.dp * scale),
        )
        Text(
            epicenterText(alert.event, if (showOrigin) remember(alert.event.originTime, alert.presentedAt) { displayOrigin(alert) } else null),
            color = AlertColors.Soft,
            fontSize = 13.5.sp * scale,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 伏地 · 遮挡 · 手抓牢 · 远离窗户 (prototype `.al-guide`). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GuideChips(modifier: Modifier = Modifier, scale: Float = 1f, background: Color = AlertColors.Surface) {
    val items = listOf(
        Icons.Filled.HealthAndSafety to R.string.pg_guide_drop,
        Icons.Filled.Shield to R.string.pg_guide_cover,
        Icons.Filled.BackHand to R.string.pg_guide_hold,
        Icons.Filled.Warning to R.string.pg_guide_windows,
    )
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.forEach { (icon, label) ->
            Row(
                Modifier
                    .heightIn(min = 32.dp * scale)
                    .clip(RoundedCornerShape(50))
                    .background(background)
                    .padding(horizontal = 12.dp * scale),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(icon, contentDescription = null, tint = AlertColors.Soft, modifier = Modifier.size(18.dp * scale))
                Text(stringResource(label), color = AlertColors.Soft, fontSize = 13.sp * scale, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

// ---------------------------------------------------------------- map

/**
 * The alert map: epicenter, user, distance line, expected-intensity zone and the P/S
 * wavefronts growing with the alert clock (redrawn four times a second).
 */
@Composable
internal fun AlertMap(
    alert: AlertData,
    timeline: AlertTimeline,
    style: EewMapStyle,
    intenseThreshold: Int,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    border: BorderStroke? = BorderStroke(1.dp, AlertColors.Line),
    legend: Boolean = true,
    showAttribution: Boolean = true,
    fitPaddingDp: Int = 48,
) {
    val event = alert.event
    val depth = event.depth ?: 10.0
    val quarter = floor(timeline.elapsed * 4.0) / 4.0
    val pRadius = remember(quarter, depth) { if (quarter <= 0.0) null else SeismicCalculator.calcWaveRadius(depth, quarter, true) }
    val sRadius = remember(quarter, depth) { if (quarter <= 0.0) null else SeismicCalculator.calcWaveRadius(depth, quarter, false) }
    val zone = remember(event.magnitude, depth, intenseThreshold) { warnZoneRadiusKm(event.magnitude, depth, intenseThreshold) }
    val user = remember(alert.userLatitude, alert.userLongitude) { MapPoint(alert.userLatitude, alert.userLongitude) }
    val epicenter = remember(event.latitude, event.longitude) { MapPoint(event.latitude, event.longitude) }
    val description = stringResource(R.string.pg_alert_map_desc)
    Box(
        modifier
            .clip(shape)
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .semantics { contentDescription = description },
    ) {
        if (LocalInspectionMode.current) {
            MapPreviewPlaceholder(pRadius, sRadius, zone, Modifier.fillMaxSize())
        } else {
            EarthquakeMap(
                style = style,
                mode = EarthquakeMapMode.ALERT,
                modifier = Modifier.fillMaxSize(),
                userPoint = user,
                interactive = false,
                epicenter = epicenter,
                pWaveRadiusKm = pRadius,
                sWaveRadiusKm = sRadius,
                warnZoneRadiusKm = zone,
                showDistanceLine = true,
                fitPaddingDp = fitPaddingDp,
                showAttribution = showAttribution,
                darkTiles = true,
            )
        }
        if (legend) {
            MapLegend(zone != null, intenseThreshold, Modifier.align(Alignment.TopStart).padding(8.dp))
        }
    }
}

@Composable
private fun MapLegend(zone: Boolean, threshold: Int, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(AlertColors.Deep.copy(alpha = 0.78f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendItem(PWaveBlue, stringResource(R.string.pg_alert_legend_p))
        LegendItem(SWaveRed, stringResource(R.string.pg_alert_legend_s))
        if (zone) LegendItem(AlertColors.Yellow, stringResource(R.string.pg_alert_legend_zone, threshold))
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Stand-in for the tile map in @Preview (MapView cannot render there). */
@Composable
private fun MapPreviewPlaceholder(pRadius: Double?, sRadius: Double?, zone: Double?, modifier: Modifier) {
    Canvas(modifier.background(AlertColors.MapPlaceholder)) {
        val epicenter = Offset(size.width * 0.62f, size.height * 0.4f)
        val user = Offset(size.width * 0.3f, size.height * 0.62f)
        val kmPx = min(size.width, size.height) / 260f
        zone?.let { drawCircle(AlertColors.Yellow.copy(alpha = 0.25f), (it * kmPx).toFloat(), epicenter) }
        sRadius?.let { drawCircle(SWaveRed, (it * kmPx).toFloat(), epicenter, style = Stroke(2.dp.toPx())) }
        pRadius?.let { drawCircle(PWaveBlue, (it * kmPx).toFloat(), epicenter, style = Stroke(2.dp.toPx())) }
        drawLine(Color.White.copy(alpha = 0.7f), epicenter, user, strokeWidth = 1.5.dp.toPx())
        drawCircle(AlertColors.Red, 6.dp.toPx(), epicenter)
        drawCircle(Color(0xFF2979FF), 6.dp.toPx(), user)
    }
}

// ---------------------------------------------------------------- buttons

/** Yellow focus ring for D-pad and keyboard focus on the red and dark alert surfaces. */
internal fun Modifier.alertFocus(shape: Shape, color: Color = AlertColors.Yellow): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    this
        .onFocusChanged { focused = it.isFocused }
        .then(if (focused) Modifier.border(3.dp, color, shape) else Modifier)
}

private fun Modifier.optionalFocusRequester(requester: FocusRequester?): Modifier =
    if (requester == null) this else focusRequester(requester)

/** 我已安全: white with red text (prototype `.btn.w`). */
@Composable
internal fun SafeButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 56.dp,
    fontSize: TextUnit = 16.sp,
    label: Int = R.string.pg_alert_safe,
    showIcon: Boolean = true,
    focusRequester: FocusRequester? = null,
) {
    val shape = RoundedCornerShape(50)
    Button(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = height)
            .optionalFocusRequester(focusRequester)
            .alertFocus(shape),
        shape = shape,
        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = AlertColors.Red),
        contentPadding = PaddingValues(horizontal = 20.dp),
    ) {
        if (showIcon) {
            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(stringResource(label), fontSize = fontSize, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 关闭 / 详情: outlined white (prototype `.btn.wo`). */
@Composable
internal fun OutlineAlertButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 56.dp,
    fontSize: TextUnit = 16.sp,
    focusRequester: FocusRequester? = null,
) {
    val shape = RoundedCornerShape(50)
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = height)
            .optionalFocusRequester(focusRequester)
            .alertFocus(shape),
        shape = shape,
        border = BorderStroke(1.5.dp, AlertColors.Outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
        contentPadding = PaddingValues(horizontal = 22.dp),
    ) {
        Text(label, fontSize = fontSize, fontWeight = FontWeight.SemiBold, maxLines = 1, textAlign = TextAlign.Center)
    }
}

/** 静音: stops the alarm sound, vibration and speech; shows 已静音 afterwards. */
@Composable
internal fun MuteButton(muted: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 56.dp) {
    val description = stringResource(if (muted) R.string.pg_alert_muted else R.string.pg_alert_mute)
    FilledIconButton(
        onClick = onClick,
        enabled = !muted,
        modifier = modifier
            .size(size)
            .alertFocus(CircleShape)
            .semantics { contentDescription = description },
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = AlertColors.Surface,
            contentColor = Color.White,
            disabledContainerColor = AlertColors.Surface,
            disabledContentColor = AlertColors.Yellow,
        ),
    ) {
        Icon(Icons.AutoMirrored.Filled.VolumeOff, contentDescription = null, modifier = Modifier.size(size * 0.43f))
    }
}
