package com.github.mytv.myearthquakealert.data.model

import kotlinx.serialization.Serializable

@Serializable
data class EarthquakeInfo(
    val no: Int,
    val type: String,
    val time: String,
    val location: String,
    val magnitude: String,
    val depth: String,
    val latitude: String,
    val longitude: String,
    val intensity: String,
)
