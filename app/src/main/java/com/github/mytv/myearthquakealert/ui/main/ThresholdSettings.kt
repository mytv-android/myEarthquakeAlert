package com.github.mytv.myearthquakealert.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.mytv.myearthquakealert.R

@Composable
fun ThresholdSettings(
    minMagnitude: Double,
    onMinMagnitudeChange: (Double) -> Unit,
    minIntensity: Int,
    onMinIntensityChange: (Int) -> Unit,
    intenseThreshold: Int,
    onIntenseThresholdChange: (Int) -> Unit,
    allowDismissWithBack: Boolean,
    onAllowDismissWithBackChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.min_magnitude),
            style = MaterialTheme.typography.titleMedium,
        )
        Slider(
            value = minMagnitude.toFloat(),
            onValueChange = { onMinMagnitudeChange(it.toDouble()) },
            valueRange = 0f..9f,
            steps = 8,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.min_intensity),
            style = MaterialTheme.typography.titleMedium,
        )
        Slider(
            value = minIntensity.toFloat(),
            onValueChange = { onMinIntensityChange(it.toInt()) },
            valueRange = 0f..12f,
            steps = 11,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.allow_dismiss),
                style = MaterialTheme.typography.bodyLarge,
            )
            Switch(
                checked = allowDismissWithBack,
                onCheckedChange = onAllowDismissWithBackChange,
            )
        }
    }
}
