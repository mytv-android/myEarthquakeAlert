package com.github.mytv.myearthquakealert.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.mytv.myearthquakealert.data.model.EarthquakeInfo

@Composable
fun EarthquakeHistoryList(
    earthquakes: List<EarthquakeInfo>,
    modifier: Modifier = Modifier,
) {
    if (earthquakes.isEmpty()) return

    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(earthquakes) { eq ->
            EarthquakeHistoryItem(earthquake = eq)
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun EarthquakeHistoryItem(
    earthquake: EarthquakeInfo,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IntensityBadge(intensity = earthquake.intensity.toDoubleOrNull() ?: 0.0)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = earthquake.location,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "M${earthquake.magnitude} · ${earthquake.depth}km · ${earthquake.time}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
