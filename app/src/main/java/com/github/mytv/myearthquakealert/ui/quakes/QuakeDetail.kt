@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.github.mytv.myearthquakealert.ui.quakes

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.domain.SeismicCalculator
import com.github.mytv.myearthquakealert.ui.components.IntensityBadge
import com.github.mytv.myearthquakealert.ui.components.MapChip
import com.github.mytv.myearthquakealert.ui.components.Subhead
import com.github.mytv.myearthquakealert.ui.components.formatDepth
import com.github.mytv.myearthquakealert.ui.components.formatKm
import com.github.mytv.myearthquakealert.ui.components.formatMagnitude
import com.github.mytv.myearthquakealert.ui.components.toMarkers
import com.github.mytv.myearthquakealert.ui.components.tvFocusHighlight
import com.github.mytv.myearthquakealert.ui.map.EarthquakeMap
import com.github.mytv.myearthquakealert.ui.map.EarthquakeMapMode
import com.github.mytv.myearthquakealert.ui.map.EewMapStyle
import com.github.mytv.myearthquakealert.ui.map.IntensityContours
import com.github.mytv.myearthquakealert.ui.map.MapPoint
import com.github.mytv.myearthquakealert.ui.theme.EeqNumberStyles
import com.github.mytv.myearthquakealert.ui.theme.romanIntensity
import kotlin.math.roundToInt

/** Contours of [quake] (3–7 度, design §7). */
fun QuakeUi.contours(): IntensityContours = IntensityContours(point, magnitude, depthKm)

/** Within this distance the detail map also frames the user and draws the distance line. */
private const val FRAME_USER_KM = 800.0

/**
 * Viewport for a detail map: the outer contour around the epicentre (capped so a great
 * quake does not zoom out to a continent).
 */
fun detailFitPoints(quake: QuakeUi, contours: IntensityContours): List<MapPoint> {
    val outer = (contours.radii.firstOrNull()?.second ?: 40.0).coerceIn(30.0, 450.0)
    return listOf(0.0, 90.0, 180.0, 270.0).map { b ->
        val p = SeismicCalculator.offsetPoint(quake.latitude, quake.longitude, b, outer)
        MapPoint(p.latitude, p.longitude)
    }
}

/** The detail map: contours, catalogue dots, epicentre, user and the distance between them. */
@Composable
fun QuakeDetailMap(
    quake: QuakeUi,
    all: List<QuakeUi>,
    user: UserPoint?,
    style: EewMapStyle,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    onQuakeClick: ((String) -> Unit)? = null,
) {
    val contours = remember(quake.key) { quake.contours() }
    val fit = remember(quake.key, user) {
        val base = detailFitPoints(quake, contours)
        if (user != null && (quake.distanceKm ?: Double.MAX_VALUE) < FRAME_USER_KM) base + user.point else base
    }
    val markers = remember(all) { all.toMarkers() }
    Box(modifier) {
        EarthquakeMap(
            style = style,
            mode = EarthquakeMapMode.BROWSE,
            modifier = Modifier.fillMaxSize(),
            userPoint = user?.point,
            interactive = interactive,
            epicenter = quake.point,
            showDistanceLine = user != null && (quake.distanceKm ?: Double.MAX_VALUE) < FRAME_USER_KM,
            quakes = markers,
            selectedQuakeId = quake.key,
            onQuakeClick = onQuakeClick?.let { click -> { m -> click(m.id) } },
            fitPaddingDp = 24,
            contours = contours,
            markEpicenter = true,
            distanceLabel = quake.distanceKm?.let { stringResource(R.string.ui_km, formatKm(it)) },
            fitPoints = fit,
            touchEnabled = interactive,
        )
        MapChip(stringResource(R.string.ui_contours_chip), modifier = Modifier.align(Alignment.TopStart).padding(10.dp))
    }
}

/**
 * 地震详情 (design scrBook right pane): parameters, the estimated intensity where you are,
 * wave arrival, contour radii and the map.
 */
@Composable
fun QuakeDetailPane(
    quake: QuakeUi,
    all: List<QuakeUi>,
    user: UserPoint?,
    style: EewMapStyle,
    showBack: Boolean,
    onBack: () -> Unit,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    mapHeight: Dp = 300.dp,
    showMap: Boolean = true,
) {
    val context = LocalContext.current
    val shareText = shareText(quake)
    val shareTitle = stringResource(R.string.ui_share)
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(quake.location, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                subtitle = {
                    Text(
                        stringResource(R.string.ui_detail_sub, quake.time, stringResource(if (quake.reviewed) R.string.ui_reviewed else R.string.ui_automatic)),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = onBack, modifier = Modifier.tvFocusHighlight()) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.ui_back))
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText)
                            context.startActivity(Intent.createChooser(send, shareTitle))
                        },
                        modifier = Modifier.tvFocusHighlight(),
                    ) {
                        Icon(Icons.Outlined.IosShare, contentDescription = shareTitle)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(start = 14.dp, end = 14.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (showMap) {
                QuakeDetailMap(
                    quake = quake,
                    all = all,
                    user = user,
                    style = style,
                    onQuakeClick = onSelect,
                    modifier = Modifier.fillMaxWidth().height(mapHeight).clip(RoundedCornerShape(28.dp)),
                )
            }
            QuakeParameters(quake)
            LocalEstimate(quake)
            ContourList(quake)
        }
    }
}

@Composable
private fun shareText(quake: QuakeUi): String {
    val base = stringResource(R.string.ui_share_text, quake.location, formatMagnitude(quake.magnitude), formatDepth(quake.depthKm), quake.time)
    return quake.distanceKm?.let { base + stringResource(R.string.ui_share_distance, formatKm(it)) } ?: base
}

/** Parameter tiles (design .mgrid / .mtile). */
@Composable
fun QuakeParameters(quake: QuakeUi, modifier: Modifier = Modifier) {
    val local = quake.localIntensity
    val tiles = buildList {
        add(stringResource(R.string.ui_param_magnitude) to ("M" + formatMagnitude(quake.magnitude) to ""))
        add(stringResource(R.string.ui_param_depth) to (formatDepth(quake.depthKm) to "km"))
        add(stringResource(R.string.ui_param_epicentral) to (romanIntensity(quake.intensity) to stringResource(R.string.ui_degree)))
        quake.distanceKm?.let { add(stringResource(R.string.ui_param_distance) to (formatKm(it) to "km")) }
        if (local != null) {
            add(
                stringResource(R.string.ui_param_local) to
                    if (local < 1.5) stringResource(R.string.ui_not_felt) to "" else romanIntensity(local.roundToInt()) to stringResource(R.string.ui_degree),
            )
        }
        add(stringResource(R.string.ui_param_source) to ("CENC" to ""))
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 420.dp) 3 else 2
        val gap = 8.dp
        val tileWidth = (maxWidth - gap * (columns - 1)) / columns
        FlowRow(horizontalArrangement = Arrangement.spacedBy(gap), verticalArrangement = Arrangement.spacedBy(gap), maxItemsInEachRow = columns) {
            tiles.forEach { (label, value) -> MetricTile(label, value.first, value.second, Modifier.width(tileWidth)) }
        }
    }
}

@Composable
private fun MetricTile(label: String, value: String, unit: String, modifier: Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(
                buildAnnotatedString {
                    append(value)
                    if (unit.isNotEmpty()) {
                        withStyle(SpanStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                            append(" $unit")
                        }
                    }
                },
                style = EeqNumberStyles.value.copy(fontSize = 20.sp, lineHeight = 24.sp),
                maxLines = 1,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** 你处估计: the estimated local intensity in words, and when the waves would arrive. */
@Composable
private fun LocalEstimate(quake: QuakeUi) {
    val local = quake.localIntensity ?: return
    val distance = quake.distanceKm ?: return
    val descriptions = stringArrayResource(R.array.ui_intensity_desc)
    val level = local.roundToInt().coerceIn(0, 12)
    val arrival = remember(quake.key, distance) { SeismicCalculator.calcWaveArrival(quake.depthKm, distance) }
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.heightIn(min = 64.dp).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            IntensityBadge(level)
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.ui_local_estimate, descriptions.getOrElse(level) { "" }),
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium),
                )
                Text(
                    stringResource(R.string.ui_wave_arrival, arrival.pWaveSeconds.roundToInt(), arrival.sWaveSeconds.roundToInt()),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 估计烈度范围: radius of every contour level. */
@Composable
private fun ContourList(quake: QuakeUi) {
    val radii = remember(quake.key) { quake.contours().radii }
    if (radii.isEmpty()) return
    Subhead(stringResource(R.string.ui_contour_title), modifier = Modifier.padding(top = 0.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        radii.asReversed().forEach { (level, r) ->
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Row(
                    Modifier.padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IntensityBadge(level, size = 28.dp, textStyle = EeqNumberStyles.badge.copy(fontSize = 14.sp))
                    Text(stringResource(R.string.ui_contour_radius, formatKm(r)), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/** Shown in the detail pane of a two-pane layout before anything is selected. */
@Composable
fun QuakeDetailPlaceholder(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            stringResource(R.string.ui_detail_placeholder),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
