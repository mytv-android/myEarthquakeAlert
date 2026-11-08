package com.github.mytv.myearthquakealert.data.repository

import android.util.Log
import com.github.mytv.myearthquakealert.data.api.EarthquakeListEntry
import com.github.mytv.myearthquakealert.data.api.WolfxApi
import com.github.mytv.myearthquakealert.data.model.EarthquakeInfo
import com.github.mytv.myearthquakealert.data.websocket.EewWebSocketClient
import com.github.mytv.myearthquakealert.service.MonitorPhase
import com.github.mytv.myearthquakealert.service.MonitorStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json

/**
 * Earthquake data for the UI. Live early-warning reports are not exposed here: detection
 * runs only in the monitoring service (MonitorController), which owns the feed sockets.
 * The UI reads the catalog ([getEarthquakeHistory], browsing only) and the aggregated
 * connection state.
 */
class EewRepository(
    private val api: WolfxApi,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * 1.0-style aggregate of the monitoring links: CONNECTED when any source is connected
     * (or served by the HTTP fallback), CONNECTING while the service is (re)connecting.
     */
    val connectionState: StateFlow<EewWebSocketClient.ConnectionState> = MonitorStatus.state
        .map { state ->
            when (state.phase) {
                MonitorPhase.OFF, MonitorPhase.PAUSED -> EewWebSocketClient.ConnectionState.DISCONNECTED
                else -> when {
                    state.connectedCount > 0 -> EewWebSocketClient.ConnectionState.CONNECTED
                    state.sources.isEmpty() -> EewWebSocketClient.ConnectionState.DISCONNECTED
                    else -> EewWebSocketClient.ConnectionState.CONNECTING
                }
            }
        }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, EewWebSocketClient.ConnectionState.DISCONNECTED)

    suspend fun getEarthquakeHistory(): List<EarthquakeInfo> {
        val raw = api.getCencEqlist()
        return raw.entries.mapNotNull { (key, element) ->
            // Entries are keyed "No1".."No50"; other keys (e.g. "md5") are skipped.
            val no = key.removePrefix("No").toIntOrNull() ?: return@mapNotNull null
            val obj = element as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
            try {
                val entry = json.decodeFromJsonElement(EarthquakeListEntry.serializer(), obj)
                EarthquakeInfo(
                    no = no,
                    type = entry.type,
                    time = entry.time,
                    location = entry.location,
                    magnitude = entry.magnitude,
                    depth = entry.depth,
                    latitude = entry.latitude,
                    longitude = entry.longitude,
                    intensity = entry.intensity,
                )
            } catch (e: Exception) {
                Log.w("EewRepository", "Skipping malformed history entry $key: ${e.message}")
                null
            }
        }.sortedBy { it.no }
    }
}
