package com.github.mytv.myearthquakealert.update

import android.app.ActivityManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import com.github.mytv.myearthquakealert.data.model.UpdateInfo
import com.github.mytv.myearthquakealert.service.ActiveAlertHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 安装 (design §8): PackageInstaller session install of a verified package. The session
 * result arrives at [InstallStatusReceiver]: the system's confirmation screen is launched
 * (or announced by a notification when the app is not in front), success needs nothing —
 * the process is replaced and MY_PACKAGE_REPLACED restores monitoring — and failures map to
 * [UpdateFailure] messages.
 */
object SessionInstaller {
    private const val TAG = "SessionInstaller"
    const val ACTION_STATUS = "com.github.mytv.myearthquakealert.action.INSTALL_STATUS"
    const val EXTRA_VERSION_CODE = "version_code"
    const val EXTRA_VERSION_NAME = "version_name"

    sealed interface Status {
        data object Idle : Status
        /** 安装未知应用 is not granted yet (Android 8+); the page explains it first. */
        data class NeedsPermission(val info: UpdateInfo) : Status
        /** The session is committed; [confirm] is the system confirmation screen once asked for. */
        data class Installing(val info: UpdateInfo, val confirm: Intent? = null) : Status
        data class Failed(val info: UpdateInfo?, val failure: UpdateFailure, val detail: String?) : Status
        data object Succeeded : Status
    }

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    /** Whether this app may install packages (always true before Android 8, where the system asks). */
    fun canRequestInstalls(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun reset() {
        _status.value = Status.Idle
    }

    /** A failure found before a session was created (e.g. the stored package changed). */
    fun fail(info: UpdateInfo, failure: UpdateFailure) {
        _status.value = Status.Failed(info, failure, null)
    }

    /** Writes [apk] into a new session and commits it. */
    suspend fun install(context: Context, apk: File, info: UpdateInfo) {
        val app = context.applicationContext
        if (!canRequestInstalls(app)) {
            _status.value = Status.NeedsPermission(info)
            return
        }
        _status.value = Status.Installing(info)
        val result = withContext(Dispatchers.IO) { runCatching { commit(app, apk, info) } }
        result.exceptionOrNull()?.let { error ->
            Log.w(TAG, "session install failed", error)
            val failure = (error as? UpdateException)?.failure ?: UpdateFailure.INSTALL_FAILED
            _status.value = Status.Failed(info, failure, (error as? UpdateException)?.detail ?: error.message)
        }
    }

    private fun commit(context: Context, apk: File, info: UpdateInfo) {
        if (!apk.isFile) throw UpdateException(UpdateFailure.FILE_MISSING)
        val installer = context.packageManager.packageInstaller
        // Sessions left by an earlier attempt (process died while waiting for confirmation).
        installer.mySessions.forEach { runCatching { installer.abandonSession(it.sessionId) } }

        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) setInstallReason(android.content.pm.PackageManager.INSTALL_REASON_USER)
            // Updating ourselves as installer of record needs no second confirmation (Android 12+).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setPackageSource(PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE)
        }
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite("base.apk", 0, apk.length()).use { output ->
                        input.copyTo(output, 64 * 1024)
                        session.fsync(output)
                    }
                }
                session.commit(statusSender(context, sessionId, info))
            }
        } catch (error: Exception) {
            runCatching { installer.abandonSession(sessionId) }
            throw error
        }
    }

    private fun statusSender(context: Context, sessionId: Int, info: UpdateInfo) =
        PendingIntent.getBroadcast(
            context,
            sessionId,
            Intent(context, InstallStatusReceiver::class.java)
                .setAction(ACTION_STATUS)
                .putExtra(EXTRA_VERSION_CODE, info.versionCode)
                .putExtra(EXTRA_VERSION_NAME, info.versionName),
            // The installer fills in the status extras, so the intent must stay mutable.
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0),
        ).intentSender

    /** Launches the pending confirmation screen again (「继续安装」). */
    fun launchConfirm(context: Context): Boolean {
        val confirm = (status.value as? Status.Installing)?.confirm ?: return false
        return runCatching { context.startActivity(Intent(confirm).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { Log.w(TAG, "cannot open the install confirmation", it) }
            .isSuccess
    }

    internal fun onStatus(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val versionName = intent.getStringExtra(EXTRA_VERSION_NAME).orEmpty()
        val versionCode = intent.getLongExtra(EXTRA_VERSION_CODE, 0L)
        val info = when (val current = _status.value) {
            is Status.Installing -> current.info
            is Status.NeedsPermission -> current.info
            is Status.Failed -> current.info
            else -> null
        }?.takeIf { it.versionCode == versionCode }
        Log.i(TAG, "install status $status ${message.orEmpty()}")
        UpdateNotifications.cancel(context, UpdateNotifications.ID_CONFIRM)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = confirmIntent(intent) ?: run {
                    _status.value = Status.Failed(info, UpdateFailure.INSTALL_FAILED, message)
                    return
                }
                if (info != null) _status.value = Status.Installing(info, confirm)
                // Never put the installer over an alert; the page keeps 「继续安装」.
                if (ActiveAlertHolder.activeAlert.value != null) return
                if (isInForeground()) {
                    runCatching { context.startActivity(Intent(confirm).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        .onFailure { UpdateNotifications.showConfirm(context, versionName, Intent(confirm)) }
                } else {
                    UpdateNotifications.showConfirm(context, versionName, Intent(confirm))
                }
            }
            PackageInstaller.STATUS_SUCCESS -> _status.value = Status.Succeeded
            else -> _status.value = Status.Failed(info, failureOf(status), message)
        }
    }

    fun failureOf(status: Int): UpdateFailure = when (status) {
        PackageInstaller.STATUS_FAILURE_BLOCKED -> UpdateFailure.INSTALL_BLOCKED
        PackageInstaller.STATUS_FAILURE_ABORTED -> UpdateFailure.INSTALL_ABORTED
        PackageInstaller.STATUS_FAILURE_INVALID -> UpdateFailure.INSTALL_INVALID
        PackageInstaller.STATUS_FAILURE_CONFLICT -> UpdateFailure.INSTALL_CONFLICT
        PackageInstaller.STATUS_FAILURE_STORAGE -> UpdateFailure.INSTALL_STORAGE
        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> UpdateFailure.INSTALL_INCOMPATIBLE
        STATUS_FAILURE_TIMEOUT -> UpdateFailure.INSTALL_TIMEOUT
        else -> UpdateFailure.INSTALL_FAILED
    }

    /** PackageInstaller.STATUS_FAILURE_TIMEOUT (API 34). */
    private const val STATUS_FAILURE_TIMEOUT = 8

    @Suppress("DEPRECATION")
    private fun confirmIntent(intent: Intent): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_INTENT)
        }

    private fun isInForeground(): Boolean {
        val info = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(info)
        return info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }
}

/** Receives the PackageInstaller session result (manifest-registered, not exported). */
class InstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != SessionInstaller.ACTION_STATUS) return
        SessionInstaller.onStatus(context.applicationContext, intent)
    }
}
