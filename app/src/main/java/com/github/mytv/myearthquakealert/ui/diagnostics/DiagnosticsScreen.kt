package com.github.mytv.myearthquakealert.ui.diagnostics

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * 诊断 destination (design §5). Stub until the diagnostics page lands.
 *
 * @param compact a one-card summary for side panes (dual screen right pane, tablet).
 */
@Composable
fun DiagnosticsScreen(modifier: Modifier = Modifier, compact: Boolean = false) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("诊断") }
}
