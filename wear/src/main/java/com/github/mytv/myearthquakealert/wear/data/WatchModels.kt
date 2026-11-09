package com.github.mytv.myearthquakealert.wear.data

import com.github.mytv.myearthquakealert.wear.source.EewSource
import kotlinx.serialization.Serializable

/**
 * An alert as shown on the watch, from the phone ([viaPhone]) or from standalone
 * monitoring. All times are epoch millis on the watch's clock (phone times are converted
 * by [com.github.mytv.myearthquakealert.wear.domain.Countdown.fromPhone]).
 */
@Serializable
data class WatchAlert(
    val eventId: String,
    val sourceLabel: String,
    val place: String,
    val magnitude: Double,
    val depthKm: Double? = null,
    val latitude: Double,
    val longitude: Double,
    val userLatitude: Double,
    val userLongitude: Double,
    val localCsis: Double,
    val distanceKm: Double,
    /** STRONG tier (full screen + strong vibration); REMIND otherwise (vibration + card). */
    val strong: Boolean,
    val drill: Boolean = false,
    val reportNum: Int = 1,
    val isFinal: Boolean = false,
    val originAt: Long,
    val pArrivalAt: Long,
    val sArrivalAt: Long,
    /** When the watch first presented the alert; the countdown ring is full here. */
    val presentedAt: Long,
    val viaPhone: Boolean = true,
)

/**
 * The phase shown on every watch surface (app, tile, complication). The phone's
 * `MonitorPhase` names map 1:1; the watch adds its own link states.
 */
enum class WatchPhase {
    OFF,
    CONNECTING,
    MONITORING,
    RECONNECTING,
    DEGRADED,
    LIMITED,
    PAUSED,

    /** No reachable phone with the app, and no standalone monitoring. */
    DISCONNECTED,

    /** The phone is away; the watch monitors a Wolfx feed itself. */
    STANDALONE,

    /** A phone is reachable but has not synced a status yet. */
    WAITING;

    /** The phone's monitoring can be paused from the watch. */
    val pausable: Boolean get() = this == MONITORING || this == CONNECTING || this == RECONNECTING || this == DEGRADED || this == LIMITED

    /** Some feed is watched for this user right now (phone or watch). */
    val protecting: Boolean get() = this == MONITORING || this == DEGRADED || this == LIMITED || this == STANDALONE
}

/** Link of the watch's own standalone monitoring. */
enum class StandaloneLink { IDLE, CONNECTING, CONNECTED, RETRYING }

data class StandaloneState(
    /** 独立监测 is switched on in the watch settings. */
    val enabled: Boolean = false,
    /** The standalone service is running (it idles while the phone is reachable). */
    val running: Boolean = false,
    val link: StandaloneLink = StandaloneLink.IDLE,
    val source: EewSource = EewSource.CENC,
    /** A location is known, so local intensity and arrival can be estimated. */
    val hasLocation: Boolean = false,
    val lastFrameAt: Long? = null,
)

/** Outcome of a command sent to the phone, for the confirmation dialog. */
data class CommandOutcome(
    /** Increments per command so the same result twice still shows a dialog. */
    val id: Long,
    val command: String,
    val ok: Boolean,
    /** A `WireCommandResult.ERR_*` code, or [ERR_NO_PHONE] / [ERR_NO_REPLY] from the watch. */
    val error: String? = null,
) {
    companion object {
        const val ERR_NO_PHONE = "no_phone"
        const val ERR_NO_REPLY = "no_reply"
    }
}

object WatchPhases {

    /**
     * Phase from the phone [status], whether a phone is reachable ([phoneConnected], null
     * while unknown) and the [standalone] state.
     */
    fun resolve(status: WireStatus?, phoneConnected: Boolean?, standalone: StandaloneState, now: Long): WatchPhase {
        if (phoneConnected == false) {
            return if (standalone.running && standalone.link == StandaloneLink.CONNECTED) WatchPhase.STANDALONE else WatchPhase.DISCONNECTED
        }
        if (status == null) return WatchPhase.WAITING
        return when (status.phase) {
            "OFF" -> WatchPhase.OFF
            "CONNECTING" -> WatchPhase.CONNECTING
            "MONITORING" -> WatchPhase.MONITORING
            "RECONNECTING" -> WatchPhase.RECONNECTING
            "DEGRADED" -> WatchPhase.DEGRADED
            "LIMITED" -> WatchPhase.LIMITED
            // The resume alarm may lag a little; the phone reconnects on its own.
            "PAUSED" -> if (status.pauseUntil > now) WatchPhase.PAUSED else WatchPhase.CONNECTING
            else -> WatchPhase.WAITING
        }
    }
}
