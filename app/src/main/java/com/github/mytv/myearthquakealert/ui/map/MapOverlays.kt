package com.github.mytv.myearthquakealert.ui.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.Typeface
import android.util.Log
import androidx.compose.runtime.Immutable
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.domain.SeismicCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay

/**
 * Estimated-intensity contours of one earthquake (design §7 估计烈度等值线): the radii at
 * which [SeismicCalculator.calcLocalIntensity] falls to each level of [levels].
 */
@Immutable
data class IntensityContours(
    val epicenter: MapPoint,
    val magnitude: Double,
    val depthKm: Double,
    val levels: IntRange = 3..7,
) {
    /** level → radius in km, outermost (lowest level) first; levels never reached are omitted. */
    val radii: List<Pair<Int, Double>> by lazy { contourRadii(magnitude, depthKm, levels) }
}

private const val MIN_RADIUS_KM = 0.5
private const val MAX_RADIUS_KM = 2500.0

/**
 * Inverts the CSIS attenuation by bisection: intensity only decreases with distance, so the
 * contour of level k is where the estimate crosses k.
 */
fun contourRadii(magnitude: Double, depthKm: Double, levels: IntRange): List<Pair<Int, Double>> =
    levels.mapNotNull { level ->
        val k = level.toDouble()
        if (SeismicCalculator.calcLocalIntensity(magnitude, depthKm, MIN_RADIUS_KM) < k) return@mapNotNull null
        if (SeismicCalculator.calcLocalIntensity(magnitude, depthKm, MAX_RADIUS_KM) >= k) return@mapNotNull level to MAX_RADIUS_KM
        var lo = MIN_RADIUS_KM
        var hi = MAX_RADIUS_KM
        repeat(40) {
            val mid = (lo + hi) / 2
            if (SeismicCalculator.calcLocalIntensity(magnitude, depthKm, mid) >= k) lo = mid else hi = mid
        }
        level to (lo + hi) / 2
    }

/** Geodesic circle as lat/lon points every [stepDeg] degrees of bearing. */
internal fun geodesicCircle(center: MapPoint, radiusKm: Double, stepDeg: Int = 5): List<GeoPoint> =
    (0..360 step stepDeg).map { b ->
        val p = SeismicCalculator.offsetPoint(center.latitude, center.longitude, b.toDouble(), radiusKm)
        GeoPoint(p.latitude, p.longitude)
    }

/** Text with a contrasting halo, as the design draws map labels. */
internal class HaloText(density: Float, sizeSp: Float, bold: Boolean = true) {
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sizeSp * density
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }
    val halo = Paint(fill).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        strokeJoin = Paint.Join.ROUND
    }

    fun draw(canvas: Canvas, text: String, x: Float, y: Float, dark: Boolean, align: Paint.Align = Paint.Align.LEFT) {
        fill.color = if (dark) 0xFFFFFFFF.toInt() else 0xFF111111.toInt()
        halo.color = if (dark) 0xB3000000.toInt() else 0xE6FFFFFF.toInt()
        fill.textAlign = align
        halo.textAlign = align
        canvas.drawText(text, x, y, halo)
        canvas.drawText(text, x, y, fill)
    }
}

/**
 * Concentric estimated-intensity rings, CSIS-coloured, labelled with Roman numerals on the
 * east edge when the label clears the epicentre mark and the previous (outer) label.
 */
internal class ContourOverlay(
    private val contours: IntensityContours,
    private val colorFor: (Int) -> Int,
    private val labelFor: (Int) -> String,
    private val density: Float,
    private val dark: Boolean,
) : Overlay() {
    private val rings: List<Triple<Int, List<GeoPoint>, GeoPoint>> = contours.radii.map { (level, r) ->
        val east = SeismicCalculator.offsetPoint(contours.epicenter.latitude, contours.epicenter.longitude, 90.0, r)
        Triple(level, geodesicCircle(contours.epicenter, r), GeoPoint(east.latitude, east.longitude))
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.4f * density
    }
    private val text = HaloText(density, 11f)
    private val path = Path()
    private val point = Point()

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val projection = mapView.projection
        // Outermost ring first so the stronger, inner rings stay on top.
        for ((level, ring, _) in rings) {
            buildPath(projection, ring)
            val c = colorFor(level)
            fill.color = (c and 0x00FFFFFF) or (0x42 shl 24)
            stroke.color = c
            canvas.drawPath(path, fill)
            canvas.drawPath(path, stroke)
        }
        projection.toPixels(GeoPoint(contours.epicenter.latitude, contours.epicenter.longitude), point)
        val ex = point.x.toFloat()
        var lastX = Float.MAX_VALUE
        for ((level, _, east) in rings) {
            projection.toPixels(east, point)
            val x = point.x.toFloat()
            if (x - ex > 16f * density && lastX - x > 20f * density) {
                text.draw(canvas, labelFor(level), x + 2f * density, point.y + 4f * density, dark)
                lastX = x
            }
        }
    }

    private fun buildPath(projection: Projection, ring: List<GeoPoint>) {
        path.rewind()
        ring.forEachIndexed { i, g ->
            projection.toPixels(g, point)
            if (i == 0) path.moveTo(point.x.toFloat(), point.y.toFloat()) else path.lineTo(point.x.toFloat(), point.y.toFloat())
        }
        path.close()
    }
}

/** Dashed epicentre–user line with the distance written at its middle. */
internal class DistanceLabelOverlay(
    private val from: GeoPoint,
    private val to: GeoPoint,
    private val label: String,
    private val density: Float,
    private val dark: Boolean,
) : Overlay() {
    private val text = HaloText(density, 11f)
    private val a = Point()
    private val b = Point()

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        mapView.projection.toPixels(from, a)
        mapView.projection.toPixels(to, b)
        text.draw(canvas, label, (a.x + b.x) / 2f, (a.y + b.y) / 2f - 6f * density, dark, Paint.Align.CENTER)
    }
}

/**
 * Bundled vector basemap shown when no tile can be loaded (design §7 兜底: 内置的矢量海岸线和
 * 省界轮廓). Natural Earth (public domain): 1:50m coastline, rivers and lakes, and the China
 * provincial boundaries from the 1:10m admin-1 lines, Douglas–Peucker simplified to 0.02°.
 * Every line is a flat `[lon, lat, lon, lat, …]` array. Loaded once per process.
 */
object BasemapOutline {
    class Data(
        val coast: List<FloatArray>,
        val rivers: List<FloatArray>,
        val lakes: List<FloatArray>,
        val provinces: List<FloatArray> = emptyList(),
    )

    @Volatile
    private var cached: Data? = null

    suspend fun load(context: Context): Data? {
        cached?.let { return it }
        return withContext(Dispatchers.IO) {
            cached ?: try {
                val text = context.resources.openRawResource(R.raw.ui_basemap_outline).bufferedReader().use { it.readText() }
                val json = JSONObject(text)
                Data(json.lines("coast"), json.lines("rivers"), json.lines("lakes"), json.lines("provinces")).also { cached = it }
            } catch (e: Exception) {
                Log.w("BasemapOutline", "Cannot read the bundled outline", e)
                null
            }
        }
    }

    private fun JSONObject.lines(key: String): List<FloatArray> {
        val arr = optJSONArray(key) ?: return emptyList()
        return List(arr.length()) { i -> arr.getJSONArray(i).toFloats() }
    }

    private fun JSONArray.toFloats(): FloatArray = FloatArray(length()) { getDouble(it).toFloat() }
}

/**
 * Draws [BasemapOutline] plus capital-city dots and names over a neutral background:
 * lakes, rivers, then the provincial boundaries as a thinner dashed line under the coast.
 */
internal class OutlineOverlay(
    private val data: BasemapOutline.Data,
    private val cities: List<ChinaCity>,
    private val density: Float,
    private val dark: Boolean,
) : Overlay() {
    private val coast = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.3f * density
        strokeJoin = Paint.Join.ROUND
        color = if (dark) 0xFF8FA3B8.toInt() else 0xFF5B6B7B.toInt()
    }
    private val river = Paint(coast).apply {
        strokeWidth = 0.8f * density
        color = if (dark) 0xFF3F6E95.toInt() else 0xFF8DB4D8.toInt()
    }
    private val province = Paint(coast).apply {
        strokeWidth = 0.7f * density
        color = if (dark) 0xFF6F8296.toInt() else 0xFF8C99A6.toInt()
        pathEffect = DashPathEffect(floatArrayOf(4f * density, 2.5f * density), 0f)
    }
    private val lakeFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = if (dark) 0xFF24476A.toInt() else 0xFFB9D3EC.toInt()
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (dark) 0xFFC9D1D9.toInt() else 0xFF41474D.toInt() }
    private val text = HaloText(density, 10f, bold = false)
    private val path = Path()
    private val point = Point()
    private val geo = GeoPoint(0.0, 0.0)

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val projection = mapView.projection
        for (line in data.lakes) {
            buildPath(projection, line, close = true)
            canvas.drawPath(path, lakeFill)
        }
        for (line in data.rivers) {
            buildPath(projection, line, close = false)
            canvas.drawPath(path, river)
        }
        for (line in data.provinces) {
            buildPath(projection, line, close = false)
            canvas.drawPath(path, province)
        }
        for (line in data.coast) {
            buildPath(projection, line, close = false)
            canvas.drawPath(path, coast)
        }
        // City names only once the view is regional, so they never pile up.
        val showNames = mapView.zoomLevelDouble >= 5.0
        for (city in cities) {
            if (!city.capital) continue
            geo.setCoords(city.latitude, city.longitude)
            projection.toPixels(geo, point)
            canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), 2.5f * density, dot)
            if (showNames) text.draw(canvas, city.name, point.x + 5f * density, point.y + 3.5f * density, dark)
        }
    }

    private fun buildPath(projection: Projection, line: FloatArray, close: Boolean) {
        path.rewind()
        var i = 0
        while (i + 1 < line.size) {
            geo.setCoords(line[i + 1].toDouble(), line[i].toDouble())
            projection.toPixels(geo, point)
            if (i == 0) path.moveTo(point.x.toFloat(), point.y.toFloat()) else path.lineTo(point.x.toFloat(), point.y.toFloat())
            i += 2
        }
        if (close) path.close()
    }
}

/**
 * Night rendering of the raster basemaps, matching the design's CSS filter
 * `invert(1) hue-rotate(180deg) brightness(.86) contrast(.92) saturate(.65)`: water and land
 * keep their hues while the light background becomes dark. Satellite imagery is not filtered.
 */
internal fun darkTileFilter(): ColorMatrixColorFilter {
    val m = ColorMatrix(
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        ),
    )
    // hue-rotate(180deg), coefficients from the Filter Effects spec with cos = -1, sin = 0.
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
