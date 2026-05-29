package com.github.mytv.myearthquakealert.ui.main

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
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
            containerColor = if (isFocused) MaterialTheme.colorScheme.surfaceContainerHigh
                             else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier.padding(EeqSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.service_toggle),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
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
