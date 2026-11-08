package com.github.mytv.myearthquakealert.service

import com.github.mytv.myearthquakealert.data.model.EewEvent
import com.github.mytv.myearthquakealert.data.source.EewSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Monitoring state machine phases (design §3 服务状态机). Every surface — overview card,
 * ongoing notification, quick-settings tile, widget, watch, TV — renders the same phase
 * with the same shape, colour and words:
 *
 * | phase        | shape (MaterialShapes) | colour | title            |
 * |--------------|------------------------|--------|------------------|
 * | OFF          | Square (squircle)      | off    | 后台监测已关闭     |
 * | CONNECTING   | Cookie9Sided + loading | primary| 正在连接…          |
 * | MONITORING   | Cookie9Sided, spinning | ok     | 后台监测中         |
 * | RECONNECTING | Clover4Leaf + loading  | warn   | 重连中             |
 * | DEGRADED     | Gem                    | warn   | 备用通道           |
 * | LIMITED      | Triangle               | error  | 受限               |
 * | PAUSED       | Pill                   | warn   | 已暂停至 HH:mm     |
 */
enum class MonitorPhase { OFF, CONNECTING, MONITORING, RECONNECTING, DEGRADED, LIMITED, PAUSED }

/** Link of one subscribed source. POLLING = HTTP fallback after repeated WebSocket failures. */
enum class LinkState { DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING, POLLING }

/** Why the service was last started; shown in 设置 › 后台监测 › 最近启动 and diagnostics. */
enum class StartOrigin { USER, BOOT, PACKAGE_REPLACED, WATCHDOG, STICKY_RESTART, PAUSE_ENDED, TILE, NOTIFICATION, WATCH }

data class SourceHealth(
    val source: EewSource,
    val link: LinkState = LinkState.DISCONNECTED,
    /** Last frame of any kind (report, heartbeat, pong) — epoch millis. */
    val lastFrameAt: Long? = null,
    /** Last parsed earthquake report from this source. */
    val lastReport: EewEvent? = null,
    val lastReportAt: Long? = null,
    /** Receive time minus ReportTime (clock-corrected) of the last report. */
    val lastLatencyMs: Long? = null,
    val reconnectAttempt: Int = 0,
    val nextRetryAt: Long? = null,
    val consecutiveFailures: Int = 0,
)

/** A permission or system setting that affects delivery (design §3 开启前检查). */
enum class DeliveryRequirement(val required: Boolean) {
    NOTIFICATIONS(true),
    OVERLAY(true),
    FULL_SCREEN_INTENT(false),
    LOCATION(false),
    BATTERY_OPTIMIZATION(false),
    DND_ACCESS(false),
    AUTO_START(false),
}

data class MonitorState(
    val phase: MonitorPhase = MonitorPhase.OFF,
    /** When [phase] was entered — epoch millis. */
    val phaseSince: Long = 0L,
    /** When monitoring was switched on (uptime "已运行 3 小时 12 分"); null when off. */
    val runningSince: Long? = null,
    val pauseUntil: Long = 0L,
    val sources: List<SourceHealth> = emptyList(),
    /** Unmet delivery requirements; any `required` one puts the phase in LIMITED. */
    val missing: Set<DeliveryRequirement> = emptySet(),
    /** Newest report seen on any source (after de-duplication). */
    val latestReport: EewEvent? = null,
    val startOrigin: StartOrigin? = null,
    /** Process the detection runs in; the UI never detects. */
    val detectorProcessStartedAt: Long? = null,
) {
    val connectedCount: Int get() = sources.count { it.link == LinkState.CONNECTED || it.link == LinkState.POLLING }
    val isActive: Boolean get() = phase != MonitorPhase.OFF && phase != MonitorPhase.PAUSED
}

/**
 * Process-wide, read-only view of the monitoring service for every UI surface.
 * Only [EewMonitorService] / MonitorController writes it; UIs collect [state] and send
 * commands through [EewMonitorService]'s companion helpers.
 */
object MonitorStatus {
    private val _state = MutableStateFlow(MonitorState())
    val state: StateFlow<MonitorState> = _state.asStateFlow()

    internal fun update(transform: (MonitorState) -> MonitorState) = _state.update(transform)
}
