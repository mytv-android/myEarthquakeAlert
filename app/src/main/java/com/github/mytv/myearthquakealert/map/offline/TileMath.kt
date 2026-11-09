package com.github.mytv.myearthquakealert.map.offline

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tan

/** One slippy-map tile in the XYZ scheme shared by osmdroid, AMap and OSM. */
data class TileCoord(val zoom: Int, val x: Int, val y: Int)

/**
 * Web-Mercator tile arithmetic for the offline precache (design §7 离线预缓存): which tiles of
 * a zoom level come within a radius of the user. Pure; no Android or osmdroid types.
 */
object TileMath {

    /** Latitude limit of Web Mercator. */
    const val MAX_LATITUDE = 85.05112878

    private const val EARTH_RADIUS_KM = 6371.0088
    private const val KM_PER_DEGREE = PI * EARTH_RADIUS_KM / 180.0

    fun tileCount(zoom: Int): Int {
        require(zoom in 0..30) { "zoom $zoom" }
        return 1 shl zoom
    }

    /** Column of [longitude]; not wrapped, so longitudes beyond ±180° give columns outside 0 until n. */
    fun tileX(longitude: Double, zoom: Int): Int =
        floor((longitude + 180.0) / 360.0 * tileCount(zoom)).toInt()

    /** Row of [latitude] (0 = north), clamped to the Mercator limits. */
    fun tileY(latitude: Double, zoom: Int): Int {
        val n = tileCount(zoom)
        val rad = Math.toRadians(latitude.coerceIn(-MAX_LATITUDE, MAX_LATITUDE))
        val y = floor((1.0 - ln(tan(rad) + 1.0 / cos(rad)) / PI) / 2.0 * n).toInt()
        return y.coerceIn(0, n - 1)
    }

    /** West edge of column [x] (unwrapped). */
    fun westLongitude(x: Int, zoom: Int): Double = x.toDouble() / tileCount(zoom) * 360.0 - 180.0

    /** North edge of row [y]. */
    fun northLatitude(y: Int, zoom: Int): Double =
        Math.toDegrees(atan(sinh(PI - 2.0 * PI * y / tileCount(zoom))))

    /** Great-circle distance in km. */
    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_KM * asin(sqrt(min(1.0, a)))
    }

    /**
     * Distance from a point to the nearest point of tile ([x] unwrapped, [y]) at [zoom]: the
     * point clamped into the tile's latitude / longitude box. 0 inside the tile.
     */
    fun distanceToTileKm(latitude: Double, longitude: Double, x: Int, y: Int, zoom: Int): Double {
        val west = westLongitude(x, zoom)
        val east = westLongitude(x + 1, zoom)
        val north = northLatitude(y, zoom)
        val south = northLatitude(y + 1, zoom)
        return distanceKm(latitude, longitude, latitude.coerceIn(south, north), longitude.coerceIn(west, east))
    }

    /**
     * Tiles of [zoom] that come within [radiusKm] of the centre, columns wrapped across the
     * antimeridian. Ordered north to south, west to east.
     */
    fun tilesWithin(latitude: Double, longitude: Double, radiusKm: Double, zoom: Int): List<TileCoord> {
        require(radiusKm >= 0.0) { "radius $radiusKm" }
        val n = tileCount(zoom)
        val dLat = radiusKm / KM_PER_DEGREE
        val north = min(latitude + dLat, MAX_LATITUDE)
        val south = max(latitude - dLat, -MAX_LATITUDE)
        val cosLat = cos(Math.toRadians(max(abs(north), abs(south)))).coerceAtLeast(1e-9)
        val dLon = min(radiusKm / (KM_PER_DEGREE * cosLat), 180.0)
        val xs = tileX(longitude - dLon, zoom)..tileX(longitude + dLon, zoom)
        val ys = tileY(north, zoom)..tileY(south, zoom)
        val result = LinkedHashSet<TileCoord>()
        for (y in ys) {
            for (x in xs) {
                if (distanceToTileKm(latitude, longitude, x, y, zoom) <= radiusKm) {
                    result += TileCoord(zoom, Math.floorMod(x, n), y)
                }
            }
        }
        return result.toList()
    }

    /** [tilesWithin] for every zoom of [zooms], lowest zoom first. */
    fun tilesWithin(latitude: Double, longitude: Double, radiusKm: Double, zooms: IntRange): List<TileCoord> =
        zooms.flatMap { tilesWithin(latitude, longitude, radiusKm, it) }
}
