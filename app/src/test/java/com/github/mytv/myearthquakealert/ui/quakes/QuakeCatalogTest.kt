package com.github.mytv.myearthquakealert.ui.quakes

import com.github.mytv.myearthquakealert.data.model.EarthquakeInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuakeCatalogTest {

    private val chengdu = UserPoint(30.573, 104.066, UserPoint.Source.MANUAL, "成都")

    private fun info(
        time: String,
        location: String,
        magnitude: String = "4.0",
        lat: String = "30.0",
        lon: String = "103.0",
        type: String = "reviewed",
    ) = EarthquakeInfo(no = 1, type = type, time = time, location = location, magnitude = magnitude, depth = "10", latitude = lat, longitude = lon, intensity = "5")

    private fun quake(time: String, location: String, magnitude: String = "4.0", lat: String = "30.0", lon: String = "103.0") =
        QuakeCatalog.fromInfo(info(time, location, magnitude, lat, lon), chengdu)!!

    /** 2026-11-08 10:12 in Beijing time. */
    private val now = QuakeCatalog.parseTime("2026-11-08 10:12:00")!!

    @Test
    fun parsesCatalogueEntries() {
        val q = quake("2026-11-08 09:58:11", "四川雅安市芦山县", magnitude = "4.6", lat = "30.31", lon = "102.94")
        assertEquals("2026-11-08 09:58:11|30.31|102.94", q.key)
        assertEquals(4.6, q.magnitude, 1e-9)
        assertEquals(5, q.intensity)
        assertTrue(q.reviewed)
        assertTrue(q.domestic)
        assertNotNull(q.distanceKm)
        assertTrue(q.distanceKm!! in 100.0..130.0)
        assertNotNull(q.localIntensity)
    }

    @Test
    fun rejectsEntriesWithoutCoordinates() {
        assertNull(QuakeCatalog.fromInfo(info("2026-11-08 09:58:11", "四川", lat = "", lon = ""), chengdu))
        assertNull(QuakeCatalog.fromInfo(info("2026-11-08 09:58:11", "四川", lat = "0", lon = "0"), chengdu))
        assertNull(QuakeCatalog.fromInfo(info("2026-11-08 09:58:11", "四川", magnitude = "?"), chengdu))
    }

    @Test
    fun withoutLocationThereIsNoDistance() {
        val q = QuakeCatalog.fromInfo(info("2026-11-08 09:58:11", "四川"), null)!!
        assertNull(q.distanceKm)
        assertNull(q.localIntensity)
    }

    @Test
    fun domesticByLeadingProvinceOrSea() {
        assertTrue(QuakeCatalog.isDomestic("新疆喀什地区伽师县"))
        assertTrue(QuakeCatalog.isDomestic("台湾花莲县海域"))
        assertTrue(QuakeCatalog.isDomestic("东海海域"))
        assertFalse(QuakeCatalog.isDomestic("日本本州东海岸近海"))
        assertFalse(QuakeCatalog.isDomestic("瓦努阿图群岛"))
    }

    @Test
    fun filtersSortsAndSearches() {
        val near = quake("2026-11-08 09:58:11", "四川雅安市芦山县", magnitude = "3.1", lat = "30.31", lon = "102.94")
        val big = quake("2026-11-07 21:40:00", "日本本州东海岸近海", magnitude = "6.1", lat = "38.0", lon = "142.0")
        val far = quake("2026-11-06 08:00:00", "新疆喀什地区伽师县", magnitude = "4.2", lat = "39.8", lon = "77.2")
        val all = listOf(far, near, big)

        assertEquals(listOf(near, big, far), QuakeCatalog.apply(all, QuakeFilter.ALL, QuakeSort.TIME, ""))
        assertEquals(listOf(big, far), QuakeCatalog.apply(all, QuakeFilter.M4, QuakeSort.MAGNITUDE, ""))
        assertEquals(setOf(near, far), QuakeCatalog.apply(all, QuakeFilter.DOMESTIC, QuakeSort.TIME, "").toSet())
        assertEquals(listOf(near), QuakeCatalog.apply(all, QuakeFilter.NEARBY, QuakeSort.TIME, ""))
        assertEquals(listOf(near, far, big), QuakeCatalog.apply(all, QuakeFilter.ALL, QuakeSort.DISTANCE, ""))
        assertEquals(listOf(far), QuakeCatalog.apply(all, QuakeFilter.ALL, QuakeSort.TIME, "  喀什 "))
        assertTrue(QuakeCatalog.apply(all, QuakeFilter.NEARBY, QuakeSort.TIME, "喀什").isEmpty())
    }

    @Test
    fun daysAgoUsesBeijingCalendarDays() {
        assertEquals(0, QuakeCatalog.daysAgo(QuakeCatalog.parseTime("2026-11-08 00:00:01")!!, now))
        assertEquals(1, QuakeCatalog.daysAgo(QuakeCatalog.parseTime("2026-11-07 23:59:59")!!, now))
        assertEquals(2, QuakeCatalog.daysAgo(QuakeCatalog.parseTime("2026-11-06 12:00:00")!!, now))
        assertEquals(11 to 8, QuakeCatalog.monthDay(now))
    }

    @Test
    fun groupsByDayInOrder() {
        val a = quake("2026-11-08 09:58:11", "四川")
        val b = quake("2026-11-08 01:00:00", "云南")
        val c = quake("2026-11-07 22:00:00", "西藏")
        val d = quake("2026-11-05 03:00:00", "新疆")
        val groups = QuakeCatalog.groupByDay(listOf(a, b, c, d), now)
        assertEquals(listOf(0, 1, 3), groups.map { it.first })
        assertEquals(listOf(a, b), groups[0].second)
        assertEquals(listOf(c), groups[1].second)
        assertEquals(listOf(d), groups[2].second)
    }

    @Test
    fun spanCoversTheOldestEntry() {
        assertEquals(1, QuakeCatalog.spanDays(emptyList(), now))
        val list = listOf(quake("2026-11-08 09:58:11", "四川"), quake("2026-10-30 10:00:00", "云南"))
        assertEquals(10, QuakeCatalog.spanDays(list, now))
        assertEquals("09:58", QuakeCatalog.hhmm("2026-11-08 09:58:11"))
    }
}
