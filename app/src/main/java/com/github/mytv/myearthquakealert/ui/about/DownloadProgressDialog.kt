package com.github.mytv.myearthquakealert.ui.about

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.ui.theme.EeqSpacing
import com.github.mytv.myearthquakealert.util.ApkInstaller

@Composable
fun DownloadProgressDialog(
    progress: Int,
    downloaded: Long,
    total: Long,
    speed: Long,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(text = stringResource(R.string.update_downloading))
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(EeqSpacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(
                        R.string.update_download_progress,
                        progress,
                        ApkInstaller.formatFileSize(downloaded),
                        ApkInstaller.formatFileSize(total)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (speed > 0) {
                    Text(
                        text = stringResource(
                            R.string.update_download_speed,
                            ApkInstaller.formatSpeed(speed)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
