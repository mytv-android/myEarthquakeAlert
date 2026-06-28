package com.github.mytv.myearthquakealert.ui.main

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.data.websocket.EewWebSocketClient
import com.github.mytv.myearthquakealert.ui.theme.ConnectionGreen
import com.github.mytv.myearthquakealert.ui.theme.ConnectingYellow
import com.github.mytv.myearthquakealert.ui.theme.DisconnectedRed
import com.github.mytv.myearthquakealert.ui.theme.EeqSpacing
import com.github.mytv.myearthquakealert.ui.theme.MyEarthQuakeAlertTheme

@Composable
fun ConnectionStatusChip(
    state: EewWebSocketClient.ConnectionState,
    modifier: Modifier = Modifier,
) {
    val (text, color) = when (state) {
        EewWebSocketClient.ConnectionState.CONNECTED ->
            stringResource(R.string.connection_connected) to ConnectionGreen
        EewWebSocketClient.ConnectionState.CONNECTING ->
            stringResource(R.string.connection_connecting) to ConnectingYellow
        EewWebSocketClient.ConnectionState.DISCONNECTED ->
            stringResource(R.string.connection_disconnected) to DisconnectedRed
    }

    val isConnecting = state == EewWebSocketClient.ConnectionState.CONNECTING

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )

    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = color.copy(alpha = 0.15f),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = EeqSpacing.sm, vertical = EeqSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(EeqSpacing.xs),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (isConnecting) color.copy(alpha = pulseAlpha) else color
                    ),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = color,
            )
        }
    }
}

@Preview(name = "Connected")
@Composable
private fun ConnectionStatusChipConnectedPreview() {
    MyEarthQuakeAlertTheme {
        ConnectionStatusChip(state = EewWebSocketClient.ConnectionState.CONNECTED)
    }
}

@Preview(name = "Connecting")
@Composable
private fun ConnectionStatusChipConnectingPreview() {
    MyEarthQuakeAlertTheme {
        ConnectionStatusChip(state = EewWebSocketClient.ConnectionState.CONNECTING)
    }
}

@Preview(name = "Disconnected")
@Composable
private fun ConnectionStatusChipDisconnectedPreview() {
    MyEarthQuakeAlertTheme {
        ConnectionStatusChip(state = EewWebSocketClient.ConnectionState.DISCONNECTED)
    }
}
