package com.github.mytv.myearthquakealert.ui.alert

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.github.mytv.myearthquakealert.domain.SeismicCalculator
import com.github.mytv.myearthquakealert.service.AlertData
import com.github.mytv.myearthquakealert.ui.theme.AlertMapBackground
import com.github.mytv.myearthquakealert.ui.theme.MyEarthQuakeAlertTheme
import com.github.mytv.myearthquakealert.ui.theme.PWaveBlue
import com.github.mytv.myearthquakealert.ui.theme.SWaveRed
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.delay

@Composable
fun AlertMap(
    alertData: AlertData,
    modifier: Modifier = Modifier,
) {
    var elapsedSeconds by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            elapsedSeconds += 1f
        }
    }

    val depthKm = alertData.event.depth ?: 10.0
    val pWaveKm = SeismicCalculator.calcWaveRadius(depthKm, elapsedSeconds.toDouble(), true)
    val sWaveKm = SeismicCalculator.calcWaveRadius(depthKm, elapsedSeconds.toDouble(), false)

    val textMeasurer = rememberTextMeasurer()

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val maxRadius = minOf(cx, cy) * 0.9f
            val scale = maxRadius / 500f

            drawCircle(
                color = PWaveBlue.copy(alpha = 0.2f),
                radius = (pWaveKm * scale).toFloat().coerceAtMost(maxRadius),
                center = Offset(cx, cy),
            )
            drawCircle(
                color = PWaveBlue,
                radius = (pWaveKm * scale).toFloat().coerceAtMost(maxRadius),
                center = Offset(cx, cy),
                style = Stroke(width = 2f),
            )

            drawCircle(
                color = SWaveRed.copy(alpha = 0.2f),
                radius = (sWaveKm * scale).toFloat().coerceAtMost(maxRadius),
                center = Offset(cx, cy),
            )
            drawCircle(
                color = SWaveRed,
                radius = (sWaveKm * scale).toFloat().coerceAtMost(maxRadius),
                center = Offset(cx, cy),
                style = Stroke(width = 2f),
            )

            drawCircle(
                color = Color.White,
                radius = 6f,
                center = Offset(cx, cy),
            )

            drawText(
                textMeasurer = textMeasurer,
                text = "P",
                topLeft = Offset(cx - 4f, cy - (pWaveKm * scale).toFloat().coerceAtMost(maxRadius) - 16f),
                style = TextStyle(color = PWaveBlue, fontSize = 12.sp),
            )
            drawText(
                textMeasurer = textMeasurer,
                text = "S",
                topLeft = Offset(cx - 4f, cy + (sWaveKm * scale).toFloat().coerceAtMost(maxRadius) + 4f),
                style = TextStyle(color = SWaveRed, fontSize = 12.sp),
            )
        }
    }
}

@Preview(name = "Alert Map", device = "spec:width=240dp,height=300dp")
@Composable
private fun AlertMapPreview() {
    MyEarthQuakeAlertTheme {
        AlertMap(
            alertData = AlertData(
                event = com.github.mytv.myearthquakealert.data.model.EewEvent(
                    id = "preview", eventId = "P", source = "CENC",
                    reportTime = "", reportNum = 1, originTime = "",
                    hypocenter = "test", latitude = 30.0, longitude = 104.0,
                    magnitude = 5.0, depth = 10.0, maxIntensity = 4.0,
                ),
                userLatitude = 31.0, userLongitude = 104.5,
                pWaveSeconds = 15.0, sWaveSeconds = 30.0, localCsis = 4.0,
                isSimulation = true,
            ),
            modifier = Modifier.fillMaxSize(),
        )
    }
}
