package com.github.mytv.myearthquakealert.ui.about

import com.github.mytv.myearthquakealert.BuildConfig
import com.github.mytv.myearthquakealert.update.CheckState
import com.github.mytv.myearthquakealert.update.DownloadPhase
import com.github.mytv.myearthquakealert.update.DownloadSnapshot
import com.github.mytv.myearthquakealert.update.PendingDownload
import com.github.mytv.myearthquakealert.update.ReadyUpdate
import com.github.mytv.myearthquakealert.update.SessionInstaller
import com.github.mytv.myearthquakealert.update.UpdateController
import com.github.mytv.myearthquakealert.update.UpdateFailure
import com.github.mytv.myearthquakealert.update.WaitReason
import com.github.mytv.myearthquakealert.ui.about.AboutViewModel.UpdateState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.File

/** Everything the update card is derived from. */
internal data class UpdateInputs(
    val check: CheckState,
    val download: DownloadSnapshot?,
    val install: SessionInstaller.Status,
    val ready: ReadyUpdate?,
    val pending: PendingDownload?,
    val dismissedWork: String?,
    val deferred: Boolean,
)

/**
 * The one 关于与更新 state shown by the page and the TV progress overlay. The step furthest
 * along wins: installing › downloading / verifying › verified package › check result.
 * Pure (the part length comes in as a function), unit tested.
 */
internal fun resolveUpdateState(
    inputs: UpdateInputs,
    installedCode: Long,
    partLength: (Long) -> Long,
    fileExists: (String) -> Boolean,
): UpdateState {
    val ready = inputs.ready?.takeIf { it.info.versionCode > installedCode && fileExists(it.path) }

    when (val install = inputs.install) {
        is SessionInstaller.Status.Installing -> return UpdateState.Installing(install.info, install.confirm != null)
        is SessionInstaller.Status.NeedsPermission ->
            if (ready != null) return UpdateState.NeedsInstallPermission(File(ready.path), install.info)
        is SessionInstaller.Status.Failed ->
            return UpdateState.Error(UpdateState.Stage.INSTALL, install.failure, install.detail, install.info ?: ready?.info)
        SessionInstaller.Status.Idle, SessionInstaller.Status.Succeeded -> Unit
    }

    val download = inputs.download
    val pending = inputs.pending?.info?.takeIf { download == null || it.versionCode == download.versionCode }
    if (download != null && pending != null) {
        when (download.phase) {
            DownloadPhase.WAITING -> return UpdateState.Waiting(
                pending,
                download.waitReason ?: WaitReason.STARTING,
                partLength(pending.versionCode),
                pending.size,
            )
            DownloadPhase.RUNNING -> return UpdateState.Downloading(
                pending,
                download.downloaded,
                download.total.takeIf { it > 0 } ?: pending.size,
                download.speed,
            )
            DownloadPhase.VERIFYING -> return UpdateState.Verifying(pending)
            DownloadPhase.FAILED -> if (download.workId != inputs.dismissedWork) {
                return UpdateState.Error(
                    if (download.verifyFailure) UpdateState.Stage.VERIFY else UpdateState.Stage.DOWNLOAD,
                    download.failure ?: UpdateFailure.NETWORK,
                    download.detail,
                    pending,
                    download.workId,
                )
            }
            DownloadPhase.CANCELLED -> return UpdateState.Paused(pending, partLength(pending.versionCode), pending.size)
            DownloadPhase.SUCCEEDED -> Unit
        }
    }

    val available = (inputs.check as? CheckState.Available)?.info?.takeIf { it.versionCode > installedCode }
    if (ready != null && (available == null || available.versionCode <= ready.info.versionCode)) {
        return UpdateState.ReadyToInstall(File(ready.path), ready.info, inputs.deferred)
    }

    return when (val check = inputs.check) {
        CheckState.Idle -> UpdateState.Idle
        is CheckState.Checking -> UpdateState.Checking
        CheckState.UpToDate -> UpdateState.UpToDate
        is CheckState.Available -> if (available != null) UpdateState.Available(available) else UpdateState.UpToDate
        is CheckState.Failed -> UpdateState.Error(UpdateState.Stage.CHECK, check.failure, check.detail, null)
    }
}

/** Shares one derived state per process between the page and the TV overlay. */
internal object UpdateUi {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    @Volatile private var flow: StateFlow<UpdateState>? = null

    fun states(controller: UpdateController): StateFlow<UpdateState> =
        flow ?: synchronized(this) { flow ?: create(controller).also { flow = it } }

    private data class Stored(val ready: ReadyUpdate?, val pending: PendingDownload?, val dismissed: String?, val deferred: Boolean)

    private fun create(controller: UpdateController): StateFlow<UpdateState> {
        val store = controller.store
        val installed = BuildConfig.VERSION_CODE.toLong()
        val partLength: (Long) -> Long = controller.files::partLength
        val exists: (String) -> Boolean = { File(it).isFile }
        val initial = UpdateInputs(
            controller.checkState.value, null, controller.installStatus.value, store.ready.value,
            store.pending.value, store.dismissedWork.value, controller.installDeferred.value,
        )
        val stored = combine(store.ready, store.pending, store.dismissedWork, controller.installDeferred) { ready, pending, dismissed, deferred ->
            Stored(ready, pending, dismissed, deferred)
        }
        return combine(controller.checkState, controller.download, controller.installStatus, stored) { check, download, install, s ->
            UpdateInputs(check, download, install, s.ready, s.pending, s.dismissed, s.deferred)
        }
            .distinctUntilChanged()
            .map { resolveUpdateState(it, installed, partLength, exists) }
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), resolveUpdateState(initial, installed, partLength, exists))
    }
}

/**
 * Release versions share versionName (1.0) and differ by versionCode (the CI run number),
 * so every user-facing version reads "1.0.123", matching the Release tag v1.0.123.
 */
fun displayVersion(versionName: String, versionCode: Long): String = "$versionName.$versionCode"
