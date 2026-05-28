package com.github.mytv.myearthquakealert.ui.main

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.mytv.myearthquakealert.ui.theme.EeqSpacing
import com.github.mytv.myearthquakealert.ui.theme.MyEarthQuakeAlertTheme
import com.github.mytv.myearthquakealert.ui.theme.csisColor

@Composable
fun IntensityBadge(
    intensity: Double,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "badgeAlpha",
    )
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.8f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "badgeScale",
    )
    val currentAlpha = alpha
    val currentScale = scale
    Box(
        modifier = modifier
            .size(EeqSpacing.xxl)
            .graphicsLayer {
                this.alpha = currentAlpha
                this.scaleX = currentScale
                this.scaleY = currentScale
            }
            .clip(CircleShape)
            .background(csisColor(intensity)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = intensity.toInt().toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Preview(name = "Intensity 3")
@Composable
private fun IntensityBadgePreview3() {
    MyEarthQuakeAlertTheme { IntensityBadge(intensity = 3.0) }
}

@Preview(name = "Intensity 5")
@Composable
private fun IntensityBadgePreview5() {
    MyEarthQuakeAlertTheme { IntensityBadge(intensity = 5.0) }
}

@Preview(name = "Intensity 8")
@Composable
private fun IntensityBadgePreview8() {
    MyEarthQuakeAlertTheme { IntensityBadge(intensity = 8.0) }
}
