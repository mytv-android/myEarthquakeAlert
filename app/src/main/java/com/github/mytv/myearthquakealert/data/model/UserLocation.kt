package com.github.mytv.myearthquakealert.data.model

/**
 * Where the user is, for local intensity and arrival times.
 *
 * [source] is one of [SOURCE_GPS], [SOURCE_NETWORK], [SOURCE_MANUAL], [SOURCE_IP] or
 * [SOURCE_NONE] (then latitude and longitude are 0).
 */
data class UserLocation(
    val latitude: Double,
    val longitude: Double,
    val source: String,
    /** Horizontal accuracy in metres, when the provider reports one. */
    val accuracyM: Float? = null,
    /** Epoch millis of the fix; null for manual locations (always current). */
    val fixTime: Long? = null,
    /** Place name of a manual location. */
    val name: String = "",
) {
    val isKnown: Boolean get() = source != SOURCE_NONE && !(latitude == 0.0 && longitude == 0.0)

    /** Age of the fix at [now]; 0 for manual locations, null when unknown. */
    fun ageMs(now: Long = System.currentTimeMillis()): Long? = when {
        source == SOURCE_MANUAL -> 0L
        fixTime == null -> null
        else -> (now - fixTime).coerceAtLeast(0L)
    }

    companion object {
        const val SOURCE_GPS = "gps"
        const val SOURCE_NETWORK = "network"
        const val SOURCE_MANUAL = "manual"
        const val SOURCE_IP = "ip"
        const val SOURCE_NONE = "none"
    }
}
