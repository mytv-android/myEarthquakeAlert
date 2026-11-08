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
)

object ActiveAlertHolder {
    private val _activeAlert = MutableStateFlow<AlertData?>(null)
    val activeAlert: StateFlow<AlertData?> = _activeAlert.asStateFlow()

    fun showAlert(data: AlertData) {
        _activeAlert.value = data
    }

    fun dismissAlert() {
        _activeAlert.value = null
    }
}
