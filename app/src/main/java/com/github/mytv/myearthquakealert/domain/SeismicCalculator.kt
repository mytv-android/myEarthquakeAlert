package com.github.mytv.myearthquakealert.domain

import kotlin.math.*

object SeismicCalculator {

    private const val EARTH_RADIUS_KM = 6371.0

    fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_KM * c
    }

    fun chordDistance(depthKm: Double, surfaceDistanceKm: Double): Double {
        val a = EARTH_RADIUS_KM - depthKm
        val R = EARTH_RADIUS_KM
        val dis = surfaceDistanceKm
        val lineDis = sqrt(a * a + R * R - 2 * a * R * cos(dis / R))
        return lineDis
    }

    fun calcLocalIntensity(magnitude: Double, depthKm: Double, distanceKm: Double): Double {
        val M = magnitude
        val lineDis = chordDistance(depthKm, distanceKm)

        val long = 10.0.pow((M - 3.821) / 1.86)

        val hypoDis = maxOf(
            lineDis - 10.0 - long,
            distanceKm - long,
            0.2 * (lineDis - 10.0),
            0.0
        )

        val csis1 = 1.297 * M - 4.368 * log10(distanceKm + 15) + 5.363
        val csis2 = 1.297 * M - 4.368 * log10(hypoDis + 15) + 5.363

        val csis = (csis1 + csis2) / 2.0
        return csis.coerceIn(0.0, 12.0)
    }

    data class WaveArrival(
        val pWaveSeconds: Double,
        val sWaveSeconds: Double,
    )

    fun calcWaveArrival(depthKm: Double, distanceKm: Double): WaveArrival {
        val pTime = TravelTimeTables.pWaveTime(distanceKm, depthKm)
        val sTime = TravelTimeTables.sWaveTime(distanceKm, depthKm)
        return WaveArrival(pWaveSeconds = pTime, sWaveSeconds = sTime)
    }

    fun calcWaveRadius(depthKm: Double, elapsedSeconds: Double, isPWave: Boolean): Double {
        val table = if (isPWave) TravelTimeTables.pTimes else TravelTimeTables.sTimes

        var lo = 0.0
        var hi = 2000.0
        for (i in 0..50) {
            val mid = (lo + hi) / 2
            val t = TravelTimeTables.interpolate(table, mid, depthKm)
            if (t < elapsedSeconds) lo = mid else hi = mid
        }
        return (lo + hi) / 2
    }

    data class GeoOffset(val latitude: Double, val longitude: Double)

    /**
     * Point at [distanceKm] from ([lat], [lon]) along [bearingDeg] (clockwise from north).
     * Used to place simulated epicenters at a realistic distance from the user.
     */
    fun offsetPoint(lat: Double, lon: Double, bearingDeg: Double, distanceKm: Double): GeoOffset {
        val phi1 = Math.toRadians(lat)
        val lambda1 = Math.toRadians(lon)
        val theta = Math.toRadians(bearingDeg)
        val delta = distanceKm / EARTH_RADIUS_KM

        val phi2 = asin(sin(phi1) * cos(delta) + cos(phi1) * sin(delta) * cos(theta))
        val lambda2 = lambda1 + atan2(
            sin(theta) * sin(delta) * cos(phi1),
            cos(delta) - sin(phi1) * sin(phi2),
        )
        return GeoOffset(Math.toDegrees(phi2), Math.toDegrees(lambda2))
    }
}
