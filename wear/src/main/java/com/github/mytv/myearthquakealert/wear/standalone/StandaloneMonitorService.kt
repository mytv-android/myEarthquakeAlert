package com.github.mytv.myearthquakealert.wear.standalone

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.github.mytv.myearthquakealert.wear.R
import com.github.mytv.myearthquakealert.wear.alert.WatchAlerts
import com.github.mytv.myearthquakealert.wear.data.PhoneLink
import com.github.mytv.myearthquakealert.wear.data.StandaloneLink
import com.github.mytv.myearthquakealert.wear.data.WatchSettings
import com.github.mytv.myearthquakealert.wear.data.WatchStore
import com.github.mytv.myearthquakealert.wear.domain.ReportTime
import com.github.mytv.myearthquakealert.wear.domain.StandaloneEvaluator
import com.github.mytv.myearthquakealert.wear.source.EewEvent
import com.github.mytv.myearthquakealert.wear.source.EewFrame
import com.github.mytv.myearthquakealert.wear.source.EewParser
import com.github.mytv.myearthquakealert.wear.source.EewSource
import com.github.mytv.myearthquakealert.wear.ui.MainActivity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * 独立监测 (design §6 手表: 手机不在身边时可独立连接数据源). Runs as a foreground service
 * while the setting is on. It idles while a phone with the app is reachable (the phone
 * forwards alerts, which saves the watch battery) and connects one Wolfx WebSocket only
 * while the phone is out of reach; when the phone comes back, the socket closes again.
 * Reports are evaluated on the watch ([StandaloneEvaluator]) with the location from
 * [WatchLocation].
 */
class StandaloneMonitorService : LifecycleService() {

    private sealed interface WsEvent {
        data object Opened : WsEvent
        data class Text(val text: String, val receivedAt: Long) : WsEvent
        data class Closed(val reason: String) : WsEvent
    }

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }

    /** Local clock minus Wolfx server clock (from heartbeat timestamps). */
    @Volatile private var skewMs = 0L
    private val seen = LinkedHashMap<String, Pair<Int, Boolean>>()

    override fun onCreate() {
        super.onCreate()
        if (!enterForeground()) {
            stopSelf()
            return
        }
        WatchStore.updateStandalone { it.copy(enabled = true, running = true, link = StandaloneLink.IDLE) }

        lifecycleScope.launch {
            combine(WatchSettings.flow(this@StandaloneMonitorService), WatchStore.phoneConnected) { s, phone -> s to phone }
                .distinctUntilChanged()
                .collectLatest { (settings, phone) ->
                    if (!settings.standaloneEnabled) {
                        stopSelf()
                        return@collectLatest
                    }
                    WatchStore.updateStandalone { it.copy(enabled = true, source = settings.standaloneSource) }
                    if (phone == false) {
                        monitor(settings.standaloneSource)
                    } else {
                        WatchStore.updateStandalone { it.copy(link = StandaloneLink.IDLE) }
                    }
                }
        }

        lifecycleScope.launch {
            WatchStore.standalone.collect { updateNotification() }
        }

        // Capability changes normally arrive by themselves; this is the safety net.
        lifecycleScope.launch {
            while (isActive) {
                PhoneLink.refreshConnection(this@StandaloneMonitorService)
                delay(if (WatchStore.phoneConnected.value == false) PROBE_AWAY_MS else PROBE_NEAR_MS)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onDestroy() {
        WatchStore.updateStandalone { it.copy(running = false, link = StandaloneLink.IDLE) }
        super.onDestroy()
    }

    private fun enterForeground(): Boolean = try {
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
        true
    } catch (e: Exception) {
        Log.w(TAG, "foreground start refused", e)
        false
    }

    private fun updateNotification() {
        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification())
        } catch (e: SecurityException) {
            // Notifications denied: the service still runs; the system shows its own chip.
        }
    }

    private fun buildNotification(): Notification {
        val state = WatchStore.standalone.value
        val text = when (state.link) {
            StandaloneLink.IDLE -> getString(R.string.standalone_idle)
            StandaloneLink.CONNECTING -> getString(R.string.standalone_connecting, state.source.label)
            StandaloneLink.CONNECTED -> getString(R.string.standalone_connected, state.source.label)
            StandaloneLink.RETRYING -> getString(R.string.standalone_retrying, state.source.label)
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, WatchAlerts.CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.ic_sensors)
            .setContentTitle(getString(R.string.standalone_title))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /** Keeps one WebSocket to [source] open, reconnecting with backoff, until cancelled. */
    private suspend fun monitor(source: EewSource) {
        var attempt = 0
        while (coroutineContext.isActive) {
            WatchStore.updateStandalone {
                it.copy(
                    link = if (attempt == 0) StandaloneLink.CONNECTING else StandaloneLink.RETRYING,
                    hasLocation = WatchLocation.current(this) != null,
                )
            }
            val gotFrame = session(source)
            attempt = if (gotFrame) 1 else attempt + 1
            WatchStore.updateStandalone { it.copy(link = StandaloneLink.RETRYING) }
            delay(BACKOFF_MS[(attempt - 1).coerceIn(0, BACKOFF_MS.lastIndex)])
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun session(source: EewSource): Boolean {
        val events = Channel<WsEvent>(Channel.UNLIMITED)
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                events.trySend(WsEvent.Opened)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                events.trySend(WsEvent.Text(text, System.currentTimeMillis()))
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                events.trySend(WsEvent.Closed("$code $reason"))
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                events.trySend(WsEvent.Closed(t.message ?: t.javaClass.simpleName))
            }
        }
        val socket = client.newWebSocket(Request.Builder().url(source.wsUrl).build(), listener)
        val startedAt = System.currentTimeMillis()
        var opened = false
        var gotFrame = false
        var lastFrame = startedAt
        var lastPing = startedAt
        try {
            while (true) {
                val now = System.currentTimeMillis()
                if (opened && now - lastPing >= PING_INTERVAL_MS) {
                    socket.send("ping")
                    lastPing = now
                }
                val deadline = if (opened) lastFrame + LIVENESS_TIMEOUT_MS else startedAt + HANDSHAKE_TIMEOUT_MS
                val wait = minOf(deadline - now, if (opened) lastPing + PING_INTERVAL_MS - now else Long.MAX_VALUE)
                if (deadline - now <= 0) {
                    Log.i(TAG, "${source.typeKey}: ${if (opened) "no frame" else "handshake"} timeout")
                    return gotFrame
                }
                val event = withTimeoutOrNull(wait.coerceAtLeast(1L)) { events.receive() } ?: continue
                when (event) {
                    WsEvent.Opened -> {
                        opened = true
                        lastFrame = System.currentTimeMillis()
                        lastPing = lastFrame
                        socket.send("ping")
                    }
                    is WsEvent.Text -> {
                        lastFrame = event.receivedAt
                        gotFrame = true
                        onText(event.text, event.receivedAt, source)
                    }
                    is WsEvent.Closed -> {
                        Log.i(TAG, "${source.typeKey} closed: ${event.reason}")
                        return gotFrame
                    }
                }
            }
        } finally {
            socket.cancel()
            events.close()
        }
    }

    private fun onText(text: String, receivedAt: Long, source: EewSource) {
        WatchStore.updateStandalone { it.copy(link = StandaloneLink.CONNECTED, lastFrameAt = receivedAt) }
        when (val frame = EewParser.parse(text, source)) {
            is EewFrame.Report -> onReport(frame.event, receivedAt)
            is EewFrame.Heartbeat -> frame.serverTime?.let { updateSkew(it, receivedAt) }
            is EewFrame.Pong -> frame.serverTime?.let { updateSkew(it, receivedAt) }
            is EewFrame.Ignored, is EewFrame.Invalid -> Unit
        }
    }

    private fun updateSkew(serverTime: Long, receivedAt: Long) {
        val serverMs = if (serverTime < 100_000_000_000L) serverTime * 1000 else serverTime
        skewMs = receivedAt - serverMs
    }

    private fun onReport(event: EewEvent, receivedAt: Long) {
        val last = seen[event.eventId]
        if (last != null && last.first >= event.reportNum && !(event.isFinal && !last.second)) return
        seen[event.eventId] = event.reportNum to event.isFinal
        while (seen.size > 50) seen.remove(seen.keys.first())

        val origin = ReportTime.parseCst(event.originTime)?.plus(skewMs) ?: receivedAt
        val reportAt = ReportTime.parseCst(event.reportTime)?.plus(skewMs)
        val location = WatchLocation.current(this)
        WatchStore.updateStandalone { it.copy(hasLocation = location != null) }
        val result = StandaloneEvaluator.evaluate(
            event = event,
            location = location,
            thresholds = StandaloneEvaluator.Thresholds.of(WatchStore.status.value),
            originAt = origin,
            receivedAt = receivedAt,
            reportAt = reportAt,
            now = System.currentTimeMillis(),
            previous = WatchStore.alert.value,
        )
        Log.i(TAG, "report ${event.eventId} #${event.reportNum} M${event.magnitude} → ${result.tier}")
        WatchStore.addQuake(result.quake)
        result.alert?.let { WatchAlerts.present(this, it) }
    }

    companion object {
        private const val TAG = "Standalone"
        private const val NOTIFICATION_ID = 1
        private const val HANDSHAKE_TIMEOUT_MS = 15_000L
        private const val LIVENESS_TIMEOUT_MS = 90_000L
        private const val PING_INTERVAL_MS = 30_000L
        private const val PROBE_AWAY_MS = 60_000L
        private const val PROBE_NEAR_MS = 5 * 60_000L
        private val BACKOFF_MS = longArrayOf(2_000L, 4_000L, 8_000L, 16_000L, 30_000L)

        /** Starts the service (when 独立监测 is on); false if the system refused the start. */
        fun start(context: Context): Boolean = try {
            ContextCompat.startForegroundService(context, Intent(context, StandaloneMonitorService::class.java))
            true
        } catch (e: Exception) {
            // Background start not allowed (e.g. the app is not visible): retried when it is.
            Log.w(TAG, "standalone start refused", e)
            false
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, StandaloneMonitorService::class.java))
        }
    }
}
