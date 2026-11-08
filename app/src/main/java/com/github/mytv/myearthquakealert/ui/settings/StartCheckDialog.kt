package com.github.mytv.myearthquakealert.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.service.DeliveryRequirement
import com.github.mytv.myearthquakealert.ui.components.SegmentedGroup
import com.github.mytv.myearthquakealert.ui.components.SegmentedRow
import com.github.mytv.myearthquakealert.ui.settings.components.InfoNote
import com.github.mytv.myearthquakealert.ui.settings.components.SwitchRow
import com.github.mytv.myearthquakealert.ui.settings.components.focusHighlight
import com.github.mytv.myearthquakealert.ui.theme.status

/** Label of the button that resolves [requirement]. */
@Composable
internal fun fixLabel(requirement: DeliveryRequirement): String = stringResource(
    when (requirement) {
        DeliveryRequirement.NOTIFICATIONS, DeliveryRequirement.LOCATION -> R.string.pg_fix_grant
        DeliveryRequirement.OVERLAY, DeliveryRequirement.FULL_SCREEN_INTENT -> R.string.pg_fix_enable
        DeliveryRequirement.BATTERY_OPTIMIZATION, DeliveryRequirement.DND_ACCESS, DeliveryRequirement.AUTO_START -> R.string.pg_fix_settings
    }
)

/** Trailing element of a requirement row: a check when met, a tonal fix button when not. */
@Composable
internal fun RequirementStatus(requirement: DeliveryRequirement, met: Boolean, onFix: (DeliveryRequirement) -> Unit) {
    if (met) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.status.ok, modifier = Modifier.size(24.dp))
    } else {
        FilledTonalButton(onClick = { onFix(requirement) }, modifier = Modifier.focusHighlight(RoundedCornerShape(50))) {
            Text(fixLabel(requirement))
        }
    }
}

/** 开启后台监测 (prototype sheetOn): lists what decides whether alerts get through. */
@Composable
internal fun StartCheckDialog(env: SettingsEnv, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val formats = env.formats
    val missingCount = StartCheckRequirements.count { it in env.missing }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pg_start_check_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.pg_start_check_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SegmentedGroup(StartCheckRequirements.size + 1) { index, shape ->
                    if (index < StartCheckRequirements.size) {
                        val requirement = StartCheckRequirements[index]
                        SegmentedRow(
                            title = formats.requirementLabel(requirement),
                            subtitle = formats.requirementDescription(requirement),
                            icon = requirementIcon(requirement),
                            shape = shape,
                            trailing = { RequirementStatus(requirement, requirement !in env.missing, env.actions::fix) },
                        )
                    } else {
                        SwitchRow(
                            title = stringResource(R.string.boot_start),
                            shape = shape,
                            icon = Icons.Filled.RestartAlt,
                            subtitle = stringResource(R.string.boot_start_on_direct),
                            checked = env.settings.bootStart,
                            onCheckedChange = { on -> env.actions.edit { updateBootStart(on) } },
                        )
                    }
                }
                if (missingCount > 0) {
                    InfoNote(
                        text = stringResource(R.string.pg_start_check_missing, missingCount),
                        icon = Icons.Filled.Warning,
                        container = MaterialTheme.status.warnContainer,
                        contentColor = MaterialTheme.status.onWarnContainer,
                    )
                }
                Spacer(Modifier.size(2.dp))
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, modifier = Modifier.focusHighlight(RoundedCornerShape(50))) {
                Text(stringResource(R.string.pg_start_check_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.focusHighlight(RoundedCornerShape(50))) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
