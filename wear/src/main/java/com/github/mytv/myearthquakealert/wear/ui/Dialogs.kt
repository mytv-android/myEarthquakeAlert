package com.github.mytv.myearthquakealert.wear.ui

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.ConfirmationDialog
import androidx.wear.compose.material3.ConfirmationDialogDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.SuccessConfirmationDialog
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.confirmationDialogCurvedText
import com.github.mytv.myearthquakealert.wear.R
import com.github.mytv.myearthquakealert.wear.data.CommandOutcome
import com.github.mytv.myearthquakealert.wear.data.WatchStore
import com.github.mytv.myearthquakealert.wear.data.WearPaths
import com.github.mytv.myearthquakealert.wear.data.WireCommandResult

/** Success / failure confirmation of the last 暂停 / 恢复 / 开启 command sent to the phone. */
@Composable
fun CommandOutcomeDialog() {
    val outcome by WatchStore.commandOutcome.collectAsStateWithLifecycle()
    // Keep the last outcome so the dialog can animate out after it was consumed.
    var shown by remember { mutableStateOf<CommandOutcome?>(null) }
    LaunchedEffect(outcome) { outcome?.let { shown = it } }
    val current = outcome
    val last = shown
    val style = ConfirmationDialogDefaults.curvedTextStyle
    val successText = last?.let { stringResource(successText(it.command)) } ?: ""
    SuccessConfirmationDialog(
        visible = current != null && current.ok,
        onDismissRequest = { current?.let { WatchStore.consumeOutcome(it.id) } },
        curvedText = { confirmationDialogCurvedText(successText, style) },
    )
    ConfirmationDialog(
        visible = current != null && !current.ok,
        onDismissRequest = { current?.let { WatchStore.consumeOutcome(it.id) } },
        text = {
            Text(
                last?.let { stringResource(errorText(it.error)) } ?: "",
                textAlign = TextAlign.Center,
            )
        },
        colors = ConfirmationDialogDefaults.failureColors(),
        durationMillis = FAILURE_MS,
    ) {
        if (last?.error == CommandOutcome.ERR_NO_PHONE) {
            ConfirmationDialogDefaults.ConnectionFailureIcon()
        } else {
            Icon(painterResource(R.drawable.ic_warning), contentDescription = null, modifier = Modifier.size(36.dp))
        }
    }
}

private const val FAILURE_MS = 6_000L

private fun successText(command: String): Int = when (command) {
    WearPaths.CMD_PAUSE -> R.string.done_paused
    WearPaths.CMD_RESUME -> R.string.done_resumed
    else -> R.string.done_started
}

private fun errorText(error: String?): Int = when (error) {
    CommandOutcome.ERR_NO_PHONE -> R.string.err_no_phone
    CommandOutcome.ERR_NO_REPLY -> R.string.err_no_reply
    WireCommandResult.ERR_OFF -> R.string.err_off
    WireCommandResult.ERR_BACKGROUND -> R.string.err_background
    WireCommandResult.ERR_TIMEOUT -> R.string.err_timeout
    else -> R.string.err_failed
}
