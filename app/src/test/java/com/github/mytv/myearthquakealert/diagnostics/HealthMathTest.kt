package com.github.mytv.myearthquakealert.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HealthMathTest {

    private val minute = 60_000L
    private val day0 = 1_700_000_100_000L - 1_700_000_100_000L % HealthMath.SLOT_MS // a slot boundary

    @Test
    fun timeline_startsOffAndMergesRepeats() {
        val t = HealthMath.timeline(
            listOf(day0 + 10 * minute to SlotStatus.UP, day0 + 20 * minute to SlotStatus.UP, day0 + 30 * minute to SlotStatus.DOWN),
            day0, day0 + 40 * minute,
        )
        assertEquals(
            listOf(
                HealthMath.Interval(day0, day0 + 10 * minute, SlotStatus.OFF),
                HealthMath.Interval(day0 + 10 * minute, day0 + 30 * minute, SlotStatus.UP),
                HealthMath.Interval(day0 + 30 * minute, day0 + 40 * minute, SlotStatus.DOWN),
            ),
            t,
        )
    }

    @Test
    fun timeline_usesStatusBeforeWindow() {
        val t = HealthMath.timeline(listOf(day0 - minute to SlotStatus.DEGRADED), day0, day0 + minute)
        assertEquals(listOf(HealthMath.Interval(day0, day0 + minute, SlotStatus.DEGRADED)), t)
    }

    @Test
    fun slotStart_alignsToQuarterHours() {
        assertEquals(day0, HealthMath.slotStart(day0 + 14 * minute))
        assertEquals(day0 + HealthMath.SLOT_MS, HealthMath.slotStart(day0 + HealthMath.SLOT_MS))
    }

    @Test
    fun slots_classifyEachCell() {
        val now = day0 + 4 * HealthMath.SLOT_MS - 1
        val s = HealthMath.SLOT_MS
        val timeline = listOf(
            HealthMath.Interval(day0, day0 + s, SlotStatus.UP), // cell 0: up
            HealthMath.Interval(day0 + s, day0 + s + 20_000, SlotStatus.DOWN), // cell 1: 20 s down → degraded
            HealthMath.Interval(day0 + s + 20_000, day0 + 2 * s, SlotStatus.UP),
            HealthMath.Interval(day0 + 2 * s, day0 + 2 * s + 10 * minute, SlotStatus.DOWN), // cell 2: mostly down
            HealthMath.Interval(day0 + 2 * s + 10 * minute, day0 + 3 * s, SlotStatus.UP),
            HealthMath.Interval(day0 + 3 * s, now, SlotStatus.OFF), // cell 3: off
        )
        val slots = HealthMath.slots(timeline, now)
        assertEquals(HealthMath.SLOTS, slots.size)
        val last4 = slots.takeLast(4).map { it.status }
        assertEquals(listOf(SlotStatus.UP, SlotStatus.DEGRADED, SlotStatus.DOWN, SlotStatus.OFF), last4)
        assertEquals(day0 + 3 * s, slots.last().start)
        assertEquals(SlotStatus.OFF, slots.first().status)
    }

    @Test
    fun availabilityAndOutage() {
        val timeline = listOf(
            HealthMath.Interval(0, 600, SlotStatus.UP),
            HealthMath.Interval(600, 700, SlotStatus.DOWN),
            HealthMath.Interval(700, 800, SlotStatus.DEGRADED),
            HealthMath.Interval(800, 900, SlotStatus.DOWN),
            HealthMath.Interval(900, 1000, SlotStatus.DOWN),
            HealthMath.Interval(1000, 2000, SlotStatus.OFF),
        )
        assertEquals(0.7, HealthMath.availability(timeline)!!, 1e-9)
        assertEquals(200L, HealthMath.longestOutage(timeline))
        assertNull(HealthMath.availability(listOf(HealthMath.Interval(0, 10, SlotStatus.OFF))))
    }

    @Test
    fun percentile_nearestRank() {
        val values = listOf(15L, 20L, 35L, 40L, 50L)
        assertEquals(35L, HealthMath.percentile(values, 50.0))
        assertEquals(50L, HealthMath.percentile(values, 95.0))
        assertEquals(15L, HealthMath.percentile(values, 0.0))
        assertNull(HealthMath.percentile(emptyList(), 50.0))
    }

    @Test
    fun clockEstimate_usesHalfTheRoundTrip() {
        val s = ClockSync.estimate(t0 = 10_000L, t1 = 10_200L, server = 9_500L)
        assertEquals(200L, s.roundTripMs)
        assertEquals(9_600L, s.serverMs)
        assertEquals(600L, s.offsetMs) // local clock 0.6 s fast
        assertEquals("+0.60", ClockSync.formatSeconds(s.offsetMs))
        assertEquals("-1.25", ClockSync.formatSeconds(-1_250L))
    }
}
