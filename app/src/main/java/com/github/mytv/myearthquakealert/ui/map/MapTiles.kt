package com.github.mytv.myearthquakealert.ui.map

import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.MapTileIndex

/**
 * Map basemap styles offered by the app.
 *
 * AMAP uses the Autonavi (Gaodi) public raster endpoint: the only tile host that
 * loads reliably on mainland China networks, with Chinese labels. Tiles are in the
 * GCJ-02 datum; the ~500 m offset versus WGS-84 quake coordinates is far below one
 * pixel at the zoom levels this app uses (hundreds of km of view).
 *
 * OSM is the official tile server, kept as an alternative for users outside China.
 */
enum class EewMapStyle {
    AMAP,
    OSM;

    fun tileSource(): ITileSource = when (this) {
        AMAP -> MapTileSources.AMAP
        OSM -> TileSourceFactory.MAPNIK
    }

    companion object {
        fun fromName(name: String?): EewMapStyle =
            entries.firstOrNull { it.name == name } ?: AMAP
    }
}

object MapTileSources {

    val AMAP: ITileSource = object : OnlineTileSourceBase(
        "AMap",
        3,
        18,
        256,
        ".png",
        arrayOf(
            "https://webrd01.is.autonavi.com/appmaptile?",
            "https://webrd02.is.autonavi.com/appmaptile?",
            "https://webrd03.is.autonavi.com/appmaptile?",
            "https://webrd04.is.autonavi.com/appmaptile?",
        ),
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)
            val z = MapTileIndex.getZoom(pMapTileIndex)
            return "${baseUrl}lang=zh_cn&size=1&scale=1&style=7&x=$x&y=$y&z=$z"
        }
    }
}
