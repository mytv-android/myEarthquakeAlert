package com.github.mytv.myearthquakealert.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.data.source.EewSource
import com.github.mytv.myearthquakealert.ui.adaptive.handleUserKey

@Composable
fun SourceSelector(
    selected: EewSource,
    onSelected: (EewSource) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.source_selector),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        EewSource.entries.forEach { source ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .handleUserKey { onSelected(source) }
                    .padding(vertical = 4.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = source == selected,
                    onClick = { onSelected(source) },
                )
                Text(text = source.label)
            }
        }
    }
}
