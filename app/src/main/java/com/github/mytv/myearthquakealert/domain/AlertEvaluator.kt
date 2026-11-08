package com.github.mytv.myearthquakealert.domain

import com.github.mytv.myearthquakealert.service.AlertTier

object AlertEvaluator {

    fun shouldAlert(
        localCsis: Double,
        magnitude: Double,
        minIntensity: Int,
        minMagnitude: Double,
    ): Boolean {
        val intensityOk = minIntensity == 0 || localCsis >= minIntensity
        val magnitudeOk = minMagnitude == 0.0 || magnitude >= minMagnitude
        return intensityOk && magnitudeOk
    }

    fun isIntense(localCsis: Double, intenseThreshold: Int): Boolean {
        return localCsis >= intenseThreshold
    }

    /**
     * How intrusive an alert is (design §4 预警体验):
     * - STRONG when the local intensity reaches [intenseThreshold] (default 5). This does not
     *   depend on the reminder thresholds: strong shaking is never filtered out.
     * - REMIND when magnitude ≥ [minMagnitude] and local intensity ≥ [minIntensity]
     *   (0 disables either threshold, as in 1.0).
     * - RECORD otherwise: written to the history only.
     *
     * Drills do not come through here: they use their scenario's tier (STRONG; REMIND for FAR).
     */
    fun tierOf(
        localCsis: Double,
        magnitude: Double,
        minMagnitude: Double,
        minIntensity: Int,
        intenseThreshold: Int,
    ): AlertTier = when {
        intenseThreshold > 0 && isIntense(localCsis, intenseThreshold) -> AlertTier.STRONG
        shouldAlert(localCsis, magnitude, minIntensity, minMagnitude) -> AlertTier.REMIND
        else -> AlertTier.RECORD
    }
}
