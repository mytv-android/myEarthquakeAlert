package com.github.mytv.myearthquakealert.domain

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
}
