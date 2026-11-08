package com.github.mytv.myearthquakealert.ui.tv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.service.MonitorPhase
import com.github.mytv.myearthquakealert.ui.components.IntensityBadge
import com.github.mytv.myearthquakealert.ui.components.MapChip
import com.github.mytv.myearthquakealert.ui.components.StatusShape
import com.github.mytv.myearthquakealert.ui.components.agoText
import com.github.mytv.myearthquakealert.ui.components.formatClock
import com.github.mytv.myearthquakealert.ui.components.formatDepth
import com.github.mytv.myearthquakealert.ui.components.formatMagnitude
import com.github.mytv.myearthquakealert.ui.components.monitorTexts
import com.github.mytv.myearthquakealert.ui.components.rememberNow
import com.github.mytv.myearthquakealert.ui.components.toMarkers
import com.github.mytv.myearthquakealert.ui.main.LocalShellNavigator
import com.github.mytv.myearthquakealert.ui.map.EarthquakeMap
import com.github.mytv.myearthquakealert.ui.map.EarthquakeMapMode
import com.github.mytv.myearthquakealert.ui.map.EewMapStyle
import com.github.mytv.myearthquakealert.ui.navigation.Destination
import com.github.mytv.myearthquakealert.ui.overview.MonitorActions
import com.github.mytv.myearthquakealert.ui.overview.catalogueChipText
import com.github.mytv.myearthquakealert.ui.overview.collectOverviewModel
import com.github.mytv.myearthquakealert.ui.overview.locationText
import com.github.mytv.myearthquakealert.ui.quakes.QuakeUi
import com.github.mytv.myearthquakealert.ui.quakes.QuakesViewModel
import com.github.mytv.myearthquakealert.ui.theme.EeqNumberStyles

/** Satellite imagery reads better from across the room (design: 电视 · 概览卫星底图). */
fun EewMapStyle.forTenFoot(): EewMapStyle = if (this == EewMapStyle.AMAP) EewMapStyle.AMAP_SAT else this

/**
 * 电视 · 概览 (design scrTvHome): brand, status and location pills and the clock; the map
 * (1.7 fr) beside the status tile and the four newest quakes. Every tile is D-pad focusable;
 * the focused card is enlarged and inverted.
 */
@Composable
fun TvHome(vm: QuakesViewModel, actions: MonitorActions, modifier: Modifier = Modifier) {
    val model = collectOverviewModel(vm)
    val nav = LocalShellNavigator.current
    val openPicker = LocalOpenTvLocationPicker.current
    val clock = rememberNow(1_000L)
    val texts = monitorTexts(model.phase, model.monitor, model.settings, model.now)
    val on = model.phase == MonitorPhase.MONITORING
    val recent = model.quakes.all.take(4)
    val markers = remember(model.quakes.all) { model.quakes.all.toMarkers() }

    val statusFocus = remember { FocusRequester() }
    val firstCardFocus = remember { FocusRequester() }
    var initialFocusDone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (initialFocusDone) return@LaunchedEffect
        // Focus starts on the newest quake (design), or on the status tile while loading.
        val target = if (recent.isNotEmpty()) firstCardFocus else statusFocus
        initialFocusDone = runCatching { target.requestFocus() }.isSuccess
    }

    Column(
        modifier
            .fillMaxSize()
            .padding(start = 14.dp, top = 26.dp, end = 40.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(painterResource(R.drawable.ic_ui_earthquake), contentDescription = null, tint = TvPalette.OnCard, modifier = Modifier.size(26.dp))
                Text(stringResource(R.string.app_name), color = TvPalette.OnCard, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            TvPill(
                icon = { Icon(if (model.settings.serviceEnabled) Icons.Filled.Sensors else Icons.Filled.PowerSettingsNew, null, Modifier.size(18.dp)) },
                text = texts.short,
                color = if (on) TvPalette.PillOn else TvPalette.Pill,
            )
            TvPill(
                icon = { Icon(Icons.Filled.LocationOn, null, Modifier.size(18.dp)) },
                text = locationText(model.quakes.user, model.quakes.locationResolved, model.metrics),
                color = TvPalette.Pill,
                onClick = openPicker,
            )
            Text(formatClock(clock), color = TvPalette.OnCard, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, style = EeqNumberStyles.tabular)
        }

        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.weight(1.7f).fillMaxHeight().clip(RoundedCornerShape(24.dp))) {
                EarthquakeMap(
                    style = model.settings.mapStyle.forTenFoot(),
                    mode = EarthquakeMapMode.BROWSE,
                    modifier = Modifier.fillMaxSize(),
                    userPoint = model.quakes.user?.point,
                    interactive = false,
                    quakes = markers,
                    maxFitDistanceKm = 2_200.0,
                    fitPaddingDp = 40,
                    touchEnabled = false,
                )
                catalogueChipText(model.quakes, model.now)?.let {
                    MapChip(
                        it,
                        icon = Icons.Outlined.History,
                        containerColor = TvPalette.Overlay,
                        contentColor = TvPalette.OnCard,
                        modifier = Modifier.align(Alignment.TopStart).padding(14.dp),
                    )
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TvMonitorTile(
                    phase = model.phase,
                    title = texts.title,
                    subtitle = when (model.phase) {
                        MonitorPhase.OFF -> stringResource(R.string.ui_tv_press_ok_on)
                        MonitorPhase.PAUSED -> stringResource(R.string.ui_tv_press_ok_resume)
                        MonitorPhase.MONITORING -> model.metrics.availability24h?.let {
                            stringResource(R.string.ui_tv_on_sub, model.monitor.sources.size.coerceAtLeast(1), String.format(java.util.Locale.US, "%.1f", it * 100))
                        } ?: texts.subtitle
                        else -> texts.subtitle
                    },
                    onClick = {
                        when (model.phase) {
                            MonitorPhase.OFF -> actions.turnOn()
                            MonitorPhase.PAUSED -> actions.resume()
                            else -> nav.open(Destination.DIAGNOSTICS)
                        }
                    },
                    modifier = Modifier.focusRequester(statusFocus),
                )
                when {
                    recent.isNotEmpty() -> recent.forEachIndexed { i, q ->
                        TvQuakeCard(
                            quake = q,
                            now = model.now,
                            onClick = { nav.openQuake(q.key) },
                            modifier = if (i == 0) Modifier.focusRequester(firstCardFocus) else Modifier,
                        )
                    }
                    else -> TvMessageCard(
                        text = stringResource(
                            when {
                                model.quakes.error -> R.string.history_load_failed
                                model.quakes.loading || !model.quakes.hasData -> R.string.ui_tv_loading
                                else -> R.string.no_earthquakes
                            },
                        ),
                        action = if (model.quakes.error) stringResource(R.string.retry) else null,
                        onClick = vm::refresh,
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            listOf(R.string.ui_tv_hint_dpad, R.string.ui_tv_hint_ok, R.string.ui_tv_hint_menu).forEach {
                Text(stringResource(it), color = TvPalette.Hint, fontSize = 12.sp)
            }
        }
    }
}

/** A tv-material clickable surface whose m3 content follows its focus state. */
@Composable
fun TvTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    container: Color = TvPalette.Tile,
    content: Color = TvPalette.OnCard,
    focusedContainer: Color = TvPalette.Focused,
    focusedContent: Color = TvPalette.OnFocused,
    focusedScale: Float = 1.04f,
    body: @Composable (focused: Boolean) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Surface(
        onClick = onClick,
        modifier = modifier,
        interactionSource = interaction,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = container,
            contentColor = content,
            focusedContainerColor = focusedContainer,
            focusedContentColor = focusedContent,
            pressedContainerColor = focusedContainer,
            pressedContentColor = focusedContent,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = focusedScale),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, focusedContainer.copy(alpha = 0.6f)), inset = 0.dp, shape = shape),
        ),
    ) {
        CompositionLocalProvider(LocalContentColor provides if (focused) focusedContent else content) { body(focused) }
    }
}

@Composable
private fun TvPill(icon: @Composable () -> Unit, text: String, color: Color, onClick: (() -> Unit)? = null) {
    val row: @Composable (Color) -> Unit = { tint ->
        Row(
            Modifier.heightIn(min = 32.dp).padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CompositionLocalProvider(LocalContentColor provides tint) {
                icon()
                Text(text, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = tint)
            }
        }
    }
    if (onClick == null) {
        androidx.compose.material3.Surface(color = TvPalette.Tile, shape = RoundedCornerShape(16.dp)) { row(color) }
    } else {
        TvTile(onClick = onClick, shape = RoundedCornerShape(16.dp), content = color, focusedScale = 1.06f) { focused ->
            row(if (focused) TvPalette.OnFocused else color)
        }
    }
}

@Composable
private fun TvMonitorTile(
    phase: MonitorPhase,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val on = phase == MonitorPhase.MONITORING
    TvTile(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        container = if (on) TvPalette.MonitorOn else TvPalette.MonitorOff,
        content = if (on) TvPalette.OnMonitorOn else TvPalette.OnMonitorOff,
        focusedScale = 1.03f,
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatusShape(phase, size = 46.dp)
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = LocalContentColor.current.copy(alpha = 0.8f))
            }
        }
    }
}

/** One quake (design .tvc): CSIS badge, place, age and depth, magnitude. */
@Composable
fun TvQuakeCard(
    quake: QuakeUi,
    now: Long,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    container: Color = TvPalette.Tile,
) {
    val body: @Composable RowScope.(Boolean) -> Unit = { focused ->
        IntensityBadge(quake.intensity, size = 38.dp, textStyle = EeqNumberStyles.badge.copy(fontSize = 17.sp))
        Column(Modifier.weight(1f)) {
            Text(quake.location, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(R.string.ui_tv_quake_sub, agoText(quake.epochMillis, quake.time, now), formatDepth(quake.depthKm)),
                fontSize = 12.sp,
                maxLines = 1,
                color = if (focused) TvPalette.OnFocusedMuted else TvPalette.Muted,
            )
        }
        Text("M" + formatMagnitude(quake.magnitude), style = EeqNumberStyles.value.copy(fontSize = 21.sp, fontWeight = FontWeight.ExtraBold))
    }
    if (onClick == null) {
        androidx.compose.material3.Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = container, contentColor = TvPalette.OnCard) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                body(false)
            }
        }
    } else {
        TvTile(onClick = onClick, modifier = modifier.fillMaxWidth(), container = container) { focused ->
            Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                body(focused)
            }
        }
    }
}

@Composable
private fun TvMessageCard(text: String, action: String?, onClick: () -> Unit) {
    TvTile(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (action != null) Text(action, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
