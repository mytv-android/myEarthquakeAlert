package com.github.mytv.myearthquakealert.ui.main

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.github.mytv.myearthquakealert.data.repository.UserSettings
import com.github.mytv.myearthquakealert.ui.navigation.Destination

/** The loaded settings; MainActivity only composes the shell once they are known. */
val LocalUserSettings = compositionLocalOf { UserSettings() }

/** Navigation commands the destinations use to reach each other. */
@Immutable
data class ShellNavigator(
    val open: (Destination) -> Unit = {},
    /** Opens 地震 with this quake's details ([com.github.mytv.myearthquakealert.ui.quakes.QuakeUi.key]). */
    val openQuake: (String) -> Unit = {},
    /** Opens 地震 with the search field focused. */
    val searchQuakes: () -> Unit = {},
    /** Opens 设置 on a sub-page ("update" = 关于与更新). */
    val openSettingsPage: (String) -> Unit = {},
    /** 切换布局 for this session. */
    val chooseLayout: () -> Unit = {},
)

val LocalShellNavigator = staticCompositionLocalOf { ShellNavigator() }
