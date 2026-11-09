package com.github.mytv.myearthquakealert.wear

import com.github.mytv.myearthquakealert.data.model.EewEvent
import com.github.mytv.myearthquakealert.data.model.UserLocation
import com.github.mytv.myearthquakealert.data.source.EewSource
import com.github.mytv.myearthquakealert.service.AlertData
import com.github.mytv.myearthquakealert.service.AlertTier
import com.github.mytv.myearthquakealert.service.EewRecord
import com.github.mytv.myearthquakealert.service.LinkState
import com.github.mytv.myearthquakealert.service.MonitorPhase
import com.github.mytv.myearthquakealert.service.MonitorState
import com.github.mytv.myearthquakealert.service.SourceHealth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WearMappingTest {

    private val event = EewEvent(
        id = "1", eventId = "ev", source = EewSource.SICHUAN.name, reportTime = "2022-06-01 17:00:14",
        reportNum = 2, originTime = "2022-06-01 17:00:08", hypocenter = "四川雅安市芦山县",
        latitude = 30.37, longitude = 102.94, magnitude = 6.0, depth = 17.0, maxIntensity = 8.0,
    )

    private val state = MonitorState(
        phase = MonitorPhase.MONITORING,
        phaseSince = 10L,
        runningSince = 5L,
        sources = listOf(SourceHealth(EewSource.CENC, LinkState.CONNECTED, lastFrameAt = 1L, lastLatencyMs = 300L)),
    )

    private val here = UserLocation(30.6421, 104.0434, UserLocation.SOURCE_GPS, fixTime = 1L)

    @Test
    fun status_ignoresNoisyFields() {
        val a = WearMapping.status(state, WearThresholds(), here)
        // Heartbeats and latencies change every few seconds; the watch shows none of them.
        val noisy = state.copy(
            sources = listOf(SourceHealth(EewSource.CENC, LinkState.CONNECTED, lastFrameAt = 99L, lastLatencyMs = 1200L)),
        )
        // A GPS jitter of a few metres is not a move.
        val jitter = here.copy(latitude = 30.6424, longitude = 104.0431, fixTime = 2L)
        assertEquals(a, WearMapping.status(noisy, WearThresholds(), jitter))
    }

    @Test
    fun status_changesOnPhaseLinkPauseOrThreshold() {
        val a = WearMapping.status(state, WearThresholds(), here)
        assertNotEquals(a, WearMapping.status(state.copy(phase = MonitorPhase.PAUSED, pauseUntil = 9L), WearThresholds(), here))
        assertNotEquals(
            a,
            WearMapping.status(state.copy(sources = listOf(SourceHealth(EewSource.CENC, LinkState.RECONNECTING))), WearThresholds(), here),
        )
        assertNotEquals(a, WearMapping.status(state, WearThresholds(minMagnitude = 4.0), here))
    }

    @Test
    fun status_mapsFields() {
        val s = WearMapping.status(state, WearThresholds(3.0, 2, 6), here)
        assertEquals("MONITORING", s.phase)
        assertEquals(listOf(WireSource("CENC", EewSource.CENC.label, "CONNECTED")), s.sources)
        assertEquals(30.64, s.userLatitude!!, 1e-9)
        assertEquals(104.04, s.userLongitude!!, 1e-9)
        assertEquals(6, s.strongThreshold)
        assertEquals(1, s.connectedCount)
        assertNull(WearMapping.status(state, WearThresholds(), UserLocation(0.0, 0.0, UserLocation.SOURCE_NONE)).userLatitude)
    }

    @Test
    fun quakes_newestTenWithParsedOrigin() {
        val records = (0 until 15).map { i ->
            EewRecord(
                event.copy(eventId = "e$i"), AlertTier.REMIND, 4.24, 110.46,
                firstReceivedAt = 1_000L - i, updatedAt = 1_000L, reports = 2,
            )
        }
        val q = WearMapping.quakes(records)
        assertEquals(WearMapping.QUAKE_LIMIT, q.quakes.size)
        val first = q.quakes.first()
        assertEquals("e0", first.eventId)
        assertEquals(1_654_074_008_000L, first.originTime)
        assertEquals(4.2, first.localCsis, 1e-9)
        assertEquals(110.5, first.distanceKm, 1e-9)
        assertEquals(EewSource.SICHUAN.label, first.sourceLabel)
        assertEquals("REMIND", first.tier)
    }

    @Test
    fun alertKey_ignoresTicksButNotNewReports() {
        val data = AlertData(
            event, 30.64, 104.04, 18.0, 31.0, 4.2, 110.0,
            tier = AlertTier.STRONG, presentedAt = 1_000L, originAt = 500L,
        )
        assertEquals(WearMapping.alertKey(data), WearMapping.alertKey(data.copy(userLatitude = 30.64)))
        assertNotEquals(WearMapping.alertKey(data), WearMapping.alertKey(data.copy(event = event.copy(reportNum = 3))))
        assertNotEquals(WearMapping.alertKey(data), WearMapping.alertKey(data.copy(sWaveSeconds = 33.0)))
        assertNotEquals(WearMapping.alertKey(data), WearMapping.alertKey(data.copy(tier = AlertTier.REMIND)))
        val wire = WearMapping.alert(data, sentAt = 2_000L)
        assertEquals(500L, wire.originAt)
        assertEquals("STRONG", wire.tier)
        assertEquals(2, wire.reportNum)
        assertEquals(2_000L, wire.sentAt)
    }

    @Test
    fun linkRoute_firstSegment() {
        assertEquals("settings", WearLinkActivity.routeOf("/settings"))
        assertEquals("settings", WearLinkActivity.routeOf("/settings/update"))
        assertNull(WearLinkActivity.routeOf("/"))
        assertNull(WearLinkActivity.routeOf(null))
    }
}
