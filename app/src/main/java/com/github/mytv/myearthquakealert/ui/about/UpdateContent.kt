package com.github.mytv.myearthquakealert.ui.about

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.mytv.myearthquakealert.BuildConfig
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.data.model.UpdateChannel
import com.github.mytv.myearthquakealert.data.model.UpdateInfo
import com.github.mytv.myearthquakealert.service.ActiveAlertHolder
import com.github.mytv.myearthquakealert.ui.about.AboutViewModel.UpdateState
import com.github.mytv.myearthquakealert.ui.adaptive.LayoutKind
import com.github.mytv.myearthquakealert.ui.adaptive.LocalResolvedLayout
import com.github.mytv.myearthquakealert.ui.components.rememberAnimationsEnabled
import com.github.mytv.myearthquakealert.ui.settings.components.Pill
import com.github.mytv.myearthquakealert.ui.settings.components.focusHighlight
import com.github.mytv.myearthquakealert.ui.settings.components.rememberFormats
import com.github.mytv.myearthquakealert.ui.theme.status
import com.github.mytv.myearthquakealert.update.SessionInstaller
import com.github.mytv.myearthquakealert.update.UpdateFailure
import com.github.mytv.myearthquakealert.update.WaitReason
import com.github.mytv.myearthquakealert.update.formatFileSize

@Composable
fun channelLabel(channel: UpdateChannel): String = when (channel) {
    UpdateChannel.STABLE -> stringResource(R.string.pg_channel_stable)
    UpdateChannel.PREVIEW -> stringResource(R.string.pg_channel_preview)
}

/** App identity: cookie-shaped mark, name and "版本 1.0（1）· 稳定版 · GitHub Releases". */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppIdentity(channel: UpdateChannel, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(60.dp)
                .clip(MaterialShapes.Cookie12Sided.toShape())
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_pg_earthquake), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.pg_version_line, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, channelLabel(channel)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Actions of the update card, bound to [AboutViewModel]. */
private class CardActions(
    val viewModel: AboutViewModel,
    val startDownload: (UpdateInfo) -> Unit,
    val primaryFocus: FocusRequester,
)

/**
 * The state card of 关于与更新 (prototype scrUpdate): 已是最新 / 发现新版本 / 下载中 (wavy
 * progress) / 校验中 / 待安装 / 失败, plus waiting for Wi-Fi, paused, the 安装未知应用
 * explanation and the running installation. On TV the primary action takes the focus and
 * the download progress moves to the bottom-right overlay ([UpdateProgressOverlay]).
 */
@Composable
fun UpdateStateCard(
    viewModel: AboutViewModel,
    channel: UpdateChannel,
    wifiOnly: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val state by viewModel.updateState.collectAsStateWithLifecycle()
    val alert by ActiveAlertHolder.activeAlert.collectAsStateWithLifecycle()
    val tv = LocalResolvedLayout.current.kind == LayoutKind.TV
    var meteredConfirm by remember { mutableStateOf<UpdateInfo?>(null) }
    val primaryFocus = remember { FocusRequester() }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResumed() }
    LaunchedEffect(state) { viewModel.onShown(state) }
    // TV: focus lands on 立即更新 / 立即安装 (design §8 电视).
    val focusKey = state.let { it is UpdateState.Available || (it is UpdateState.ReadyToInstall && !it.deferred) }
    LaunchedEffect(tv, focusKey, state::class) {
        if (tv && focusKey) runCatching { primaryFocus.requestFocus() }
    }

    val actions = CardActions(
        viewModel = viewModel,
        startDownload = { info -> if (wifiOnly && isMetered(context)) meteredConfirm = info else viewModel.downloadUpdate(info) },
        primaryFocus = primaryFocus,
    )

    when (val s = state) {
        UpdateState.Idle, UpdateState.UpToDate -> IdleCard(viewModel, s, channel, actions, modifier)
        UpdateState.Checking -> UpdateCard(icon = Icons.Filled.SystemUpdate, heading = stringResource(R.string.update_checking), modifier = modifier) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Loading()
                Spacer(Modifier.width(12.dp))
                CardText(stringResource(R.string.pg_update_checking_desc))
            }
        }
        is UpdateState.Available -> AvailableCard(s.updateInfo, tv, actions, modifier)
        is UpdateState.Waiting -> WaitingCard(s, actions, modifier)
        is UpdateState.Downloading -> DownloadingCard(s, tv, actions, modifier)
        is UpdateState.Paused -> UpdateCard(icon = Icons.Filled.Pause, heading = stringResource(R.string.upd_paused_title), modifier = modifier) {
            VersionLine(s.info)
            LinearProgressIndicator(
                progress = { s.progress / 100f },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
            )
            CardText(stringResource(R.string.upd_paused_desc, s.progress, formatFileSize(s.downloaded), formatFileSize(s.total)))
            CardButtons {
                TextButton(onClick = viewModel::cancelDownload, modifier = Modifier.focusHighlight(PillShape)) {
                    Text(stringResource(R.string.pg_update_cancel_download))
                }
                Button(onClick = viewModel::resumeDownload, modifier = Modifier.focusRequester(primaryFocus).focusHighlight(PillShape)) {
                    ButtonIcon(Icons.Filled.PlayArrow)
                    Text(stringResource(R.string.upd_resume))
                }
            }
        }
        is UpdateState.Verifying -> UpdateCard(icon = Icons.Filled.Verified, heading = stringResource(R.string.pg_update_verifying), modifier = modifier) {
            VersionLine(s.updateInfo)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Loading()
                Spacer(Modifier.width(12.dp))
                CardText(stringResource(R.string.pg_update_verifying_desc))
            }
        }
        is UpdateState.ReadyToInstall -> ReadyCard(s, alert != null, actions, modifier)
        is UpdateState.NeedsInstallPermission -> UpdateCard(icon = Icons.Filled.InstallMobile, heading = stringResource(R.string.pg_update_permission_title), modifier = modifier) {
            VersionLine(s.info)
            CardText(stringResource(R.string.upd_permission_explain))
            CardText(stringResource(R.string.pg_update_permission_desc))
            CardButtons {
                TextButton(onClick = { viewModel.dismiss(s) }, modifier = Modifier.focusHighlight(PillShape)) {
                    Text(stringResource(R.string.cancel))
                }
                Button(onClick = { openUnknownSources(context) }, modifier = Modifier.focusRequester(primaryFocus).focusHighlight(PillShape)) {
                    Text(stringResource(R.string.pg_update_grant))
                }
            }
        }
        is UpdateState.Installing -> UpdateCard(icon = Icons.Filled.SystemUpdate, heading = stringResource(R.string.upd_installing_title), modifier = modifier) {
            VersionLine(s.info)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Loading()
                Spacer(Modifier.width(12.dp))
                CardText(stringResource(R.string.upd_installing_desc))
            }
            if (s.canContinue) {
                CardButtons {
                    Button(onClick = { viewModel.continueInstall(context) }, modifier = Modifier.focusRequester(primaryFocus).focusHighlight(PillShape)) {
                        Text(stringResource(R.string.upd_continue_install))
                    }
                }
            }
        }
        is UpdateState.Error -> ErrorCard(s, actions, modifier)
    }

    meteredConfirm?.let { info ->
        AlertDialog(
            onDismissRequest = { meteredConfirm = null },
            title = { Text(stringResource(R.string.pg_update_metered_title)) },
            text = { Text(stringResource(R.string.pg_update_metered_desc, formatFileSize(info.size))) },
            confirmButton = {
                Button(onClick = { meteredConfirm = null; viewModel.downloadUpdate(info, allowMetered = true) }, modifier = Modifier.focusHighlight(PillShape)) {
                    Text(stringResource(R.string.pg_update_download_anyway))
                }
            },
            dismissButton = {
                TextButton(onClick = { meteredConfirm = null; viewModel.downloadUpdate(info, allowMetered = false) }, modifier = Modifier.focusHighlight(PillShape)) {
                    Text(stringResource(R.string.upd_wait_wifi))
                }
            },
        )
    }
}

@Composable
private fun IdleCard(viewModel: AboutViewModel, state: UpdateState, channel: UpdateChannel, actions: CardActions, modifier: Modifier) {
    val formats = rememberFormats()
    val lastCheckedAt by viewModel.lastCheckedAt.collectAsStateWithLifecycle()
    val known by viewModel.knownUpdate.collectAsStateWithLifecycle()
    val knownUpdate = known
    if (knownUpdate != null && state == UpdateState.Idle) {
        UpdateCard(icon = Icons.Filled.NewReleases, heading = stringResource(R.string.update_available), modifier = modifier) {
            VersionText(displayVersion(knownUpdate.versionName, knownUpdate.versionCode))
            CardText(stringResource(R.string.pg_update_known_hint))
            CardButtons {
                Button(onClick = viewModel::checkForUpdate, modifier = Modifier.focusRequester(actions.primaryFocus).focusHighlight(PillShape)) {
                    Text(stringResource(R.string.pg_update_fetch))
                }
            }
        }
        return
    }
    val okContainer = MaterialTheme.status.okContainer
    val onOk = MaterialTheme.status.onOkContainer
    Surface(shape = RoundedCornerShape(28.dp), color = okContainer, contentColor = onOk, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(if (lastCheckedAt > 0L) R.string.update_up_to_date else R.string.pg_update_never_checked),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (lastCheckedAt > 0L) {
                        stringResource(R.string.pg_update_last_check, formats.dayTime(lastCheckedAt, System.currentTimeMillis(), seconds = false), channelLabel(channel))
                    } else {
                        stringResource(R.string.pg_update_auto_desc)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = onOk.copy(alpha = 0.8f),
                )
            }
            FilledTonalButton(onClick = viewModel::checkForUpdate, modifier = Modifier.focusHighlight(PillShape)) {
                Text(stringResource(R.string.check_update))
            }
        }
    }
}

@Composable
private fun AvailableCard(info: UpdateInfo, tv: Boolean, actions: CardActions, modifier: Modifier) {
    UpdateCard(icon = Icons.Filled.NewReleases, heading = stringResource(R.string.update_available), modifier = modifier) {
        VersionLine(info)
        CardText(updateMeta(info))
        val notes = info.notes.filter { it.isNotBlank() }
        if (notes.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                notes.forEach { Text("•  $it", style = MaterialTheme.typography.bodyMedium) }
            }
        }
        CardButtons {
            TextButton(onClick = { actions.viewModel.dismiss(UpdateState.Available(info)) }, modifier = Modifier.focusHighlight(PillShape)) {
                Text(stringResource(R.string.update_later))
            }
            Button(onClick = { actions.startDownload(info) }, modifier = Modifier.focusRequester(actions.primaryFocus).focusHighlight(PillShape)) {
                ButtonIcon(Icons.Filled.Download)
                Text(stringResource(if (tv) R.string.update_now else R.string.pg_update_download_install))
            }
        }
    }
}

@Composable
private fun WaitingCard(s: UpdateState.Waiting, actions: CardActions, modifier: Modifier) {
    val (icon, heading, desc) = when (s.reason) {
        WaitReason.UNMETERED -> Triple(Icons.Filled.Download, R.string.upd_waiting_wifi, R.string.upd_waiting_wifi_desc)
        WaitReason.NETWORK -> Triple(Icons.Filled.CloudOff, R.string.upd_waiting_network, R.string.upd_waiting_network_desc)
        WaitReason.RETRY -> Triple(Icons.Filled.CloudOff, R.string.upd_waiting_retry, R.string.upd_waiting_retry_desc)
        WaitReason.STARTING -> Triple(Icons.Filled.Download, R.string.upd_waiting_starting, R.string.pg_update_monitoring_continues)
    }
    UpdateCard(icon = icon, heading = stringResource(heading), modifier = modifier) {
        VersionLine(s.info)
        if (s.downloaded > 0 && s.total > 0) {
            val percent = (s.downloaded * 100 / s.total).toInt().coerceIn(0, 100)
            CardText(stringResource(R.string.pg_update_progress, percent, formatFileSize(s.downloaded), formatFileSize(s.total)))
        }
        CardText(stringResource(desc))
        CardButtons {
            TextButton(onClick = actions.viewModel::cancelDownload, modifier = Modifier.focusHighlight(PillShape)) {
                Text(stringResource(R.string.pg_update_cancel_download))
            }
            if (s.reason == WaitReason.UNMETERED) {
                Button(
                    onClick = { actions.viewModel.downloadUpdate(s.info, allowMetered = true) },
                    modifier = Modifier.focusRequester(actions.primaryFocus).focusHighlight(PillShape),
                ) { Text(stringResource(R.string.pg_update_download_anyway)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DownloadingCard(s: UpdateState.Downloading, tv: Boolean, actions: CardActions, modifier: Modifier) {
    UpdateCard(icon = Icons.Filled.Download, heading = stringResource(R.string.pg_update_downloading), modifier = modifier) {
        VersionLine(s.info)
        if (!tv) {
            val animate = rememberAnimationsEnabled()
            LinearWavyProgressIndicator(
                progress = { s.progress / 100f },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                waveSpeed = if (animate) 20.dp else 0.dp,
            )
        }
        Row(Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.pg_update_progress, s.progress, formatFileSize(s.downloaded), formatFileSize(s.total)),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            if (s.speed > 0) {
                Text(stringResource(R.string.update_download_speed, formatFileSize(s.speed)), style = MaterialTheme.typography.bodyMedium)
            }
        }
        CardText(stringResource(if (tv) R.string.upd_tv_progress_hint else R.string.pg_update_monitoring_continues))
        CardButtons {
            TextButton(onClick = actions.viewModel::cancelDownload, modifier = Modifier.focusHighlight(PillShape)) {
                Text(stringResource(R.string.pg_update_cancel_download))
            }
            FilledTonalButton(onClick = actions.viewModel::pauseDownload, modifier = Modifier.focusRequester(actions.primaryFocus).focusHighlight(PillShape)) {
                ButtonIcon(Icons.Filled.Pause)
                Text(stringResource(R.string.upd_pause))
            }
        }
    }
}

@Composable
private fun ReadyCard(s: UpdateState.ReadyToInstall, alertActive: Boolean, actions: CardActions, modifier: Modifier) {
    val context = LocalContext.current
    val viewModel = actions.viewModel
    UpdateCard(icon = Icons.Filled.Verified, heading = stringResource(R.string.pg_update_verified), modifier = modifier) {
        VersionLine(s.info)
        if (s.deferred) {
            CardText(stringResource(R.string.upd_ready_deferred))
        } else {
            CardText(stringResource(R.string.upd_verified_detail, stringResource(R.string.pg_update_verified_desc)))
            CardText(stringResource(R.string.pg_update_install_note))
            if (!SessionInstaller.canRequestInstalls(context)) CardText(stringResource(R.string.upd_permission_first))
        }
        if (alertActive) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                CardText(stringResource(R.string.upd_alert_active))
            }
        }
        CardButtons {
            if (!s.deferred) {
                TextButton(onClick = viewModel::deferInstall, modifier = Modifier.focusHighlight(PillShape)) {
                    Text(stringResource(R.string.update_later))
                }
            }
            Button(
                onClick = viewModel::installUpdate,
                enabled = !alertActive,
                modifier = Modifier.focusRequester(actions.primaryFocus).focusHighlight(PillShape),
            ) {
                ButtonIcon(Icons.Filled.SystemUpdate)
                Text(stringResource(R.string.pg_update_install_now))
            }
        }
    }
}

@Composable
private fun ErrorCard(s: UpdateState.Error, actions: CardActions, modifier: Modifier) {
    val viewModel = actions.viewModel
    val c = MaterialTheme.colorScheme
    val heading = when (s.stage) {
        UpdateState.Stage.CHECK -> R.string.upd_err_title_check
        UpdateState.Stage.DOWNLOAD -> R.string.upd_err_title_download
        UpdateState.Stage.VERIFY -> R.string.upd_err_title_verify
        UpdateState.Stage.INSTALL -> R.string.upd_err_title_install
    }
    val reinstall = s.stage == UpdateState.Stage.INSTALL && s.failure != UpdateFailure.FILE_MISSING && s.failure != UpdateFailure.SHA256
    Surface(shape = RoundedCornerShape(28.dp), color = c.errorContainer, contentColor = c.onErrorContainer, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.ErrorOutline, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(heading), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            s.retryInfo?.let { Text(displayVersion(it.versionName, it.versionCode), style = MaterialTheme.typography.bodySmall) }
            Text(failureText(s.failure, s.detail), style = MaterialTheme.typography.bodyMedium)
            if (!s.failure.hasArgument && !s.detail.isNullOrBlank()) {
                Text(s.detail, style = MaterialTheme.typography.bodySmall, color = c.onErrorContainer.copy(alpha = 0.8f))
            }
            CardButtons {
                TextButton(onClick = { viewModel.dismiss(s) }, modifier = Modifier.focusHighlight(PillShape)) {
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    onClick = {
                        when {
                            s.stage == UpdateState.Stage.CHECK -> viewModel.checkForUpdate()
                            reinstall -> viewModel.retryInstall()
                            else -> s.retryInfo?.let(actions.startDownload) ?: viewModel.checkForUpdate()
                        }
                    },
                    modifier = Modifier.focusRequester(actions.primaryFocus).focusHighlight(PillShape),
                ) { Text(stringResource(if (reinstall) R.string.pg_update_install_again else R.string.update_retry)) }
            }
        }
    }
}

@Composable
internal fun failureText(failure: UpdateFailure, detail: String?): String =
    if (failure.hasArgument) stringResource(failure.message, detail.orEmpty()) else stringResource(failure.message)

/** "2.0.0" with the 预览版 badge for prereleases. */
@Composable
private fun VersionLine(info: UpdateInfo) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        VersionText(displayVersion(info.versionName, info.versionCode))
        if (info.prerelease) {
            Spacer(Modifier.width(10.dp))
            Pill(stringResource(R.string.pg_channel_preview), MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.onTertiary)
        }
    }
}

@Composable
private fun updateMeta(info: UpdateInfo): String {
    val parts = buildList {
        if (info.size > 0) add(formatFileSize(info.size))
        info.publishedAt.takeIf { it.isNotBlank() }?.let { add(stringResource(R.string.pg_update_published, it.take(10))) }
        add(stringResource(if (info.prerelease) R.string.pg_channel_preview else R.string.pg_channel_stable))
    }
    return parts.joinToString(" · ")
}

private val PillShape = RoundedCornerShape(50)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Loading() {
    LoadingIndicator(modifier = Modifier.size(40.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
}

@Composable
private fun ButtonIcon(icon: ImageVector) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
    Spacer(Modifier.width(6.dp))
}

@Composable
private fun UpdateCard(
    icon: ImageVector,
    heading: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    content: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    body: @Composable () -> Unit,
) {
    Surface(shape = RoundedCornerShape(28.dp), color = container, contentColor = content, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(heading, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            body()
        }
    }
}

@Composable
private fun VersionText(version: String) {
    Text(version, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.headlineMedium)
}

@Composable
private fun CardText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun CardButtons(content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
        content()
    }
}

private fun isMetered(context: Context): Boolean =
    context.getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered ?: false

/** 安装未知应用 for this app (Android 8+), with the app details page as fallback. */
private fun openUnknownSources(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
    } else {
        Intent(Settings.ACTION_SECURITY_SETTINGS)
    }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
