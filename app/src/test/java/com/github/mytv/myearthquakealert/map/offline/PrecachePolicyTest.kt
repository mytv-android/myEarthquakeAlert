package com.github.mytv.myearthquakealert.map.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrecachePolicyTest {

    private val now = 1_800_000_000_000L
    private val lat = 30.66
    private val lon = 104.07

    /** One degree of latitude in km on the sphere [TileMath] uses. */
    private val kmPerDegree = Math.PI * 6371.0088 / 180.0

    private fun area(
        style: String = "AMAP",
        latitude: Double = lat,
        tiles: Int = 129,
        total: Int = 129,
        updatedAt: Long = now - 3_600_000L,
    ) = CachedArea(style, latitude, lon, tiles, total, bytes = 2_000_000L, updatedAt = updatedAt)

    @Test
    fun noArea_isFirstRun() {
        assertEquals(PrecacheReason.FIRST, PrecachePolicy.reason(null, "AMAP", lat, lon, now))
    }

    @Test
    fun freshCompleteAreaAtTheSamePlace_needsNothing() {
        assertNull(PrecachePolicy.reason(area(), "AMAP", lat, lon, now))
    }

    @Test
    fun moved49km_needsNothing() {
        val north = lat + 49.0 / kmPerDegree
        assertEquals(49.0, PrecachePolicy.movedKm(area(), north, lon), 0.01)
        assertNull(PrecachePolicy.reason(area(), "AMAP", north, lon, now))
    }

    @Test
    fun moved51km_refetches() {
        val north = lat + 51.0 / kmPerDegree
        assertEquals(51.0, PrecachePolicy.movedKm(area(), north, lon), 0.01)
        assertEquals(PrecacheReason.MOVED, PrecachePolicy.reason(area(), "AMAP", north, lon, now))
    }

    @Test
    fun moveRule_usesGreatCircleDistanceNotDegrees() {
        // 0.6 degrees of longitude at 60 N is about 33 km; 0.6 degrees of latitude about 67 km.
        val far = area(latitude = 60.0)
        assertNull(PrecachePolicy.reason(far, "AMAP", 60.0, lon + 0.6, now))
        assertEquals(PrecacheReason.MOVED, PrecachePolicy.reason(far, "AMAP", 60.6, lon, now))
    }

    @Test
    fun styleChange_winsOverAMove() {
        val north = lat + 80.0 / kmPerDegree
        assertEquals(PrecacheReason.STYLE, PrecachePolicy.reason(area(), "AMAP_SAT", north, lon, now))
    }

    @Test
    fun incompleteArea_retriedAfterSixHours() {
        val partial = area(tiles = 100, updatedAt = now - PrecachePolicy.RETRY_INCOMPLETE_MS + 60_000L)
        assertNull(PrecachePolicy.reason(partial, "AMAP", lat, lon, now))
        val older = partial.copy(updatedAt = now - PrecachePolicy.RETRY_INCOMPLETE_MS)
        assertEquals(PrecacheReason.INCOMPLETE, PrecachePolicy.reason(older, "AMAP", lat, lon, now))
    }

    @Test
    fun completeArea_refreshedAfterThirtyDays() {
        val young = area(updatedAt = now - PrecachePolicy.MAX_AGE_MS + 60_000L)
        assertNull(PrecachePolicy.reason(young, "AMAP", lat, lon, now))
        val stale = area(updatedAt = now - PrecachePolicy.MAX_AGE_MS)
        assertEquals(PrecacheReason.STALE, PrecachePolicy.reason(stale, "AMAP", lat, lon, now))
    }

    @Test
    fun complete_needsAllTilesOfANonEmptyArea() {
        assertTrue(area().complete)
        assertFalse(area(tiles = 128).complete)
        assertFalse(area(tiles = 0, total = 0).complete)
    }

    @Test
    fun tileFreshness_keepsTilesWithMoreThanHalfTheirLifeLeft() {
        val expiry = PrecachePolicy.tileExpiry(now)
        assertEquals(now + PrecachePolicy.MAX_AGE_MS, expiry)
        assertTrue(PrecachePolicy.isFresh(expiry, now))
        assertTrue(PrecachePolicy.isFresh(expiry, now + PrecachePolicy.MAX_AGE_MS / 2 - 1))
        assertFalse(PrecachePolicy.isFresh(expiry, now + PrecachePolicy.MAX_AGE_MS / 2))
        assertFalse(PrecachePolicy.isFresh(now - 1, now))
    }
}
