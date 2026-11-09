package com.github.mytv.myearthquakealert.map.offline

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Persisted precache state and the 仅 Wi-Fi switch. */
data class OfflineMapPrefs(
    val area: CachedArea? = null,
    /** Download only on unmetered networks (default on: about 8 MB per run). */
    val wifiOnly: Boolean = true,
    /** Last run in which no tile could be fetched, epoch millis; 0 = none since the last success. */
    val failedAt: Long = 0L,
    /** Bumped by 清除缓存 so a run that was cancelled meanwhile does not write its area back. */
    val generation: Int = 0,
)

private val Context.offlineMapDataStore: DataStore<Preferences> by preferencesDataStore(name = "offline_map")

/**
 * Offline-map state in its own DataStore (credential-encrypted storage, like the tile cache):
 * only used after the first unlock.
 */
class OfflineMapStore private constructor(private val dataStore: DataStore<Preferences>) {

    val prefs: Flow<OfflineMapPrefs> = dataStore.data.map { it.toPrefs() }

    suspend fun read(): OfflineMapPrefs = prefs.first()

    /** Saves [area] unless 清除缓存 ran since [generation] was read; returns whether it was saved. */
    suspend fun saveArea(area: CachedArea, generation: Int): Boolean {
        var saved = false
        dataStore.edit { p ->
            if ((p[Keys.GENERATION] ?: 0) != generation) return@edit
            p[Keys.STYLE] = area.style
            p[Keys.LAT] = area.latitude
            p[Keys.LON] = area.longitude
            p[Keys.TILES] = area.tiles
            p[Keys.TOTAL] = area.total
            p[Keys.BYTES] = area.bytes
            p[Keys.UPDATED_AT] = area.updatedAt
            if (area.tiles > 0) p.remove(Keys.FAILED_AT)
            saved = true
        }
        return saved
    }

    suspend fun markFailed(now: Long, generation: Int) {
        dataStore.edit { p -> if ((p[Keys.GENERATION] ?: 0) == generation) p[Keys.FAILED_AT] = now }
    }

    suspend fun setWifiOnly(on: Boolean) {
        dataStore.edit { it[Keys.WIFI_ONLY] = on }
    }

    /** Forgets the cached area (清除缓存); keeps the 仅 Wi-Fi switch. */
    suspend fun clearArea() {
        dataStore.edit { p ->
            listOf(Keys.STYLE, Keys.LAT, Keys.LON, Keys.TILES, Keys.TOTAL, Keys.BYTES, Keys.UPDATED_AT, Keys.FAILED_AT)
                .forEach { p.remove(it) }
            p[Keys.GENERATION] = (p[Keys.GENERATION] ?: 0) + 1
        }
    }

    private fun Preferences.toPrefs(): OfflineMapPrefs {
        val style = this[Keys.STYLE]
        val lat = this[Keys.LAT]
        val lon = this[Keys.LON]
        val area = if (style != null && lat != null && lon != null) {
            CachedArea(
                style = style,
                latitude = lat,
                longitude = lon,
                tiles = this[Keys.TILES] ?: 0,
                total = this[Keys.TOTAL] ?: 0,
                bytes = this[Keys.BYTES] ?: 0L,
                updatedAt = this[Keys.UPDATED_AT] ?: 0L,
            )
        } else {
            null
        }
        return OfflineMapPrefs(
            area = area,
            wifiOnly = this[Keys.WIFI_ONLY] ?: true,
            failedAt = this[Keys.FAILED_AT] ?: 0L,
            generation = this[Keys.GENERATION] ?: 0,
        )
    }

    private object Keys {
        val STYLE = stringPreferencesKey("style")
        val LAT = doublePreferencesKey("latitude")
        val LON = doublePreferencesKey("longitude")
        val TILES = intPreferencesKey("tiles")
        val TOTAL = intPreferencesKey("total")
        val BYTES = longPreferencesKey("bytes")
        val UPDATED_AT = longPreferencesKey("updated_at")
        val FAILED_AT = longPreferencesKey("failed_at")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val GENERATION = intPreferencesKey("generation")
    }

    companion object {
        @Volatile private var instance: OfflineMapStore? = null

        fun get(context: Context): OfflineMapStore =
            instance ?: synchronized(this) {
                instance ?: OfflineMapStore(context.applicationContext.offlineMapDataStore).also { instance = it }
            }
    }
}
