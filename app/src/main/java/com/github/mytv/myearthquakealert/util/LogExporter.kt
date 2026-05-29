package com.github.mytv.myearthquakealert.util

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogExporter {

    fun export(context: Context) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "earthquake_alert_log_$timestamp.txt"
        val logDir = File(context.cacheDir, "logs").apply { mkdirs() }
        val logFile = File(logDir, fileName)

        // Capture logcat for this app's process
        val process = Runtime.getRuntime().exec(
            "logcat -d -v threadtime --pid=${android.os.Process.myPid()}"
        )
        logFile.outputStream().use { out ->
            // Write device/app header
            val header = buildString {
                appendLine("=== Earthquake Alert Log ===")
                appendLine("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
                appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                appendLine("App: ${context.packageName}")
                appendLine("=============================")
                appendLine()
            }
            out.write(header.toByteArray())
            process.inputStream.copyTo(out)
        }
        process.waitFor()

        // Share via intent
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            logFile
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(shareIntent, "Export Log").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
