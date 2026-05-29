package com.github.mytv.myearthquakealert.ui.main

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.ui.adaptive.handleKeyEvents
import com.github.mytv.myearthquakealert.ui.theme.EeqSpacing
import com.github.mytv.myearthquakealert.ui.theme.MyEarthQuakeAlertTheme

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
        // ── Magnitude slider card ──────────────────────────────────────
        val magInteractionSource = remember { MutableInteractionSource() }
        val magFocused by magInteractionSource.collectIsFocusedAsState()

        Card(
            onClick = {},
            modifier = Modifier
                .fillMaxWidth()
                .handleKeyEvents(
                    interactionSource = magInteractionSource,
                    onLeft = { onMinMagnitudeChange((minMagnitude - 0.5).coerceIn(0.0, 9.0)) },
                    onRight = { onMinMagnitudeChange((minMagnitude + 0.5).coerceIn(0.0, 9.0)) },
                    onContinuousLongLeft = { onMinMagnitudeChange((minMagnitude - 0.5).coerceIn(0.0, 9.0)) },
                    onContinuousLongRight = { onMinMagnitudeChange((minMagnitude + 0.5).coerceIn(0.0, 9.0)) },
                ),
            colors = CardDefaults.cardColors(
                containerColor = if (magFocused) MaterialTheme.colorScheme.surfaceContainerHigh
                else MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Column(modifier = Modifier.padding(EeqSpacing.md)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.min_magnitude),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "M$minMagnitude",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Slider(
                    value = minMagnitude.toFloat(),
                    onValueChange = { onMinMagnitudeChange(it.toDouble()) },
                    valueRange = 0f..9f,
                    steps = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(modifier = Modifier.height(EeqSpacing.sm))

        // ── Intensity slider card ──────────────────────────────────────
        val intensityInteractionSource = remember { MutableInteractionSource() }
        val intensityFocused by intensityInteractionSource.collectIsFocusedAsState()

        Card(
            onClick = {},
            modifier = Modifier
                .fillMaxWidth()
                .handleKeyEvents(
                    interactionSource = intensityInteractionSource,
                    onLeft = { onMinIntensityChange((minIntensity - 1).coerceIn(0, 12)) },
                    onRight = { onMinIntensityChange((minIntensity + 1).coerceIn(0, 12)) },
                    onContinuousLongLeft = { onMinIntensityChange((minIntensity - 1).coerceIn(0, 12)) },
                    onContinuousLongRight = { onMinIntensityChange((minIntensity + 1).coerceIn(0, 12)) },
                ),
            colors = CardDefaults.cardColors(
                containerColor = if (intensityFocused) MaterialTheme.colorScheme.surfaceContainerHigh
                else MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Column(modifier = Modifier.padding(EeqSpacing.md)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.min_intensity),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "$minIntensity",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Slider(
                    value = minIntensity.toFloat(),
                    onValueChange = { onMinIntensityChange(it.toInt()) },
                    valueRange = 0f..12f,
                    steps = 11,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(modifier = Modifier.height(EeqSpacing.sm))

        // ── Allow-dismiss-with-back card (same pattern as ServiceToggleCard) ──
        val dismissInteractionSource = remember { MutableInteractionSource() }
        val dismissFocused by dismissInteractionSource.collectIsFocusedAsState()

        Card(
            onClick = { onAllowDismissWithBackChange(!allowDismissWithBack) },
            modifier = Modifier
                .fillMaxWidth()
                .handleKeyEvents(
                    interactionSource = dismissInteractionSource,
                    onSelect = { onAllowDismissWithBackChange(!allowDismissWithBack) },
                ),
            colors = CardDefaults.cardColors(
                containerColor = if (dismissFocused) MaterialTheme.colorScheme.surfaceContainerHigh
                else MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Row(
                modifier = Modifier.padding(EeqSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.allow_dismiss),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    enabled = false,
                    checked = allowDismissWithBack,
                    onCheckedChange = null,
                )
            }
        }
    }
}

@Preview(name = "Threshold Settings")
@Composable
private fun ThresholdSettingsPreview() {
    MyEarthQuakeAlertTheme {
        ThresholdSettings(
            minMagnitude = 4.0,
            onMinMagnitudeChange = {},
            minIntensity = 3,
            onMinIntensityChange = {},
            intenseThreshold = 6,
            onIntenseThresholdChange = {},
            allowDismissWithBack = false,
            onAllowDismissWithBackChange = {},
        )
    }
}
