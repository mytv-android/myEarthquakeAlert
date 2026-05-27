package com.github.mytv.myearthquakealert.domain

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class SeismicCalculatorTest {

    @Test
    fun haversine_distance_betweenTwoPoints() {
        val dist = SeismicCalculator.haversineDistance(39.9, 116.4, 30.6, 104.1)
        assertTrue("Expected ~1520km, got $dist", abs(dist - 1520.0) < 50.0)
    }

    @Test
    fun haversine_samePoint_returnsZero() {
        val dist = SeismicCalculator.haversineDistance(30.0, 104.0, 30.0, 104.0)
        assertEquals(0.0, dist, 0.1)
    }

    @Test
    fun chordDistance_shallowEarthquake() {
        val chord = SeismicCalculator.chordDistance(10.0, 100.0)
        assertTrue("Chord should be > surface distance", chord >= 100.0)
    }

    @Test
    fun calcLocalIntensity_closeShallowQuake() {
        val csis = SeismicCalculator.calcLocalIntensity(5.0, 10.0, 50.0)
        assertTrue("M5.0 at 50km should produce CSIS > 2, got $csis", csis > 2.0)
    }

    @Test
    fun calcLocalIntensity_distantQuake_lowIntensity() {
        val csis = SeismicCalculator.calcLocalIntensity(5.0, 10.0, 1000.0)
        assertTrue("M5.0 at 1000km should produce CSIS < 2, got $csis", csis < 2.0)
    }

    @Test
    fun calcLocalIntensity_largeCloseQuake_highIntensity() {
        val csis = SeismicCalculator.calcLocalIntensity(7.0, 10.0, 30.0)
        assertTrue("M7.0 at 30km should produce CSIS > 5, got $csis", csis > 5.0)
    }

    @Test
    fun calcLocalIntensity_clampsToRange() {
        val high = SeismicCalculator.calcLocalIntensity(9.0, 5.0, 10.0)
        assertTrue("CSIS should be <= 12, got $high", high <= 12.0)

        val low = SeismicCalculator.calcLocalIntensity(1.0, 100.0, 2000.0)
        assertTrue("CSIS should be >= 0, got $low", low >= 0.0)
    }

    @Test
    fun waveArrival_sWaveLaterThanPWave() {
        val arrival = SeismicCalculator.calcWaveArrival(10.0, 100.0)
        assertTrue("S-wave should arrive after P-wave", arrival.sWaveSeconds > arrival.pWaveSeconds)
    }

    @Test
    fun waveArrival_closerDistance_shorterTime() {
        val close = SeismicCalculator.calcWaveArrival(10.0, 100.0)
        val far = SeismicCalculator.calcWaveArrival(10.0, 500.0)
        assertTrue("Closer distance should have shorter P-wave time", close.pWaveSeconds < far.pWaveSeconds)
    }

    @Test
    fun calcWaveRadius_increasesOverTime() {
        val r1 = SeismicCalculator.calcWaveRadius(10.0, 30.0, isPWave = true)
        val r2 = SeismicCalculator.calcWaveRadius(10.0, 60.0, isPWave = true)
        assertTrue("Wave radius should increase over time", r2 > r1)
    }

    @Test
    fun calcWaveRadius_pWaveFasterThanSWave() {
        val pR = SeismicCalculator.calcWaveRadius(10.0, 60.0, isPWave = true)
        val sR = SeismicCalculator.calcWaveRadius(10.0, 60.0, isPWave = false)
        assertTrue("P-wave radius should be larger than S-wave at same time", pR > sR)
    }
}
