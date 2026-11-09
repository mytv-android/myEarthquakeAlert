package com.github.mytv.myearthquakealert.wear.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the phone ↔ watch JSON on the watch side. The golden strings are identical to the
 * phone module's WearProtocolTest, so both copies of the protocol stay wire-compatible.
 */
class WearProtocolTest {

    @Test
    fun alert_matchesGolden() {
        assertEquals(GOLDEN_ALERT, WearCodec.encode(ALERT).decodeToString())
        assertEquals(ALERT, WearCodec.decodeAlert(GOLDEN_ALERT.encodeToByteArray()))
    }

    @Test
    fun status_matchesGolden() {
        assertEquals(GOLDEN_STATUS, WearCodec.encode(STATUS).decodeToString())
        val decoded = WearCodec.decodeStatus(GOLDEN_STATUS.encodeToByteArray())
        assertEquals(STATUS, decoded)
        assertEquals(1, decoded!!.connectedCount)
    }

    @Test
    fun quakes_matchesGolden() {
        assertEquals(GOLDEN_QUAKES, WearCodec.encode(QUAKES).decodeToString())
        assertEquals(QUAKES, WearCodec.decodeQuakes(GOLDEN_QUAKES.encodeToByteArray()))
    }

    @Test
    fun commandAndResult_roundTrip() {
        val command = WireCommand(durationMs = WearPaths.DEFAULT_PAUSE_MS, sentAt = 5L)
        assertEquals(GOLDEN_COMMAND, WearCodec.encode(command).decodeToString())
        assertEquals(command, WearCodec.decodeCommand(GOLDEN_COMMAND.encodeToByteArray()))

        val result = WireCommandResult(
            command = WearPaths.CMD_START, ok = false, error = WireCommandResult.ERR_BACKGROUND,
            phase = "OFF", sentAt = 9L,
        )
        assertEquals(result, WearCodec.decodeCommandResult(WearCodec.encode(result)))
        val end = WireAlertEnd(eventId = "e1", sentAt = 3L)
        assertEquals(end, WearCodec.decodeAlertEnd(WearCodec.encode(end)))
    }

    @Test
    fun decode_toleratesUnknownFieldsAndMissingOptionals() {
        val newer = "{\"v\":2,\"phase\":\"PAUSED\",\"pauseUntil\":42,\"futureField\":{\"a\":1}}"
        val status = WearCodec.decodeStatus(newer.encodeToByteArray())!!
        assertEquals("PAUSED", status.phase)
        assertEquals(42L, status.pauseUntil)
        assertTrue(status.sources.isEmpty())
        assertEquals(5, status.strongThreshold)
    }

    @Test
    fun decode_malformed_returnsNull() {
        assertNull(WearCodec.decodeStatus(null))
        assertNull(WearCodec.decodeStatus(ByteArray(0)))
        assertNull(WearCodec.decodeAlert("not json".encodeToByteArray()))
        assertNull(WearCodec.decodeAlert("{\"eventId\":\"x\"}".encodeToByteArray()))
        // Every command field is optional: an empty object is a valid command.
        assertNotNull(WearCodec.decodeCommand("{}".encodeToByteArray()))
    }

    companion object {
        val ALERT = WireAlert(
            eventId = "202206011700", source = "SICHUAN", sourceLabel = "四川地震局", place = "四川雅安市芦山县",
            magnitude = 6.1, depthKm = 17.0, latitude = 30.37, longitude = 102.94,
            userLatitude = 30.64, userLongitude = 104.04, originAt = 1_654_074_008_000L,
            pWaveSeconds = 18.5, sWaveSeconds = 31.25, localCsis = 4.2, distanceKm = 110.5,
            tier = "STRONG", drill = true, reportNum = 3, isFinal = true,
            presentedAt = 1_654_074_014_400L, sentAt = 1_654_074_014_500L,
        )
        const val GOLDEN_ALERT = "{\"v\":1,\"eventId\":\"202206011700\",\"source\":\"SICHUAN\",\"sourceLabel\":\"四川地震局\"," +
            "\"place\":\"四川雅安市芦山县\",\"magnitude\":6.1,\"depthKm\":17.0,\"latitude\":30.37,\"longitude\":102.94," +
            "\"userLatitude\":30.64,\"userLongitude\":104.04,\"originAt\":1654074008000,\"pWaveSeconds\":18.5," +
            "\"sWaveSeconds\":31.25,\"localCsis\":4.2,\"distanceKm\":110.5,\"tier\":\"STRONG\",\"drill\":true," +
            "\"reportNum\":3,\"isFinal\":true,\"presentedAt\":1654074014400,\"sentAt\":1654074014500}"

        val STATUS = WireStatus(
            phase = "MONITORING", phaseSince = 100L, runningSince = 50L, pauseUntil = 0L,
            sources = listOf(WireSource("CENC", "中国地震台网", "CONNECTED"), WireSource("SICHUAN", "四川地震局", "RECONNECTING")),
            missing = listOf("FULL_SCREEN_INTENT"), userLatitude = 30.64, userLongitude = 104.04,
            minMagnitude = 3.5, minIntensity = 2, strongThreshold = 5, updatedAt = 200L,
        )
        const val GOLDEN_STATUS = "{\"v\":1,\"phase\":\"MONITORING\",\"phaseSince\":100,\"runningSince\":50,\"pauseUntil\":0," +
            "\"sources\":[{\"id\":\"CENC\",\"label\":\"中国地震台网\",\"link\":\"CONNECTED\"}," +
            "{\"id\":\"SICHUAN\",\"label\":\"四川地震局\",\"link\":\"RECONNECTING\"}],\"missing\":[\"FULL_SCREEN_INTENT\"]," +
            "\"userLatitude\":30.64,\"userLongitude\":104.04,\"minMagnitude\":3.5,\"minIntensity\":2," +
            "\"strongThreshold\":5,\"updatedAt\":200}"

        val QUAKES = WireQuakes(
            quakes = listOf(
                WireQuake(
                    eventId = "e1", source = "CENC", sourceLabel = "中国地震台网", place = "日本本州东岸近海",
                    magnitude = 5.3, depthKm = null, latitude = 38.1, longitude = 142.6, originTime = 1_000L,
                    maxIntensity = 0.0, localCsis = 0.4, distanceKm = 3120.2, tier = "RECORD", reports = 2,
                    isFinal = false, receivedAt = 2_000L,
                ),
            ),
            updatedAt = 3_000L,
        )
        const val GOLDEN_QUAKES = "{\"v\":1,\"quakes\":[{\"eventId\":\"e1\",\"source\":\"CENC\",\"sourceLabel\":\"中国地震台网\"," +
            "\"place\":\"日本本州东岸近海\",\"magnitude\":5.3,\"depthKm\":null,\"latitude\":38.1,\"longitude\":142.6," +
            "\"originTime\":1000,\"maxIntensity\":0.0,\"localCsis\":0.4,\"distanceKm\":3120.2,\"tier\":\"RECORD\"," +
            "\"reports\":2,\"isFinal\":false,\"receivedAt\":2000}],\"updatedAt\":3000}"

        const val GOLDEN_COMMAND = "{\"v\":1,\"durationMs\":3600000,\"sentAt\":5}"
    }
}
