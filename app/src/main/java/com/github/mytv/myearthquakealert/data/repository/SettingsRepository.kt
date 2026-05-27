package com.github.mytv.myearthquakealert.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.github.mytv.myearthquakealert.data.source.EewSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class UserSettings(
    val selectedSource: EewSource = EewSource.CENC,
    val serviceEnabled: Boolean = false,
    val actionMinMagnitude: Double = 0.0,
    val actionMinIntensity: Int = 0,
    val intenseThreshold: Int = 5,
    val allowDismissWithBack: Boolean = true,
)

class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val SELECTED_SOURCE = stringPreferencesKey("selected_source")
        val SERVICE_ENABLED = booleanPreferencesKey("service_enabled")
        val ACTION_MIN_MAGNITUDE = doublePreferencesKey("action_min_magnitude")
        val ACTION_MIN_INTENSITY = intPreferencesKey("action_min_intensity")
        val INTENSE_THRESHOLD = intPreferencesKey("intense_threshold")
        val ALLOW_DISMISS_WITH_BACK = booleanPreferencesKey("allow_dismiss_with_back")
    }

    val settings: Flow<UserSettings> = dataStore.data.map { prefs ->
        UserSettings(
            selectedSource = try {
                EewSource.valueOf(prefs[Keys.SELECTED_SOURCE] ?: EewSource.CENC.name)
            } catch (_: IllegalArgumentException) {
                EewSource.CENC
            },
            serviceEnabled = prefs[Keys.SERVICE_ENABLED] ?: false,
            actionMinMagnitude = prefs[Keys.ACTION_MIN_MAGNITUDE] ?: 0.0,
            actionMinIntensity = prefs[Keys.ACTION_MIN_INTENSITY] ?: 0,
            intenseThreshold = prefs[Keys.INTENSE_THRESHOLD] ?: 5,
            allowDismissWithBack = prefs[Keys.ALLOW_DISMISS_WITH_BACK] ?: true,
        )
    }

    suspend fun updateSelectedSource(source: EewSource) {
        dataStore.edit { it[Keys.SELECTED_SOURCE] = source.name }
    }

    suspend fun updateServiceEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.SERVICE_ENABLED] = enabled }
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
}
