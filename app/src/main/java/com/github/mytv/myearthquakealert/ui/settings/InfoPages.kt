package com.github.mytv.myearthquakealert.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.ui.about.UpdatePreferences
import com.github.mytv.myearthquakealert.ui.about.aboutItems
import com.github.mytv.myearthquakealert.ui.about.updateItems
import com.github.mytv.myearthquakealert.ui.settings.components.SettingsPageScaffold

/** 设置 › 关于与更新 (prototype scrUpdate). */
@Composable
internal fun UpdatePage(env: SettingsEnv, onBack: (() -> Unit)?, onOpenAbout: () -> Unit) {
    val settings = env.settings
    val actions = env.actions
    SettingsPageScaffold(title = stringResource(R.string.pg_about_update), onBack = onBack) {
        updateItems(
            viewModel = env.updateViewModel,
            preferences = UpdatePreferences(settings.updateAutoCheck, settings.updateWifiOnly, settings.updateChannel),
            onAutoCheckChange = { on -> actions.edit { updateUpdateAutoCheck(on) } },
            onWifiOnlyChange = { on -> actions.edit { updateUpdateWifiOnly(on) } },
            onChannelChange = { channel -> actions.edit { updateUpdateChannel(channel) } },
            onOpenUrl = actions::openUrl,
            onOpenAbout = onOpenAbout,
        )
    }
}

/** 设置 › 关于: identity, data sources and attribution, licences. */
@Composable
internal fun AboutPage(env: SettingsEnv, onBack: (() -> Unit)?) {
    SettingsPageScaffold(title = stringResource(R.string.about), onBack = onBack) {
        aboutItems(env.settings.updateChannel, env.actions::openUrl)
    }
}
