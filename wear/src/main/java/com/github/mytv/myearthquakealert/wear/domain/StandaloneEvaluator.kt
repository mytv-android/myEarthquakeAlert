package com.github.mytv.myearthquakealert.wear.domain

import com.github.mytv.myearthquakealert.wear.data.WatchAlert
import com.github.mytv.myearthquakealert.wear.data.WireQuake
import com.github.mytv.myearthquakealert.wear.data.WireStatus
import com.github.mytv.myearthquakealert.wear.source.EewEvent
import com.github.mytv.myearthquakealert.wear.source.EewSource
import kotlin.math.round

/**
 * Evaluates a report received by the watch's own (standalone) monitoring, with the phone's
 * rules: tiers as in `:app` `domain.AlertEvaluator.tierOf`, the 10 km default depth and the
 * "too late, history only" cut of `service.AlertPresenter` (copied logic, keep in sync).
 */
object StandaloneEvaluator {

    const val DEFAULT_DEPTH_KM = 10.0

    /** Reports older than this (by report time) are replays, as on the phone. */
    const val STALE_REPORT_MS = 10 * 60_000L

    enum class Tier { RECORD, REMIND, STRONG }

    /** The phone's 预警 thresholds; the last synced phone status provides them. */
    data class Thresholds(
        val minMagnitude: Double = 0.0,
        val minIntensity: Int = 0,
        val strongThreshold: Int = 5,
    ) {
        companion object {
            fun of(status: WireStatus?): Thresholds =
                status?.let { Thresholds(it.minMagnitude, it.minIntensity, it.strongThreshold) } ?: Thresholds()
        }
    }

    data class Location(val latitude: Double, val longitude: Double)

    data class Result(
        val tier: Tier,
        /** The history row (the 最近地震 list), always produced. */
        val quake: WireQuake,
        /** The alert to present, or null (record tier, no location, late or stale). */
        val alert: WatchAlert?,
    )

    fun tierOf(localCsis: Double, magnitude: Double, t: Thresholds): Tier {
        val intensityOk = t.minIntensity == 0 || localCsis >= t.minIntensity
        val magnitudeOk = t.minMagnitude == 0.0 || magnitude >= t.minMagnitude
        return when {
            t.strongThreshold > 0 && localCsis >= t.strongThreshold -> Tier.STRONG
            intensityOk && magnitudeOk -> Tier.REMIND
            else -> Tier.RECORD
        }
    }

    /**
     * [originAt] is the origin on the watch clock (server origin shifted by the measured
     * skew), [now] the watch clock. A same-event [previous] alert keeps its presentation
     * time so further reports do not refill the ring.
     */
    fun evaluate(
        event: EewEvent,
        location: Location?,
        thresholds: Thresholds,
        originAt: Long,
        receivedAt: Long,
        reportAt: Long?,
        now: Long,
        previous: WatchAlert? = null,
    ): Result {
        val depth = event.depth ?: DEFAULT_DEPTH_KM
        val distance = location?.let { SeismicCalculator.haversineDistance(it.latitude, it.longitude, event.latitude, event.longitude) }
        val csis = distance?.let { SeismicCalculator.calcLocalIntensity(event.magnitude, depth, it) } ?: 0.0
        val tier = if (distance == null) Tier.RECORD else tierOf(csis, event.magnitude, thresholds)
        val quake = WireQuake(
            eventId = event.eventId,
            source = event.source,
            sourceLabel = EewSource.labelOf(event.source),
            place = event.hypocenter,
            magnitude = event.magnitude,
            depthKm = event.depth,
            latitude = event.latitude,
            longitude = event.longitude,
            originTime = ReportTime.parseCst(event.originTime) ?: 0L,
            maxIntensity = event.maxIntensity,
            localCsis = round1(csis),
            distanceKm = round1(distance ?: 0.0),
            tier = tier.name,
            reports = event.reportNum,
            isFinal = event.isFinal,
            receivedAt = receivedAt,
        )
        if (tier == Tier.RECORD || location == null || distance == null) return Result(tier, quake, null)
        if (reportAt != null && receivedAt - reportAt > STALE_REPORT_MS) return Result(tier, quake, null)
        val arrival = SeismicCalculator.calcWaveArrival(depth, distance)
        val same = previous?.takeIf { it.eventId == event.eventId }
        val alert = WatchAlert(
            eventId = event.eventId,
            sourceLabel = EewSource.labelOf(event.source),
            place = event.hypocenter,
            magnitude = event.magnitude,
            depthKm = event.depth,
            latitude = event.latitude,
            longitude = event.longitude,
            userLatitude = location.latitude,
            userLongitude = location.longitude,
            localCsis = csis,
            distanceKm = distance,
            // A report never downgrades an alert in progress.
            strong = tier == Tier.STRONG || same?.strong == true,
            drill = false,
            reportNum = event.reportNum,
            isFinal = event.isFinal,
            originAt = originAt,
            pArrivalAt = originAt + (arrival.pWaveSeconds * 1000).toLong(),
            sArrivalAt = originAt + (arrival.sWaveSeconds * 1000).toLong(),
            presentedAt = same?.presentedAt ?: now,
            viaPhone = false,
        )
        if (same == null && Countdown.isLate(alert, now)) return Result(tier, quake, null)
        return Result(tier, quake, alert)
    }

    private fun round1(v: Double): Double = round(v * 10.0) / 10.0
}
