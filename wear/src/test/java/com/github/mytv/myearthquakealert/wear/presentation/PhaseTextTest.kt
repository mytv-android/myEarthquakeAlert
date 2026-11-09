package com.github.mytv.myearthquakealert.wear.presentation

import com.github.mytv.myearthquakealert.wear.data.WireQuake
import org.junit.Assert.assertEquals
import org.junit.Test

class PhaseTextTest {

    @Test
    fun shortPlace_keepsTheCounty() {
        assertEquals("芦山", PhaseText.shortPlace("四川雅安市芦山县"))
        assertEquals("泸定", PhaseText.shortPlace("四川甘孜州泸定县"))
        assertEquals("日本本州东…", PhaseText.shortPlace("日本本州东岸近海"))
        assertEquals("台湾花莲", PhaseText.shortPlace("台湾花莲"))
    }

    @Test
    fun quakeChip_placeAndMagnitude() {
        val q = WireQuake(eventId = "e", source = "CENC", sourceLabel = "", place = "四川雅安市芦山县", magnitude = 6.0, latitude = 0.0, longitude = 0.0)
        assertEquals("芦山 M6.0", PhaseText.quakeChip(q))
    }
}
