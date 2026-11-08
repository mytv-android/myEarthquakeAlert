package com.github.mytv.myearthquakealert.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.github.mytv.myearthquakealert.R

/** 概览 / 地震 / 诊断 / 设置. */
@Composable
fun Destination.label(): String = stringResource(
    when (this) {
        Destination.OVERVIEW -> R.string.ui_dest_overview
        Destination.QUAKES -> R.string.ui_dest_quakes
        Destination.DIAGNOSTICS -> R.string.ui_dest_diagnostics
        Destination.SETTINGS -> R.string.tab_settings
    },
)

/** Filled when selected, outlined otherwise (design: 激活项是胶囊指示器 + 实心图标). */
@Composable
fun DestinationIcon(destination: Destination, selected: Boolean, modifier: Modifier = Modifier) {
    when (destination) {
        Destination.OVERVIEW -> Icon(if (selected) Icons.Filled.Home else Icons.Outlined.Home, contentDescription = null, modifier = modifier)
        Destination.QUAKES -> Icon(painterResource(R.drawable.ic_ui_earthquake), contentDescription = null, modifier = modifier)
        Destination.DIAGNOSTICS -> Icon(if (selected) Icons.Filled.MonitorHeart else Icons.Outlined.MonitorHeart, contentDescription = null, modifier = modifier)
        Destination.SETTINGS -> Icon(if (selected) Icons.Filled.Settings else Icons.Outlined.Settings, contentDescription = null, modifier = modifier)
    }
}
