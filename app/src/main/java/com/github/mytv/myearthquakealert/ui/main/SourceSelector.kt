package com.github.mytv.myearthquakealert.ui.main

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.data.source.EewSource
import com.github.mytv.myearthquakealert.ui.adaptive.handleKeyEvents
import com.github.mytv.myearthquakealert.ui.theme.EeqSpacing
import com.github.mytv.myearthquakealert.ui.theme.MyEarthQuakeAlertTheme
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun SourceSelector(
    selected: EewSource,
    onSelected: (EewSource) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(EeqSpacing.sm)) {
        Text(
            text = stringResource(R.string.source_selector),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = EeqSpacing.sm),
        )
        EewSource.entries.forEach { source ->
            SourceItem(
                label = source.label,
                isSelected = source == selected,
                onSelect = { onSelected(source) },
            )
        }
    }
}

@Composable
private fun SourceItem(
    label: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.02f else 1f,
        label = "sourceScale",
    )
    val cornerSize by animateDpAsState(
        targetValue = if (isFocused) 20.dp else 16.dp,
        label = "sourceCorner",
    )

    val colors = if (isSelected) {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    } else {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        )
    }

    Card(
        onClick = onSelect,
        shape = RoundedCornerShape(cornerSize),
        colors = colors,
        border = if (!isSelected) CardDefaults.outlinedCardBorder() else null,
        interactionSource = interactionSource,
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .handleKeyEvents(
                interactionSource = interactionSource,
                onSelect = onSelect,
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = EeqSpacing.md, vertical = EeqSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (isSelected) {
                Text(
                    text = "✓",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Preview(name = "Source Selector")
@Composable
private fun SourceSelectorPreview() {
    MyEarthQuakeAlertTheme { SourceSelector(selected = EewSource.CENC, onSelected = {}) }
}
