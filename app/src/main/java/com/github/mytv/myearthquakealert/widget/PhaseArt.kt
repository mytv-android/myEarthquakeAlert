package com.github.mytv.myearthquakealert.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.util.LruCache
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import com.github.mytv.myearthquakealert.service.MonitorPhase

/** Icons the widget draws besides the phase icons. */
internal enum class WidgetIcon(val vector: ImageVector) {
    PAUSE(Icons.Filled.Pause),
    RESUME(Icons.Filled.PlayArrow),
    POWER(Icons.Filled.PowerSettingsNew),
}

/**
 * White bitmaps of the status shapes and icons for the widget, tinted there per phase.
 * RemoteViews cannot draw polygons, so each phase's MaterialShapes polygon — the same one
 * [com.github.mytv.myearthquakealert.ui.components.StatusShape] clips to — and each Material
 * icon is rasterised once per pixel size and cached (the same Bitmap instance is shared by
 * every responsive layout, so RemoteViews parcels it once).
 */
internal object PhaseArt {

    private val cache = object : LruCache<String, Bitmap>(256 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    private fun polygon(phase: MonitorPhase): RoundedPolygon = when (phase) {
        MonitorPhase.MONITORING, MonitorPhase.CONNECTING -> MaterialShapes.Cookie9Sided
        MonitorPhase.RECONNECTING -> MaterialShapes.Clover4Leaf
        MonitorPhase.DEGRADED -> MaterialShapes.Gem
        MonitorPhase.LIMITED -> MaterialShapes.Triangle
        MonitorPhase.PAUSED -> MaterialShapes.Pill
        MonitorPhase.OFF -> MaterialShapes.Square
    }

    /** The icon inside the shape, as on [com.github.mytv.myearthquakealert.ui.components.StatusShape]. */
    fun phaseIcon(phase: MonitorPhase): ImageVector? = when (phase) {
        MonitorPhase.MONITORING -> Icons.Filled.Sensors
        MonitorPhase.CONNECTING -> null
        MonitorPhase.RECONNECTING -> Icons.Filled.Sync
        MonitorPhase.DEGRADED -> Icons.Filled.SyncProblem
        MonitorPhase.LIMITED -> Icons.Filled.Warning
        MonitorPhase.PAUSED -> Icons.Filled.Pause
        MonitorPhase.OFF -> Icons.Filled.PowerSettingsNew
    }

    /** The phase's status shape filling a [sizePx] square, white on transparent. */
    fun shape(phase: MonitorPhase, sizePx: Int): Bitmap = cached("shape:${phase.name}:$sizePx") {
        val polygon = polygon(phase).normalized()
        val path = polygon.toPath()
        path.transform(Matrix().apply { setScale(sizePx.toFloat(), sizePx.toFloat()) })
        draw(sizePx, listOf(path))
    }

    /** [vector] filling a [sizePx] square, white on transparent. */
    fun icon(vector: ImageVector, sizePx: Int): Bitmap = cached("icon:${vector.name}:$sizePx") {
        val scale = Matrix().apply { setScale(sizePx / vector.viewportWidth, sizePx / vector.viewportHeight) }
        val paths = ArrayList<Path>()
        addGroup(paths, vector.root, scale)
        draw(sizePx, paths)
    }

    /** Each vector path separately, so every one keeps its own fill rule. */
    private fun addGroup(target: MutableList<Path>, group: VectorGroup, parent: Matrix) {
        val matrix = Matrix(parent).apply {
            // Same order as VectorComponent: translate, rotate / scale about the pivot.
            preTranslate(group.translationX + group.pivotX, group.translationY + group.pivotY)
            preRotate(group.rotation)
            preScale(group.scaleX, group.scaleY)
            preTranslate(-group.pivotX, -group.pivotY)
        }
        for (node in group) {
            when (node) {
                is VectorGroup -> addGroup(target, node, matrix)
                is VectorPath -> {
                    val p = node.pathData.toPath().asAndroidPath()
                    p.fillType = if (node.pathFillType == PathFillType.EvenOdd) Path.FillType.EVEN_ODD else Path.FillType.WINDING
                    p.transform(matrix)
                    target += p
                }
            }
        }
    }

    private fun draw(sizePx: Int, paths: List<Path>): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
        paths.forEach { canvas.drawPath(it, paint) }
        return bitmap
    }

    private inline fun cached(key: String, create: () -> Bitmap): Bitmap =
        synchronized(cache) { cache.get(key) } ?: create().also { synchronized(cache) { cache.put(key, it) } }
}
