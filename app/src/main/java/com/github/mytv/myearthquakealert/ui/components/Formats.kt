package com.github.mytv.myearthquakealert.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.res.stringResource
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.ui.quakes.QuakeCatalog
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** "86" / "1,284" — kilometres without the unit (design fmtKm). */
fun formatKm(km: Double): String =
    if (km < 1000) km.roundToInt().toString() else String.format(Locale.US, "%,d", km.roundToLong())

/** "10" for whole depths, "8.5" otherwise. */
fun formatDepth(km: Double): String =
    if (km == floor(km)) km.toInt().toString() else String.format(Locale.US, "%.1f", km)

fun formatMagnitude(m: Double): String = String.format(Locale.US, "%.1f", m)

/** "+0.21 s" / "−1.4 s". */
fun formatSignedSeconds(ms: Long): String {
    val s = ms / 1000.0
    val sign = if (ms < 0) "−" else "+"
    return sign + String.format(Locale.US, if (abs(s) < 10) "%.2f s" else "%.1f s", abs(s))
}

/** "0.6 s" from milliseconds. */
fun formatSeconds(ms: Long): String = String.format(Locale.US, "%.1f s", ms / 1000.0)

/** Local "HH:mm". */
fun formatClock(epochMillis: Long): String = SimpleDateFormat("HH:mm", Locale.US).format(Date(epochMillis))

/** The current time, refreshed every [periodMs] (aligned to the period boundary). */
@Composable
fun rememberNow(periodMs: Long = 30_000L): Long {
    val now by produceState(System.currentTimeMillis(), periodMs) {
        while (true) {
            val t = System.currentTimeMillis()
            value = t
            delay(periodMs - t % periodMs)
        }
    }
    return now
}

/** "3 分钟前" / "2 小时前" / "11月6日 21:40" (design ago()). */
@Composable
fun agoText(epochMillis: Long?, catalogueTime: String, now: Long): String {
    if (epochMillis == null) return catalogueTime
    val minutes = (now - epochMillis) / 60_000.0
    return when {
        minutes < 60 -> stringResource(R.string.ui_ago_minutes, maxOf(1, minutes.roundToInt()))
        minutes < 1440 -> stringResource(R.string.ui_ago_hours, floor(minutes / 60).toInt())
        else -> {
            val (month, day) = QuakeCatalog.monthDay(epochMillis)
            stringResource(R.string.ui_date_time, month, day, QuakeCatalog.hhmm(catalogueTime))
        }
    }
}

/** "18 秒前" / "4 分钟前" for heartbeat ages. */
@Composable
fun ageText(ageMs: Long): String {
    val s = (ageMs / 1000).coerceAtLeast(0)
    return when {
        s < 60 -> stringResource(R.string.ui_ago_seconds, s.toInt())
        s < 3600 -> stringResource(R.string.ui_ago_minutes, (s / 60).toInt())
        else -> stringResource(R.string.ui_ago_hours, (s / 3600).toInt())
    }
}

/** "3 小时 12 分" / "59 分钟" / "2 天 4 小时". */
@Composable
fun durationText(ms: Long): String {
    val totalMinutes = ((ms + 59_999) / 60_000).coerceAtLeast(1)
    val days = totalMinutes / 1440
    val hours = (totalMinutes % 1440) / 60
    val minutes = totalMinutes % 60
    return when {
        days > 0 -> stringResource(R.string.ui_duration_dh, days.toInt(), hours.toInt())
        hours > 0 -> stringResource(R.string.ui_duration_hm, hours.toInt(), minutes.toInt())
        else -> stringResource(R.string.ui_duration_m, minutes.toInt())
    }
}

/** "今天 · 11月8日" / "昨天 · 11月7日" / "11月6日" (design dayLabel()). */
@Composable
fun dayLabel(daysAgo: Int, epochMillis: Long): String {
    val (month, day) = QuakeCatalog.monthDay(epochMillis)
    return when (daysAgo) {
        0 -> stringResource(R.string.ui_day_today, month, day)
        1 -> stringResource(R.string.ui_day_yesterday, month, day)
        else -> stringResource(R.string.ui_day_other, month, day)
    }
}

/** "11:12" today, "明天 08:00" tomorrow, "11月10日 08:00" later (device time zone). */
@Composable
fun untilText(epochMillis: Long, now: Long): String {
    val clock = formatClock(epochMillis)
    val target = Calendar.getInstance().apply { timeInMillis = epochMillis }
    val today = Calendar.getInstance().apply { timeInMillis = now }
    val sameYear = target.get(Calendar.YEAR) == today.get(Calendar.YEAR)
    val dayDiff = if (sameYear) target.get(Calendar.DAY_OF_YEAR) - today.get(Calendar.DAY_OF_YEAR) else Int.MAX_VALUE
    return when (dayDiff) {
        0 -> clock
        1 -> stringResource(R.string.ui_tomorrow_time, clock)
        else -> stringResource(R.string.ui_date_time, target.get(Calendar.MONTH) + 1, target.get(Calendar.DAY_OF_MONTH), clock)
    }
}
