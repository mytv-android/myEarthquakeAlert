package com.github.mytv.myearthquakealert.wear

import com.github.mytv.myearthquakealert.data.model.UserLocation
import com.github.mytv.myearthquakealert.data.source.EewSource
import com.github.mytv.myearthquakealert.domain.ReportTime
import com.github.mytv.myearthquakealert.service.AlertData
import com.github.mytv.myearthquakealert.service.EewRecord
import com.github.mytv.myearthquakealert.service.MonitorState
import kotlin.math.roundToLong

/** Alert thresholds of the phone settings that the watch's standalone monitoring mirrors. */
data class WearThresholds(
    val minMagnitude: Double = 0.0,
    val minIntensity: Int = 0,
    val strongThreshold: Int = 5,
)

/**
 * Maps the phone's service state to [WearProtocol] payloads. Pure: no Android or Data
 * Layer calls, so the "meaningful change" rules are unit-tested.
 *
 * The mapped values carry only what the watch shows. Noisy fields (frame times, latencies,
 * retry timers) are left out and the location is rounded to 0.01° (about 1 km), so equal
 * mapped values mean nothing visible changed and nothing needs to be sent.
 */
object WearMapping {

    /** Events sent to the watch (newest first). */
    const val QUAKE_LIMIT = 10

    fun status(state: MonitorState, thresholds: WearThresholds, location: UserLocation?): WireStatus =
        WireStatus(
            phase = state.phase.name,
            phaseSince = state.phaseSince,
            runningSince = state.runningSince,
            pauseUntil = state.pauseUntil,
            sources = state.sources.map { WireSource(it.source.name, it.source.label, it.link.name) },
            missing = state.missing.map { it.name }.sorted(),
            userLatitude = location?.takeIf { it.isKnown }?.latitude?.let(::round2),
            userLongitude = location?.takeIf { it.isKnown }?.longitude?.let(::round2),
            minMagnitude = thresholds.minMagnitude,
            minIntensity = thresholds.minIntensity,
            strongThreshold = thresholds.strongThreshold,
            updatedAt = 0L,
        )

    fun quake(record: EewRecord): WireQuake {
        val e = record.event
        return WireQuake(
            eventId = e.eventId,
            source = e.source,
            sourceLabel = EewSource.labelOf(e.source),
            place = e.hypocenter,
            magnitude = e.magnitude,
            depthKm = e.depth,
            latitude = e.latitude,
            longitude = e.longitude,
            originTime = ReportTime.parseCst(e.originTime) ?: 0L,
            maxIntensity = e.maxIntensity,
            localCsis = round1(record.localCsis),
            distanceKm = round1(record.distanceKm),
            tier = record.tier.name,
            reports = record.reports,
            isFinal = e.isFinal,
            receivedAt = record.firstReceivedAt,
        )
    }

    fun quakes(records: List<EewRecord>): WireQuakes =
        WireQuakes(quakes = records.take(QUAKE_LIMIT).map(::quake), updatedAt = 0L)

    fun alert(data: AlertData, sentAt: Long): WireAlert {
        val e = data.event
        return WireAlert(
            eventId = e.eventId,
            source = e.source,
            sourceLabel = EewSource.labelOf(e.source),
            place = e.hypocenter,
            magnitude = e.magnitude,
            depthKm = e.depth,
            latitude = e.latitude,
            longitude = e.longitude,
            userLatitude = data.userLatitude,
            userLongitude = data.userLongitude,
            originAt = data.originAt,
            pWaveSeconds = data.pWaveSeconds,
            sWaveSeconds = data.sWaveSeconds,
            localCsis = data.localCsis,
            distanceKm = data.distanceKm,
            tier = data.tier.name,
            drill = data.isSimulation,
            reportNum = e.reportNum,
            isFinal = e.isFinal,
            presentedAt = data.presentedAt,
            sentAt = sentAt,
        )
    }

    /**
     * What makes an alert worth re-sending: a new event, a new report, or a change of the
     * numbers the watch shows. Countdown ticks on the phone do not change it.
     */
    data class AlertKey(
        val eventId: String,
        val reportNum: Int,
        val tier: String,
        val magnitudeX10: Long,
        val sArrivalSecond: Long,
        val csisX10: Long,
    )

    fun alertKey(data: AlertData): AlertKey = AlertKey(
        eventId = data.event.eventId,
        reportNum = data.event.reportNum,
        tier = data.tier.name,
        magnitudeX10 = (data.event.magnitude * 10).roundToLong(),
        sArrivalSecond = data.sArrivalAt / 1000L,
        csisX10 = (data.localCsis * 10).roundToLong(),
    )

    private fun round2(v: Double): Double = (v * 100).roundToLong() / 100.0
    private fun round1(v: Double): Double = (v * 10).roundToLong() / 10.0
}
