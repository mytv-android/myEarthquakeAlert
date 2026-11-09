package com.github.mytv.myearthquakealert.update

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.github.mytv.myearthquakealert.BuildConfig
import com.github.mytv.myearthquakealert.MyEarthQuakeAlertApp
import com.github.mytv.myearthquakealert.data.model.UpdateChannel
import com.github.mytv.myearthquakealert.data.model.UpdateInfo
import com.github.mytv.myearthquakealert.data.repository.UserSettings
import com.github.mytv.myearthquakealert.service.ActiveAlertHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/** The check part of the pipeline. */
sealed interface CheckState {
    data object Idle : CheckState
    data class Checking(val manual: Boolean) : CheckState
    data object UpToDate : CheckState
    data class Available(val info: UpdateInfo) : CheckState
    data class Failed(val failure: UpdateFailure, val detail: String?) : CheckState
}

enum class DownloadPhase { WAITING, RUNNING, VERIFYING, SUCCEEDED, FAILED, CANCELLED }

/** Why an enqueued download is not running yet. */
enum class WaitReason { STARTING, NETWORK, UNMETERED, RETRY }

/** The latest run of the unique download work, independent of WorkManager types. */
data class DownloadSnapshot(
    val workId: String,
    val phase: DownloadPhase,
    val versionCode: Long,
    val downloaded: Long = 0,
    val total: Long = 0,
    val speed: Long = 0,
    val waitReason: WaitReason? = null,
    val failure: UpdateFailure? = null,
    val detail: String? = null,
    /** The failure happened while verifying (the package was deleted). */
    val verifyFailure: Boolean = false,
)

/**
 * Process-wide owner of 应用内更新 (design §8): checks (manual, at start, daily), the
 * WorkManager download, the verified package and the session install. The 关于与更新 page
 * and the TV progress overlay observe it; the workers and MainActivity drive it.
 */
class UpdateController private constructor(context: Context) {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val checkMutex = Mutex()
    private val installedCode = BuildConfig.VERSION_CODE.toLong()

    val store = UpdateStore(app)
    val files = UpdateFiles(app)

    private val repository get() = (app as MyEarthQuakeAlertApp).updateRepository
    private val workManager get() = WorkManager.getInstance(app)

    private val _check = MutableStateFlow(initialCheck())
    val checkState: StateFlow<CheckState> = _check.asStateFlow()

    private val _installDeferred = MutableStateFlow(false)
    /** 稍后 on the ready card: collapsed for the rest of this process. */
    val installDeferred: StateFlow<Boolean> = _installDeferred.asStateFlow()

    /** The latest run of the download work (survives leaving the page and process death). */
    val download: Flow<DownloadSnapshot?> = workManager.getWorkInfosForUniqueWorkFlow(UpdateDownloadWorker.NAME)
        .map { infos -> infos.lastOrNull()?.let(::snapshotOf) }
        .distinctUntilChanged()

    val installStatus: StateFlow<SessionInstaller.Status> = SessionInstaller.status

    init {
        scope.launch(Dispatchers.IO) { tidyFiles() }
        scope.launch {
            // The first value is the stored channel; only later changes trigger a check.
            (app as MyEarthQuakeAlertApp).settingsRepository.settings
                .map { it.updateChannel }
                .distinctUntilChanged()
                .drop(1)
                .collect { channel -> onChannelChanged(channel) }
        }
    }

    // ---- 检查 ----

    /** 检查更新 (manual): shows Checking, and failures as an error card. */
    fun check() {
        scope.launch { runCheck(manual = true, channel = null) }
    }

    /** At app start: when 自动检查更新 is on, at most once per [START_INTERVAL_MS]. */
    fun checkOnStart() {
        scope.launch {
            val s = settings()
            if (!s.updateAutoCheck || alertActive) return@launch
            if (System.currentTimeMillis() - store.lastCheckedAt.value < START_INTERVAL_MS) return@launch
            runCheck(manual = false, channel = s.updateChannel)
        }
    }

    /** While 关于与更新 is open: auto check when the last one is a day old. */
    fun checkIfDue(enabled: Boolean, now: Long) {
        if (!enabled || alertActive || _check.value is CheckState.Checking) return
        if (now - store.lastCheckedAt.value < DAY_MS) return
        scope.launch { runCheck(manual = false, channel = null) }
    }

    /** The daily [UpdateCheckWorker]. Returns false when the check failed and should be retried. */
    suspend fun backgroundCheck(): Boolean {
        val s = settings()
        if (!s.updateAutoCheck) return true
        if (System.currentTimeMillis() - store.lastCheckedAt.value < WORKER_INTERVAL_MS) {
            // Checked recently (e.g. at start); announce what it found if that was held back.
            store.known.value?.let { notifyAvailable(it) }
            return true
        }
        return runCheck(manual = false, channel = s.updateChannel).isSuccess
    }

    private suspend fun runCheck(manual: Boolean, channel: UpdateChannel?): Result<UpdateInfo?> = checkMutex.withLock {
        val previous = _check.value
        _check.value = CheckState.Checking(manual)
        val result = repository.checkForUpdate(channel ?: settings().updateChannel)
        result.fold(
            onSuccess = { info ->
                store.recordCheck(System.currentTimeMillis(), info)
                _check.value = if (info != null) CheckState.Available(info) else CheckState.UpToDate
                if (info != null) {
                    if (manual) store.notifiedVersion = maxOf(store.notifiedVersion, info.versionCode) else notifyAvailable(info)
                }
            },
            onFailure = { error ->
                Log.w(TAG, "update check failed", error)
                _check.value = if (manual) {
                    val e = error as? UpdateException
                    CheckState.Failed(e?.failure ?: UpdateFailure.NETWORK, e?.detail)
                } else {
                    // Automatic checks fail quietly.
                    previous.takeUnless { it is CheckState.Checking || it is CheckState.Failed } ?: CheckState.Idle
                }
            },
        )
        result
    }

    private fun notifyAvailable(info: UpdateInfo) {
        if (info.versionCode <= store.notifiedVersion) return
        if (UpdateNotifications.showAvailable(app, info)) store.notifiedVersion = info.versionCode
    }

    private suspend fun onChannelChanged(channel: UpdateChannel) {
        if (channel == UpdateChannel.STABLE) {
            if (store.known.value?.prerelease == true) store.clearKnown()
            if ((_check.value as? CheckState.Available)?.info?.prerelease == true) _check.value = CheckState.Idle
        }
        runCheck(manual = true, channel = channel)
    }

    /** 稍后 / dismissing a check error. */
    fun dismissCheck() {
        _check.value = CheckState.Idle
    }

    // ---- 下载 ----

    /** Starts (or resumes) downloading [info]; [allowMetered] is 仍然下载 on mobile data. */
    fun download(info: UpdateInfo, allowMetered: Boolean) {
        scope.launch {
            val unmetered = settings().updateWifiOnly && !allowMetered
            store.setPending(PendingDownload(info, allowMetered))
            store.ready.value?.takeIf { it.info.versionCode != info.versionCode }?.let {
                File(it.path).delete()
                store.setReady(null)
            }
            store.setDismissedWork(null)
            if (SessionInstaller.status.value is SessionInstaller.Status.Failed) SessionInstaller.reset()
            _installDeferred.value = false
            UpdateNotifications.cancel(app, UpdateNotifications.ID_FAILED)
            UpdateDownloadWorker.enqueue(app, info, unmetered)
        }
    }

    /** 暂停: stops the work and keeps the partial file for 继续. */
    fun pause() {
        workManager.cancelUniqueWork(UpdateDownloadWorker.NAME)
    }

    /** 继续 after a pause or a failed download, with the same mobile-data choice. */
    fun resume() {
        val pending = store.pending.value ?: return
        download(pending.info, pending.allowMetered)
    }

    /** 取消下载: stops the work and deletes what was downloaded. */
    fun cancelDownload() {
        scope.launch {
            val pending = store.pending.value
            workManager.cancelUniqueWork(UpdateDownloadWorker.NAME)
            // Let the worker stop writing before its files go.
            withTimeoutOrNull(5_000) { download.first { it == null || it.phase !in RUNNING_PHASES } }
            store.setPending(null)
            pending?.let { withContext(Dispatchers.IO) { files.deleteDownload(it.info.versionCode) } }
            UpdateNotifications.cancel(app, UpdateNotifications.ID_PROGRESS)
            if (pending != null && pending.info.versionCode > installedCode) _check.value = CheckState.Available(pending.info)
        }
    }

    /** Hides a failed download's error card. */
    fun dismissDownloadFailure(workId: String) {
        store.setDismissedWork(workId)
        store.pending.value?.let { if (it.info.versionCode == store.known.value?.versionCode) _check.value = CheckState.Available(it.info) }
        store.setPending(null)
        UpdateNotifications.cancel(app, UpdateNotifications.ID_FAILED)
    }

    // ---- 安装 ----

    /** 立即安装: re-checks the stored hash, then commits a PackageInstaller session. */
    fun install() {
        if (alertActive) return
        scope.launch {
            val ready = store.ready.value ?: return@launch
            val file = File(ready.path)
            val ok = withContext(Dispatchers.IO) { file.isFile && Sha256.of(file).equals(ready.sha256, ignoreCase = true) }
            if (!ok) {
                file.delete()
                store.setReady(null)
                SessionInstaller.fail(ready.info, if (file.exists()) UpdateFailure.SHA256 else UpdateFailure.FILE_MISSING)
                return@launch
            }
            UpdateNotifications.cancel(app, UpdateNotifications.ID_READY)
            SessionInstaller.install(app, file, ready.info)
        }
    }

    /** Back on the page after the 安装未知应用 settings: continue once granted. */
    fun onResumed() {
        if (SessionInstaller.status.value is SessionInstaller.Status.NeedsPermission && SessionInstaller.canRequestInstalls(app)) install()
    }

    fun deferInstall() {
        _installDeferred.value = true
    }

    fun dismissInstallFailure() {
        SessionInstaller.reset()
    }

    /** 继续安装: the system confirmation screen again. */
    fun continueInstall(context: Context) {
        if (!SessionInstaller.launchConfirm(context)) install()
    }

    /** The page shows a state: its notification is redundant now. */
    fun onShown(vararg ids: Int) {
        UpdateNotifications.cancel(app, *ids)
    }

    // ---- 内部 ----

    private val alertActive: Boolean get() = ActiveAlertHolder.activeAlert.value != null

    private suspend fun settings(): UserSettings = (app as MyEarthQuakeAlertApp).settingsRepository.settings.first()

    private fun initialCheck(): CheckState {
        val known = store.known.value ?: return CheckState.Idle
        val fresh = System.currentTimeMillis() - store.lastCheckedAt.value < DAY_MS
        return if (fresh) CheckState.Available(known) else CheckState.Idle
    }

    /** Drops packages that are installed or superseded, and the 1.x cache directory. */
    private fun tidyFiles() {
        File(app.cacheDir, "updates").takeIf { it.exists() }?.deleteRecursively()
        val pending = store.pending.value
        val ready = store.ready.value
        if (ready != null && (!File(ready.path).isFile || (pending != null && pending.info.versionCode != ready.info.versionCode))) {
            if (pending != null) File(ready.path).delete()
            store.setReady(null)
        }
        files.cleanup(installedCode, keep = pending?.info?.versionCode ?: store.ready.value?.info?.versionCode)
    }

    private fun snapshotOf(info: WorkInfo): DownloadSnapshot {
        val id = info.id.toString()
        val code = info.tags.firstNotNullOfOrNull { tag -> tag.removePrefix(UpdateDownloadWorker.TAG_VERSION).takeIf { tag.startsWith(UpdateDownloadWorker.TAG_VERSION) }?.toLongOrNull() } ?: 0L
        return when (info.state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> DownloadSnapshot(
                id, DownloadPhase.WAITING, code,
                waitReason = waitReason(info.runAttemptCount, UpdateDownloadWorker.TAG_UNMETERED in info.tags),
            )
            WorkInfo.State.RUNNING -> {
                val p = info.progress
                val verifying = p.getBoolean(UpdateDownloadWorker.KEY_VERIFYING, false)
                DownloadSnapshot(
                    id, if (verifying) DownloadPhase.VERIFYING else DownloadPhase.RUNNING, code,
                    downloaded = p.getLong(UpdateDownloadWorker.KEY_DOWNLOADED, 0L),
                    total = p.getLong(UpdateDownloadWorker.KEY_TOTAL, 0L),
                    speed = p.getLong(UpdateDownloadWorker.KEY_SPEED, 0L),
                )
            }
            WorkInfo.State.SUCCEEDED -> DownloadSnapshot(id, DownloadPhase.SUCCEEDED, code)
            WorkInfo.State.FAILED -> {
                val out = info.outputData
                DownloadSnapshot(
                    id, DownloadPhase.FAILED, code,
                    failure = UpdateFailure.fromName(out.getString(UpdateDownloadWorker.KEY_FAILURE)) ?: UpdateFailure.NETWORK,
                    detail = out.getString(UpdateDownloadWorker.KEY_DETAIL),
                    verifyFailure = out.getBoolean(UpdateDownloadWorker.KEY_VERIFYING, false),
                )
            }
            WorkInfo.State.CANCELLED -> DownloadSnapshot(id, DownloadPhase.CANCELLED, code)
        }
    }

    private fun waitReason(attempts: Int, unmetered: Boolean): WaitReason {
        if (attempts > 0) return WaitReason.RETRY
        val cm = app.getSystemService(ConnectivityManager::class.java) ?: return WaitReason.STARTING
        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        val online = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        return when {
            !online -> WaitReason.NETWORK
            unmetered && cm.isActiveNetworkMetered -> WaitReason.UNMETERED
            else -> WaitReason.STARTING
        }
    }

    companion object {
        private const val TAG = "UpdateController"
        private const val DAY_MS = 24 * 60 * 60 * 1000L
        /** App-start checks are skipped when the last check is younger than this. */
        private const val START_INTERVAL_MS = 60 * 60 * 1000L
        /** The daily worker skips its check when the app checked within this window. */
        private const val WORKER_INTERVAL_MS = 12 * 60 * 60 * 1000L
        private val RUNNING_PHASES = setOf(DownloadPhase.WAITING, DownloadPhase.RUNNING, DownloadPhase.VERIFYING)

        @Volatile private var instance: UpdateController? = null

        fun get(context: Context): UpdateController =
            instance ?: synchronized(this) { instance ?: UpdateController(context).also { instance = it } }
    }
}
