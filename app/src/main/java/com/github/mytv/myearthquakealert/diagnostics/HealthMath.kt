package com.github.mytv.myearthquakealert.diagnostics

import kotlin.math.ceil

/**
 * Pure computations behind the 诊断 page's health overview: the 24 h link timeline,
 * the 96 availability cells, availability, longest outage and latency percentiles.
 * Free of Android APIs so it is unit tested on the JVM.
 */
object HealthMath {

    const val SLOT_MS = 15 * 60_000L
    const val SLOTS = 96
    const val WINDOW_MS = SLOT_MS * SLOTS

    /** A cell is DEGRADED once this much of its on-time was down or on the fallback. */
    const val DEGRADED_MIN_MS = 10_000L

    /** Less on-time than this in a cell counts as OFF (e.g. switched on at its very end). */
    const val OFF_MAX_ON_MS = 5_000L

    /** Link status over [start, end). UP = a socket is connected, DEGRADED = HTTP fallback only, DOWN = none, OFF = monitoring off or paused. */
    data class Interval(val start: Long, val end: Long, val status: SlotStatus) {
        val length: Long get() = end - start
    }

    /**
     * The link timeline over [from, to] built from status [transitions] (time, new status),
     * in any order. The status before the first transition is OFF.
     */
    fun timeline(transitions: List<Pair<Long, SlotStatus>>, from: Long, to: Long): List<Interval> {
        if (to <= from) return emptyList()
        val sorted = transitions.sortedBy { it.first }
        var status = SlotStatus.OFF
        for ((t, s) in sorted) if (t <= from) status = s else break
        val out = ArrayList<Interval>()
        var cursor = from
        for ((t, s) in sorted) {
            if (t <= from) continue
            if (t >= to) break
            if (s == status) continue
            if (t > cursor) out += Interval(cursor, t, status)
            cursor = t
            status = s
        }
        out += Interval(cursor, to, status)
        return out
    }

    /** Start of the 15-minute cell containing [time] (cells are aligned to the epoch, i.e. :00/:15/:30/:45 in whole-hour zones). */
    fun slotStart(time: Long): Long = time - time.mod(SLOT_MS)

    /**
     * The [SLOTS] cells ending with the one containing [now], oldest first, classified from
     * the time spent in each status:
     * OFF when on for less than [OFF_MAX_ON_MS]; DOWN when down for more than half of the
     * on-time; DEGRADED when down or on the fallback for at least [DEGRADED_MIN_MS]; else UP.
     */
    fun slots(timeline: List<Interval>, now: Long): List<AvailabilitySlot> {
        val lastStart = slotStart(now)
        val firstStart = lastStart - (SLOTS - 1) * SLOT_MS
        return List(SLOTS) { i ->
            val start = firstStart + i * SLOT_MS
            val end = minOf(start + SLOT_MS, now)
            var up = 0L
            var degraded = 0L
            var down = 0L
            for (iv in timeline) {
                val overlap = minOf(iv.end, end) - maxOf(iv.start, start)
                if (overlap <= 0) continue
                when (iv.status) {
                    SlotStatus.UP -> up += overlap
                    SlotStatus.DEGRADED -> degraded += overlap
                    SlotStatus.DOWN -> down += overlap
                    SlotStatus.OFF -> Unit
                }
            }
            val on = up + degraded + down
            val status = when {
                on < OFF_MAX_ON_MS -> SlotStatus.OFF
                down * 2 > on -> SlotStatus.DOWN
                down + degraded >= DEGRADED_MIN_MS -> SlotStatus.DEGRADED
                else -> SlotStatus.UP
            }
            AvailabilitySlot(start, status)
        }
    }

    /** Connected (UP or fallback) time over monitoring-on time; null when monitoring was never on. */
    fun availability(timeline: List<Interval>): Double? {
        var on = 0L
        var available = 0L
        for (iv in timeline) {
            if (iv.status == SlotStatus.OFF) continue
            on += iv.length
            if (iv.status != SlotStatus.DOWN) available += iv.length
        }
        return if (on <= 0L) null else available.toDouble() / on
    }

    /** Longest uninterrupted DOWN stretch. */
    fun longestOutage(timeline: List<Interval>): Long {
        var longest = 0L
        var run = 0L
        for (iv in timeline) {
            if (iv.status == SlotStatus.DOWN) {
                run += iv.length
                if (run > longest) longest = run
            } else {
                run = 0L
            }
        }
        return longest
    }

    /** Nearest-rank percentile ([p] in 0..100); null for no values. */
    fun percentile(values: Collection<Long>, p: Double): Long? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val rank = ceil(p / 100.0 * sorted.size).toInt().coerceIn(1, sorted.size)
        return sorted[rank - 1]
    }
}
