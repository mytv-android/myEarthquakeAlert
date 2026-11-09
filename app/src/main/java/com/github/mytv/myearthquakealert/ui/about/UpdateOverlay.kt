package com.github.mytv.myearthquakealert.ui.about

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.service.ActiveAlertHolder
import com.github.mytv.myearthquakealert.ui.about.AboutViewModel.UpdateState
import com.github.mytv.myearthquakealert.ui.components.rememberAnimationsEnabled
import com.github.mytv.myearthquakealert.update.UpdateController
import com.github.mytv.myearthquakealert.update.formatFileSize

/**
 * TV (design §8 电视): the download progress at the bottom-right of every screen, since TV
 * launchers do not show notifications. Not focusable; hidden while an alert is active.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UpdateProgressOverlay(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val states = remember(context) { UpdateUi.states(UpdateController.get(context)) }
    val state by states.collectAsStateWithLifecycle()
    val alert by ActiveAlertHolder.activeAlert.collectAsStateWithLifecycle()
    val visible = alert == null && (state is UpdateState.Downloading || state is UpdateState.Verifying)
    val animate = rememberAnimationsEnabled()

    AnimatedVisibility(
        visible = visible,
        modifier = modifier.padding(end = 48.dp, bottom = 32.dp),
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
            modifier = Modifier.width(340.dp),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val s = state
                val info = (s as? UpdateState.Downloading)?.info ?: (s as? UpdateState.Verifying)?.updateInfo
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (s is UpdateState.Verifying) Icons.Filled.Verified else Icons.Filled.Download,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.upd_tv_overlay_title, info?.let { displayVersion(it.versionName, it.versionCode) }.orEmpty()),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (s is UpdateState.Downloading) {
                    LinearWavyProgressIndicator(
                        progress = { s.progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        waveSpeed = if (animate) 20.dp else 0.dp,
                    )
                    val speed = if (s.speed > 0) " · " + stringResource(R.string.update_download_speed, formatFileSize(s.speed)) else ""
                    Text(
                        stringResource(R.string.pg_update_progress, s.progress, formatFileSize(s.downloaded), formatFileSize(s.total)) + speed,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        stringResource(R.string.pg_update_verifying_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
