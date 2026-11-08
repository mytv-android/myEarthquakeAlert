package com.github.mytv.myearthquakealert.service

import com.github.mytv.myearthquakealert.data.model.EewEvent
import com.github.mytv.myearthquakealert.diagnostics.SlotStatus

/**
 * The pure rules of the monitoring state machine (design §3 服务状态机 / 保活与恢复机制),
 * kept free of Android APIs so they are unit tested on the JVM.
 */
object MonitorRules {

    /** No frame of any kind for this long ⇒ the link is dead ⇒ close and reconnect. */
    const val LIVENESS_TIMEOUT_MS = 90_000L

    /** WebSocket handshake must complete within this. */
    const val HANDSHAKE_TIMEOUT_MS = 15_000L

    /** Text `ping` cadence on open sockets; the feed answers with a JSON pong frame. */
    const val APP_PING_INTERVAL_MS = 30_000L

    /** Consecutive WebSocket failures after which a source also polls over HTTP. */
    const val FALLBACK_AFTER_FAILURES = 3

    /** Reports whose ReportTime is older than this are history (e.g. the first poll), never alerted. */
    const val STALE_REPORT_MS = 10 * 60_000L

    private val BACKOFF_MS = longArrayOf(2_000, 4_000, 8_000, 16_000, 30_000)

    /**
     * Reconnect delay before attempt [attempt] (1-based): 2/4/8/16/30 s, capped at 30 s.
     * The attempt counter resets once a connection delivers its first frame.
     */
    fun backoffMs(attempt: Int): Long = BACKOFF_MS[(attempt.coerceAtLeast(1) - 1).coerceAtMost(BACKOFF_MS.lastIndex)]

    /**
     * The phase shown on every surface, from the service's situation:
     * 1. not running → OFF; 2. paused → PAUSED; 3. a *required* delivery requirement is
     * missing → LIMITED (running, but alerts may not be delivered);
     * then from the links: any socket CONNECTED → MONITORING (other sources may be
     * reconnecting — the connected ones cover); else any source served by the HTTP fallback →
     * DEGRADED; else any source RECONNECTING (it failed at least once) → RECONNECTING;
     * else (still on the first handshake) → CONNECTING.
     */
    fun derivePhase(
        running: Boolean,
        paused: Boolean,
        sources: List<SourceHealth>,
        missing: Set<DeliveryRequirement>,
    ): MonitorPhase = when {
        !running -> MonitorPhase.OFF
        paused -> MonitorPhase.PAUSED
        missing.any { it.required } -> MonitorPhase.LIMITED
        sources.any { it.link == LinkState.CONNECTED } -> MonitorPhase.MONITORING
        sources.any { it.link == LinkState.POLLING } -> MonitorPhase.DEGRADED
        sources.any { it.link == LinkState.RECONNECTING } -> MonitorPhase.RECONNECTING
        else -> MonitorPhase.CONNECTING
    }

    /**
     * Link status for the availability record. Independent of LIMITED: availability is about
     * receiving reports (已连接时长 / 监测开启时长).
     */
    fun linkStatus(running: Boolean, paused: Boolean, sources: List<SourceHealth>): SlotStatus = when {
        !running || paused -> SlotStatus.OFF
        sources.any { it.link == LinkState.CONNECTED } -> SlotStatus.UP
        sources.any { it.link == LinkState.POLLING } -> SlotStatus.DEGRADED
        else -> SlotStatus.DOWN
    }

    /** True when the report is older than [STALE_REPORT_MS] at [now] (corrected epoch millis). */
    fun isStale(reportAt: Long?, now: Long): Boolean = reportAt != null && now - reportAt > STALE_REPORT_MS
}

/**
 * Cross-source de-duplication (design: 多选同时订阅，按 EventID 去重并保留最新报数).
 * The first arrival of a given report wins; the same report from another source (or the
 * poller) is a duplicate that only refreshes that source's health; older report numbers
 * are ignored. A final report ends the event: later reports of it are duplicates.
 */
class ReportDeduper(private val retentionMs: Long = 3_600_000L) {

    enum class Verdict { NEW_EVENT, NEWER_REPORT, DUPLICATE }

    private data class Seen(val reportNum: Int, val final: Boolean, val at: Long)

    private val seen = LinkedHashMap<String, Seen>()

    fun accept(event: EewEvent, now: Long): Verdict {
        prune(now)
        val previous = seen[event.eventId]
        val verdict = when {
            previous == null -> Verdict.NEW_EVENT
            previous.final -> Verdict.DUPLICATE
            event.reportNum > previous.reportNum -> Verdict.NEWER_REPORT
            // Same number but now marked final (Fujian): take it as the newer report.
            event.reportNum == previous.reportNum && event.isFinal -> Verdict.NEWER_REPORT
            else -> Verdict.DUPLICATE
        }
        if (verdict != Verdict.DUPLICATE) seen[event.eventId] = Seen(event.reportNum, event.isFinal, now)
        return verdict
    }

    /** Forgets every event (monitoring restarted). */
    fun clear() = seen.clear()

    /** Newest report number accepted for [eventId], or null. */
    fun latestReportNum(eventId: String): Int? = seen[eventId]?.reportNum

    private fun prune(now: Long) {
        val it = seen.entries.iterator()
        while (it.hasNext()) if (now - it.next().value.at > retentionMs) it.remove()
    }
}
