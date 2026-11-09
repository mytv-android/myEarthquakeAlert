package com.github.mytv.myearthquakealert.ui.settings

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.mytv.myearthquakealert.MyEarthQuakeAlertApp
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.map.offline.OfflineMapStatus
import com.github.mytv.myearthquakealert.map.offline.OfflineMapStore
import com.github.mytv.myearthquakealert.map.offline.OfflineMaps
import com.github.mytv.myearthquakealert.ui.components.SegmentedGroup
import com.github.mytv.myearthquakealert.ui.settings.components.NavRow
import com.github.mytv.myearthquakealert.ui.settings.components.SwitchRow
import com.github.mytv.myearthquakealert.ui.settings.components.focusHighlight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The map part of 设置 › 显示: 地图样式 and 离线地图 (design §7 离线预缓存) — status
 * (已缓存 N 张 · X MB · 时间 / 正在下载 n/N / 未缓存), 立即更新 (the row itself; 取消 while
 * downloading), 仅 Wi-Fi and 清除缓存. OSM forbids bulk downloads, which the status says.
 */
@Composable
internal fun MapRows(env: SettingsEnv, onShowDialog: (SettingsDialog) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as MyEarthQuakeAlertApp
    val scope = rememberCoroutineScope()
    val style = env.settings.mapStyle
    val status by remember(app) {
        OfflineMaps.status(app, app.settingsRepository.settings.map { it.mapStyle }.distinctUntilChanged())
    }.collectAsStateWithLifecycle(initialValue = null)
    val wifiOnly by remember(app) {
        OfflineMapStore.get(app).prefs.map { it.wifiOnly }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = true)
    var cleared by remember { mutableIntStateOf(0) }
    val running = status is OfflineMapStatus.Running
    // Stored size, re-read when a run starts / ends and after 清除缓存.
    val storedBytes by produceState<Long?>(null, status?.javaClass, cleared) {
        value = withContext(Dispatchers.IO) { OfflineMaps.storedBytes(app) }
    }
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    SegmentedGroup(4) { index, shape ->
        when (index) {
            0 -> NavRow(
                title = stringResource(R.string.map_tile_style),
                shape = shape,
                icon = Icons.Filled.Map,
                subtitle = mapStyleLabel(style),
                onClick = { onShowDialog(SettingsDialog.MAP_STYLE) },
                trailing = null,
            )
            1 -> NavRow(
                title = stringResource(R.string.wg_offline_title),
                shape = shape,
                icon = Icons.Filled.DownloadForOffline,
                subtitle = offlineStatusText(status, wifiOnly, env),
                enabled = status != null && status != OfflineMapStatus.Unsupported,
                onClick = { if (running) OfflineMaps.cancel(app) else OfflineMaps.updateNow(app) },
                trailing = when (val s = status) {
                    null, OfflineMapStatus.Unsupported -> null
                    is OfflineMapStatus.Running -> {
                        { RunningTrailing(s) }
                    }
                    else -> {
                        { TrailingLabel(stringResource(R.string.wg_offline_update)) }
                    }
                },
            )
            2 -> SwitchRow(
                title = stringResource(R.string.wg_offline_wifi_only),
                shape = shape,
                icon = Icons.Filled.Wifi,
                subtitle = stringResource(R.string.wg_offline_wifi_only_desc),
                checked = wifiOnly,
                onCheckedChange = { on -> scope.launch { OfflineMaps.setWifiOnly(app, on) } },
            )
            else -> NavRow(
                title = stringResource(R.string.wg_offline_clear),
                shape = shape,
                icon = Icons.Filled.DeleteSweep,
                subtitle = storedBytes?.let { bytes ->
                    if (bytes > 0L) {
                        stringResource(R.string.wg_offline_clear_desc, Formatter.formatShortFileSize(context, bytes))
                    } else {
                        stringResource(R.string.wg_offline_clear_empty)
                    }
                },
                enabled = (storedBytes ?: 0L) > 0L || (status as? OfflineMapStatus.Cached) != null,
                onClick = { confirmClear = true },
                trailing = null,
            )
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.wg_offline_clear_title)) },
            text = {
                Text(
                    stringResource(R.string.wg_offline_clear_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmClear = false
                        scope.launch {
                            OfflineMaps.clear(app)
                            cleared++
                            env.actions.toast(R.string.wg_offline_cleared)
                        }
                    },
                    modifier = Modifier.focusHighlight(RoundedCornerShape(50)),
                ) { Text(stringResource(R.string.wg_offline_clear_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }, modifier = Modifier.focusHighlight(RoundedCornerShape(50))) {
                    Text(stringResource(R.string.wg_offline_cancel))
                }
            },
        )
    }
}

@Composable
private fun offlineStatusText(status: OfflineMapStatus?, wifiOnly: Boolean, env: SettingsEnv): String? {
    val context = LocalContext.current
    return when (status) {
        null -> null
        OfflineMapStatus.Unsupported -> stringResource(R.string.wg_offline_unsupported)
        is OfflineMapStatus.Running -> if (status.total > 0) {
            stringResource(R.string.wg_offline_downloading, status.done, status.total)
        } else {
            stringResource(R.string.wg_offline_preparing)
        }
        OfflineMapStatus.Waiting -> when {
            OfflineMaps.waitsForUnmetered(context, wifiOnly) && !wifiOnly -> stringResource(R.string.wg_offline_waiting_data_saver)
            OfflineMaps.waitsForUnmetered(context, wifiOnly) -> stringResource(R.string.wg_offline_waiting_wifi)
            else -> stringResource(R.string.wg_offline_waiting)
        }
        is OfflineMapStatus.Cached -> {
            val area = status.area
            val size = Formatter.formatShortFileSize(context, area.bytes)
            val time = env.formats.dayTime(area.updatedAt, env.now, seconds = false)
            if (area.complete) {
                stringResource(R.string.wg_offline_cached, area.tiles, size, time)
            } else {
                stringResource(R.string.wg_offline_partial, area.tiles, area.total, size, time)
            }
        }
        is OfflineMapStatus.NotCached -> if (status.failedAt > 0L) {
            stringResource(R.string.wg_offline_failed, env.formats.dayTime(status.failedAt, env.now, seconds = false))
        } else {
            stringResource(R.string.wg_offline_none)
        }
    }
}

@Composable
private fun RunningTrailing(status: OfflineMapStatus.Running) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (status.total > 0) {
            CircularProgressIndicator(
                progress = { status.done.toFloat() / status.total },
                modifier = Modifier.size(22.dp),
                strokeWidth = 3.dp,
            )
        } else {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 3.dp)
        }
        TrailingLabel(stringResource(R.string.wg_offline_cancel))
    }
}

@Composable
private fun TrailingLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, maxLines = 1)
}
