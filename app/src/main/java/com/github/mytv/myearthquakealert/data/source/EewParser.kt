package com.github.mytv.myearthquakealert.data.source

import com.github.mytv.myearthquakealert.data.api.CencEewResponse
import com.github.mytv.myearthquakealert.data.api.CqEewResponse
import com.github.mytv.myearthquakealert.data.api.FjEewResponse
import com.github.mytv.myearthquakealert.data.api.ScEewResponse
import com.github.mytv.myearthquakealert.data.model.EewEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/** One decoded frame of a Wolfx feed, from the WebSocket or the HTTP fallback. */
sealed interface EewFrame {
    /** An earthquake early-warning report. */
    data class Report(val event: EewEvent) : EewFrame

    /** The feed's own keep-alive (`{"type":"heartbeat",…}`, about once a minute). */
    data class Heartbeat(val serverTime: Long?) : EewFrame

    /** Reply to the client's text `ping` (`{"type":"pong","timestamp":…}`). */
    data class Pong(val serverTime: Long?) : EewFrame

    /** A well-formed frame of a type that carries no report (e.g. a feed of another source). */
    data class Ignored(val type: String) : EewFrame

    /** Not JSON, or a report without the fields an alert needs. */
    data class Invalid(val reason: String) : EewFrame
}

/**
 * The single parser shared by the WebSocket and the HTTP fallback poller. WebSocket frames
 * carry a `type` (`cenc_eew`, `heartbeat`, `pong`…); HTTP bodies carry none, so [parse]
 * falls back to the feed of the [source] the frame came from.
 *
 * Never throws: heartbeats and pongs are ordinary frames, not parse errors.
 */
object EewParser {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    fun parse(raw: String, source: EewSource): EewFrame {
        val obj = try {
            json.parseToJsonElement(raw) as? JsonObject
        } catch (e: Exception) {
            null
        } ?: return EewFrame.Invalid("not a JSON object")

        val type = (obj["type"] as? JsonPrimitive)?.contentOrNull
        when (type) {
            "heartbeat" -> return EewFrame.Heartbeat(obj.long("timestamp"))
            "pong" -> return EewFrame.Pong(obj.long("timestamp"))
        }
        val feed = if (type == null) source else EewSource.fromTypeKey(type) ?: return EewFrame.Ignored(type)

        val event = try {
            decode(feed, obj)
        } catch (e: Exception) {
            return EewFrame.Invalid("${feed.typeKey}: ${e.message}")
        }
        return when {
            event.eventId.isBlank() -> EewFrame.Invalid("${feed.typeKey}: empty EventID")
            // Guard against malformed reports with a null-island epicenter.
            event.latitude == 0.0 && event.longitude == 0.0 -> EewFrame.Invalid("${feed.typeKey}: empty epicenter")
            else -> EewFrame.Report(event)
        }
    }

    private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull

    private fun decode(feed: EewSource, obj: JsonObject): EewEvent = when (feed) {
        EewSource.SICHUAN -> json.decodeFromJsonElement(ScEewResponse.serializer(), obj).let { r ->
            EewEvent(
                id = r.ID.toString(),
                eventId = r.EventID,
                source = feed.name,
                reportTime = r.ReportTime,
                reportNum = r.ReportNum,
                originTime = r.OriginTime,
                hypocenter = r.HypoCenter,
                latitude = r.Latitude,
                longitude = r.Longitude,
                magnitude = r.Magunitude,
                // Sichuan reports 0 when the depth is not yet determined.
                depth = r.Depth?.takeIf { it > 0.0 },
                maxIntensity = r.MaxIntensity,
            )
        }
        EewSource.CENC -> json.decodeFromJsonElement(CencEewResponse.serializer(), obj).let { r ->
            EewEvent(
                id = r.ID,
                eventId = r.EventID,
                source = feed.name,
                reportTime = r.ReportTime,
                reportNum = r.ReportNum,
                originTime = r.OriginTime,
                hypocenter = r.HypoCenter,
                latitude = r.Latitude,
                longitude = r.Longitude,
                magnitude = r.Magnitude,
                depth = r.Depth,
                maxIntensity = r.MaxIntensity,
            )
        }
        EewSource.FUJIAN -> json.decodeFromJsonElement(FjEewResponse.serializer(), obj).let { r ->
            EewEvent(
                id = r.ID.toString(),
                eventId = r.EventID,
                source = feed.name,
                reportTime = r.ReportTime,
                reportNum = r.ReportNum,
                originTime = r.OriginTime,
                hypocenter = r.HypoCenter,
                latitude = r.Latitude,
                longitude = r.Longitude,
                magnitude = r.Magunitude,
                depth = null,
                maxIntensity = 0.0,
                isFinal = r.isFinal,
            )
        }
        EewSource.CHONGQING -> json.decodeFromJsonElement(CqEewResponse.serializer(), obj).let { r ->
            EewEvent(
                id = r.ID,
                eventId = r.EventID,
                source = feed.name,
                reportTime = r.ReportTime,
                reportNum = r.ReportNum,
                originTime = r.OriginTime,
                hypocenter = r.HypoCenter,
                latitude = r.Latitude,
                longitude = r.Longitude,
                magnitude = r.Magnitude,
                depth = r.Depth,
                maxIntensity = r.MaxIntensity,
            )
        }
    }
}
