package com.github.mytv.myearthquakealert.service

import com.github.mytv.myearthquakealert.data.model.EewEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * How intrusive an alert is (design §4 预警体验): proportional to the local intensity
 * and the device state. Drills are presented as STRONG with [AlertData.isSimulation].
 */
enum class AlertTier {
    /** Below the reminder thresholds: only written to the 地震 list. */
    RECORD,
    /** Magnitude ≥ min magnitude and local intensity ≥ min intensity: floating card / notification. */
    REMIND,
    /** Local intensity ≥ strong threshold (default 5): full-screen takeover, alarm sound. */
    STRONG,
}

data class AlertData(
    val event: EewEvent,
    val userLatitude: Double,
    val userLongitude: Double,
    val pWaveSeconds: Double,
    val sWaveSeconds: Double,
    val localCsis: Double,
    val distanceKm: Double = 0.0,
    val isSimulation: Boolean = false,
    val tier: AlertTier = AlertTier.REMIND,
    /** Epoch millis when the alert was first presented; countdowns run from here. */
    val presentedAt: Long = System.currentTimeMillis(),
    /**
     * Origin time of the quake on the local clock (epoch millis comparable with
     * System.currentTimeMillis(); the service converts the report's OriginTime with the
     * ClockSync offset, so a wrong device clock does not skew the countdown).
     * [pWaveSeconds] and [sWaveSeconds] are travel times counted from here. Defaults to
     * [presentedAt] (1.0 behaviour); the service sets it from the report, so the countdown
     * also accounts for the seconds between the origin and the report's arrival.
     */
    val originAt: Long = presentedAt,
) {
    /** Epoch millis when the S wave is expected at the user. */
    val sArrivalAt: Long get() = originAt + (sWaveSeconds * 1000).toLong()

    /** Epoch millis when the P wave is expected at the user. */
    val pArrivalAt: Long get() = originAt + (pWaveSeconds * 1000).toLong()

    /** Seconds since the origin time at local time [now]. */
    fun elapsedSeconds(now: Long = System.currentTimeMillis()): Double = (now - originAt) / 1000.0

    /** Seconds until the S wave arrives, 0 once it has. */
    fun sRemainingSeconds(now: Long = System.currentTimeMillis()): Double = ((sArrivalAt - now) / 1000.0).coerceAtLeast(0.0)

    /** Seconds until the P wave arrives, 0 once it has. */
    fun pRemainingSeconds(now: Long = System.currentTimeMillis()): Double = ((pArrivalAt - now) / 1000.0).coerceAtLeast(0.0)
}

object ActiveAlertHolder {
    private val _activeAlert = MutableStateFlow<AlertData?>(null)
    val activeAlert: StateFlow<AlertData?> = _activeAlert.asStateFlow()

    fun showAlert(data: AlertData) {
        _activeAlert.value = data
    }

    private val _muted = MutableStateFlow(false)
    /** "静音" on the alert: the service stops the alarm sound, vibration and speech. */
    val muted: StateFlow<Boolean> = _muted.asStateFlow()

    fun mute() {
        _muted.value = true
    }

    /** Clears 静音 when a new alert starts (service only; the UI uses [dismissAlert]). */
    internal fun resetMute() {
        _muted.value = false
    }

    fun dismissAlert() {
        _activeAlert.value = null
        _muted.value = false
    }
}
