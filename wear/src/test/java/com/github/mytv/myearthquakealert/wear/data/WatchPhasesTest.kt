package com.github.mytv.myearthquakealert.wear.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchPhasesTest {

    private val idle = StandaloneState()
    private val watching = StandaloneState(enabled = true, running = true, link = StandaloneLink.CONNECTED)

    private fun status(phase: String, pauseUntil: Long = 0L) = WireStatus(phase = phase, pauseUntil = pauseUntil)

    @Test
    fun phoneAway_disconnectedUnlessStandaloneConnected() {
        assertEquals(WatchPhase.DISCONNECTED, WatchPhases.resolve(status("MONITORING"), false, idle, 0L))
        assertEquals(WatchPhase.STANDALONE, WatchPhases.resolve(status("MONITORING"), false, watching, 0L))
        assertEquals(
            WatchPhase.DISCONNECTED,
            WatchPhases.resolve(null, false, watching.copy(link = StandaloneLink.RETRYING), 0L),
        )
    }

    @Test
    fun phonePhases_mapOneToOne() {
        for (name in listOf("OFF", "CONNECTING", "MONITORING", "RECONNECTING", "DEGRADED", "LIMITED")) {
            assertEquals(WatchPhase.valueOf(name), WatchPhases.resolve(status(name), true, idle, 0L))
        }
        // Unknown before the first capability answer: the synced status still shows.
        assertEquals(WatchPhase.MONITORING, WatchPhases.resolve(status("MONITORING"), null, idle, 0L))
        assertEquals(WatchPhase.WAITING, WatchPhases.resolve(null, true, idle, 0L))
        assertEquals(WatchPhase.WAITING, WatchPhases.resolve(status("SOMETHING_NEW"), true, idle, 0L))
    }

    @Test
    fun pause_expiresOnTheWatchToo() {
        assertEquals(WatchPhase.PAUSED, WatchPhases.resolve(status("PAUSED", pauseUntil = 1_000L), true, idle, 999L))
        assertEquals(WatchPhase.CONNECTING, WatchPhases.resolve(status("PAUSED", pauseUntil = 1_000L), true, idle, 1_000L))
    }

    @Test
    fun pausableAndProtecting() {
        assertTrue(WatchPhase.MONITORING.pausable)
        assertTrue(WatchPhase.DEGRADED.pausable)
        assertFalse(WatchPhase.PAUSED.pausable)
        assertFalse(WatchPhase.OFF.pausable)
        assertFalse(WatchPhase.DISCONNECTED.pausable)
        assertTrue(WatchPhase.STANDALONE.protecting)
        assertFalse(WatchPhase.CONNECTING.protecting)
    }
}
