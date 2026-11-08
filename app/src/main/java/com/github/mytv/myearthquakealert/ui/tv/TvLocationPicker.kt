package com.github.mytv.myearthquakealert.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.EditLocationAlt
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.mytv.myearthquakealert.MyEarthQuakeAlertApp
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.ui.components.MapChip
import com.github.mytv.myearthquakealert.ui.main.LocalUserSettings
import com.github.mytv.myearthquakealert.ui.map.ChinaCities
import com.github.mytv.myearthquakealert.ui.map.ChinaCity
import com.github.mytv.myearthquakealert.ui.map.EarthquakeMap
import com.github.mytv.myearthquakealert.ui.map.EarthquakeMapMode
import com.github.mytv.myearthquakealert.ui.map.MapPoint
import com.github.mytv.myearthquakealert.ui.overview.coordinateText
import com.github.mytv.myearthquakealert.ui.quakes.QuakesViewModel
import java.util.Locale
import kotlinx.coroutines.launch

/** A chosen position: a listed city or coordinates typed in. */
private data class PickedPlace(val latitude: Double, val longitude: Double, val name: String)

/**
 * 设置你的位置 (design §2 电视: 电视没有 GPS，首次启动要求在地图上选定位置): province list,
 * city grid and a map preview, or coordinates typed in. Saved as the manual location.
 */
@Composable
fun TvLocationPicker(
    vm: QuakesViewModel,
    firstRun: Boolean,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val app = context.applicationContext as? MyEarthQuakeAlertApp
    val scope = rememberCoroutineScope()
    val settings = LocalUserSettings.current
    val state by vm.state.collectAsStateWithLifecycle()
    val english = remember { Locale.getDefault().language != Locale.CHINESE.language }
    val provinces = ChinaCities.byProvince

    // Start on the province of the current position when it is one of ours.
    val initialProvince = remember {
        val here = state.user
        val nearest = here?.let { u -> ChinaCities.all.minByOrNull { c -> sq(c.latitude - u.latitude) + sq(c.longitude - u.longitude) } }
        nearest?.province ?: provinces.first().first
    }
    var province by rememberSaveable { mutableStateOf(initialProvince) }
    var pickedLat by rememberSaveable { mutableStateOf(settings.manualLatitude) }
    var pickedLon by rememberSaveable { mutableStateOf(settings.manualLongitude) }
    var pickedName by rememberSaveable { mutableStateOf(settings.manualLocationName) }
    var manualEntry by rememberSaveable { mutableStateOf(false) }
    val picked = if (pickedLat != null && pickedLon != null) PickedPlace(pickedLat!!, pickedLon!!, pickedName) else null
    val pick: (PickedPlace) -> Unit = {
        pickedLat = it.latitude
        pickedLon = it.longitude
        pickedName = it.name
    }
    val save = {
        val p = picked
        if (p != null) {
            scope.launch {
                app?.settingsRepository?.updateManualLocation(p.latitude, p.longitude, p.name)
                onDone()
            }
        }
    }
    val provinceFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { provinceFocus.requestFocus() } }

    Box(modifier.background(TvPalette.Background).padding(horizontal = 48.dp, vertical = 27.dp)) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Icon(Icons.Outlined.EditLocationAlt, contentDescription = null, tint = TvPalette.OnCard, modifier = Modifier.size(34.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.ui_tv_location_title), color = TvPalette.OnCard, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.ui_tv_location_desc), color = TvPalette.Muted, fontSize = 14.sp)
                }
            }

            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // Provinces: moving the focus switches the city grid.
                LazyColumn(Modifier.width(176.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(provinces, key = { it.first }) { (name, cities) ->
                        val selected = name == province
                        TvTile(
                            onClick = { province = name },
                            container = if (selected) TvPalette.MonitorOff else TvPalette.Tile,
                            focusedScale = 1.03f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (name == initialProvince) Modifier.focusRequester(provinceFocus) else Modifier)
                                .onFocusChanged { if (it.isFocused) province = name },
                        ) {
                            Text(
                                if (english) cities.first().provinceEn else name,
                                fontSize = 16.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                            )
                        }
                    }
                }

                val cities = provinces.firstOrNull { it.first == province }?.second.orEmpty()
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(cities, key = { it.province + it.name }) { city ->
                        CityTile(city, english, selected = picked?.matches(city) == true) {
                            pick(PickedPlace(city.latitude, city.longitude, city.displayName(english)))
                        }
                    }
                }

                Box(Modifier.weight(1.1f).fillMaxHeight().clip(RoundedCornerShape(24.dp))) {
                    val point = picked?.let { MapPoint(it.latitude, it.longitude) } ?: state.user?.point
                    EarthquakeMap(
                        style = settings.mapStyle.forTenFoot(),
                        mode = EarthquakeMapMode.BROWSE,
                        modifier = Modifier.fillMaxSize(),
                        userPoint = point,
                        interactive = false,
                        touchEnabled = false,
                    )
                    if (point != null) {
                        MapChip(
                            picked?.name?.ifBlank { null } ?: coordinateText(point.latitude, point.longitude),
                            icon = Icons.Outlined.LocationOn,
                            containerColor = TvPalette.Overlay,
                            contentColor = TvPalette.OnCard,
                            modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton(
                    text = stringResource(R.string.ui_tv_location_enter),
                    icon = { Icon(Icons.Outlined.Keyboard, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    onClick = { manualEntry = true },
                )
                Spacer(Modifier.weight(1f))
                TvButton(
                    text = stringResource(if (firstRun) R.string.ui_tv_location_later else R.string.cancel),
                    onClick = onDone,
                )
                TvButton(
                    text = stringResource(R.string.ui_tv_location_use),
                    primary = true,
                    enabled = picked != null,
                    onClick = save,
                )
            }
        }
    }

    if (manualEntry) {
        ManualCoordinatesDialog(
            initial = picked,
            onDismiss = { manualEntry = false },
            onConfirm = {
                pick(it)
                manualEntry = false
            },
        )
    }
}

private fun sq(x: Double) = x * x

private fun PickedPlace.matches(city: ChinaCity): Boolean =
    kotlin.math.abs(latitude - city.latitude) < 1e-6 && kotlin.math.abs(longitude - city.longitude) < 1e-6

@Composable
private fun CityTile(city: ChinaCity, english: Boolean, selected: Boolean, onClick: () -> Unit) {
    TvTile(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp), focusedScale = 1.04f) { focused ->
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (english) city.nameEn else city.name,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (city.capital) stringResource(R.string.ui_tv_capital) else coordinateText(city.latitude, city.longitude),
                    fontSize = 12.sp,
                    color = if (focused) TvPalette.OnFocusedMuted else TvPalette.Muted,
                    maxLines = 1,
                )
            }
            if (selected) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = if (focused) TvPalette.OnFocused else TvPalette.PillOn, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun TvButton(
    text: String,
    onClick: () -> Unit,
    icon: (@Composable () -> Unit)? = null,
    primary: Boolean = false,
    enabled: Boolean = true,
) {
    TvTile(
        onClick = { if (enabled) onClick() },
        shape = RoundedCornerShape(26.dp),
        container = when {
            !enabled -> TvPalette.Tile.copy(alpha = 0.5f)
            primary -> TvPalette.PillOn
            else -> TvPalette.Tile
        },
        content = when {
            !enabled -> TvPalette.Muted
            primary -> TvPalette.OnFocused
            else -> TvPalette.OnCard
        },
        focusedScale = 1.06f,
    ) {
        Row(
            Modifier.heightIn(min = 52.dp).padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            icon?.invoke()
            Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = LocalContentColor.current)
        }
    }
}

/** 手动输入坐标: latitude, longitude and an optional place name. */
@Composable
private fun ManualCoordinatesDialog(
    initial: PickedPlace?,
    onDismiss: () -> Unit,
    onConfirm: (PickedPlace) -> Unit,
) {
    var lat by rememberSaveable { mutableStateOf(initial?.latitude?.let { String.format(Locale.US, "%.4f", it) }.orEmpty()) }
    var lon by rememberSaveable { mutableStateOf(initial?.longitude?.let { String.format(Locale.US, "%.4f", it) }.orEmpty()) }
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    val latValue = lat.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it in -90.0..90.0 }
    val lonValue = lon.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it in -180.0..180.0 }
    val valid = latValue != null && lonValue != null
    val latitudeFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { latitudeFocus.requestFocus() } }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Keyboard, contentDescription = null) },
        title = { Text(stringResource(R.string.ui_tv_location_enter)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = lat,
                    onValueChange = { lat = it },
                    label = { Text(stringResource(R.string.ui_latitude)) },
                    placeholder = { Text("30.5728") },
                    isError = lat.isNotBlank() && latValue == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().focusRequester(latitudeFocus),
                )
                OutlinedTextField(
                    value = lon,
                    onValueChange = { lon = it },
                    label = { Text(stringResource(R.string.ui_longitude)) },
                    placeholder = { Text("104.0668") },
                    isError = lon.isNotBlank() && lonValue == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.ui_place_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.ui_coordinates_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (latValue != null && lonValue != null) {
                        onConfirm(PickedPlace(latValue, lonValue, name.trim().ifBlank { coordinateText(latValue, lonValue) }))
                    }
                },
                enabled = valid,
            ) { Text(stringResource(R.string.ui_done)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
