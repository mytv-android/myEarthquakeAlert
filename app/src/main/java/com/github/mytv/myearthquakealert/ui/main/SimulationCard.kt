package com.github.mytv.myearthquakealert.ui.main

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.ui.adaptive.handleKeyEvents
import androidx.compose.ui.text.font.FontWeight
import com.github.mytv.myearthquakealert.ui.theme.EeqSpacing

@Composable
fun SimulationCard(
    onSimulate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val focusRequester = remember { FocusRequester() }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.02f else 1f,
        label = "simScale",
    )
    val cornerSize by animateDpAsState(
        targetValue = if (isFocused) 20.dp else 16.dp,
        label = "simCorner",
    )

    Card(
        onClick = onSimulate,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(cornerSize),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        interactionSource = interactionSource,
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .scale(scale)
            .handleKeyEvents(
                interactionSource = interactionSource,
                onSelect = onSimulate,
            ),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(EeqSpacing.md),
        ) {
            Text(
                text = stringResource(R.string.simulation_test),
                style = if (isFocused) MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        else MaterialTheme.typography.labelLarge,
            )
        }
    }
}
