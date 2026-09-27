package com.github.mytv.myearthquakealert.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** A latitude/longitude pair, kept free of osmdroid types for callers. */
data class MapPoint(val latitude: Double, val longitude: Double) {
    fun toGeoPoint(): GeoPoint = GeoPoint(latitude, longitude)
}

enum class EarthquakeMapMode { ALERT, BROWSE }

/** One earthquake dot on the browse map. */
data class QuakeMarker(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val colorArgb: Int,
    val radiusDp: Float,
)

/**
 * The real map, backed by osmdroid.
 *
 * ALERT mode: epicenter + user position + expanding P/S wave circles + distance line.
 * BROWSE mode: user position + earthquake dots, tap to select.
 *
 * Fits the view to all content once per content change; afterwards the user is in
 * control (browse mode only — alert mode keeps multi-touch off).
 */
@Composable
fun EarthquakeMap(
    style: EewMapStyle,
    mode: EarthquakeMapMode,
    modifier: Modifier = Modifier,
    userPoint: MapPoint? = null,
    interactive: Boolean = mode == EarthquakeMapMode.BROWSE,
    epicenter: MapPoint? = null,
    pWaveRadiusKm: Double? = null,
    sWaveRadiusKm: Double? = null,
    warnZoneRadiusKm: Double? = null,
    showDistanceLine: Boolean = false,
    quakes: List<QuakeMarker> = emptyList(),
    selectedQuakeId: String? = null,
    focusPoint: MapPoint? = null,
    onQuakeClick: ((QuakeMarker) -> Unit)? = null,
    maxFitDistanceKm: Double? = null,
    showAttribution: Boolean = true,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density

    val mapView = remember {
        MapView(context).apply {
            setBuiltInZoomControls(false)
            setMultiTouchControls(false)
            setUseDataConnection(true)
            setTilesScaledToDpi(true)
            minZoomLevel = 3.0
            maxZoomLevel = 19.0
            setBackgroundColor(0xFFE6EAF0.toInt())
            controller.setZoom(4.5)
            controller.setCenter(GeoPoint(34.5, 108.9))
        }
    }

    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }

    LaunchedEffect(style) {
        mapView.setTileSource(style.tileSource())
        mapView.invalidate()
    }

    LaunchedEffect(interactive) {
        mapView.setMultiTouchControls(interactive)
    }

    // ---- Fit the viewport once per content change ----
    val signature = buildString {
        append(mode.name).append('|')
        append(userPoint?.latitude).append(',').append(userPoint?.longitude).append('|')
        append(epicenter?.latitude).append(',').append(epicenter?.longitude).append('|')
        append(quakes.joinToString(",") { "${it.id}:${it.latitude}:${it.longitude}" })
    }
    LaunchedEffect(signature) {
        val userGeo = userPoint?.toGeoPoint()
        var targets = buildList {
            userGeo?.let { add(it) }
            epicenter?.let { add(it.toGeoPoint()) }
            quakes.forEach { add(GeoPoint(it.latitude, it.longitude)) }
        }
        if (targets.isEmpty()) return@LaunchedEffect

        // Keep world-spanning outliers (e.g. a M6.6 near Vanuatu) from zooming the
        // viewport out to the whole globe — fit around the user's neighbourhood.
        if (maxFitDistanceKm != null && targets.size > 1) {
            val anchor = userGeo ?: targets.first()
            targets = targets.filter { p ->
                val dLatKm = (p.latitude - anchor.latitude) * 111.0
                val dLonKm = (p.longitude - anchor.longitude) * 111.0 * cos(Math.toRadians(anchor.latitude))
                dLatKm * dLatKm + dLonKm * dLonKm <= maxFitDistanceKm * maxFitDistanceKm
            }.ifEmpty { listOf(anchor) }
        }
        val fitPoints = targets

        val fit = {
            if (fitPoints.size == 1) {
                mapView.controller.setZoom(if (mode == EarthquakeMapMode.ALERT) 8.0 else 6.0)
                mapView.controller.setCenter(fitPoints.first())
            } else {
                val box = BoundingBox.fromGeoPoints(fitPoints)
                mapView.zoomToBoundingBox(box, false, (72 * density).toInt())
                val z = mapView.zoomLevelDouble
                if (z > 4.0) mapView.controller.setZoom(z - 0.5)
            }
            mapView.invalidate()
        }
        mapView.post {
            if (mapView.width > 0 && mapView.height > 0) fit()
            else mapView.post { fit() }
        }
    }

    LaunchedEffect(focusPoint) {
        val p = focusPoint ?: return@LaunchedEffect
        mapView.controller.animateTo(p.toGeoPoint())
    }

    // ---- Overlays, rebuilt on every content update ----
    val epicenterColor = Color(0xFFE60012).toArgb()
    val userColor = Color(0xFF2979FF).toArgb()
    val pWaveColor = Color(0xFF2196F3).toArgb()
    val sWaveColor = Color(0xFFF44336).toArgb()
    val selectedRingColor = Color(0xFFFFB300).toArgb()

    Box(modifier = modifier) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = { mv ->
                mv.overlays.clear()

            if (mode == EarthquakeMapMode.ALERT && epicenter != null) {
                // Expected strong-shaking zone (broadcast-style yellow highlight).
                warnZoneRadiusKm?.takeIf { it > 1.0 }?.let { r ->
                    mv.overlays.add(
                        warningZonePolygon(mv, epicenter.toGeoPoint(), r, density)
                    )
                }
                sWaveRadiusKm?.takeIf { it > 1.0 }?.let { r ->
                    mv.overlays.add(
                        wavePolygon(mv, epicenter.toGeoPoint(), r, sWaveColor, density)
                    )
                }
                pWaveRadiusKm?.takeIf { it > 1.0 }?.let { r ->
                    mv.overlays.add(
                        wavePolygon(mv, epicenter.toGeoPoint(), r, pWaveColor, density)
                    )
                }
                if (showDistanceLine && userPoint != null) {
                    mv.overlays.add(
                        distanceLine(mv, userPoint.toGeoPoint(), epicenter.toGeoPoint(), density)
                    )
                }
            }

            userPoint?.let {
                mv.overlays.add(
                    dotMarker(
                        mv, context, it.toGeoPoint(),
                        drawable = dotDrawable(context, userColor, 0xFFFFFFFF.toInt(), 6f, 0x552979FF),
                    )
                )
            }

            if (mode == EarthquakeMapMode.ALERT && epicenter != null) {
                mv.overlays.add(
                    dotMarker(
                        mv, context, epicenter.toGeoPoint(),
                        drawable = crossDrawable(context, epicenterColor),
                    )
                )
            }

            if (mode == EarthquakeMapMode.BROWSE) {
                quakes.forEach { q ->
                    val selected = q.id == selectedQuakeId
                    val ring = if (selected) selectedRingColor else 0xFFFFFFFF.toInt()
                    val radius = if (selected) q.radiusDp + 3f else q.radiusDp
                    val marker = dotMarker(
                        mv, context, GeoPoint(q.latitude, q.longitude),
                        drawable = dotDrawable(context, q.colorArgb, ring, radius, null),
                    )
                    if (onQuakeClick != null) {
                        marker.setOnMarkerClickListener { _, _ ->
                            onQuakeClick.invoke(q)
                            true
                        }
                    }
                    mv.overlays.add(marker)
                }
            }

            mv.invalidate()
        },
    )

        // Attribution chip
        if (showAttribution) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color(0x66000000),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp),
            ) {
                Text(
                    text = if (style == EewMapStyle.AMAP) "© 高德" else "© OpenStreetMap",
                    color = Color.White,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// osmdroid helpers
// ---------------------------------------------------------------------------

private fun wavePolygon(
    mapView: MapView,
    center: GeoPoint,
    radiusKm: Double,
    strokeArgb: Int,
    density: Float,
): Polygon = Polygon(mapView).apply {
    setPoints(circlePoints(center.latitude, center.longitude, radiusKm))
    fillColor = (strokeArgb and 0x00FFFFFF) or 0x2B000000
    outlinePaint.color = strokeArgb
    outlinePaint.strokeWidth = 3f * density
    isGeodesic = true
}

/** Translucent yellow disc for the area expected to reach the strong-shaking threshold. */
private fun warningZonePolygon(
    mapView: MapView,
    center: GeoPoint,
    radiusKm: Double,
    density: Float,
): Polygon = Polygon(mapView).apply {
    setPoints(circlePoints(center.latitude, center.longitude, radiusKm, segments = 96))
    fillColor = 0x40FFE100
    outlinePaint.color = 0xFFFFE100.toInt()
    outlinePaint.strokeWidth = 3f * density
    isGeodesic = true
}

private fun distanceLine(
    mapView: MapView,
    from: GeoPoint,
    to: GeoPoint,
    density: Float,
): Polyline = Polyline(mapView).apply {
    setPoints(listOf(from, to))
    outlinePaint.color = 0xCCFFFFFF.toInt()
    outlinePaint.strokeWidth = 2f * density
    outlinePaint.pathEffect = DashPathEffect(floatArrayOf(12f * density, 8f * density), 0f)
}

private fun dotMarker(
    mapView: MapView,
    context: Context,
    point: GeoPoint,
    drawable: Drawable,
): Marker = Marker(mapView).apply {
    position = point
    icon = drawable
    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
    infoWindow = null
    isDraggable = false
}

/** Broadcast-style epicenter cross: bold colored X over a white outline. */
private fun crossDrawable(context: Context, colorArgb: Int): Drawable {
    val density = context.resources.displayMetrics.density
    val size = (32f * density).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val pad = 6f * density

    val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        strokeWidth = 8f * density
        strokeCap = Paint.Cap.ROUND
    }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorArgb
        strokeWidth = 5f * density
        strokeCap = Paint.Cap.ROUND
    }

    canvas.drawLine(pad, pad, size - pad, size - pad, outline)
    canvas.drawLine(size - pad, pad, pad, size - pad, outline)
    canvas.drawLine(pad, pad, size - pad, size - pad, stroke)
    canvas.drawLine(size - pad, pad, pad, size - pad, stroke)

    return BitmapDrawable(context.resources, bitmap)
}

/** A round dot with a ring, optionally a soft halo. Center-anchored. */
private fun dotDrawable(
    context: Context,
    fillArgb: Int,
    ringArgb: Int,
    coreRadiusDp: Float,
    haloArgb: Int?,
): Drawable {
    val density = context.resources.displayMetrics.density
    val core = coreRadiusDp * density
    val ringW = 2f * density
    val halo = if (haloArgb != null) 6f * density else 0f
    val size = ((core + ringW + halo) * 2f).toInt().coerceAtLeast(8)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = size / 2f
    val cy = size / 2f

    if (haloArgb != null) {
        canvas.drawCircle(cx, cy, core + ringW + halo, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = haloArgb })
    }
    canvas.drawCircle(cx, cy, core + ringW, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ringArgb })
    canvas.drawCircle(cx, cy, core, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fillArgb })

    return BitmapDrawable(context.resources, bitmap)
}

/**
 * Circle approximated as a polygon around [lat]/[lon] with [radiusKm] radius.
 * Equirectangular approximation — accurate enough at regional scale.
 */
private fun circlePoints(lat: Double, lon: Double, radiusKm: Double, segments: Int = 72): List<GeoPoint> {
    val latPerKm = 1.0 / 110.574
    val lonPerKm = 1.0 / (111.320 * cos(Math.toRadians(lat)))
    val points = ArrayList<GeoPoint>(segments + 1)
    for (i in 0..segments) {
        val angle = 2.0 * PI * i / segments
        points.add(
            GeoPoint(
                lat + radiusKm * cos(angle) * latPerKm,
                lon + radiusKm * sin(angle) * lonPerKm,
            )
        )
    }
    return points
}
