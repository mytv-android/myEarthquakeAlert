package com.github.mytv.myearthquakealert.ui.about

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.mytv.myearthquakealert.MyEarthQuakeAlertApp
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.data.repository.UserSettings
import com.github.mytv.myearthquakealert.ui.settings.components.SettingsPageScaffold
import kotlinx.coroutines.launch

/**
 * The 1.0 about route, kept for callers that still navigate to it. It shows the same
 * 关于与更新 content as 设置 › 关于与更新, followed by the licences and data sources.
 */
@Composable
fun AboutScreen(
    viewModel: AboutViewModel,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as MyEarthQuakeAlertApp
    val scope = rememberCoroutineScope()
    val settings by app.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = UserSettings())

    LaunchedEffect(settings.updateAutoCheck) { viewModel.autoCheckIfDue(settings.updateAutoCheck) }

    val openUrl: (String) -> Unit = { url ->
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.pg_no_browser, Toast.LENGTH_SHORT).show()
        }
    }

    SettingsPageScaffold(title = stringResource(R.string.pg_about_update), onBack = onNavigateBack) {
        updateItems(
            viewModel = viewModel,
            preferences = UpdatePreferences(settings.updateAutoCheck, settings.updateWifiOnly, settings.updateChannel),
            onAutoCheckChange = { scope.launch { app.settingsRepository.updateUpdateAutoCheck(it) } },
            onWifiOnlyChange = { scope.launch { app.settingsRepository.updateUpdateWifiOnly(it) } },
            onChannelChange = { scope.launch { app.settingsRepository.updateUpdateChannel(it) } },
            onOpenUrl = openUrl,
            onOpenAbout = null,
        )
        aboutItems(settings.updateChannel, openUrl, showIdentity = false)
    }
}
