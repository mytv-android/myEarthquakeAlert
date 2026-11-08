package com.github.mytv.myearthquakealert.domain

import com.github.mytv.myearthquakealert.data.model.EewEvent
import kotlin.math.roundToInt

/**
 * Alert drills. The monitoring service feeds every report of a drill script through the
 * same parse → evaluate → present path as a real report, so a drill also exercises the
 * service; drills are presented as STRONG with the 演练 mark everywhere.
 */
object DrillScenario {

    /**
     * The drills offered in 设置 › 模拟演练 (design: 芦山 6.1 级回放等 3 个场景).
     * Fixed-epicenter scenarios use real parameters; relative ones are placed from the
     * user's location. Names come from string resources keyed by [id].
     */
    enum class Scenario(val id: String) {
        /** 2022-06-01 四川芦山 M6.1, 30.37°N 102.94°E, depth 17 km — replayed at its real epicenter. */
        LUSHAN("lushan"),
        /** Near-field strong quake: M6.5, depth 10 km, ~85 km away — always a STRONG-tier alert. */
        NEAR("near"),
        /** Distant moderate quake: M5.0, depth 10 km, ~300 km away — a REMIND-tier alert. */
        FAR("far");

        companion object {
            fun fromId(id: String?): Scenario = entries.firstOrNull { it.id == id } ?: LUSHAN
        }
    }

    const val EVENT_ID = "SIMULATION"
    private const val MAGNITUDE = 6.5
    private const val DEPTH_KM = 10.0
    private const val DISTANCE_KM = 85.0
    private const val BEARING_DEG = 60.0

    /** Real epicenter of the 2022-06-01 Lushan M6.1 earthquake. */
    const val LUSHAN_LATITUDE = 30.37
    const val LUSHAN_LONGITUDE = 102.94
    const val LUSHAN_DEPTH_KM = 17.0

    private const val FAR_DISTANCE_KM = 300.0
    private const val FAR_BEARING_DEG = 240.0

    /** One report of a drill, [offsetMs] after the origin time. */
    private data class Step(val offsetMs: Long, val magnitude: Double, val final: Boolean)

    /** One scripted report: shown [delayMs] after the drill starts. */
    data class DrillReport(val delayMs: Long, val event: EewEvent)

    /**
     * A drill run. [originAt] is the simulated origin time (epoch millis, in the same clock
     * as the `now` passed to [script]); the first report is due immediately.
     */
    data class DrillScript(val scenario: Scenario, val originAt: Long, val reports: List<DrillReport>)

    /** 1.0 drill: a single M6.5 report about 85 km north-east of the user. */
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

    /** First report of [scenario], as if it arrived at [now] (epoch millis). */
    fun create(
        scenario: Scenario,
        userLatitude: Double,
        userLongitude: Double,
        source: String,
        hypocenter: String,
        now: Long = System.currentTimeMillis(),
    ): EewEvent = script(scenario, userLatitude, userLongitude, source, hypocenter, now).reports.first().event

    /** The LUSHAN replay: the real epicenter, three reports as issued (M5.8 → 6.0 → 6.1 final). */
    fun createLushan(source: String, hypocenter: String, now: Long = System.currentTimeMillis()): DrillScript =
        build(
            Scenario.LUSHAN, LUSHAN_LATITUDE, LUSHAN_LONGITUDE, LUSHAN_DEPTH_KM, source, hypocenter, now,
            listOf(Step(5_800, 5.8, false), Step(9_400, 6.0, false), Step(14_200, 6.1, true)),
        )

    /** NEAR: M6.5 at 10 km depth, 85 km north-east of the user; two reports. */
    fun createNear(userLatitude: Double, userLongitude: Double, source: String, hypocenter: String, now: Long = System.currentTimeMillis()): DrillScript {
        val epicenter = SeismicCalculator.offsetPoint(userLatitude, userLongitude, BEARING_DEG, DISTANCE_KM)
        return build(
            Scenario.NEAR, epicenter.latitude, epicenter.longitude, DEPTH_KM, source, hypocenter, now,
            listOf(Step(4_500, 6.2, false), Step(8_500, MAGNITUDE, true)),
        )
    }

    /** FAR: M5.0 at 10 km depth, about 300 km south-west of the user; two reports. */
    fun createFar(userLatitude: Double, userLongitude: Double, source: String, hypocenter: String, now: Long = System.currentTimeMillis()): DrillScript {
        val epicenter = SeismicCalculator.offsetPoint(userLatitude, userLongitude, FAR_BEARING_DEG, FAR_DISTANCE_KM)
        return build(
            Scenario.FAR, epicenter.latitude, epicenter.longitude, DEPTH_KM, source, hypocenter, now,
            listOf(Step(6_000, 4.8, false), Step(11_000, 5.0, true)),
        )
    }

    /** The script of [scenario]; LUSHAN ignores the user's position (fixed epicenter). */
    fun script(
        scenario: Scenario,
        userLatitude: Double,
        userLongitude: Double,
        source: String,
        hypocenter: String,
        now: Long = System.currentTimeMillis(),
    ): DrillScript = when (scenario) {
        Scenario.LUSHAN -> createLushan(source, hypocenter, now)
        Scenario.NEAR -> createNear(userLatitude, userLongitude, source, hypocenter, now)
        Scenario.FAR -> createFar(userLatitude, userLongitude, source, hypocenter, now)
    }

    /** True for events created by a drill. */
    fun isDrillEvent(event: EewEvent): Boolean = event.eventId.startsWith(EVENT_ID)

    private fun build(
        scenario: Scenario,
        latitude: Double,
        longitude: Double,
        depthKm: Double,
        source: String,
        hypocenter: String,
        now: Long,
        steps: List<Step>,
    ): DrillScript {
        val firstOffset = steps.first().offsetMs
        val originAt = now - firstOffset
        val eventId = "$EVENT_ID-${scenario.id}-$originAt"
        val reports = steps.mapIndexed { index, step ->
            DrillReport(
                delayMs = step.offsetMs - firstOffset,
                event = EewEvent(
                    id = "sim-${scenario.id}-${index + 1}",
                    eventId = eventId,
                    source = source,
                    reportTime = ReportTime.formatCst(originAt + step.offsetMs),
                    reportNum = index + 1,
                    originTime = ReportTime.formatCst(originAt),
                    hypocenter = hypocenter,
                    latitude = latitude,
                    longitude = longitude,
                    magnitude = step.magnitude,
                    depth = depthKm,
                    maxIntensity = (SeismicCalculator.calcLocalIntensity(step.magnitude, depthKm, 0.0) * 10).roundToInt() / 10.0,
                    isFinal = step.final,
                ),
            )
        }
        return DrillScript(scenario, originAt, reports)
    }
}
