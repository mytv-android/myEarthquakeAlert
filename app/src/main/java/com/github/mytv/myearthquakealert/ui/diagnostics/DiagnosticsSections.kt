package com.github.mytv.myearthquakealert.ui.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Troubleshoot
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.diagnostics.FixAction
import com.github.mytv.myearthquakealert.diagnostics.HealthLevel
import com.github.mytv.myearthquakealert.diagnostics.LogEntry
import com.github.mytv.myearthquakealert.diagnostics.SelfTestRun
import com.github.mytv.myearthquakealert.diagnostics.SelfTestStep
import com.github.mytv.myearthquakealert.diagnostics.SelfTestStepResult
import com.github.mytv.myearthquakealert.diagnostics.StepStatus
import com.github.mytv.myearthquakealert.service.DeliveryChecks
import com.github.mytv.myearthquakealert.service.DeliveryRequirement
import com.github.mytv.myearthquakealert.ui.components.SegmentedGroup
import com.github.mytv.myearthquakealert.ui.components.SegmentedRow
import com.github.mytv.myearthquakealert.ui.components.Subhead
import com.github.mytv.myearthquakealert.ui.settings.components.Formats
import com.github.mytv.myearthquakealert.ui.settings.components.focusHighlight
import com.github.mytv.myearthquakealert.ui.theme.status

// ---------------------------------------------------------------- 端到端自检

private fun stepIcon(step: SelfTestStep): ImageVector = when (step) {
    SelfTestStep.NETWORK -> Icons.Filled.NetworkCheck
    SelfTestStep.SOURCES -> Icons.Filled.Sensors
    SelfTestStep.CLOCK -> Icons.Filled.Schedule
    SelfTestStep.LOCATION -> Icons.Filled.MyLocation
    SelfTestStep.PERMISSIONS -> Icons.Filled.Notifications
    SelfTestStep.SYSTEM_LIMITS -> Icons.Filled.BatteryFull
    SelfTestStep.PIPELINE -> Icons.Filled.Description
    SelfTestStep.PRESENTATION -> Icons.Filled.Layers
}

@Composable
private fun stepTitle(step: SelfTestStep): String = stringResource(
    when (step) {
        SelfTestStep.NETWORK -> R.string.pg_step_network
        SelfTestStep.SOURCES -> R.string.pg_step_sources
        SelfTestStep.CLOCK -> R.string.pg_step_clock
        SelfTestStep.LOCATION -> R.string.pg_step_location
        SelfTestStep.PERMISSIONS -> R.string.pg_step_permissions
        SelfTestStep.SYSTEM_LIMITS -> R.string.pg_step_limits
        SelfTestStep.PIPELINE -> R.string.pg_step_pipeline
        SelfTestStep.PRESENTATION -> R.string.pg_step_presentation
    }
)

@Composable
private fun stepDescription(step: SelfTestStep): String = stringResource(
    when (step) {
        SelfTestStep.NETWORK -> R.string.pg_step_network_desc
        SelfTestStep.SOURCES -> R.string.pg_step_sources_desc
        SelfTestStep.CLOCK -> R.string.pg_step_clock_desc
        SelfTestStep.LOCATION -> R.string.pg_step_location_desc
        SelfTestStep.PERMISSIONS -> R.string.pg_step_permissions_desc
        SelfTestStep.SYSTEM_LIMITS -> R.string.pg_step_limits_desc
        SelfTestStep.PIPELINE -> R.string.pg_step_pipeline_desc
        SelfTestStep.PRESENTATION -> R.string.pg_step_presentation_desc
    }
)

/**
 * Opens the screen behind a self-test fix. Returns false for [FixAction.LOCATION_PERMISSION],
 * which the caller requests as a runtime permission.
 */
internal fun openFix(context: Context, action: FixAction): Boolean {
    fun requirement(r: DeliveryRequirement): Boolean {
        if (!DeliveryChecks.fix(context, r)) toast(context, R.string.pg_open_settings_failed)
        return true
    }
    fun intent(action: String): Boolean {
        try {
            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            toast(context, R.string.pg_open_settings_failed)
        }
        return true
    }
    return when (action) {
        FixAction.NOTIFICATION_SETTINGS -> requirement(DeliveryRequirement.NOTIFICATIONS)
        FixAction.OVERLAY_SETTINGS -> requirement(DeliveryRequirement.OVERLAY)
        FixAction.FULL_SCREEN_SETTINGS -> requirement(DeliveryRequirement.FULL_SCREEN_INTENT)
        FixAction.BATTERY_OPTIMIZATION -> requirement(DeliveryRequirement.BATTERY_OPTIMIZATION)
        FixAction.AUTO_START -> requirement(DeliveryRequirement.AUTO_START)
        FixAction.NETWORK_SETTINGS -> intent(Settings.ACTION_WIRELESS_SETTINGS)
        FixAction.DATE_SETTINGS -> intent(Settings.ACTION_DATE_SETTINGS)
        FixAction.LOCATION_PERMISSION -> false
    }
}

@Composable
private fun selfTestSummary(run: SelfTestRun?, formats: Formats, now: Long): String {
    if (run == null) return stringResource(R.string.pg_selftest_never)
    if (run.running) {
        val index = run.steps.indexOfFirst { it.status == StepStatus.RUNNING }.takeIf { it >= 0 }
            ?: run.steps.count { it.status != StepStatus.PENDING }.coerceAtMost(run.steps.size - 1)
        return stringResource(R.string.pg_selftest_step, index + 1, run.steps.size)
    }
    val parts = buildList {
        add(formats.ago(run.finishedAt ?: run.startedAt, now))
        add(stringResource(R.string.pg_selftest_pass, run.steps.count { it.status == StepStatus.PASS }))
        run.steps.count { it.status == StepStatus.WARN }.takeIf { it > 0 }?.let { add(stringResource(R.string.pg_selftest_warn, it)) }
        run.steps.count { it.status == StepStatus.FAIL }.takeIf { it > 0 }?.let { add(stringResource(R.string.pg_selftest_fail, it)) }
    }
    return parts.first() + " · " + parts.drop(1).joinToString(stringResource(R.string.pg_comma))
}

/** 端到端自检: 8 steps with live status, conclusions and fix entries. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SelfTestSection(
    run: SelfTestRun?,
    formats: Formats,
    now: Long,
    onRun: () -> Unit,
    onFix: (FixAction) -> Unit,
) {
    val steps = run?.steps ?: SelfTestStep.entries.map { SelfTestStepResult(it) }
    val running = run?.running == true
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Subhead(stringResource(R.string.pg_diag_selftest), trailing = { SectionValue(selfTestSummary(run, formats, now)) })
        SegmentedGroup(steps.size) { index, shape ->
            val result = steps[index]
            val subtitle = when {
                run == null -> stepDescription(result.step)
                result.status == StepStatus.RUNNING -> stringResource(R.string.pg_selftest_checking)
                result.status == StepStatus.PENDING -> stringResource(if (running) R.string.pg_selftest_waiting else R.string.pg_selftest_skipped)
                result.detail.isNotBlank() -> result.detail
                result.status == StepStatus.PASS -> stringResource(R.string.pg_selftest_passed)
                result.status == StepStatus.WARN -> stringResource(R.string.pg_level_warn)
                else -> stringResource(R.string.pg_selftest_failed)
            }
            SegmentedRow(
                title = stepTitle(result.step),
                subtitle = subtitle,
                icon = stepIcon(result.step),
                shape = shape,
                trailing = if (run == null) {
                    null
                } else {
                    {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val fix = result.fix
                            if (fix != null && (result.status == StepStatus.WARN || result.status == StepStatus.FAIL)) {
                                FilledTonalButton(onClick = { onFix(fix) }, modifier = Modifier.focusHighlight(RoundedCornerShape(50))) {
                                    Text(stringResource(R.string.pg_selftest_fix))
                                }
                            }
                            StepStatusIcon(result.status)
                        }
                    }
                },
            )
        }
        Button(
            onClick = onRun,
            enabled = !running,
            modifier = Modifier.focusHighlight(RoundedCornerShape(50)),
        ) {
            Icon(Icons.Filled.Troubleshoot, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(if (running) R.string.pg_selftest_running else R.string.pg_selftest_run))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StepStatusIcon(status: StepStatus) {
    val size = Modifier.size(26.dp)
    when (status) {
        StepStatus.RUNNING -> LoadingIndicator(modifier = size, color = MaterialTheme.colorScheme.primary)
        StepStatus.PENDING -> Icon(Icons.Filled.RadioButtonUnchecked, contentDescription = stringResource(R.string.pg_selftest_waiting), tint = MaterialTheme.colorScheme.outline, modifier = size)
        StepStatus.PASS -> Icon(Icons.Filled.CheckCircle, contentDescription = stringResource(R.string.pg_selftest_passed), tint = MaterialTheme.status.ok, modifier = size)
        StepStatus.WARN -> Icon(Icons.Filled.Warning, contentDescription = stringResource(R.string.pg_level_warn), tint = MaterialTheme.status.warn, modifier = size)
        StepStatus.FAIL -> Icon(Icons.Filled.Error, contentDescription = stringResource(R.string.pg_selftest_failed), tint = MaterialTheme.colorScheme.error, modifier = size)
    }
}

// ---------------------------------------------------------------- 事件日志

private const val LOG_PAGE = 80

@Composable
private fun healthLevelLabel(level: HealthLevel): String = stringResource(
    when (level) {
        HealthLevel.OK -> R.string.pg_log_ok
        HealthLevel.INFO -> R.string.pg_log_info
        HealthLevel.WARN -> R.string.pg_log_warn
        HealthLevel.ERROR -> R.string.pg_log_error
    }
)

private fun logLine(entry: LogEntry, formats: Formats): String =
    "${formats.clockMillis(entry.time)} ${entry.tag.padEnd(10)} ${entry.message}"

/** 事件日志: newest first, filter by level and tag, monospace, copy and export. */
internal fun LazyListScope.logItems(log: List<LogEntry>, formats: Formats, onExport: () -> Unit) {
    item(key = "log") { LogSection(log, formats, onExport) }
}

@Composable
private fun LogSection(log: List<LogEntry>, formats: Formats, onExport: () -> Unit) {
    val context = LocalContext.current
    var levels by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var tag by rememberSaveable { mutableStateOf<String?>(null) }
    var limit by rememberSaveable { mutableIntStateOf(LOG_PAGE) }
    val tags = remember(log) { log.groupingBy { it.tag }.eachCount().entries.sortedByDescending { it.value }.take(8).map { it.key } }
    val filtered = remember(log, levels, tag) {
        log.asReversed().filter { entry ->
            (levels.isEmpty() || entry.level.name in levels) && (tag == null || entry.tag == tag)
        }
    }
    val okColor = MaterialTheme.status.ok
    val warnColor = MaterialTheme.status.warn
    val errColor = MaterialTheme.colorScheme.error
    val infoColor = MaterialTheme.colorScheme.primary
    val timeColor = MaterialTheme.colorScheme.onSurface

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Subhead(
            stringResource(R.string.pg_diag_log),
            trailing = {
                Row {
                    TextButton(
                        onClick = {
                            val text = filtered.joinToString("\n") { logLine(it, formats) }
                            val clipboard = context.getSystemService(ClipboardManager::class.java)
                            clipboard?.setPrimaryClip(ClipData.newPlainText("log", text))
                            toast(context, R.string.pg_log_copied)
                        },
                        enabled = filtered.isNotEmpty(),
                        modifier = Modifier.focusHighlight(RoundedCornerShape(50)),
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.pg_log_copy))
                    }
                    TextButton(onClick = onExport, modifier = Modifier.focusHighlight(RoundedCornerShape(50))) {
                        Text(stringResource(R.string.pg_export))
                    }
                }
            },
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HealthLevel.entries.forEach { level ->
                val selected = level.name in levels
                FilterChip(
                    selected = selected,
                    onClick = { levels = if (selected) levels - level.name else levels + level.name },
                    label = { Text(healthLevelLabel(level)) },
                    modifier = Modifier.focusHighlight(RoundedCornerShape(8.dp)),
                )
            }
            if (tags.isNotEmpty()) {
                Box(
                    Modifier
                        .align(Alignment.CenterVertically)
                        .size(width = 1.dp, height = 24.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }
            tags.forEach { t ->
                val selected = tag == t
                FilterChip(
                    selected = selected,
                    onClick = { tag = if (selected) null else t; limit = LOG_PAGE },
                    label = { Text(t, fontFamily = FontFamily.Monospace) },
                    modifier = Modifier.focusHighlight(RoundedCornerShape(8.dp)),
                )
            }
        }
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                if (filtered.isEmpty()) {
                    Text(
                        stringResource(if (log.isEmpty()) R.string.pg_log_empty else R.string.pg_log_no_match),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    SelectionContainer {
                        Column(Modifier.horizontalScroll(rememberScrollState())) {
                            filtered.take(limit).forEach { entry ->
                                val levelColor = when (entry.level) {
                                    HealthLevel.OK -> okColor
                                    HealthLevel.WARN -> warnColor
                                    HealthLevel.ERROR -> errColor
                                    HealthLevel.INFO -> infoColor
                                }
                                Text(
                                    buildAnnotatedString {
                                        withStyle(SpanStyle(color = timeColor, fontWeight = FontWeight.SemiBold)) { append(formats.clockMillis(entry.time)) }
                                        append(' ')
                                        withStyle(SpanStyle(color = levelColor, fontWeight = FontWeight.SemiBold)) { append(entry.tag.padEnd(10)) }
                                        append(' ')
                                        append(entry.message)
                                    },
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.5.sp,
                                    lineHeight = 19.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    softWrap = false,
                                )
                            }
                        }
                    }
                    if (filtered.size > limit) {
                        TextButton(
                            onClick = { limit += LOG_PAGE * 3 },
                            modifier = Modifier.align(Alignment.CenterHorizontally).focusHighlight(RoundedCornerShape(50)),
                        ) { Text(stringResource(R.string.pg_log_more, filtered.size - limit)) }
                    }
                }
            }
        }
    }
}
