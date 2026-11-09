package com.github.mytv.myearthquakealert.ui.alert

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.github.mytv.myearthquakealert.diagnostics.Diagnostics
import com.github.mytv.myearthquakealert.domain.SeismicCalculator
import com.github.mytv.myearthquakealert.service.AlertData
import com.github.mytv.myearthquakealert.service.AlertTier
import kotlinx.coroutines.delay
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/** How an alert is laid out (design §4 预警体验 and §6 多端形态). */
enum class AlertPresentation {
    /** Floating card over whatever is in use (REMIND tier, narrow windows). */
    CARD,
    /** Full-screen takeover (STRONG tier, lock screen, AlertActivity). */
    FULL_SCREEN,
    /** TV REMIND tier: subtitle bar at the bottom and a corner map; the programme keeps playing. */
    TV_TICKER,
}

/**
 * Picks the presentation for an alert. AlertOverlayService can call this to size its
 * window: CARD wraps its content, FULL_SCREEN and TV_TICKER need a MATCH_PARENT window.
 */
fun alertPresentation(tier: AlertTier, wideLayout: Boolean, isTelevision: Boolean): AlertPresentation = when {
    isTelevision -> if (tier == AlertTier.STRONG && wideLayout) AlertPresentation.FULL_SCREEN else AlertPresentation.TV_TICKER
    tier == AlertTier.STRONG && wideLayout -> AlertPresentation.FULL_SCREEN
    else -> AlertPresentation.CARD
}

/** True on Android TV / leanback devices (no Activity needed, works from a service window). */
fun isTelevisionDevice(context: Context): Boolean {
    val uiMode = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    return uiMode?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
}

/** Seconds after the S wave arrived when the full-screen alert turns into the summary card. */
const val SummaryDelaySeconds = 60.0

/** A device-clock time corrected by the service's NTP check (offset = 本机时钟 − ntp.json). */
fun correctedTime(deviceMillis: Long): Long = deviceMillis - (Diagnostics.metrics.value.clockOffsetMs ?: 0L)

/** Current time on the corrected clock the countdown runs on. */
fun alertNow(): Long = correctedTime(System.currentTimeMillis())

// ThreadLocal.withInitial needs API 26; minSdk is 23.
private val originFormat = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue() =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("Asia/Shanghai") }
}

/** Parses an [com.github.mytv.myearthquakealert.data.model.EewEvent.originTime] (CST); null when empty or malformed. */
fun parseOriginTime(originTime: String): Long? {
    if (originTime.isBlank()) return null
    return try {
        originFormat.get()?.parse(originTime.trim())?.time
    } catch (e: ParseException) {
        null
    }
}

/**
 * Where the countdown runs from. Wave arrival times are counted from the origin time; when
 * the origin time is missing (drills) or implausible against when the alert was presented,
 * the presentation time stands in for it. [presentedAt] is on the same clock as the result.
 */
fun alertZero(alert: AlertData, presentedAt: Long): Long {
    val origin = parseOriginTime(alert.event.originTime)
    return if (origin != null && origin in (presentedAt - 10 * 60_000L)..(presentedAt + 5_000L)) origin else presentedAt
}

/** One snapshot of the alert clock. All times in seconds. */
@Immutable
data class AlertTimeline(
    val elapsed: Double,
    val pSeconds: Double,
    val sSeconds: Double,
    /** Elapsed seconds when the alert was first shown; the ring runs from here to the S wave. */
    val shownAt: Double,
    /** Epoch millis the wave times count from (origin time, or presentation time for drills). */
    val zeroAt: Long = 0L,
) {
    val sRemaining: Double get() = max(0.0, sSeconds - elapsed)
    val pRemaining: Double get() = max(0.0, pSeconds - elapsed)
    val sArrived: Boolean get() = elapsed >= sSeconds
    val pArrived: Boolean get() = elapsed >= pSeconds
    /** Whole seconds shown in the countdown. */
    val sCountdown: Int get() = ceil(sRemaining).toInt()
    val pCountdown: Int get() = ceil(pRemaining).toInt()
    /** Remaining fraction of the warning time, 1 → 0 (the ring depletes). */
    val fraction: Float
        get() {
            val total = sSeconds - shownAt
            return if (total <= 0.0) 0f else (sRemaining / total).toFloat().coerceIn(0f, 1f)
        }
    /** Seconds of warning the user got before the S wave (预警提前量). */
    val leadSeconds: Int get() = max(0.0, sSeconds - shownAt).roundToInt()
    val summaryDue: Boolean get() = elapsed >= sSeconds + SummaryDelaySeconds
    /** Epoch millis of the S-wave arrival. */
    val sArrivalAt: Long get() = zeroAt + (sSeconds * 1000).toLong()
}

/**
 * Ticking alert clock. The first presentation time of an event anchors it, so later
 * reports of the same event only refresh numbers instead of restarting the countdown.
 */
@Composable
fun rememberAlertTimeline(alert: AlertData, tickMs: Long = 100L): State<AlertTimeline> {
    val anchor = remember(alert.event.eventId) { correctedTime(alert.presentedAt) }
    val zero = remember(alert.event.eventId, alert.event.originTime) { alertZero(alert, anchor) }
    var now by remember { mutableLongStateOf(alertNow()) }
    LaunchedEffect(alert.event.eventId) {
        while (true) {
            now = alertNow()
            delay(tickMs)
        }
    }
    val shownAt = remember(zero, anchor) { max(0.0, (anchor - zero) / 1000.0) }
    return remember(alert.pWaveSeconds, alert.sWaveSeconds, zero, shownAt) {
        derivedStateOf { AlertTimeline((now - zero) / 1000.0, alert.pWaveSeconds, alert.sWaveSeconds, shownAt, zero) }
    }
}

/** Integer CSIS level of an estimate (prototype ci()). */
fun csisLevel(localCsis: Double): Int = localCsis.roundToInt().coerceIn(0, 12)

/** Radius of the area expected to reach [threshold] (预计 N 度以上), or null when negligible. */
fun warnZoneRadiusKm(magnitude: Double, depthKm: Double, threshold: Int): Double? {
    if (threshold <= 0) return null
    var lo = 0.0
    var hi = 2000.0
    repeat(40) {
        val mid = (lo + hi) / 2.0
        if (SeismicCalculator.calcLocalIntensity(magnitude, depthKm, mid) >= threshold) lo = mid else hi = mid
    }
    val r = (lo + hi) / 2.0
    return if (r in 3.0..1999.0) r else null
}
