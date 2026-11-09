package com.github.mytv.myearthquakealert.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import com.github.mytv.myearthquakealert.MyEarthQuakeAlertApp
import com.github.mytv.myearthquakealert.domain.ReportTime
import com.github.mytv.myearthquakealert.service.DeliveryRequirement
import com.github.mytv.myearthquakealert.service.EewHistory
import com.github.mytv.myearthquakealert.service.EewRecord
import com.github.mytv.myearthquakealert.service.MonitorPhase
import com.github.mytv.myearthquakealert.service.MonitorStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/**
 * Keeps every [StatusWidget] current while the process lives (it does whenever monitoring is
 * on): redraws on phase changes, new or updated EewHistory records, the settings the widget
 * shows, when a relative time label ("3 分钟前") would change, and once at process start (also
 * after a reboot). Redraws are throttled to one per [THROTTLE_MS]; nothing happens without a
 * placed widget. Started after the first unlock (Glance keeps its state in
 * credential-encrypted storage).
 */
object WidgetUpdater {
    private const val TAG = "WidgetUpdater"
    private const val THROTTLE_MS = 2_000L
    private const val MIN_TICK_MS = 30_000L

    private val requests = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    @Volatile private var started = false

    /** What a phase change means to the widget (no heartbeats, no latency figures). */
    private data class StatusKey(
        val phase: MonitorPhase,
        val pauseUntil: Long,
        val connected: Int,
        val missing: Set<DeliveryRequirement>,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start(app: MyEarthQuakeAlertApp) {
        if (started) return
        started = true
        val records = EewHistory.records.map { it.take(WidgetModel.MAX_QUAKES) }.distinctUntilChanged()
        val triggers: Flow<Any> = merge(
            MonitorStatus.state.map { StatusKey(it.phase, it.pauseUntil, it.connectedCount, it.missing) }.distinctUntilChanged(),
            records,
            app.settingsRepository.settings
                .map { listOf(it.serviceEnabled, it.pauseUntil, it.themeMode, it.enabledSources) }
                .distinctUntilChanged(),
            records.flatMapLatest { ticks(it) },
            requests,
        )
        app.appScope.launch(Dispatchers.Default) {
            triggers.conflate().collect {
                if (hasWidgets(app)) {
                    try {
                        StatusWidget().updateAll(app)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "widget update failed", e)
                    }
                    delay(THROTTLE_MS)
                }
            }
        }
    }

    /** Asks for a redraw soon (after a widget action). No-op before [start]. */
    fun requestUpdate() {
        requests.tryEmit(Unit)
    }

    /** Emits whenever one of the shown relative times changes its label. */
    private fun ticks(shown: List<EewRecord>): Flow<Unit> {
        if (shown.isEmpty()) return emptyFlow()
        val times = shown.map { ReportTime.parseCst(it.event.originTime) ?: it.firstReceivedAt }
        return flow {
            while (true) {
                val now = System.currentTimeMillis()
                val next = times.minOf { RelativeTime.untilChange(now - it) }
                delay(next.coerceAtLeast(MIN_TICK_MS))
                emit(Unit)
            }
        }
    }

    private fun hasWidgets(context: Context): Boolean = try {
        AppWidgetManager.getInstance(context)
            ?.getAppWidgetIds(ComponentName(context, StatusWidgetReceiver::class.java))
            ?.isNotEmpty() == true
    } catch (e: Exception) {
        false
    }
}
