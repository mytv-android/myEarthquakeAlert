package com.github.mytv.myearthquakealert.data.model

/** Settings › 显示 › 界面布局. AUTO resolves from device type, posture and window width. */
enum class LayoutOverride { AUTO, PHONE, TABLET, TV, KIOSK }

/** Settings › 位置 › 位置来源. MANUAL is required on TVs, which have no GPS. */
enum class LocationMode { AUTO, MANUAL }

/** Settings › 关于与更新 › 更新渠道. PREVIEW also offers GitHub prereleases. */
enum class UpdateChannel { STABLE, PREVIEW }

/** Settings › 显示 › 主题. Colours never follow the wallpaper. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Settings › 预警 › 声音与振动 › 警报音: which sound a STRONG alert plays on the alarm stream.
 *
 * [PRESET] is the built-in siren ([com.github.mytv.myearthquakealert.R.raw.eew_alert]): it is
 * at full level from its first sample and identical on every device, so it never inherits a
 * system alarm ringtone that fades in. [SYSTEM] plays the ringtone the user picked as their
 * alarm sound instead.
 */
enum class AlertSound { PRESET, SYSTEM }

internal inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default
