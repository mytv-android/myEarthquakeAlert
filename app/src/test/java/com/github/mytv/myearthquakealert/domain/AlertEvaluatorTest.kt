package com.github.mytv.myearthquakealert.domain

import org.junit.Assert.*
import org.junit.Test

class AlertEvaluatorTest {

    @Test
    fun shouldAction_zeroThresholds_alwaysTrue() {
        val result = AlertEvaluator.shouldAlert(
            localCsis = 1.0,
            magnitude = 2.0,
            minIntensity = 0,
            minMagnitude = 0.0,
        )
        assertTrue(result)
    }

    @Test
    fun shouldAction_intensityThresholdMet_returnsTrue() {
        val result = AlertEvaluator.shouldAlert(
            localCsis = 4.0,
            magnitude = 5.0,
            minIntensity = 3,
            minMagnitude = 0.0,
        )
        assertTrue(result)
    }

    @Test
    fun shouldAction_intensityThresholdNotMet_returnsFalse() {
        val result = AlertEvaluator.shouldAlert(
            localCsis = 2.0,
            magnitude = 5.0,
            minIntensity = 3,
            minMagnitude = 0.0,
        )
        assertFalse(result)
    }

    @Test
    fun shouldAction_magnitudeThresholdNotMet_returnsFalse() {
        val result = AlertEvaluator.shouldAlert(
            localCsis = 4.0,
            magnitude = 2.0,
            minIntensity = 3,
            minMagnitude = 3.0,
        )
        assertFalse(result)
    }

    @Test
    fun shouldAction_bothThresholdsMet_returnsTrue() {
        val result = AlertEvaluator.shouldAlert(
            localCsis = 5.0,
            magnitude = 4.5,
            minIntensity = 3,
            minMagnitude = 4.0,
        )
        assertTrue(result)
    }

    @Test
    fun isIntense_aboveThreshold_returnsTrue() {
        assertTrue(AlertEvaluator.isIntense(localCsis = 6.0, intenseThreshold = 5))
    }

    @Test
    fun isIntense_belowThreshold_returnsFalse() {
        assertFalse(AlertEvaluator.isIntense(localCsis = 4.0, intenseThreshold = 5))
    }

    @Test
    fun isIntense_equalThreshold_returnsTrue() {
        assertTrue(AlertEvaluator.isIntense(localCsis = 5.0, intenseThreshold = 5))
    }
}
