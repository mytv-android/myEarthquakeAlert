package com.github.mytv.myearthquakealert.diagnostics

/**
 * Contracts for the diagnostics page (design §5 监控与诊断). The service side
 * (HealthRecorder, ClockSync, SelfTestRunner) produces these; the UI only renders them.
 */

enum class HealthLevel { OK, WARN, ERROR, INFO }

/** One line of the event log (等宽日志). [tag] is a short upper-case code, e.g. CONNECTED, BOOT. */
data class LogEntry(
    val time: Long,
    val level: HealthLevel,
    val tag: String,
    val message: String,
)

/** One 15-minute cell of the 24 h availability bar. */
enum class SlotStatus { UP, DEGRADED, DOWN, OFF }

data class AvailabilitySlot(val start: Long, val status: SlotStatus)

/** Thresholds follow the 指标定义与阈值 table. */
data class HealthMetrics(
    val availability24h: Double? = null,          // 0..1, connected time / monitoring-on time
    val reconnects24h: Int = 0,
    val longestOutageMs: Long = 0L,
    val heartbeatAgeMs: Long? = null,              // now - last frame on any source
    val heartbeatSamplesSec: List<Int> = emptyList(), // recent heartbeat gaps, for the sparkline
    val latencyP50Ms: Long? = null,
    val latencyP95Ms: Long? = null,
    val reportsToday: Int = 0,
    val clockOffsetMs: Long? = null,               // local clock - ntp.json
    val clockCheckedAt: Long? = null,
    val locationSource: String? = null,            // gps / network / manual / ip / none
    val locationAccuracyM: Float? = null,
    val locationAgeMs: Long? = null,
    val batteryOptimizationExempt: Boolean? = null,
    val standbyBucket: String? = null,             // ACTIVE / WORKING_SET / FREQUENT / RARE / RESTRICTED
    val dataSaverOn: Boolean? = null,
    val watchdogLastRunAt: Long? = null,
    val watchdogNextRunAt: Long? = null,
    val bootStartDelaySec: Long? = null,
    val pipelineParseMs: Long? = null,
    val pipelineEvaluateMs: Long? = null,
    val endToEndMs: Long? = null,
)

/** The 8 steps of 端到端自检, in order. */
enum class SelfTestStep {
    NETWORK,        // 网络可达
    SOURCES,        // 数据源连接
    CLOCK,          // 时钟偏差
    LOCATION,       // 定位
    PERMISSIONS,    // 通知、悬浮窗、全屏通知
    SYSTEM_LIMITS,  // 电池优化、待机分组
    PIPELINE,       // 注入测试报文走完解析 → 评估
    PRESENTATION,   // 预警呈现通道（静默）
}

enum class StepStatus { PENDING, RUNNING, PASS, WARN, FAIL }

/** What tapping the fix entry of a failed step opens. */
enum class FixAction { NOTIFICATION_SETTINGS, OVERLAY_SETTINGS, FULL_SCREEN_SETTINGS, LOCATION_PERMISSION, BATTERY_OPTIMIZATION, AUTO_START, NETWORK_SETTINGS, DATE_SETTINGS }

data class SelfTestStepResult(
    val step: SelfTestStep,
    val status: StepStatus = StepStatus.PENDING,
    val detail: String = "",
    val fix: FixAction? = null,
)

data class SelfTestRun(
    val startedAt: Long,
    val finishedAt: Long? = null,
    val steps: List<SelfTestStepResult> = SelfTestStep.entries.map { SelfTestStepResult(it) },
) {
    val running: Boolean get() = finishedAt == null
}
