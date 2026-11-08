package com.github.mytv.myearthquakealert.ui.settings.components

import android.content.res.Resources
import android.text.format.DateFormat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.ViewQuilt
import androidx.compose.material.icons.filled.Watch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.os.ConfigurationCompat
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.data.repository.UserSettings
import com.github.mytv.myearthquakealert.data.source.EewSource
import com.github.mytv.myearthquakealert.diagnostics.LogEntry
import com.github.mytv.myearthquakealert.diagnostics.LogTags
import com.github.mytv.myearthquakealert.service.DeliveryRequirement
import com.github.mytv.myearthquakealert.service.MonitorPhase
import com.github.mytv.myearthquakealert.service.MonitorState
import com.github.mytv.myearthquakealert.service.StartOrigin
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/**
 * The phase settings and diagnostics show. It is the service's own phase, reconciled with
 * the stored switches for the moments before the service has reported (it starts in OFF):
 * a switched-on service that has not published yet reads as connecting, a stored pause as paused.
 */
fun displayPhase(state: MonitorState, settings: UserSettings, now: Long): MonitorPhase = when {
    !settings.serviceEnabled -> MonitorPhase.OFF
    state.phase == MonitorPhase.PAUSED || settings.isPaused(now) -> MonitorPhase.PAUSED
    state.phase == MonitorPhase.OFF -> MonitorPhase.CONNECTING
    else -> state.phase
}

fun pauseEnd(state: MonitorState, settings: UserSettings): Long = max(state.pauseUntil, settings.pauseUntil)

/** Wall-clock ticker for countdowns and "x minutes ago" texts. */
@Composable
fun rememberNow(periodMs: Long = 1_000L): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(periodMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(periodMs - now % periodMs)
        }
    }
    return now
}

@Composable
fun rememberFormats(): Formats {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(context, configuration) { Formats(context.resources) }
}

/** Locale-aware text for times, durations, phases and start origins. */
@Stable
class Formats(private val res: Resources) {
    private val locale: Locale = ConfigurationCompat.getLocales(res.configuration)[0] ?: Locale.getDefault()
    private val hm = SimpleDateFormat("HH:mm", locale)
    private val hms = SimpleDateFormat("HH:mm:ss", locale)
    private val hmsMillis = SimpleDateFormat("HH:mm:ss.SSS", locale)
    private val monthDay = SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "MMMd"), locale)

    fun clock(time: Long): String = hm.format(Date(time))
    fun clockSeconds(time: Long): String = hms.format(Date(time))
    fun clockMillis(time: Long): String = hmsMillis.format(Date(time))

    /** "今天 07:00:09" / "昨天 08:12:02" / "11月3日 22:41:30". */
    fun dayTime(time: Long, now: Long, seconds: Boolean = true): String {
        val clock = if (seconds) clockSeconds(time) else clock(time)
        return when (daysBetween(time, now)) {
            0 -> res.getString(R.string.pg_today_at, clock)
            1 -> res.getString(R.string.pg_yesterday_at, clock)
            else -> "${monthDay.format(Date(time))} $clock"
        }
    }

    /** Clock time, prefixed with 明天 when it falls on the next day. */
    fun clockWithDay(time: Long, now: Long): String =
        if (daysBetween(now, time) >= 1) res.getString(R.string.pg_tomorrow_at, clock(time)) else clock(time)

    fun duration(ms: Long): String {
        val totalSec = max(0L, ms / 1000)
        val days = totalSec / 86_400
        val hours = (totalSec % 86_400) / 3600
        val minutes = (totalSec % 3600) / 60
        return when {
            days > 0 -> res.getString(R.string.pg_dur_dh, days, hours)
            hours > 0 -> res.getString(R.string.pg_dur_hm, hours, minutes)
            minutes > 0 -> res.getString(R.string.pg_dur_m, minutes)
            else -> res.getString(R.string.pg_dur_s, totalSec)
        }
    }

    fun ago(time: Long, now: Long): String {
        val sec = (now - time) / 1000
        return when {
            sec < 10 -> res.getString(R.string.pg_just_now)
            sec < 60 -> res.getString(R.string.pg_seconds_ago, sec)
            sec < 3600 -> res.getString(R.string.pg_minutes_ago, sec / 60)
            sec < 86_400 -> res.getString(R.string.pg_hours_ago, sec / 3600)
            else -> dayTime(time, now, seconds = false)
        }
    }

    fun seconds(ms: Long, decimals: Int = 1): String =
        String.format(locale, "%.${decimals}f", ms / 1000.0)

    fun signedSeconds(ms: Long): String =
        (if (ms >= 0) "+" else "−") + String.format(locale, "%.2f", abs(ms) / 1000.0)

    fun phaseTitle(phase: MonitorPhase, pauseUntil: Long, now: Long): String = when (phase) {
        MonitorPhase.MONITORING -> res.getString(R.string.pg_phase_monitoring)
        MonitorPhase.CONNECTING -> res.getString(R.string.pg_phase_connecting)
        MonitorPhase.RECONNECTING -> res.getString(R.string.pg_phase_reconnecting)
        MonitorPhase.DEGRADED -> res.getString(R.string.pg_phase_degraded)
        MonitorPhase.LIMITED -> res.getString(R.string.pg_phase_limited)
        MonitorPhase.PAUSED -> if (pauseUntil > now) {
            res.getString(R.string.pg_phase_paused_until, clockWithDay(pauseUntil, now))
        } else {
            res.getString(R.string.pg_phase_paused)
        }
        MonitorPhase.OFF -> res.getString(R.string.pg_phase_off)
    }

    fun phaseShort(phase: MonitorPhase, pauseUntil: Long, now: Long): String = when (phase) {
        MonitorPhase.MONITORING -> res.getString(R.string.pg_short_monitoring)
        MonitorPhase.CONNECTING -> res.getString(R.string.pg_short_connecting)
        MonitorPhase.RECONNECTING -> res.getString(R.string.pg_phase_reconnecting)
        MonitorPhase.DEGRADED -> res.getString(R.string.pg_phase_degraded)
        MonitorPhase.LIMITED -> res.getString(R.string.pg_phase_limited)
        MonitorPhase.PAUSED -> if (pauseUntil > now) {
            res.getString(R.string.pg_short_paused_until, clockWithDay(pauseUntil, now))
        } else {
            res.getString(R.string.pg_phase_paused)
        }
        MonitorPhase.OFF -> res.getString(R.string.pg_short_off)
    }

    fun phaseSubtitle(phase: MonitorPhase, state: MonitorState, settings: UserSettings, now: Long): String = when (phase) {
        MonitorPhase.MONITORING -> {
            val count = state.connectedCount.takeIf { it > 0 } ?: settings.enabledSources.size
            val since = state.runningSince
            if (since != null && since <= now) {
                res.getString(R.string.pg_sub_monitoring_uptime, count, duration(now - since))
            } else {
                res.getString(R.string.pg_sub_monitoring, count)
            }
        }
        MonitorPhase.CONNECTING -> res.getString(R.string.pg_sub_connecting, settings.enabledSources.size)
        MonitorPhase.RECONNECTING -> {
            val source = state.sources.filter { it.reconnectAttempt > 0 }.maxByOrNull { it.reconnectAttempt }
            val retryAt = source?.nextRetryAt
            if (source != null && retryAt != null) {
                res.getString(R.string.pg_sub_reconnecting, source.reconnectAttempt, max(0L, (retryAt - now + 999) / 1000))
            } else {
                res.getString(R.string.pg_sub_reconnecting_generic)
            }
        }
        MonitorPhase.DEGRADED -> res.getString(R.string.pg_sub_degraded)
        MonitorPhase.LIMITED -> {
            val required = state.missing.filter { it.required }
            if (required.isNotEmpty()) {
                res.getString(R.string.pg_sub_limited, required.joinToString(res.getString(R.string.pg_list_separator)) { requirementLabel(it) })
            } else {
                res.getString(R.string.pg_sub_limited_generic)
            }
        }
        MonitorPhase.PAUSED -> res.getString(R.string.pg_sub_paused)
        MonitorPhase.OFF -> res.getString(R.string.pg_sub_off)
    }

    fun requirementLabel(requirement: DeliveryRequirement): String = when (requirement) {
        DeliveryRequirement.NOTIFICATIONS -> res.getString(R.string.pg_req_notifications)
        DeliveryRequirement.OVERLAY -> res.getString(R.string.pg_req_overlay)
        DeliveryRequirement.FULL_SCREEN_INTENT -> res.getString(R.string.pg_req_full_screen)
        DeliveryRequirement.LOCATION -> res.getString(R.string.pg_req_location)
        DeliveryRequirement.BATTERY_OPTIMIZATION -> res.getString(R.string.pg_req_battery)
        DeliveryRequirement.DND_ACCESS -> res.getString(R.string.pg_req_dnd)
        DeliveryRequirement.AUTO_START -> res.getString(R.string.pg_req_auto_start)
    }

    fun requirementDescription(requirement: DeliveryRequirement): String = when (requirement) {
        DeliveryRequirement.NOTIFICATIONS -> res.getString(R.string.pg_req_notifications_desc)
        DeliveryRequirement.OVERLAY -> res.getString(R.string.pg_req_overlay_desc)
        DeliveryRequirement.FULL_SCREEN_INTENT -> res.getString(R.string.pg_req_full_screen_desc)
        DeliveryRequirement.LOCATION -> res.getString(R.string.pg_req_location_desc)
        DeliveryRequirement.BATTERY_OPTIMIZATION -> res.getString(R.string.pg_req_battery_desc)
        DeliveryRequirement.DND_ACCESS -> res.getString(R.string.pg_req_dnd_desc)
        DeliveryRequirement.AUTO_START -> res.getString(R.string.pg_req_auto_start_desc)
    }

    fun originLabel(origin: StartOrigin?): String = when (origin) {
        StartOrigin.USER -> res.getString(R.string.pg_origin_user)
        StartOrigin.BOOT -> res.getString(R.string.pg_origin_boot)
        StartOrigin.PACKAGE_REPLACED -> res.getString(R.string.pg_origin_package_replaced)
        StartOrigin.WATCHDOG -> res.getString(R.string.pg_origin_watchdog)
        StartOrigin.STICKY_RESTART -> res.getString(R.string.pg_origin_sticky)
        StartOrigin.PAUSE_ENDED -> res.getString(R.string.pg_origin_pause_ended)
        StartOrigin.TILE -> res.getString(R.string.pg_origin_tile)
        StartOrigin.NOTIFICATION -> res.getString(R.string.pg_origin_notification)
        StartOrigin.WATCH -> res.getString(R.string.pg_origin_watch)
        null -> res.getString(R.string.pg_origin_unknown)
    }

    fun sourceName(source: EewSource): String = when (source.name) {
        "CENC" -> res.getString(R.string.pg_source_cenc)
        "SICHUAN" -> res.getString(R.string.pg_source_sichuan)
        "FUJIAN" -> res.getString(R.string.pg_source_fujian)
        "CHONGQING" -> res.getString(R.string.pg_source_chongqing)
        else -> source.label
    }

    fun sourceShortName(source: EewSource): String = when (source.name) {
        "CENC" -> res.getString(R.string.pg_source_cenc)
        "SICHUAN" -> res.getString(R.string.pg_source_sichuan_short)
        "FUJIAN" -> res.getString(R.string.pg_source_fujian_short)
        "CHONGQING" -> res.getString(R.string.pg_source_chongqing_short)
        else -> source.label
    }

    /** "中国地震台网、四川、福建、重庆 · 4 路" in [EewSource] order. */
    fun sourcesSummary(sources: Set<EewSource>): String {
        val ordered = EewSource.entries.filter { it in sources }
        return res.getString(
            R.string.pg_sources_summary,
            ordered.joinToString(res.getString(R.string.pg_list_separator)) { sourceShortName(it) },
            ordered.size,
        )
    }

    fun string(id: Int, vararg args: Any): String = res.getString(id, *args)

    private fun daysBetween(from: Long, to: Long): Int {
        val a = Calendar.getInstance().apply { timeInMillis = from; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        val b = Calendar.getInstance().apply { timeInMillis = to; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        return ((b.timeInMillis - a.timeInMillis + 43_200_000L) / 86_400_000L).toInt()
    }
}

fun originIcon(origin: StartOrigin?): ImageVector = when (origin) {
    StartOrigin.USER -> Icons.Filled.TouchApp
    StartOrigin.BOOT -> Icons.Filled.RestartAlt
    StartOrigin.PACKAGE_REPLACED -> Icons.Filled.SystemUpdate
    StartOrigin.WATCHDOG -> Icons.Filled.HealthAndSafety
    StartOrigin.STICKY_RESTART -> Icons.Filled.SettingsBackupRestore
    StartOrigin.PAUSE_ENDED -> Icons.Filled.Restore
    StartOrigin.TILE -> Icons.Filled.ViewQuilt
    StartOrigin.NOTIFICATION -> Icons.Filled.Notifications
    StartOrigin.WATCH -> Icons.Filled.Watch
    null -> Icons.Filled.PhoneAndroid
}

/** One service start parsed from a [LogTags.START] log entry. */
data class StartRecord(val time: Long, val origin: StartOrigin?, val detail: String)

/** Recent service starts, newest first. The message begins with the [StartOrigin] name. */
fun startHistory(log: List<LogEntry>, limit: Int = 6): List<StartRecord> =
    log.asReversed()
        .asSequence()
        .filter { it.tag == LogTags.START }
        .map { entry ->
            val origin = StartOrigin.entries
                .sortedByDescending { it.name.length }
                .firstOrNull { entry.message.startsWith(it.name) }
            val detail = if (origin != null) {
                entry.message.removePrefix(origin.name).trimStart(' ', ':', '·', '-', ',', '|').trim()
            } else {
                entry.message
            }
            StartRecord(entry.time, origin, detail)
        }
        .take(limit)
        .toList()
