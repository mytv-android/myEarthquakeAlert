package com.github.mytv.myearthquakealert.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ScEewResponse(
    val type: String? = null,
    val ID: Int,
    val EventID: String,
    val ReportTime: String,
    val ReportNum: Int,
    val OriginTime: String,
    val HypoCenter: String,
    val Latitude: Double,
    val Longitude: Double,
    @SerialName("Magunitude") val Magunitude: Double,
    val Depth: Double? = null,
    val MaxIntensity: Int = 0,
)

@Serializable
data class CencEewResponse(
    val type: String? = null,
    val ID: String,
    val EventID: String,
    val ReportTime: String,
    val ReportNum: Int,
    val OriginTime: String,
    val HypoCenter: String,
    val Latitude: Double,
    val Longitude: Double,
    val Magnitude: Double,
    val Depth: Double? = null,
    val MaxIntensity: Int = 0,
)

@Serializable
data class FjEewResponse(
    val type: String? = null,
    val ID: Int,
    val EventID: String,
    val ReportTime: String,
    val ReportNum: Int,
    val OriginTime: String,
    val HypoCenter: String,
    val Latitude: Double,
    val Longitude: Double,
    @SerialName("Magunitude") val Magunitude: Double,
    val isFinal: Boolean = false,
)

@Serializable
data class CqEewResponse(
    val type: String? = null,
    val ID: String,
    val EventID: String,
    val ReportTime: String,
    val ReportNum: Int,
    val OriginTime: String,
    val HypoCenter: String,
    val Latitude: Double,
    val Longitude: Double,
    val Magnitude: Double,
    val Depth: Double? = null,
    val MaxIntensity: Int = 0,
)

@Serializable
data class EarthquakeListEntry(
    val type: String,
    val time: String,
    val location: String,
    val magnitude: String,
    val depth: String,
    val latitude: String,
    val longitude: String,
    val intensity: String,
    val md5: String,
)

@Serializable
data class GeoIpResponse(
    val ip: String,
    val country_code: String = "",
    val country_name: String = "",
    val country_name_zh: String = "",
    val province_code: String = "",
    val province_name: String = "",
    val province_name_zh: String = "",
    val city: String = "",
    val city_zh: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
)

@Serializable
data class NtpResponse(
    val JST: String = "",
    val CST: String = "",
    val str: String = "",
    val int: Long = 0,
    val timestamp: Long = 0,
)
