package com.github.mytv.myearthquakealert.wear

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.github.mytv.myearthquakealert.wear.complication.StatusComplicationService
import com.github.mytv.myearthquakealert.wear.tile.StatusTileService

/** Asks the system to re-read the tile and the complications after the store changed. */
object WearSurfaces {

    fun refresh(context: Context) {
        try {
            TileService.getUpdater(context).requestUpdate(StatusTileService::class.java)
        } catch (e: Exception) {
            Log.w("WearSurfaces", "tile update failed", e)
        }
        try {
            ComplicationDataSourceUpdateRequester
                .create(context, ComponentName(context, StatusComplicationService::class.java))
                .requestUpdateAll()
        } catch (e: Exception) {
            Log.w("WearSurfaces", "complication update failed", e)
        }
    }
}
