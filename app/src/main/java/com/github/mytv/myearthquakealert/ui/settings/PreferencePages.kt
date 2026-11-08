package com.github.mytv.myearthquakealert.ui.settings

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CrisisAlert
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.EditLocation
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardReturn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.data.model.LocationMode
import com.github.mytv.myearthquakealert.data.source.EewSource
import com.github.mytv.myearthquakealert.service.DeliveryRequirement
import com.github.mytv.myearthquakealert.service.LinkState
import com.github.mytv.myearthquakealert.service.SourceHealth
import com.github.mytv.myearthquakealert.ui.components.SegmentedGroup
import com.github.mytv.myearthquakealert.ui.components.SegmentedRow
import com.github.mytv.myearthquakealert.ui.components.Subhead
import com.github.mytv.myearthquakealert.ui.settings.components.ChoiceOption
import com.github.mytv.myearthquakealert.ui.settings.components.Formats
import com.github.mytv.myearthquakealert.ui.settings.components.InfoNote
import com.github.mytv.myearthquakealert.ui.settings.components.InfoRow
import com.github.mytv.myearthquakealert.ui.settings.components.RadioOptions
import com.github.mytv.myearthquakealert.ui.settings.components.SettingsPageScaffold
import com.github.mytv.myearthquakealert.ui.settings.components.StepperSlider
import com.github.mytv.myearthquakealert.ui.settings.components.SwitchRow
import com.github.mytv.myearthquakealert.ui.settings.components.focusHighlight
import com.github.mytv.myearthquakealert.ui.theme.csisColor
import com.github.mytv.myearthquakealert.ui.theme.status
import java.util.Locale
import kotlin.math.roundToInt

// ---------------------------------------------------------------- 数据源

/** 设置 › 数据源: the four Wolfx feeds; at least one stays subscribed. */
@Composable
internal fun SourcesPage(env: SettingsEnv, onBack: (() -> Unit)?) {
    val settings = env.settings
    val formats = env.formats
    val health = env.monitor.sources.associateBy { it.source }
    val active = env.monitor.isActive
    SettingsPageScaffold(title = stringResource(R.string.pg_sources), onBack = onBack) {
        item(key = "list") {
            SegmentedGroup(EewSource.entries.size) { index, shape ->
                val source = EewSource.entries[index]
                val checked = source in settings.enabledSources
                val toggle = {
                    if (checked && settings.enabledSources.size == 1) {
                        env.actions.toast(R.string.pg_sources_min)
                    } else {
                        val next = if (checked) settings.enabledSources - source else settings.enabledSources + source
                        env.actions.edit { updateEnabledSources(next) }
                    }
                }
                SegmentedRow(
                    title = formats.sourceName(source),
                    subtitle = sourceStatus(source, checked, if (active) health[source] else null, formats),
                    icon = Icons.Filled.Sensors,
                    shape = shape,
                    modifier = Modifier.focusHighlight(shape),
                    onClick = toggle,
                    trailing = { Checkbox(checked = checked, onCheckedChange = null) },
                )
            }
        }
        item(key = "note") { InfoNote(stringResource(R.string.pg_sources_note)) }
    }
}

@Composable
private fun sourceStatus(source: EewSource, subscribed: Boolean, health: SourceHealth?, formats: Formats): String {
    val id = source.wsUrl.substringAfterLast('/')
    if (!subscribed) return "$id · " + stringResource(R.string.pg_source_unsubscribed)
    if (health == null) return id
    val latency = health.lastLatencyMs
    val link = when (health.link) {
        LinkState.CONNECTED -> if (latency != null) {
            stringResource(R.string.pg_link_connected_latency, formats.seconds(latency))
        } else {
            stringResource(R.string.pg_link_connected)
        }
        LinkState.POLLING -> stringResource(R.string.pg_link_polling)
        LinkState.RECONNECTING -> stringResource(R.string.pg_link_reconnecting)
        LinkState.CONNECTING -> stringResource(R.string.pg_link_connecting)
        LinkState.DISCONNECTED -> stringResource(R.string.pg_link_disconnected)
    }
    return "$id · $link"
}

// ---------------------------------------------------------------- 预警阈值

/** 设置 › 预警阈值 / 强震阈值 (migrated from the 1.0 ThresholdSettings card). */
@Composable
internal fun AlertsPage(env: SettingsEnv, onBack: (() -> Unit)?) {
    val settings = env.settings
    val actions = env.actions
    val dndMissing = DeliveryRequirement.DND_ACCESS in env.missing
    val any = stringResource(R.string.pg_value_any)
    SettingsPageScaffold(title = stringResource(R.string.pg_alert_thresholds), onBack = onBack) {
        item(key = "remind") {
            Column {
                Subhead(stringResource(R.string.pg_group_remind))
                SegmentedGroup(2) { index, shape ->
                    if (index == 0) {
                        StepperSlider(
                            title = stringResource(R.string.min_magnitude),
                            description = stringResource(R.string.pg_min_mag_desc),
                            valueLabel = { if (it <= 0f) any else "M" + magnitudeValue(it.toDouble()) },
                            value = settings.actionMinMagnitude.toFloat(),
                            valueRange = 0f..9f,
                            step = 0.5f,
                            onCommit = { value -> actions.edit { updateActionMinMagnitude(value.toDouble()) } },
                            shape = shape,
                            icon = Icons.Filled.Speed,
                        )
                    } else {
                        StepperSlider(
                            title = stringResource(R.string.pg_min_int_title),
                            description = stringResource(R.string.pg_min_int_desc),
                            valueLabel = { if (it <= 0f) any else env.formats.string(R.string.pg_value_intensity, it.roundToInt()) },
                            value = settings.actionMinIntensity.toFloat(),
                            valueRange = 0f..12f,
                            step = 1f,
                            onCommit = { value -> actions.edit { updateActionMinIntensity(value.roundToInt()) } },
                            shape = shape,
                            icon = Icons.Filled.NearMe,
                        )
                    }
                }
            }
        }
        item(key = "strong") {
            Column {
                Subhead(stringResource(R.string.pg_group_strong))
                SegmentedGroup(1) { _, shape ->
                    StepperSlider(
                        title = stringResource(R.string.pg_strong_title),
                        description = stringResource(R.string.pg_strong_desc),
                        valueLabel = { env.formats.string(R.string.pg_value_intensity, it.roundToInt()) },
                        value = settings.intenseThreshold.toFloat(),
                        valueRange = 1f..12f,
                        step = 1f,
                        onCommit = { value -> actions.edit { updateIntenseThreshold(value.roundToInt()) } },
                        shape = shape,
                        icon = Icons.Filled.CrisisAlert,
                    )
                }
            }
        }
        item(key = "scale") { CsisScale(settings.actionMinIntensity, settings.intenseThreshold) }
        item(key = "sound") {
            Column {
                Subhead(stringResource(R.string.pg_group_sound))
                SegmentedGroup(3) { index, shape ->
                    when (index) {
                        0 -> SwitchRow(
                            title = stringResource(R.string.pg_bypass_dnd),
                            shape = shape,
                            icon = Icons.Filled.DoNotDisturbOn,
                            subtitle = stringResource(if (settings.alarmBypassDnd && dndMissing) R.string.pg_bypass_dnd_needs else R.string.pg_bypass_dnd_desc),
                            checked = settings.alarmBypassDnd,
                            onCheckedChange = { on ->
                                actions.edit { updateAlarmBypassDnd(on) }
                                if (on && dndMissing) actions.fix(DeliveryRequirement.DND_ACCESS)
                            },
                        )
                        1 -> SwitchRow(
                            title = stringResource(R.string.pg_tts),
                            shape = shape,
                            icon = Icons.Filled.GraphicEq,
                            subtitle = stringResource(R.string.pg_tts_desc),
                            checked = settings.ttsEnabled,
                            onCheckedChange = { on -> actions.edit { updateTtsEnabled(on) } },
                        )
                        else -> SwitchRow(
                            title = stringResource(R.string.pg_back_dismiss),
                            shape = shape,
                            icon = Icons.Filled.KeyboardReturn,
                            subtitle = stringResource(R.string.pg_back_dismiss_desc),
                            checked = settings.allowDismissWithBack,
                            onCheckedChange = { on -> actions.edit { updateAllowDismissWithBack(on) } },
                        )
                    }
                }
            }
        }
        item(key = "note") { InfoNote(stringResource(R.string.pg_alert_tiers_note)) }
    }
}

/**
 * The CSIS colour scale with the two thresholds marked: cells below the reminder threshold
 * are dimmed, cells at or above the strong threshold carry a bar underneath.
 */
@Composable
private fun CsisScale(minIntensity: Int, strong: Int) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.pg_csis_scale), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.fillMaxWidth()) {
                for (level in 1..12) {
                    val reminded = minIntensity <= 0 || level >= minIntensity
                    val color = csisColor(level.toDouble())
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                                .padding(0.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (reminded) color else color.copy(alpha = 0.25f),
                                modifier = Modifier.fillMaxWidth().height(28.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        level.toString(),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (level >= 4 && level <= 6) Color(0xFF16191E) else Color.White,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .padding(horizontal = 1.dp),
                        ) {
                            if (level >= strong) {
                                Surface(shape = RoundedCornerShape(2.dp), color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth().height(4.dp)) {}
                            }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    stringResource(R.string.pg_scale_remind, if (minIntensity <= 0) 1 else minIntensity),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.pg_scale_strong, strong),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- 位置

/** Preset cities for 手动指定; names come from `pg_city_names` in the same order. */
private val CityCoordinates = listOf(
    39.9042 to 116.4074, // 北京
    31.2304 to 121.4737, // 上海
    23.1291 to 113.2644, // 广州
    22.5431 to 114.0579, // 深圳
    30.5728 to 104.0668, // 成都
    29.5630 to 106.5516, // 重庆
    34.3416 to 108.9398, // 西安
    25.0389 to 102.7183, // 昆明
    36.0611 to 103.8343, // 兰州
    29.6525 to 91.1721,  // 拉萨
    43.8256 to 87.6168,  // 乌鲁木齐
    26.0745 to 119.2965, // 福州
    25.0330 to 121.5654, // 台北
    30.2741 to 120.1551, // 杭州
)

@SuppressLint("MissingPermission")
private fun lastKnownLocation(context: Context): Pair<Double, Double>? {
    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    if (!granted) return null
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    return try {
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
            .mapNotNull { manager.getLastKnownLocation(it) }
            .maxByOrNull { it.time }
            ?.let { it.latitude to it.longitude }
    } catch (e: SecurityException) {
        null
    }
}

private fun parseCoordinate(text: String, limit: Double): Double? =
    text.trim().toDoubleOrNull()?.takeIf { it in -limit..limit }

/** 设置 › 位置来源: automatic (GPS, IP fallback) or a fixed place. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LocationPage(env: SettingsEnv, onBack: (() -> Unit)?) {
    val context = LocalContext.current
    val settings = env.settings
    val metrics = env.metrics
    val actions = env.actions
    val formats = env.formats
    val cities = stringArrayResource(R.array.pg_city_names)

    var name by rememberSaveable(settings.manualLocationName) { mutableStateOf(settings.manualLocationName) }
    var latText by rememberSaveable(settings.manualLatitude) { mutableStateOf(settings.manualLatitude?.let { String.format(Locale.ROOT, "%.4f", it) } ?: "") }
    var lonText by rememberSaveable(settings.manualLongitude) { mutableStateOf(settings.manualLongitude?.let { String.format(Locale.ROOT, "%.4f", it) } ?: "") }
    val lat = parseCoordinate(latText, 90.0)
    val lon = parseCoordinate(lonText, 180.0)
    val latError = latText.isNotBlank() && lat == null
    val lonError = lonText.isNotBlank() && lon == null
    val dirty = lat != settings.manualLatitude || lon != settings.manualLongitude || name.trim() != settings.manualLocationName

    fun save(newLat: Double, newLon: Double, newName: String) {
        actions.edit { updateManualLocation(newLat, newLon, newName.trim()) }
        actions.toast(R.string.pg_loc_saved)
    }

    SettingsPageScaffold(title = stringResource(R.string.pg_location_source), onBack = onBack) {
        item(key = "mode") {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                RadioOptions(
                    options = listOf(
                        ChoiceOption(LocationMode.AUTO, stringResource(R.string.pg_loc_mode_auto), stringResource(R.string.pg_loc_mode_auto_desc)),
                        ChoiceOption(LocationMode.MANUAL, stringResource(R.string.pg_loc_mode_manual), stringResource(R.string.pg_loc_mode_manual_desc)),
                    ),
                    selected = settings.locationMode,
                    onSelect = { mode -> actions.edit { updateLocationMode(mode) } },
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
        if (settings.locationMode == LocationMode.AUTO) {
            item(key = "auto") {
                Column {
                    Subhead(stringResource(R.string.pg_loc_status))
                    SegmentedGroup(2) { index, shape ->
                        if (index == 0) {
                            val parts = buildList {
                                add(locationSourceLabel(metrics.locationSource))
                                metrics.locationAccuracyM?.let { add(stringResource(R.string.pg_loc_accuracy, it.roundToInt())) }
                                metrics.locationAgeMs?.let { add(stringResource(R.string.pg_loc_age, formats.ago(env.now - it, env.now))) }
                            }
                            InfoRow(
                                title = stringResource(R.string.pg_loc_current),
                                shape = shape,
                                icon = Icons.Filled.MyLocation,
                                subtitle = parts.joinToString(" · "),
                            )
                        } else {
                            val requirement = DeliveryRequirement.LOCATION
                            SegmentedRow(
                                title = formats.requirementLabel(requirement),
                                subtitle = formats.requirementDescription(requirement),
                                icon = requirementIcon(requirement),
                                shape = shape,
                                trailing = { RequirementStatus(requirement, requirement !in env.missing, actions::fix) },
                            )
                        }
                    }
                }
            }
            val source = metrics.locationSource?.lowercase(Locale.ROOT)
            if (source == "ip" || source == "none") {
                item(key = "ipwarn") {
                    InfoNote(
                        text = stringResource(R.string.pg_loc_ip_warn),
                        icon = Icons.Filled.Warning,
                        container = MaterialTheme.status.warnContainer,
                        contentColor = MaterialTheme.status.onWarnContainer,
                    )
                }
            }
        } else {
            item(key = "manual") {
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.EditLocation, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.size(10.dp))
                            Text(stringResource(R.string.pg_loc_manual_title), style = MaterialTheme.typography.titleMedium)
                        }
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it.take(40) },
                            label = { Text(stringResource(R.string.pg_loc_name)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = latText,
                                onValueChange = { latText = it.take(12) },
                                label = { Text(stringResource(R.string.pg_loc_lat)) },
                                isError = latError,
                                supportingText = if (latError) ({ Text(stringResource(R.string.pg_loc_invalid_lat)) }) else null,
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = lonText,
                                onValueChange = { lonText = it.take(12) },
                                label = { Text(stringResource(R.string.pg_loc_lon)) },
                                isError = lonError,
                                supportingText = if (lonError) ({ Text(stringResource(R.string.pg_loc_invalid_lon)) }) else null,
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
                            FilledTonalButton(
                                onClick = {
                                    val fix = lastKnownLocation(context)
                                    if (fix == null) {
                                        if (DeliveryRequirement.LOCATION in env.missing) actions.fix(DeliveryRequirement.LOCATION) else actions.toast(R.string.pg_loc_no_fix)
                                    } else {
                                        latText = String.format(Locale.ROOT, "%.4f", fix.first)
                                        lonText = String.format(Locale.ROOT, "%.4f", fix.second)
                                    }
                                },
                                modifier = Modifier.focusHighlight(RoundedCornerShape(50)),
                            ) {
                                Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(6.dp))
                                Text(stringResource(R.string.pg_loc_use_current))
                            }
                            Button(
                                onClick = { if (lat != null && lon != null) save(lat, lon, name) },
                                enabled = lat != null && lon != null && (dirty || !settings.hasManualLocation),
                                modifier = Modifier.focusHighlight(RoundedCornerShape(50)),
                            ) { Text(stringResource(R.string.pg_loc_save)) }
                        }
                    }
                }
            }
            item(key = "cities") {
                Column {
                    Subhead(stringResource(R.string.pg_loc_presets))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
                        cities.forEachIndexed { index, city ->
                            val coordinate = CityCoordinates.getOrNull(index) ?: return@forEachIndexed
                            SuggestionChip(
                                onClick = {
                                    name = city
                                    latText = String.format(Locale.ROOT, "%.4f", coordinate.first)
                                    lonText = String.format(Locale.ROOT, "%.4f", coordinate.second)
                                    save(coordinate.first, coordinate.second, city)
                                },
                                label = { Text(city) },
                                modifier = Modifier.focusHighlight(RoundedCornerShape(8.dp)),
                            )
                        }
                    }
                }
            }
            item(key = "manualnote") { InfoNote(stringResource(R.string.pg_loc_manual_note)) }
        }
    }
}
