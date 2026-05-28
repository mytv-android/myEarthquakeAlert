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
import androidx.compose.ui.unit.sp
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.data.model.EewEvent
import com.github.mytv.myearthquakealert.service.AlertData
import com.github.mytv.myearthquakealert.ui.adaptive.backHandler
import com.github.mytv.myearthquakealert.ui.theme.AlertRed
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
            RedTitleBar(
                sourceName = alertData.event.source,
                isSimulation = alertData.isSimulation,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(EeqSpacing.sm)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(EeqSpacing.sm)) {
                    CountdownSection(
                        remainingSeconds = remainingSeconds,
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
private fun RedTitleBar(
    sourceName: String,
    isSimulation: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(AlertRed)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.alert_title) + "（${sourceName}）",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        if (isSimulation) {
            Text(
                text = stringResource(R.string.simulation_label),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun CountdownSection(
    remainingSeconds: Double,
    localCsis: Double,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start,
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = remainingSeconds.toInt().toString(),
                style = MaterialTheme.typography.displayLarge,
                color = Color.White,
                fontSize = 72.sp,
            )
            Text(
                text = "秒",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "CSIS ${localCsis.toInt()}",
            style = MaterialTheme.typography.titleLarge,
            color = csisColor(localCsis),
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun EpicenterSection(
    hypocenter: String,
    magnitude: Double,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = buildAnnotatedString {
                append("震源：")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(hypocenter)
                }
            },
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
        )
        Text(
            text = buildAnnotatedString {
                append("震级：")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFFFFEB3B))) {
                    append("M${magnitude}")
                }
            },
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
        )
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

@Preview(name = "Countdown Section")
@Composable
private fun CountdownSectionPreview() {
    MyEarthQuakeAlertTheme(darkTheme = true) {
        Box(modifier = Modifier.background(Color.Black).padding(EeqSpacing.md)) {
            CountdownSection(remainingSeconds = 25.0, localCsis = 4.0)
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
