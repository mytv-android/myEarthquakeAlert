package com.github.mytv.myearthquakealert.data.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EewParserTest {

    private val cenc = """
        {"type":"cenc_eew","ID":"bacby4yab1oyb","EventID":"202206011700.0001","ReportTime":"2022-06-01 17:00:20",
         "ReportNum":2,"OriginTime":"2022-06-01 17:00:08","HypoCenter":"四川雅安市芦山县","Latitude":30.37,
         "Longitude":102.94,"Magnitude":6.1,"Depth":17,"MaxIntensity":8}
    """.trimIndent()

    @Test
    fun cencReport_isParsed() {
        val frame = EewParser.parse(cenc, EewSource.CENC)
        assertTrue(frame is EewFrame.Report)
        val e = (frame as EewFrame.Report).event
        assertEquals("202206011700.0001", e.eventId)
        assertEquals(EewSource.CENC.name, e.source)
        assertEquals(2, e.reportNum)
        assertEquals(6.1, e.magnitude, 1e-9)
        assertEquals(17.0, e.depth!!, 1e-9)
        assertEquals(30.37, e.latitude, 1e-9)
        assertEquals("2022-06-01 17:00:08", e.originTime)
    }

    @Test
    fun feedTypeOverridesSocketSource() {
        // A frame's own type wins over the socket it arrived on.
        val frame = EewParser.parse(cenc, EewSource.SICHUAN)
        assertEquals(EewSource.CENC.name, (frame as EewFrame.Report).event.source)
    }

    @Test
    fun httpBodyWithoutType_usesSource() {
        val body = cenc.replace("\"type\":\"cenc_eew\",", "")
        val frame = EewParser.parse(body, EewSource.CENC)
        assertTrue(frame is EewFrame.Report)
    }

    @Test
    fun heartbeatAndPong_areSilentFrames() {
        val hb = EewParser.parse("""{"type":"heartbeat","ver":18,"id":"x","timestamp":1700000000000}""", EewSource.CENC)
        assertEquals(EewFrame.Heartbeat(1_700_000_000_000L), hb)
        val pong = EewParser.parse("""{"type":"pong","timestamp":1700000000001}""", EewSource.CENC)
        assertEquals(EewFrame.Pong(1_700_000_000_001L), pong)
    }

    @Test
    fun unknownType_isIgnored() {
        val frame = EewParser.parse("""{"type":"jma_eew","EventID":"x"}""", EewSource.CENC)
        assertEquals(EewFrame.Ignored("jma_eew"), frame)
    }

    @Test
    fun malformedFrames_areInvalid() {
        assertTrue(EewParser.parse("not json", EewSource.CENC) is EewFrame.Invalid)
        assertTrue(EewParser.parse("[1,2]", EewSource.CENC) is EewFrame.Invalid)
        assertTrue(EewParser.parse(cenc.replace("202206011700.0001", ""), EewSource.CENC) is EewFrame.Invalid)
        val nullIsland = cenc.replace("30.37", "0").replace("102.94", "0")
        assertTrue(EewParser.parse(nullIsland, EewSource.CENC) is EewFrame.Invalid)
    }

    @Test
    fun sichuanZeroDepth_isUnknown() {
        val sc = """
            {"type":"sc_eew","ID":1,"EventID":"sc-1","ReportTime":"2022-06-01 17:00:12","ReportNum":1,
             "OriginTime":"2022-06-01 17:00:08","HypoCenter":"芦山","Latitude":30.4,"Longitude":102.9,
             "Magunitude":5.8,"Depth":0,"MaxIntensity":7.5}
        """.trimIndent()
        val e = (EewParser.parse(sc, EewSource.SICHUAN) as EewFrame.Report).event
        assertNull(e.depth)
        assertEquals(5.8, e.magnitude, 1e-9)
    }

    @Test
    fun fujianFinalFlag_isKept() {
        val fj = """
            {"type":"fj_eew","ID":7,"EventID":"fj-7","ReportTime":"2024-01-01 10:00:10","ReportNum":3,
             "OriginTime":"2024-01-01 10:00:00","HypoCenter":"台湾海峡","Latitude":24.1,"Longitude":119.2,
             "Magunitude":4.6,"isFinal":true}
        """.trimIndent()
        val e = (EewParser.parse(fj, EewSource.FUJIAN) as EewFrame.Report).event
        assertTrue(e.isFinal)
        assertNull(e.depth)
    }

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
        // No EventID on this feed: the CWA 地震编号 stands in, so reports of one quake share it.
        assertEquals("cwa-1150085", e.eventId)
        assertEquals("1150085", e.id)
        assertEquals(EewSource.CWA.name, e.source)
        assertEquals(1, e.reportNum)
        assertEquals(4.7, e.magnitude, 1e-9)
        assertEquals(20.0, e.depth!!, 1e-9)
        assertEquals(3.0, e.maxIntensity, 1e-9)
    }

    @Test
    fun cwaFeedType_isRoutedFromTheFrame() {
        val frame = EewParser.parse(cwa.replace("{", """{"type":"cwa_eew","""), EewSource.SICHUAN)
        assertEquals(EewSource.CWA.name, (frame as EewFrame.Report).event.source)
    }

    @Test
    fun cwaShindoSuffix_keepsTheNumber() {
        val withSuffix = cwa.replace("\"MaxIntensity\":\"3\"", "\"MaxIntensity\":\"5弱\"")
        val e = (EewParser.parse(withSuffix, EewSource.CWA) as EewFrame.Report).event
        assertEquals(5.0, e.maxIntensity, 1e-9)
        val asNumber = cwa.replace("\"MaxIntensity\":\"3\"", "\"MaxIntensity\":4")
        assertEquals(4.0, (EewParser.parse(asNumber, EewSource.CWA) as EewFrame.Report).event.maxIntensity, 1e-9)
    }

    @Test
    fun cwaCancelledReport_isIgnored() {
        val cancelled = cwa.replace("\"isCancel\":false", "\"isCancel\":true")
        assertEquals(EewFrame.Ignored("cwa_eew:isCancel"), EewParser.parse(cancelled, EewSource.CWA))
    }
}
