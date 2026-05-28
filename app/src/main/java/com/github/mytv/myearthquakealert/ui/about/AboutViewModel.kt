package com.github.mytv.myearthquakealert.ui.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.mytv.myearthquakealert.data.model.UpdateInfo
import com.github.mytv.myearthquakealert.data.repository.UpdateRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class AboutViewModel(
    private val updateRepository: UpdateRepository,
) : ViewModel() {
    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private var downloadJob: Job? = null

    sealed class UpdateState {
        object Idle : UpdateState()
        object Checking : UpdateState()
        data class Available(val updateInfo: UpdateInfo) : UpdateState()
        object UpToDate : UpdateState()
        data class Downloading(
            val progress: Int,
            val downloaded: Long,
            val total: Long,
            val speed: Long
        ) : UpdateState()
        data class Downloaded(val apkFile: File) : UpdateState()
        data class Error(val message: String) : UpdateState()
    }

    fun checkForUpdate() {
        viewModelScope.launch {
            _updateState.value = UpdateState.Checking
            val result = updateRepository.checkForUpdate()
            _updateState.value = result.fold(
                onSuccess = { updateInfo ->
                    if (updateInfo != null) {
                        UpdateState.Available(updateInfo)
                    } else {
                        UpdateState.UpToDate
                    }
                },
                onFailure = { e ->
                    UpdateState.Error(e.message ?: "Unknown error")
                }
            )
        }
    }

    fun downloadUpdate(updateInfo: UpdateInfo) {
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            var lastUpdateTime = System.currentTimeMillis()
            var lastBytesRead = 0L

            val result = updateRepository.downloadApk(updateInfo.apkUrl) { progress, downloaded, total ->
                val currentTime = System.currentTimeMillis()
                val timeDiff = currentTime - lastUpdateTime
                val bytesDiff = downloaded - lastBytesRead

                val speed = if (timeDiff > 0) {
                    (bytesDiff * 1000 / timeDiff)
                } else 0L

                if (timeDiff >= 500) {
                    lastUpdateTime = currentTime
                    lastBytesRead = downloaded
                }

                _updateState.value = UpdateState.Downloading(progress, downloaded, total, speed)
            }

            _updateState.value = result.fold(
                onSuccess = { file -> UpdateState.Downloaded(file) },
                onFailure = { e -> UpdateState.Error(e.message ?: "Download failed") }
            )
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        _updateState.value = UpdateState.Idle
    }

    fun resetState() {
        _updateState.value = UpdateState.Idle
    }
}
