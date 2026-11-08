package com.github.mytv.myearthquakealert.service

import android.content.Context
import android.util.Log
import com.github.mytv.myearthquakealert.data.model.EewEvent
import com.github.mytv.myearthquakealert.util.deviceProtectedContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.Executors

/** One early-warning event as received live (newest report), for the 地震 list's 预警记录. */
@Serializable
data class EewRecord(
    val event: EewEvent,
    /** Highest tier the event reached here (RECORD = below the reminder thresholds). */
    val tier: AlertTier,
    val localCsis: Double,
    val distanceKm: Double,
    val firstReceivedAt: Long,
    val updatedAt: Long,
    /** Reports received for the event (newest report number). */
    val reports: Int,
)

/**
 * Early-warning reports received by the service, de-duplicated per event (design: 记录 —
 * 只写入「地震」列表). Drills are not recorded. Kept 7 days / 200 events in
 * device-protected storage; loaded at process start.
 */
object EewHistory {

    private const val FILE = "eew_history.json"
    private const val MAX = 200
    private const val RETENTION_MS = 7 * 24 * 3_600_000L

    private val json = Json { ignoreUnknownKeys = true }
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "eew-history").apply { isDaemon = true } }
    @Volatile private var file: File? = null

    private val _records = MutableStateFlow<List<EewRecord>>(emptyList())
    /** Newest first. */
    val records: StateFlow<List<EewRecord>> = _records.asStateFlow()

    fun init(context: Context) {
        if (file != null) return
        val f = File(context.deviceProtectedContext().filesDir, FILE)
        file = f
        io.execute {
            if (!f.exists()) return@execute
            try {
                val now = System.currentTimeMillis()
                val loaded = json.decodeFromString(ListSerializer(EewRecord.serializer()), f.readText())
                    .filter { now - it.updatedAt < RETENTION_MS }
                synchronized(this) {
                    val current = _records.value
                    _records.value = (current + loaded.filter { l -> current.none { it.event.eventId == l.event.eventId } })
                        .sortedByDescending { it.firstReceivedAt }
                }
            } catch (e: Exception) {
                Log.w("EewHistory", "load failed", e)
            }
        }
    }

    /** Adds or updates the event of [event]; the tier only ever rises. */
    fun record(event: EewEvent, tier: AlertTier, localCsis: Double, distanceKm: Double, now: Long = System.currentTimeMillis()) {
        val list = synchronized(this) {
            val current = _records.value
            val existing = current.firstOrNull { it.event.eventId == event.eventId }
            val updated = if (existing == null) {
                EewRecord(event, tier, localCsis, distanceKm, now, now, event.reportNum)
            } else {
                existing.copy(
                    event = if (event.reportNum >= existing.event.reportNum) event else existing.event,
                    tier = if (tier > existing.tier) tier else existing.tier,
                    localCsis = localCsis,
                    distanceKm = distanceKm,
                    updatedAt = now,
                    reports = maxOf(existing.reports, event.reportNum),
                )
            }
            val next = (listOf(updated) + current.filter { it.event.eventId != event.eventId })
                .sortedByDescending { it.firstReceivedAt }
                .take(MAX)
            _records.value = next
            next
        }
        val f = file ?: return
        io.execute {
            try {
                val tmp = File(f.parentFile, f.name + ".tmp")
                tmp.writeText(json.encodeToString(ListSerializer(EewRecord.serializer()), list))
                if (!tmp.renameTo(f)) {
                    f.writeText(tmp.readText())
                    tmp.delete()
                }
            } catch (e: Exception) {
                Log.w("EewHistory", "save failed", e)
            }
        }
    }
}
