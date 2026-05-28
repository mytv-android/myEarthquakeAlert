package com.github.mytv.myearthquakealert.ui.alert

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.data.model.EewEvent
import com.github.mytv.myearthquakealert.service.AlertData
import com.github.mytv.myearthquakealert.ui.adaptive.backHandler
import com.github.mytv.myearthquakealert.ui.theme.EeqSpacing
import com.github.mytv.myearthquakealert.ui.theme.MyEarthQuakeAlertTheme
import com.github.mytv.myearthquakealert.ui.theme.csisColor
import kotlinx.coroutines.delay
import kotlin.math.max

@Composable
fun AlertOverlay(
    alertData: AlertData,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var elapsedSeconds by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(alertData) {
        while (true) {
            delay(1000)
            elapsedSeconds += 1f
        }
    }

    val remainingSeconds = max(0.0, alertData.sWaveSeconds - elapsedSeconds)

    backHandler(onBack = onDismiss)

    Box(
        modifier = modifier
            .width(600.dp)
            .background(
                color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.87f),
                shape = MaterialTheme.shapes.extraLarge,
            )
            .padding(EeqSpacing.md),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(EeqSpacing.sm)) {
            AlertHeader(
                sourceName = alertData.event.source,
                isSimulation = alertData.isSimulation,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(EeqSpacing.sm)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(EeqSpacing.sm)) {
                    AlertCountdown(
                        remainingSeconds = remainingSeconds,
                        totalSeconds = alertData.sWaveSeconds,
                        localCsis = alertData.localCsis,
                    )
                    AlertDescription(
                        hypocenter = alertData.event.hypocenter,
                        magnitude = alertData.event.magnitude,
                        remainingSeconds = remainingSeconds,
                        localCsis = alertData.localCsis,
                    )
                }

                AlertMap(
                    alertData = alertData,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun AlertHeader(
    sourceName: String,
    isSimulation: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.alert_title) + "（${sourceName}）",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.inverseOnSurface,
        )
        if (isSimulation) {
            Text(
                text = stringResource(R.string.simulation_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun AlertCountdown(
    remainingSeconds: Double,
    totalSeconds: Double,
    localCsis: Double,
    modifier: Modifier = Modifier,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = if (totalSeconds > 0) (remainingSeconds / totalSeconds).toFloat() else 0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 4000f),
        label = "countdown_progress",
    )

    val pulseAlpha by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_alpha",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(csisColor(localCsis).copy(alpha = pulseAlpha * 0.3f), RoundedCornerShape(48.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = remainingSeconds.toInt().toString(),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.inverseOnSurface,
            )
            Text(
                text = "秒",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.inverseOnSurface,
            )
            Spacer(modifier = Modifier.height(EeqSpacing.sm))
            Text(
                text = "CSIS ${localCsis.toInt()}",
                style = MaterialTheme.typography.titleMedium,
                color = csisColor(localCsis),
            )
        }
    }
}

@Composable
private fun AlertDescription(
    hypocenter: String,
    magnitude: Double,
    remainingSeconds: Double,
    localCsis: Double,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(EeqSpacing.sm),
    ) {
        Text(
            text = stringResource(R.string.alert_line1),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.inverseOnSurface,
        )

        Text(
            text = buildAnnotatedString {
                append(stringResource(R.string.alert_line2, "", "").removeSuffix("发生了级地震。"))
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)) {
                    append(hypocenter)
                }
                append("发生了")
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)) {
                    append("M${magnitude}")
                }
                append("级地震。")
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.inverseOnSurface,
        )

        Text(
            text = buildAnnotatedString {
                append("地震波将在")
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)) {
                    append("${remainingSeconds.toInt()}")
                }
                append("秒后到达，预计震级为")
                withStyle(SpanStyle(color = csisColor(localCsis), fontWeight = FontWeight.Bold)) {
                    append("CSIS ${localCsis.toInt()}")
                }
                append("。")
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.inverseOnSurface,
        )
    }
}

private val sampleAlertData = AlertData(
    event = EewEvent(
        id = "preview-1",
        eventId = "PREVIEW",
        source = "中国地震台网",
        reportTime = "2024-01-15 10:30:00",
        reportNum = 1,
        originTime = "2024-01-15 10:29:00",
        hypocenter = "四川成都市",
        latitude = 30.5,
        longitude = 104.0,
        magnitude = 5.5,
        depth = 10.0,
        maxIntensity = 4,
    ),
    userLatitude = 31.0,
    userLongitude = 104.5,
    pWaveSeconds = 15.0,
    sWaveSeconds = 30.0,
    localCsis = 4.0,
    isSimulation = true,
)

@Preview(name = "Alert Overlay", device = "spec:width=600dp,height=400dp")
@Composable
private fun AlertOverlayPreview() {
    MyEarthQuakeAlertTheme {
        Box(modifier = Modifier.background(Color.Black)) {
            AlertOverlay(alertData = sampleAlertData, onDismiss = {})
        }
    }
}

@Preview(name = "Alert Countdown")
@Composable
private fun AlertCountdownPreview() {
    MyEarthQuakeAlertTheme(darkTheme = true) {
        Box(modifier = Modifier.background(Color.Black).padding(EeqSpacing.md)) {
            AlertCountdown(remainingSeconds = 25.0, totalSeconds = 30.0, localCsis = 4.0)
        }
    }
}

@Preview(name = "Alert Description")
@Composable
private fun AlertDescriptionPreview() {
    MyEarthQuakeAlertTheme(darkTheme = true) {
        Box(modifier = Modifier.background(Color.Black).padding(EeqSpacing.md)) {
            AlertDescription(hypocenter = "四川成都市", magnitude = 5.5, remainingSeconds = 25.0, localCsis = 4.0)
        }
    }
}
