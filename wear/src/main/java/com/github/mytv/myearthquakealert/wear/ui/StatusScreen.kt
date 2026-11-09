package com.github.mytv.myearthquakealert.wear.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.github.mytv.myearthquakealert.wear.R
import com.github.mytv.myearthquakealert.wear.alert.AlertActivity
import com.github.mytv.myearthquakealert.wear.data.PhoneLink
import com.github.mytv.myearthquakealert.wear.data.WatchPhase
import com.github.mytv.myearthquakealert.wear.data.WatchPhases
import com.github.mytv.myearthquakealert.wear.data.WatchStore
import com.github.mytv.myearthquakealert.wear.data.WearPaths
import com.github.mytv.myearthquakealert.wear.data.WireQuake
import com.github.mytv.myearthquakealert.wear.data.WireSource
import com.github.mytv.myearthquakealert.wear.domain.Countdown
import com.github.mytv.myearthquakealert.wear.presentation.PhaseText
import com.github.mytv.myearthquakealert.wear.ui.theme.MagnitudeBadge
import com.github.mytv.myearthquakealert.wear.ui.theme.PhaseBadge
import com.github.mytv.myearthquakealert.wear.ui.theme.StatusTones
import com.github.mytv.myearthquakealert.wear.ui.theme.WatchColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 手表 › 状态 (design scrWStatus): the phase shape, the state and its sub line, the latest
 * quake chip and the edge button (暂停 1 小时 / 恢复 / 开启); below, the phone's data sources
 * and the recent quakes, then 地图 and 设置.
 */
@Composable
fun StatusScreen(onMap: (String?) -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    val status by WatchStore.status.collectAsStateWithLifecycle()
    val quakes by WatchStore.quakes.collectAsStateWithLifecycle()
    val phone by WatchStore.phoneConnected.collectAsStateWithLifecycle()
    val standalone by WatchStore.standalone.collectAsStateWithLifecycle()
    val pending by WatchStore.pendingCommand.collectAsStateWithLifecycle()
    val alert by WatchStore.alert.collectAsStateWithLifecycle()
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(TICK_MS)
            value = System.currentTimeMillis()
        }
    }
    val phase = WatchPhases.resolve(status, phone, standalone, now)
    val scope = rememberCoroutineScope()
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    val activeAlert = alert?.takeIf { Countdown.snapshot(it, now).stage != Countdown.Stage.EXPIRED }

    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            val action = edgeAction(phase)
            EdgeButton(
                onClick = {
                    scope.launch {
                        when (action) {
                            EdgeAction.PAUSE -> PhoneLink.send(context, WearPaths.CMD_PAUSE, WearPaths.DEFAULT_PAUSE_MS)
                            EdgeAction.RESUME -> PhoneLink.send(context, WearPaths.CMD_RESUME)
                            EdgeAction.START -> PhoneLink.send(context, WearPaths.CMD_START)
                            EdgeAction.RETRY -> {
                                PhoneLink.refreshConnection(context)
                                PhoneLink.syncDataItems(context)
                            }
                        }
                    }
                },
                buttonSize = EdgeButtonSize.Small,
                enabled = pending == null,
                colors = ButtonDefaults.buttonColors(containerColor = WatchColors.EdgeButton, contentColor = WatchColors.OnEdgeButton),
            ) {
                Icon(painterResource(action.icon), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (pending != null) stringResource(R.string.action_waiting) else stringResource(action.label), maxLines = 1)
            }
        },
    ) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            if (activeAlert != null) {
                item {
                    Button(
                        onClick = { context.startActivity(AlertActivity.intent(context)) },
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                        colors = ButtonDefaults.buttonColors(containerColor = WatchColors.RedEdge, contentColor = WatchColors.OnRedEdge),
                        icon = { Icon(painterResource(R.drawable.ic_earthquake), contentDescription = null) },
                        label = {
                            Text(
                                stringResource(R.string.status_alert_active, PhaseText.magnitude(activeAlert.magnitude)),
                                maxLines = 1,
                            )
                        },
                        secondaryLabel = { Text(PhaseText.shortPlace(activeAlert.place, 10), maxLines = 1) },
                    )
                }
            }
            item {
                Header(phase, PhaseText.short(context, phase, status), PhaseText.sub(context, phase, status, standalone), quakes.firstOrNull())
            }
            val sources = status?.sources.orEmpty()
            if (sources.isNotEmpty() && phone != false && phase != WatchPhase.OFF) {
                item {
                    ListHeader(modifier = Modifier.transformedHeight(this, spec), transformation = SurfaceTransformation(spec)) {
                        Text(stringResource(R.string.status_sources))
                    }
                }
                items(sources, key = { "src-" + it.id }) { source ->
                    SourceRow(source, Modifier.transformedHeight(this, spec))
                }
            }
            item {
                ListHeader(modifier = Modifier.transformedHeight(this, spec), transformation = SurfaceTransformation(spec)) {
                    Text(stringResource(R.string.status_recent))
                }
            }
            if (quakes.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.status_no_quakes),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).transformedHeight(this, spec),
                        textAlign = TextAlign.Center,
                        color = WatchColors.Sub,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            items(quakes, key = { "q-" + it.eventId }) { quake ->
                Card(
                    onClick = { onMap(quake.eventId) },
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                ) {
                    QuakeRow(context, quake, now)
                }
            }
            item {
                Button(
                    onClick = { onMap(null) },
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    icon = { Icon(painterResource(R.drawable.ic_map), contentDescription = null) },
                    label = { Text(stringResource(R.string.status_map)) },
                )
            }
            item {
                Button(
                    onClick = onSettings,
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    icon = { Icon(painterResource(R.drawable.ic_settings), contentDescription = null) },
                    label = { Text(stringResource(R.string.status_settings)) },
                )
            }
        }
    }
    CommandOutcomeDialog()
}

@Composable
private fun Header(phase: WatchPhase, title: String, sub: String, latest: WireQuake?) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PhaseBadge(phase)
        Spacer(Modifier.height(6.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = WatchColors.Text,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Text(
            sub,
            style = MaterialTheme.typography.bodySmall,
            color = WatchColors.Sub,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (latest != null) {
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(WatchColors.Chip)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text(PhaseText.quakeChip(latest), style = MaterialTheme.typography.labelSmall, color = WatchColors.Text, maxLines = 1)
            }
        }
    }
}

@Composable
private fun SourceRow(source: WireSource, modifier: Modifier) {
    val (tone, label) = when (source.link) {
        "CONNECTED" -> StatusTones.Ok.content to R.string.link_connected
        "POLLING" -> StatusTones.Warn.content to R.string.link_polling
        "CONNECTING" -> StatusTones.Warn.content to R.string.link_connecting
        "RECONNECTING" -> StatusTones.Warn.content to R.string.link_reconnecting
        else -> StatusTones.Err.content to R.string.link_disconnected
    }
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(tone))
        Text(source.label, style = MaterialTheme.typography.bodySmall, color = WatchColors.Text, maxLines = 1, modifier = Modifier.weight(1f))
        Text(stringResource(label), style = MaterialTheme.typography.labelSmall, color = tone, maxLines = 1)
    }
}

@Composable
private fun QuakeRow(context: Context, quake: WireQuake, now: Long) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MagnitudeBadge(quake.magnitude, if (quake.distanceKm > 0) quake.localCsis else quake.maxIntensity)
        Column(Modifier.weight(1f)) {
            Text(quake.place, style = MaterialTheme.typography.labelMedium, color = WatchColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val meta = buildString {
                append(PhaseText.ago(quake, now))
                if (quake.distanceKm > 0) append(" · ").append(PhaseText.distance(context, quake.distanceKm))
            }
            Text(meta, style = MaterialTheme.typography.bodyExtraSmall, color = WatchColors.Sub, maxLines = 1)
        }
    }
}

private enum class EdgeAction(val label: Int, val icon: Int) {
    PAUSE(R.string.action_pause_hour, R.drawable.ic_pause),
    RESUME(R.string.action_resume, R.drawable.ic_play),
    START(R.string.action_start, R.drawable.ic_play),
    RETRY(R.string.action_retry, R.drawable.ic_sync),
}

private fun edgeAction(phase: WatchPhase): EdgeAction = when {
    phase == WatchPhase.PAUSED -> EdgeAction.RESUME
    phase == WatchPhase.OFF -> EdgeAction.START
    phase.pausable -> EdgeAction.PAUSE
    else -> EdgeAction.RETRY
}

private const val TICK_MS = 30_000L
