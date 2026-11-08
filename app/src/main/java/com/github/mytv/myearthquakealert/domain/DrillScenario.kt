package com.github.mytv.myearthquakealert.domain

import com.github.mytv.myearthquakealert.data.model.EewEvent

/**
 * The alert drill: a realistic M6.5 at 10 km depth, about 85 km north-east of the user.
 * The monitoring service feeds it through the same evaluate → present path as a real
 * report, so a drill also exercises the service.
 */
object DrillScenario {

    const val EVENT_ID = "SIMULATION"
    private const val MAGNITUDE = 6.5
    private const val DEPTH_KM = 10.0
    private const val DISTANCE_KM = 85.0
    private const val BEARING_DEG = 60.0

    fun create(userLatitude: Double, userLongitude: Double, source: String, hypocenter: String): EewEvent {
        val epicenter = SeismicCalculator.offsetPoint(userLatitude, userLongitude, BEARING_DEG, DISTANCE_KM)
        return EewEvent(
            id = "sim-1",
            eventId = EVENT_ID,
            source = source,
            reportTime = "",
            reportNum = 1,
            originTime = "",
            hypocenter = hypocenter,
            latitude = epicenter.latitude,
            longitude = epicenter.longitude,
            magnitude = MAGNITUDE,
            depth = DEPTH_KM,
            maxIntensity = 5.0,
        )
    }
}
