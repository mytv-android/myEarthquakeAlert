package com.github.mytv.myearthquakealert.wear.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The watch's parser is a copy of `:app`'s; this covers the feed shapes that differ between
 * them (the CWA feed) so the two cannot drift apart unnoticed.
 */
class EewParserTest {

    /** The body of `cwa_eew.json` as the feed serves it: no `type`, no `EventID`, 震度 as a string. */
    private val cwa = """
        {"ID":1150085,"ReportTime":"2026-11-17 16:46:30","ReportNum":1,"OriginTime":"2026-11-17 16:46:03",
         "HypoCenter":"臺東縣北部外海","Latitude":23.38,"Longitude":121.68,"Magunitude":4.7,"Magnitude":4.7,
         "Depth":20.0,"MaxIntensity":"3","isCancel":false}
    """.trimIndent()

    @Test
    fun cwaReport_isParsed() {
        val frame = EewParser.parse(cwa, EewSource.CWA)
        assertTrue(frame is EewFrame.Report)
        val e = (frame as EewFrame.Report).event
        assertEquals("cwa-1150085", e.eventId)
        assertEquals(EewSource.CWA.name, e.source)
        assertEquals(4.7, e.magnitude, 1e-9)
        assertEquals(20.0, e.depth!!, 1e-9)
        assertEquals(3.0, e.maxIntensity, 1e-9)
    }

    @Test
    fun cwaShindoSuffix_keepsTheNumber() {
        val withSuffix = cwa.replace("\"MaxIntensity\":\"3\"", "\"MaxIntensity\":\"5弱\"")
        val e = (EewParser.parse(withSuffix, EewSource.CWA) as EewFrame.Report).event
        assertEquals(5.0, e.maxIntensity, 1e-9)
    }

    @Test
    fun cwaCancelledReport_isIgnored() {
        val cancelled = cwa.replace("\"isCancel\":false", "\"isCancel\":true")
        assertEquals(EewFrame.Ignored("cwa_eew:isCancel"), EewParser.parse(cancelled, EewSource.CWA))
    }

    @Test
    fun heartbeatAndPong_areSilentFrames() {
        val hb = EewParser.parse("""{"type":"heartbeat","ver":27,"id":"x","timestamp":1700000000000}""", EewSource.CWA)
        assertEquals(EewFrame.Heartbeat(1_700_000_000_000L), hb)
        val pong = EewParser.parse("""{"type":"pong","timestamp":1700000000001}""", EewSource.CWA)
        assertEquals(EewFrame.Pong(1_700_000_000_001L), pong)
    }
}
