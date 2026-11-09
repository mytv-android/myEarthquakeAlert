package com.github.mytv.myearthquakealert.update

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import android.os.storage.StorageManager
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.github.mytv.myearthquakealert.BuildConfig
import com.github.mytv.myearthquakealert.MyEarthQuakeAlertApp
import com.github.mytv.myearthquakealert.data.model.UpdateInfo
import com.github.mytv.myearthquakealert.data.repository.UpdateRepository
import com.github.mytv.myearthquakealert.service.AppNotifications
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 下载 (design §8): the APK download as a WorkManager foreground task (dataSync) with a
 * progress notification. Unique work, so leaving the page or a process death never starts
 * a second download; the page observes it by name. HTTP Range resume, mirror fallback,
 * then 校验 (SHA-256 + signing certificate). Monitoring keeps running meanwhile — this is a
 * separate foreground service of the same process.
 */
class UpdateDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val json = Json { ignoreUnknownKeys = true }

    /** setForeground succeeded: WorkManager owns (and removes) the progress notification. */
    private var inForeground = false

    override suspend fun doWork(): Result = try {
        download()
    } finally {
        // Without a foreground service the progress notification is ours to remove.
        if (!inForeground) AppNotifications.cancel(applicationContext, UpdateNotifications.ID_PROGRESS)
    }

    private suspend fun download(): Result {
        val info = inputData.getString(KEY_INFO)?.let { runCatching { json.decodeFromString<UpdateInfo>(it) }.getOrNull() }
            ?: return failure(UpdateFailure.MANIFEST)
        if (info.versionCode <= BuildConfig.VERSION_CODE.toLong()) return failure(UpdateFailure.NOT_NEWER)
        val controller = UpdateController.get(applicationContext)
        val files = controller.files
        val repository = (applicationContext as MyEarthQuakeAlertApp).updateRepository
        val pause = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        val reporter = ProgressReporter(info, pause)

        enterForeground(reporter.notification(files.partLength(info.versionCode), info.size, verifying = false))

        val part = files.part(info.versionCode)
        val needed = info.size - files.partLength(info.versionCode) + SPACE_MARGIN
        if (info.size > 0 && !withContext(Dispatchers.IO) { ensureSpace(files.dir, needed) }) {
            return failure(UpdateFailure.STORAGE, formatFileSize(info.size + SPACE_MARGIN))
        }

        try {
            val urls = repository.downloadUrls(info.url)
            ApkDownloader(repository.http, UpdateRepository.USER_AGENT).download(
                urls = urls,
                part = part,
                meta = files.meta(info.versionCode),
                target = ApkDownloader.Target(info.versionCode, info.sha256, info.size),
            ) { downloaded, total -> reporter.report(downloaded, total) }
            reporter.flush()
        } catch (e: CancellationException) {
            throw e
        } catch (e: UpdateException) {
            Log.w(TAG, "download failed", e)
            return if (e.failure.retryable && runAttemptCount < MAX_ATTEMPTS - 1) Result.retry() else failure(e.failure, e.detail, info = info)
        } catch (e: IOException) {
            Log.w(TAG, "download failed", e)
            return if (runAttemptCount < MAX_ATTEMPTS - 1) Result.retry() else failure(UpdateFailure.NETWORK, info = info)
        }

        // 校验
        setProgress(workDataOf(KEY_VERIFYING to true, KEY_DOWNLOADED to info.size, KEY_TOTAL to info.size))
        reporter.show(reporter.notification(info.size, info.size, verifying = true))
        val apk = files.apk(info.versionCode)
        return try {
            val releaseSha = repository.fetchReleaseSha256(info)
            // Short and atomic: a stop request waits until the package is checked and recorded.
            withContext(Dispatchers.IO + NonCancellable) {
                apk.delete()
                if (!part.renameTo(apk)) throw UpdateException(UpdateFailure.FILE_MISSING)
                ApkVerifier(applicationContext).verify(apk, info, releaseSha)
                files.meta(info.versionCode).delete()
                controller.store.setReady(ReadyUpdate(info, apk.absolutePath, info.sha256))
                controller.store.setPending(null)
            }
            UpdateNotifications.showReady(applicationContext, info)
            Result.success(workDataOf(KEY_APK to apk.absolutePath))
        } catch (e: CancellationException) {
            // Stopped before the package was recorded: the complete part is checked again next run.
            if (controller.store.ready.value?.info?.versionCode != info.versionCode && apk.exists() && !part.exists()) apk.renameTo(part)
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "verification failed", e)
            files.deleteAll(info.versionCode)
            val failure = (e as? UpdateException)?.failure ?: UpdateFailure.UNREADABLE
            failure(failure, (e as? UpdateException)?.detail, verifying = true, info = info)
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val info = inputData.getString(KEY_INFO)?.let { runCatching { json.decodeFromString<UpdateInfo>(it) }.getOrNull() }
        val notification = if (info != null) {
            UpdateNotifications.progress(applicationContext, info, 0, info.size, verifying = false, pause = null)
        } else {
            UpdateNotifications.progress(applicationContext, PLACEHOLDER, 0, 0, verifying = false, pause = null)
        }
        return foregroundInfo(notification)
    }

    private suspend fun enterForeground(notification: Notification) {
        try {
            setForeground(foregroundInfo(notification))
            inForeground = true
        } catch (e: IllegalStateException) {
            // Android 12+ refuses a foreground start from the background (e.g. a retry while
            // the app is closed): download without one; WorkManager resumes it if stopped.
            Log.w(TAG, "cannot run in the foreground", e)
        }
    }

    /** Room for [needed] bytes in [dir]; on Android 8+ clearable cache is freed when that is what it takes. */
    private fun ensureSpace(dir: File, needed: Long): Boolean {
        dir.mkdirs()
        if (dir.usableSpace >= needed) return true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val storage = applicationContext.getSystemService(StorageManager::class.java) ?: return false
        return try {
            val uuid = storage.getUuidForPath(dir)
            if (storage.getAllocatableBytes(uuid) < needed) return false
            storage.allocateBytes(uuid, needed)
            true
        } catch (e: IOException) {
            Log.w(TAG, "cannot free space", e)
            false
        }
    }

    private fun foregroundInfo(notification: Notification): ForegroundInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(UpdateNotifications.ID_PROGRESS, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(UpdateNotifications.ID_PROGRESS, notification)
        }

    private fun failure(
        failure: UpdateFailure,
        detail: String? = null,
        verifying: Boolean = false,
        info: UpdateInfo? = null,
    ): Result {
        if (info != null && !isStopped) {
            val message = applicationContext.getString(failure.message, *(if (failure.hasArgument) arrayOf(detail.orEmpty()) else emptyArray()))
            UpdateNotifications.showFailed(applicationContext, info, message)
        }
        return Result.failure(
            Data.Builder()
                .putString(KEY_FAILURE, failure.name)
                .putString(KEY_DETAIL, detail)
                .putBoolean(KEY_VERIFYING, verifying)
                .build()
        )
    }

    /** Throttled progress: WorkInfo progress every 0.5 s, the notification every second. */
    private inner class ProgressReporter(private val info: UpdateInfo, private val pause: PendingIntent) {
        private var lastProgressAt = 0L
        private var lastNotifyAt = 0L
        private var windowStartAt = SystemClock.elapsedRealtime()
        private var windowStartBytes = -1L
        private var speed = 0L
        private var downloaded = 0L
        private var total = info.size

        fun notification(downloaded: Long, total: Long, verifying: Boolean) =
            UpdateNotifications.progress(applicationContext, info, downloaded, total, verifying, pause)

        fun show(notification: Notification) {
            AppNotifications.notify(applicationContext, UpdateNotifications.ID_PROGRESS, notification)
        }

        fun report(downloaded: Long, total: Long) {
            this.downloaded = downloaded
            if (total > 0) this.total = total
            val now = SystemClock.elapsedRealtime()
            if (windowStartBytes < 0 || downloaded < windowStartBytes) {
                windowStartBytes = downloaded
                windowStartAt = now
            } else if (now - windowStartAt >= SPEED_WINDOW_MS) {
                speed = (downloaded - windowStartBytes) * 1000 / (now - windowStartAt)
                windowStartBytes = downloaded
                windowStartAt = now
            }
            if (now - lastProgressAt >= PROGRESS_INTERVAL_MS) {
                lastProgressAt = now
                publish()
            }
            if (now - lastNotifyAt >= NOTIFY_INTERVAL_MS) {
                lastNotifyAt = now
                show(notification(downloaded, this.total, verifying = false))
            }
        }

        suspend fun flush() {
            setProgress(progressData())
        }

        private fun publish() {
            setProgressAsync(progressData())
        }

        private fun progressData(): Data = workDataOf(KEY_DOWNLOADED to downloaded, KEY_TOTAL to total, KEY_SPEED to speed)
    }

    companion object {
        private const val TAG = "UpdateDownloadWorker"
        const val NAME = "app_update_download"
        const val TAG_UNMETERED = "update_unmetered"
        const val TAG_VERSION = "update_v"
        const val KEY_INFO = "info"
        const val KEY_DOWNLOADED = "downloaded"
        const val KEY_TOTAL = "total"
        const val KEY_SPEED = "speed"
        const val KEY_VERIFYING = "verifying"
        const val KEY_FAILURE = "failure"
        const val KEY_DETAIL = "detail"
        const val KEY_APK = "apk"

        private const val MAX_ATTEMPTS = 3
        private const val SPACE_MARGIN = 8L * 1024 * 1024
        private const val PROGRESS_INTERVAL_MS = 500L
        private const val NOTIFY_INTERVAL_MS = 1_000L
        private const val SPEED_WINDOW_MS = 1_000L

        private val PLACEHOLDER = UpdateInfo(0, "", "", "", 0, "", "", emptyList())

        /**
         * Enqueues the download of [info], replacing any earlier run (a paused download of
         * the same version resumes from its part file). [unmetered] waits for Wi-Fi.
         */
        fun enqueue(context: Context, info: UpdateInfo, unmetered: Boolean) {
            val json = Json { ignoreUnknownKeys = true }
            val request = OneTimeWorkRequestBuilder<UpdateDownloadWorker>()
                .setInputData(workDataOf(KEY_INFO to json.encodeToString(UpdateInfo.serializer(), info)))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(if (unmetered) NetworkType.UNMETERED else NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag(TAG_VERSION + info.versionCode)
                .apply { if (unmetered) addTag(TAG_UNMETERED) }
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
