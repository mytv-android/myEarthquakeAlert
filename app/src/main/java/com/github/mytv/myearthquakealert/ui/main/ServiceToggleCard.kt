package com.github.mytv.myearthquakealert.ui.main

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.ui.adaptive.handleKeyEvents
import com.github.mytv.myearthquakealert.ui.theme.EeqSpacing
import com.github.mytv.myearthquakealert.ui.theme.MyEarthQuakeAlertTheme

@Composable
fun ServiceToggleCard(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Card(
        onClick = { onToggle(!enabled) },
        modifier = modifier
            .fillMaxWidth()
            .handleKeyEvents(
                interactionSource = interactionSource,
                onSelect = { onToggle(!enabled) },
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) MaterialTheme.colorScheme.primaryContainer
                             else if (isFocused) MaterialTheme.colorScheme.surfaceContainerHigh
                             else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier.padding(EeqSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (enabled) Icons.Filled.NotificationsActive else Icons.Filled.NotificationsOff,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(EeqSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.service_toggle),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = if (enabled) stringResource(R.string.service_active)
                           else stringResource(R.string.service_inactive),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                enabled = false,
                checked = enabled,
                onCheckedChange = null,
            )
        }
    }
}

@Preview(name = "Service Enabled")
@Composable
private fun ServiceToggleCardEnabledPreview() {
    MyEarthQuakeAlertTheme { ServiceToggleCard(enabled = true, onToggle = {}) }
}

@Preview(name = "Service Disabled")
@Composable
private fun ServiceToggleCardDisabledPreview() {
    MyEarthQuakeAlertTheme { ServiceToggleCard(enabled = false, onToggle = {}) }
}
