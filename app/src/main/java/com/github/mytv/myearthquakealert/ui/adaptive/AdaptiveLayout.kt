package com.github.mytv.myearthquakealert.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

enum class LayoutMode {
    COMPACT,
    MEDIUM,
    EXPANDED,
}

@Composable
fun currentLayoutMode(): LayoutMode {
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    return when (windowSizeClass.widthSizeClass) {
        WindowWidthSizeClass.Compact -> LayoutMode.COMPACT
        WindowWidthSizeClass.Medium -> LayoutMode.MEDIUM
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
            Column {
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
