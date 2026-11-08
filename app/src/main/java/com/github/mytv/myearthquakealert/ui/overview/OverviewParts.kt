@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package com.github.mytv.myearthquakealert.ui.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.outlined.CrisisAlert
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.mytv.myearthquakealert.MyEarthQuakeAlertApp
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.data.model.EewEvent
import com.github.mytv.myearthquakealert.data.source.EewSource
import com.github.mytv.myearthquakealert.diagnostics.HealthMetrics
import com.github.mytv.myearthquakealert.ui.components.InfoNote
import com.github.mytv.myearthquakealert.ui.components.MapChip
import com.github.mytv.myearthquakealert.ui.components.QuakeRow
import com.github.mytv.myearthquakealert.ui.components.SectionHeader
import com.github.mytv.myearthquakealert.ui.components.SegmentedGroup
import com.github.mytv.myearthquakealert.ui.components.formatMagnitude
import com.github.mytv.myearthquakealert.ui.components.toMarkers
import com.github.mytv.myearthquakealert.ui.components.tvFocusHighlight
import com.github.mytv.myearthquakealert.ui.map.EarthquakeMap
import com.github.mytv.myearthquakealert.ui.map.EarthquakeMapMode
import com.github.mytv.myearthquakealert.ui.map.EewMapStyle
import com.github.mytv.myearthquakealert.ui.map.FullScreenMapDialog
import com.github.mytv.myearthquakealert.ui.map.MapToolbar
import com.github.mytv.myearthquakealert.ui.map.rememberEarthquakeMapController
import com.github.mytv.myearthquakealert.ui.quakes.QuakeCatalog
import com.github.mytv.myearthquakealert.ui.quakes.QuakesViewModel
import com.github.mytv.myearthquakealert.ui.quakes.UserPoint
import kotlinx.coroutines.launch
import java.util.Locale

/** "30.64°N 104.04°E". */
fun coordinateText(latitude: Double, longitude: Double): String = String.format(
    Locale.US,
    "%.2f°%s %.2f°%s",
    kotlin.math.abs(latitude), if (latitude >= 0) "N" else "S",
    kotlin.math.abs(longitude), if (longitude >= 0) "E" else "W",
)

/** "成都市武侯区 · GPS ±35 m" — where distances are measured from. */
@Composable
fun locationText(user: UserPoint?, resolved: Boolean, metrics: HealthMetrics): String {
    if (user == null) return stringResource(if (resolved) R.string.ui_location_none else R.string.ui_location_pending)
    val place = user.name.ifBlank { coordinateText(user.latitude, user.longitude) }
    return when (user.source) {
        UserPoint.Source.MANUAL -> stringResource(R.string.ui_location_manual, place)
        UserPoint.Source.GPS -> {
            val accuracy = metrics.locationAccuracyM?.takeIf { metrics.locationSource == "gps" }
            if (accuracy != null) stringResource(R.string.ui_location_gps_accuracy, place, accuracy.toInt())
            else stringResource(R.string.ui_location_gps, place)
        }
        UserPoint.Source.NETWORK -> stringResource(R.string.ui_location_network, place)
        UserPoint.Source.IP -> stringResource(R.string.ui_location_ip, place)
    }
}

@Composable
fun LocationSubtitle(text: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Icon(Icons.Filled.NearMe, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 概览 overflow menu: 切换布局 (this session) and 关于与更新. */
@Composable
fun OverviewMenu(onChooseLayout: () -> Unit, onAbout: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.tvFocusHighlight()) {
            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.ui_more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.ui_switch_layout)) },
                leadingIcon = { Icon(Icons.Outlined.Dashboard, contentDescription = null) },
                onClick = {
                    open = false
                    onChooseLayout()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.ui_about_update)) },
                leadingIcon = { Icon(Icons.Outlined.SystemUpdate, contentDescription = null) },
                onClick = {
                    open = false
                    onAbout()
                },
            )
        }
    }
}

/** LargeFlexibleTopAppBar: 地震速报 + location subtitle, search and the overflow menu. */
@Composable
fun OverviewTopBar(
    location: String,
    scrollBehavior: TopAppBarScrollBehavior?,
    onSearch: (() -> Unit)?,
    onChooseLayout: () -> Unit,
    onAbout: () -> Unit,
) {
    LargeFlexibleTopAppBar(
        title = { Text(stringResource(R.string.app_name), maxLines = 1) },
        subtitle = { LocationSubtitle(location) },
        actions = {
            if (onSearch != null) {
                IconButton(onClick = onSearch, modifier = Modifier.tvFocusHighlight()) {
                    Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.ui_search))
                }
            }
            OverviewMenu(onChooseLayout, onAbout)
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        scrollBehavior = scrollBehavior,
    )
}

/** Compact header for panes without a top app bar (tablet side pane). */
@Composable
fun PaneHeader(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    Row(modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) LocationSubtitle(subtitle, Modifier.padding(top = 2.dp))
        }
        actions()
    }
}

/** "近 10 天 · 50 次" for the map chip. */
@Composable
fun catalogueChipText(state: QuakesViewModel.UiState, now: Long, withSource: Boolean = false): String? {
    if (!state.hasData) return null
    val days = QuakeCatalog.spanDays(state.all, now)
    return if (withSource) stringResource(R.string.ui_map_chip_source, days, state.all.size)
    else stringResource(R.string.ui_map_chip, days, state.all.size)
}

/**
 * Overview map card (design .mapcard): catalogue dots + user, a chip with the catalogue span
 * and the floating toolbar. Taps open the interactive full-screen map; with [interactive]
 * the card itself can be panned (tablet main area).
 */
@Composable
fun OverviewMapCard(
    quakes: QuakesViewModel.UiState,
    style: EewMapStyle,
    now: Long,
    onOpenQuake: (String) -> Unit,
    modifier: Modifier = Modifier,
    interactive: Boolean = false,
    corner: Dp = 28.dp,
    toolbarAlignment: Alignment = Alignment.BottomEnd,
    chipWithSource: Boolean = false,
) {
    val context = LocalContext.current
    val app = context.applicationContext as? MyEarthQuakeAlertApp
    val scope = rememberCoroutineScope()
    val controller = rememberEarthquakeMapController()
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    val markers = remember(quakes.all) { quakes.all.toMarkers() }
    val chip = catalogueChipText(quakes, now, chipWithSource)
    val setStyle: (EewMapStyle) -> Unit = { s -> scope.launch { app?.settingsRepository?.updateMapStyle(s) } }
    val user = quakes.user?.point

    Box(modifier.clip(RoundedCornerShape(corner))) {
        EarthquakeMap(
            style = style,
            mode = EarthquakeMapMode.BROWSE,
            modifier = Modifier.fillMaxSize(),
            userPoint = user,
            interactive = interactive,
            quakes = markers,
            onQuakeClick = { onOpenQuake(it.id) },
            maxFitDistanceKm = 2_200.0,
            fitPaddingDp = 40,
            controller = controller,
            touchEnabled = interactive,
            onMapClick = { fullscreen = true },
        )
        if (chip != null) {
            MapChip(chip, icon = Icons.Outlined.History, modifier = Modifier.align(Alignment.TopStart).padding(12.dp))
        }
        MapToolbar(
            style = style,
            onStyleChange = setStyle,
            onLocate = user?.let { p -> { controller.animateTo(p, 7.0) } },
            controller = controller,
            showZoom = interactive,
            onFullscreen = if (interactive) null else ({ fullscreen = true }),
            // Lifted above the attribution, which stays in the bottom-right corner.
            modifier = Modifier
                .align(toolbarAlignment)
                .padding(start = 10.dp, end = 10.dp, bottom = 24.dp),
        )
    }
    if (fullscreen) {
        FullScreenMapDialog(
            style = style,
            onStyleChange = setStyle,
            quakes = markers,
            userPoint = user,
            chipText = chip,
            onQuakeClick = {
                fullscreen = false
                onOpenQuake(it.id)
            },
            onDismiss = { fullscreen = false },
        )
    }
}

/** 最近地震 · 全部 with the newest [count] events, or the loading / error / empty state. */
@Composable
fun RecentQuakesSection(
    quakes: QuakesViewModel.UiState,
    now: Long,
    count: Int,
    onOpenQuake: (String) -> Unit,
    onShowAll: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.earthquake_history),
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(title, link = stringResource(R.string.ui_show_all), onLink = onShowAll)
        val recent = quakes.all.take(count)
        when {
            recent.isNotEmpty() -> SegmentedGroup(recent.size) { i, shape ->
                val q = recent[i]
                QuakeRow(q, shape, now, onClick = { onOpenQuake(q.key) })
            }
            quakes.loading -> Box(Modifier.fillMaxWidth().heightIn(min = 96.dp), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
            quakes.error -> InfoNote(
                stringResource(R.string.history_load_failed),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                action = { TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) } },
            )
            else -> InfoNote(stringResource(R.string.no_earthquakes), containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        }
    }
}

/** 最新报文: the newest early-warning report any source delivered. */
@Composable
fun LatestReportCard(report: EewEvent?, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(
            Modifier.heightIn(min = 60.dp).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (report != null) Icons.Outlined.CrisisAlert else Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.ui_latest_report),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (report == null) {
                    Text(stringResource(R.string.ui_latest_report_none), style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(
                        stringResource(R.string.ui_latest_report_title, report.hypocenter, formatMagnitude(report.magnitude)),
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(
                            if (report.isFinal) R.string.ui_latest_report_sub_final else R.string.ui_latest_report_sub,
                            EewSource.labelOf(report.source),
                            report.reportNum,
                            report.reportTime.let { if (it.length >= 16) it.substring(5, 16) else it },
                        ),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
