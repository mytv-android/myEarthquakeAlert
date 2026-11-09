package com.github.mytv.myearthquakealert.map.offline

/** The area the last precache run covered (persisted by [OfflineMapStore]). */
data class CachedArea(
    /** [com.github.mytv.myearthquakealert.ui.map.EewMapStyle] name the tiles belong to. */
    val style: String,
    val latitude: Double,
    val longitude: Double,
    /** Tiles of the area present in the cache after the run (all layers). */
    val tiles: Int,
    /** Tiles the area needs (all layers). */
    val total: Int,
    /** Stored size of [tiles]. */
    val bytes: Long,
    /** End of the run, epoch millis. */
    val updatedAt: Long,
) {
    val complete: Boolean get() = total > 0 && tiles >= total
}

/** Why a precache run is due. */
enum class PrecacheReason { FIRST, STYLE, MOVED, INCOMPLETE, STALE }

/**
 * When to prefetch (design §7 离线预缓存): on first launch, after the user moved more than
 * [MOVE_KM] from the cached centre, when the map style changed, when the last run left tiles
 * missing ([RETRY_INCOMPLETE_MS] later) and when the tiles have expired ([MAX_AGE_MS]).
 */
object PrecachePolicy {
    const val RADIUS_KM = 300.0
    val ZOOMS = 5..9
    const val MOVE_KM = 50.0
    const val MAX_AGE_MS = 30L * 24 * 3_600_000
    const val RETRY_INCOMPLETE_MS = 6L * 3_600_000

    /** A cached tile younger than this is kept; older ones are fetched again (and live another [MAX_AGE_MS]). */
    const val KEEP_IF_EXPIRES_AFTER_MS = MAX_AGE_MS / 2

    fun reason(last: CachedArea?, style: String, latitude: Double, longitude: Double, now: Long): PrecacheReason? = when {
        last == null -> PrecacheReason.FIRST
        last.style != style -> PrecacheReason.STYLE
        movedKm(last, latitude, longitude) > MOVE_KM -> PrecacheReason.MOVED
        !last.complete && now - last.updatedAt >= RETRY_INCOMPLETE_MS -> PrecacheReason.INCOMPLETE
        now - last.updatedAt >= MAX_AGE_MS -> PrecacheReason.STALE
        else -> null
    }

    fun movedKm(last: CachedArea, latitude: Double, longitude: Double): Double =
        TileMath.distanceKm(last.latitude, last.longitude, latitude, longitude)

    /** Expiry written with a prefetched tile. */
    fun tileExpiry(now: Long): Long = now + MAX_AGE_MS

    /** True when a cached tile expiring at [expiresAt] is still good enough to skip. */
    fun isFresh(expiresAt: Long, now: Long): Boolean = expiresAt - now > KEEP_IF_EXPIRES_AFTER_MS
}
