package com.github.mytv.myearthquakealert.wear.domain

import com.github.mytv.myearthquakealert.wear.data.WireStatus
import com.github.mytv.myearthquakealert.wear.source.EewEvent
import com.github.mytv.myearthquakealert.wear.source.EewSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StandaloneEvaluatorTest {

    private val event = EewEvent(
        id = "1", eventId = "ev", source = EewSource.SICHUAN.name, reportTime = "2022-06-01 17:00:14",
        reportNum = 2, originTime = "2022-06-01 17:00:08", hypocenter = "四川雅安市芦山县",
        latitude = 30.37, longitude = 102.94, magnitude = 6.1, depth = 17.0, maxIntensity = 8.0,
    )
    private val origin = ReportTime.parseCst(event.originTime)!!
    private val chengdu = StandaloneEvaluator.Location(30.66, 104.06)

    @Test
    fun tiers_followPhoneRules() {
        val t = StandaloneEvaluator.Thresholds(minMagnitude = 4.0, minIntensity = 2, strongThreshold = 5)
        assertEquals(StandaloneEvaluator.Tier.STRONG, StandaloneEvaluator.tierOf(5.0, 3.0, t)) // strong is never filtered
        assertEquals(StandaloneEvaluator.Tier.REMIND, StandaloneEvaluator.tierOf(2.5, 4.5, t))
        assertEquals(StandaloneEvaluator.Tier.RECORD, StandaloneEvaluator.tierOf(2.5, 3.9, t))
        assertEquals(StandaloneEvaluator.Tier.RECORD, StandaloneEvaluator.tierOf(1.9, 4.5, t))
        // 0 disables a threshold; strong threshold 0 disables STRONG.
        val off = StandaloneEvaluator.Thresholds(0.0, 0, 0)
        assertEquals(StandaloneEvaluator.Tier.REMIND, StandaloneEvaluator.tierOf(9.0, 1.0, off))
    }

    @Test
    fun thresholds_fromPhoneStatus() {
        val s = WireStatus(phase = "MONITORING", minMagnitude = 3.5, minIntensity = 3, strongThreshold = 6)
        assertEquals(StandaloneEvaluator.Thresholds(3.5, 3, 6), StandaloneEvaluator.Thresholds.of(s))
        assertEquals(StandaloneEvaluator.Thresholds(), StandaloneEvaluator.Thresholds.of(null))
    }

    @Test
    fun nearbyQuake_alertsWithCountdown() {
        val now = origin + 6_000L
        val r = StandaloneEvaluator.evaluate(
            event, chengdu, StandaloneEvaluator.Thresholds(), originAt = origin,
            receivedAt = now, reportAt = origin + 6_000L, now = now,
        )
        assertNotNull(r.alert)
        val alert = r.alert!!
        assertFalse(alert.viaPhone)
        assertEquals(now, alert.presentedAt)
        assertTrue(alert.distanceKm in 100.0..120.0)
        val arrival = SeismicCalculator.calcWaveArrival(17.0, alert.distanceKm)
        assertEquals(origin + (arrival.sWaveSeconds * 1000).toLong(), alert.sArrivalAt)
        assertTrue(alert.sArrivalAt > alert.pArrivalAt)
        assertEquals("ev", r.quake.eventId)
        assertEquals(origin, r.quake.originTime)
        assertEquals(EewSource.SICHUAN.label, r.quake.sourceLabel)
        assertEquals(r.tier.name, r.quake.tier)
    }

    @Test
    fun noLocation_recordOnly() {
        val r = StandaloneEvaluator.evaluate(event, null, StandaloneEvaluator.Thresholds(), origin, origin + 6_000L, null, origin + 6_000L)
        assertEquals(StandaloneEvaluator.Tier.RECORD, r.tier)
        assertNull(r.alert)
        assertEquals(0.0, r.quake.distanceKm, 0.0)
    }

    @Test
    fun staleOrLateReports_historyOnly() {
        val stale = StandaloneEvaluator.evaluate(
            event, chengdu, StandaloneEvaluator.Thresholds(), origin,
            receivedAt = origin + 20 * 60_000L, reportAt = origin + 6_000L, now = origin + 20 * 60_000L,
        )
        assertNull(stale.alert)
        val late = StandaloneEvaluator.evaluate(
            event, chengdu, StandaloneEvaluator.Thresholds(), origin,
            receivedAt = origin + 5 * 60_000L, reportAt = origin + 5 * 60_000L, now = origin + 5 * 60_000L,
        )
        assertNull(late.alert)
    }

    @Test
    fun furtherReport_keepsPresentationAndNeverDowngrades() {
        val t = StandaloneEvaluator.Thresholds(strongThreshold = 3)
        val first = StandaloneEvaluator.evaluate(event, chengdu, t, origin, origin + 6_000L, origin + 6_000L, origin + 6_000L).alert!!
        assertTrue(first.strong)
        val weaker = event.copy(reportNum = 3, magnitude = 4.0)
        val second = StandaloneEvaluator.evaluate(weaker, chengdu, t, origin, origin + 9_000L, origin + 9_000L, origin + 9_000L, previous = first).alert!!
        assertEquals(first.presentedAt, second.presentedAt)
        assertTrue(second.strong)
        assertEquals(3, second.reportNum)
    }

    @Test
    fun missingDepth_usesDefault() {
        val r = StandaloneEvaluator.evaluate(event.copy(depth = null), chengdu, StandaloneEvaluator.Thresholds(), origin, origin + 6_000L, null, origin + 6_000L)
        val alert = r.alert!!
        val arrival = SeismicCalculator.calcWaveArrival(StandaloneEvaluator.DEFAULT_DEPTH_KM, alert.distanceKm)
        assertEquals(origin + (arrival.sWaveSeconds * 1000).toLong(), alert.sArrivalAt)
        assertNull(r.quake.depthKm)
    }
}
