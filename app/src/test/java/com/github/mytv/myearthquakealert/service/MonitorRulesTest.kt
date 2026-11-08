package com.github.mytv.myearthquakealert.service

import com.github.mytv.myearthquakealert.data.model.EewEvent
import com.github.mytv.myearthquakealert.data.source.EewSource
import com.github.mytv.myearthquakealert.diagnostics.SlotStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitorRulesTest {

    private fun health(source: EewSource, link: LinkState) = SourceHealth(source, link)

    @Test
    fun backoff_followsTheSchedule() {
        assertEquals(listOf(2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L, 30_000L), (1..7).map(MonitorRules::backoffMs))
        assertEquals(2_000L, MonitorRules.backoffMs(0))
    }

    @Test
    fun phase_precedence() {
        val connected = listOf(health(EewSource.CENC, LinkState.CONNECTED), health(EewSource.SICHUAN, LinkState.RECONNECTING))
        val none = emptySet<DeliveryRequirement>()
        assertEquals(MonitorPhase.OFF, MonitorRules.derivePhase(false, true, connected, setOf(DeliveryRequirement.OVERLAY)))
        assertEquals(MonitorPhase.PAUSED, MonitorRules.derivePhase(true, true, connected, setOf(DeliveryRequirement.OVERLAY)))
        assertEquals(MonitorPhase.LIMITED, MonitorRules.derivePhase(true, false, connected, setOf(DeliveryRequirement.NOTIFICATIONS)))
        // Optional requirements never limit.
        assertEquals(MonitorPhase.MONITORING, MonitorRules.derivePhase(true, false, connected, setOf(DeliveryRequirement.LOCATION)))
        assertEquals(MonitorPhase.MONITORING, MonitorRules.derivePhase(true, false, connected, none))
        val polling = listOf(health(EewSource.CENC, LinkState.POLLING), health(EewSource.SICHUAN, LinkState.RECONNECTING))
        assertEquals(MonitorPhase.DEGRADED, MonitorRules.derivePhase(true, false, polling, none))
        val reconnecting = listOf(health(EewSource.CENC, LinkState.RECONNECTING), health(EewSource.SICHUAN, LinkState.CONNECTING))
        assertEquals(MonitorPhase.RECONNECTING, MonitorRules.derivePhase(true, false, reconnecting, none))
        assertEquals(MonitorPhase.CONNECTING, MonitorRules.derivePhase(true, false, listOf(health(EewSource.CENC, LinkState.CONNECTING)), none))
        assertEquals(MonitorPhase.CONNECTING, MonitorRules.derivePhase(true, false, emptyList(), none))
    }

    @Test
    fun linkStatus_forAvailability() {
        val connected = listOf(health(EewSource.CENC, LinkState.CONNECTED))
        assertEquals(SlotStatus.OFF, MonitorRules.linkStatus(false, false, connected))
        assertEquals(SlotStatus.OFF, MonitorRules.linkStatus(true, true, connected))
        assertEquals(SlotStatus.UP, MonitorRules.linkStatus(true, false, connected))
        assertEquals(SlotStatus.DEGRADED, MonitorRules.linkStatus(true, false, listOf(health(EewSource.CENC, LinkState.POLLING))))
        assertEquals(SlotStatus.DOWN, MonitorRules.linkStatus(true, false, listOf(health(EewSource.CENC, LinkState.RECONNECTING))))
    }

    @Test
    fun staleReports() {
        val now = 1_000_000_000L
        assertFalse(MonitorRules.isStale(null, now))
        assertFalse(MonitorRules.isStale(now - MonitorRules.STALE_REPORT_MS, now))
        assertTrue(MonitorRules.isStale(now - MonitorRules.STALE_REPORT_MS - 1, now))
    }

    private fun event(id: String, num: Int, final: Boolean = false) = EewEvent(
        id = "$id-$num", eventId = id, source = "CENC", reportTime = "", reportNum = num, originTime = "",
        hypocenter = "", latitude = 30.0, longitude = 103.0, magnitude = 5.0, isFinal = final,
    )

    @Test
    fun deduper_keepsNewestReportAcrossSources() {
        val d = ReportDeduper()
        val t = 1_000L
        assertEquals(ReportDeduper.Verdict.NEW_EVENT, d.accept(event("A", 1), t))
        assertEquals(ReportDeduper.Verdict.DUPLICATE, d.accept(event("A", 1), t)) // second source
        assertEquals(ReportDeduper.Verdict.NEWER_REPORT, d.accept(event("A", 3), t))
        assertEquals(ReportDeduper.Verdict.DUPLICATE, d.accept(event("A", 2), t)) // older number arrives late
        assertEquals(3, d.latestReportNum("A"))
        assertEquals(ReportDeduper.Verdict.NEWER_REPORT, d.accept(event("A", 3, final = true), t))
        assertEquals(ReportDeduper.Verdict.DUPLICATE, d.accept(event("A", 4), t)) // after the final report
        assertEquals(ReportDeduper.Verdict.NEW_EVENT, d.accept(event("B", 1), t))
    }

    @Test
    fun deduper_forgetsOldEvents() {
        val d = ReportDeduper(retentionMs = 1_000L)
        d.accept(event("A", 2), 0L)
        assertEquals(ReportDeduper.Verdict.NEW_EVENT, d.accept(event("A", 1), 5_000L))
        d.clear()
        assertNull(d.latestReportNum("A"))
    }
}
