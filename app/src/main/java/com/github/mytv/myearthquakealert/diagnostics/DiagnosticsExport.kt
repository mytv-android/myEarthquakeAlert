package com.github.mytv.myearthquakealert.diagnostics

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.github.mytv.myearthquakealert.MyEarthQuakeAlertApp
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.data.repository.UserSettings
import com.github.mytv.myearthquakealert.service.BootStartRecord
import com.github.mytv.myearthquakealert.service.EewHistory
import com.github.mytv.myearthquakealert.service.EewRecord
import com.github.mytv.myearthquakealert.service.MonitorStatus
import com.github.mytv.myearthquakealert.service.WatchdogWorker
import com.github.mytv.myearthquakealert.util.LogExporter
import com.github.mytv.myearthquakealert.util.isUserUnlocked
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.roundToLong

/**
 * 导出诊断包 (design §5): a zip in the cache with everything needed to judge a report of
 * "no alert arrived" — the event log (live and persisted), metrics, the 24 h availability,
 * the last self test, the monitor state, the early-warning history, settings with
 * coordinates rounded to 0.1° and no place names, device/build information and crash logs —
 * shared through the system sheet.
 */
object DiagnosticsExport {

    private const val TAG = "DiagnosticsExport"
    private const val DIR = "diagnostics"
    private const val KEEP = 3

    private val prettyJson = Json { prettyPrint = true }

    /** Builds the bundle in the background and opens the share sheet. */
    fun share(context: Context) {
        val app = context.applicationContext as MyEarthQuakeAlertApp
        app.appScope.launch {
            val file = try {
                withContext(Dispatchers.IO) { build(app) }
            } catch (e: Exception) {
                Log.e(TAG, "export failed", e)
                Diagnostics.log(HealthLevel.ERROR, LogTags.ERROR, "export: ${e.message}")
                Toast.makeText(app, R.string.svc_export_failed, Toast.LENGTH_LONG).show()
                return@launch
            }
            try {
                val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
                val send = Intent(Intent.ACTION_SEND)
                    .setType("application/zip")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .putExtra(Intent.EXTRA_SUBJECT, file.name)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val chooser = Intent.createChooser(send, app.getString(R.string.svc_export_title))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                (if (context is android.app.Activity) context else app).startActivity(chooser)
            } catch (e: Exception) {
                Log.e(TAG, "share failed", e)
                Toast.makeText(app, R.string.svc_export_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    /** Writes the bundle and returns it (cacheDir/diagnostics, the newest [KEEP] kept). */
    suspend fun build(context: Context): File {
        val app = context.applicationContext as MyEarthQuakeAlertApp
        val settings = try {
            app.settingsRepository.settings.first()
        } catch (e: Exception) {
            null
        }
        val dir = File(app.cacheDir, DIR).apply { mkdirs() }
        dir.listFiles()?.filter { it.name.endsWith(".zip") }?.sortedBy { it.name }?.dropLast(KEEP - 1)?.forEach { it.delete() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "eew_diagnostics_$stamp.zip")
        val persisted = HealthRecorder.exportFiles()
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            zip.text("device.txt", deviceInfo(app))
            zip.text("settings.json", settings?.let { prettyJson.encodeToString(JsonObject.serializer(), redacted(it)) } ?: "null")
            zip.text("monitor_state.json", prettyJson.encodeToString(JsonObject.serializer(), monitorState()))
            zip.text("metrics.json", prettyJson.encodeToString(JsonObject.serializer(), metrics(Diagnostics.metrics.value)))
            zip.text("availability.json", prettyJson.encodeToString(JsonArray.serializer(), availability()))
            zip.text("selftest.json", prettyJson.encodeToString(JsonElement.serializer(), selfTest(Diagnostics.selfTest.value)))
            zip.text("log.txt", Diagnostics.log.value.joinToString("\n") { "${time(it.time)}  ${it.level.name.padEnd(5)} ${it.tag.padEnd(10)} ${it.message}" })
            zip.text("eew_history.json", prettyJson.encodeToString(ListSerializer(EewRecord.serializer()), EewHistory.records.value))
            persisted.forEach { zip.file("health/${it.name}", it) }
            LogExporter.crashLogFiles(app).forEach { zip.file("crash/${it.name}", it) }
        }
        return file
    }

    private fun ZipOutputStream.text(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun ZipOutputStream.file(name: String, source: File) {
        try {
            putNextEntry(ZipEntry(name))
            source.inputStream().use { it.copyTo(this) }
            closeEntry()
        } catch (e: Exception) {
            Log.w(TAG, "skip $name", e)
        }
    }

    private fun deviceInfo(context: Context): String = buildString {
        val pm = context.packageManager
        val info = try {
            pm.getPackageInfo(context.packageName, 0)
        } catch (e: Exception) {
            null
        }
        appendLine("App: ${context.packageName} ${info?.versionName ?: "?"} (${info?.let { PackageInfoCompat.getLongVersionCode(it) } ?: "?"})")
        appendLine("Exported: ${time(System.currentTimeMillis())} ${TimeZone.getDefault().id}")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) ${Build.DISPLAY}")
        appendLine("Locale: ${Locale.getDefault()}")
        appendLine("Unlocked: ${context.isUserUnlocked()}")
        appendLine("Battery optimization exempt: ${HealthRecorder.batteryExempt(context)}")
        appendLine("Standby bucket: ${HealthRecorder.standbyBucket(context)}")
        appendLine("Data saver: ${HealthRecorder.dataSaverOn(context)}")
        appendLine("Watchdog last run: ${WatchdogWorker.lastRunAt(context)?.let(::time)}")
        appendLine("Watchdog next run: ${WatchdogWorker.nextRunAt(context)?.let(::time)}")
        appendLine("Service started this boot: ${BootStartRecord.serviceStartedThisBoot(context)}")
        appendLine("Boot start delay: ${BootStartRecord.delaySecondsThisBoot(context)?.let { "$it s" }}")
        appendLine("Clock offset: ${ClockSync.currentOffsetMs()?.let { ClockSync.formatSeconds(it) + " s" }}")
    }

    /** Settings without anything that locates the user more precisely than ~10 km. */
    private fun redacted(s: UserSettings): JsonObject = buildJsonObject {
        put("serviceEnabled", s.serviceEnabled)
        put("bootStart", s.bootStart)
        put("enabledSources", buildJsonArray { s.enabledSources.forEach { add(JsonPrimitive(it.name)) } })
        put("pauseUntil", if (s.pauseUntil > 0) time(s.pauseUntil) else "")
        put("actionMinMagnitude", s.actionMinMagnitude)
        put("actionMinIntensity", s.actionMinIntensity)
        put("intenseThreshold", s.intenseThreshold)
        put("allowDismissWithBack", s.allowDismissWithBack)
        put("ttsEnabled", s.ttsEnabled)
        put("alarmBypassDnd", s.alarmBypassDnd)
        put("themeMode", s.themeMode.name)
        put("layoutOverride", s.layoutOverride.name)
        put("mapStyle", s.mapStyle.name)
        put("locationMode", s.locationMode.name)
        put("manualLatitude", s.manualLatitude?.let { JsonPrimitive(round1(it)) } ?: JsonNull)
        put("manualLongitude", s.manualLongitude?.let { JsonPrimitive(round1(it)) } ?: JsonNull)
        put("manualLocationNameSet", s.manualLocationName.isNotBlank())
        put("updateAutoCheck", s.updateAutoCheck)
        put("updateChannel", s.updateChannel.name)
        put("updateWifiOnly", s.updateWifiOnly)
    }

    private fun monitorState(): JsonObject {
        val st = MonitorStatus.state.value
        return buildJsonObject {
            put("phase", st.phase.name)
            put("phaseSince", time(st.phaseSince))
            put("runningSince", st.runningSince?.let(::time) ?: "")
            put("pauseUntil", if (st.pauseUntil > 0) time(st.pauseUntil) else "")
            put("startOrigin", st.startOrigin?.name ?: "")
            put("detectorProcessStartedAt", st.detectorProcessStartedAt?.let(::time) ?: "")
            put("missing", buildJsonArray { st.missing.forEach { add(JsonPrimitive(it.name)) } })
            put("latestReport", st.latestReport?.let { "${it.eventId} #${it.reportNum} ${it.hypocenter} M${it.magnitude}" } ?: "")
            put("sources", buildJsonArray {
                st.sources.forEach { h ->
                    add(buildJsonObject {
                        put("source", h.source.name)
                        put("link", h.link.name)
                        put("lastFrameAt", h.lastFrameAt?.let(::time) ?: "")
                        put("lastReportAt", h.lastReportAt?.let(::time) ?: "")
                        put("lastLatencyMs", h.lastLatencyMs?.let { JsonPrimitive(it) } ?: JsonNull)
                        put("reconnectAttempt", h.reconnectAttempt)
                        put("consecutiveFailures", h.consecutiveFailures)
                    })
                }
            })
        }
    }

    private fun metrics(m: HealthMetrics): JsonObject = buildJsonObject {
        put("availability24h", m.availability24h?.let { JsonPrimitive(it) } ?: JsonNull)
        put("reconnects24h", m.reconnects24h)
        put("longestOutageMs", m.longestOutageMs)
        put("heartbeatAgeMs", m.heartbeatAgeMs?.let { JsonPrimitive(it) } ?: JsonNull)
        put("heartbeatSamplesSec", buildJsonArray { m.heartbeatSamplesSec.forEach { add(JsonPrimitive(it)) } })
        put("latencyP50Ms", m.latencyP50Ms?.let { JsonPrimitive(it) } ?: JsonNull)
        put("latencyP95Ms", m.latencyP95Ms?.let { JsonPrimitive(it) } ?: JsonNull)
        put("reportsToday", m.reportsToday)
        put("clockOffsetMs", m.clockOffsetMs?.let { JsonPrimitive(it) } ?: JsonNull)
        put("clockCheckedAt", m.clockCheckedAt?.let(::time) ?: "")
        put("locationSource", m.locationSource ?: "")
        put("locationAccuracyM", m.locationAccuracyM?.let { JsonPrimitive(it) } ?: JsonNull)
        put("locationAgeMs", m.locationAgeMs?.let { JsonPrimitive(it) } ?: JsonNull)
        put("batteryOptimizationExempt", m.batteryOptimizationExempt?.let { JsonPrimitive(it) } ?: JsonNull)
        put("standbyBucket", m.standbyBucket ?: "")
        put("dataSaverOn", m.dataSaverOn?.let { JsonPrimitive(it) } ?: JsonNull)
        put("watchdogLastRunAt", m.watchdogLastRunAt?.let(::time) ?: "")
        put("watchdogNextRunAt", m.watchdogNextRunAt?.let(::time) ?: "")
        put("bootStartDelaySec", m.bootStartDelaySec?.let { JsonPrimitive(it) } ?: JsonNull)
        put("pipelineParseMs", m.pipelineParseMs?.let { JsonPrimitive(it) } ?: JsonNull)
        put("pipelineEvaluateMs", m.pipelineEvaluateMs?.let { JsonPrimitive(it) } ?: JsonNull)
        put("endToEndMs", m.endToEndMs?.let { JsonPrimitive(it) } ?: JsonNull)
    }

    private fun availability(): JsonArray = buildJsonArray {
        Diagnostics.availability.value.forEach { slot ->
            add(buildJsonObject {
                put("start", time(slot.start))
                put("status", slot.status.name)
            })
        }
    }

    private fun selfTest(run: SelfTestRun?): JsonElement {
        if (run == null) return JsonNull
        return buildJsonObject {
            put("startedAt", time(run.startedAt))
            put("finishedAt", run.finishedAt?.let(::time) ?: "")
            put("steps", buildJsonArray {
                run.steps.forEach { step ->
                    add(buildJsonObject {
                        put("step", step.step.name)
                        put("status", step.status.name)
                        put("detail", step.detail)
                        put("fix", step.fix?.name ?: "")
                    })
                }
            })
        }
    }

    private fun round1(value: Double): Double = (value * 10).roundToLong() / 10.0

    private fun time(epochMillis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(epochMillis))
}
