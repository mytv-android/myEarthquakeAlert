package com.github.mytv.myearthquakealert.ui.diagnostics

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.github.mytv.myearthquakealert.ui.theme.status
import java.util.Locale
import kotlin.math.abs

/** Classification of one metric against the 指标定义与阈值 table. */
enum class MetricLevel { GOOD, WARN, BAD, UNKNOWN }

/**
 * Thresholds of design §5 指标定义与阈值. Every function returns [MetricLevel.UNKNOWN] when
 * the service has not published the value yet, so an empty page reads as "no data", not "bad".
 */
object MetricThresholds {
    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE

    /** 心跳间隔: ≤ 60 s · 60–90 s · > 90 s (reconnect is triggered). */
    fun heartbeat(ageMs: Long?): MetricLevel = when {
        ageMs == null -> MetricLevel.UNKNOWN
        ageMs <= 60_000L -> MetricLevel.GOOD
        ageMs <= 90_000L -> MetricLevel.WARN
        else -> MetricLevel.BAD
    }

    /** 报文延迟 p95: ≤ 2 s · 2–5 s · > 5 s. */
    fun latencyP95(ms: Long?): MetricLevel = when {
        ms == null -> MetricLevel.UNKNOWN
        ms <= 2_000L -> MetricLevel.GOOD
        ms <= 5_000L -> MetricLevel.WARN
        else -> MetricLevel.BAD
    }

    /** 时钟偏差: ≤ 0.5 s · 0.5–2 s · > 2 s (the countdown corrects for it). */
    fun clockOffset(ms: Long?): MetricLevel = when {
        ms == null -> MetricLevel.UNKNOWN
        abs(ms) <= 500L -> MetricLevel.GOOD
        abs(ms) <= 2_000L -> MetricLevel.WARN
        else -> MetricLevel.BAD
    }

    /** 24 h 可用率: ≥ 99 % · 95–99 % · < 95 %. */
    fun availability(ratio: Double?): MetricLevel = when {
        ratio == null -> MetricLevel.UNKNOWN
        ratio >= 0.99 -> MetricLevel.GOOD
        ratio >= 0.95 -> MetricLevel.WARN
        else -> MetricLevel.BAD
    }

    /** 重连次数 (24 h): ≤ 5 · 6–20 · > 20. */
    fun reconnects(count: Int): MetricLevel = when {
        count <= 5 -> MetricLevel.GOOD
        count <= 20 -> MetricLevel.WARN
        else -> MetricLevel.BAD
    }

    /** 定位: GPS or manual within 30 min · within 24 h · IP only or none. */
    fun location(source: String?, ageMs: Long?): MetricLevel = when (source?.lowercase(Locale.ROOT)) {
        null, "" -> MetricLevel.UNKNOWN
        "manual" -> MetricLevel.GOOD
        "gps", "network" -> when {
            ageMs == null || ageMs <= 30 * MINUTE -> MetricLevel.GOOD
            ageMs <= 24 * HOUR -> MetricLevel.WARN
            else -> MetricLevel.BAD
        }
        else -> MetricLevel.BAD
    }

    /** 系统限制: exempt + ACTIVE · WORKING_SET (or not exempt) · RARE / RESTRICTED. */
    fun systemLimits(exempt: Boolean?, bucket: String?): MetricLevel {
        val b = bucket?.uppercase(Locale.ROOT)
        return when {
            exempt == null && b == null -> MetricLevel.UNKNOWN
            b == "RARE" || b == "RESTRICTED" -> MetricLevel.BAD
            exempt == false || b == "WORKING_SET" || b == "FREQUENT" -> MetricLevel.WARN
            else -> MetricLevel.GOOD
        }
    }

    /** 看门狗: on time · more than 30 min late · found the service killed and restarted it. */
    fun watchdog(lastRunAt: Long?, nextRunAt: Long?, now: Long, restartedRecently: Boolean): MetricLevel = when {
        lastRunAt == null && nextRunAt == null -> MetricLevel.UNKNOWN
        restartedRecently -> MetricLevel.BAD
        nextRunAt != null && now - nextRunAt > 30 * MINUTE -> MetricLevel.WARN
        else -> MetricLevel.GOOD
    }

    /** 开机自启: ≤ 60 s · 60 s – 5 min · not started in this boot (probably blocked). */
    fun bootStart(delaySec: Long?, enabled: Boolean, runningNow: Boolean): MetricLevel = when {
        !enabled -> MetricLevel.UNKNOWN
        delaySec == null -> if (runningNow) MetricLevel.WARN else MetricLevel.BAD
        delaySec <= 60L -> MetricLevel.GOOD
        delaySec <= 300L -> MetricLevel.WARN
        else -> MetricLevel.BAD
    }

    fun worst(levels: Iterable<MetricLevel>): MetricLevel = when {
        levels.any { it == MetricLevel.BAD } -> MetricLevel.BAD
        levels.any { it == MetricLevel.WARN } -> MetricLevel.WARN
        levels.any { it == MetricLevel.GOOD } -> MetricLevel.GOOD
        else -> MetricLevel.UNKNOWN
    }
}

@Immutable
data class LevelColors(val accent: Color, val container: Color, val content: Color)

/** Accent (dot, line) and tile container colours of a [MetricLevel]. */
@Composable
fun levelColors(level: MetricLevel): LevelColors {
    val s = MaterialTheme.status
    val c = MaterialTheme.colorScheme
    return when (level) {
        MetricLevel.GOOD -> LevelColors(s.ok, c.surfaceContainerLow, c.onSurface)
        MetricLevel.WARN -> LevelColors(s.warn, s.warnContainer, s.onWarnContainer)
        MetricLevel.BAD -> LevelColors(c.error, c.errorContainer, c.onErrorContainer)
        MetricLevel.UNKNOWN -> LevelColors(c.outline, c.surfaceContainerLow, c.onSurface)
    }
}
