package com.github.mytv.myearthquakealert.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CrisisAlert
import androidx.compose.material.icons.outlined.DevicesFold
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Tablet
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.data.model.LayoutOverride
import com.github.mytv.myearthquakealert.ui.adaptive.LayoutKind
import com.github.mytv.myearthquakealert.ui.adaptive.LayoutSource
import com.github.mytv.myearthquakealert.ui.adaptive.Posture
import com.github.mytv.myearthquakealert.ui.adaptive.ResolvedLayout
import com.github.mytv.myearthquakealert.ui.components.InfoNote
import com.github.mytv.myearthquakealert.ui.components.LayoutThumbnail
import com.github.mytv.myearthquakealert.ui.components.SegmentedDefaults
import com.github.mytv.myearthquakealert.ui.components.tvFocusHighlight

@Composable
fun layoutKindLabel(kind: LayoutKind): String = stringResource(
    when (kind) {
        LayoutKind.PHONE -> R.string.ui_layout_phone
        LayoutKind.MEDIUM -> R.string.ui_layout_medium
        LayoutKind.TABLET -> R.string.ui_layout_tablet
        LayoutKind.TV -> R.string.ui_layout_tv
        LayoutKind.KIOSK -> R.string.ui_layout_kiosk
    },
)

@Composable
private fun overrideTitle(o: LayoutOverride): String = stringResource(
    when (o) {
        LayoutOverride.AUTO -> R.string.ui_layout_auto
        LayoutOverride.PHONE -> R.string.ui_layout_phone
        LayoutOverride.TABLET -> R.string.ui_layout_tablet
        LayoutOverride.TV -> R.string.ui_layout_tv
        LayoutOverride.KIOSK -> R.string.ui_layout_kiosk
    },
)

@Composable
private fun overrideDescription(o: LayoutOverride): String = stringResource(
    when (o) {
        LayoutOverride.AUTO -> R.string.ui_layout_auto_desc
        LayoutOverride.PHONE -> R.string.ui_layout_phone_desc
        LayoutOverride.TABLET -> R.string.ui_layout_tablet_desc
        LayoutOverride.TV -> R.string.ui_layout_tv_desc
        LayoutOverride.KIOSK -> R.string.ui_layout_kiosk_desc
    },
)

/** The option currently in effect, as the chooser shows it. */
fun currentOverride(layout: ResolvedLayout, session: LayoutOverride?, stored: LayoutOverride): LayoutOverride = when {
    session != null -> session
    layout.source == LayoutSource.SETTINGS -> stored
    else -> LayoutOverride.AUTO
}

/**
 * 概览 › 菜单 › 切换布局 (and the TV 菜单 key): applies for this session only
 * ([com.github.mytv.myearthquakealert.ui.adaptive.SessionLayout]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayoutChooserSheet(
    layout: ResolvedLayout,
    selected: LayoutOverride,
    onSelect: (LayoutOverride) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded)),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, bottom = 18.dp)
                .windowInsetsPadding(WindowInsets.navigationBars),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column {
                Text(stringResource(R.string.ui_switch_layout), style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp))
                Spacer(Modifier.size(4.dp))
                Text(
                    stringResource(R.string.ui_layout_session_desc),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val options = LayoutOverride.entries
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(SegmentedDefaults.Gap)) {
                options.forEachIndexed { i, option ->
                    val shape = SegmentedDefaults.shape(i, options.size)
                    Surface(
                        shape = shape,
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        modifier = Modifier
                            .fillMaxWidth()
                            .tvFocusHighlight(shape)
                            .selectable(selected = option == selected, role = Role.RadioButton, onClick = { onSelect(option) }),
                    ) {
                        Row(
                            Modifier.heightIn(min = 62.dp).padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            RadioButton(selected = option == selected, onClick = null)
                            Column(Modifier.weight(1f)) {
                                Text(overrideTitle(option), style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold))
                                Text(overrideDescription(option), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            LayoutThumbnail(option)
                        }
                    }
                }
            }
            InfoNote(
                stringResource(R.string.ui_layout_auto_now, layoutKindLabel(layout.autoKind)) + "\n" + autoDetails(layout),
                icon = when {
                    layout.autoKind == LayoutKind.TV -> Icons.Outlined.Tv
                    layout.posture != Posture.FLAT -> Icons.Outlined.DevicesFold
                    layout.autoKind == LayoutKind.PHONE -> Icons.Outlined.Smartphone
                    else -> Icons.Outlined.Tablet
                },
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            )
            InfoNote(
                stringResource(R.string.ui_layout_alert_note),
                icon = Icons.Outlined.CrisisAlert,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            )
        }
    }
}

/** "紧凑宽度 · 390 × 870 dp · 竖屏 · 触摸" (design scrLayout). */
@Composable
fun autoDetails(layout: ResolvedLayout): String {
    val width = stringResource(
        when {
            layout.widthDp < 600 -> R.string.ui_width_compact
            layout.widthDp < 840 -> R.string.ui_width_medium
            else -> R.string.ui_width_expanded
        },
    )
    val posture = stringResource(
        when (layout.posture) {
            Posture.BOOK -> R.string.ui_posture_book
            Posture.TABLETOP -> R.string.ui_posture_tabletop
            Posture.DUAL -> R.string.ui_posture_dual
            Posture.FLAT -> if (layout.heightDp >= layout.widthDp) R.string.ui_orientation_portrait else R.string.ui_orientation_landscape
        },
    )
    val input = stringResource(
        when {
            layout.isTelevision -> R.string.ui_input_dpad
            layout.isTouch -> R.string.ui_input_touch
            else -> R.string.ui_input_keyboard
        },
    )
    return stringResource(R.string.ui_layout_details, width, layout.widthDp, layout.heightDp, posture, input)
}
