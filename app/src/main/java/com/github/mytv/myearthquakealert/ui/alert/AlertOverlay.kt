package com.github.mytv.myearthquakealert.ui.alert

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import com.github.mytv.myearthquakealert.ui.theme.AlertBlue
import com.github.mytv.myearthquakealert.ui.theme.AlertRed
import com.github.mytv.myearthquakealert.ui.theme.AlertScrim
import com.github.mytv.myearthquakealert.ui.theme.EeqSpacing
import com.github.mytv.myearthquakealert.ui.theme.MyEarthQuakeAlertTheme
import com.github.mytv.myearthquakealert.ui.theme.PWaveBlue
import com.github.mytv.myearthquakealert.ui.theme.SWaveRed
import com.github.mytv.myearthquakealert.ui.theme.csisColor
import kotlinx.coroutines.delay
import kotlin.math.ceil
import kotlin.math.max

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

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(AlertScrim),
    ) {
        val wide = maxWidth >= 600.dp && maxWidth > maxHeight

        val mapPane: @Composable () -> Unit = {
            AlertMapPane(
                alertData = alertData,
                pWaveRadiusKm = pWaveRadius,
                sWaveRadiusKm = sWaveRadius,
                mapStyle = mapStyle,
            )
        }

        if (wide) {
            Column(modifier = Modifier.fillMaxSize()) {
                AlertHeader(alertData = alertData, intense = intense)
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Box(modifier = Modifier.weight(1.15f).fillMaxHeight()) { mapPane() }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) {
                        AlertInfoPane(
                            alertData = alertData,
                            sRemaining = sRemaining,
                            sArrived = sArrived,
                            elapsedSeconds = elapsedSeconds,
                            intense = intense,
                            modifier = Modifier.weight(1f),
                        )
                        AlertDismissButton(
                            onDismiss = onDismiss,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = EeqSpacing.md, vertical = EeqSpacing.sm),
                        )
                    }
                }
            }
        } else {
            // The map fills the whole window and the chrome overlays it. This is
            // deliberate: the AndroidView interop draws embedded views starting at
            // the window origin regardless of slot position, so a full-window slot
            // is the one geometry whose drawing always matches the plan.
            Box(modifier = Modifier.fillMaxSize()) {
                AlertMapPane(
                    alertData = alertData,
                    pWaveRadiusKm = pWaveRadius,
                    sWaveRadiusKm = sWaveRadius,
                    mapStyle = mapStyle,
                    showChips = false,
                )
                Column(modifier = Modifier.fillMaxSize()) {
                    AlertHeader(alertData = alertData, intense = intense)
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        DistanceChip(
                            alertData = alertData,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(EeqSpacing.sm),
                        )
                        WaveLegend(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(EeqSpacing.sm),
                        )
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AlertBlue),
                    ) {
                        AlertInfoPane(
                            alertData = alertData,
                            sRemaining = sRemaining,
                            sArrived = sArrived,
                            elapsedSeconds = elapsedSeconds,
                            intense = intense,
                            compact = true,
                        )
                        AlertDismissButton(
                            onDismiss = onDismiss,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = EeqSpacing.md, vertical = EeqSpacing.sm),
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Header
// ---------------------------------------------------------------------------

@Composable
private fun AlertHeader(
    alertData: AlertData,
    intense: Boolean,
    modifier: Modifier = Modifier,
) {
    val reportLabel = if (alertData.event.reportNum > 0) {
        stringResource(R.string.alert_report_no).format(alertData.event.reportNum)
    } else {
        null
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(AlertRed)
            .padding(horizontal = EeqSpacing.md, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = stringResource(R.string.alert_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = Color.White,
            )
            Text(
                text = buildString {
                    append(EewSource.labelOf(alertData.event.source))
                    if (reportLabel != null) {
                        append(" · ")
                        append(reportLabel)
                    }
                },
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.85f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(EeqSpacing.sm)) {
            if (alertData.isSimulation) {
                AlertTag(text = stringResource(R.string.simulation_label), pulsing = false)
            }
            if (intense) {
                AlertTag(text = stringResource(R.string.alert_intense_label), pulsing = true)
            }
        }
    }
}

@Composable
private fun AlertTag(text: String, pulsing: Boolean) {
    val alpha = if (pulsing) {
        val transition = rememberInfiniteTransition(label = "alertTag")
        val value by transition.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 550),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "alertTagAlpha",
        )
        value
    } else {
        1f
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color.White.copy(alpha = 0.22f * alpha + 0.08f),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = alpha),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Map pane
// ---------------------------------------------------------------------------

@Composable
private fun AlertMapPane(
    alertData: AlertData,
    pWaveRadiusKm: Double,
    sWaveRadiusKm: Double,
    mapStyle: EewMapStyle,
    modifier: Modifier = Modifier,
    showChips: Boolean = true,
) {
    Box(modifier = modifier.fillMaxSize()) {
        EarthquakeMap(
            style = mapStyle,
            mode = EarthquakeMapMode.ALERT,
            modifier = Modifier.fillMaxSize(),
            userPoint = MapPoint(alertData.userLatitude, alertData.userLongitude),
            interactive = false,
            epicenter = MapPoint(alertData.event.latitude, alertData.event.longitude),
            pWaveRadiusKm = pWaveRadiusKm,
            sWaveRadiusKm = sWaveRadiusKm,
            showDistanceLine = true,
            showAttribution = showChips,
        )

        if (showChips) {
            DistanceChip(
                alertData = alertData,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(EeqSpacing.sm),
            )
            WaveLegend(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(EeqSpacing.sm),
            )
        }
    }
}

@Composable
private fun DistanceChip(alertData: AlertData, modifier: Modifier = Modifier) {
    if (alertData.distanceKm > 0.0) {
        MapChip(
            text = stringResource(R.string.alert_distance_km)
                .format("%.0f".format(alertData.distanceKm)),
            modifier = modifier,
        )
    }
}

@Composable
private fun WaveLegend(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(EeqSpacing.xs),
    ) {
        WaveChip(color = PWaveBlue, label = stringResource(R.string.alert_wave_p))
        WaveChip(color = SWaveRed, label = stringResource(R.string.alert_wave_s))
    }
}

@Composable
private fun MapChip(text: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xCC000000),
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun WaveChip(color: Color, label: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xCC000000),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
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
// Info pane
// ---------------------------------------------------------------------------

@Composable
private fun AlertInfoPane(
    alertData: AlertData,
    sRemaining: Double,
    sArrived: Boolean,
    elapsedSeconds: Float,
    intense: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AlertBlue)
            .padding(horizontal = EeqSpacing.lg, vertical = if (compact) EeqSpacing.md else EeqSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(if (compact) EeqSpacing.sm else EeqSpacing.md),
    ) {
        // ── Countdown + CSIS badge ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CountdownBlock(
                sRemaining = sRemaining,
                sArrived = sArrived,
                elapsedSeconds = elapsedSeconds,
                sWaveSeconds = alertData.sWaveSeconds,
                intense = intense,
            )
            CsisBadge(csis = alertData.localCsis, compact = compact)
        }

        // ── P/S wave status ──
        Row(horizontalArrangement = Arrangement.spacedBy(EeqSpacing.sm)) {
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

        // ── Epicenter / magnitude / depth / distance ──
        Row(modifier = Modifier.fillMaxWidth()) {
            StatTile(
                label = stringResource(R.string.epicenter_label),
                value = alertData.event.hypocenter.ifBlank { "—" },
                modifier = Modifier.weight(1.4f),
            )
            StatTile(
                label = stringResource(R.string.magnitude_label),
                value = "M%.1f".format(alertData.event.magnitude),
                valueColor = Color(0xFFFFD54F),
                modifier = Modifier.weight(0.8f),
            )
            alertData.event.depth?.let { depth ->
                StatTile(
                    label = stringResource(R.string.depth_label),
                    value = "%.0f km".format(depth),
                    modifier = Modifier.weight(0.9f),
                )
            }
        }
    }
}

@Composable
private fun CountdownBlock(
    sRemaining: Double,
    sArrived: Boolean,
    elapsedSeconds: Float,
    sWaveSeconds: Double,
    intense: Boolean,
    modifier: Modifier = Modifier,
) {
    val number = if (sArrived) {
        max(0f, elapsedSeconds - sWaveSeconds.toFloat()).toInt()
    } else {
        ceil(sRemaining).toInt()
    }

    Column(modifier = modifier) {
        Text(
            text = if (sArrived) stringResource(R.string.alert_waves_arrived)
                   else stringResource(R.string.alert_countdown_hint),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.75f),
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = number.toString(),
                color = if (intense && !sArrived) Color(0xFFFFD54F) else Color.White,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 76.sp,
                    fontWeight = FontWeight.Black,
                    fontFeatureSettings = "tnum",
                ),
            )
            Text(
                text = "秒",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.padding(start = 6.dp, bottom = 12.dp),
            )
        }
    }
}

@Composable
private fun CsisBadge(csis: Double, compact: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 68.dp else 84.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(csisColor(csis)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = csis.toInt().toString(),
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = if (compact) 30.sp else 38.sp,
                    ),
                    color = Color.White,
                )
                Text(
                    text = stringResource(R.string.alert_intensity_unit),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.9f),
                )
            }
        }
        Text(
            text = stringResource(R.string.alert_intensity_expected),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.75f),
        )
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
        shape = RoundedCornerShape(10.dp),
        color = Color.White.copy(alpha = if (arrived) 0.08f else 0.14f),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (arrived) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(14.dp),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(color),
                )
            }
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
                color = if (arrived) Color.White.copy(alpha = 0.6f) else color,
            )
        }
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Color.White,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.65f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            maxLines = 1,
        )
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
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White.copy(alpha = 0.14f),
            contentColor = Color.White,
        ),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
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
        source = "中国地震台网",
        reportTime = "2024-01-15 10:30:00",
        reportNum = 2,
        originTime = "2024-01-15 10:29:00",
        hypocenter = "四川成都市",
        latitude = 30.5,
        longitude = 104.0,
        magnitude = 5.5,
        depth = 10.0,
        maxIntensity = 4.0,
    ),
    userLatitude = 31.0,
    userLongitude = 104.5,
    pWaveSeconds = 15.0,
    sWaveSeconds = 30.0,
    localCsis = 4.0,
    distanceKm = 78.0,
    isSimulation = true,
)

@Preview(name = "Alert Header")
@Composable
private fun AlertHeaderPreview() {
    MyEarthQuakeAlertTheme {
        Column {
            AlertHeader(alertData = sampleAlertData, intense = false)
            AlertHeader(alertData = sampleAlertData.copy(isSimulation = false), intense = true)
        }
    }
}

@Preview(name = "Alert Info Pane", device = "spec:width=600dp,height=360dp")
@Composable
private fun AlertInfoPanePreview() {
    MyEarthQuakeAlertTheme {
        AlertInfoPane(
            alertData = sampleAlertData,
            sRemaining = 23.0,
            sArrived = false,
            elapsedSeconds = 7f,
            intense = false,
        )
    }
}
