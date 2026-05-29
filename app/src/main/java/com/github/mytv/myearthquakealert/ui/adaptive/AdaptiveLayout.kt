package com.github.mytv.myearthquakealert.ui.adaptive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.Preview
import androidx.window.core.layout.WindowWidthSizeClass
import com.github.mytv.myearthquakealert.ui.theme.MyEarthQuakeAlertTheme

enum class LayoutMode {
    COMPACT,
    MEDIUM,
    EXPANDED,
}

@Composable
fun currentLayoutMode(): LayoutMode {
    val widthSizeClass = currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass
    return when {
        widthSizeClass == WindowWidthSizeClass.COMPACT -> LayoutMode.COMPACT
        widthSizeClass == WindowWidthSizeClass.MEDIUM -> LayoutMode.MEDIUM
        else -> LayoutMode.EXPANDED
    }
}

@Composable
fun AdaptiveLayout(
    settingsPane: @Composable () -> Unit,
    listPane: @Composable () -> Unit,
    detailPane: @Composable () -> Unit,
) {
    when (currentLayoutMode()) {
        LayoutMode.COMPACT -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                settingsPane()
                listPane()
            }
        }
        LayoutMode.MEDIUM -> {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) {
                    settingsPane()
                }
                Box(modifier = Modifier.weight(2f)) {
                    listPane()
                }
            }
        }
        LayoutMode.EXPANDED -> {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) {
                    settingsPane()
                }
                Box(modifier = Modifier.weight(1f)) {
                    listPane()
                }
                Box(modifier = Modifier.weight(2f)) {
                    detailPane()
                }
            }
        }
    }
}

@Preview(name = "Adaptive Layout Compact", device = "spec:width=360dp,height=640dp")
@Composable
private fun AdaptiveLayoutCompactPreview() {
    MyEarthQuakeAlertTheme {
        AdaptiveLayout(
            settingsPane = { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer)) { Text("Settings") } },
            listPane = { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.secondaryContainer)) { Text("List") } },
            detailPane = { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.tertiaryContainer)) { Text("Detail") } },
        )
    }
}

@Preview(name = "Adaptive Layout Expanded", device = "spec:width=1200dp,height=800dp")
@Composable
private fun AdaptiveLayoutExpandedPreview() {
    MyEarthQuakeAlertTheme {
        AdaptiveLayout(
            settingsPane = { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer)) { Text("Settings") } },
            listPane = { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.secondaryContainer)) { Text("List") } },
            detailPane = { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.tertiaryContainer)) { Text("Detail") } },
        )
    }
}
