package com.github.mytv.myearthquakealert.ui.main

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.data.model.EarthquakeInfo
import com.github.mytv.myearthquakealert.ui.adaptive.handleKeyEvents
import com.github.mytv.myearthquakealert.ui.theme.EeqSpacing
import com.github.mytv.myearthquakealert.ui.theme.MyEarthQuakeAlertTheme
import com.github.mytv.myearthquakealert.ui.theme.csisColor

@Composable
fun EarthquakeHistoryList(
    earthquakes: List<EarthquakeInfo>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(
            icon = Icons.Filled.History,
            title = stringResource(R.string.earthquake_history),
        )
        if (earthquakes.isEmpty()) {
            Text(
                text = stringResource(R.string.no_earthquakes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = EeqSpacing.md),
            )
        } else {
            earthquakes.forEach { eq ->
                EarthquakeHistoryItem(earthquake = eq, onClick = {})
                Spacer(modifier = Modifier.height(EeqSpacing.sm))
            }
        }
    }
}

@Composable
private fun EarthquakeHistoryItem(
    earthquake: EarthquakeInfo,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.01f else 1f, label = "scale")

    val intensity = earthquake.intensity.toDoubleOrNull() ?: 0.0

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .handleKeyEvents(interactionSource = interactionSource, onSelect = onClick),
    ) {
        Row(
            modifier = Modifier.padding(EeqSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(EeqSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left intensity color bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(40.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(csisColor(intensity)),
            )
            IntensityBadge(intensity = intensity)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = earthquake.location,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(EeqSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            text = "M${earthquake.magnitude}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                    Text(
                        text = "${earthquake.depth}km · ${earthquake.time}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private val sampleEarthquakes = listOf(
    EarthquakeInfo(no = 1, type = "地震", time = "2024-01-15 10:30:00", location = "四川成都市", magnitude = "4.5", depth = "10", latitude = "30.5", longitude = "104.0", intensity = "4"),
    EarthquakeInfo(no = 2, type = "地震", time = "2024-01-15 09:15:00", location = "云南昆明市", magnitude = "3.2", depth = "15", latitude = "25.0", longitude = "102.7", intensity = "2"),
    EarthquakeInfo(no = 3, type = "地震", time = "2024-01-14 22:00:00", location = "甘肃兰州市", magnitude = "5.1", depth = "20", latitude = "36.0", longitude = "103.8", intensity = "6"),
)

@Preview(name = "Earthquake History List")
@Composable
private fun EarthquakeHistoryListPreview() {
    MyEarthQuakeAlertTheme { EarthquakeHistoryList(earthquakes = sampleEarthquakes) }
}
