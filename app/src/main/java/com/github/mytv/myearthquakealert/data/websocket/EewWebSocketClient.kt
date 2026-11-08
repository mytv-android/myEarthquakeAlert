package com.github.mytv.myearthquakealert.data.websocket

import com.github.mytv.myearthquakealert.data.source.EewSource
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * One WebSocket session to one Wolfx feed. The monitoring service opens one client per
 * subscribed source and per connection attempt; every callback is delivered in order
 * through [events], so the caller decides about liveness, backoff and fallback.
 *
 * OkHttp pings every 30 s at the protocol level and fails the socket when pongs stop;
 * the caller additionally sends a text `ping` (answered by a JSON `pong` frame) so the
 * feed itself proves it is alive.
 */
class EewWebSocketClient(
    private val client: OkHttpClient = defaultClient,
) {
    enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED }

    sealed interface Event {
        /** Handshake completed; [tls] is e.g. "TLSv1.3". */
        data class Opened(val handshakeMs: Long, val tls: String?) : Event
        data class Message(val text: String, val receivedAt: Long) : Event
        /** The server closed the socket (or we did, through [disconnect]). */
        data class Closed(val code: Int, val reason: String) : Event
        /** Connect error, TLS failure, missing pong, network loss… */
        data class Failure(val message: String, val httpCode: Int?) : Event
    }

    private val _events = Channel<Event>(Channel.UNLIMITED)
    val events: ReceiveChannel<Event> get() = _events

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    @Volatile
    private var webSocket: WebSocket? = null

    /** Opens the socket. A client connects once; further calls while open are ignored. */
    @Synchronized
    fun connect(source: EewSource) {
        if (webSocket != null) return
        _connectionState.value = ConnectionState.CONNECTING
        val startedAt = System.currentTimeMillis()
        val request = Request.Builder().url(source.wsUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _connectionState.value = ConnectionState.CONNECTED
                _events.trySend(
                    Event.Opened(
                        handshakeMs = System.currentTimeMillis() - startedAt,
                        tls = response.handshake?.tlsVersion?.javaName,
                    )
                )
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                _events.trySend(Event.Message(text, System.currentTimeMillis()))
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                _connectionState.value = ConnectionState.DISCONNECTED
                _events.trySend(Event.Closed(code, reason))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _connectionState.value = ConnectionState.DISCONNECTED
                _events.trySend(Event.Closed(code, reason))
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _connectionState.value = ConnectionState.DISCONNECTED
                _events.trySend(Event.Failure(t.message ?: t.javaClass.simpleName, response?.code))
            }
        })
    }

    /** Sends a text frame; false when the socket is not open. */
    fun send(text: String): Boolean = webSocket?.send(text) ?: false

    /** Graceful close; pending events are still delivered. */
    @Synchronized
    fun disconnect(code: Int = 1000, reason: String? = null) {
        val ws = webSocket
        webSocket = null
        // close() returns false when the socket already failed or is closing; drop it then.
        if (ws != null && !ws.close(code, reason)) ws.cancel()
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    /**
     * Drops the socket at once, without the closing handshake (the network it was bound to
     * is gone, or it is being replaced), and emits [Event.Closed] immediately so the session
     * ends now rather than when a close frame or a timeout would arrive.
     */
    @Synchronized
    fun abort(reason: String) {
        val ws = webSocket ?: return
        webSocket = null
        ws.cancel()
        _events.trySend(Event.Closed(CODE_ABORTED, reason))
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    companion object {
        /** Close code reported for [abort] (application range 4000–4999). */
        const val CODE_ABORTED = 4000

        /** Client for feed sockets: no read timeout (frames are sparse), protocol ping every 30 s. */
        val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)
                .pingInterval(30, TimeUnit.SECONDS)
                .build()
        }
    }
}
