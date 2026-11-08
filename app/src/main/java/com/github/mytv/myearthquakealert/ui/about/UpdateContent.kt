package com.github.mytv.myearthquakealert.ui.about

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.mytv.myearthquakealert.BuildConfig
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.data.model.UpdateChannel
import com.github.mytv.myearthquakealert.data.model.UpdateInfo
import com.github.mytv.myearthquakealert.ui.components.rememberAnimationsEnabled
import com.github.mytv.myearthquakealert.ui.settings.components.focusHighlight
import com.github.mytv.myearthquakealert.ui.settings.components.rememberFormats
import com.github.mytv.myearthquakealert.ui.theme.status
import com.github.mytv.myearthquakealert.util.ApkInstaller

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

/**
 * The state card of 关于与更新 (prototype scrUpdate): up to date / available with notes /
 * downloading with a wavy progress line / verifying / ready to install / error with retry.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UpdateStateCard(
    viewModel: AboutViewModel,
    channel: UpdateChannel,
    wifiOnly: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val formats = rememberFormats()
    val state by viewModel.updateState.collectAsStateWithLifecycle()
    val lastCheckedAt by viewModel.lastCheckedAt.collectAsStateWithLifecycle()
    val known by viewModel.knownUpdate.collectAsStateWithLifecycle()
    var meteredConfirm by remember { mutableStateOf<UpdateInfo?>(null) }
    val now = System.currentTimeMillis()

    fun startDownload(info: UpdateInfo) {
        if (wifiOnly && isMetered(context)) meteredConfirm = info else viewModel.downloadUpdate(info)
    }

    when (val s = state) {
        AboutViewModel.UpdateState.Idle, AboutViewModel.UpdateState.UpToDate -> {
            val knownUpdate = known
            if (knownUpdate != null && s == AboutViewModel.UpdateState.Idle) {
                UpdateCard(icon = Icons.Filled.NewReleases, heading = stringResource(R.string.update_available), modifier = modifier) {
                    VersionText(knownUpdate.versionName)
                    CardText(stringResource(R.string.pg_update_known_hint))
                    CardButtons {
                        Button(onClick = { viewModel.checkForUpdate() }, modifier = Modifier.focusHighlight(RoundedCornerShape(50))) {
                            Text(stringResource(R.string.pg_update_fetch))
                        }
                    }
                }
            } else {
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
                                    stringResource(R.string.pg_update_last_check, formats.dayTime(lastCheckedAt, now, seconds = false), channelLabel(channel))
                                } else {
                                    stringResource(R.string.pg_update_auto_desc)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = onOk.copy(alpha = 0.8f),
                            )
                        }
                        FilledTonalButton(onClick = { viewModel.checkForUpdate() }, modifier = Modifier.focusHighlight(RoundedCornerShape(50))) {
                            Text(stringResource(R.string.check_update))
                        }
                    }
                }
            }
        }
        AboutViewModel.UpdateState.Checking -> {
            UpdateCard(icon = Icons.Filled.SystemUpdate, heading = stringResource(R.string.update_checking), modifier = modifier) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LoadingIndicator(modifier = Modifier.size(40.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.width(12.dp))
                    CardText(stringResource(R.string.pg_update_checking_desc))
                }
            }
        }
        is AboutViewModel.UpdateState.Available -> {
            val info = s.updateInfo
            UpdateCard(icon = Icons.Filled.NewReleases, heading = stringResource(R.string.update_available), modifier = modifier) {
                VersionText(info.versionName)
                CardText(updateMeta(info, channel))
                val notes = info.notes.filter { it.isNotBlank() }
                if (notes.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        notes.forEach { Text("•  $it", style = MaterialTheme.typography.bodyMedium) }
                    }
                }
                CardButtons {
                    TextButton(onClick = { viewModel.resetState() }, modifier = Modifier.focusHighlight(RoundedCornerShape(50))) {
                        Text(stringResource(R.string.update_later))
                    }
                    Button(onClick = { startDownload(info) }, modifier = Modifier.focusHighlight(RoundedCornerShape(50))) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.pg_update_download_install))
                    }
                }
            }
        }
        is AboutViewModel.UpdateState.Downloading -> {
            val animate = rememberAnimationsEnabled()
            UpdateCard(icon = Icons.Filled.Download, heading = stringResource(R.string.pg_update_downloading), modifier = modifier) {
                s.info?.let { VersionText(it.versionName) }
                LinearWavyProgressIndicator(
                    progress = { s.progress / 100f },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    waveSpeed = if (animate) 20.dp else 0.dp,
                )
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.pg_update_progress, s.progress, ApkInstaller.formatFileSize(s.downloaded), ApkInstaller.formatFileSize(s.total)),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (s.speed > 0) {
                        Text(stringResource(R.string.update_download_speed, ApkInstaller.formatFileSize(s.speed)), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                CardText(stringResource(R.string.pg_update_monitoring_continues))
                CardButtons {
                    TextButton(onClick = { viewModel.cancelDownload() }, modifier = Modifier.focusHighlight(RoundedCornerShape(50))) {
                        Text(stringResource(R.string.pg_update_cancel_download))
                    }
                }
            }
        }
        is AboutViewModel.UpdateState.Verifying -> {
            UpdateCard(icon = Icons.Filled.Verified, heading = stringResource(R.string.pg_update_verifying), modifier = modifier) {
                VersionText(s.updateInfo.versionName)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LoadingIndicator(modifier = Modifier.size(40.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.width(12.dp))
                    CardText(stringResource(R.string.pg_update_verifying_desc))
                }
            }
        }
        is AboutViewModel.UpdateState.Downloaded -> {
            UpdateCard(icon = Icons.Filled.Verified, heading = stringResource(R.string.pg_update_verified), modifier = modifier) {
                s.info?.let { VersionText(it.versionName) }
                CardText(stringResource(R.string.pg_update_verified_desc))
                CardText(stringResource(R.string.pg_update_install_note))
                CardButtons {
                    TextButton(onClick = { viewModel.resetState() }, modifier = Modifier.focusHighlight(RoundedCornerShape(50))) {
                        Text(stringResource(R.string.update_later))
                    }
                    Button(onClick = { viewModel.installUpdate(s.apkFile, s.info) }, modifier = Modifier.focusHighlight(RoundedCornerShape(50))) {
                        Icon(Icons.Filled.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.pg_update_install_now))
                    }
                }
            }
        }
        is AboutViewModel.UpdateState.NeedsInstallPermission -> {
            UpdateCard(icon = Icons.Filled.InstallMobile, heading = stringResource(R.string.pg_update_permission_title), modifier = modifier) {
                s.info?.let { VersionText(it.versionName) }
                CardText(stringResource(R.string.pg_update_permission_desc))
                CardButtons {
                    TextButton(onClick = { viewModel.installUpdate(s.apkFile, s.info) }, modifier = Modifier.focusHighlight(RoundedCornerShape(50))) {
                        Text(stringResource(R.string.pg_update_install_again))
                    }
                    Button(onClick = { openUnknownSources(context) }, modifier = Modifier.focusHighlight(RoundedCornerShape(50))) {
                        Text(stringResource(R.string.pg_update_grant))
                    }
                }
            }
        }
        is AboutViewModel.UpdateState.Error -> {
            val c = MaterialTheme.colorScheme
            Surface(shape = RoundedCornerShape(28.dp), color = c.errorContainer, contentColor = c.onErrorContainer, modifier = modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.ErrorOutline, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.update_error), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Text(s.message, style = MaterialTheme.typography.bodyMedium)
                    CardButtons {
                        TextButton(onClick = { viewModel.resetState() }, modifier = Modifier.focusHighlight(RoundedCornerShape(50))) {
                            Text(stringResource(R.string.cancel))
                        }
                        Button(
                            onClick = { s.retryInfo?.let { startDownload(it) } ?: viewModel.checkForUpdate() },
                            modifier = Modifier.focusHighlight(RoundedCornerShape(50)),
                        ) { Text(stringResource(R.string.update_retry)) }
                    }
                }
            }
        }
    }

    meteredConfirm?.let { info ->
        AlertDialog(
            onDismissRequest = { meteredConfirm = null },
            title = { Text(stringResource(R.string.pg_update_metered_title)) },
            text = { Text(stringResource(R.string.pg_update_metered_desc, ApkInstaller.formatFileSize(info.size))) },
            confirmButton = {
                Button(onClick = { meteredConfirm = null; viewModel.downloadUpdate(info) }, modifier = Modifier.focusHighlight(RoundedCornerShape(50))) {
                    Text(stringResource(R.string.pg_update_download_anyway))
                }
            },
            dismissButton = {
                TextButton(onClick = { meteredConfirm = null }, modifier = Modifier.focusHighlight(RoundedCornerShape(50))) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun updateMeta(info: UpdateInfo, channel: UpdateChannel): String {
    val parts = buildList {
        if (info.size > 0) add(ApkInstaller.formatFileSize(info.size))
        info.publishedAt.takeIf { it.isNotBlank() }?.let { add(stringResource(R.string.pg_update_published, it.take(10))) }
        add(channelLabel(channel))
    }
    return parts.joinToString(" · ")
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

private fun openUnknownSources(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
    } else {
        Intent(Settings.ACTION_SECURITY_SETTINGS)
    }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
