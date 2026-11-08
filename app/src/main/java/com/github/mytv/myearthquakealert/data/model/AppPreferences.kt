package com.github.mytv.myearthquakealert.data.model

/** Settings › 显示 › 界面布局. AUTO resolves from device type, posture and window width. */
enum class LayoutOverride { AUTO, PHONE, TABLET, TV, KIOSK }

/** Settings › 位置 › 位置来源. MANUAL is required on TVs, which have no GPS. */
enum class LocationMode { AUTO, MANUAL }

/** Settings › 关于与更新 › 更新渠道. PREVIEW also offers GitHub prereleases. */
enum class UpdateChannel { STABLE, PREVIEW }

/** Settings › 显示 › 主题. Colours never follow the wallpaper. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

internal inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default
