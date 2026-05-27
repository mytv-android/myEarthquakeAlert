package com.github.mytv.myearthquakealert.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.github.mytv.myearthquakealert.ui.theme.csisColor

@Composable
fun IntensityBadge(
    intensity: Double,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(csisColor(intensity)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = intensity.toInt().toString(),
            style = MaterialTheme.typography.labelLarge,
            color = androidx.compose.ui.graphics.Color.White,
        )
    }
}
