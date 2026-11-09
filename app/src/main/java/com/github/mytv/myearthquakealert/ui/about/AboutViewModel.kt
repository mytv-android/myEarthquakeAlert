package com.github.mytv.myearthquakealert.ui.about

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.github.mytv.myearthquakealert.data.model.UpdateInfo
import com.github.mytv.myearthquakealert.update.UpdateController
import com.github.mytv.myearthquakealert.update.UpdateFailure
import com.github.mytv.myearthquakealert.update.UpdateNotifications
import com.github.mytv.myearthquakealert.update.WaitReason
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.File

/**
 * 应用内更新 (design §8) for 设置 › 关于与更新: a thin view of [UpdateController], whose
 * state outlives the page — the download runs as unique WorkManager work, so its progress
 * survives leaving the page and process death.
 */
class AboutViewModel(private val controller: UpdateController) : ViewModel() {

    val updateState: StateFlow<UpdateState> = UpdateUi.states(controller)

    /** Epoch millis of the last successful check; 0 when never checked. */
    val lastCheckedAt: StateFlow<Long> = controller.store.lastCheckedAt

    /** Newest release seen by a check that is newer than this build, or null. */
    val knownUpdate: StateFlow<KnownUpdate?> = controller.store.known
        .map { it?.let { info -> KnownUpdate(info.versionName, info.versionCode) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, controller.store.known.value?.let { KnownUpdate(it.versionName, it.versionCode) })

    data class KnownUpdate(val versionName: String, val versionCode: Long)

    sealed class UpdateState {
        data object Idle : UpdateState()
        data object Checking : UpdateState()
        data object UpToDate : UpdateState()
        data class Available(val updateInfo: UpdateInfo) : UpdateState()
        /** Enqueued: waiting for (Wi-Fi) network, or for an automatic retry. */
        data class Waiting(val info: UpdateInfo, val reason: WaitReason, val downloaded: Long, val total: Long) : UpdateState()
        data class Downloading(val info: UpdateInfo, val downloaded: Long, val total: Long, val speed: Long) : UpdateState() {
            val progress: Int get() = if (total > 0) (downloaded * 100 / total).toInt().coerceIn(0, 100) else 0
        }
        data class Paused(val info: UpdateInfo, val downloaded: Long, val total: Long) : UpdateState() {
            val progress: Int get() = if (total > 0) (downloaded * 100 / total).toInt().coerceIn(0, 100) else 0
        }
        /** The package is complete; SHA-256 and signing certificate are being checked. */
        data class Verifying(val updateInfo: UpdateInfo) : UpdateState()
        /** 待安装: verified; [deferred] after 稍后. */
        data class ReadyToInstall(val apkFile: File, val info: UpdateInfo, val deferred: Boolean) : UpdateState()
        /** The system needs 安装未知应用 for this app before the verified package can be installed. */
        data class NeedsInstallPermission(val apkFile: File, val info: UpdateInfo) : UpdateState()
        /** Session committed; [canContinue] once the system asked for confirmation. */
        data class Installing(val info: UpdateInfo, val canContinue: Boolean) : UpdateState()
        data class Error(
            val stage: Stage,
            val failure: UpdateFailure,
            val detail: String?,
            val retryInfo: UpdateInfo?,
            val workId: String? = null,
        ) : UpdateState()

        enum class Stage { CHECK, DOWNLOAD, VERIFY, INSTALL }
    }

    fun checkForUpdate() = controller.check()

    /** 自动检查更新 while the page is open: at most once a day, never interrupting a flow. */
    fun autoCheckIfDue(enabled: Boolean, now: Long = System.currentTimeMillis()) {
        if (updateState.value !is UpdateState.Idle && updateState.value !is UpdateState.UpToDate) return
        controller.checkIfDue(enabled, now)
    }

    /** [allowMetered]: 仍然下载 confirmed on mobile data. */
    fun downloadUpdate(info: UpdateInfo, allowMetered: Boolean = false) = controller.download(info, allowMetered)

    fun pauseDownload() = controller.pause()

    fun resumeDownload() = controller.resume()

    fun cancelDownload() = controller.cancelDownload()

    fun installUpdate() = controller.install()

    fun continueInstall(context: Context) = controller.continueInstall(context)

    fun deferInstall() = controller.deferInstall()

    fun onResumed() = controller.onResumed()

    /** 稍后 / 取消 on a card: back to the quiet state of that step. */
    fun dismiss(state: UpdateState) {
        when (state) {
            is UpdateState.Error -> when (state.stage) {
                UpdateState.Stage.CHECK -> controller.dismissCheck()
                UpdateState.Stage.INSTALL -> controller.dismissInstallFailure()
                else -> state.workId?.let(controller::dismissDownloadFailure)
            }
            is UpdateState.NeedsInstallPermission -> controller.dismissInstallFailure()
            else -> controller.dismissCheck()
        }
    }

    /** 重新安装 after an install failure that left the verified package in place. */
    fun retryInstall() {
        controller.dismissInstallFailure()
        controller.install()
    }

    /** The page shows [state]: its notification is redundant. */
    fun onShown(state: UpdateState) {
        when (state) {
            is UpdateState.Available -> controller.onShown(UpdateNotifications.ID_AVAILABLE)
            is UpdateState.ReadyToInstall -> controller.onShown(UpdateNotifications.ID_READY)
            is UpdateState.Error -> controller.onShown(UpdateNotifications.ID_FAILED)
            is UpdateState.Installing -> controller.onShown(UpdateNotifications.ID_CONFIRM)
            else -> Unit
        }
    }

    companion object {
        /** Factory used by the settings pages. */
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return AboutViewModel(UpdateController.get(context.applicationContext)) as T
            }
        }
    }
}
