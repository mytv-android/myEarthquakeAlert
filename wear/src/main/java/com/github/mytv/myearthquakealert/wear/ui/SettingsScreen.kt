package com.github.mytv.myearthquakealert.wear.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ConfirmationDialog
import androidx.wear.compose.material3.ConfirmationDialogDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.OpenOnPhoneDialog
import androidx.wear.compose.material3.OpenOnPhoneDialogDefaults
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.material3.openOnPhoneDialogCurvedText
import com.github.mytv.myearthquakealert.wear.BuildConfig
import com.github.mytv.myearthquakealert.wear.R
import com.github.mytv.myearthquakealert.wear.data.PhoneLink
import com.github.mytv.myearthquakealert.wear.data.WatchSettings
import com.github.mytv.myearthquakealert.wear.data.WatchStore
import com.github.mytv.myearthquakealert.wear.source.EewSource
import com.github.mytv.myearthquakealert.wear.standalone.StandaloneMonitorService
import com.github.mytv.myearthquakealert.wear.ui.theme.WatchColors
import kotlinx.coroutines.launch

/**
 * 手表 › 设置: 独立监测 and its data source, the permissions the watch needs, the version and
 * 「在手机上查看更新」 (opens 设置 of the phone app, where updates are checked).
 */
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by WatchSettings.flow(context).collectAsStateWithLifecycle(WatchSettings.Values())
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()

    // Re-read permission states whenever the screen comes back (system dialogs, settings).
    var permissionTick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        permissionTick += 1
        onPauseOrDispose { }
    }
    val notificationsOk = remember(permissionTick) { notificationsGranted(context) }
    val locationOk = remember(permissionTick) { locationGranted(context) }
    val fullScreenOk = remember(permissionTick) { fullScreenGranted(context) }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionTick += 1 }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissionTick += 1 }

    var openedOnPhone by remember { mutableStateOf(false) }
    var phoneFailed by remember { mutableStateOf(false) }

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader(modifier = Modifier.transformedHeight(this, spec), transformation = SurfaceTransformation(spec)) {
                    Text(stringResource(R.string.settings_title))
                }
            }
            item {
                SwitchButton(
                    checked = settings.standaloneEnabled,
                    onCheckedChange = { on ->
                        scope.launch {
                            WatchSettings.setStandalone(context, on)
                            WatchStore.updateStandalone { it.copy(enabled = on) }
                            if (on) {
                                if (!locationGranted(context)) {
                                    locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                                }
                                StandaloneMonitorService.start(context)
                            } else {
                                StandaloneMonitorService.stop(context)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                    secondaryLabel = { Text(stringResource(R.string.settings_standalone_desc), maxLines = 3) },
                    label = { Text(stringResource(R.string.settings_standalone)) },
                )
            }
            if (settings.standaloneEnabled) {
                item {
                    ListHeader(modifier = Modifier.transformedHeight(this, spec), transformation = SurfaceTransformation(spec)) {
                        Text(stringResource(R.string.settings_source))
                    }
                }
                items(EewSource.entries.toList(), key = { it.name }) { source ->
                    RadioButton(
                        selected = settings.standaloneSource == source,
                        onSelect = { scope.launch { WatchSettings.setSource(context, source) } },
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                        label = { Text(source.label) },
                    )
                }
            }
            if (!notificationsOk || (settings.standaloneEnabled && !locationOk) || !fullScreenOk) {
                item {
                    ListHeader(modifier = Modifier.transformedHeight(this, spec), transformation = SurfaceTransformation(spec)) {
                        Text(stringResource(R.string.settings_permissions))
                    }
                }
            }
            if (!notificationsOk) {
                item {
                    PermissionButton(R.string.perm_notifications, R.string.perm_notifications_desc, R.drawable.ic_notifications, Modifier.transformedHeight(this, spec), SurfaceTransformation(spec)) {
                        if (Build.VERSION.SDK_INT >= 33) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else openAppSettings(context)
                    }
                }
            }
            if (settings.standaloneEnabled && !locationOk) {
                item {
                    PermissionButton(R.string.perm_location, R.string.perm_location_desc, R.drawable.ic_my_location, Modifier.transformedHeight(this, spec), SurfaceTransformation(spec)) {
                        locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    }
                }
            }
            if (!fullScreenOk) {
                item {
                    PermissionButton(R.string.perm_full_screen, R.string.perm_full_screen_desc, R.drawable.ic_warning, Modifier.transformedHeight(this, spec), SurfaceTransformation(spec)) {
                        openFullScreenSettings(context)
                    }
                }
            }
            item {
                ListHeader(modifier = Modifier.transformedHeight(this, spec), transformation = SurfaceTransformation(spec)) {
                    Text(stringResource(R.string.settings_about))
                }
            }
            item {
                Text(
                    stringResource(R.string.settings_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).transformedHeight(this, spec),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = WatchColors.Sub,
                )
            }
            item {
                Button(
                    onClick = {
                        scope.launch {
                            if (PhoneLink.openOnPhone(context, PhoneLink.ROUTE_SETTINGS)) openedOnPhone = true else phoneFailed = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    icon = { Icon(painterResource(R.drawable.ic_phone_update), contentDescription = null) },
                    secondaryLabel = { Text(stringResource(R.string.settings_update_desc), maxLines = 2) },
                    label = { Text(stringResource(R.string.settings_update)) },
                )
            }
        }
    }

    val phoneStyle = OpenOnPhoneDialogDefaults.curvedTextStyle
    val continueText = stringResource(R.string.open_on_phone)
    OpenOnPhoneDialog(
        visible = openedOnPhone,
        onDismissRequest = { openedOnPhone = false },
        curvedText = { openOnPhoneDialogCurvedText(continueText, phoneStyle) },
    )
    ConfirmationDialog(
        visible = phoneFailed,
        onDismissRequest = { phoneFailed = false },
        text = { Text(stringResource(R.string.err_no_phone), textAlign = TextAlign.Center) },
        colors = ConfirmationDialogDefaults.failureColors(),
    ) {
        ConfirmationDialogDefaults.ConnectionFailureIcon()
    }
}

@Composable
private fun PermissionButton(
    label: Int,
    description: Int,
    icon: Int,
    modifier: Modifier,
    transformation: SurfaceTransformation,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        transformation = transformation,
        colors = ButtonDefaults.filledTonalButtonColors(),
        icon = { Icon(painterResource(icon), contentDescription = null) },
        secondaryLabel = { Text(stringResource(description), maxLines = 2) },
        label = { Text(stringResource(label)) },
    )
}

private fun notificationsGranted(context: Context): Boolean =
    Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private fun locationGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun fullScreenGranted(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < 34) return true
    return try {
        context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() ?: true
    } catch (e: Exception) {
        true
    }
}

private fun openFullScreenSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= 34) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + context.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        } catch (e: Exception) {
            // Not offered by this watch: fall back to the app's settings page.
        }
    }
    openAppSettings(context)
}

private fun openAppSettings(context: Context) {
    try {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (e: Exception) {
        // No settings page available; nothing else to open.
    }
}
