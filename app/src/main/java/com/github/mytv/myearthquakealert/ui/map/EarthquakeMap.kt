package com.github.mytv.myearthquakealert.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.ui.components.MapChip
import com.github.mytv.myearthquakealert.ui.theme.OfflineMapDark
import com.github.mytv.myearthquakealert.ui.theme.OfflineMapLight
import com.github.mytv.myearthquakealert.ui.theme.UserDotBlue
import com.github.mytv.myearthquakealert.ui.theme.csisColor
import com.github.mytv.myearthquakealert.ui.theme.romanIntensity
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.util.SimpleInvalidationHandler
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

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
    /** Soft halo marking the newest event. */
    val halo: Boolean = false,
)

/**
 * Commands for a map from outside the composable (map toolbar: zoom, locate, re-centre).
 * Create with [rememberEarthquakeMapController] and pass to [EarthquakeMap].
 */
@Stable
class EarthquakeMapController {
    internal var mapView: MapView? = null
    internal var refitRequest by mutableIntStateOf(0)

    fun zoomIn() {
        mapView?.controller?.zoomIn()
    }

    fun zoomOut() {
        mapView?.controller?.zoomOut()
    }

    /** Pans (and optionally zooms) to [point] with the default animation. */
    fun animateTo(point: MapPoint, zoom: Double? = null) {
        mapView?.controller?.animateTo(point.toGeoPoint(), zoom, null)
    }

    /** Fits the view to the content again, as when it was first shown. */
    fun recenter() {
        refitRequest++
    }
}

@Composable
fun rememberEarthquakeMapController(): EarthquakeMapController = remember { EarthquakeMapController() }

/**
 * The real map, backed by osmdroid.
 *
 * ALERT mode: epicenter + user position + expanding P/S wave circles + distance line.
 * BROWSE mode: user position + earthquake dots, tap to select.
 *
 * Fits the view to all content once per content change; afterwards the user is in
 * control (browse mode only — alert mode keeps multi-touch off).
 *
 * 2.0 additions (all optional): estimated-intensity [contours], a night rendering of the
 * raster tiles ([darkTiles], following the theme by default), the satellite label layer,
 * an offline fallback basemap with a 离线 chip ([offlineFallback]), an external
 * [controller], [touchEnabled] = false for map cards inside scrolling content (taps go to
 * [onMapClick]), [markEpicenter] / [distanceLabel] for browse-mode details, explicit
 * [fitPoints] and the [attributionAlignment].
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
    fitPaddingDp: Int = 72,
    showAttribution: Boolean = true,
    contours: IntensityContours? = null,
    darkTiles: Boolean? = null,
    offlineFallback: Boolean = true,
    controller: EarthquakeMapController? = null,
    touchEnabled: Boolean = true,
    onMapClick: (() -> Unit)? = null,
    markEpicenter: Boolean = mode == EarthquakeMapMode.ALERT,
    distanceLabel: String? = null,
    fitPoints: List<MapPoint>? = null,
    attributionAlignment: Alignment = Alignment.BottomEnd,
    showOfflineChip: Boolean = true,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val dark = darkTiles ?: (MaterialTheme.colorScheme.background.luminance() < 0.5f)

    val mapView = remember {
        MapView(context).apply {
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            setMultiTouchControls(false)
            setUseDataConnection(true)
            setTilesScaledToDpi(true)
            isHorizontalMapRepetitionEnabled = true
            isVerticalMapRepetitionEnabled = false
            minZoomLevel = 3.0
            maxZoomLevel = 19.0
            setBackgroundColor(OfflineMapLight.toArgb())
            // Never a D-pad stop: zoom and pan go through the toolbar, focus stays on Compose.
            isFocusable = false
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            this.controller.setZoom(4.5)
            this.controller.setCenter(GeoPoint(34.5, 108.9))
        }
    }

    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }

    DisposableEffect(controller) {
        controller?.mapView = mapView
        onDispose { if (controller?.mapView === mapView) controller.mapView = null }
    }

    // ---- Base layer, satellite labels and night rendering ----
    LaunchedEffect(style) {
        mapView.setTileSource(style.tileSource())
        mapView.invalidate()
    }

    val labelOverlay = remember(style) {
        style.labelOverlay()?.let { source ->
            val provider = MapTileProviderBasic(context.applicationContext, source)
            provider.tileRequestCompleteHandlers.add(SimpleInvalidationHandler(mapView))
            TilesOverlay(provider, context.applicationContext).apply {
                loadingBackgroundColor = android.graphics.Color.TRANSPARENT
                loadingLineColor = android.graphics.Color.TRANSPARENT
            }
        }
    }
    DisposableEffect(labelOverlay) {
        onDispose { labelOverlay?.onDetach(mapView) }
    }

    val darkFilter = remember { darkTileFilter() }
    LaunchedEffect(style, dark) {
        val tiles = mapView.overlayManager.tilesOverlay
        tiles.setColorFilter(if (dark && style != EewMapStyle.AMAP_SAT) darkFilter else null)
        val background = when {
            style == EewMapStyle.AMAP_SAT -> SatelliteBackground
            dark -> OfflineMapDark
            else -> OfflineMapLight
        }.toArgb()
        tiles.loadingBackgroundColor = background
        tiles.loadingLineColor = background
        mapView.setBackgroundColor(background)
        mapView.invalidate()
    }

    LaunchedEffect(interactive, touchEnabled) {
        mapView.setMultiTouchControls(interactive && touchEnabled)
    }

    // ---- Offline fallback: no tile drawable for a while → bundled outline + 离线 chip ----
    var offline by remember { mutableStateOf(false) }
    var outline by remember { mutableStateOf<BasemapOutline.Data?>(null) }
    LaunchedEffect(style, offlineFallback) {
        offline = false
        if (!offlineFallback) return@LaunchedEffect
        val since = SystemClock.elapsedRealtime()
        while (true) {
            delay(1_500)
            val states = mapView.overlayManager.tilesOverlay.tileStates
            val loaded = states.upToDate + states.expired + states.scaled
            val nothingDrawn = states.total > 0 && loaded == 0
            offline = nothingDrawn && (!hasNetwork(context) || SystemClock.elapsedRealtime() - since > OFFLINE_GRACE_MS)
        }
    }
    LaunchedEffect(offline) {
        if (offline && outline == null) outline = BasemapOutline.load(context)
    }

    // ---- Fit the viewport once per content change ----
    val signature = buildString {
        append(mode.name).append('|')
        append(userPoint?.latitude).append(',').append(userPoint?.longitude).append('|')
        append(epicenter?.latitude).append(',').append(epicenter?.longitude).append('|')
        append(quakes.joinToString(",") { "${it.id}:${it.latitude}:${it.longitude}" }).append('|')
        append(fitPoints?.joinToString(",") { "${it.latitude}:${it.longitude}" })
    }
    LaunchedEffect(signature, controller?.refitRequest) {
        val userGeo = userPoint?.toGeoPoint()
        var targets = fitPoints?.map { it.toGeoPoint() } ?: buildList {
            userGeo?.let { add(it) }
            epicenter?.let { add(it.toGeoPoint()) }
            quakes.forEach { add(GeoPoint(it.latitude, it.longitude)) }
        }
        if (targets.isEmpty()) return@LaunchedEffect

        // Keep world-spanning outliers (e.g. a M6.6 near Vanuatu) from zooming the
        // viewport out to the whole globe — fit around the user's neighbourhood.
        if (maxFitDistanceKm != null && targets.size > 1) {
            val anchor = if (fitPoints != null) targets.first() else userGeo ?: targets.first()
            targets = targets.filter { p ->
                val dLatKm = (p.latitude - anchor.latitude) * 111.0
                val dLonKm = (p.longitude - anchor.longitude) * 111.0 * cos(Math.toRadians(anchor.latitude))
                dLatKm * dLatKm + dLonKm * dLonKm <= maxFitDistanceKm * maxFitDistanceKm
            }.ifEmpty { listOf(anchor) }
        }
        val points = targets

        val fit = {
            if (points.size == 1) {
                mapView.controller.setZoom(if (mode == EarthquakeMapMode.ALERT) 8.0 else 6.0)
                mapView.controller.setCenter(points.first())
            } else {
                val box = BoundingBox.fromGeoPoints(points)
                mapView.zoomToBoundingBox(box, false, (fitPaddingDp * density).toInt())
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
    val userColor = UserDotBlue.toArgb()
    val pWaveColor = Color(0xFF2196F3).toArgb()
    val sWaveColor = Color(0xFFF44336).toArgb()
    val selectionRing = if (dark || style == EewMapStyle.AMAP_SAT) 0xFFFFFFFF.toInt() else 0xFF111111.toInt()
    val labelsDark = dark || style == EewMapStyle.AMAP_SAT

    val contourOverlay = remember(contours, labelsDark, density) {
        contours?.let { ContourOverlay(it, { level -> csisColor(level).toArgb() }, ::romanIntensity, density, labelsDark) }
    }
    val outlineOverlay = remember(outline, labelsDark, density) {
        outline?.let { OutlineOverlay(it, ChinaCities.capitals, density, labelsDark) }
    }
    val drawables = remember { HashMap<String, Drawable>() }

    Box(modifier = modifier) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = { mv ->
                mv.overlays.clear()
                labelOverlay?.let { mv.overlays.add(it) }
                if (offline) outlineOverlay?.let { mv.overlays.add(it) }
                contourOverlay?.let { mv.overlays.add(it) }

                if (mode == EarthquakeMapMode.ALERT && epicenter != null) {
                    // Expected strong-shaking zone (broadcast-style yellow highlight).
                    warnZoneRadiusKm?.takeIf { it > 1.0 }?.let { r ->
                        mv.overlays.add(warningZonePolygon(mv, epicenter.toGeoPoint(), r, density))
                    }
                    sWaveRadiusKm?.takeIf { it > 1.0 }?.let { r ->
                        mv.overlays.add(wavePolygon(mv, epicenter.toGeoPoint(), r, sWaveColor, density))
                    }
                    pWaveRadiusKm?.takeIf { it > 1.0 }?.let { r ->
                        mv.overlays.add(wavePolygon(mv, epicenter.toGeoPoint(), r, pWaveColor, density))
                    }
                }
                if (showDistanceLine && userPoint != null && epicenter != null) {
                    val lineColor = if (labelsDark) 0xD9FFFFFF.toInt() else 0xBF141414.toInt()
                    mv.overlays.add(distanceLine(mv, userPoint.toGeoPoint(), epicenter.toGeoPoint(), lineColor, density))
                    if (distanceLabel != null) {
                        mv.overlays.add(DistanceLabelOverlay(epicenter.toGeoPoint(), userPoint.toGeoPoint(), distanceLabel, density, labelsDark))
                    }
                }

                if (mode == EarthquakeMapMode.BROWSE) {
                    // Oldest first so the newest dots end up on top.
                    quakes.asReversed().forEach { q ->
                        val selected = q.id == selectedQuakeId
                        val key = "q:${q.colorArgb}:${q.radiusDp}:${q.halo}:${if (selected) selectionRing else 0}"
                        val drawable = drawables.getOrPut(key) {
                            quakeDotDrawable(context, q.colorArgb, q.radiusDp, q.halo, if (selected) selectionRing else null)
                        }
                        val marker = dotMarker(mv, GeoPoint(q.latitude, q.longitude), drawable)
                        if (onQuakeClick != null) {
                            marker.setOnMarkerClickListener { _, _ ->
                                onQuakeClick.invoke(q)
                                true
                            }
                        }
                        mv.overlays.add(marker)
                    }
                }

                if (markEpicenter && epicenter != null) {
                    val drawable = drawables.getOrPut("epi") { crossDrawable(context, epicenterColor) }
                    mv.overlays.add(dotMarker(mv, epicenter.toGeoPoint(), drawable))
                }

                userPoint?.let {
                    val drawable = drawables.getOrPut("user") { dotDrawable(context, userColor, 0xFFFFFFFF.toInt(), 6f, 0x472196F3) }
                    mv.overlays.add(dotMarker(mv, it.toGeoPoint(), drawable))
                }

                mv.invalidate()
            },
        )

        if (!touchEnabled) {
            // Map cards inside scrolling content: the map never steals drags; a tap opens it.
            Box(
                Modifier
                    .matchParentSize()
                    .then(
                        if (onMapClick != null) {
                            Modifier.clickable(role = Role.Button, onClick = onMapClick)
                        } else {
                            Modifier.pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                                }
                            }
                        },
                    ),
            )
        }

        if (offline && showOfflineChip) {
            MapChip(
                text = stringResource(R.string.ui_map_offline),
                icon = Icons.Outlined.WifiOff,
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
            )
        }

        if (showAttribution) {
            Text(
                text = stringResource(
                    when {
                        offline -> R.string.ui_map_attr_offline
                        style == EewMapStyle.OSM -> R.string.ui_map_attr_osm
                        else -> R.string.ui_map_attr_amap
                    },
                ),
                color = Color(0xFF333333),
                fontSize = 9.sp,
                lineHeight = 10.sp,
                modifier = Modifier
                    .align(attributionAlignment)
                    .padding(horizontal = 8.dp, vertical = 5.dp)
                    .background(Color.White.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}

/** Dot radius for a magnitude, as the design sizes history points. */
fun quakeDotRadiusDp(magnitude: Double): Float = (2.6 + (magnitude - 2.5) * 1.5).coerceIn(2.6, 9.0).toFloat()

private const val OFFLINE_GRACE_MS = 8_000L
private val SatelliteBackground = Color(0xFF0D1B2A)

private fun hasNetwork(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
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
    fillPaint.color = (strokeArgb and 0x00FFFFFF) or 0x2B000000
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
    fillPaint.color = 0x40FFE100
    outlinePaint.color = 0xFFFFE100.toInt()
    outlinePaint.strokeWidth = 3f * density
    isGeodesic = true
}

private fun distanceLine(
    mapView: MapView,
    from: GeoPoint,
    to: GeoPoint,
    colorArgb: Int,
    density: Float,
): Polyline = Polyline(mapView).apply {
    setPoints(listOf(from, to))
    outlinePaint.color = colorArgb
    outlinePaint.strokeWidth = 1.6f * density
    outlinePaint.pathEffect = DashPathEffect(floatArrayOf(5f * density, 4f * density), 0f)
}

private fun dotMarker(
    mapView: MapView,
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
 * History dot (design layer "hist"): CSIS fill with a thin white edge, an optional halo for
 * the newest event and an outer ring for the selected one.
 */
private fun quakeDotDrawable(
    context: Context,
    fillArgb: Int,
    radiusDp: Float,
    halo: Boolean,
    selectionRingArgb: Int?,
): Drawable {
    val density = context.resources.displayMetrics.density
    val r = radiusDp * density
    val edge = 1.3f * density
    val ringGap = 5f * density
    val ringW = 2f * density
    val outer = maxOf(
        r + edge,
        if (halo) r * 1.8f else 0f,
        if (selectionRingArgb != null) r + ringGap + ringW else 0f,
    )
    val size = (outer * 2f + 2f * density).toInt().coerceAtLeast(8)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val c = size / 2f
    if (halo) {
        canvas.drawCircle(c, c, r * 1.8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = (fillArgb and 0x00FFFFFF) or (0x80 shl 24) })
    }
    canvas.drawCircle(c, c, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fillArgb })
    canvas.drawCircle(
        c, c, r,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = edge
            color = 0xFFFFFFFF.toInt()
        },
    )
    if (selectionRingArgb != null) {
        canvas.drawCircle(
            c, c, r + ringGap,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = ringW
                color = selectionRingArgb
            },
        )
    }
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
