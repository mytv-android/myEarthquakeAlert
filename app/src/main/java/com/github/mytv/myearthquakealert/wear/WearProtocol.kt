package com.github.mytv.myearthquakealert.wear

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Wearable Data Layer contract between the phone app and the watch app (design §6 手表:
 * 默认由手机转发预警). Both apps share the applicationId and signing key, which the Data
 * Layer requires; each keeps its own copy of this file (the watch's lives in
 * `:wear` › `com.github.mytv.myearthquakealert.wear.data.WearProtocol`) and the two are
 * pinned to the same JSON by golden-string unit tests on both sides.
 *
 * Payloads are UTF-8 JSON under [KEY_PAYLOAD] of a DataItem, or the raw bytes of a message.
 * Decoders ignore unknown keys, so either side can add optional fields without breaking an
 * older peer; [VERSION] is bumped only for incompatible changes.
 */
object WearPaths {
    /** DataItem, urgent: monitoring status of the phone ([WireStatus]). */
    const val STATUS = "/eew/status"

    /** DataItem, urgent: the latest early-warning events received by the phone ([WireQuakes]). */
    const val QUAKES = "/eew/quakes"

    /**
     * Message (low latency) and DataItem (so a watch that connects later still sees it):
     * the active alert ([WireAlert]). The DataItem is deleted when the alert ends.
     */
    const val ALERT = "/eew/alert"

    /** Message: the phone's alert ended or was dismissed ([WireAlertEnd]). */
    const val ALERT_END = "/eew/alert/end"

    /** Messages from the watch ([WireCommand]); the phone answers with [CMD_RESULT]. */
    const val CMD_PREFIX = "/eew/cmd"
    const val CMD_PAUSE = "/eew/cmd/pause"
    const val CMD_RESUME = "/eew/cmd/resume"
    const val CMD_START = "/eew/cmd/start"

    /** Message from the phone: outcome of a command ([WireCommandResult]). */
    const val CMD_RESULT = "/eew/result"

    /** DataMap key holding the JSON payload. */
    const val KEY_PAYLOAD = "payload"

    /** Capability advertised by the phone app (res/values/wear.xml). */
    const val CAPABILITY_PHONE = "eew_phone"

    /** Capability advertised by the watch app. */
    const val CAPABILITY_WATCH = "eew_watch"

    const val VERSION = 1

    /** Default pause sent by the watch's 暂停 1 小时. */
    const val DEFAULT_PAUSE_MS = 3_600_000L
}

/** One subscribed source on the phone. [link] is a `LinkState` name. */
@Serializable
data class WireSource(
    val id: String,
    val label: String,
    val link: String,
)

/**
 * Monitoring status of the phone. [phase] is a `MonitorPhase` name; [missing] lists unmet
 * `DeliveryRequirement` names. The user location and alert thresholds let the watch's
 * standalone monitoring evaluate reports the same way the phone does.
 */
@Serializable
data class WireStatus(
    val v: Int = WearPaths.VERSION,
    val phase: String,
    val phaseSince: Long = 0L,
    val runningSince: Long? = null,
    val pauseUntil: Long = 0L,
    val sources: List<WireSource> = emptyList(),
    val missing: List<String> = emptyList(),
    val userLatitude: Double? = null,
    val userLongitude: Double? = null,
    val minMagnitude: Double = 0.0,
    val minIntensity: Int = 0,
    val strongThreshold: Int = 5,
    val updatedAt: Long = 0L,
) {
    /** Sources that deliver reports (WebSocket open or HTTP fallback polling). */
    val connectedCount: Int get() = sources.count { it.link == "CONNECTED" || it.link == "POLLING" }
}

/** One early-warning event as last received by the phone (newest report). */
@Serializable
data class WireQuake(
    val eventId: String,
    /** `EewSource` name. */
    val source: String,
    val sourceLabel: String,
    val place: String,
    val magnitude: Double,
    val depthKm: Double? = null,
    val latitude: Double,
    val longitude: Double,
    /** Origin time, epoch millis; 0 when the report's time could not be parsed. */
    val originTime: Long = 0L,
    /** Epicentral intensity reported by the source, 0 when it gives none. */
    val maxIntensity: Double = 0.0,
    /** Estimated intensity at the user. */
    val localCsis: Double = 0.0,
    val distanceKm: Double = 0.0,
    /** `AlertTier` name: RECORD, REMIND or STRONG. */
    val tier: String = "RECORD",
    val reports: Int = 1,
    val isFinal: Boolean = false,
    val receivedAt: Long = 0L,
)

@Serializable
data class WireQuakes(
    val v: Int = WearPaths.VERSION,
    /** Newest first. */
    val quakes: List<WireQuake> = emptyList(),
    val updatedAt: Long = 0L,
)

/**
 * The phone's active alert. Times are on the phone's clock: [originAt] is the origin time
 * (clock-corrected on the phone) and the wave times count from it; [sentAt] lets the watch
 * detect a skewed clock.
 */
@Serializable
data class WireAlert(
    val v: Int = WearPaths.VERSION,
    val eventId: String,
    val source: String,
    val sourceLabel: String,
    val place: String,
    val magnitude: Double,
    val depthKm: Double? = null,
    val latitude: Double,
    val longitude: Double,
    val userLatitude: Double,
    val userLongitude: Double,
    val originAt: Long,
    val pWaveSeconds: Double,
    val sWaveSeconds: Double,
    val localCsis: Double,
    val distanceKm: Double,
    /** `AlertTier` name: REMIND or STRONG. */
    val tier: String,
    val drill: Boolean = false,
    val reportNum: Int = 1,
    val isFinal: Boolean = false,
    val presentedAt: Long,
    val sentAt: Long,
)

@Serializable
data class WireAlertEnd(
    val v: Int = WearPaths.VERSION,
    val eventId: String,
    val sentAt: Long,
)

/** A command from the watch. [durationMs] is only used by pause. */
@Serializable
data class WireCommand(
    val v: Int = WearPaths.VERSION,
    val durationMs: Long = 0L,
    val sentAt: Long = 0L,
)

/** Outcome of a watch command. [error] is one of the `WireCommandResult.ERR_*` codes. */
@Serializable
data class WireCommandResult(
    val v: Int = WearPaths.VERSION,
    /** The command path, e.g. [WearPaths.CMD_PAUSE]. */
    val command: String,
    val ok: Boolean,
    val error: String? = null,
    /** Phone phase after the command (`MonitorPhase` name). */
    val phase: String? = null,
    val pauseUntil: Long = 0L,
    val sentAt: Long = 0L,
) {
    companion object {
        /** Monitoring is switched off on the phone (pause makes no sense). */
        const val ERR_OFF = "off"

        /** The phone's system did not allow starting the service from the background. */
        const val ERR_BACKGROUND = "background"

        /** The service was asked but did not reach the expected phase in time. */
        const val ERR_TIMEOUT = "timeout"

        /** Unknown command or malformed payload. */
        const val ERR_INVALID = "invalid"

        /** Any other failure. */
        const val ERR_FAILED = "failed"
    }
}

/** JSON codec of the payloads. Decoders return null for empty or malformed input; never throw. */
object WearCodec {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    fun encode(value: WireStatus): ByteArray = encode(WireStatus.serializer(), value)
    fun encode(value: WireQuakes): ByteArray = encode(WireQuakes.serializer(), value)
    fun encode(value: WireAlert): ByteArray = encode(WireAlert.serializer(), value)
    fun encode(value: WireAlertEnd): ByteArray = encode(WireAlertEnd.serializer(), value)
    fun encode(value: WireCommand): ByteArray = encode(WireCommand.serializer(), value)
    fun encode(value: WireCommandResult): ByteArray = encode(WireCommandResult.serializer(), value)

    fun decodeStatus(bytes: ByteArray?): WireStatus? = decode(WireStatus.serializer(), bytes)
    fun decodeQuakes(bytes: ByteArray?): WireQuakes? = decode(WireQuakes.serializer(), bytes)
    fun decodeAlert(bytes: ByteArray?): WireAlert? = decode(WireAlert.serializer(), bytes)
    fun decodeAlertEnd(bytes: ByteArray?): WireAlertEnd? = decode(WireAlertEnd.serializer(), bytes)
    fun decodeCommand(bytes: ByteArray?): WireCommand? = decode(WireCommand.serializer(), bytes)
    fun decodeCommandResult(bytes: ByteArray?): WireCommandResult? = decode(WireCommandResult.serializer(), bytes)

    private fun <T> encode(serializer: KSerializer<T>, value: T): ByteArray =
        json.encodeToString(serializer, value).encodeToByteArray()

    private fun <T> decode(serializer: KSerializer<T>, bytes: ByteArray?): T? {
        if (bytes == null || bytes.isEmpty()) return null
        return try {
            json.decodeFromString(serializer, bytes.decodeToString())
        } catch (e: Exception) {
            null
        }
    }
}
