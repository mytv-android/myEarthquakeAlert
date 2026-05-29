package com.github.mytv.myearthquakealert.data.websocket

import android.util.Log
import com.github.mytv.myearthquakealert.data.source.EewSource
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

class EewWebSocketClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build(),
) {
    @Volatile
    private var webSocket: WebSocket? = null
    @Volatile
    private var currentSourceUrl: String? = null

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    enum class ConnectionState {
        DISCONNECTED, CONNECTING, CONNECTED,
    }

    @Synchronized
    fun connect(source: EewSource) {
        if (currentSourceUrl == source.wsUrl && _connectionState.value == ConnectionState.CONNECTED) {
            return
        }
        disconnect()
        _connectionState.value = ConnectionState.CONNECTING
        currentSourceUrl = source.wsUrl

        val request = Request.Builder()
            .url(source.wsUrl)
            .build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                _connectionState.value = ConnectionState.CONNECTED
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                _messages.trySend(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                _connectionState.value = ConnectionState.DISCONNECTED
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                Log.w("EewWebSocket", "Connection failure: ${t.message}")
                if (currentSourceUrl == source.wsUrl) {
                    _connectionState.value = ConnectionState.DISCONNECTED
                }
            }
        }

        webSocket = client.newWebSocket(request, listener)
    }

    @Synchronized
    fun disconnect() {
        webSocket?.close(1000, null)
        webSocket = null
        currentSourceUrl = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }
}
