package com.github.mytv.myearthquakealert.ui.alert

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.github.mytv.myearthquakealert.ui.theme.BroadcastBlue
import com.github.mytv.myearthquakealert.ui.theme.BroadcastInk
import com.github.mytv.myearthquakealert.ui.theme.BroadcastInkSoft
import com.github.mytv.myearthquakealert.ui.theme.BroadcastWhite
import com.github.mytv.myearthquakealert.ui.theme.CautionYellow
import com.github.mytv.myearthquakealert.ui.theme.EeqSpacing
import com.github.mytv.myearthquakealert.ui.theme.MyEarthQuakeAlertTheme
import com.github.mytv.myearthquakealert.ui.theme.PWaveBlue
import com.github.mytv.myearthquakealert.ui.theme.SWaveRed
import kotlinx.coroutines.delay
import kotlin.math.ceil
import kotlin.math.max

/**
 * Floating alert card in broadcast-bulletin style: a bordered card with a map
 * panel and a red / white / blue info stack, centered over the dimmed screen
 * without taking it over. The window itself wraps this card.
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
) {
    var elapsedSeconds by remember(alertData.event.eventId) { mutableFloatStateOf(0f) }

    LaunchedEffect(alertData.event.eventId) {
        elapsedSeconds = 0f
        while (true) {
            delay(500)
            elapsedSeconds += 0.5f
        }
    }

    // Always consume Back while the alert card owns focus — when back-dismiss is
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

    val cardShape = RoundedCornerShape(10.dp)

    Box(modifier = modifier) {
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
            Column(modifier = stackModifier) {
                AlertHeaderBar(alertData = alertData)
                SourceBand(alertData = alertData, intense = intense)
                CountdownBand(
                    alertData = alertData,
                    sRemaining = sRemaining,
                    sArrived = sArrived,
                    elapsedSeconds = elapsedSeconds,
                    intense = intense,
                    onDismiss = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (wideLayout) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .clip(cardShape)
                    .border(2.dp, BroadcastWhite.copy(alpha = 0.92f), cardShape),
            ) {
                mapPanel(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.36f)
                )
                infoStack(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(cardShape)
                    .border(2.dp, BroadcastWhite.copy(alpha = 0.92f), cardShape),
            ) {
                mapPanel(
                    Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                )
                infoStack(Modifier.fillMaxWidth())
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Red header band — centered broadcast title
// ---------------------------------------------------------------------------

@Composable
private fun AlertHeaderBar(
    alertData: AlertData,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(R.string.alert_title_source).format(
        stringResource(R.string.alert_title),
        EewSource.labelOf(alertData.event.source),
    )
    val reportText = if (alertData.event.reportNum > 0) {
        stringResource(R.string.alert_report_no).format(alertData.event.reportNum)
    } else {
        null
    }
    val subtitle = listOfNotNull(
        reportText,
        if (alertData.isSimulation) stringResource(R.string.simulation_label) else null,
    ).joinToString(" · ")

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AlertRed)
            .padding(horizontal = EeqSpacing.md, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
            fontWeight = FontWeight.Black,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle.isNotEmpty()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// White source band — hypocenter, magnitude, intensity chip
// ---------------------------------------------------------------------------

@Composable
private fun SourceBand(
    alertData: AlertData,
    intense: Boolean,
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
    val details = listOfNotNull(depthText, distanceText).joinToString(" · ")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(BroadcastWhite)
            .padding(horizontal = EeqSpacing.md, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${alertData.event.hypocenter}  M%.1f".format(alertData.event.magnitude),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
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
            modifier = Modifier.padding(start = EeqSpacing.sm),
        ) {
            Text(
                text = stringResource(R.string.alert_intensity_expected),
                style = MaterialTheme.typography.labelSmall,
                color = BroadcastInkSoft,
            )
            Box(
                modifier = Modifier
                    .padding(top = 1.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(if (intense) AlertRed else CautionYellow)
                    .padding(horizontal = 12.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = alertData.localCsis.toInt().toString(),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                    color = if (intense) Color.White else BroadcastInk,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Blue countdown band — large arrival countdown, wave status, dismiss
// ---------------------------------------------------------------------------

@Composable
private fun CountdownBand(
    alertData: AlertData,
    sRemaining: Double,
    sArrived: Boolean,
    elapsedSeconds: Float,
    intense: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val number = if (sArrived) {
        max(0f, elapsedSeconds - alertData.sWaveSeconds.toFloat()).toInt()
    } else {
        ceil(sRemaining).toInt()
    }

    Column(
        modifier = modifier
            .background(BroadcastBlue)
            .padding(horizontal = EeqSpacing.md, vertical = 12.dp),
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
                    fontSize = 68.sp,
                    fontWeight = FontWeight.Black,
                    fontFeatureSettings = "tnum",
                ),
            )
            Text(
                text = stringResource(R.string.alert_seconds_unit),
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.padding(start = 6.dp, bottom = 10.dp),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(EeqSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 6.dp),
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
            if (intense) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = CautionYellow,
                ) {
                    Text(
                        text = stringResource(R.string.alert_intense_label),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = BroadcastInk,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    )
                }
            }
        }
        Button(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .height(44.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = BroadcastWhite,
                contentColor = BroadcastInk,
            ),
        ) {
            Text(
                text = stringResource(R.string.alert_dismiss),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
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
        shape = RoundedCornerShape(7.dp),
        color = Color.White.copy(alpha = if (arrived) 0.12f else 0.18f),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
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
            showDistanceLine = false,
            fitPaddingDp = 36,
            showAttribution = false,
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
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
private fun LegendChip(color: Color, label: String) {
    Surface(
        shape = RoundedCornerShape(5.dp),
        color = Color(0xB3000000),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = Color.White,
            )
        }
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

@Preview(name = "Alert Column", device = "spec:width=420dp,height=460dp")
@Composable
private fun AlertColumnPreview() {
    MyEarthQuakeAlertTheme {
        Column {
            AlertHeaderBar(alertData = sampleAlertData)
            SourceBand(alertData = sampleAlertData, intense = true)
            CountdownBand(
                alertData = sampleAlertData,
                sRemaining = 23.0,
                sArrived = false,
                elapsedSeconds = 7f,
                intense = true,
                onDismiss = {},
            )
        }
    }
}
