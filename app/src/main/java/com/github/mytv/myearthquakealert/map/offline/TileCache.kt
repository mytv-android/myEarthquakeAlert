package com.github.mytv.myearthquakealert.map.offline

import android.util.Log
import org.osmdroid.tileprovider.modules.DatabaseFileArchive
import org.osmdroid.tileprovider.modules.SqlTileWriter
import org.osmdroid.tileprovider.tilesource.ITileSource

/**
 * osmdroid's SQLite tile cache (the one every [org.osmdroid.views.MapView] of the app reads),
 * with the few extra queries the precache and its settings rows need. osmdroid keeps a single
 * process-wide database behind all [SqlTileWriter] instances, so this is cheap to create.
 * Blocking: call off the main thread, after `MyEarthQuakeAlertApp.ensureMapConfigured()`.
 */
internal class TileCache : SqlTileWriter() {

    /** One cached tile: expiry (epoch millis) and stored size. */
    data class Entry(val expiresAt: Long, val bytes: Long)

    /** The cached copy of [mapTileIndex] of [source], or null when there is none. */
    fun entry(source: ITileSource, mapTileIndex: Long): Entry? = try {
        getTileCursor(
            getPrimaryKeyParameters(getIndex(mapTileIndex), source),
            arrayOf(COLUMN_EXPIRES, "length(${DatabaseFileArchive.COLUMN_TILE})"),
        )?.use { c -> if (c.moveToFirst()) Entry(c.getLong(0), c.getLong(1)) else null }
    } catch (e: Exception) {
        Log.w(TAG, "tile lookup failed", e)
        null
    }

    /** Bytes of tile images stored for every source (浏览 + 预缓存). */
    fun storedBytes(): Long = try {
        db?.rawQuery(
            "SELECT SUM(LENGTH(${DatabaseFileArchive.COLUMN_TILE})) FROM ${DatabaseFileArchive.TABLE}",
            null,
        )?.use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else 0L } ?: 0L
    } catch (e: Exception) {
        Log.w(TAG, "size query failed", e)
        0L
    }

    /** Deletes every cached tile and gives the space back to the file system. */
    fun clearAll(): Boolean {
        val ok = purgeCache()
        try {
            db?.execSQL("VACUUM")
        } catch (e: Exception) {
            Log.w(TAG, "vacuum failed", e)
        }
        return ok
    }

    private companion object {
        const val TAG = "TileCache"
    }
}
