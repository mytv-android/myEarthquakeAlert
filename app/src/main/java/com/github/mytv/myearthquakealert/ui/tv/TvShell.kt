package com.github.mytv.myearthquakealert.ui.tv

import android.content.Context
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.EditLocationAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor as M3ContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.LocalContentColor as TvContentColor
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.NavigationDrawerItemDefaults
import androidx.tv.material3.NavigationDrawerScope
import androidx.tv.material3.Text
import androidx.tv.material3.rememberDrawerState
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.data.model.LocationMode
import com.github.mytv.myearthquakealert.ui.adaptive.LocalResolvedLayout
import com.github.mytv.myearthquakealert.ui.adaptive.handleKeyEvents
import com.github.mytv.myearthquakealert.ui.main.LocalUserSettings
import com.github.mytv.myearthquakealert.ui.navigation.Destination
import com.github.mytv.myearthquakealert.ui.navigation.DestinationIcon
import com.github.mytv.myearthquakealert.ui.navigation.label
import com.github.mytv.myearthquakealert.ui.overview.MonitorActions
import com.github.mytv.myearthquakealert.ui.quakes.QuakesViewModel

/** The 10-foot palette of the design (.tvs, .tvc, .tv-drawer). */
object TvPalette {
    val Background = Color(0xFF0E1217)
    val Tile = Color(0xFF1B2129)
    val OnCard = Color(0xFFE4E7EB)
    val Muted = Color(0xFF9FA8B2)
    val Hint = Color(0xFF8E98A3)
    val Icon = Color(0xFFB9C2CC)
    val Focused = Color(0xFFE4E7EB)
    val OnFocused = Color(0xFF0E1217)
    val OnFocusedMuted = Color(0xFF3A4046)
    val Pill = Color(0xFFC1C7CE)
    val PillOn = Color(0xFF86D8A8)
    val MonitorOn = Color(0xFF123524)
    val OnMonitorOn = Color(0xFFC9EED6)
    val MonitorOff = Color(0xFF262B31)
    val OnMonitorOff = Color(0xFFE1E3E7)
    val Overlay = Color(0xD90E1217)
}

/** Opens the first-run 设置位置 screen again (from the location pill). */
val LocalOpenTvLocationPicker = staticCompositionLocalOf<() -> Unit> { {} }

private const val UI_STATE_PREFS = "ui_state"
private const val KEY_TV_LOCATION_PROMPTED = "tv_location_prompted"

private fun Context.uiState() = getSharedPreferences(UI_STATE_PREFS, Context.MODE_PRIVATE)

/**
 * 电视 shell (design §2, 电视): the tv-material NavigationDrawer with the four destinations,
 * the 菜单 key for 切换布局, and — on a real TV without a manual location yet — the
 * first-run location picker (TVs have no GPS).
 */
@Composable
fun TvShell(
    vm: QuakesViewModel,
    actions: MonitorActions,
    current: Destination,
    onNavigate: (Destination) -> Unit,
    onChooseLayout: () -> Unit,
    content: @Composable (Destination) -> Unit,
) {
    val context = LocalContext.current
    val settings = LocalUserSettings.current
    val layout = LocalResolvedLayout.current
    var prompted by remember(context) { mutableStateOf(context.uiState().getBoolean(KEY_TV_LOCATION_PROMPTED, false)) }
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    val firstRun = layout.isTelevision && settings.locationMode != LocationMode.MANUAL && !prompted
    val markPrompted = {
        context.uiState().edit().putBoolean(KEY_TV_LOCATION_PROMPTED, true).apply()
        prompted = true
        pickerOpen = false
    }

    if (firstRun || pickerOpen) {
        TvLocationPicker(
            vm = vm,
            firstRun = firstRun,
            onDone = markPrompted,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    CompositionLocalProvider(LocalOpenTvLocationPicker provides { pickerOpen = true }) {
        NavigationDrawer(
            drawerState = drawerState,
            modifier = Modifier
                .fillMaxSize()
                .background(TvPalette.Background)
                // HandleUserKey: the remote's 菜单 key opens 切换布局 from anywhere.
                .handleKeyEvents(onKeyTap = mapOf(KeyEvent.KEYCODE_MENU to onChooseLayout)),
            drawerContent = { value ->
                TvDrawer(
                    value = value,
                    current = current,
                    onNavigate = onNavigate,
                    onChooseLayout = onChooseLayout,
                    onPickLocation = { pickerOpen = true },
                )
            },
        ) {
            Box(Modifier.fillMaxSize().background(TvPalette.Background)) {
                if (current == Destination.OVERVIEW) {
                    content(current)
                } else {
                    // Phone-style destinations get the 5% overscan margin of the 10-foot canvas.
                    Box(Modifier.fillMaxSize().padding(start = 8.dp, top = 20.dp, end = 40.dp, bottom = 16.dp)) { content(current) }
                }
            }
        }
    }
}

@Composable
private fun NavigationDrawerScope.TvDrawer(
    value: DrawerValue,
    current: Destination,
    onNavigate: (Destination) -> Unit,
    onChooseLayout: () -> Unit,
    onPickLocation: () -> Unit,
) {
    val open = value == DrawerValue.Open
    Column(
        Modifier
            .fillMaxHeight()
            .background(
                Brush.horizontalGradient(
                    if (open) listOf(Color(0xF20E1217), Color(0xCC0E1217)) else listOf(Color(0x8C000000), Color.Transparent),
                ),
            )
            .padding(start = 16.dp, end = 12.dp, top = 38.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Destination.entries.forEach { d ->
            DrawerItem(
                selected = d == current,
                onClick = { onNavigate(d) },
                icon = { DestinationIcon(d, selected = d == current, modifier = Modifier.size(24.dp)) },
                label = d.label(),
            )
        }
        Spacer(Modifier.weight(1f))
        DrawerItem(
            selected = false,
            onClick = onPickLocation,
            icon = { Icon(Icons.Outlined.EditLocationAlt, contentDescription = null, modifier = Modifier.size(24.dp)) },
            label = stringResource(R.string.ui_tv_set_location),
        )
        DrawerItem(
            selected = false,
            onClick = onChooseLayout,
            icon = { Icon(Icons.Outlined.Dashboard, contentDescription = null, modifier = Modifier.size(24.dp)) },
            label = stringResource(R.string.ui_switch_layout),
        )
        if (!open) Spacer(Modifier.size(1.dp))
    }
}

@Composable
private fun NavigationDrawerScope.DrawerItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: String,
) {
    NavigationDrawerItem(
        selected = selected,
        onClick = onClick,
        // The m3 icons follow the drawer item's (tv) content colour.
        leadingContent = { CompositionLocalProvider(M3ContentColor provides TvContentColor.current) { icon() } },
        shape = NavigationDrawerItemDefaults.shape(shape = CircleShape),
        colors = NavigationDrawerItemDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = TvPalette.Icon,
            inactiveContentColor = TvPalette.Icon,
            focusedContainerColor = TvPalette.Focused,
            focusedContentColor = TvPalette.OnFocused,
            selectedContainerColor = TvPalette.Tile,
            selectedContentColor = TvPalette.OnCard,
            focusedSelectedContainerColor = TvPalette.Focused,
            focusedSelectedContentColor = TvPalette.OnFocused,
            pressedContainerColor = TvPalette.Focused,
            pressedContentColor = TvPalette.OnFocused,
            pressedSelectedContainerColor = TvPalette.Focused,
            pressedSelectedContentColor = TvPalette.OnFocused,
        ),
    ) {
        Text(label, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
