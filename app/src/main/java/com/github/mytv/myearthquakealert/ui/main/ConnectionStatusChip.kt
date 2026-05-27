package com.github.mytv.myearthquakealert.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.data.websocket.EewWebSocketClient

@Composable
fun ConnectionStatusChip(
    state: EewWebSocketClient.ConnectionState,
    modifier: Modifier = Modifier,
) {
    val (text, color) = when (state) {
        EewWebSocketClient.ConnectionState.CONNECTED ->
            stringResource(R.string.connection_connected) to Color(0xFF4CAF50)
        EewWebSocketClient.ConnectionState.CONNECTING ->
            stringResource(R.string.connection_connecting) to Color(0xFFFFC107)
        EewWebSocketClient.ConnectionState.DISCONNECTED ->
            stringResource(R.string.connection_disconnected) to Color(0xFFF44336)
    }

    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = Color.White,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}
