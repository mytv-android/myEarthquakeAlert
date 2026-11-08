package com.github.mytv.myearthquakealert.diagnostics

import android.content.Context
import android.os.SystemClock
import com.github.mytv.myearthquakealert.data.api.WolfxApi
import com.github.mytv.myearthquakealert.util.deviceProtectedContext
import kotlin.math.abs

/**
 * Clock correction against `api.wolfx.jp/ntp.json` (design §5 时钟偏差).
 *
 * A sync takes a few samples and keeps the one with the shortest round trip: the server's
 * timestamp is assumed to be taken half-way through it. The result is anchored to
 * [SystemClock.elapsedRealtime], so [now] stays correct even if the user changes the wall
 * clock after the sync. Report latency and alert countdowns use [now].
 */
object ClockSync {

    private const val PREFS = "clock_sync"
    private const val KEY_OFFSET = "offset_ms"
    private const val KEY_CHECKED_AT = "checked_at"
    private const val KEY_ANCHOR_SERVER = "anchor_server"
    private const val KEY_ANCHOR_ELAPSED = "anchor_elapsed"
    private const val KEY_BOOT_WALL = "boot_wall"
    private const val SAMPLES = 3

    /** Thresholds of the 指标定义与阈值 table. */
    const val OK_MS = 500L
    const val WARN_MS = 2_000L

    /** Re-sync period while monitoring. */
    const val INTERVAL_MS = 30 * 60_000L

    /** Server time at [anchorElapsed]; 0 until the first sync of this boot. */
    @Volatile private var anchorServer = 0L
    @Volatile private var anchorElapsed = 0L

    /** Local clock minus server clock at the last sync; positive = local clock is fast. */
    @Volatile var offsetMs: Long? = null
        private set

    /** Wall time of the last successful sync. */
    @Volatile var checkedAt: Long? = null
        private set

    /** Corrected epoch millis (server time); the local clock until the first sync. */
    fun now(): Long {
        val server = anchorServer
        if (server == 0L) return System.currentTimeMillis()
        return server + (SystemClock.elapsedRealtime() - anchorElapsed)
    }

    /** Current deviation of the local wall clock from [now]. */
    fun currentOffsetMs(): Long? = if (anchorServer == 0L) offsetMs else System.currentTimeMillis() - now()

    data class Sample(val offsetMs: Long, val roundTripMs: Long, val serverMs: Long, val localMs: Long)

    /**
     * Offset estimate from one request: sent at local [t0], answered at local [t1] with
     * server time [server]; the server is assumed to have stamped the reply at the midpoint.
     */
    fun estimate(t0: Long, t1: Long, server: Long): Sample {
        val rtt = (t1 - t0).coerceAtLeast(0L)
        val serverAtT1 = server + rtt / 2
        return Sample(offsetMs = t1 - serverAtT1, roundTripMs = rtt, serverMs = serverAtT1, localMs = t1)
    }

    /** Restores the last result (shown in diagnostics; used for [now] only if the clock did not change since). */
    fun init(context: Context) {
        val p = prefs(context)
        if (!p.contains(KEY_OFFSET)) return
        offsetMs = p.getLong(KEY_OFFSET, 0L)
        checkedAt = p.getLong(KEY_CHECKED_AT, 0L)
        val bootWall = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        val savedBootWall = p.getLong(KEY_BOOT_WALL, Long.MIN_VALUE)
        // Same boot and no wall-clock change since: the elapsed-realtime anchor is still valid.
        if (savedBootWall != Long.MIN_VALUE && abs(bootWall - savedBootWall) < 1_000L) {
            val elapsed = p.getLong(KEY_ANCHOR_ELAPSED, 0L)
            if (elapsed in 1..SystemClock.elapsedRealtime()) {
                anchorElapsed = elapsed
                anchorServer = p.getLong(KEY_ANCHOR_SERVER, 0L)
            }
        }
    }

    /** Syncs now; returns the chosen sample, or null when ntp.json is unreachable. */
    suspend fun sync(context: Context, api: WolfxApi): Sample? {
        var best: Sample? = null
        var lastError: Exception? = null
        repeat(SAMPLES) {
            try {
                val t0 = System.currentTimeMillis()
                val response = api.getNtp()
                val t1 = System.currentTimeMillis()
                if (response.timestamp > 0) {
                    val sample = estimate(t0, t1, response.timestamp)
                    if (best == null || sample.roundTripMs < best!!.roundTripMs) best = sample
                }
            } catch (e: Exception) {
                lastError = e
            }
        }
        val chosen = best
        if (chosen == null) {
            Diagnostics.log(HealthLevel.WARN, LogTags.CLOCK, "ntp.json: ${lastError?.message ?: "no timestamp"}")
            return null
        }
        apply(context, chosen)
        val level = when {
            abs(chosen.offsetMs) <= OK_MS -> HealthLevel.OK
            abs(chosen.offsetMs) <= WARN_MS -> HealthLevel.WARN
            else -> HealthLevel.ERROR
        }
        Diagnostics.log(level, LogTags.CLOCK, "offset ${formatSeconds(chosen.offsetMs)} s · rtt ${chosen.roundTripMs} ms")
        HealthRecorder.recordClock(chosen.offsetMs)
        return chosen
    }

    private fun apply(context: Context, sample: Sample) {
        val elapsedNow = SystemClock.elapsedRealtime()
        // Anchor at the moment of the reply.
        anchorElapsed = elapsedNow - (System.currentTimeMillis() - sample.localMs)
        anchorServer = sample.serverMs
        offsetMs = sample.offsetMs
        checkedAt = sample.localMs
        prefs(context).edit()
            .putLong(KEY_OFFSET, sample.offsetMs)
            .putLong(KEY_CHECKED_AT, sample.localMs)
            .putLong(KEY_ANCHOR_SERVER, anchorServer)
            .putLong(KEY_ANCHOR_ELAPSED, anchorElapsed)
            .putLong(KEY_BOOT_WALL, System.currentTimeMillis() - elapsedNow)
            .apply()
        Diagnostics.updateMetrics { it.copy(clockOffsetMs = sample.offsetMs, clockCheckedAt = sample.localMs) }
    }

    /** "+0.21" style seconds with sign. */
    fun formatSeconds(ms: Long): String {
        val sign = if (ms >= 0) "+" else "-"
        return sign + String.format(java.util.Locale.US, "%.2f", abs(ms) / 1000.0)
    }

    private fun prefs(context: Context) =
        context.deviceProtectedContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
