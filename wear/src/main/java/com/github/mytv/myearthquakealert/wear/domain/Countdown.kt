package com.github.mytv.myearthquakealert.wear.domain

import com.github.mytv.myearthquakealert.wear.data.WatchAlert
import com.github.mytv.myearthquakealert.wear.data.WireAlert
import kotlin.math.ceil

/**
 * Countdown math of the watch alert (design §4: 倒计时 → 本地烈度 → 距离; 波到达之后).
 *
 * Stages after the S wave reaches the user: [Stage.ARRIVED] (red ring, 「地震波已到达」)
 * for [SUMMARY_AFTER_MS], then [Stage.SUMMARY] until [CLOSE_AFTER_MS], then [Stage.EXPIRED]
 * (the alert closes by itself).
 */
object Countdown {

    /** A message "received before it was sent" by more than this means the watch clock is behind. */
    const val CLOCK_TOLERANCE_MS = 5_000L

    const val SUMMARY_AFTER_MS = 60_000L
    const val CLOSE_AFTER_MS = 180_000L

    /** Strong vibration runs until this long after the S wave (design: 强振动). */
    const val VIBRATE_AFTER_ARRIVAL_MS = 10_000L

    enum class Stage { COUNTDOWN, ARRIVED, SUMMARY, EXPIRED }

    data class Snapshot(
        val stage: Stage,
        /** Whole seconds until the S wave (rounded up, as the phone shows them); 0 once arrived. */
        val sSeconds: Int,
        /** Whole seconds until the P wave; 0 once it has arrived. */
        val pSeconds: Int,
        /** Remaining share of the countdown ring, 1 when presented and 0 at arrival. */
        val fraction: Float,
    ) {
        val arrived: Boolean get() = stage != Stage.COUNTDOWN
        val pArrived: Boolean get() = pSeconds == 0
    }

    /**
     * Offset to add to phone timestamps to express them on the watch clock. Wear OS keeps the
     * watch clock in step with the phone, so a positive difference is taken as transport
     * latency and not corrected (a late message counts down less, never more). Only a watch
     * clock clearly behind the phone (received [CLOCK_TOLERANCE_MS] before it was sent) is
     * corrected, else the countdown would run long.
     */
    fun clockOffset(sentAt: Long, receivedAt: Long): Long {
        if (sentAt <= 0L) return 0L
        val d = receivedAt - sentAt
        return if (d < -CLOCK_TOLERANCE_MS) d else 0L
    }

    /**
     * The watch alert for a phone alert received at [receivedAt]. Further reports of the
     * same event keep the [previous] presentation time, so the ring does not refill.
     */
    fun fromPhone(wire: WireAlert, receivedAt: Long, previous: WatchAlert? = null): WatchAlert {
        val offset = clockOffset(wire.sentAt, receivedAt)
        val origin = wire.originAt + offset
        val same = previous?.takeIf { it.eventId == wire.eventId }
        return WatchAlert(
            eventId = wire.eventId,
            sourceLabel = wire.sourceLabel,
            place = wire.place,
            magnitude = wire.magnitude,
            depthKm = wire.depthKm,
            latitude = wire.latitude,
            longitude = wire.longitude,
            userLatitude = wire.userLatitude,
            userLongitude = wire.userLongitude,
            localCsis = wire.localCsis,
            distanceKm = wire.distanceKm,
            strong = wire.tier == "STRONG" || wire.drill,
            drill = wire.drill,
            reportNum = wire.reportNum,
            isFinal = wire.isFinal,
            originAt = origin,
            pArrivalAt = origin + (wire.pWaveSeconds * 1000).toLong(),
            sArrivalAt = origin + (wire.sWaveSeconds * 1000).toLong(),
            presentedAt = same?.presentedAt ?: receivedAt,
            viaPhone = true,
        )
    }

    fun snapshot(alert: WatchAlert, now: Long): Snapshot {
        val sRemainingMs = alert.sArrivalAt - now
        val pRemainingMs = alert.pArrivalAt - now
        val stage = when {
            sRemainingMs > 0 -> Stage.COUNTDOWN
            -sRemainingMs < SUMMARY_AFTER_MS -> Stage.ARRIVED
            -sRemainingMs < CLOSE_AFTER_MS -> Stage.SUMMARY
            else -> Stage.EXPIRED
        }
        val total = (alert.sArrivalAt - alert.presentedAt).coerceAtLeast(1_000L)
        val fraction = (sRemainingMs.toFloat() / total).coerceIn(0f, 1f)
        return Snapshot(
            stage = stage,
            sSeconds = wholeSeconds(sRemainingMs),
            pSeconds = wholeSeconds(pRemainingMs),
            fraction = if (stage == Stage.COUNTDOWN) fraction else 0f,
        )
    }

    /** The alert came too late to be useful (the shaking is over): history only. */
    fun isLate(alert: WatchAlert, now: Long): Boolean = now - alert.sArrivalAt >= SUMMARY_AFTER_MS

    /** How long the strong vibration should still run at [now], 0 when it should stop. */
    fun vibrationRemainingMs(alert: WatchAlert, now: Long): Long =
        (alert.sArrivalAt + VIBRATE_AFTER_ARRIVAL_MS - now).coerceAtLeast(0L)

    private fun wholeSeconds(ms: Long): Int = if (ms <= 0) 0 else ceil(ms / 1000.0).toInt()
}
