package com.github.mytv.myearthquakealert.util

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogExporter {

    private const val TAG = "LogExporter"
    private const val CRASH_DIR = "crash_logs"
    private lateinit var crashLogDir: File
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null

    fun init(context: Context) {
        crashLogDir = File(context.filesDir, CRASH_DIR).apply { mkdirs() }
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            saveCrashToFile(throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun saveCrashToFile(throwable: Throwable) {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(crashLogDir, "crash_$timestamp.txt")
            val sw = StringWriter()
            PrintWriter(sw).use { pw ->
                pw.println("=== Crash Log ===")
                pw.println("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
                pw.println("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                pw.println("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                pw.println("=================")
                pw.println()
                throwable.printStackTrace(pw)
            }
            file.writeText(sw.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save crash log", e)
        }
    }

    fun export(context: Context) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "earthquake_alert_log_$timestamp.txt"
        val logDir = File(context.cacheDir, "logs").apply { mkdirs() }
        val logFile = File(logDir, fileName)

        logFile.outputStream().use { out ->
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

            // Crash logs from disk
            val crashFiles = crashLogDir.listFiles()?.sortedBy { it.name }?.takeLast(5)
            if (!crashFiles.isNullOrEmpty()) {
                out.write("=== Recent Crashes ===\n\n".toByteArray())
                for (f in crashFiles) {
                    out.write("--- ${f.name} ---\n".toByteArray())
                    out.write(f.readBytes())
                    out.write("\n\n".toByteArray())
                }
            }

            // Full logcat: no --pid filter, capture AndroidRuntime crashes and app logs
            val process = Runtime.getRuntime().exec(
                arrayOf("logcat", "-d", "-v", "threadtime", "-t", "2000")
            )
            process.inputStream.copyTo(out)
            process.waitFor()
        }

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
