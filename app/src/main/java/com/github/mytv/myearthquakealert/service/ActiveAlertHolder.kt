package com.github.mytv.myearthquakealert.service

import com.github.mytv.myearthquakealert.data.model.EewEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AlertData(
    val event: EewEvent,
    val userLatitude: Double,
    val userLongitude: Double,
    val pWaveSeconds: Double,
    val sWaveSeconds: Double,
    val localCsis: Double,
    val isSimulation: Boolean = false,
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
