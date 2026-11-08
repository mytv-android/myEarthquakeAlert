package com.github.mytv.myearthquakealert.ui.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.github.mytv.myearthquakealert.ui.components.SegmentedRow

/** Segmented row that opens something (sub-page, dialog); shows a chevron by default. */
@Composable
fun NavRow(
    title: String,
    shape: Shape,
    icon: ImageVector?,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    badge: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = { Chevron() },
) {
    SegmentedRow(
        title = title,
        shape = shape,
        modifier = modifier.focusHighlight(shape).selectedTint(selected, shape),
        icon = icon,
        subtitle = subtitle,
        enabled = enabled,
        onClick = onClick,
        trailing = if (badge == null) trailing else {
            {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    badge()
                    trailing?.invoke()
                }
            }
        },
    )
}

/** Segmented row whose whole surface toggles a switch: one D-pad target per setting. */
@Composable
fun SwitchRow(
    title: String,
    shape: Shape,
    icon: ImageVector?,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    SegmentedRow(
        title = title,
        shape = shape,
        modifier = modifier.focusHighlight(shape),
        icon = icon,
        subtitle = subtitle,
        enabled = enabled,
        onClick = { onCheckedChange(!checked) },
        trailing = { SettingSwitch(checked = checked, enabled = enabled) },
    )
}

/**
 * Row that opens a sub-page and also carries its own switch (后台持续监测, 开机自动启动):
 * select opens the page, the switch is a second focus target to its right.
 */
@Composable
fun NavSwitchRow(
    title: String,
    shape: Shape,
    icon: ImageVector?,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    switchEnabled: Boolean = true,
    enabled: Boolean = true,
) {
    SegmentedRow(
        title = title,
        shape = shape,
        modifier = modifier.focusHighlight(shape).selectedTint(selected, shape),
        icon = icon,
        subtitle = subtitle,
        enabled = enabled,
        onClick = onClick,
        trailing = { SettingSwitch(checked = checked, enabled = switchEnabled, onCheckedChange = if (switchEnabled) onCheckedChange else null) },
    )
}

/** Static row with an arbitrary trailing element. */
@Composable
fun InfoRow(
    title: String,
    shape: Shape,
    icon: ImageVector?,
    subtitle: String?,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    SegmentedRow(title = title, shape = shape, modifier = modifier, icon = icon, subtitle = subtitle, trailing = trailing)
}
