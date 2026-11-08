package com.github.mytv.myearthquakealert.diagnostics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Process-wide diagnostics read by the 诊断 page, settings rows, widget and watch.
 * Written only by the service side (HealthRecorder, ClockSync, SelfTestRunner).
 */
object Diagnostics {
    private const val MAX_LOG = 500

    private val _log = MutableStateFlow<List<LogEntry>>(emptyList())
    /** Newest last. */
    val log: StateFlow<List<LogEntry>> = _log.asStateFlow()

    private val _availability = MutableStateFlow<List<AvailabilitySlot>>(emptyList())
    /** 96 cells covering the last 24 h, oldest first. */
    val availability: StateFlow<List<AvailabilitySlot>> = _availability.asStateFlow()

    private val _metrics = MutableStateFlow(HealthMetrics())
    val metrics: StateFlow<HealthMetrics> = _metrics.asStateFlow()

    private val _selfTest = MutableStateFlow<SelfTestRun?>(null)
    val selfTest: StateFlow<SelfTestRun?> = _selfTest.asStateFlow()

    fun log(level: HealthLevel, tag: String, message: String, time: Long = System.currentTimeMillis()) {
        _log.update { (it + LogEntry(time, level, tag, message)).takeLast(MAX_LOG) }
    }

    internal fun replaceLog(entries: List<LogEntry>) { _log.value = entries.takeLast(MAX_LOG) }
    internal fun setAvailability(slots: List<AvailabilitySlot>) { _availability.value = slots }
    internal fun updateMetrics(transform: (HealthMetrics) -> HealthMetrics) = _metrics.update(transform)
    internal fun setSelfTest(run: SelfTestRun?) { _selfTest.value = run }
}
