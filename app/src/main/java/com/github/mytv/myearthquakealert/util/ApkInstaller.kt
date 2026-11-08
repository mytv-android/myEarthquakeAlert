package com.github.mytv.myearthquakealert.util

import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

enum class InstallResult { Launched, NeedsPermission }

object ApkInstaller {
    private const val TAG = "ApkInstaller"

    private var installResultReceiver: BroadcastReceiver? = null

    /**
     * 依次尝试 ACTION_VIEW → ACTION_INSTALL_PACKAGE → PackageInstaller 会话。
     * 只有全部起不来时才认为缺少“安装未知来源”授权。
     */
    @Suppress("DEPRECATION")
    suspend fun installApk(context: Context, apkFile: File): InstallResult {
        val appContext = context.applicationContext
        val uri = runCatching {
            FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", apkFile)
        }.onFailure {
            Log.w(TAG, "无法生成更新包的 content URI", it)
        }.getOrNull()

        if (uri != null) {
            if (startInstallerActivity(appContext, Intent.ACTION_VIEW, uri)) return InstallResult.Launched
            if (startInstallerActivity(appContext, Intent.ACTION_INSTALL_PACKAGE, uri)) return InstallResult.Launched
        }

        if (withContext(Dispatchers.IO) { commitInstallSession(appContext, apkFile) }) {
            return InstallResult.Launched
        }
        return InstallResult.NeedsPermission
    }

    private fun startInstallerActivity(context: Context, action: String, apkUri: Uri): Boolean = try {
        val intent = Intent(action)
            .setDataAndType(apkUri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        // 有 clipData 时授权按 clipData 走，个别 ROM 只认这里不看 setData
        intent.clipData = ClipData.newRawUri("", apkUri)
        context.startActivity(intent)
        true
    } catch (error: ActivityNotFoundException) {
        Log.w(TAG, "$action 没有可响应的系统安装器", error)
        false
    } catch (error: Exception) {
        Log.w(TAG, "$action 唤起安装器失败", error)
        false
    }

    /**
     * 走系统 PackageInstaller 会话安装，不依赖安装器 Activity 的 IntentFilter。
     * 部分 ROM 把安装确认界面交给调用方拉起，收到 PENDING_USER_ACTION 时继续拉起。
     */
    private fun commitInstallSession(context: Context, file: File): Boolean {
        val installer = context.packageManager.packageInstaller
        var sessionId = -1
        return try {
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                file.inputStream().use { input ->
                    session.openWrite("update.apk", 0, file.length()).use { output ->
                        input.copyTo(output)
                        // 写完后必须写透，否则系统读到的是不完整的包
                        output.flush()
                    }
                }
                registerInstallResultReceiver(context)
                session.commit(installResultSender(context, sessionId))
            }
            true
        } catch (error: Exception) {
            Log.w(TAG, "PackageInstaller 会话安装失败", error)
            if (sessionId >= 0) {
                runCatching { installer.abandonSession(sessionId) }
            }
            false
        }
    }

    private fun installResultSender(context: Context, sessionId: Int): IntentSender {
        val intent = Intent(INSTALL_RESULT_ACTION).setPackage(context.packageName)
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= 31) {
            flags = flags or PendingIntent.FLAG_MUTABLE
        }
        return PendingIntent.getBroadcast(context, sessionId, intent, flags).intentSender
    }

    private fun registerInstallResultReceiver(context: Context) {
        if (installResultReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                    confirmIntentOf(intent)?.let { confirm ->
                        confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { receiverContext.startActivity(confirm) }
                            .onFailure { Log.w(TAG, "拉起安装确认界面失败", it) }
                    }
                    return
                }
                unregisterInstallResultReceiver(receiverContext)
                val detail = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                if (status == PackageInstaller.STATUS_SUCCESS) {
                    Log.i(TAG, "更新包已安装")
                } else {
                    Log.w(TAG, "安装失败${if (detail.isNullOrBlank()) "" else "：$detail"}")
                }
            }
        }
        installResultReceiver = receiver
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(INSTALL_RESULT_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    private fun unregisterInstallResultReceiver(context: Context) {
        val receiver = installResultReceiver ?: return
        installResultReceiver = null
        runCatching { context.unregisterReceiver(receiver) }
            .onFailure { Log.w(TAG, "注销安装结果监听失败", it) }
    }

    @Suppress("DEPRECATION")
    private fun confirmIntentOf(intent: Intent): Intent? =
        if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_INTENT)
        }

    fun formatFileSize(bytes: Long): String = when {
        bytes >= 1024L * 1024 * 1024 ->
            String.format(Locale.ROOT, "%.1f GB", bytes / 1024.0 / 1024 / 1024)
        bytes >= 1024L * 1024 ->
            String.format(Locale.ROOT, "%.1f MB", bytes / 1024.0 / 1024)
        bytes >= 1024L -> String.format(Locale.ROOT, "%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    private const val INSTALL_RESULT_ACTION = "com.github.mytv.myearthquakealert.action.INSTALL_RESULT"
}
