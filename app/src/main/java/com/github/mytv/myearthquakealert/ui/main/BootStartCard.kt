package com.github.mytv.myearthquakealert.ui.main

import android.os.Build
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.ui.adaptive.handleKeyEvents
import com.github.mytv.myearthquakealert.ui.theme.EeqSpacing
import com.github.mytv.myearthquakealert.ui.theme.MyEarthQuakeAlertTheme

/**
 * "Start on boot" setting. Independent of the monitoring switch, but only meaningful
 * while monitoring is on, so it is greyed out otherwise.
 *
 * @param bootDelaySeconds seconds from boot to the automatic start in this boot, or null
 * @param showAutoStartHint the ROM is known to block boot broadcasts by default
 */
@Composable
fun BootStartCard(
    monitoringEnabled: Boolean,
    bootStart: Boolean,
    bootDelaySeconds: Long?,
    showAutoStartHint: Boolean,
    onToggle: (Boolean) -> Unit,
    onOpenAutoStartSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val checked = monitoringEnabled && bootStart

    val subtitle = when {
        !monitoringEnabled -> stringResource(R.string.boot_start_unavailable)
        !bootStart -> stringResource(R.string.boot_start_off)
        bootDelaySeconds != null -> stringResource(R.string.boot_start_done, bootDelaySeconds)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.N -> stringResource(R.string.boot_start_on_direct)
        else -> stringResource(R.string.boot_start_on)
    }

    Card(
        onClick = { onToggle(!bootStart) },
        enabled = monitoringEnabled,
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (monitoringEnabled) 1f else 0.5f)
            .handleKeyEvents(
                interactionSource = interactionSource,
                onSelect = { if (monitoringEnabled) onToggle(!bootStart) },
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isFocused) MaterialTheme.colorScheme.surfaceContainerHigh
                             else MaterialTheme.colorScheme.surfaceContainerLow,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(EeqSpacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.RestartAlt,
                    contentDescription = null,
                    tint = if (checked) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(EeqSpacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.boot_start),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = checked,
                    onCheckedChange = null,
                    enabled = monitoringEnabled,
                )
            }
            if (checked && showAutoStartHint) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = EeqSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.autostart_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onOpenAutoStartSettings) {
                        Text(stringResource(R.string.autostart_open))
                    }
                }
            }
        }
    }
}

@Preview(name = "Boot start on")
@Composable
private fun BootStartCardOnPreview() {
    MyEarthQuakeAlertTheme {
        BootStartCard(
            monitoringEnabled = true, bootStart = true, bootDelaySeconds = 9,
            showAutoStartHint = true, onToggle = {}, onOpenAutoStartSettings = {},
        )
    }
}

@Preview(name = "Monitoring off")
@Composable
private fun BootStartCardDisabledPreview() {
    MyEarthQuakeAlertTheme {
        BootStartCard(
            monitoringEnabled = false, bootStart = true, bootDelaySeconds = null,
            showAutoStartHint = false, onToggle = {}, onOpenAutoStartSettings = {},
        )
    }
}
