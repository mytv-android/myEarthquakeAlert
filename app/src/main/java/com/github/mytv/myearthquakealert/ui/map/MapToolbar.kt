@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.github.mytv.myearthquakealert.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.SatelliteAlt
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.ui.components.MapChip
import com.github.mytv.myearthquakealert.ui.components.tvFocusHighlight

/** Display name of a basemap style. */
@Composable
fun mapStyleLabel(style: EewMapStyle): String = stringResource(
    when (style) {
        EewMapStyle.AMAP -> R.string.ui_map_style_amap
        EewMapStyle.AMAP_SAT -> R.string.ui_map_style_sat
        EewMapStyle.OSM -> R.string.map_style_osm
    },
)

/**
 * Floating map toolbar (design .ftb): 图层 (basemap menu), 卫星 toggle, 定位, optional zoom
 * buttons and full screen.
 */
@Composable
fun MapToolbar(
    style: EewMapStyle,
    onStyleChange: (EewMapStyle) -> Unit,
    modifier: Modifier = Modifier,
    onLocate: (() -> Unit)? = null,
    controller: EarthquakeMapController? = null,
    showZoom: Boolean = false,
    onFullscreen: (() -> Unit)? = null,
) {
    var menu by remember { mutableStateOf(false) }
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier,
        colors = FloatingToolbarDefaults.standardFloatingToolbarColors(),
        contentPadding = PaddingValues(6.dp),
    ) {
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.tvFocusHighlight()) {
                Icon(Icons.Outlined.Layers, contentDescription = stringResource(R.string.map_tile_style))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                EewMapStyle.entries.forEach { s ->
                    DropdownMenuItem(
                        text = { Text(mapStyleLabel(s)) },
                        trailingIcon = if (s == style) {
                            { Icon(Icons.Outlined.Check, contentDescription = null) }
                        } else {
                            null
                        },
                        onClick = {
                            menu = false
                            onStyleChange(s)
                        },
                    )
                }
            }
        }
        IconToggleButton(
            checked = style == EewMapStyle.AMAP_SAT,
            onCheckedChange = { onStyleChange(if (it) EewMapStyle.AMAP_SAT else EewMapStyle.AMAP) },
            colors = IconButtonDefaults.iconToggleButtonColors(
                checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
            modifier = Modifier.tvFocusHighlight(),
        ) {
            Icon(Icons.Outlined.SatelliteAlt, contentDescription = stringResource(R.string.ui_map_style_sat))
        }
        if (onLocate != null) {
            IconButton(onClick = onLocate, modifier = Modifier.tvFocusHighlight()) {
                Icon(Icons.Outlined.MyLocation, contentDescription = stringResource(R.string.ui_map_locate))
            }
        }
        if (showZoom && controller != null) {
            IconButton(onClick = controller::zoomIn, modifier = Modifier.tvFocusHighlight()) {
                Icon(Icons.Outlined.ZoomIn, contentDescription = stringResource(R.string.ui_map_zoom_in))
            }
            IconButton(onClick = controller::zoomOut, modifier = Modifier.tvFocusHighlight()) {
                Icon(Icons.Outlined.ZoomOut, contentDescription = stringResource(R.string.ui_map_zoom_out))
            }
        }
        if (onFullscreen != null) {
            IconButton(onClick = onFullscreen, modifier = Modifier.tvFocusHighlight()) {
                Icon(Icons.Outlined.Fullscreen, contentDescription = stringResource(R.string.ui_map_fullscreen))
            }
        }
    }
}

/**
 * Full-screen, interactive version of a map card: pan, zoom, tap a dot to open the quake.
 */
@Composable
fun FullScreenMapDialog(
    style: EewMapStyle,
    onStyleChange: (EewMapStyle) -> Unit,
    quakes: List<QuakeMarker>,
    userPoint: MapPoint?,
    chipText: String?,
    onQuakeClick: (QuakeMarker) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val controller = rememberEarthquakeMapController()
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                EarthquakeMap(
                    style = style,
                    mode = EarthquakeMapMode.BROWSE,
                    modifier = Modifier.fillMaxSize(),
                    userPoint = userPoint,
                    interactive = true,
                    quakes = quakes,
                    onQuakeClick = onQuakeClick,
                    maxFitDistanceKm = 3_000.0,
                    controller = controller,
                )
                Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    FilledTonalIconButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.TopStart).padding(12.dp).tvFocusHighlight(),
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.ui_back))
                    }
                    if (chipText != null) {
                        MapChip(chipText, icon = Icons.Outlined.History, modifier = Modifier.align(Alignment.TopCenter).padding(top = 18.dp))
                    }
                    MapToolbar(
                        style = style,
                        onStyleChange = onStyleChange,
                        onLocate = userPoint?.let { p -> { controller.animateTo(p, 7.0) } },
                        controller = controller,
                        showZoom = true,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp),
                    )
                }
            }
        }
    }
}
