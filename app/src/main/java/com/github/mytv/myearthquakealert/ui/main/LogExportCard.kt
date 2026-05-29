package com.github.mytv.myearthquakealert.ui.main

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.ui.adaptive.handleKeyEvents
import com.github.mytv.myearthquakealert.ui.theme.EeqSpacing

@Composable
fun LogExportCard(
    onExport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Card(
        onClick = onExport,
        colors = CardDefaults.cardColors(
            containerColor = if (isFocused) MaterialTheme.colorScheme.surfaceContainerHigh
            else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        interactionSource = interactionSource,
        modifier = modifier
            .fillMaxWidth()
            .handleKeyEvents(
                interactionSource = interactionSource,
                onSelect = onExport,
            ),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(EeqSpacing.md),
        ) {
            Text(
                text = stringResource(R.string.export_log),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
