package com.github.mytv.myearthquakealert.ui.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.data.model.UpdateChannel
import com.github.mytv.myearthquakealert.data.repository.UpdateRepository
import com.github.mytv.myearthquakealert.ui.components.SegmentedGroup
import com.github.mytv.myearthquakealert.ui.components.SegmentedRow
import com.github.mytv.myearthquakealert.ui.components.Subhead
import com.github.mytv.myearthquakealert.ui.settings.components.Chevron
import com.github.mytv.myearthquakealert.ui.settings.components.ConnectedChoice
import com.github.mytv.myearthquakealert.ui.settings.components.SettingSwitch
import com.github.mytv.myearthquakealert.ui.settings.components.focusHighlight

/** Settings of the update flow shown on 设置 › 关于与更新. */
data class UpdatePreferences(
    val autoCheck: Boolean,
    val wifiOnly: Boolean,
    val channel: UpdateChannel,
)

/** 关于与更新 (prototype scrUpdate): identity, state card, 更新设置, changelog and licences. */
fun LazyListScope.updateItems(
    viewModel: AboutViewModel,
    preferences: UpdatePreferences,
    onAutoCheckChange: (Boolean) -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
    onChannelChange: (UpdateChannel) -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenAbout: (() -> Unit)?,
) {
    item(key = "upd-id") { AppIdentity(preferences.channel) }
    item(key = "upd-card") { UpdateStateCard(viewModel, preferences.channel, preferences.wifiOnly) }
    item(key = "upd-settings") {
        Column {
            Subhead(stringResource(R.string.pg_update_settings))
            UpdateSettingsGroup(preferences, onAutoCheckChange, onWifiOnlyChange, onChannelChange)
        }
    }
    item(key = "upd-links") {
        Column {
            Subhead(stringResource(R.string.pg_more))
            SegmentedGroup(if (onOpenAbout != null) 2 else 1) { index, shape ->
                if (index == 0) {
                    SegmentedRow(
                        title = stringResource(R.string.view_changelog),
                        subtitle = stringResource(R.string.pg_about_changelog_value),
                        icon = Icons.Filled.Description,
                        shape = shape,
                        modifier = Modifier.focusHighlight(shape),
                        onClick = { onOpenUrl(UpdateRepository.RELEASES_URL) },
                        trailing = { Chevron() },
                    )
                } else if (onOpenAbout != null) {
                    SegmentedRow(
                        title = stringResource(R.string.pg_about_licenses_sources),
                        icon = Icons.Filled.Info,
                        shape = shape,
                        modifier = Modifier.focusHighlight(shape),
                        onClick = onOpenAbout,
                        trailing = { Chevron() },
                    )
                }
            }
        }
    }
}

@Composable
private fun UpdateSettingsGroup(
    preferences: UpdatePreferences,
    onAutoCheckChange: (Boolean) -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
    onChannelChange: (UpdateChannel) -> Unit,
) {
    SegmentedGroup(4) { index, shape ->
        when (index) {
            0 -> SegmentedRow(
                title = stringResource(R.string.pg_update_auto_check),
                subtitle = stringResource(R.string.pg_update_auto_desc),
                icon = Icons.Filled.Refresh,
                shape = shape,
                modifier = Modifier.focusHighlight(shape),
                onClick = { onAutoCheckChange(!preferences.autoCheck) },
                trailing = { SettingSwitch(preferences.autoCheck) },
            )
            1 -> SegmentedRow(
                title = stringResource(R.string.pg_update_wifi_only),
                subtitle = stringResource(R.string.pg_update_wifi_only_desc),
                icon = Icons.Filled.Wifi,
                shape = shape,
                modifier = Modifier.focusHighlight(shape),
                onClick = { onWifiOnlyChange(!preferences.wifiOnly) },
                trailing = { SettingSwitch(preferences.wifiOnly) },
            )
            2 -> SegmentedRow(
                title = stringResource(R.string.pg_update_channel),
                subtitle = stringResource(
                    if (preferences.channel == UpdateChannel.PREVIEW) R.string.pg_channel_preview_desc else R.string.pg_channel_stable_desc
                ),
                icon = Icons.Filled.Tune,
                shape = shape,
                trailing = {
                    ConnectedChoice(
                        options = UpdateChannel.entries.map { it to channelLabel(it) },
                        selected = preferences.channel,
                        onSelect = onChannelChange,
                    )
                },
            )
            else -> SegmentedRow(
                title = stringResource(R.string.pg_update_verification),
                subtitle = stringResource(R.string.pg_update_verification_desc),
                icon = Icons.Filled.Lock,
                shape = shape,
            )
        }
    }
}
