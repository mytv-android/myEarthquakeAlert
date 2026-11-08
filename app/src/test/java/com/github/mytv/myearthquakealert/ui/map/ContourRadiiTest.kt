package com.github.mytv.myearthquakealert.ui.map

import com.github.mytv.myearthquakealert.domain.SeismicCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContourRadiiTest {

    @Test
    fun radiiShrinkAsTheLevelRises() {
        val radii = contourRadii(magnitude = 6.0, depthKm = 10.0, levels = 3..7)
        assertEquals(listOf(3, 4, 5, 6, 7), radii.map { it.first })
        radii.zipWithNext().forEach { (outer, inner) -> assertTrue("$outer then $inner", outer.second > inner.second) }
    }

    @Test
    fun eachRadiusIsWhereTheIntensityFallsToItsLevel() {
        contourRadii(magnitude = 6.0, depthKm = 10.0, levels = 3..7).forEach { (level, r) ->
            assertEquals(level.toDouble(), SeismicCalculator.calcLocalIntensity(6.0, 10.0, r), 0.01)
        }
    }

    @Test
    fun largerQuakesReachFarther() {
        val small = contourRadii(5.0, 10.0, 3..3).single().second
        val large = contourRadii(7.0, 10.0, 3..3).single().second
        assertTrue(large > small)
    }

    @Test
    fun levelsNeverReachedAreOmitted() {
        val radii = contourRadii(magnitude = 3.0, depthKm = 10.0, levels = 3..7)
        assertTrue(radii.all { (level, _) -> SeismicCalculator.calcLocalIntensity(3.0, 10.0, 0.5) >= level })
        assertTrue(radii.size < 5)
    }
}
