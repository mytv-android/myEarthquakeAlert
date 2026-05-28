package com.github.mytv.myearthquakealert.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.github.mytv.myearthquakealert.ui.theme.ConnectionGreen
import com.github.mytv.myearthquakealert.ui.theme.ConnectingYellow
import com.github.mytv.myearthquakealert.ui.theme.DisconnectedRed
import com.github.mytv.myearthquakealert.ui.theme.EeqSpacing
import com.github.mytv.myearthquakealert.ui.theme.MyEarthQuakeAlertTheme
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.data.websocket.EewWebSocketClient

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

    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = Color.White,
        modifier = modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(color)
            .padding(horizontal = EeqSpacing.md, vertical = EeqSpacing.xs),
    )
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
