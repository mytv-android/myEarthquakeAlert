package com.github.mytv.myearthquakealert.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.github.mytv.myearthquakealert.data.model.LayoutOverride
import com.github.mytv.myearthquakealert.data.model.LocationMode
import com.github.mytv.myearthquakealert.data.model.ThemeMode
import com.github.mytv.myearthquakealert.data.model.UpdateChannel
import com.github.mytv.myearthquakealert.data.model.enumOrDefault
import com.github.mytv.myearthquakealert.data.source.EewSource
import com.github.mytv.myearthquakealert.ui.map.EewMapStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class UserSettings(
    // 监测
    val serviceEnabled: Boolean = false,
    /** Restart monitoring after a reboot. Independent of [serviceEnabled]; only applies while it is on. */
    val bootStart: Boolean = true,
    /** Subscribed sources; reports are de-duplicated by event id across them. Never empty. */
    val enabledSources: Set<EewSource> = setOf(EewSource.CENC),
    /** Epoch millis until which monitoring is paused; 0 when not paused. */
    val pauseUntil: Long = 0L,
    // 预警
    val actionMinMagnitude: Double = 0.0,
    val actionMinIntensity: Int = 0,
    val intenseThreshold: Int = 5,
    val allowDismissWithBack: Boolean = true,
    val ttsEnabled: Boolean = true,
    val alarmBypassDnd: Boolean = true,
    // 显示
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val layoutOverride: LayoutOverride = LayoutOverride.AUTO,
    val spanHinge: Boolean = true,
    val tabletopSplit: Boolean = true,
    val singleScreen: Boolean = false,
    val mapStyle: EewMapStyle = EewMapStyle.AMAP,
    // 位置
    val locationMode: LocationMode = LocationMode.AUTO,
    val manualLatitude: Double? = null,
    val manualLongitude: Double? = null,
    val manualLocationName: String = "",
    // 更新
    val updateAutoCheck: Boolean = true,
    val updateChannel: UpdateChannel = UpdateChannel.STABLE,
    val updateWifiOnly: Boolean = true,
) {
    /** First subscribed source, for places that show a single source. */
    val primarySource: EewSource get() = EewSource.entries.firstOrNull { it in enabledSources } ?: EewSource.CENC

    fun isPaused(now: Long = System.currentTimeMillis()): Boolean = pauseUntil > now

    val hasManualLocation: Boolean
        get() = locationMode == LocationMode.MANUAL && manualLatitude != null && manualLongitude != null
}

class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val SELECTED_SOURCE = stringPreferencesKey("selected_source") // 1.0, migrated to ENABLED_SOURCES
        val ENABLED_SOURCES = stringSetPreferencesKey("enabled_sources")
        val SERVICE_ENABLED = booleanPreferencesKey("service_enabled")
        val BOOT_START = booleanPreferencesKey("boot_start")
        val PAUSE_UNTIL = longPreferencesKey("pause_until")
        val ACTION_MIN_MAGNITUDE = doublePreferencesKey("action_min_magnitude")
        val ACTION_MIN_INTENSITY = intPreferencesKey("action_min_intensity")
        val INTENSE_THRESHOLD = intPreferencesKey("intense_threshold")
        val ALLOW_DISMISS_WITH_BACK = booleanPreferencesKey("allow_dismiss_with_back")
        val TTS_ENABLED = booleanPreferencesKey("tts_enabled")
        val ALARM_BYPASS_DND = booleanPreferencesKey("alarm_bypass_dnd")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LAYOUT_OVERRIDE = stringPreferencesKey("layout_override")
        val SPAN_HINGE = booleanPreferencesKey("span_hinge")
        val TABLETOP_SPLIT = booleanPreferencesKey("tabletop_split")
        val SINGLE_SCREEN = booleanPreferencesKey("single_screen")
        val MAP_STYLE = stringPreferencesKey("map_style")
        val LOCATION_MODE = stringPreferencesKey("location_mode")
        val MANUAL_LAT = doublePreferencesKey("manual_lat")
        val MANUAL_LON = doublePreferencesKey("manual_lon")
        val MANUAL_NAME = stringPreferencesKey("manual_name")
        val UPDATE_AUTO_CHECK = booleanPreferencesKey("update_auto_check")
        val UPDATE_CHANNEL = stringPreferencesKey("update_channel")
        val UPDATE_WIFI_ONLY = booleanPreferencesKey("update_wifi_only")
    }

    val settings: Flow<UserSettings> = dataStore.data.map { prefs ->
        val legacySource = prefs[Keys.SELECTED_SOURCE]
        val sources = (prefs[Keys.ENABLED_SOURCES] ?: setOfNotNull(legacySource))
            .mapNotNull { name -> EewSource.entries.firstOrNull { it.name == name } }
            .toSet()
            .ifEmpty { setOf(EewSource.CENC) }
        UserSettings(
            serviceEnabled = prefs[Keys.SERVICE_ENABLED] ?: false,
            bootStart = prefs[Keys.BOOT_START] ?: true,
            enabledSources = sources,
            pauseUntil = prefs[Keys.PAUSE_UNTIL] ?: 0L,
            actionMinMagnitude = prefs[Keys.ACTION_MIN_MAGNITUDE] ?: 0.0,
            actionMinIntensity = prefs[Keys.ACTION_MIN_INTENSITY] ?: 0,
            intenseThreshold = prefs[Keys.INTENSE_THRESHOLD] ?: 5,
            allowDismissWithBack = prefs[Keys.ALLOW_DISMISS_WITH_BACK] ?: true,
            ttsEnabled = prefs[Keys.TTS_ENABLED] ?: true,
            alarmBypassDnd = prefs[Keys.ALARM_BYPASS_DND] ?: true,
            themeMode = enumOrDefault(prefs[Keys.THEME_MODE], ThemeMode.SYSTEM),
            layoutOverride = enumOrDefault(prefs[Keys.LAYOUT_OVERRIDE], LayoutOverride.AUTO),
            spanHinge = prefs[Keys.SPAN_HINGE] ?: true,
            tabletopSplit = prefs[Keys.TABLETOP_SPLIT] ?: true,
            singleScreen = prefs[Keys.SINGLE_SCREEN] ?: false,
            mapStyle = EewMapStyle.fromName(prefs[Keys.MAP_STYLE]),
            locationMode = enumOrDefault(prefs[Keys.LOCATION_MODE], LocationMode.AUTO),
            manualLatitude = prefs[Keys.MANUAL_LAT],
            manualLongitude = prefs[Keys.MANUAL_LON],
            manualLocationName = prefs[Keys.MANUAL_NAME] ?: "",
            updateAutoCheck = prefs[Keys.UPDATE_AUTO_CHECK] ?: true,
            updateChannel = enumOrDefault(prefs[Keys.UPDATE_CHANNEL], UpdateChannel.STABLE),
            updateWifiOnly = prefs[Keys.UPDATE_WIFI_ONLY] ?: true,
        )
    }

    suspend fun updateEnabledSources(sources: Set<EewSource>) {
        if (sources.isEmpty()) return
        dataStore.edit {
            it[Keys.ENABLED_SOURCES] = sources.map(EewSource::name).toSet()
            it.remove(Keys.SELECTED_SOURCE)
        }
    }

    suspend fun updateServiceEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.SERVICE_ENABLED] = enabled }
    }

    suspend fun updateBootStart(enabled: Boolean) {
        dataStore.edit { it[Keys.BOOT_START] = enabled }
    }

    suspend fun updatePauseUntil(epochMillis: Long) {
        dataStore.edit { it[Keys.PAUSE_UNTIL] = epochMillis }
    }

    suspend fun updateActionMinMagnitude(magnitude: Double) {
        dataStore.edit { it[Keys.ACTION_MIN_MAGNITUDE] = magnitude }
    }

    suspend fun updateActionMinIntensity(intensity: Int) {
        dataStore.edit { it[Keys.ACTION_MIN_INTENSITY] = intensity }
    }

    suspend fun updateIntenseThreshold(threshold: Int) {
        dataStore.edit { it[Keys.INTENSE_THRESHOLD] = threshold }
    }

    suspend fun updateAllowDismissWithBack(allow: Boolean) {
        dataStore.edit { it[Keys.ALLOW_DISMISS_WITH_BACK] = allow }
    }

    suspend fun updateTtsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.TTS_ENABLED] = enabled }
    }

    suspend fun updateAlarmBypassDnd(enabled: Boolean) {
        dataStore.edit { it[Keys.ALARM_BYPASS_DND] = enabled }
    }

    suspend fun updateThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun updateLayoutOverride(layout: LayoutOverride) {
        dataStore.edit { it[Keys.LAYOUT_OVERRIDE] = layout.name }
    }

    suspend fun updateSpanHinge(enabled: Boolean) {
        dataStore.edit { it[Keys.SPAN_HINGE] = enabled }
    }

    suspend fun updateTabletopSplit(enabled: Boolean) {
        dataStore.edit { it[Keys.TABLETOP_SPLIT] = enabled }
    }

    suspend fun updateSingleScreen(enabled: Boolean) {
        dataStore.edit { it[Keys.SINGLE_SCREEN] = enabled }
    }

    suspend fun updateMapStyle(style: EewMapStyle) {
        dataStore.edit { it[Keys.MAP_STYLE] = style.name }
    }

    suspend fun updateLocationMode(mode: LocationMode) {
        dataStore.edit { it[Keys.LOCATION_MODE] = mode.name }
    }

    suspend fun updateManualLocation(latitude: Double, longitude: Double, name: String) {
        dataStore.edit {
            it[Keys.MANUAL_LAT] = latitude
            it[Keys.MANUAL_LON] = longitude
            it[Keys.MANUAL_NAME] = name
            it[Keys.LOCATION_MODE] = LocationMode.MANUAL.name
        }
    }

    suspend fun updateUpdateAutoCheck(enabled: Boolean) {
        dataStore.edit { it[Keys.UPDATE_AUTO_CHECK] = enabled }
    }

    suspend fun updateUpdateChannel(channel: UpdateChannel) {
        dataStore.edit { it[Keys.UPDATE_CHANNEL] = channel.name }
    }

    suspend fun updateUpdateWifiOnly(enabled: Boolean) {
        dataStore.edit { it[Keys.UPDATE_WIFI_ONLY] = enabled }
    }
}
