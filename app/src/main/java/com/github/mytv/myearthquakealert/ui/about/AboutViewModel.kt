package com.github.mytv.myearthquakealert.ui.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.mytv.myearthquakealert.data.model.UpdateInfo
import com.github.mytv.myearthquakealert.data.repository.UpdateRepository
import com.github.mytv.myearthquakealert.util.InstallResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
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
            updateRepository.checkForUpdate().fold(
                onSuccess = { updateInfo ->
                    _updateState.value = if (updateInfo != null) {
                        UpdateState.Available(updateInfo)
                    } else {
                        UpdateState.UpToDate
                    }
                },
                onFailure = { error ->
                    _updateState.value = UpdateState.Error(error.message ?: "Unknown error")
                }
            )
        }
    }

    fun downloadUpdate(updateInfo: UpdateInfo) {
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            var lastUpdateTime = System.currentTimeMillis()
            var lastBytesRead = 0L
            var speed = 0L

            _updateState.value = UpdateState.Downloading(
                progress = 0,
                downloaded = 0,
                total = updateInfo.size,
                speed = 0,
            )

            val result = updateRepository.downloadApk(updateInfo) { downloaded, total ->
                if (!isActive) return@downloadApk
                val currentTime = System.currentTimeMillis()
                val timeDiff = currentTime - lastUpdateTime
                if (timeDiff >= 500) {
                    speed = (downloaded - lastBytesRead) * 1000 / timeDiff
                    lastUpdateTime = currentTime
                    lastBytesRead = downloaded
                }
                val progress = if (total > 0) {
                    (downloaded * 100 / total).toInt().coerceIn(0, 100)
                } else {
                    0
                }
                _updateState.value = UpdateState.Downloading(progress, downloaded, total, speed)
            }

            _updateState.value = result.fold(
                onSuccess = { file -> UpdateState.Downloaded(file) },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    UpdateState.Error(error.message ?: "Download failed")
                }
            )
        }
    }

    fun installUpdate(apkFile: File) {
        viewModelScope.launch {
            when (updateRepository.install(apkFile)) {
                InstallResult.Launched -> _updateState.value = UpdateState.Idle
                InstallResult.NeedsPermission -> _updateState.value =
                    UpdateState.Error("Please allow this app to install unknown apps, then try again")
            }
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
