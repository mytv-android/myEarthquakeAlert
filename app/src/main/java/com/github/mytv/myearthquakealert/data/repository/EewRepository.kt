package com.github.mytv.myearthquakealert.data.repository

import android.util.Log
import com.github.mytv.myearthquakealert.data.api.*
import com.github.mytv.myearthquakealert.data.model.EewEvent
import com.github.mytv.myearthquakealert.data.model.EarthquakeInfo
import com.github.mytv.myearthquakealert.data.source.EewSource
import com.github.mytv.myearthquakealert.data.websocket.EewWebSocketClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class EewRepository(
    private val api: WolfxApi,
    private val webSocketClient: EewWebSocketClient,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    val connectionState: StateFlow<EewWebSocketClient.ConnectionState> = webSocketClient.connectionState

    val eewMessages: Flow<EewEvent> = webSocketClient.messages.mapNotNull { raw ->
        try {
            parseEewMessage(raw)
        } catch (e: Exception) {
            Log.w("EewRepository", "Failed to parse EEW message: ${e.message}", e)
            null
        }
    }

    fun connectWebSocket(source: EewSource) {
        webSocketClient.connect(source)
    }

    fun disconnectWebSocket() {
        webSocketClient.disconnect()
    }

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

    private fun parseEewMessage(raw: String): EewEvent {
        val jsonObj = json.parseToJsonElement(raw).jsonObject
        val type = jsonObj["type"]?.jsonPrimitive?.content ?: ""

        return when (type) {
            "sc_eew" -> {
                val resp = json.decodeFromString<ScEewResponse>(raw)
                EewEvent(
                    id = resp.ID.toString(),
                    eventId = resp.EventID,
                    source = EewSource.SICHUAN.name,
                    reportTime = resp.ReportTime,
                    reportNum = resp.ReportNum,
                    originTime = resp.OriginTime,
                    hypocenter = resp.HypoCenter,
                    latitude = resp.Latitude,
                    longitude = resp.Longitude,
                    magnitude = resp.Magunitude,
                    depth = resp.Depth,
                    maxIntensity = resp.MaxIntensity,
                )
            }
            "cenc_eew" -> {
                val resp = json.decodeFromString<CencEewResponse>(raw)
                EewEvent(
                    id = resp.ID,
                    eventId = resp.EventID,
                    source = EewSource.CENC.name,
                    reportTime = resp.ReportTime,
                    reportNum = resp.ReportNum,
                    originTime = resp.OriginTime,
                    hypocenter = resp.HypoCenter,
                    latitude = resp.Latitude,
                    longitude = resp.Longitude,
                    magnitude = resp.Magnitude,
                    depth = resp.Depth,
                    maxIntensity = resp.MaxIntensity,
                )
            }
            "fj_eew" -> {
                val resp = json.decodeFromString<FjEewResponse>(raw)
                EewEvent(
                    id = resp.ID.toString(),
                    eventId = resp.EventID,
                    source = EewSource.FUJIAN.name,
                    reportTime = resp.ReportTime,
                    reportNum = resp.ReportNum,
                    originTime = resp.OriginTime,
                    hypocenter = resp.HypoCenter,
                    latitude = resp.Latitude,
                    longitude = resp.Longitude,
                    magnitude = resp.Magunitude,
                    depth = null,
                    maxIntensity = 0.0,
                    isFinal = resp.isFinal,
                )
            }
            "cq_eew" -> {
                val resp = json.decodeFromString<CqEewResponse>(raw)
                EewEvent(
                    id = resp.ID,
                    eventId = resp.EventID,
                    source = EewSource.CHONGQING.name,
                    reportTime = resp.ReportTime,
                    reportNum = resp.ReportNum,
                    originTime = resp.OriginTime,
                    hypocenter = resp.HypoCenter,
                    latitude = resp.Latitude,
                    longitude = resp.Longitude,
                    magnitude = resp.Magnitude,
                    depth = resp.Depth,
                    maxIntensity = resp.MaxIntensity,
                )
            }
            else -> throw IllegalArgumentException("Unknown EEW type: $type")
        }
    }
}
