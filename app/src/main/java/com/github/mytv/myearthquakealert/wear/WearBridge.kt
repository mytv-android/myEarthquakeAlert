package com.github.mytv.myearthquakealert.wear

import android.content.Context
import android.util.Log
import com.github.mytv.myearthquakealert.MyEarthQuakeAlertApp
import com.github.mytv.myearthquakealert.service.ActiveAlertHolder
import com.github.mytv.myearthquakealert.service.AlertData
import com.github.mytv.myearthquakealert.service.EewHistory
import com.github.mytv.myearthquakealert.service.MonitorStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

/**
 * Phone → watch forwarding (design §6 手表: 默认由手机转发预警，省电). Started once from the
 * Application; collects the read-only state the service publishes and mirrors it to the
 * Wearable Data Layer:
 *
 * - [MonitorStatus.state] + thresholds + location → DataItem [WearPaths.STATUS]
 * - [EewHistory.records] (latest [WearMapping.QUAKE_LIMIT]) → DataItem [WearPaths.QUAKES]
 * - [ActiveAlertHolder.activeAlert] → message [WearPaths.ALERT] to every watch (lowest
 *   latency) plus a DataItem a watch connecting later still sees; when it ends, message
 *   [WearPaths.ALERT_END] and the DataItem is deleted.
 *
 * Status and quakes go out only on a meaningful change ([WearMapping]) and at most every
 * [MIN_INTERVAL_MS]; alerts are never throttled. Without Google Play services (or the Wear
 * OS companion) the bridge does nothing; no call can crash or block the phone app.
 */
object WearBridge {

    private const val TAG = "WearBridge"
    private const val MIN_INTERVAL_MS = 2_000L
    /** The location is read from the cache; re-read now and then to follow moves. */
    private const val LOCATION_REFRESH_MS = 10 * 60_000L

    @Volatile private var started = false
    @Volatile private var layer: WearDataLayer? = null

    fun start(app: MyEarthQuakeAlertApp) {
        if (started) return
        started = true
        val dataLayer = try {
            WearDataLayer(app)
        } catch (e: Exception) {
            Log.w(TAG, "Wearable bridge disabled", e)
            return
        }
        if (dataLayer.unavailable) {
            Log.i(TAG, "No Google Play services: watch bridge off")
            return
        }
        layer = dataLayer
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        scope.launch {
            val thresholds: Flow<WearThresholds> = app.settingsRepository.settings
                .map { WearThresholds(it.actionMinMagnitude, it.actionMinIntensity, it.intenseThreshold) }
                .catch { e ->
                    Log.w(TAG, "settings unreadable", e)
                    emit(WearThresholds())
                }
            combine(MonitorStatus.state, thresholds, ticker(LOCATION_REFRESH_MS)) { state, t, _ ->
                WearMapping.status(state, t, cachedLocation(app))
            }
                .distinctUntilChanged()
                .conflate()
                .collect { status ->
                    if (dataLayer.unavailable) return@collect
                    dataLayer.put(WearPaths.STATUS, WearCodec.encode(status.copy(updatedAt = System.currentTimeMillis())))
                    delay(MIN_INTERVAL_MS)
                }
        }

        scope.launch {
            EewHistory.records
                .map { WearMapping.quakes(it) }
                .distinctUntilChanged()
                .conflate()
                .collect { quakes ->
                    if (dataLayer.unavailable) return@collect
                    dataLayer.put(WearPaths.QUAKES, WearCodec.encode(quakes.copy(updatedAt = System.currentTimeMillis())))
                    delay(MIN_INTERVAL_MS)
                }
        }

        scope.launch {
            var shown: AlertData? = null
            var initial = true
            ActiveAlertHolder.activeAlert
                .distinctUntilChangedBy { it?.let(WearMapping::alertKey) }
                .collect { alert ->
                    if (dataLayer.unavailable) return@collect
                    val previous = shown
                    shown = alert
                    if (initial && alert == null) {
                        // A stale DataItem left by a process that died mid-alert must not
                        // alert a watch that connects later.
                        dataLayer.delete(WearPaths.ALERT)
                    }
                    initial = false
                    if (alert != null) {
                        val payload = WearCodec.encode(WearMapping.alert(alert, System.currentTimeMillis()))
                        val delivered = dataLayer.broadcast(WearPaths.ALERT, payload)
                        dataLayer.put(WearPaths.ALERT, payload)
                        Log.i(TAG, "alert ${alert.event.eventId} #${alert.event.reportNum} → $delivered watch(es)")
                    } else if (previous != null) {
                        val end = WireAlertEnd(eventId = previous.event.eventId, sentAt = System.currentTimeMillis())
                        dataLayer.broadcast(WearPaths.ALERT_END, WearCodec.encode(end))
                        dataLayer.delete(WearPaths.ALERT)
                    }
                }
        }
    }

    /** Sends a command outcome back to the watch that asked (from [WearCommandService]). */
    internal fun reply(app: Context, nodeId: String, result: WireCommandResult) {
        val dataLayer = layer ?: WearDataLayer(app)
        dataLayer.send(nodeId, WearPaths.CMD_RESULT, WearCodec.encode(result))
    }

    private fun cachedLocation(app: MyEarthQuakeAlertApp) = try {
        app.locationProvider.cached()
    } catch (e: Exception) {
        null
    }

    private fun ticker(periodMs: Long): Flow<Unit> = flow {
        while (true) {
            delay(periodMs)
            emit(Unit)
        }
    }.onStart { emit(Unit) }
}
