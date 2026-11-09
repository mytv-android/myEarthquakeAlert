package com.github.mytv.myearthquakealert.wear.domain

/**
 * Wolfx timestamps (`ReportTime`, `OriginTime`) are `yyyy-MM-dd HH:mm:ss` strings in
 * China Standard Time (UTC+8, no daylight saving). Parsed by hand: it is on the hot path
 * of every report, needs no locale or time-zone database, and stays thread-safe.
 *
 * Copied from `:app` `domain.ReportTime` (keep the two in sync). Wolfx CST timestamps.
 */
object ReportTime {

    private const val CST_OFFSET_MS = 8 * 3_600_000L
    private const val DAY_MS = 86_400_000L

    /** Epoch millis of a CST timestamp, or null when [text] is not `yyyy-MM-dd HH:mm:ss`. */
    fun parseCst(text: String?): Long? {
        val s = text?.trim() ?: return null
        if (s.length < 19) return null
        if (s[4] != '-' || s[7] != '-' || (s[10] != ' ' && s[10] != 'T') || s[13] != ':' || s[16] != ':') return null
        val year = digits(s, 0, 4) ?: return null
        val month = digits(s, 5, 2) ?: return null
        val day = digits(s, 8, 2) ?: return null
        val hour = digits(s, 11, 2) ?: return null
        val minute = digits(s, 14, 2) ?: return null
        val second = digits(s, 17, 2) ?: return null
        if (month !in 1..12 || day !in 1..31 || hour > 23 || minute > 59 || second > 60) return null
        val days = daysFromCivil(year, month, day)
        return days * DAY_MS + hour * 3_600_000L + minute * 60_000L + second * 1000L - CST_OFFSET_MS
    }

    /** Formats epoch millis as a CST `yyyy-MM-dd HH:mm:ss` string (drill reports). */
    fun formatCst(epochMillis: Long): String {
        val local = epochMillis + CST_OFFSET_MS
        val days = local.floorDiv(DAY_MS)
        val msOfDay = local.mod(DAY_MS)
        val (y, m, d) = civilFromDays(days)
        val h = (msOfDay / 3_600_000L).toInt()
        val min = (msOfDay / 60_000L % 60).toInt()
        val sec = (msOfDay / 1000L % 60).toInt()
        return buildString(19) {
            append(y.toString().padStart(4, '0')).append('-')
            append(two(m)).append('-').append(two(d)).append(' ')
            append(two(h)).append(':').append(two(min)).append(':').append(two(sec))
        }
    }

    private fun two(v: Int) = if (v < 10) "0$v" else v.toString()

    private fun digits(s: String, start: Int, count: Int): Int? {
        var v = 0
        for (i in start until start + count) {
            val c = s[i]
            if (c !in '0'..'9') return null
            v = v * 10 + (c - '0')
        }
        return v
    }

    // Howard Hinnant's days_from_civil / civil_from_days (proleptic Gregorian, epoch 1970-01-01).
    private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
        val y = (if (month <= 2) year - 1 else year).toLong()
        val era = y.floorDiv(400L)
        val yoe = y - era * 400
        val mp = (month + 9) % 12
        val doy = (153 * mp + 2) / 5 + day - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146_097 + doe - 719_468
    }

    private fun civilFromDays(days: Long): Triple<Int, Int, Int> {
        val z = days + 719_468
        val era = z.floorDiv(146_097L)
        val doe = z - era * 146_097
        val yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = (doy - (153 * mp + 2) / 5 + 1).toInt()
        val m = (if (mp < 10) mp + 3 else mp - 9).toInt()
        val y = (yoe + era * 400 + if (m <= 2) 1 else 0).toInt()
        return Triple(y, m, d)
    }
}
