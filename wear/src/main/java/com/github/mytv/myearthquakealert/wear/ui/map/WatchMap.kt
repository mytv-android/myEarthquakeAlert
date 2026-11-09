package com.github.mytv.myearthquakealert.wear.ui.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.github.mytv.myearthquakealert.wear.data.WatchAlert
import com.github.mytv.myearthquakealert.wear.data.WireQuake
import com.github.mytv.myearthquakealert.wear.domain.SeismicCalculator
import com.github.mytv.myearthquakealert.wear.domain.StandaloneEvaluator
import com.github.mytv.myearthquakealert.wear.ui.theme.Csis
import com.github.mytv.myearthquakealert.wear.ui.theme.WatchColors
import kotlinx.coroutines.delay
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/** What the round map shows. */
data class MapContent(
    /** Alert in progress: epicenter, user and the moving P / S fronts; the view fits all three. */
    val alert: WatchAlert? = null,
    val quakes: List<WireQuake> = emptyList(),
    /** Quake to centre on (from the status list). */
    val focusEventId: String? = null,
    val userLatitude: Double? = null,
    val userLongitude: Double? = null,
)

/**
 * Round osmdroid map (design scrWMap): AMap tiles in the night filter, clipped to the
 * watch circle. The crown zooms; a double tap zooms in. The map itself does not pan, so a
 * swipe still goes back.
 */
@Composable
fun WatchMap(content: MapContent, chip: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val mapView = remember { createMapView(context) }
    val overlay = remember { QuakeOverlay() }
    val focus = remember { FocusRequester() }

    DisposableEffect(mapView) {
        mapView.overlays.add(overlay)
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }

    LaunchedEffect(content) {
        overlay.content = content
        fit(mapView, content)
        mapView.invalidate()
    }
    // The fronts move: redraw while an alert is shown.
    LaunchedEffect(content.alert != null) {
        while (content.alert != null) {
            mapView.invalidate()
            delay(FRAME_MS)
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Box(
        modifier
            .fillMaxSize()
            .clip(CircleShape)
            .background(WatchColors.Background)
            .onRotaryScrollEvent { event ->
                val zoom = (mapView.zoomLevelDouble + event.verticalScrollPixels / ROTARY_PX_PER_LEVEL)
                    .coerceIn(mapView.minZoomLevel, mapView.maxZoomLevel)
                mapView.controller.setZoom(zoom)
                true
            }
            .focusRequester(focus)
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        // Takes the touches: taps zoom, drags fall through to swipe-to-dismiss.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { mapView.controller.zoomIn() })
                },
        )
        if (chip != null) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 22.dp)
                    .clip(RoundedCornerShape(50))
                    .background(WatchColors.Chip)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text(chip, style = MaterialTheme.typography.labelSmall, color = WatchColors.Text, maxLines = 1)
            }
        }
    }
}

private const val FRAME_MS = 100L
private const val ROTARY_PX_PER_LEVEL = 120f
private val CHINA = GeoPoint(35.0, 105.0)

private fun createMapView(context: Context): MapView = MapView(context).apply {
    setTileSource(MapTiles.AMAP)
    overlayManager.tilesOverlay.setColorFilter(MapTiles.darkTileFilter())
    overlayManager.tilesOverlay.loadingBackgroundColor = WatchColors.Background.toArgb()
    overlayManager.tilesOverlay.loadingLineColor = WatchColors.Chip.toArgb()
    zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
    setMultiTouchControls(false)
    isTilesScaledToDpi = true
    isHorizontalMapRepetitionEnabled = false
    isVerticalMapRepetitionEnabled = false
    minZoomLevel = 3.0
    maxZoomLevel = 12.0
    controller.setZoom(3.5)
    controller.setCenter(CHINA)
}

/** Centres the view on what matters: the alert triangle, the focused quake, or everything. */
private fun fit(map: MapView, content: MapContent) {
    val user = if (content.userLatitude != null && content.userLongitude != null) GeoPoint(content.userLatitude, content.userLongitude) else null
    val alert = content.alert
    val focused = content.quakes.firstOrNull { it.eventId == content.focusEventId }
    val points: List<GeoPoint>
    var minSpanKm = 150.0
    when {
        alert != null -> {
            // The S front when it reaches the user: epicenter, user and front all in view.
            val reach = max(alert.distanceKm, 50.0) * 1.05
            points = listOf(GeoPoint(alert.latitude, alert.longitude), GeoPoint(alert.userLatitude, alert.userLongitude)) +
                listOf(0.0, 90.0, 180.0, 270.0).map { b ->
                    val o = SeismicCalculator.offsetPoint(alert.latitude, alert.longitude, b, reach)
                    GeoPoint(o.latitude, o.longitude)
                }
        }
        focused != null -> {
            points = listOfNotNull(GeoPoint(focused.latitude, focused.longitude), user?.takeIf { focused.distanceKm in 1.0..800.0 })
            minSpanKm = 300.0
        }
        else -> points = content.quakes.map { GeoPoint(it.latitude, it.longitude) } + listOfNotNull(user)
    }
    if (points.isEmpty()) {
        map.controller.setZoom(3.5)
        map.controller.setCenter(CHINA)
        return
    }
    val apply = Runnable {
        val box = BoundingBox.fromGeoPointsSafe(points).let { b ->
            // A single point (or a tight cluster) still shows its surroundings.
            val halfDeg = minSpanKm / 111.0 / 2
            if (b.latitudeSpan < halfDeg * 2 && b.longitudeSpanWithDateLine < halfDeg * 2) {
                BoundingBox(b.centerLatitude + halfDeg, b.centerLongitude + halfDeg, b.centerLatitude - halfDeg, b.centerLongitude - halfDeg)
            } else {
                b
            }
        }
        // The visible disc is the inscribed circle: keep a generous border.
        val border = (map.width.coerceAtLeast(1) * 0.18).toInt()
        map.zoomToBoundingBox(box, false, border)
    }
    if (map.width > 0 && map.height > 0) apply.run() else map.addOnFirstLayoutListener { _, _, _, _, _ -> apply.run() }
}

/** Quake dots, the epicenter cross, the P / S fronts and the user dot. */
private class QuakeOverlay : Overlay() {

    @Volatile var content: MapContent = MapContent()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val p = Point()
    private val q = Point()

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val c = content
        val projection = mapView.projection
        val density = mapView.resources.displayMetrics.density

        for (quake in c.quakes.asReversed()) {
            if (c.alert?.eventId == quake.eventId) continue
            projection.toPixels(GeoPoint(quake.latitude, quake.longitude), p)
            val r = ((2.6 + (quake.magnitude - 2.5) * 1.5).coerceIn(2.6, 9.0) * density).toFloat()
            fill.color = Csis.color(if (quake.distanceKm > 0) quake.localCsis else quake.maxIntensity).toArgb()
            canvas.drawCircle(p.x.toFloat(), p.y.toFloat(), r, fill)
            if (quake.eventId == c.focusEventId) {
                stroke.color = WatchColors.Text.toArgb()
                stroke.strokeWidth = 2f * density
                canvas.drawCircle(p.x.toFloat(), p.y.toFloat(), r + 3f * density, stroke)
            }
        }

        val alert = c.alert
        val userLat = alert?.userLatitude ?: c.userLatitude
        val userLon = alert?.userLongitude ?: c.userLongitude
        if (alert != null) {
            val elapsed = ((System.currentTimeMillis() - alert.originAt) / 1000.0).coerceAtLeast(0.0)
            val depth = alert.depthKm ?: StandaloneEvaluator.DEFAULT_DEPTH_KM
            drawFront(canvas, mapView, alert, SeismicCalculator.calcWaveRadius(depth, elapsed, isPWave = true), WatchColors.PWave.toArgb(), density)
            drawFront(canvas, mapView, alert, SeismicCalculator.calcWaveRadius(depth, elapsed, isPWave = false), WatchColors.SWave.toArgb(), density)
            projection.toPixels(GeoPoint(alert.latitude, alert.longitude), p)
            drawCross(canvas, p.x.toFloat(), p.y.toFloat(), 7f * density, density)
        }
        if (userLat != null && userLon != null) {
            projection.toPixels(GeoPoint(userLat, userLon), p)
            fill.color = android.graphics.Color.WHITE
            canvas.drawCircle(p.x.toFloat(), p.y.toFloat(), 6f * density, fill)
            fill.color = WatchColors.UserDot.toArgb()
            canvas.drawCircle(p.x.toFloat(), p.y.toFloat(), 4.5f * density, fill)
        }
    }

    private fun drawFront(canvas: Canvas, map: MapView, alert: WatchAlert, radiusKm: Double, color: Int, density: Float) {
        if (radiusKm <= 0.5) return
        val projection = map.projection
        projection.toPixels(GeoPoint(alert.latitude, alert.longitude), p)
        val edge = SeismicCalculator.offsetPoint(alert.latitude, alert.longitude, 90.0, radiusKm)
        projection.toPixels(GeoPoint(edge.latitude, edge.longitude), q)
        val r = hypot((q.x - p.x).toFloat(), (q.y - p.y).toFloat())
        if (r < 1f || abs(r) > 20_000f) return
        fill.color = color
        fill.alpha = 0x22
        canvas.drawCircle(p.x.toFloat(), p.y.toFloat(), r, fill)
        fill.alpha = 0xFF
        stroke.color = color
        stroke.strokeWidth = 2f * density
        canvas.drawCircle(p.x.toFloat(), p.y.toFloat(), r, stroke)
    }

    private fun drawCross(canvas: Canvas, x: Float, y: Float, half: Float, density: Float) {
        stroke.color = android.graphics.Color.WHITE
        stroke.strokeWidth = 5f * density
        canvas.drawLine(x - half, y - half, x + half, y + half, stroke)
        canvas.drawLine(x - half, y + half, x + half, y - half, stroke)
        stroke.color = WatchColors.AlertRed.toArgb()
        stroke.strokeWidth = 3f * density
        canvas.drawLine(x - half, y - half, x + half, y + half, stroke)
        canvas.drawLine(x - half, y + half, x + half, y - half, stroke)
    }
}
