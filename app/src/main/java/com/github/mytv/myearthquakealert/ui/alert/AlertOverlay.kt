package com.github.mytv.myearthquakealert.ui.alert

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.service.AlertData
import com.github.mytv.myearthquakealert.ui.theme.AlertRed
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

    Box(
        modifier = modifier
            .width(600.dp)
            .background(
                color = Color(0xDD000000),
                shape = MaterialTheme.shapes.large,
            )
            .padding(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AlertHeader(
                sourceName = alertData.event.source,
                isSimulation = alertData.isSimulation,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
            color = Color.White,
        )
        if (isSimulation) {
            Text(
                text = stringResource(R.string.simulation_label),
                style = MaterialTheme.typography.labelLarge,
                color = AlertRed,
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
        animationSpec = tween(durationMillis = 500),
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
            .background(csisColor(localCsis).copy(alpha = pulseAlpha * 0.3f), MaterialTheme.shapes.large),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = remainingSeconds.toInt().toString(),
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = Color.White,
            )
            Text(
                text = "秒",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
            )
            Spacer(modifier = Modifier.height(8.dp))
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
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.alert_line1),
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
        )

        Text(
            text = buildAnnotatedString {
                append(stringResource(R.string.alert_line2, "", "").removeSuffix("发生了级地震。"))
                withStyle(SpanStyle(color = AlertRed, fontWeight = FontWeight.Bold)) {
                    append(hypocenter)
                }
                append("发生了")
                withStyle(SpanStyle(color = AlertRed, fontWeight = FontWeight.Bold)) {
                    append("M${magnitude}")
                }
                append("级地震。")
            },
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
        )

        Text(
            text = buildAnnotatedString {
                append("地震波将在")
                withStyle(SpanStyle(color = AlertRed, fontWeight = FontWeight.Bold)) {
                    append("${remainingSeconds.toInt()}")
                }
                append("秒后到达，预计震级为")
                withStyle(SpanStyle(color = csisColor(localCsis), fontWeight = FontWeight.Bold)) {
                    append("CSIS ${localCsis.toInt()}")
                }
                append("。")
            },
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
        )
    }
}
