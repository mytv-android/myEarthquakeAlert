package com.github.mytv.myearthquakealert.map.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TileMathTest {

    private val chengduLat = 30.66
    private val chengduLon = 104.07

    @Test
    fun tileOfPoint_matchesSlippyMapFormula() {
        assertEquals(404, TileMath.tileX(chengduLon, 9))
        assertEquals(210, TileMath.tileY(chengduLat, 9))
        assertEquals(0, TileMath.tileX(-180.0, 3))
        assertEquals(4, TileMath.tileX(0.0, 3))
        assertEquals(0, TileMath.tileY(89.0, 4))
        assertEquals(15, TileMath.tileY(-89.0, 4))
    }

    @Test
    fun zeroRadius_givesTheTileOfTheCentre() {
        val tiles = TileMath.tilesWithin(chengduLat, chengduLon, 0.0, 9)
        assertEquals(listOf(TileCoord(9, 404, 210)), tiles)
    }

    @Test
    fun chengdu300km_tileCountsPerZoom() {
        val counts = PrecachePolicy.ZOOMS.map { TileMath.tilesWithin(chengduLat, chengduLon, 300.0, it).size }
        assertEquals(listOf(3, 4, 10, 28, 84), counts)
        val all = TileMath.tilesWithin(chengduLat, chengduLon, PrecachePolicy.RADIUS_KM, PrecachePolicy.ZOOMS)
        assertEquals(129, all.size)
        assertEquals(all.size, all.toSet().size)
        assertEquals(PrecachePolicy.ZOOMS.first, all.first().zoom)
        assertEquals(PrecachePolicy.ZOOMS.last, all.last().zoom)
    }

    @Test
    fun tilesWithin_matchesBruteForceOverTheWholeLevel() {
        val centres = listOf(chengduLat to chengduLon, 39.90 to 116.41, 60.0 to 30.0, -33.87 to 151.21)
        for ((lat, lon) in centres) {
            for (zoom in 5..8) {
                val n = TileMath.tileCount(zoom)
                val expected = HashSet<TileCoord>()
                for (y in 0 until n) {
                    for (x in 0 until n) {
                        if (TileMath.distanceToTileKm(lat, lon, x, y, zoom) <= 300.0) expected += TileCoord(zoom, x, y)
                    }
                }
                val actual = TileMath.tilesWithin(lat, lon, 300.0, zoom)
                assertEquals("centre $lat,$lon z$zoom", expected, actual.toSet())
            }
        }
    }

    @Test
    fun antimeridian_wrapsColumns() {
        val tiles = TileMath.tilesWithin(0.0, 179.9, 300.0, 5).toSet()
        val expected = setOf(TileCoord(5, 0, 15), TileCoord(5, 0, 16), TileCoord(5, 31, 15), TileCoord(5, 31, 16))
        assertEquals(expected, tiles)
        assertTrue(tiles.all { it.x in 0 until 32 })
    }

    @Test
    fun nearThePole_rowsStayInRange() {
        val tiles = TileMath.tilesWithin(84.9, 10.0, 300.0, 6)
        assertTrue(tiles.isNotEmpty())
        assertTrue(tiles.all { it.y in 0 until 64 && it.x in 0 until 64 })
    }

    @Test
    fun distanceKm_knownCities() {
        // Beijing to Shanghai is about 1067 km.
        assertEquals(1067.3, TileMath.distanceKm(39.9042, 116.4074, 31.2304, 121.4737), 0.5)
        assertEquals(0.0, TileMath.distanceKm(10.0, 20.0, 10.0, 20.0), 1e-9)
    }

    @Test
    fun distanceToTile_isZeroInside() {
        val x = TileMath.tileX(chengduLon, 7)
        val y = TileMath.tileY(chengduLat, 7)
        assertEquals(0.0, TileMath.distanceToTileKm(chengduLat, chengduLon, x, y, 7), 1e-9)
        assertTrue(TileMath.distanceToTileKm(chengduLat, chengduLon, x + 2, y, 7) > 0.0)
    }
}
