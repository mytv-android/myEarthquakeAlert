package com.github.mytv.myearthquakealert.diagnostics

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.github.mytv.myearthquakealert.MyEarthQuakeAlertApp
import com.github.mytv.myearthquakealert.data.model.UserLocation
import com.github.mytv.myearthquakealert.service.BootStartRecord
import com.github.mytv.myearthquakealert.service.MonitorPhase
import com.github.mytv.myearthquakealert.service.MonitorState
import com.github.mytv.myearthquakealert.service.WatchdogWorker
import com.github.mytv.myearthquakealert.util.deviceProtectedContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Calendar
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** One persisted health event (newline-delimited JSON, device-protected storage, 7 days). */
@Serializable
data class HealthEvent(
    /** Epoch millis. */
    val t: Long,
    /** Kind, one of the HealthRecorder.K_* constants. */
    val k: String,
    /** Source name (EewSource) when the event concerns one feed. */
    val s: String? = null,
    /** Numeric value: latency, handshake time, delay, offset… */
    val v: Long? = null,
    /** Text value: phase, link status, reason, tier… */
    val d: String? = null,
)

/**
 * The service's flight recorder (design §5 数据保留). Persists state transitions, link
 * changes, reconnects, reports with their latency, alerts, clock checks, starts and stops,
 * plus the last 500 log lines, in device-protected storage (readable before the first
 * unlock), keeping 7 days. Room is not available, so both are newline-delimited JSON files
 * written on one background thread, in order.
 *
 * From these it computes [HealthMetrics] and the 96 availability cells and publishes them in
 * [Diagnostics]: every few seconds while the service runs ([publish]) and once at process
 * start ([init]), so the diagnostics page is never empty even with monitoring off.
 */
object HealthRecorder {

    private const val TAG = "HealthRecorder"
    private const val DIR = "health"
    private const val EVENTS_FILE = "events.ndjson"
    private const val LOG_FILE = "log.ndjson"
    private const val METRICS_FILE = "metrics.json"
    private const val PREFS = "health"
    private const val KEY_ALIVE_AT = "alive_at"

    const val RETENTION_MS = 7 * 24 * 3_600_000L
    private const val LOG_KEEP = 500
    private const val LOG_COMPACT_AT = 1_000
    private const val MAX_HEARTBEAT_SAMPLES = 30
    private const val SNAPSHOT_EVERY_MS = 60_000L
    private const val PRUNE_EVERY_MS = 6 * 3_600_000L

    const val K_START = "START"
    const val K_STOP = "STOP"
    const val K_PHASE = "PHASE"
    const val K_LINK = "LINK"
    const val K_CONNECT = "CONNECT"
    const val K_DISCONNECT = "DISCONNECT"
    const val K_RECONNECT = "RECONNECT"
    const val K_FALLBACK = "FALLBACK"
    const val K_REPORT = "REPORT"
    const val K_ALERT = "ALERT"
    const val K_CLOCK = "CLOCK"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    /** All file and event-list work runs here, in submission order. */
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "health-recorder").apply { isDaemon = true } }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Confined to [executor].
    private val events = ArrayList<HealthEvent>()
    private var logLines = 0
    private var lastPersistedLog: LogEntry? = null
    private var lastSnapshotAt = 0L
    private var lastPruneAt = 0L

    @Volatile private var dir: File? = null
    @Volatile private var initialized = false

    // Live samples, written from the service thread.
    private val heartbeatSamples = ArrayDeque<Int>()
    @Volatile private var pipelineParseMs: Long? = null
    @Volatile private var pipelineEvaluateMs: Long? = null
    @Volatile private var endToEndMs: Long? = null

    @Serializable
    private data class LogLine(val t: Long, val l: String, val g: String, val m: String)

    @Serializable
    private data class Snapshot(
        val savedAt: Long = 0L,
        val heartbeatSamplesSec: List<Int> = emptyList(),
        val pipelineParseMs: Long? = null,
        val pipelineEvaluateMs: Long? = null,
        val endToEndMs: Long? = null,
        val heartbeatAgeMs: Long? = null,
        val locationSource: String? = null,
        val locationAccuracyM: Float? = null,
    )

    /**
     * Loads the persisted log into [Diagnostics] synchronously (small, and before anything
     * logs), then loads events and publishes metrics in the background, and from then on
     * persists every new [Diagnostics.log] entry.
     */
    fun init(context: Context) {
        if (initialized) return
        initialized = true
        val app = context.applicationContext
        val folder = File(app.deviceProtectedContext().filesDir, DIR).apply { mkdirs() }
        dir = folder

        val now = System.currentTimeMillis()
        val loaded = readLines(File(folder, LOG_FILE)) { json.decodeFromString(LogLine.serializer(), it) }
            .filter { now - it.t < RETENTION_MS }
            .map { LogEntry(it.t, enumValueOrNull<HealthLevel>(it.l) ?: HealthLevel.INFO, it.g, it.m) }
        Diagnostics.replaceLog(loaded)
        val persisted = Diagnostics.log.value
        executor.execute {
            lastPersistedLog = persisted.lastOrNull()
            logLines = persisted.size
            // Rewrite once when entries expired or the file grew past the cap.
            if (persisted.size != loaded.size || loaded.size > LOG_KEEP) rewriteLog(persisted)
        }

        executor.execute { loadEvents(app) }
        publish(app, null, null)

        scope.launch {
            Diagnostics.log.collect { list -> executor.execute { persistNewLog(list) } }
        }
    }

    // ── Recording ───────────────────────────────────────────────────────────

    fun recordStart(origin: String) = record(HealthEvent(now(), K_START, d = origin))
    fun recordStop(reason: String) = record(HealthEvent(now(), K_STOP, d = reason))
    fun recordPhase(phase: MonitorPhase) = record(HealthEvent(now(), K_PHASE, d = phase.name))
    fun recordLink(status: SlotStatus) = record(HealthEvent(now(), K_LINK, d = status.name))
    fun recordConnect(source: String, handshakeMs: Long) = record(HealthEvent(now(), K_CONNECT, s = source, v = handshakeMs))
    fun recordDisconnect(source: String, reason: String) = record(HealthEvent(now(), K_DISCONNECT, s = source, d = reason))
    fun recordReconnect(source: String, attempt: Int, delayMs: Long) =
        record(HealthEvent(now(), K_RECONNECT, s = source, v = delayMs, d = attempt.toString()))
    fun recordFallback(source: String, active: Boolean) = record(HealthEvent(now(), K_FALLBACK, s = source, d = if (active) "ON" else "OFF"))
    /** A fresh live report; [latencyMs] = receive time − ReportTime, clock-corrected. */
    fun recordReport(source: String, latencyMs: Long?) = record(HealthEvent(now(), K_REPORT, s = source, v = latencyMs))
    fun recordAlert(eventId: String, tier: String, drill: Boolean) =
        record(HealthEvent(now(), K_ALERT, s = eventId, d = if (drill) "$tier/DRILL" else tier))
    fun recordClock(offsetMs: Long) = record(HealthEvent(now(), K_CLOCK, v = offsetMs))

    /** Gap between two frames of one source (the 心跳间隔 sparkline). */
    fun recordHeartbeatGap(seconds: Int) {
        synchronized(heartbeatSamples) {
            heartbeatSamples.addLast(seconds)
            while (heartbeatSamples.size > MAX_HEARTBEAT_SAMPLES) heartbeatSamples.removeFirst()
        }
    }

    /** Timings of the last report through the pipeline (数据链路). */
    fun recordPipeline(parseMs: Long?, evaluateMs: Long?, endToEndMs: Long?) {
        parseMs?.let { pipelineParseMs = it }
        evaluateMs?.let { pipelineEvaluateMs = it }
        endToEndMs?.let { this.endToEndMs = it }
    }

    private fun now() = System.currentTimeMillis()

    private fun record(event: HealthEvent) {
        if (dir == null) return
        executor.execute {
            events += event
            append(File(dir, EVENTS_FILE), json.encodeToString(HealthEvent.serializer(), event))
        }
    }

    // ── Publishing ──────────────────────────────────────────────────────────

    /**
     * Recomputes metrics and availability and publishes them in [Diagnostics]. [state] is the
     * live monitor state (null when the service is not running); [location] the location in
     * use (falls back to the last persisted one).
     */
    fun publish(context: Context, state: MonitorState?, location: UserLocation?) {
        val app = context.applicationContext
        val running = state != null && state.phase != MonitorPhase.OFF
        val lastFrame = state?.sources?.mapNotNull { it.lastFrameAt }?.maxOrNull()
        val samples = synchronized(heartbeatSamples) { heartbeatSamples.toList() }
        executor.execute {
            try {
                val now = now()
                if (now - lastPruneAt > PRUNE_EVERY_MS) prune(now)
                val metrics = compute(app, now, running, lastFrame, samples, location)
                Diagnostics.updateMetrics { metrics }
                Diagnostics.setAvailability(HealthMath.slots(timeline(now), now))
                if (running && now - lastSnapshotAt > SNAPSHOT_EVERY_MS) {
                    saveSnapshot(metrics, now)
                    prefs(app).edit().putLong(KEY_ALIVE_AT, now).apply()
                }
            } catch (e: Exception) {
                Log.w(TAG, "publish failed", e)
            }
        }
    }

    /** Persists the metrics snapshot now (service stop). */
    fun flushSnapshot() {
        executor.execute { saveSnapshot(Diagnostics.metrics.value, now()) }
    }

    /**
     * Copies of the persisted files for the diagnostics bundle, after all queued writes:
     * events, log and metrics. Waits at most 3 s.
     */
    fun exportFiles(): List<File> {
        val folder = dir ?: return emptyList()
        try {
            executor.submit {}.get(3, TimeUnit.SECONDS)
        } catch (e: Exception) {
            Log.w(TAG, "export: queue not drained", e)
        }
        return listOf(EVENTS_FILE, LOG_FILE, METRICS_FILE).map { File(folder, it) }.filter { it.exists() }
    }

    private fun timeline(now: Long): List<HealthMath.Interval> {
        val from = now - HealthMath.WINDOW_MS - HealthMath.SLOT_MS
        val transitions = events.filter { it.k == K_LINK }
            .mapNotNull { e -> enumValueOrNull<SlotStatus>(e.d)?.let { e.t to it } }
        return HealthMath.timeline(transitions, from, now)
    }

    private fun compute(
        app: Context,
        now: Long,
        running: Boolean,
        lastFrame: Long?,
        samples: List<Int>,
        location: UserLocation?,
    ): HealthMetrics {
        val dayAgo = now - 24 * 3_600_000L
        val window = timeline(now).mapNotNull { iv ->
            val start = maxOf(iv.start, dayAgo)
            if (iv.end <= start) null else iv.copy(start = start)
        }
        val reports = events.filter { it.k == K_REPORT && it.t >= dayAgo }
        val latencies = reports.mapNotNull { it.v?.coerceAtLeast(0L) }
        val midnight = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val snapshot = if (!running) loadSnapshot() else null
        val loc = location ?: (app as? MyEarthQuakeAlertApp)?.locationProvider?.cached()
        return HealthMetrics(
            availability24h = HealthMath.availability(window),
            reconnects24h = events.count { it.k == K_RECONNECT && it.t >= dayAgo },
            longestOutageMs = HealthMath.longestOutage(window),
            heartbeatAgeMs = if (running && lastFrame != null) (now - lastFrame).coerceAtLeast(0L) else null,
            heartbeatSamplesSec = samples.ifEmpty { snapshot?.heartbeatSamplesSec.orEmpty() },
            latencyP50Ms = HealthMath.percentile(latencies, 50.0),
            latencyP95Ms = HealthMath.percentile(latencies, 95.0),
            reportsToday = reports.count { it.t >= midnight },
            clockOffsetMs = ClockSync.currentOffsetMs(),
            clockCheckedAt = ClockSync.checkedAt,
            locationSource = loc?.source ?: snapshot?.locationSource ?: UserLocation.SOURCE_NONE,
            locationAccuracyM = loc?.accuracyM ?: snapshot?.locationAccuracyM,
            locationAgeMs = loc?.ageMs(now),
            batteryOptimizationExempt = batteryExempt(app),
            standbyBucket = standbyBucket(app),
            dataSaverOn = dataSaverOn(app),
            watchdogLastRunAt = WatchdogWorker.lastRunAt(app),
            watchdogNextRunAt = WatchdogWorker.nextRunAt(app),
            bootStartDelaySec = BootStartRecord.delaySecondsThisBoot(app),
            pipelineParseMs = pipelineParseMs ?: snapshot?.pipelineParseMs,
            pipelineEvaluateMs = pipelineEvaluateMs ?: snapshot?.pipelineEvaluateMs,
            endToEndMs = endToEndMs ?: snapshot?.endToEndMs,
        )
    }

    // ── System limits (系统限制) ─────────────────────────────────────────────

    fun batteryExempt(context: Context): Boolean? = try {
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName)
    } catch (e: Exception) {
        null
    }

    /** App standby bucket name (API 28+), e.g. ACTIVE, WORKING_SET, RARE, RESTRICTED. */
    fun standbyBucket(context: Context): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        return try {
            val bucket = context.getSystemService(UsageStatsManager::class.java)?.appStandbyBucket ?: return null
            when (bucket) {
                5 -> "EXEMPTED"
                UsageStatsManager.STANDBY_BUCKET_ACTIVE -> "ACTIVE"
                UsageStatsManager.STANDBY_BUCKET_WORKING_SET -> "WORKING_SET"
                UsageStatsManager.STANDBY_BUCKET_FREQUENT -> "FREQUENT"
                UsageStatsManager.STANDBY_BUCKET_RARE -> "RARE"
                45 -> "RESTRICTED" // STANDBY_BUCKET_RESTRICTED, API 30
                else -> "BUCKET_$bucket"
            }
        } catch (e: Exception) {
            null
        }
    }

    /** True when Data Saver restricts this app's background data (API 24+). */
    fun dataSaverOn(context: Context): Boolean? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return null
        return try {
            context.getSystemService(ConnectivityManager::class.java)?.restrictBackgroundStatus ==
                ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
        } catch (e: Exception) {
            null
        }
    }

    // ── Persistence (executor thread) ───────────────────────────────────────

    private fun loadEvents(app: Context) {
        val folder = dir ?: return
        val file = File(folder, EVENTS_FILE)
        val now = now()
        val all = readLines(file) { json.decodeFromString(HealthEvent.serializer(), it) }
        val kept = all.filter { now - it.t < RETENTION_MS }
        events.clear()
        events.addAll(kept.sortedBy { it.t })
        if (kept.size != all.size) rewriteEvents()
        lastPruneAt = now

        // The process ended without a clean stop (killed): close the open interval at the
        // last moment the service was known alive.
        val lastLink = events.lastOrNull { it.k == K_LINK }
        if (lastLink != null && lastLink.d != SlotStatus.OFF.name) {
            val aliveAt = prefs(app).getLong(KEY_ALIVE_AT, 0L)
            val closeAt = maxOf(aliveAt, events.last().t)
            val off = HealthEvent(closeAt, K_LINK, d = SlotStatus.OFF.name)
            val phase = HealthEvent(closeAt, K_PHASE, d = MonitorPhase.OFF.name)
            events += off
            events += phase
            append(file, json.encodeToString(HealthEvent.serializer(), off))
            append(file, json.encodeToString(HealthEvent.serializer(), phase))
        }
    }

    private fun prune(now: Long) {
        lastPruneAt = now
        val before = events.size
        events.removeAll { now - it.t >= RETENTION_MS }
        if (events.size != before) rewriteEvents()
    }

    private fun rewriteEvents() {
        val folder = dir ?: return
        writeAtomically(File(folder, EVENTS_FILE), events.joinToString("") { json.encodeToString(HealthEvent.serializer(), it) + "\n" })
    }

    private fun persistNewLog(list: List<LogEntry>) {
        val folder = dir ?: return
        if (list.isEmpty()) return
        val last = lastPersistedLog
        val fresh = when {
            last == null -> list
            else -> {
                val index = list.indexOfLast { it === last }
                if (index >= 0) list.subList(index + 1, list.size) else list.filter { it.time > last.time }
            }
        }
        if (fresh.isEmpty()) return
        val file = File(folder, LOG_FILE)
        append(file, fresh.joinToString("\n") { encodeLog(it) })
        lastPersistedLog = list.last()
        logLines += fresh.size
        if (logLines > LOG_COMPACT_AT) rewriteLog(list)
    }

    private fun rewriteLog(entries: List<LogEntry>) {
        val folder = dir ?: return
        val keep = entries.takeLast(LOG_KEEP)
        writeAtomically(File(folder, LOG_FILE), keep.joinToString("") { encodeLog(it) + "\n" })
        logLines = keep.size
    }

    private fun encodeLog(e: LogEntry) = json.encodeToString(LogLine.serializer(), LogLine(e.time, e.level.name, e.tag, e.message))

    private fun saveSnapshot(metrics: HealthMetrics, now: Long) {
        val folder = dir ?: return
        lastSnapshotAt = now
        val snapshot = Snapshot(
            savedAt = now,
            heartbeatSamplesSec = metrics.heartbeatSamplesSec,
            pipelineParseMs = metrics.pipelineParseMs,
            pipelineEvaluateMs = metrics.pipelineEvaluateMs,
            endToEndMs = metrics.endToEndMs,
            heartbeatAgeMs = metrics.heartbeatAgeMs,
            locationSource = metrics.locationSource,
            locationAccuracyM = metrics.locationAccuracyM,
        )
        writeAtomically(File(folder, METRICS_FILE), json.encodeToString(Snapshot.serializer(), snapshot))
    }

    private fun loadSnapshot(): Snapshot? {
        val folder = dir ?: return null
        val file = File(folder, METRICS_FILE)
        if (!file.exists()) return null
        return try {
            json.decodeFromString(Snapshot.serializer(), file.readText())
        } catch (e: Exception) {
            null
        }
    }

    private fun append(file: File, lines: String) {
        try {
            file.appendText(lines + "\n")
        } catch (e: Exception) {
            Log.w(TAG, "append ${file.name} failed", e)
        }
    }

    private fun writeAtomically(file: File, text: String) {
        try {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(file)) {
                file.writeText(text)
                tmp.delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "write ${file.name} failed", e)
        }
    }

    private fun <T> readLines(file: File, decode: (String) -> T): List<T> {
        if (!file.exists()) return emptyList()
        return try {
            file.readLines().mapNotNull { line ->
                if (line.isBlank()) null else try {
                    decode(line)
                } catch (e: Exception) {
                    null // a torn last line after a crash
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "read ${file.name} failed", e)
            emptyList()
        }
    }

    private fun prefs(context: Context): SharedPreferences =
        context.deviceProtectedContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private inline fun <reified T : Enum<T>> enumValueOrNull(name: String?): T? =
        enumValues<T>().firstOrNull { it.name == name }
}
