package com.github.mytv.myearthquakealert.service

import android.content.Context
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.data.model.EewEvent
import com.github.mytv.myearthquakealert.data.source.EewFrame
import com.github.mytv.myearthquakealert.data.source.EewParser
import com.github.mytv.myearthquakealert.data.source.EewSource
import com.github.mytv.myearthquakealert.data.source.FallbackPoller
import com.github.mytv.myearthquakealert.data.websocket.EewWebSocketClient
import com.github.mytv.myearthquakealert.diagnostics.ClockSync
import com.github.mytv.myearthquakealert.diagnostics.Diagnostics
import com.github.mytv.myearthquakealert.diagnostics.HealthLevel
import com.github.mytv.myearthquakealert.diagnostics.HealthRecorder
import com.github.mytv.myearthquakealert.diagnostics.LogTags
import com.github.mytv.myearthquakealert.domain.ReportTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The link to one subscribed feed (design §3 保活与恢复机制):
 *
 * - One WebSocket session at a time. Any frame (report, feed heartbeat, pong to our text
 *   `ping` every 30 s) proves liveness; none for [MonitorRules.LIVENESS_TIMEOUT_MS] means
 *   a dead "connected" socket, which is closed and replaced.
 * - Failed or dropped sessions retry after 2/4/8/16/30 s; the counter resets once a session
 *   delivers its first frame. [reconnectNow] skips the wait (new network).
 * - After [MonitorRules.FALLBACK_AFTER_FAILURES] consecutive failures the feed is also polled
 *   over HTTP every 10 s ([LinkState.POLLING] while that works) until the socket is back.
 *
 * All state is confined to the controller's main-thread [scope].
 */
internal class SourceLink(
    val source: EewSource,
    private val context: Context,
    private val scope: CoroutineScope,
    private val listener: Listener,
    private val newClient: () -> EewWebSocketClient = { EewWebSocketClient() },
    private val poller: FallbackPoller = FallbackPoller(source),
) {
    interface Listener {
        fun onLinkChanged(link: SourceLink)

        /**
         * A report this source had not delivered before. [latencyMs] is receive time minus
         * ReportTime on the corrected clock; [fresh] is false for history (older than
         * [MonitorRules.STALE_REPORT_MS], e.g. the first HTTP poll).
         */
        fun onReport(link: SourceLink, event: EewEvent, latencyMs: Long?, fresh: Boolean, parseMs: Long, viaHttp: Boolean)
    }

    var health: SourceHealth = SourceHealth(source)
        private set

    private var loopJob: Job? = null
    private var pollJob: Job? = null
    private var client: EewWebSocketClient? = null
    private val wake = Channel<Unit>(Channel.CONFLATED)

    private var running = false
    private var wsConnected = false
    private var pollOk = false
    private var everFailed = false
    private var attempt = 0
    private var failures = 0
    private var skipNextBackoff = false
    private var lastWsFrameAt = 0L
    private var invalidLogged = false

    val isConnected: Boolean get() = wsConnected

    fun start() {
        if (running) return
        running = true
        loopJob = scope.launch { loop() }
        // Fill 最近报文 right away (the socket only pushes new reports); a fresh one is processed.
        scope.launch {
            val result = poller.fetchOnce()
            if (result is FallbackPoller.Result.Ok && running) handleText(result.body, result.receivedAt, viaHttp = true, liveness = false)
        }
        publish()
    }

    fun stop() {
        running = false
        loopJob?.cancel()
        loopJob = null
        stopPolling(logIt = false)
        client?.disconnect(1000, "stop")
        client = null
        wsConnected = false
        health = health.copy(link = LinkState.DISCONNECTED, nextRetryAt = null)
        listener.onLinkChanged(this)
    }

    /**
     * Retries now with the backoff reset. With [force], an open socket is also replaced
     * (default network changed: the old socket is bound to the previous network).
     */
    fun reconnectNow(force: Boolean, reason: String) {
        if (!running) return
        attempt = 0
        if (force && client != null) {
            skipNextBackoff = true
            closeSession(reason)
        }
        wake.trySend(Unit)
    }

    /** The default network is gone: the socket cannot survive, close it now. */
    fun onNetworkLost(reason: String) {
        if (running && client != null) closeSession(reason)
    }

    private var closeReason: String? = null

    private fun closeSession(reason: String) {
        closeReason = reason
        client?.abort(reason)
    }

    private suspend fun loop() {
        while (scope.isActive && running) {
            val outcome = session()
            if (!running) return
            failures++
            attempt++
            everFailed = true
            val reason = closeReason ?: outcome.reason
            closeReason = null
            Diagnostics.log(
                if (outcome.receivedFrame) HealthLevel.WARN else HealthLevel.ERROR,
                LogTags.DISCONNECTED,
                context.getString(R.string.svc_log_disconnected, source.typeKey, reason),
            )
            HealthRecorder.recordDisconnect(source.name, reason)
            if (failures >= MonitorRules.FALLBACK_AFTER_FAILURES) startPolling()

            val delayMs = if (skipNextBackoff) 0L else MonitorRules.backoffMs(attempt)
            skipNextBackoff = false
            health = health.copy(
                reconnectAttempt = attempt,
                consecutiveFailures = failures,
                nextRetryAt = System.currentTimeMillis() + delayMs,
            )
            publish()
            Diagnostics.log(
                HealthLevel.WARN, LogTags.RECONNECT,
                context.getString(R.string.svc_log_reconnect, source.typeKey, attempt, (delayMs / 1000).toInt()),
            )
            HealthRecorder.recordReconnect(source.name, attempt, delayMs)
            if (delayMs > 0) {
                // Woken early by reconnectNow() (network back / forced).
                withTimeoutOrNull(delayMs) { wake.receive() }
            } else {
                wake.tryReceive()
            }
            health = health.copy(nextRetryAt = null)
        }
    }

    private data class Outcome(val receivedFrame: Boolean, val reason: String)

    private suspend fun session(): Outcome {
        val c = newClient()
        client = c
        publish()
        val startedAt = System.currentTimeMillis()
        c.connect(source)
        var opened = false
        var handshakeMs = 0L
        var tls: String? = null
        var gotFrame = false
        var lastFrame = startedAt
        var pingJob: Job? = null
        try {
            while (true) {
                val deadline = if (opened) lastFrame + MonitorRules.LIVENESS_TIMEOUT_MS else startedAt + MonitorRules.HANDSHAKE_TIMEOUT_MS
                val wait = deadline - System.currentTimeMillis()
                val event = if (wait <= 0) null else withTimeoutOrNull(wait) { c.events.receive() }
                if (event == null) {
                    val reason = if (opened) context.getString(R.string.svc_reason_heartbeat_timeout) else context.getString(R.string.svc_reason_handshake_timeout)
                    return Outcome(gotFrame, reason)
                }
                when (event) {
                    is EewWebSocketClient.Event.Opened -> {
                        opened = true
                        handshakeMs = event.handshakeMs
                        tls = event.tls
                        lastFrame = System.currentTimeMillis()
                        c.send("ping")
                        pingJob = scope.launch {
                            while (isActive) {
                                delay(MonitorRules.APP_PING_INTERVAL_MS)
                                c.send("ping")
                            }
                        }
                    }
                    is EewWebSocketClient.Event.Message -> {
                        lastFrame = event.receivedAt
                        if (!gotFrame) {
                            gotFrame = true
                            onConnected(handshakeMs, tls)
                        }
                        handleText(event.text, event.receivedAt, viaHttp = false, liveness = true)
                    }
                    is EewWebSocketClient.Event.Closed -> return Outcome(gotFrame, "${event.code} ${event.reason}".trim())
                    is EewWebSocketClient.Event.Failure -> return Outcome(gotFrame, event.message)
                }
            }
        } finally {
            pingJob?.cancel()
            c.disconnect()
            if (client === c) client = null
            wsConnected = false
            publish()
        }
    }

    private fun onConnected(handshakeMs: Long, tls: String?) {
        wsConnected = true
        attempt = 0
        failures = 0
        health = health.copy(reconnectAttempt = 0, consecutiveFailures = 0, nextRetryAt = null)
        Diagnostics.log(
            HealthLevel.OK, LogTags.CONNECTED,
            context.getString(R.string.svc_log_connected, source.typeKey, tls ?: "TLS", handshakeMs),
        )
        HealthRecorder.recordConnect(source.name, handshakeMs)
        stopPolling(logIt = true)
        publish()
    }

    private fun handleText(text: String, receivedAt: Long, viaHttp: Boolean, liveness: Boolean) {
        if (!running) return
        if (liveness) {
            if (!viaHttp) {
                val gap = receivedAt - lastWsFrameAt
                // Frames closer than 1 s (pong right after a heartbeat) are one beat.
                if (lastWsFrameAt > 0 && gap >= 1_000L) HealthRecorder.recordHeartbeatGap((gap / 1000).toInt())
                lastWsFrameAt = receivedAt
            }
            health = health.copy(lastFrameAt = receivedAt)
        }
        val t0 = System.nanoTime()
        val frame = EewParser.parse(text, source)
        val parseMs = (System.nanoTime() - t0 + 500_000) / 1_000_000
        when (frame) {
            is EewFrame.Report -> onReportFrame(frame.event, receivedAt, parseMs, viaHttp)
            is EewFrame.Invalid -> if (!invalidLogged) {
                invalidLogged = true
                Diagnostics.log(HealthLevel.WARN, LogTags.ERROR, "${source.typeKey}: ${frame.reason}")
            }
            is EewFrame.Heartbeat, is EewFrame.Pong, is EewFrame.Ignored -> Unit
        }
        if (liveness) publish()
    }

    private fun onReportFrame(event: EewEvent, receivedAt: Long, parseMs: Long, viaHttp: Boolean) {
        val last = health.lastReport
        if (last != null && last.eventId == event.eventId && last.reportNum >= event.reportNum &&
            !(event.isFinal && !last.isFinal)
        ) return // same report again (every poll returns the newest one)
        val receivedCorrected = ClockSync.now() - (System.currentTimeMillis() - receivedAt)
        val reportAt = ReportTime.parseCst(event.reportTime)
        val latency = reportAt?.let { (receivedCorrected - it).coerceAtLeast(0L) }
        val fresh = !MonitorRules.isStale(reportAt, receivedCorrected)
        health = health.copy(
            lastReport = event,
            lastReportAt = receivedAt,
            lastLatencyMs = if (fresh) latency else health.lastLatencyMs,
        )
        listener.onReport(this, event, latency, fresh, parseMs, viaHttp)
        publish()
    }

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        Diagnostics.log(
            HealthLevel.WARN, LogTags.FALLBACK,
            context.getString(R.string.svc_log_fallback_on, source.typeKey, failures, (FallbackPoller.INTERVAL_MS / 1000).toInt()),
        )
        HealthRecorder.recordFallback(source.name, true)
        pollJob = scope.launch {
            while (isActive && running) {
                when (val result = poller.fetchOnce()) {
                    is FallbackPoller.Result.Ok -> {
                        pollOk = true
                        if (!wsConnected) handleText(result.body, result.receivedAt, viaHttp = true, liveness = true)
                    }
                    is FallbackPoller.Result.Error -> pollOk = false
                }
                publish()
                delay(FallbackPoller.INTERVAL_MS)
            }
        }
    }

    private fun stopPolling(logIt: Boolean) {
        val job = pollJob ?: return
        pollJob = null
        job.cancel()
        pollOk = false
        if (logIt) {
            Diagnostics.log(HealthLevel.OK, LogTags.FALLBACK, context.getString(R.string.svc_log_fallback_off, source.typeKey))
            HealthRecorder.recordFallback(source.name, false)
        }
    }

    private fun computeLink(): LinkState = when {
        !running -> LinkState.DISCONNECTED
        wsConnected -> LinkState.CONNECTED
        pollJob?.isActive == true && pollOk -> LinkState.POLLING
        everFailed -> LinkState.RECONNECTING
        else -> LinkState.CONNECTING
    }

    private fun publish() {
        val link = computeLink()
        if (health.link != link) health = health.copy(link = link)
        listener.onLinkChanged(this)
    }
}
