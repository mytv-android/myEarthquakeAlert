package com.github.mytv.myearthquakealert.ui.quakes

import androidx.compose.runtime.Immutable
import com.github.mytv.myearthquakealert.data.model.EarthquakeInfo
import com.github.mytv.myearthquakealert.domain.SeismicCalculator
import com.github.mytv.myearthquakealert.ui.map.MapPoint
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Where the user is, for distances and estimated local intensity. */
@Immutable
data class UserPoint(
    val latitude: Double,
    val longitude: Double,
    val source: Source,
    /** Place name of a manual location ("成都市"), empty otherwise. */
    val name: String = "",
) {
    enum class Source { GPS, NETWORK, IP, MANUAL }

    val point: MapPoint get() = MapPoint(latitude, longitude)
}

/** One entry of the CENC catalogue (中国地震台网目录), parsed for display. */
@Immutable
data class QuakeUi(
    /** Stable across refreshes (the catalogue renumbers "No1.." every time). */
    val key: String,
    /** "2026-11-08 09:58:11", China Standard Time as published. */
    val time: String,
    val epochMillis: Long?,
    val location: String,
    val magnitude: Double,
    val depthKm: Double,
    val latitude: Double,
    val longitude: Double,
    /** Epicentral intensity (震中烈度) as published. */
    val intensity: Int,
    /** 正式测定 (reviewed) vs 自动测定 (automatic). */
    val reviewed: Boolean,
    val distanceKm: Double?,
    /** Estimated intensity at the user (CSIS formula); null without a location. */
    val localIntensity: Double?,
    val domestic: Boolean,
) {
    val point: MapPoint get() = MapPoint(latitude, longitude)
}

enum class QuakeFilter { ALL, M4, DOMESTIC, NEARBY }

enum class QuakeSort { TIME, MAGNITUDE, DISTANCE }

/** Radius of the 「500 km 内」 filter. */
const val NEARBY_RADIUS_KM = 500.0

object QuakeCatalog {
    /** CENC publishes Beijing time. */
    val CHINA_TIME: TimeZone = TimeZone.getTimeZone("Asia/Shanghai")

    private val provinces = listOf(
        "北京", "天津", "上海", "重庆", "河北", "山西", "内蒙古", "辽宁", "吉林", "黑龙江", "江苏", "浙江",
        "安徽", "福建", "江西", "山东", "河南", "湖北", "湖南", "广东", "广西", "海南", "四川", "贵州",
        "云南", "西藏", "陕西", "甘肃", "青海", "宁夏", "新疆", "台湾", "香港", "澳门",
        "渤海", "黄海", "东海", "南海", "台湾海峡",
    )

    /** 国内: the catalogue names Chinese places by province, foreign ones by country or sea area. */
    fun isDomestic(location: String): Boolean = provinces.any { location.startsWith(it) }

    fun parseTime(time: String): Long? = try {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = CHINA_TIME }.parse(time)?.time
    } catch (e: ParseException) {
        null
    }

    fun fromInfo(info: EarthquakeInfo, user: UserPoint?): QuakeUi? {
        val lat = info.latitude.toDoubleOrNull() ?: return null
        val lon = info.longitude.toDoubleOrNull() ?: return null
        if (lat == 0.0 && lon == 0.0) return null
        val magnitude = info.magnitude.toDoubleOrNull() ?: return null
        val depth = info.depth.toDoubleOrNull() ?: 10.0
        val distance = user?.let { SeismicCalculator.haversineDistance(it.latitude, it.longitude, lat, lon) }
        return QuakeUi(
            key = "${info.time}|$lat|$lon",
            time = info.time,
            epochMillis = parseTime(info.time),
            location = info.location,
            magnitude = magnitude,
            depthKm = depth,
            latitude = lat,
            longitude = lon,
            intensity = info.intensity.toDoubleOrNull()?.roundToInt()?.coerceIn(0, 12) ?: 0,
            reviewed = info.type.equals("reviewed", ignoreCase = true),
            distanceKm = distance,
            localIntensity = distance?.let { SeismicCalculator.calcLocalIntensity(magnitude, depth, it) },
            domestic = isDomestic(info.location),
        )
    }

    fun apply(list: List<QuakeUi>, filter: QuakeFilter, sort: QuakeSort, query: String): List<QuakeUi> {
        val q = query.trim()
        val filtered = list.filter { quake ->
            val passes = when (filter) {
                QuakeFilter.ALL -> true
                QuakeFilter.M4 -> quake.magnitude >= 4.0
                QuakeFilter.DOMESTIC -> quake.domestic
                QuakeFilter.NEARBY -> quake.distanceKm != null && quake.distanceKm <= NEARBY_RADIUS_KM
            }
            passes && (q.isEmpty() || quake.location.contains(q, ignoreCase = true))
        }
        return when (sort) {
            QuakeSort.TIME -> filtered.sortedByDescending { it.epochMillis ?: Long.MIN_VALUE }
            QuakeSort.MAGNITUDE -> filtered.sortedByDescending { it.magnitude }
            QuakeSort.DISTANCE -> filtered.sortedBy { it.distanceKm ?: Double.MAX_VALUE }
        }
    }

    /** Whole days covered by the catalogue ("近 10 天"), at least 1. */
    fun spanDays(list: List<QuakeUi>, now: Long): Int {
        val oldest = list.mapNotNull { it.epochMillis }.minOrNull() ?: return 1
        return ceil((now - oldest).coerceAtLeast(0L) / DAY_MS.toDouble()).toInt().coerceAtLeast(1)
    }

    /** Calendar-day difference in China time: 0 today, 1 yesterday … */
    fun daysAgo(epochMillis: Long, now: Long): Int {
        fun dayIndex(t: Long): Long {
            val c = Calendar.getInstance(CHINA_TIME).apply { timeInMillis = t }
            c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
            return (c.timeInMillis + CHINA_TIME.getOffset(c.timeInMillis)) / DAY_MS
        }
        return (dayIndex(now) - dayIndex(epochMillis)).toInt()
    }

    /** Month (1–12) and day of month of [epochMillis] in China time. */
    fun monthDay(epochMillis: Long): Pair<Int, Int> {
        val c = Calendar.getInstance(CHINA_TIME).apply { timeInMillis = epochMillis }
        return (c.get(Calendar.MONTH) + 1) to c.get(Calendar.DAY_OF_MONTH)
    }

    /** "09:58" from the catalogue time. */
    fun hhmm(time: String): String = if (time.length >= 16) time.substring(11, 16) else time

    /** Groups an already time-sorted list by calendar day (China time), keeping order. */
    fun groupByDay(list: List<QuakeUi>, now: Long): List<Pair<Int, List<QuakeUi>>> {
        val groups = LinkedHashMap<Int, MutableList<QuakeUi>>()
        for (q in list) {
            val t = q.epochMillis ?: continue
            groups.getOrPut(daysAgo(t, now)) { mutableListOf() }.add(q)
        }
        return groups.map { (daysAgo, quakes) -> daysAgo to quakes }
    }

    const val DAY_MS = 86_400_000L
}
