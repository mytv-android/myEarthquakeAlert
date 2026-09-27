package com.github.mytv.myearthquakealert.ui.alert

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.data.model.EewEvent
import com.github.mytv.myearthquakealert.data.source.EewSource
import com.github.mytv.myearthquakealert.domain.AlertEvaluator
import com.github.mytv.myearthquakealert.domain.SeismicCalculator
import com.github.mytv.myearthquakealert.service.AlertData
import com.github.mytv.myearthquakealert.ui.adaptive.backHandler
import com.github.mytv.myearthquakealert.ui.map.EarthquakeMap
import com.github.mytv.myearthquakealert.ui.map.EarthquakeMapMode
import com.github.mytv.myearthquakealert.ui.map.EewMapStyle
import com.github.mytv.myearthquakealert.ui.map.MapPoint
import com.github.mytv.myearthquakealert.ui.theme.AlertRed
import com.github.mytv.myearthquakealert.ui.theme.AlertScrim
import com.github.mytv.myearthquakealert.ui.theme.BroadcastBlue
import com.github.mytv.myearthquakealert.ui.theme.BroadcastInk
import com.github.mytv.myearthquakealert.ui.theme.BroadcastInkSoft
import com.github.mytv.myearthquakealert.ui.theme.BroadcastWhite
import com.github.mytv.myearthquakealert.ui.theme.CautionYellow
import com.github.mytv.myearthquakealert.ui.theme.EeqSpacing
import com.github.mytv.myearthquakealert.ui.theme.MyEarthQuakeAlertTheme
import com.github.mytv.myearthquakealert.ui.theme.PWaveBlue
import com.github.mytv.myearthquakealert.ui.theme.SWaveRed
import com.github.mytv.myearthquakealert.ui.theme.csisColor
import kotlinx.coroutines.delay
import kotlin.math.ceil
import kotlin.math.max

/**
 * Full-takeover alert in broadcast-graphic style: a map panel with the expected
 * strong-shaking zone, and a red / white / blue info stack — the same visual
 * grammar as TV emergency earthquake bulletins.
 */
@Composable
fun AlertOverlay(
    alertData: AlertData,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    allowBackDismiss: Boolean = true,
    intenseThreshold: Int = 5,
    mapStyle: EewMapStyle = EewMapStyle.AMAP,
) {
    var elapsedSeconds by remember(alertData.event.eventId) { mutableFloatStateOf(0f) }

    LaunchedEffect(alertData.event.eventId) {
        elapsedSeconds = 0f
        while (true) {
            delay(500)
            elapsedSeconds += 0.5f
        }
    }

    // Always consume Back while the alert owns the screen — when back-dismiss is
    // disabled the key is swallowed instead of falling through to the app below.
    backHandler(onBack = { if (allowBackDismiss) onDismiss() })

    val depthKm = alertData.event.depth ?: 10.0
    val pWaveRadius = SeismicCalculator.calcWaveRadius(depthKm, elapsedSeconds.toDouble(), true)
    val sWaveRadius = SeismicCalculator.calcWaveRadius(depthKm, elapsedSeconds.toDouble(), false)
    val sRemaining = max(0.0, alertData.sWaveSeconds - elapsedSeconds)
    val sArrived = elapsedSeconds >= alertData.sWaveSeconds
    val intense = AlertEvaluator.isIntense(alertData.localCsis, intenseThreshold)

    // Radius of the area expected to reach the strong-shaking threshold.
    val warnZoneRadiusKm = remember(
        alertData.event.eventId, alertData.event.magnitude, depthKm, intenseThreshold,
    ) {
        if (intenseThreshold <= 0) {
            null
        } else {
            var lo = 0.0
            var hi = 2000.0
            repeat(40) {
                val mid = (lo + hi) / 2.0
                if (SeismicCalculator.calcLocalIntensity(alertData.event.magnitude, depthKm, mid) >= intenseThreshold) {
                    lo = mid
                } else {
                    hi = mid
                }
            }
            val r = (lo + hi) / 2.0
            if (r in 3.0..1999.0) r else null
        }
    }
    val warnZoneLabel = if (warnZoneRadiusKm != null) {
        stringResource(R.string.alert_warn_zone).format(intenseThreshold)
    } else {
        null
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(AlertScrim),
    ) {
        val wide = maxWidth >= 600.dp && maxWidth > maxHeight
        val totalWidth = maxWidth
        val totalHeight = maxHeight

        val mapPanel: @Composable (Modifier) -> Unit = { panelModifier ->
            AlertMapPanel(
                alertData = alertData,
                pWaveRadiusKm = pWaveRadius,
                sWaveRadiusKm = sWaveRadius,
                warnZoneRadiusKm = warnZoneRadiusKm,
                warnZoneLabel = warnZoneLabel,
                mapStyle = mapStyle,
                modifier = panelModifier,
            )
        }

        val infoStack: @Composable (Modifier) -> Unit = { stackModifier ->
            Column(modifier = stackModifier.fillMaxWidth()) {
                AlertHeaderBar(alertData = alertData, intense = intense)
                SourceSection(alertData = alertData)
                CountdownSection(
                    alertData = alertData,
                    sRemaining = sRemaining,
                    sArrived = sArrived,
                    elapsedSeconds = elapsedSeconds,
                    modifier = Modifier.weight(1f),
                )
                AlertDismissButton(
                    onDismiss = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(EeqSpacing.md),
                )
            }
        }

        if (wide) {
            Row(modifier = Modifier.fillMaxSize()) {
                mapPanel(
                    Modifier
                        .fillMaxHeight()
                        .width(totalWidth * 0.36f)
                        .border(2.dp, BroadcastWhite.copy(alpha = 0.9f))
                )
                infoStack(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                mapPanel(
                    Modifier
                        .fillMaxWidth()
                        .height(totalHeight * 0.42f)
                        .border(2.dp, BroadcastWhite.copy(alpha = 0.9f))
                )
                infoStack(Modifier.weight(1f))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Red header bar
// ---------------------------------------------------------------------------

@Composable
private fun AlertHeaderBar(
    alertData: AlertData,
    intense: Boolean,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(R.string.alert_title_source).format(
        stringResource(R.string.alert_title),
        EewSource.labelOf(alertData.event.source),
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(AlertRed)
            .padding(horizontal = EeqSpacing.md, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(EeqSpacing.sm)) {
            if (alertData.isSimulation) {
                BroadcastTag(text = stringResource(R.string.simulation_label), pulsing = false)
            }
            if (intense) {
                BroadcastTag(text = stringResource(R.string.alert_intense_label), pulsing = true)
            }
        }
    }
}

@Composable
private fun BroadcastTag(text: String, pulsing: Boolean) {
    val alpha = if (pulsing) {
        val transition = rememberInfiniteTransition(label = "broadcastTag")
        val value by transition.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 550),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "broadcastTagAlpha",
        )
        value
    } else {
        1f
    }
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = CautionYellow.copy(alpha = alpha),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = BroadcastInk,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// White source section
// ---------------------------------------------------------------------------

@Composable
private fun SourceSection(
    alertData: AlertData,
    modifier: Modifier = Modifier,
) {
    val depthText = alertData.event.depth?.let {
        stringResource(R.string.alert_depth_km).format("%.0f".format(it))
    }
    val distanceText = if (alertData.distanceKm > 0.0) {
        stringResource(R.string.alert_distance_km).format("%.0f".format(alertData.distanceKm))
    } else {
        null
    }
    val reportText = if (alertData.event.reportNum > 0) {
        stringResource(R.string.alert_report_no).format(alertData.event.reportNum)
    } else {
        null
    }
    val details = listOfNotNull(depthText, distanceText, reportText).joinToString(" · ")
    val csis = alertData.localCsis
    val chipColor = csisColor(csis)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(BroadcastWhite)
            .padding(horizontal = EeqSpacing.md, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${alertData.event.hypocenter}  M%.1f".format(alertData.event.magnitude),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = BroadcastInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (details.isNotEmpty()) {
                Text(
                    text = details,
                    style = MaterialTheme.typography.labelMedium,
                    color = BroadcastInkSoft,
                    maxLines = 1,
                )
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(start = EeqSpacing.md),
        ) {
            Text(
                text = stringResource(R.string.alert_intensity_expected),
                style = MaterialTheme.typography.labelSmall,
                color = BroadcastInkSoft,
            )
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(chipColor)
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = csis.toInt().toString(),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                    color = if (chipColor.luminance() > 0.45f) Color.Black else Color.White,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Blue countdown section
// ---------------------------------------------------------------------------

@Composable
private fun CountdownSection(
    alertData: AlertData,
    sRemaining: Double,
    sArrived: Boolean,
    elapsedSeconds: Float,
    modifier: Modifier = Modifier,
) {
    val number = if (sArrived) {
        max(0f, elapsedSeconds - alertData.sWaveSeconds.toFloat()).toInt()
    } else {
        ceil(sRemaining).toInt()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(BroadcastBlue)
            .padding(EeqSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = if (sArrived) stringResource(R.string.alert_waves_arrived)
                   else stringResource(R.string.alert_countdown_hint),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.8f),
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = number.toString(),
                color = Color.White,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 76.sp,
                    fontWeight = FontWeight.Black,
                    fontFeatureSettings = "tnum",
                ),
            )
            Text(
                text = stringResource(R.string.alert_seconds_unit),
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.padding(start = 6.dp, bottom = 12.dp),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(EeqSpacing.sm),
            modifier = Modifier.padding(top = EeqSpacing.sm),
        ) {
            WaveStatusChip(
                label = stringResource(R.string.alert_wave_p),
                color = PWaveBlue,
                secondsRemaining = alertData.pWaveSeconds - elapsedSeconds,
            )
            WaveStatusChip(
                label = stringResource(R.string.alert_wave_s),
                color = SWaveRed,
                secondsRemaining = alertData.sWaveSeconds - elapsedSeconds,
            )
        }
    }
}

@Composable
private fun WaveStatusChip(
    label: String,
    color: Color,
    secondsRemaining: Double,
    modifier: Modifier = Modifier,
) {
    val arrived = secondsRemaining <= 0.0
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.White.copy(alpha = if (arrived) 0.12f else 0.2f),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (arrived) Color.White.copy(alpha = 0.6f) else color),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
            Text(
                text = if (arrived) {
                    stringResource(R.string.alert_wave_state_arrived)
                } else {
                    stringResource(R.string.alert_wave_state_in).format(ceil(secondsRemaining).toInt())
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (arrived) Color.White.copy(alpha = 0.6f) else Color.White,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Map panel
// ---------------------------------------------------------------------------

@Composable
private fun AlertMapPanel(
    alertData: AlertData,
    pWaveRadiusKm: Double,
    sWaveRadiusKm: Double,
    warnZoneRadiusKm: Double?,
    warnZoneLabel: String?,
    mapStyle: EewMapStyle,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        EarthquakeMap(
            style = mapStyle,
            mode = EarthquakeMapMode.ALERT,
            modifier = Modifier.fillMaxSize(),
            userPoint = MapPoint(alertData.userLatitude, alertData.userLongitude),
            interactive = false,
            epicenter = MapPoint(alertData.event.latitude, alertData.event.longitude),
            pWaveRadiusKm = pWaveRadiusKm,
            sWaveRadiusKm = sWaveRadiusKm,
            warnZoneRadiusKm = warnZoneRadiusKm,
            showDistanceLine = true,
            showAttribution = false,
        )

        if (alertData.distanceKm > 0.0) {
            MapChip(
                text = stringResource(R.string.alert_distance_km)
                    .format("%.0f".format(alertData.distanceKm)),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(EeqSpacing.sm),
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(EeqSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(EeqSpacing.xs),
        ) {
            LegendChip(color = PWaveBlue, label = stringResource(R.string.alert_wave_p))
            LegendChip(color = SWaveRed, label = stringResource(R.string.alert_wave_s))
            if (warnZoneLabel != null) {
                LegendChip(color = CautionYellow, label = warnZoneLabel)
            }
        }
    }
}

@Composable
private fun MapChip(text: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color(0xCC000000),
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun LegendChip(color: Color, label: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color(0xCC000000),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Dismiss
// ---------------------------------------------------------------------------

@Composable
private fun AlertDismissButton(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onDismiss,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = BroadcastWhite,
            contentColor = BroadcastInk,
        ),
    ) {
        Text(
            text = stringResource(R.string.alert_dismiss),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ---------------------------------------------------------------------------
// Preview
// ---------------------------------------------------------------------------

private val sampleAlertData = AlertData(
    event = EewEvent(
        id = "preview-1",
        eventId = "PREVIEW",
        source = EewSource.CENC.name,
        reportTime = "2024-01-15 10:30:00",
        reportNum = 2,
        originTime = "2024-01-15 10:29:00",
        hypocenter = "四川宜宾市长宁县",
        latitude = 28.5,
        longitude = 104.7,
        magnitude = 5.5,
        depth = 10.0,
        maxIntensity = 5.0,
    ),
    userLatitude = 29.0,
    userLongitude = 105.1,
    pWaveSeconds = 15.0,
    sWaveSeconds = 30.0,
    localCsis = 5.0,
    distanceKm = 78.0,
    isSimulation = true,
)

@Preview(name = "Alert Panels", device = "spec:width=520dp,height=360dp")
@Composable
private fun AlertPanelsPreview() {
    MyEarthQuakeAlertTheme {
        Column {
            AlertHeaderBar(alertData = sampleAlertData, intense = true)
            SourceSection(alertData = sampleAlertData)
            CountdownSection(
                alertData = sampleAlertData,
                sRemaining = 23.0,
                sArrived = false,
                elapsedSeconds = 7f,
            )
        }
    }
}
