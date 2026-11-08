package com.github.mytv.myearthquakealert.domain

import com.github.mytv.myearthquakealert.service.AlertTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrillScenarioTest {

    private val now = 1_654_074_020_000L

    @Test
    fun lushan_replaysAtTheRealEpicenter() {
        val script = DrillScenario.script(DrillScenario.Scenario.LUSHAN, 39.9, 116.4, "CENC", "四川雅安市芦山县", now)
        assertEquals(3, script.reports.size)
        assertEquals(listOf(0L, 3_600L, 8_400L), script.reports.map { it.delayMs })
        val last = script.reports.last().event
        assertEquals(DrillScenario.LUSHAN_LATITUDE, last.latitude, 1e-9)
        assertEquals(DrillScenario.LUSHAN_LONGITUDE, last.longitude, 1e-9)
        assertEquals(17.0, last.depth!!, 1e-9)
        assertEquals(6.1, last.magnitude, 1e-9)
        assertTrue(last.isFinal)
        assertFalse(script.reports.first().event.isFinal)
        // The first report is due now: the origin lies 5.8 s back.
        assertEquals(now - 5_800L, script.originAt)
        // The feed format has whole seconds.
        assertEquals(script.originAt / 1000 * 1000, ReportTime.parseCst(last.originTime))
        assertEquals(script.reports.map { it.event.eventId }.toSet().size, 1)
        assertTrue(script.reports.all { DrillScenario.isDrillEvent(it.event) })
        assertEquals((1..3).toList(), script.reports.map { it.event.reportNum })
    }

    @Test
    fun near_isAbout85KmAway() {
        val script = DrillScenario.script(DrillScenario.Scenario.NEAR, 30.66, 104.07, "CENC", "near", now)
        val e = script.reports.last().event
        val d = SeismicCalculator.haversineDistance(30.66, 104.07, e.latitude, e.longitude)
        assertEquals(85.0, d, 0.5)
        assertEquals(6.5, e.magnitude, 1e-9)
        assertEquals(10.0, e.depth!!, 1e-9)
    }

    @Test
    fun far_isAbout300KmAway() {
        val script = DrillScenario.script(DrillScenario.Scenario.FAR, 30.66, 104.07, "CENC", "far", now)
        val e = script.reports.last().event
        val d = SeismicCalculator.haversineDistance(30.66, 104.07, e.latitude, e.longitude)
        assertEquals(300.0, d, 1.0)
        assertEquals(5.0, e.magnitude, 1e-9)
    }

    @Test
    fun legacyCreate_isKept() {
        val e = DrillScenario.create(30.0, 104.0, "CENC", "x")
        assertEquals(DrillScenario.EVENT_ID, e.eventId)
        assertEquals(6.5, e.magnitude, 1e-9)
        assertEquals(DrillScenario.Scenario.LUSHAN, DrillScenario.Scenario.fromId("unknown"))
    }

    @Test
    fun tierOf_strongWinsOverReminderThresholds() {
        assertEquals(AlertTier.STRONG, AlertEvaluator.tierOf(5.2, 3.0, 4.0, 6, 5))
        assertEquals(AlertTier.REMIND, AlertEvaluator.tierOf(3.2, 4.5, 4.0, 3, 5))
        assertEquals(AlertTier.RECORD, AlertEvaluator.tierOf(3.2, 3.5, 4.0, 3, 5))
        // A strong threshold of 0 disables STRONG.
        assertEquals(AlertTier.REMIND, AlertEvaluator.tierOf(7.0, 6.0, 0.0, 0, 0))
    }
}
