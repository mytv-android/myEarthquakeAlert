package com.github.mytv.myearthquakealert.ui.about

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.mytv.myearthquakealert.BuildConfig
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.ui.adaptive.currentLayoutMode
import com.github.mytv.myearthquakealert.ui.adaptive.LayoutMode
import com.github.mytv.myearthquakealert.ui.theme.EeqSpacing
import com.github.mytv.myearthquakealert.util.ApkInstaller

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    viewModel: AboutViewModel,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val updateState by viewModel.updateState.collectAsState()
    val layoutMode = currentLayoutMode()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when (layoutMode) {
            LayoutMode.COMPACT -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(EeqSpacing.md)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(EeqSpacing.md),
                ) {
                    AppInfoCard()
                    VersionInfoCard(
                        onCheckUpdate = { viewModel.checkForUpdate() },
                        onViewChangelog = {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                data = Uri.parse("https://github.com/mytv-android/myEarthquakeAlert/releases")
                            }
                            context.startActivity(intent)
                        },
                    )
                    DataSourcesCard(
                        onOpenLink = { url ->
                            val intent = Intent(Intent.ACTION_VIEW).apply { data = Uri.parse(url) }
                            context.startActivity(intent)
                        },
                    )
                    LicenseCard(
                        onViewLicense = {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                data = Uri.parse("https://github.com/mytv-android/myEarthquakeAlert/blob/main/LICENSE")
                            }
                            context.startActivity(intent)
                        },
                    )
                    AcknowledgmentsCard()
                }
            }
            LayoutMode.MEDIUM, LayoutMode.EXPANDED -> {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(EeqSpacing.md)
                        .verticalScroll(scrollState),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(EeqSpacing.md),
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(EeqSpacing.md),
                        ) {
                            AppInfoCard()
                            DataSourcesCard(onOpenLink = { url ->
                                val intent = Intent(Intent.ACTION_VIEW).apply { data = Uri.parse(url) }
                                context.startActivity(intent)
                            })
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(EeqSpacing.md),
                        ) {
                            VersionInfoCard(
                                onCheckUpdate = { viewModel.checkForUpdate() },
                                onViewChangelog = {
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        data = Uri.parse("https://github.com/mytv-android/myEarthquakeAlert/releases")
                                    }
                                    context.startActivity(intent)
                                },
                            )
                            LicenseCard(
                                onViewLicense = {
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        data = Uri.parse("https://github.com/mytv-android/myEarthquakeAlert/blob/main/LICENSE")
                                    }
                                    context.startActivity(intent)
                                },
                            )
                            AcknowledgmentsCard()
                        }
                    }
                }
            }
        }

        when (val state = updateState) {
            is AboutViewModel.UpdateState.Checking -> {
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text(stringResource(R.string.update_checking)) },
                    text = { CircularProgressIndicator() },
                    confirmButton = {},
                )
            }
            is AboutViewModel.UpdateState.Available -> {
                UpdateDialog(
                    updateInfo = state.updateInfo,
                    onDownload = { viewModel.downloadUpdate(state.updateInfo) },
                    onDismiss = { viewModel.resetState() },
                )
            }
            is AboutViewModel.UpdateState.UpToDate -> {
                LaunchedEffect(Unit) {
                    Toast.makeText(context, context.getString(R.string.update_up_to_date), Toast.LENGTH_SHORT).show()
                    viewModel.resetState()
                }
            }
            is AboutViewModel.UpdateState.Downloading -> {
                DownloadProgressDialog(
                    progress = state.progress,
                    downloaded = state.downloaded,
                    total = state.total,
                    speed = state.speed,
                    onCancel = { viewModel.cancelDownload() },
                )
            }
            is AboutViewModel.UpdateState.Downloaded -> {
                LaunchedEffect(Unit) {
                    ApkInstaller.installApk(context, state.apkFile)
                    viewModel.resetState()
                }
            }
            is AboutViewModel.UpdateState.Error -> {
                AlertDialog(
                    onDismissRequest = { viewModel.resetState() },
                    title = { Text(stringResource(R.string.update_error)) },
                    text = { Text(state.message) },
                    confirmButton = {
                        TextButton(onClick = { viewModel.resetState() }) {
                            Text("OK")
                        }
                    },
                )
            }
            else -> {}
        }
    }
}

@Composable
private fun AppInfoCard() {
    Card {
        Column(
            modifier = Modifier.padding(EeqSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(EeqSpacing.sm),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.app_tagline),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(R.string.app_description),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun VersionInfoCard(
    onCheckUpdate: () -> Unit,
    onViewChangelog: () -> Unit,
) {
    Card {
        Column(
            modifier = Modifier.padding(EeqSpacing.md),
            verticalArrangement = Arrangement.spacedBy(EeqSpacing.sm),
        ) {
            Text(
                text = stringResource(R.string.version_info),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "${stringResource(R.string.current_version)}: ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "${stringResource(R.string.version_code)}: ${BuildConfig.VERSION_CODE}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "${stringResource(R.string.build_date)}: 2026-05-29",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(EeqSpacing.sm),
            ) {
                FilledTonalButton(onClick = onCheckUpdate) {
                    Text(stringResource(R.string.check_update))
                }
                TextButton(onClick = onViewChangelog) {
                    Text(stringResource(R.string.view_changelog))
                }
            }
        }
    }
}

@Composable
private fun DataSourcesCard(onOpenLink: (String) -> Unit) {
    Card {
        Column(
            modifier = Modifier.padding(EeqSpacing.md),
            verticalArrangement = Arrangement.spacedBy(EeqSpacing.sm),
        ) {
            Text(
                text = stringResource(R.string.data_sources),
                style = MaterialTheme.typography.titleMedium,
            )
            DataSourceItem(
                label = stringResource(R.string.data_source_eew),
                value = "Wolfx Open API",
                onClick = { onOpenLink("https://wolfx.jp/apidoc") },
            )
            DataSourceItem(
                label = stringResource(R.string.data_source_map),
                value = "OpenStreetMap",
                onClick = { onOpenLink("https://www.openstreetmap.org/") },
            )
            DataSourceItem(
                label = stringResource(R.string.data_source_algorithm),
                value = "kanameishi",
                onClick = { onOpenLink("https://github.com/Lipomoea/kanameishi") },
            )
        }
    }
}

@Composable
private fun DataSourceItem(
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    Column {
        Text(
            text = "• $label",
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(
            onClick = onClick,
            contentPadding = PaddingValues(start = EeqSpacing.md, top = 0.dp, end = 0.dp, bottom = 0.dp),
        ) {
            Text(value)
        }
    }
}

@Composable
private fun LicenseCard(onViewLicense: () -> Unit) {
    Card {
        Column(
            modifier = Modifier.padding(EeqSpacing.md),
            verticalArrangement = Arrangement.spacedBy(EeqSpacing.sm),
        ) {
            Text(
                text = stringResource(R.string.license),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Apache License 2.0",
                style = MaterialTheme.typography.bodyMedium,
            )
            FilledTonalButton(onClick = onViewLicense) {
                Text(stringResource(R.string.view_full_license))
            }
        }
    }
}

@Composable
private fun AcknowledgmentsCard() {
    Card {
        Column(
            modifier = Modifier.padding(EeqSpacing.md),
            verticalArrangement = Arrangement.spacedBy(EeqSpacing.sm),
        ) {
            Text(
                text = stringResource(R.string.acknowledgments),
                style = MaterialTheme.typography.titleMedium,
            )
            Text("• ${stringResource(R.string.acknowledgment_wolfx)}")
            Text("• ${stringResource(R.string.acknowledgment_kanameishi)}")
            Text("• ${stringResource(R.string.acknowledgment_osm)}")
            Text("• ${stringResource(R.string.acknowledgment_developers)}")
        }
    }
}
