package com.github.mytv.myearthquakealert.data.model

import kotlinx.serialization.Serializable

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
