package com.github.mytv.myearthquakealert.ui.about

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.data.model.UpdateChannel
import com.github.mytv.myearthquakealert.data.repository.UpdateRepository
import com.github.mytv.myearthquakealert.ui.components.SegmentedGroup
import com.github.mytv.myearthquakealert.ui.components.SegmentedRow
import com.github.mytv.myearthquakealert.ui.components.Subhead
import com.github.mytv.myearthquakealert.ui.settings.components.focusHighlight

const val LICENSE_URL = "https://github.com/mytv-android/myEarthquakeAlert/blob/main/LICENSE"
const val SOURCE_URL = "https://github.com/mytv-android/myEarthquakeAlert"

private data class LinkRow(val icon: ImageVector, val title: String, val subtitle: String, val url: String?)

/** 关于: identity, data sources and attribution, licences and acknowledgements. */
fun LazyListScope.aboutItems(channel: UpdateChannel, onOpenUrl: (String) -> Unit, showIdentity: Boolean = true) {
    if (showIdentity) item(key = "about-id") { AppIdentity(channel) }
    item(key = "about-desc") {
        Text(
            stringResource(R.string.app_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
    item(key = "about-sources") {
        LinkGroup(
            heading = stringResource(R.string.data_sources),
            rows = listOf(
                LinkRow(Icons.Filled.Public, stringResource(R.string.data_source_eew), stringResource(R.string.pg_about_eew_value), "https://wolfx.jp/apidoc"),
                LinkRow(Icons.Filled.LibraryBooks, stringResource(R.string.pg_about_catalog), stringResource(R.string.pg_about_catalog_value), "https://news.ceic.ac.cn/"),
                LinkRow(Icons.Filled.Map, stringResource(R.string.data_source_map), stringResource(R.string.pg_about_map_value), "https://www.openstreetmap.org/copyright"),
                LinkRow(Icons.Filled.Science, stringResource(R.string.data_source_algorithm), stringResource(R.string.pg_about_algorithm_value), "https://github.com/Lipomoea/kanameishi"),
            ),
            onOpenUrl = onOpenUrl,
        )
    }
    item(key = "about-license") {
        LinkGroup(
            heading = stringResource(R.string.license),
            rows = listOf(
                LinkRow(Icons.Filled.Gavel, stringResource(R.string.pg_about_app_license), "Apache License 2.0", LICENSE_URL),
                LinkRow(Icons.Filled.Code, stringResource(R.string.pg_about_source_code), "github.com/mytv-android/myEarthquakeAlert", SOURCE_URL),
                LinkRow(Icons.Filled.LibraryBooks, stringResource(R.string.pg_about_libraries), stringResource(R.string.pg_about_libraries_value), null),
                LinkRow(Icons.Filled.Handshake, stringResource(R.string.pg_about_changelog), stringResource(R.string.pg_about_changelog_value), UpdateRepository.RELEASES_URL),
            ),
            onOpenUrl = onOpenUrl,
        )
    }
    item(key = "about-thanks") {
        LinkGroup(
            heading = stringResource(R.string.acknowledgments),
            rows = listOf(
                LinkRow(Icons.Filled.Favorite, stringResource(R.string.acknowledgment_wolfx), stringResource(R.string.pg_about_thanks_wolfx), "https://wolfx.jp/"),
                LinkRow(Icons.Filled.Favorite, stringResource(R.string.acknowledgment_kanameishi), stringResource(R.string.pg_about_thanks_kanameishi), "https://github.com/Lipomoea/kanameishi"),
                LinkRow(Icons.Filled.Favorite, stringResource(R.string.acknowledgment_osm), stringResource(R.string.pg_about_thanks_osm), "https://www.openstreetmap.org/copyright"),
                LinkRow(Icons.Filled.Favorite, stringResource(R.string.acknowledgment_developers), stringResource(R.string.pg_about_thanks_devs), null),
            ),
            onOpenUrl = onOpenUrl,
        )
    }
}

@Composable
private fun LinkGroup(heading: String, rows: List<LinkRow>, onOpenUrl: (String) -> Unit) = Column {
    Subhead(heading)
    SegmentedGroup(rows.size) { index, shape ->
        val row = rows[index]
        SegmentedRow(
            title = row.title,
            subtitle = row.subtitle,
            icon = row.icon,
            shape = shape,
            modifier = if (row.url != null) Modifier.focusHighlight(shape) else Modifier,
            onClick = row.url?.let { url -> { onOpenUrl(url) } },
            trailing = if (row.url != null) {
                { Icon(Icons.Filled.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) }
            } else {
                null
            },
        )
    }
}
