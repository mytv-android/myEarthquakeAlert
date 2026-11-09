package com.github.mytv.myearthquakealert.wear.source

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Wolfx early-warning models, copied from `:app` `data.model.EewEvent` and the four EEW
// responses of `data.api.WolfxApiModels` (keep them in sync).

/**
 * One early-warning report, normalised across the four feeds.
 *
 * Copied from `:app` `data.model.EewEvent`.
 */
@Serializable
data class EewEvent(
    val id: String,
    val eventId: String,
    val source: String,
    val reportTime: String,
    val reportNum: Int,
    val originTime: String,
    val hypocenter: String,
    val latitude: Double,
    val longitude: Double,
    val magnitude: Double,
    val depth: Double? = null,
    val maxIntensity: Double = 0.0,
    val isFinal: Boolean = false,
)

@Serializable
data class ScEewResponse(
    val type: String? = null,
    val ID: Int = 0,
    val EventID: String = "",
    val ReportTime: String = "",
    val ReportNum: Int = 0,
    val OriginTime: String = "",
    val HypoCenter: String = "",
    val Latitude: Double = 0.0,
    val Longitude: Double = 0.0,
    @SerialName("Magunitude") val Magunitude: Double = 0.0,
    val Depth: Double? = null,
    val MaxIntensity: Double = 0.0,
)

@Serializable
data class CencEewResponse(
    val type: String? = null,
    val ID: String = "",
    val EventID: String = "",
    val ReportTime: String = "",
    val ReportNum: Int = 0,
    val OriginTime: String = "",
    val HypoCenter: String = "",
    val Latitude: Double = 0.0,
    val Longitude: Double = 0.0,
    val Magnitude: Double = 0.0,
    val Depth: Double? = null,
    val MaxIntensity: Double = 0.0,
)

@Serializable
data class FjEewResponse(
    val type: String? = null,
    val ID: Int = 0,
    val EventID: String = "",
    val ReportTime: String = "",
    val ReportNum: Int = 0,
    val OriginTime: String = "",
    val HypoCenter: String = "",
    val Latitude: Double = 0.0,
    val Longitude: Double = 0.0,
    @SerialName("Magunitude") val Magunitude: Double = 0.0,
    val isFinal: Boolean = false,
)

@Serializable
data class CqEewResponse(
    val type: String? = null,
    val ID: String = "",
    val EventID: String = "",
    val ReportTime: String = "",
    val ReportNum: Int = 0,
    val OriginTime: String = "",
    val HypoCenter: String = "",
    val Latitude: Double = 0.0,
    val Longitude: Double = 0.0,
    val Magnitude: Double = 0.0,
    val Depth: Double? = null,
    val MaxIntensity: Double = 0.0,
)
