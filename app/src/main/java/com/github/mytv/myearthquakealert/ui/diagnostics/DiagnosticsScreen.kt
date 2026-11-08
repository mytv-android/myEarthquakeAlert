package com.github.mytv.myearthquakealert.ui.diagnostics

import android.Manifest
import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.mytv.myearthquakealert.MyEarthQuakeAlertApp
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.data.repository.UserSettings
import com.github.mytv.myearthquakealert.data.source.EewSource
import com.github.mytv.myearthquakealert.diagnostics.AvailabilitySlot
import com.github.mytv.myearthquakealert.diagnostics.Diagnostics
import com.github.mytv.myearthquakealert.diagnostics.DiagnosticsExport
import com.github.mytv.myearthquakealert.diagnostics.HealthMetrics
import com.github.mytv.myearthquakealert.diagnostics.LogEntry
import com.github.mytv.myearthquakealert.diagnostics.SlotStatus
import com.github.mytv.myearthquakealert.service.DeliveryRequirement
import com.github.mytv.myearthquakealert.service.EewMonitorService
import com.github.mytv.myearthquakealert.service.LinkState
import com.github.mytv.myearthquakealert.service.MonitorPhase
import com.github.mytv.myearthquakealert.service.MonitorState
import com.github.mytv.myearthquakealert.service.MonitorStatus
import com.github.mytv.myearthquakealert.service.SourceHealth
import com.github.mytv.myearthquakealert.service.StartOrigin
import com.github.mytv.myearthquakealert.ui.components.SegmentedGroup
import com.github.mytv.myearthquakealert.ui.components.StatusShape
import com.github.mytv.myearthquakealert.ui.components.Subhead
import com.github.mytv.myearthquakealert.ui.settings.components.Formats
import com.github.mytv.myearthquakealert.ui.settings.components.displayPhase
import com.github.mytv.myearthquakealert.ui.settings.components.focusHighlight
import com.github.mytv.myearthquakealert.ui.settings.components.pauseEnd
import com.github.mytv.myearthquakealert.ui.settings.components.rememberFormats
import com.github.mytv.myearthquakealert.ui.settings.components.rememberNow
import com.github.mytv.myearthquakealert.ui.settings.components.startHistory
import com.github.mytv.myearthquakealert.ui.settings.locationSourceLabel
import com.github.mytv.myearthquakealert.ui.theme.MyEarthQuakeAlertTheme
import com.github.mytv.myearthquakealert.ui.theme.status
import java.util.Locale

/** Everything the diagnostics page renders, collected once. */
internal class DiagnosticsData(
    val settings: UserSettings?,
    val monitor: MonitorState,
    val metrics: HealthMetrics,
    val slots: List<AvailabilitySlot>,
    val phase: MonitorPhase,
    val pauseUntil: Long,
    val now: Long,
    val formats: Formats,
) {
    val enabledSources: Set<EewSource> get() = settings?.enabledSources ?: monitor.sources.map { it.source }.toSet()
    val active: Boolean get() = phase != MonitorPhase.OFF && phase != MonitorPhase.PAUSED
}

/**
 * 诊断 destination (design §5 监控与诊断): health overview, data pipeline, sources,
 * metrics, end-to-end self test and the event log.
 *
 * @param compact a one-card summary for side panes (dual-screen right pane, tablet).
 * @param onOpenFull shown as 查看完整诊断 on the compact card when non-null.
 */
@Composable
fun DiagnosticsScreen(modifier: Modifier = Modifier, compact: Boolean = false, onOpenFull: (() -> Unit)? = null) {
    val context = LocalContext.current
    val app = context.applicationContext as MyEarthQuakeAlertApp
    val settings by app.settingsRepository.settings.collectAsStateWithLifecycle<UserSettings?>(initialValue = null)
    val monitor by MonitorStatus.state.collectAsStateWithLifecycle()
    val metrics by Diagnostics.metrics.collectAsStateWithLifecycle()
    val slots by Diagnostics.availability.collectAsStateWithLifecycle()
    val formats = rememberFormats()
    val now = rememberNow()
    val current = settings
    val phase = if (current != null) displayPhase(monitor, current, now) else monitor.phase
    val pauseUntil = if (current != null) pauseEnd(monitor, current) else monitor.pauseUntil
    val data = DiagnosticsData(current, monitor, metrics, slots, phase, pauseUntil, now, formats)

    if (compact) {
        Box(modifier, contentAlignment = Alignment.TopCenter) {
            DiagnosticsSummaryCard(data, onOpenFull, Modifier.fillMaxWidth())
        }
    } else {
        DiagnosticsPage(data, modifier)
    }
}

internal fun exportDiagnostics(context: Context) {
    try {
        DiagnosticsExport.share(context)
    } catch (e: Exception) {
        Log.w("DiagnosticsScreen", "Diagnostics export failed", e)
        Toast.makeText(context, R.string.pg_export_failed, Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DiagnosticsPage(data: DiagnosticsData, modifier: Modifier) {
    val context = LocalContext.current
    val log by Diagnostics.log.collectAsStateWithLifecycle()
    val selfTest by Diagnostics.selfTest.collectAsStateWithLifecycle()
    val updatedAt = remember(data.metrics, data.monitor) { System.currentTimeMillis() }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val formats = data.formats

    Scaffold(
        modifier = modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.pg_diagnostics)) },
                subtitle = { Text(stringResource(R.string.pg_diag_updated, formats.clockSeconds(updatedAt))) },
                actions = {
                    IconButton(onClick = { exportDiagnostics(context) }, modifier = Modifier.focusHighlight(CircleShape)) {
                        Icon(Icons.Filled.IosShare, contentDescription = stringResource(R.string.pg_export))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 880.dp).fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 4.dp,
                    bottom = padding.calculateBottomPadding() + 32.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item(key = "health") { HealthHeader(data) }
                item(key = "pipeline") { PipelineSection(data) }
                item(key = "sources") { SourcesSection(data) }
                item(key = "metrics") { MetricsSection(data, log) }
                item(key = "selftest") {
                    SelfTestSection(
                        run = selfTest,
                        formats = formats,
                        now = data.now,
                        onRun = { if (!EewMonitorService.selfTest(context)) toast(context, R.string.pg_command_failed) },
                        onFix = { action ->
                            if (!openFix(context, action)) {
                                locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                            }
                        },
                    )
                }
                logItems(log, formats, onExport = { exportDiagnostics(context) })
            }
        }
    }
}

internal fun toast(context: Context, message: Int) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

/** Container and content colours of the health header / compact card by phase. */
@Composable
private fun headerColors(phase: MonitorPhase): Pair<Color, Color> = when (phase) {
    MonitorPhase.MONITORING -> MaterialTheme.status.okContainer to MaterialTheme.status.onOkContainer
    MonitorPhase.RECONNECTING, MonitorPhase.DEGRADED -> MaterialTheme.status.warnContainer to MaterialTheme.status.onWarnContainer
    MonitorPhase.LIMITED -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    else -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurface
}

@Composable
private fun healthTitle(phase: MonitorPhase): String = stringResource(
    when (phase) {
        MonitorPhase.MONITORING -> R.string.pg_diag_ok
        MonitorPhase.CONNECTING -> R.string.pg_diag_title_connecting
        MonitorPhase.RECONNECTING -> R.string.pg_diag_title_reconnecting
        MonitorPhase.DEGRADED -> R.string.pg_diag_title_degraded
        MonitorPhase.LIMITED -> R.string.pg_diag_title_limited
        MonitorPhase.PAUSED -> R.string.pg_diag_title_paused
        MonitorPhase.OFF -> R.string.pg_diag_title_off
    }
)

internal fun percent(ratio: Double): String = String.format(Locale.ROOT, "%.1f", ratio * 100)

/** "24 小时可用率 99.6% · 重连 3 次 · 最长中断 41 s". */
@Composable
private fun availabilitySummary(data: DiagnosticsData, includeOutage: Boolean): String {
    val metrics = data.metrics
    val parts = buildList {
        metrics.availability24h?.let { add(stringResource(R.string.pg_diag_availability, percent(it))) }
        add(stringResource(R.string.pg_diag_reconnects, metrics.reconnects24h))
        if (includeOutage && metrics.longestOutageMs > 0) add(stringResource(R.string.pg_diag_longest, data.formats.duration(metrics.longestOutageMs)))
    }
    return parts.joinToString(" · ")
}

@Composable
private fun availabilityLabels(data: DiagnosticsData): List<String> {
    val formats = data.formats
    val start = data.slots.takeLast(AvailabilitySlots).firstOrNull()?.start?.let {
        // Account for cells padded on the left when fewer than 96 were published.
        it - (AvailabilitySlots - minOf(AvailabilitySlots, data.slots.size)) * 15 * 60_000L
    } ?: (data.now - 24 * 3_600_000L)
    val span = data.now - start
    return listOf(
        formats.dayTime(start, data.now, seconds = false),
        formats.clock(start + span / 3),
        formats.clock(start + span * 2 / 3),
        stringResource(R.string.pg_now),
    )
}

/** 健康总览 (prototype `.dh`). */
@Composable
private fun HealthHeader(data: DiagnosticsData) {
    val (container, content) = headerColors(data.phase)
    val formats = data.formats
    val monitor = data.monitor
    val running = data.active
    Surface(shape = RoundedCornerShape(28.dp), color = container, contentColor = content, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                StatusShape(data.phase, size = 52.dp)
                Column(Modifier.weight(1f)) {
                    Text(healthTitle(data.phase), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            running -> availabilitySummary(data, includeOutage = true)
                            data.phase == MonitorPhase.PAUSED && data.pauseUntil > data.now ->
                                stringResource(R.string.pg_diag_paused_until, formats.clockWithDay(data.pauseUntil, data.now))
                            else -> stringResource(R.string.pg_diag_history)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = content.copy(alpha = 0.85f),
                    )
                    val since = buildList {
                        if (monitor.phaseSince > 0L && monitor.phase != MonitorPhase.OFF) {
                            add(stringResource(R.string.pg_diag_since, formats.dayTime(monitor.phaseSince, data.now, seconds = false)))
                        }
                        monitor.runningSince?.takeIf { running && it <= data.now }?.let {
                            add(stringResource(R.string.pg_diag_running, formats.duration(data.now - it)))
                        }
                        monitor.startOrigin?.takeIf { running }?.let { add(stringResource(R.string.pg_diag_origin, formats.originLabel(it))) }
                    }
                    if (since.isNotEmpty()) {
                        Text(since.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = content.copy(alpha = 0.75f))
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AvailabilityBar(
                    data.slots,
                    description = data.metrics.availability24h?.let { stringResource(R.string.pg_diag_availability, percent(it)) },
                )
                AvailabilityLabels(availabilityLabels(data), content.copy(alpha = 0.7f))
            }
        }
    }
}

/** 数据链路: 数据源 → 连接 → 解析 → 评估 → 呈现. */
@Composable
private fun PipelineSection(data: DiagnosticsData) {
    val metrics = data.metrics
    val monitor = data.monitor
    val running = data.active
    val total = data.enabledSources.size
    val connected = monitor.connectedCount
    val requiredMissing = monitor.missing.any { it.required }
    val fullScreenMissing = DeliveryRequirement.FULL_SCREEN_INTENT in monitor.missing
    val dash = "—"
    val nodes = listOf(
        PipeNode(Icons.Filled.Public, stringResource(R.string.pg_pipe_source), "Wolfx", if (running) MetricLevel.GOOD else MetricLevel.UNKNOWN),
        PipeNode(
            Icons.Filled.Sensors,
            stringResource(R.string.pg_pipe_link),
            if (running) stringResource(R.string.pg_pipe_link_value, connected, total) else dash,
            when {
                !running -> MetricLevel.UNKNOWN
                connected >= total && total > 0 -> MetricLevel.GOOD
                connected > 0 -> MetricLevel.WARN
                else -> MetricLevel.BAD
            },
        ),
        PipeNode(
            Icons.Filled.Description,
            stringResource(R.string.pg_pipe_parse),
            metrics.pipelineParseMs?.let { stringResource(R.string.pg_ms, it) } ?: dash,
            if (metrics.pipelineParseMs != null) MetricLevel.GOOD else MetricLevel.UNKNOWN,
        ),
        PipeNode(
            Icons.Filled.Tune,
            stringResource(R.string.pg_pipe_evaluate),
            metrics.pipelineEvaluateMs?.let { stringResource(R.string.pg_ms, it) } ?: dash,
            if (metrics.pipelineEvaluateMs != null) MetricLevel.GOOD else MetricLevel.UNKNOWN,
        ),
        PipeNode(
            Icons.Filled.NotificationsActive,
            stringResource(R.string.pg_pipe_present),
            when {
                !running -> stringResource(R.string.pg_pipe_idle)
                requiredMissing -> stringResource(R.string.pg_phase_limited)
                fullScreenMissing -> stringResource(R.string.pg_pipe_partial)
                else -> stringResource(R.string.pg_pipe_ready)
            },
            when {
                !running -> MetricLevel.UNKNOWN
                requiredMissing -> MetricLevel.BAD
                fullScreenMissing -> MetricLevel.WARN
                else -> MetricLevel.GOOD
            },
        ),
    )
    Column {
        Subhead(
            stringResource(R.string.pg_diag_pipeline),
            trailing = metrics.endToEndMs?.let { ms ->
                { SectionValue(stringResource(R.string.pg_diag_end_to_end, data.formats.seconds(ms))) }
            },
        )
        PipelineStrip(nodes)
    }
}

@Composable
internal fun SectionValue(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = color)
}

/** 数据源: each subscribed feed with link state, last report and latency. */
@Composable
private fun SourcesSection(data: DiagnosticsData) {
    val monitor = data.monitor
    val rows = if (monitor.sources.isNotEmpty()) {
        monitor.sources.sortedBy { it.source.ordinal }
    } else {
        data.enabledSources.sortedBy { it.ordinal }.map { SourceHealth(it) }
    }
    Column {
        Subhead(
            stringResource(R.string.pg_sources),
            trailing = if (data.active) {
                { SectionValue(stringResource(R.string.pg_diag_sources_connected, monitor.connectedCount, rows.size)) }
            } else {
                null
            },
        )
        SegmentedGroup(rows.size) { index, shape -> SourceRow(rows[index], data, shape) }
    }
}

@Composable
private fun SourceRow(health: SourceHealth, data: DiagnosticsData, shape: Shape) {
    val formats = data.formats
    val now = data.now
    val running = data.active
    val dot = when {
        !running -> MaterialTheme.colorScheme.outline
        health.link == LinkState.CONNECTED -> MaterialTheme.status.ok
        health.link == LinkState.POLLING || health.link == LinkState.RECONNECTING -> MaterialTheme.status.warn
        health.link == LinkState.CONNECTING -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.error
    }
    val others = (data.monitor.connectedCount - if (health.link == LinkState.CONNECTED || health.link == LinkState.POLLING) 1 else 0).coerceAtLeast(0)
    val detail = when {
        !running -> stringResource(R.string.pg_src_idle)
        health.link == LinkState.CONNECTED -> {
            val report = health.lastReport
            val reportAt = health.lastReportAt
            when {
                report != null && reportAt != null -> stringResource(
                    R.string.pg_src_last_report,
                    "${report.hypocenter} M${String.format(Locale.ROOT, "%.1f", report.magnitude)}",
                    formats.ago(reportAt, now),
                )
                health.lastFrameAt != null -> stringResource(R.string.pg_meta_heartbeat, formats.ago(health.lastFrameAt, now))
                else -> stringResource(R.string.pg_link_connected)
            }
        }
        health.link == LinkState.POLLING -> stringResource(R.string.pg_src_polling, health.consecutiveFailures)
        health.link == LinkState.RECONNECTING -> {
            val retry = health.nextRetryAt
            if (retry != null) {
                stringResource(R.string.pg_src_reconnecting, health.reconnectAttempt, ((retry - now + 999) / 1000).coerceAtLeast(0L), others)
            } else {
                stringResource(R.string.pg_link_reconnecting)
            }
        }
        health.link == LinkState.CONNECTING -> stringResource(R.string.pg_src_connecting)
        else -> stringResource(R.string.pg_link_disconnected)
    }
    val latency = health.lastLatencyMs
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusDot(dot)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(formats.sourceName(health.source), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        health.source.wsUrl.substringAfterLast('/'),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (latency != null && running) {
                Spacer(Modifier.width(8.dp))
                val level = MetricThresholds.latencyP95(latency)
                Text(
                    stringResource(R.string.pg_seconds_value, formats.seconds(latency)),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (level == MetricLevel.GOOD) MaterialTheme.colorScheme.onSurface else levelColors(level).accent,
                )
            }
        }
    }
}

private data class TileSpec(
    val icon: ImageVector,
    val label: String,
    val value: String,
    val unit: String?,
    val detail: String,
    val level: MetricLevel,
    val extra: (@Composable () -> Unit)? = null,
)

@Composable
private fun levelLabel(level: MetricLevel): String = stringResource(
    when (level) {
        MetricLevel.GOOD -> R.string.pg_level_good
        MetricLevel.WARN -> R.string.pg_level_warn
        MetricLevel.BAD -> R.string.pg_level_bad
        MetricLevel.UNKNOWN -> R.string.pg_level_unknown
    }
)

/** 指标 grid with the thresholds of 指标定义与阈值. */
@Composable
private fun MetricsSection(data: DiagnosticsData, log: List<LogEntry>) {
    val m = data.metrics
    val formats = data.formats
    val now = data.now
    val dash = "—"
    val noData = stringResource(R.string.pg_no_data)
    val settings = data.settings
    val restarted = remember(log, now / 60_000L) {
        startHistory(log, limit = 50).any { it.origin == StartOrigin.WATCHDOG && now - it.time < 24 * 3_600_000L }
    }
    val bootEnabled = settings?.let { it.serviceEnabled && it.bootStart } ?: false
    val bootWall = remember(now / 60_000L) { System.currentTimeMillis() - SystemClock.elapsedRealtime() }
    val ok = MaterialTheme.status.ok
    val warn = MaterialTheme.status.warn

    val tiles = listOf(
        TileSpec(
            Icons.Filled.MonitorHeart,
            stringResource(R.string.pg_metric_heartbeat),
            m.heartbeatAgeMs?.let { (it / 1000).toString() } ?: dash,
            if (m.heartbeatAgeMs != null) "s" else null,
            if (m.heartbeatAgeMs != null) stringResource(R.string.pg_metric_heartbeat_expect) else noData,
            MetricThresholds.heartbeat(m.heartbeatAgeMs),
            if (m.heartbeatSamplesSec.size >= 2) ({ Sparkline(m.heartbeatSamplesSec, ok, warn) }) else null,
        ),
        TileSpec(
            Icons.Filled.Speed,
            stringResource(R.string.pg_metric_latency),
            m.latencyP50Ms?.let { formats.seconds(it) } ?: dash,
            if (m.latencyP50Ms != null) stringResource(R.string.pg_unit_s_p50) else null,
            m.latencyP95Ms?.let { stringResource(R.string.pg_metric_latency_detail, formats.seconds(it), m.reportsToday) }
                ?: stringResource(R.string.pg_metric_reports_today, m.reportsToday),
            MetricThresholds.latencyP95(m.latencyP95Ms),
        ),
        TileSpec(
            Icons.Filled.Schedule,
            stringResource(R.string.pg_metric_clock),
            m.clockOffsetMs?.let { formats.signedSeconds(it) } ?: dash,
            if (m.clockOffsetMs != null) "s" else null,
            m.clockCheckedAt?.let { stringResource(R.string.pg_metric_clock_detail, formats.clockSeconds(it)) } ?: noData,
            MetricThresholds.clockOffset(m.clockOffsetMs),
        ),
        TileSpec(
            Icons.Filled.MyLocation,
            stringResource(R.string.pg_metric_location),
            if (m.locationSource != null) locationSourceLabel(m.locationSource) else dash,
            null,
            buildList {
                m.locationAccuracyM?.let { add(stringResource(R.string.pg_loc_accuracy, it.toInt())) }
                m.locationAgeMs?.let { add(formats.ago(now - it, now)) }
            }.joinToString(" · ").ifEmpty { if (m.locationSource != null) "" else noData },
            MetricThresholds.location(m.locationSource, m.locationAgeMs),
        ),
        TileSpec(
            Icons.Filled.BatteryFull,
            stringResource(R.string.pg_metric_system),
            when (m.batteryOptimizationExempt) {
                true -> stringResource(R.string.pg_metric_exempt)
                false -> stringResource(R.string.pg_metric_not_exempt)
                null -> dash
            },
            null,
            buildList {
                m.standbyBucket?.let { add(stringResource(R.string.pg_metric_bucket, it)) }
                m.dataSaverOn?.let { add(stringResource(if (it) R.string.pg_metric_data_saver_on else R.string.pg_metric_data_saver_off)) }
            }.joinToString(" · ").ifEmpty { noData },
            MetricThresholds.systemLimits(m.batteryOptimizationExempt, m.standbyBucket),
        ),
        TileSpec(
            Icons.Filled.HealthAndSafety,
            stringResource(R.string.pg_metric_watchdog),
            m.watchdogLastRunAt?.let { formats.clock(it) } ?: dash,
            null,
            when {
                restarted -> stringResource(R.string.pg_metric_watchdog_restarted)
                m.watchdogNextRunAt != null -> stringResource(R.string.pg_metric_watchdog_next, formats.clockWithDay(m.watchdogNextRunAt, now))
                else -> noData
            },
            MetricThresholds.watchdog(m.watchdogLastRunAt, m.watchdogNextRunAt, now, restarted),
        ),
        TileSpec(
            Icons.Filled.RestartAlt,
            stringResource(R.string.boot_start),
            when {
                !bootEnabled -> stringResource(R.string.pg_short_off)
                m.bootStartDelaySec != null -> m.bootStartDelaySec.toString()
                else -> dash
            },
            if (bootEnabled && m.bootStartDelaySec != null) "s" else null,
            when {
                !bootEnabled -> stringResource(R.string.boot_start_off)
                m.bootStartDelaySec != null -> stringResource(R.string.pg_metric_boot_detail, formats.dayTime(bootWall, now, seconds = false))
                else -> stringResource(R.string.pg_boot_this_none)
            },
            MetricThresholds.bootStart(m.bootStartDelaySec, bootEnabled, data.active),
        ),
        TileSpec(
            Icons.Filled.Memory,
            stringResource(R.string.pg_metric_process),
            if (data.monitor.detectorProcessStartedAt != null) stringResource(R.string.pg_metric_process_value) else dash,
            null,
            data.monitor.detectorProcessStartedAt?.let { stringResource(R.string.pg_metric_process_detail, formats.duration(now - it)) }
                ?: stringResource(R.string.pg_src_idle),
            if (data.monitor.detectorProcessStartedAt != null && data.active) MetricLevel.GOOD else MetricLevel.UNKNOWN,
        ),
        TileSpec(
            Icons.Filled.Timer,
            stringResource(R.string.pg_metric_availability),
            m.availability24h?.let { percent(it) } ?: dash,
            if (m.availability24h != null) "%" else null,
            if (m.longestOutageMs > 0) stringResource(R.string.pg_diag_longest, formats.duration(m.longestOutageMs)) else stringResource(R.string.pg_metric_24h),
            MetricThresholds.availability(m.availability24h),
        ),
        TileSpec(
            Icons.Filled.Sync,
            stringResource(R.string.pg_metric_reconnects),
            m.reconnects24h.toString(),
            stringResource(R.string.pg_unit_times),
            stringResource(R.string.pg_metric_24h),
            if (data.active || m.reconnects24h > 0) MetricThresholds.reconnects(m.reconnects24h) else MetricLevel.UNKNOWN,
        ),
    )
    Column {
        Subhead(stringResource(R.string.pg_diag_metrics))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = if (maxWidth >= 640.dp) 4 else 2
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tiles.chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { tile ->
                            MetricTile(
                                icon = tile.icon,
                                label = tile.label,
                                value = tile.value,
                                unit = tile.unit,
                                detail = tile.detail,
                                level = tile.level,
                                levelLabel = levelLabel(tile.level),
                                extra = tile.extra,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/** Compact summary (prototype diagMini): phase, availability bar, three key metrics. */
@Composable
private fun DiagnosticsSummaryCard(data: DiagnosticsData, onOpenFull: (() -> Unit)?, modifier: Modifier = Modifier) {
    val (container, content) = headerColors(data.phase)
    val m = data.metrics
    val formats = data.formats
    val dash = "—"
    Surface(shape = RoundedCornerShape(28.dp), color = container, contentColor = content, modifier = modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatusShape(data.phase, size = 40.dp)
                Column(Modifier.weight(1f)) {
                    Text(
                        if (data.active || data.phase == MonitorPhase.CONNECTING) healthTitle(data.phase) else stringResource(R.string.pg_diag_title_off),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(availabilitySummary(data, includeOutage = false), style = MaterialTheme.typography.bodySmall, color = content.copy(alpha = 0.8f))
                }
            }
            AvailabilityBar(data.slots, height = 18.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniMetric(
                    stringResource(R.string.pg_metric_heartbeat),
                    m.heartbeatAgeMs?.let { stringResource(R.string.pg_seconds_value, (it / 1000).toString()) } ?: dash,
                    MetricThresholds.heartbeat(m.heartbeatAgeMs),
                    Modifier.weight(1f),
                )
                MiniMetric(
                    stringResource(R.string.pg_metric_latency_p95),
                    m.latencyP95Ms?.let { stringResource(R.string.pg_seconds_value, formats.seconds(it)) } ?: dash,
                    MetricThresholds.latencyP95(m.latencyP95Ms),
                    Modifier.weight(1f),
                )
                MiniMetric(
                    stringResource(R.string.pg_metric_clock),
                    m.clockOffsetMs?.let { stringResource(R.string.pg_seconds_value, formats.signedSeconds(it)) } ?: dash,
                    MetricThresholds.clockOffset(m.clockOffsetMs),
                    Modifier.weight(1f),
                )
            }
            if (onOpenFull != null) {
                TextButton(
                    onClick = onOpenFull,
                    modifier = Modifier.align(Alignment.End).focusHighlight(RoundedCornerShape(50)),
                ) {
                    Text(stringResource(R.string.pg_diag_open_full), color = content)
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun MiniMetric(label: String, value: String, level: MetricLevel, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f), modifier = modifier) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(levelColors(level).accent, Modifier.size(8.dp))
                Spacer(Modifier.width(5.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(2.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Preview(showBackground = true, widthDp = 380)
@Composable
private fun AvailabilityBarPreview() {
    MyEarthQuakeAlertTheme(darkTheme = false, dynamicColor = false) {
        val start = 0L
        val slots = List(AvailabilitySlots) { i ->
            AvailabilitySlot(start + i * 900_000L, if (i == 22 || i == 95) SlotStatus.DEGRADED else if (i == 79) SlotStatus.DOWN else SlotStatus.UP)
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AvailabilityBar(slots)
            Sparkline(listOf(16, 22, 18, 25, 19, 17, 31, 20, 18, 24, 21, 19, 58, 22, 18), MaterialTheme.status.ok, MaterialTheme.status.warn)
            MetricTile(Icons.Filled.MonitorHeart, "心跳间隔", "18", "期望 ≤ 60 s", MetricLevel.GOOD, unit = "s")
        }
    }
}
