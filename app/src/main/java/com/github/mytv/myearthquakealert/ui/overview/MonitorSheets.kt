package com.github.mytv.myearthquakealert.ui.overview

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.service.DeliveryRequirement
import com.github.mytv.myearthquakealert.ui.components.InfoNote
import com.github.mytv.myearthquakealert.ui.components.SegmentedDefaults
import com.github.mytv.myearthquakealert.ui.components.durationText
import com.github.mytv.myearthquakealert.ui.components.formatClock
import com.github.mytv.myearthquakealert.ui.components.requirementDescription
import com.github.mytv.myearthquakealert.ui.components.requirementIcon
import com.github.mytv.myearthquakealert.ui.components.requirementTitle
import com.github.mytv.myearthquakealert.ui.theme.status
import java.util.Calendar

/** Requirements the preflight lists on this device (full-screen intents only exist on 34+). */
internal fun preflightRequirements(missing: Set<DeliveryRequirement>): List<DeliveryRequirement> =
    DeliveryRequirement.entries.filter { r ->
        when (r) {
            DeliveryRequirement.FULL_SCREEN_INTENT -> Build.VERSION.SDK_INT >= 34
            DeliveryRequirement.AUTO_START -> r in missing
            else -> true
        }
    }

/**
 * 开启后台监测 (design sheetOn): every requirement that decides whether alerts arrive in
 * time, each with its fix, plus 开机自动启动. Missing items never block switching on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreflightSheet(
    missing: Set<DeliveryRequirement>,
    bootStart: Boolean,
    onFix: (DeliveryRequirement) -> Unit,
    onBootStartChange: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    alreadyOn: Boolean = false,
) {
    val sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded))
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        SheetBody(
            title = stringResource(if (alreadyOn) R.string.ui_preflight_title_fix else R.string.ui_preflight_title),
            description = stringResource(R.string.ui_preflight_desc),
        ) {
            val rows = preflightRequirements(missing)
            val count = rows.size + 1
            Column(verticalArrangement = Arrangement.spacedBy(SegmentedDefaults.Gap)) {
                rows.forEachIndexed { i, r ->
                    CheckRow(
                        icon = requirementIcon(r),
                        title = requirementTitle(r),
                        subtitle = requirementDescription(r),
                        shape = SegmentedDefaults.shape(i, count),
                    ) {
                        if (r in missing) {
                            FilledTonalButton(
                                onClick = { onFix(r) },
                                modifier = Modifier.heightIn(min = 36.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp),
                            ) {
                                Text(stringResource(fixLabel(r)), style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp))
                            }
                        } else {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = stringResource(R.string.ui_preflight_ok),
                                tint = MaterialTheme.status.ok,
                            )
                        }
                    }
                }
                CheckRow(
                    icon = Icons.Outlined.RestartAlt,
                    title = stringResource(R.string.boot_start),
                    subtitle = stringResource(R.string.boot_start_on_direct),
                    shape = SegmentedDefaults.shape(count - 1, count),
                ) {
                    Switch(
                        checked = bootStart,
                        onCheckedChange = onBootStartChange,
                        thumbContent = if (bootStart) {
                            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
                        } else {
                            null
                        },
                    )
                }
            }
            val missingRequired = rows.count { it in missing && it.required }
            val missingOptional = rows.count { it in missing && !it.required }
            when {
                missingRequired > 0 -> InfoNote(stringResource(R.string.ui_preflight_missing_required, missingRequired + missingOptional))
                missingOptional > 0 -> InfoNote(stringResource(R.string.ui_preflight_missing_optional, missingOptional))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                if (!alreadyOn) TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                Button(onClick = onConfirm) { Text(stringResource(if (alreadyOn) R.string.ui_done else R.string.ui_turn_on)) }
            }
        }
    }
}

private fun fixLabel(r: DeliveryRequirement): Int = when (r) {
    DeliveryRequirement.LOCATION -> R.string.ui_fix_grant
    DeliveryRequirement.NOTIFICATIONS, DeliveryRequirement.OVERLAY, DeliveryRequirement.FULL_SCREEN_INTENT -> R.string.ui_fix_enable
    else -> R.string.ui_fix_settings
}

private enum class OffOption { HOUR, MORNING, OFF }

/** Next 08:00 in the device time zone (today if it is still before eight). */
internal fun nextMorning(now: Long, hour: Int = 8): Long {
    val c = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    if (c.timeInMillis <= now) c.add(Calendar.DAY_OF_MONTH, 1)
    return c.timeInMillis
}

private fun isSameDay(a: Long, b: Long): Boolean {
    val ca = Calendar.getInstance().apply { timeInMillis = a }
    val cb = Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
}

/**
 * 暂停或关闭后台监测？(design sheetOff): pausing is offered before switching off — 1 hour,
 * until 08:00, or completely off.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PauseSheet(
    now: Long,
    onPause: (untilEpochMillis: Long) -> Unit,
    onTurnOff: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded))
    var option by rememberSaveable { mutableStateOf(OffOption.HOUR) }
    val hourUntil = now + 60 * 60_000L
    val morningUntil = nextMorning(now)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        SheetBody(
            title = stringResource(R.string.ui_pause_title),
            description = stringResource(R.string.ui_pause_desc),
        ) {
            val options = listOf(
                Triple(OffOption.HOUR, stringResource(R.string.ui_pause_hour), stringResource(R.string.ui_pause_resume_at, formatClock(hourUntil))),
                Triple(
                    OffOption.MORNING,
                    stringResource(if (isSameDay(morningUntil, now)) R.string.ui_pause_morning_today else R.string.ui_pause_morning_tomorrow),
                    stringResource(R.string.ui_pause_resume_in, durationText(morningUntil - now)),
                ),
                Triple(OffOption.OFF, stringResource(R.string.ui_pause_off), stringResource(R.string.ui_pause_off_desc)),
            )
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(SegmentedDefaults.Gap)) {
                options.forEachIndexed { i, (key, title, subtitle) ->
                    val shape = SegmentedDefaults.shape(i, options.size)
                    Surface(
                        shape = shape,
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = option == key, role = Role.RadioButton, onClick = { option = key }),
                    ) {
                        Row(
                            Modifier.heightIn(min = 62.dp).padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            RadioButton(selected = option == key, onClick = null)
                            Column(Modifier.weight(1f)) {
                                Text(title, style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold))
                                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                Button(
                    onClick = {
                        when (option) {
                            OffOption.HOUR -> onPause(hourUntil)
                            OffOption.MORNING -> onPause(morningUntil)
                            OffOption.OFF -> onTurnOff()
                        }
                    },
                ) {
                    Text(stringResource(if (option == OffOption.OFF) R.string.ui_pause_confirm_off else R.string.ui_pause_confirm))
                }
            }
        }
    }
}

@Composable
private fun SheetBody(title: String, description: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, bottom = 18.dp)
            .windowInsetsPadding(WindowInsets.navigationBars),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp))
            Spacer(Modifier.size(4.dp))
            Text(description, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        content()
    }
}

/** One row of a sheet checklist (design .chk). */
@Composable
internal fun CheckRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    shape: Shape,
    trailing: @Composable () -> Unit,
) {
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.heightIn(min = 62.dp).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}
