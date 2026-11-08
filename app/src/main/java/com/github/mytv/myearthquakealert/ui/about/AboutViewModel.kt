package com.github.mytv.myearthquakealert.ui.about

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.github.mytv.myearthquakealert.BuildConfig
import com.github.mytv.myearthquakealert.MyEarthQuakeAlertApp
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

/**
 * 应用内更新 (design §8): check → download (wavy progress) → verify (SHA-256 + package) →
 * install. [prefs] remembers the last check and the newest known release so the settings
 * list can show "有新版本" without checking again, and so the daily auto check knows when
 * it is due.
 */
class AboutViewModel(
    private val updateRepository: UpdateRepository,
    private val prefs: SharedPreferences? = null,
) : ViewModel() {
    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val _lastCheckedAt = MutableStateFlow(prefs?.getLong(KEY_LAST_CHECK, 0L) ?: 0L)
    /** Epoch millis of the last successful check; 0 when never checked. */
    val lastCheckedAt: StateFlow<Long> = _lastCheckedAt.asStateFlow()

    private val _knownUpdate = MutableStateFlow(readKnownUpdate())
    /** Newest release seen by a check that is newer than this build, or null. */
    val knownUpdate: StateFlow<KnownUpdate?> = _knownUpdate.asStateFlow()

    private var downloadJob: Job? = null

    data class KnownUpdate(val versionName: String, val versionCode: Long)

    sealed class UpdateState {
        object Idle : UpdateState()
        object Checking : UpdateState()
        data class Available(val updateInfo: UpdateInfo) : UpdateState()
        object UpToDate : UpdateState()
        data class Downloading(
            val progress: Int,
            val downloaded: Long,
            val total: Long,
            val speed: Long,
            val info: UpdateInfo? = null,
        ) : UpdateState()
        /** The package is complete; SHA-256 and package identity are being checked. */
        data class Verifying(val updateInfo: UpdateInfo) : UpdateState()
        data class Downloaded(val apkFile: File, val info: UpdateInfo? = null) : UpdateState()
        /** The system needs 安装未知应用 permission before the verified package can be installed. */
        data class NeedsInstallPermission(val apkFile: File, val info: UpdateInfo? = null) : UpdateState()
        data class Error(val message: String, val retryInfo: UpdateInfo? = null) : UpdateState()
    }

    fun checkForUpdate() {
        if (_updateState.value is UpdateState.Checking || _updateState.value is UpdateState.Downloading) return
        viewModelScope.launch {
            _updateState.value = UpdateState.Checking
            updateRepository.checkForUpdate().fold(
                onSuccess = { updateInfo ->
                    rememberCheck(updateInfo)
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

    /** 自动检查更新: checks at most once a day, and never interrupts a running flow. */
    fun autoCheckIfDue(enabled: Boolean, now: Long = System.currentTimeMillis()) {
        if (!enabled || _updateState.value != UpdateState.Idle) return
        if (now - _lastCheckedAt.value < AUTO_CHECK_INTERVAL_MS) return
        checkForUpdate()
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
                info = updateInfo,
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
                _updateState.value = if (total in 1..downloaded) {
                    UpdateState.Verifying(updateInfo)
                } else {
                    val progress = if (total > 0) (downloaded * 100 / total).toInt().coerceIn(0, 100) else 0
                    UpdateState.Downloading(progress, downloaded, total, speed, updateInfo)
                }
            }

            _updateState.value = result.fold(
                onSuccess = { file -> UpdateState.Downloaded(file, updateInfo) },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    UpdateState.Error(error.message ?: "Download failed", retryInfo = updateInfo)
                }
            )
        }
    }

    fun installUpdate(apkFile: File, info: UpdateInfo? = null) {
        viewModelScope.launch {
            when (updateRepository.install(apkFile)) {
                InstallResult.Launched -> _updateState.value = UpdateState.Downloaded(apkFile, info)
                InstallResult.NeedsPermission -> _updateState.value = UpdateState.NeedsInstallPermission(apkFile, info)
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

    private fun rememberCheck(info: UpdateInfo?) {
        val now = System.currentTimeMillis()
        _lastCheckedAt.value = now
        val known = info?.let { KnownUpdate(it.versionName, it.versionCode) }
        _knownUpdate.value = known
        prefs?.edit()?.apply {
            putLong(KEY_LAST_CHECK, now)
            if (known != null) {
                putString(KEY_KNOWN_NAME, known.versionName)
                putLong(KEY_KNOWN_CODE, known.versionCode)
            } else {
                remove(KEY_KNOWN_NAME)
                remove(KEY_KNOWN_CODE)
            }
        }?.apply()
    }

    private fun readKnownUpdate(): KnownUpdate? {
        val p = prefs ?: return null
        val code = p.getLong(KEY_KNOWN_CODE, 0L)
        val name = p.getString(KEY_KNOWN_NAME, null) ?: return null
        return if (code > BuildConfig.VERSION_CODE.toLong()) KnownUpdate(name, code) else null
    }

    companion object {
        private const val PREFS = "update_check"
        private const val KEY_LAST_CHECK = "last_check_at"
        private const val KEY_KNOWN_NAME = "known_version_name"
        private const val KEY_KNOWN_CODE = "known_version_code"
        private const val AUTO_CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L

        /** Factory used by the settings pages; remembers checks in a small preferences file. */
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = context.applicationContext as MyEarthQuakeAlertApp
                @Suppress("UNCHECKED_CAST")
                return AboutViewModel(app.updateRepository, app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)) as T
            }
        }
    }
}
