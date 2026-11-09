package com.github.mytv.myearthquakealert.wear.ui.map

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex

/**
 * The Autonavi (AMap) raster basemap with Chinese labels: the one tile host that loads
 * reliably on mainland China networks.
 *
 * Copied from `:app` `ui.map.MapTileSources.AMAP` (keep the two in sync).
 */
object MapTiles {

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

    /**
     * Night rendering of the raster basemap (`invert(1) hue-rotate(180deg) brightness(.86)
     * contrast(.92) saturate(.65)` in the design).
     *
     * Copied from `:app` `ui.map.darkTileFilter` (keep the two in sync).
     */
    fun darkTileFilter(): ColorMatrixColorFilter {
        val m = ColorMatrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
        m.postConcat(
            ColorMatrix(
                floatArrayOf(
                    -0.574f, 1.430f, 0.144f, 0f, 0f,
                    0.426f, 0.430f, 0.144f, 0f, 0f,
                    0.426f, 1.430f, -0.856f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
        m.postConcat(ColorMatrix().apply { setScale(0.86f, 0.86f, 0.86f, 1f) })
        val c = 0.92f
        val offset = 255f * (0.5f - 0.5f * c)
        m.postConcat(
            ColorMatrix(
                floatArrayOf(
                    c, 0f, 0f, 0f, offset,
                    0f, c, 0f, 0f, offset,
                    0f, 0f, c, 0f, offset,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
        m.postConcat(ColorMatrix().apply { setSaturation(0.65f) })
        return ColorMatrixColorFilter(m)
    }
}
