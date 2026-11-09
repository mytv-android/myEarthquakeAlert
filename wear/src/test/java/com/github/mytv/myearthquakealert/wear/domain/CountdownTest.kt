package com.github.mytv.myearthquakealert.wear.domain

import com.github.mytv.myearthquakealert.wear.data.WireAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CountdownTest {

    private val origin = 1_654_074_008_000L
    private val wire = WireAlert(
        eventId = "e1", source = "SICHUAN", sourceLabel = "四川地震局", place = "四川雅安市芦山县",
        magnitude = 6.1, depthKm = 17.0, latitude = 30.37, longitude = 102.94,
        userLatitude = 30.64, userLongitude = 104.04, originAt = origin,
        pWaveSeconds = 18.5, sWaveSeconds = 31.25, localCsis = 4.2, distanceKm = 110.5,
        tier = "STRONG", presentedAt = origin + 6_400L, sentAt = origin + 6_500L,
    )

    @Test
    fun syncedClocks_keepPhoneTimes() {
        // 300 ms transport latency on synchronised clocks: no shift.
        val a = Countdown.fromPhone(wire, receivedAt = origin + 6_800L)
        assertEquals(origin, a.originAt)
        assertEquals(origin + 31_250L, a.sArrivalAt)
        assertEquals(origin + 18_500L, a.pArrivalAt)
        assertEquals(origin + 6_800L, a.presentedAt)
        assertTrue(a.strong)
    }

    @Test
    fun watchAhead_isTakenAsLatency() {
        // Received 20 s after it was sent: never counted down longer than the phone would.
        val receivedAt = wire.sentAt + 20_000L
        val a = Countdown.fromPhone(wire, receivedAt)
        assertEquals(origin, a.originAt)
        assertEquals(5, Countdown.snapshot(a, receivedAt).sSeconds) // 31.25 − 26.5 = 4.75 → 5
    }

    @Test
    fun watchBehind_isCorrected() {
        // Watch clock 20 s behind the phone: shifted onto the watch clock.
        val receivedAt = wire.sentAt - 20_000L
        val a = Countdown.fromPhone(wire, receivedAt)
        assertEquals(origin - 20_000L, a.originAt)
        assertEquals(origin - 20_000L + 31_250L, a.sArrivalAt)
        // Seconds left are the same as on the phone at send time.
        assertEquals(25, Countdown.snapshot(a, receivedAt).sSeconds) // 31.25 − 6.5 = 24.75 → 25
    }

    @Test
    fun clockOffset_toleranceAndMissingSentAt() {
        assertEquals(0L, Countdown.clockOffset(sentAt = 1_000L, receivedAt = 5_900L))
        assertEquals(0L, Countdown.clockOffset(sentAt = 1_000L, receivedAt = 60_000L))
        assertEquals(0L, Countdown.clockOffset(sentAt = 10_000L, receivedAt = 6_000L))
        assertEquals(-7_000L, Countdown.clockOffset(sentAt = 10_000L, receivedAt = 3_000L))
        assertEquals(0L, Countdown.clockOffset(sentAt = 0L, receivedAt = 99_999L))
    }

    @Test
    fun furtherReport_keepsPresentationTime() {
        val first = Countdown.fromPhone(wire, receivedAt = origin + 6_800L)
        val second = Countdown.fromPhone(wire.copy(reportNum = 2, sWaveSeconds = 32.0, sentAt = origin + 9_000L), origin + 9_200L, first)
        assertEquals(first.presentedAt, second.presentedAt)
        val other = Countdown.fromPhone(wire.copy(eventId = "e2"), origin + 9_200L, first)
        assertEquals(origin + 9_200L, other.presentedAt)
    }

    @Test
    fun snapshot_secondsRoundUpAndRingDrains() {
        val a = Countdown.fromPhone(wire, receivedAt = origin + 6_250L)
        val atStart = Countdown.snapshot(a, a.presentedAt)
        assertEquals(Countdown.Stage.COUNTDOWN, atStart.stage)
        assertEquals(25, atStart.sSeconds) // 25.0 s exactly
        assertEquals(1f, atStart.fraction, 1e-6f)
        assertFalse(atStart.pArrived)

        val half = Countdown.snapshot(a, a.presentedAt + 12_500L)
        assertEquals(0.5f, half.fraction, 1e-6f)
        assertEquals(13, half.sSeconds) // 12.5 → 13
        assertTrue(half.pArrived) // P at 18.5 s after origin, now 18.75 s

        val oneMsLeft = Countdown.snapshot(a, a.sArrivalAt - 1)
        assertEquals(1, oneMsLeft.sSeconds)
    }

    @Test
    fun snapshot_stagesAfterArrival() {
        val a = Countdown.fromPhone(wire, receivedAt = origin + 6_500L)
        assertEquals(Countdown.Stage.ARRIVED, Countdown.snapshot(a, a.sArrivalAt).stage)
        assertEquals(0, Countdown.snapshot(a, a.sArrivalAt).sSeconds)
        assertEquals(0f, Countdown.snapshot(a, a.sArrivalAt + 1).fraction, 0f)
        assertEquals(Countdown.Stage.ARRIVED, Countdown.snapshot(a, a.sArrivalAt + 59_999L).stage)
        assertEquals(Countdown.Stage.SUMMARY, Countdown.snapshot(a, a.sArrivalAt + 60_000L).stage)
        assertEquals(Countdown.Stage.EXPIRED, Countdown.snapshot(a, a.sArrivalAt + 180_000L).stage)
        assertFalse(Countdown.isLate(a, a.sArrivalAt + 59_999L))
        assertTrue(Countdown.isLate(a, a.sArrivalAt + 60_000L))
    }

    @Test
    fun ring_neverEmptyBeforeOneSecondTotal() {
        // Presented after the S wave (late message): total clamps to 1 s, ring empty.
        val a = Countdown.fromPhone(wire, receivedAt = origin + 40_000L)
        val s = Countdown.snapshot(a, origin + 40_000L)
        assertEquals(Countdown.Stage.ARRIVED, s.stage)
        assertEquals(0f, s.fraction, 0f)
    }

    @Test
    fun vibration_runsUntilShortlyAfterArrival() {
        val a = Countdown.fromPhone(wire, receivedAt = origin + 6_500L)
        assertEquals(31_250L - 6_500L + Countdown.VIBRATE_AFTER_ARRIVAL_MS, Countdown.vibrationRemainingMs(a, origin + 6_500L))
        assertEquals(0L, Countdown.vibrationRemainingMs(a, a.sArrivalAt + 60_000L))
    }
}
