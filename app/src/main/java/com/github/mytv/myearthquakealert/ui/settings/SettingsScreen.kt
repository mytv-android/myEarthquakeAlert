package com.github.mytv.myearthquakealert.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * 设置 destination with its own sub-pages (后台监测, 数据源, 界面布局, 位置, 关于与更新 …).
 * Stub until the settings pages land.
 *
 * @param openPage optional sub-page to open directly, e.g. "update" from an update prompt.
 */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier, openPage: String? = null) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("设置") }
}
