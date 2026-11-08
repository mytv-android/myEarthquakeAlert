package com.github.mytv.myearthquakealert.ui.settings.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.mytv.myearthquakealert.R
import kotlin.math.roundToInt

/** Widest a settings column grows; wider windows centre it (design §2 手机：内容限宽居中). */
val SettingsMaxWidth = 720.dp

/**
 * Visible D-pad / keyboard focus: a 3 dp primary outline in [shape] while the element
 * that follows in the chain is focused. Touch never focuses, so phones are unaffected.
 */
fun Modifier.focusHighlight(shape: Shape): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val color = MaterialTheme.colorScheme.primary
    this
        .onFocusChanged { focused = it.isFocused }
        .then(if (focused) Modifier.border(3.dp, color, shape) else Modifier)
}

/** Tints a list row selected in the two-pane settings layout. */
fun Modifier.selectedTint(selected: Boolean, shape: Shape): Modifier = if (!selected) this else composed {
    val color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    drawWithContent {
        drawContent()
        drawOutline(shape.createOutline(size, layoutDirection, this), color)
    }
}

/** Sub-page frame: small top app bar with back (hidden in two-pane mode) and a centred list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPageScaffold(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack, modifier = Modifier.focusHighlight(CircleShape)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.pg_back))
                        }
                    }
                },
                actions = actions,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        SettingsList(contentPadding = padding, state = listState, content = content)
    }
}

/** The scrolling column every settings page uses, capped at [SettingsMaxWidth] and centred. */
@Composable
fun SettingsList(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            state = state,
            modifier = Modifier.widthIn(max = SettingsMaxWidth).fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = contentPadding.calculateTopPadding() + 4.dp,
                bottom = contentPadding.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = content,
        )
    }
}

/** Muted explanatory note with a leading icon (prototype `.mon-warn`). */
@Composable
fun InfoNote(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Filled.Info,
    title: String? = null,
    container: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    action: (@Composable () -> Unit)? = null,
) {
    Surface(shape = RoundedCornerShape(16.dp), color = container, contentColor = contentColor, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.padding(top = 1.dp).size(18.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (title != null) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                Text(text, style = MaterialTheme.typography.bodySmall)
                if (action != null) {
                    Spacer(Modifier.size(4.dp))
                    action()
                }
            }
        }
    }
}

/** Display-only switch for rows whose whole surface toggles (keeps a single D-pad target). */
@Composable
fun SettingSwitch(checked: Boolean, enabled: Boolean = true, onCheckedChange: ((Boolean) -> Unit)? = null) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = if (onCheckedChange != null) Modifier.focusHighlight(CircleShape) else Modifier,
        thumbContent = if (checked) {
            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
        } else {
            null
        },
    )
}

@Composable
fun Chevron() {
    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Small rounded label, e.g. the "新版本" badge or a polling badge. */
@Composable
fun Pill(text: String, container: Color, content: Color, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(50), color = container, contentColor = content, modifier = modifier) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

data class ChoiceOption<T>(val value: T, val title: String, val subtitle: String? = null)

/** Radio-list dialog: picking an option applies it and closes (D-pad friendly). */
@Composable
fun <T> ChoiceDialog(
    title: String,
    options: List<ChoiceOption<T>>,
    selected: T?,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    description: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (description != null) {
                    Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.size(8.dp))
                }
                RadioOptions(options = options, selected = selected, onSelect = onSelect)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.focusHighlight(RoundedCornerShape(50))) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

/** A selectable group of radio rows. */
@Composable
fun <T> RadioOptions(
    options: List<ChoiceOption<T>>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable (T) -> Unit)? = null,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusHighlight(shape)
                    .clip(shape)
                    .selectable(selected = option.value == selected, role = Role.RadioButton, onClick = { onSelect(option.value) })
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = option.value == selected, onClick = null)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(option.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    if (!option.subtitle.isNullOrEmpty()) {
                        Text(option.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                trailing?.invoke(option.value)
            }
        }
    }
}

/** M3 Expressive connected button group used as a single-choice selector. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> ConnectedChoice(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        options.forEachIndexed { index, (value, label) ->
            ToggleButton(
                checked = value == selected,
                onCheckedChange = { onSelect(value) },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                modifier = Modifier
                    .focusHighlight(RoundedCornerShape(50))
                    .semantics { role = Role.RadioButton },
            ) {
                Text(label, maxLines = 1)
            }
        }
    }
}

/**
 * Slider with −/+ steppers. The steppers make the value reachable with a D-pad; the
 * slider commits on release so DataStore is not written on every drag frame.
 */
@Composable
fun StepperSlider(
    title: String,
    description: String,
    valueLabel: (Float) -> String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    step: Float,
    onCommit: (Float) -> Unit,
    shape: Shape,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    var local by remember(value) { mutableFloatStateOf(value) }
    val steps = ((valueRange.endInclusive - valueRange.start) / step).roundToInt() - 1
    fun snap(v: Float): Float =
        (((v - valueRange.start) / step).roundToInt() * step + valueRange.start).coerceIn(valueRange.start, valueRange.endInclusive)

    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(22.dp))
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(8.dp))
                Pill(valueLabel(local), MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                FilledTonalIconButton(
                    onClick = { local = snap(local - step); onCommit(local) },
                    enabled = local > valueRange.start,
                    modifier = Modifier.focusHighlight(CircleShape),
                ) { Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.pg_decrease)) }
                Slider(
                    value = local,
                    onValueChange = { local = snap(it) },
                    onValueChangeFinished = { onCommit(local) },
                    valueRange = valueRange,
                    steps = steps.coerceAtLeast(0),
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                FilledTonalIconButton(
                    onClick = { local = snap(local + step); onCommit(local) },
                    enabled = local < valueRange.endInclusive,
                    modifier = Modifier.focusHighlight(CircleShape),
                ) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.pg_increase)) }
            }
        }
    }
}

/** Fills the remaining width so trailing content hugs the end of a row. */
@Composable
fun RowScope.Fill() = Spacer(Modifier.weight(1f))

/** Full-width wrapper for a row of buttons below a group. */
@Composable
fun ButtonRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
