package com.github.mytv.myearthquakealert.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.domain.DrillScenario
import com.github.mytv.myearthquakealert.ui.settings.components.ChoiceOption
import com.github.mytv.myearthquakealert.ui.settings.components.Formats
import com.github.mytv.myearthquakealert.ui.settings.components.RadioOptions
import com.github.mytv.myearthquakealert.ui.settings.components.focusHighlight

private enum class PauseChoice { ONE_HOUR, MORNING, OFF }

/**
 * 暂停或关闭后台监测 (design §3: 关闭前先提供暂停). With [includeOff] the dialog is the
 * answer to switching monitoring off; without it, it edits the pause from the 暂停 row.
 */
@Composable
internal fun PauseDialog(
    formats: Formats,
    now: Long,
    pausedUntil: Long,
    includeOff: Boolean,
    onPause: (Long) -> Unit,
    onResume: () -> Unit,
    onTurnOff: () -> Unit,
    onDismiss: () -> Unit,
) {
    var choice by rememberSaveable { mutableStateOf(PauseChoice.ONE_HOUR) }
    val oneHour = SettingsActions.oneHourFrom(now)
    val morning = SettingsActions.nextMorning(now)
    val paused = pausedUntil > now
    val options = buildList {
        add(ChoiceOption(PauseChoice.ONE_HOUR, stringResource(R.string.pg_pause_1h), stringResource(R.string.pg_pause_resumes_at, formats.clockWithDay(oneHour, now))))
        add(
            ChoiceOption(
                PauseChoice.MORNING,
                stringResource(R.string.pg_pause_morning),
                stringResource(R.string.pg_pause_resumes_in, formats.duration(morning - now)),
            )
        )
        if (includeOff) add(ChoiceOption(PauseChoice.OFF, stringResource(R.string.pg_turn_off_fully), stringResource(R.string.pg_turn_off_fully_desc)))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (includeOff) R.string.pg_pause_or_off_title else R.string.pg_pause_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    if (paused) {
                        stringResource(R.string.pg_pause_current, formats.clockWithDay(pausedUntil, now))
                    } else {
                        stringResource(R.string.pg_pause_desc)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(8.dp))
                RadioOptions(options = options, selected = choice, onSelect = { choice = it })
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when (choice) {
                        PauseChoice.ONE_HOUR -> onPause(oneHour)
                        PauseChoice.MORNING -> onPause(morning)
                        PauseChoice.OFF -> onTurnOff()
                    }
                    onDismiss()
                },
                modifier = Modifier.focusHighlight(RoundedCornerShape(50)),
            ) {
                Text(stringResource(if (choice == PauseChoice.OFF) R.string.pg_turn_off_confirm else R.string.pg_pause_confirm))
            }
        },
        dismissButton = {
            if (paused) {
                TextButton(onClick = { onResume(); onDismiss() }, modifier = Modifier.focusHighlight(RoundedCornerShape(50))) {
                    Text(stringResource(R.string.pg_resume_now))
                }
            } else {
                TextButton(onClick = onDismiss, modifier = Modifier.focusHighlight(RoundedCornerShape(50))) {
                    Text(stringResource(R.string.cancel))
                }
            }
        },
    )
}

/** 模拟演练: the three scenarios the service can replay. */
@Composable
internal fun DrillDialog(
    onRun: (DrillScenario.Scenario) -> Unit,
    onDismiss: () -> Unit,
) {
    var choice by rememberSaveable { mutableStateOf(DrillScenario.Scenario.LUSHAN) }
    val options = DrillScenario.Scenario.entries.map { ChoiceOption(it, drillTitle(it), drillDescription(it)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pg_drill_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.pg_drill_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.size(8.dp))
                RadioOptions(options = options, selected = choice, onSelect = { choice = it })
            }
        },
        confirmButton = {
            Button(onClick = { onRun(choice); onDismiss() }, modifier = Modifier.focusHighlight(RoundedCornerShape(50))) {
                Text(stringResource(R.string.pg_drill_run))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.focusHighlight(RoundedCornerShape(50))) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
internal fun drillTitle(scenario: DrillScenario.Scenario): String = when (scenario.id) {
    "lushan" -> stringResource(R.string.pg_drill_lushan)
    "near" -> stringResource(R.string.pg_drill_near)
    "far" -> stringResource(R.string.pg_drill_far)
    else -> scenario.name
}

@Composable
internal fun drillDescription(scenario: DrillScenario.Scenario): String? = when (scenario.id) {
    "lushan" -> stringResource(R.string.pg_drill_lushan_desc)
    "near" -> stringResource(R.string.pg_drill_near_desc)
    "far" -> stringResource(R.string.pg_drill_far_desc)
    else -> null
}
