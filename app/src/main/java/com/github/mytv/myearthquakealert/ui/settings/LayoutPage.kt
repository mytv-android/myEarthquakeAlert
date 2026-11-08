package com.github.mytv.myearthquakealert.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CrisisAlert
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DevicesFold
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Splitscreen
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VerticalSplit
import androidx.compose.material.icons.filled.ViewQuilt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.data.model.LayoutOverride
import com.github.mytv.myearthquakealert.ui.adaptive.LayoutKind
import com.github.mytv.myearthquakealert.ui.adaptive.LayoutSource
import com.github.mytv.myearthquakealert.ui.adaptive.SessionLayout
import com.github.mytv.myearthquakealert.ui.components.SegmentedGroup
import com.github.mytv.myearthquakealert.ui.components.Subhead
import com.github.mytv.myearthquakealert.ui.settings.components.InfoNote
import com.github.mytv.myearthquakealert.ui.settings.components.NavRow
import com.github.mytv.myearthquakealert.ui.settings.components.SettingsPageScaffold
import com.github.mytv.myearthquakealert.ui.settings.components.SwitchRow
import com.github.mytv.myearthquakealert.ui.settings.components.focusHighlight
import com.github.mytv.myearthquakealert.ui.theme.MyEarthQuakeAlertTheme

private fun kindIcon(kind: LayoutKind): ImageVector = when (kind) {
    LayoutKind.PHONE -> Icons.Filled.Smartphone
    LayoutKind.MEDIUM -> Icons.Filled.VerticalSplit
    LayoutKind.TABLET -> Icons.Filled.Tablet
    LayoutKind.TV -> Icons.Filled.Tv
    LayoutKind.KIOSK -> Icons.Filled.ViewQuilt
}

/** 设置 › 显示 › 界面布局 (prototype scrLayout, design §2 手动切换界面布局). */
@Composable
internal fun LayoutPage(env: SettingsEnv, onBack: (() -> Unit)?, onShowDialog: (SettingsDialog) -> Unit) {
    val settings = env.settings
    val layout = env.layout
    val actions = env.actions
    val session by SessionLayout.override.collectAsStateWithLifecycle()
    val isDefault = settings.layoutOverride == LayoutOverride.AUTO && settings.spanHinge && settings.tabletopSplit && !settings.singleScreen && session == null

    SettingsPageScaffold(title = stringResource(R.string.pg_layout), onBack = onBack) {
        item(key = "options") {
            SegmentedGroup(LayoutOverride.entries.size, Modifier.selectableGroup()) { index, shape ->
                val option = LayoutOverride.entries[index]
                LayoutOptionRow(
                    option = option,
                    selected = settings.layoutOverride == option,
                    shape = shape,
                    onSelect = { actions.edit { updateLayoutOverride(option) } },
                )
            }
        }
        item(key = "auto") {
            val facts = layoutFacts(layout)
            val effective = if (layout.source != LayoutSource.AUTO) {
                "\n" + stringResource(R.string.pg_layout_effective, layoutKindLabel(layout.kind), layoutSourceLabel(layout.source))
            } else {
                ""
            }
            InfoNote(
                title = stringResource(R.string.pg_layout_current_auto, layoutKindLabel(layout.autoKind)),
                text = facts + effective,
                icon = kindIcon(layout.autoKind),
            )
        }
        item(key = "fold") {
            Column {
                Subhead(stringResource(R.string.pg_group_fold))
                SegmentedGroup(3) { index, shape ->
                    when (index) {
                        0 -> SwitchRow(
                            title = stringResource(R.string.pg_span_hinge),
                            shape = shape,
                            icon = Icons.Filled.VerticalSplit,
                            subtitle = stringResource(R.string.pg_span_hinge_desc),
                            checked = settings.spanHinge,
                            onCheckedChange = { on -> actions.edit { updateSpanHinge(on) } },
                        )
                        1 -> SwitchRow(
                            title = stringResource(R.string.pg_tabletop_split),
                            shape = shape,
                            icon = Icons.Filled.DevicesFold,
                            subtitle = stringResource(R.string.pg_tabletop_split_desc),
                            checked = settings.tabletopSplit,
                            onCheckedChange = { on -> actions.edit { updateTabletopSplit(on) } },
                        )
                        else -> SwitchRow(
                            title = stringResource(R.string.pg_single_screen),
                            shape = shape,
                            icon = Icons.Filled.Splitscreen,
                            subtitle = stringResource(R.string.pg_single_screen_desc),
                            checked = settings.singleScreen,
                            onCheckedChange = { on -> actions.edit { updateSingleScreen(on) } },
                        )
                    }
                }
            }
        }
        item(key = "session") {
            Column {
                Subhead(stringResource(R.string.pg_group_session))
                SegmentedGroup(2) { index, shape ->
                    if (index == 0) {
                        val current = session
                        NavRow(
                            title = stringResource(R.string.pg_layout_session),
                            shape = shape,
                            icon = Icons.Filled.Dashboard,
                            subtitle = if (current != null) {
                                stringResource(R.string.pg_layout_session_active, overrideLabel(current))
                            } else {
                                stringResource(R.string.pg_layout_session_idle)
                            },
                            onClick = { onShowDialog(SettingsDialog.SESSION_LAYOUT) },
                        )
                    } else {
                        NavRow(
                            title = stringResource(R.string.pg_layout_reset),
                            shape = shape,
                            icon = Icons.Filled.SettingsBackupRestore,
                            subtitle = stringResource(R.string.pg_layout_reset_desc),
                            enabled = !isDefault,
                            onClick = {
                                SessionLayout.override.value = null
                                actions.edit {
                                    updateLayoutOverride(LayoutOverride.AUTO)
                                    updateSpanHinge(true)
                                    updateTabletopSplit(true)
                                    updateSingleScreen(false)
                                }
                            },
                            trailing = null,
                        )
                    }
                }
            }
        }
        item(key = "alert") { InfoNote(stringResource(R.string.pg_layout_alert_note), icon = Icons.Filled.CrisisAlert) }
    }
}

@Composable
private fun LayoutOptionRow(option: LayoutOverride, selected: Boolean, shape: Shape, onSelect: () -> Unit) {
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .focusHighlight(shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
    ) {
        Row(
            modifier = Modifier.heightIn(min = 72.dp).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(overrideLabel(option), style = MaterialTheme.typography.titleMedium, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                Text(overrideDescription(option), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            LayoutThumbnail(option)
        }
    }
}

/** Miniature of each layout (prototype thumb()), drawn on a 60 × 34 grid. */
@Composable
internal fun LayoutThumbnail(option: LayoutOverride, modifier: Modifier = Modifier) {
    val fill = MaterialTheme.colorScheme.outlineVariant
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(modifier.size(width = 68.dp, height = 40.dp)) {
        val unit = minOf(size.width / 60f, size.height / 34f)
        val dx = (size.width - 60f * unit) / 2f
        val dy = (size.height - 34f * unit) / 2f
        fun box(x: Float, y: Float, w: Float, h: Float, r: Float, color: Color, alpha: Float = 1f) =
            roundRect(dx, dy, unit, x, y, w, h, r, color.copy(alpha = color.alpha * alpha))
        when (option) {
            LayoutOverride.AUTO -> {
                box(2f, 4f, 34f, 24f, 3f, surface)
                box(40f, 2f, 18f, 30f, 3f, surface)
                drawLine(primary, Offset(dx + 14f * unit, dy + 16f * unit), Offset(dx + 24f * unit, dy + 16f * unit), 2.4f * unit, StrokeCap.Round)
                drawLine(primary, Offset(dx + 50f * unit, dy + 12f * unit), Offset(dx + 50f * unit, dy + 22f * unit), 2.4f * unit, StrokeCap.Round)
            }
            LayoutOverride.PHONE -> {
                box(20f, 1f, 20f, 32f, 4f, surface)
                box(23f, 5f, 14f, 6f, 2f, primary)
                box(23f, 13f, 14f, 10f, 2f, fill)
                box(22f, 28f, 16f, 3f, 1.5f, fill)
            }
            LayoutOverride.TABLET -> {
                box(1f, 3f, 58f, 28f, 4f, surface)
                box(4f, 6f, 5f, 22f, 2f, fill)
                box(11f, 6f, 30f, 22f, 2f, primary, 0.7f)
                box(43f, 6f, 13f, 22f, 2f, fill)
            }
            LayoutOverride.TV -> {
                box(1f, 2f, 58f, 26f, 3f, Color(0xFF1B2129))
                box(5f, 6f, 4f, 18f, 2f, Color(0xFF4A5560))
                box(12f, 6f, 28f, 18f, 2f, primary, 0.8f)
                box(42f, 6f, 14f, 5f, 2f, Color(0xFFE4E7EB))
                box(42f, 13f, 14f, 5f, 2f, Color(0xFF4A5560))
                box(24f, 29f, 12f, 3f, 1.5f, fill)
            }
            LayoutOverride.KIOSK -> {
                box(1f, 2f, 58f, 30f, 3f, primary, 0.75f)
                box(5f, 6f, 16f, 8f, 2f, Color(0xFF0E1217))
                box(5f, 24f, 50f, 5f, 2f, Color(0xFF0E1217), 0.8f)
            }
        }
    }
}

private fun DrawScope.roundRect(dx: Float, dy: Float, unit: Float, x: Float, y: Float, w: Float, h: Float, r: Float, color: Color) {
    drawRoundRect(
        color = color,
        topLeft = Offset(dx + x * unit, dy + y * unit),
        size = Size(w * unit, h * unit),
        cornerRadius = CornerRadius(r * unit, r * unit),
    )
}

@Preview(showBackground = true)
@Composable
private fun LayoutThumbnailsPreview() {
    MyEarthQuakeAlertTheme(darkTheme = false, dynamicColor = false) {
        Row(Modifier.padding(8.dp)) {
            LayoutOverride.entries.forEach { LayoutThumbnail(it, Modifier.padding(4.dp)) }
        }
    }
}
