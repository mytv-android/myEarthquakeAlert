@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalMaterial3AdaptiveApi::class,
)

package com.github.mytv.myearthquakealert.ui.quakes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SyncProblem
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.HingePolicy
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.mytv.myearthquakealert.MyEarthQuakeAlertApp
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.ui.components.InfoNote
import com.github.mytv.myearthquakealert.ui.components.QuakeRow
import com.github.mytv.myearthquakealert.ui.components.SegmentedDefaults
import com.github.mytv.myearthquakealert.ui.components.dayLabel
import com.github.mytv.myearthquakealert.ui.components.rememberNow
import com.github.mytv.myearthquakealert.ui.components.toMarkers
import com.github.mytv.myearthquakealert.ui.components.tvFocusHighlight
import com.github.mytv.myearthquakealert.ui.main.LocalUserSettings
import com.github.mytv.myearthquakealert.ui.map.EarthquakeMap
import com.github.mytv.myearthquakealert.ui.map.EarthquakeMapMode
import com.github.mytv.myearthquakealert.ui.map.EewMapStyle
import com.github.mytv.myearthquakealert.ui.map.FullScreenMapDialog
import kotlinx.coroutines.launch

/** How the 地震 destination arranges list and detail. */
enum class QuakesMode {
    /** One pane at a time, back returns to the list (手机, 桌面 lower half). */
    SINGLE,
    /** List | detail side by side (600 dp and wider). */
    TWO_PANE,
    /** 书本: list | detail with the boundary on the fold. */
    HINGE,
    /** List only; the selection is shown elsewhere (双屏 companion screen). */
    LIST_ONLY,
}

/** A one-off request from elsewhere in the app: open [key]'s details and/or focus search. */
@Immutable
data class QuakesRequest(val id: Long, val key: String? = null, val search: Boolean = false)

@Composable
fun QuakesScreen(
    vm: QuakesViewModel,
    mode: QuakesMode,
    request: QuakesRequest?,
    modifier: Modifier = Modifier,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val settings = LocalUserSettings.current
    val now = rememberNow()
    var searchActive by rememberSaveable { mutableStateOf(false) }
    // A request is applied once: coming back to 地震 later must not reopen its quake.
    var handledRequest by rememberSaveable { mutableLongStateOf(0L) }
    val pending = request?.takeIf { it.id != handledRequest }

    if (mode == QuakesMode.LIST_ONLY) {
        LaunchedEffect(pending?.id) {
            val r = pending ?: return@LaunchedEffect
            handledRequest = r.id
            r.key?.let(vm::select)
            if (r.search) searchActive = true
        }
        QuakeListPane(
            state = state,
            vm = vm,
            now = now,
            style = settings.mapStyle,
            showMapCard = false,
            highlightSelection = true,
            searchActive = searchActive,
            onSearchActiveChange = { searchActive = it },
            onOpen = vm::select,
            modifier = modifier,
        )
        return
    }

    val info = currentWindowAdaptiveInfo()
    val directive = when (mode) {
        QuakesMode.TWO_PANE -> calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth(info).let {
            if (it.maxHorizontalPartitions < 2) it.copy(maxHorizontalPartitions = 2) else it
        }
        QuakesMode.HINGE -> calculatePaneScaffoldDirective(info, verticalHingePolicy = HingePolicy.AlwaysAvoid).let {
            if (it.maxHorizontalPartitions < 2) it.copy(maxHorizontalPartitions = 2) else it
        }
        else -> calculatePaneScaffoldDirective(info).copy(maxHorizontalPartitions = 1)
    }
    val navigator = rememberListDetailPaneScaffoldNavigator<String>(scaffoldDirective = directive)
    val scope = rememberCoroutineScope()
    val listVisible = navigator.scaffoldValue[ListDetailPaneScaffoldRole.List] == PaneAdaptedValue.Expanded
    val detailVisible = navigator.scaffoldValue[ListDetailPaneScaffoldRole.Detail] == PaneAdaptedValue.Expanded
    val bothVisible = listVisible && detailVisible

    val open: (String) -> Unit = { key ->
        vm.select(key)
        scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, key) }
    }

    LaunchedEffect(pending?.id) {
        val r = pending ?: return@LaunchedEffect
        handledRequest = r.id
        r.key?.let(open)
        if (r.search) searchActive = true
    }
    // Side by side, the detail pane always shows something: the newest matching quake.
    LaunchedEffect(bothVisible, state.visible) {
        if (bothVisible && state.selected == null) state.visible.firstOrNull()?.let { vm.select(it.key) }
    }

    NavigableListDetailPaneScaffold(
        navigator = navigator,
        modifier = modifier,
        listPane = {
            AnimatedPane {
                QuakeListPane(
                    state = state,
                    vm = vm,
                    now = now,
                    style = settings.mapStyle,
                    showMapCard = !bothVisible,
                    highlightSelection = bothVisible,
                    searchActive = searchActive,
                    onSearchActiveChange = { searchActive = it },
                    onOpen = open,
                )
            }
        },
        detailPane = {
            AnimatedPane {
                val key = navigator.currentDestination?.contentKey ?: state.selectedKey
                val quake = key?.let { k -> state.all.firstOrNull { it.key == k } }
                if (quake == null) {
                    QuakeDetailPlaceholder()
                } else {
                    QuakeDetailPane(
                        quake = quake,
                        all = state.all,
                        user = state.user,
                        style = settings.mapStyle,
                        showBack = !listVisible,
                        onBack = { scope.launch { navigator.navigateBack() } },
                        onSelect = open,
                    )
                }
            }
        },
    )
}

@Composable
private fun QuakeListPane(
    state: QuakesViewModel.UiState,
    vm: QuakesViewModel,
    now: Long,
    style: EewMapStyle,
    showMapCard: Boolean,
    highlightSelection: Boolean,
    searchActive: Boolean,
    onSearchActiveChange: (Boolean) -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sortMenu by remember { mutableStateOf(false) }
    BackHandler(enabled = searchActive) {
        vm.setQuery("")
        onSearchActiveChange(false)
    }
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ui_quakes_title)) },
                subtitle = {
                    Text(
                        if (state.hasData) stringResource(R.string.ui_quakes_sub, state.all.size) else stringResource(R.string.ui_quakes_sub_empty),
                        maxLines = 1,
                    )
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (searchActive) vm.setQuery("")
                            onSearchActiveChange(!searchActive)
                        },
                        modifier = Modifier.tvFocusHighlight(),
                    ) {
                        Icon(if (searchActive) Icons.Outlined.Close else Icons.Outlined.Search, contentDescription = stringResource(R.string.ui_search))
                    }
                    Box {
                        IconButton(onClick = { sortMenu = true }, modifier = Modifier.tvFocusHighlight()) {
                            Icon(Icons.Outlined.FilterList, contentDescription = stringResource(R.string.ui_sort))
                        }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            QuakeSort.entries.forEach { sort ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(sortLabel(sort))) },
                                    trailingIcon = if (state.sort == sort) {
                                        { Icon(Icons.Outlined.Check, contentDescription = null) }
                                    } else {
                                        null
                                    },
                                    enabled = sort != QuakeSort.DISTANCE || state.user != null,
                                    onClick = {
                                        sortMenu = false
                                        vm.setSort(sort)
                                    },
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (searchActive) SearchField(state.query, vm::setQuery)
            FilterButtons(state.filter, vm::setFilter, Modifier.padding(start = 14.dp, end = 14.dp, bottom = 10.dp))
            val pullState = rememberPullToRefreshState()
            val refreshing = state.loading && state.hasData
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = vm::refresh,
                state = pullState,
                modifier = Modifier.fillMaxSize(),
                indicator = {
                    PullToRefreshDefaults.LoadingIndicator(
                        state = pullState,
                        isRefreshing = refreshing,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                },
            ) {
                when {
                    !state.hasData && state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
                    !state.hasData && state.error -> MessageState(
                        icon = { Icon(Icons.Outlined.SyncProblem, contentDescription = null, modifier = Modifier.size(48.dp)) },
                        text = stringResource(R.string.history_load_failed),
                        action = { Button(onClick = vm::refresh) { Text(stringResource(R.string.retry)) } },
                    )
                    else -> QuakeList(state, vm, now, style, showMapCard, highlightSelection, onOpen)
                }
            }
        }
    }
}

@Composable
private fun QuakeList(
    state: QuakesViewModel.UiState,
    vm: QuakesViewModel,
    now: Long,
    style: EewMapStyle,
    showMapCard: Boolean,
    highlightSelection: Boolean,
    onOpen: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    val groups = remember(state.visible, state.sort, now / QuakeCatalog.DAY_MS) {
        if (state.sort == QuakeSort.TIME) QuakeCatalog.groupByDay(state.visible, now) else listOf(-1 to state.visible)
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 16.dp),
    ) {
        if (state.error) {
            item(key = "error") {
                InfoNote(
                    stringResource(R.string.ui_refresh_failed),
                    icon = Icons.Outlined.SyncProblem,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.padding(bottom = 10.dp),
                    action = { TextButton(onClick = vm::refresh) { Text(stringResource(R.string.retry)) } },
                )
            }
        }
        if (showMapCard && state.all.isNotEmpty()) {
            item(key = "map") { ListMapCard(state, style, now, onOpen) }
        }
        if (state.visible.isEmpty()) {
            item(key = "empty") {
                val text = when {
                    state.query.isNotBlank() -> stringResource(R.string.ui_quakes_empty_query, state.query.trim())
                    state.filter == QuakeFilter.NEARBY && state.user == null -> stringResource(R.string.ui_quakes_empty_location)
                    else -> stringResource(R.string.ui_quakes_empty_filter)
                }
                MessageState(
                    icon = { Icon(painterResource(R.drawable.ic_ui_earthquake), contentDescription = null, modifier = Modifier.size(48.dp)) },
                    text = text,
                    action = {
                        TextButton(onClick = {
                            vm.setFilter(QuakeFilter.ALL)
                            vm.setQuery("")
                        }) { Text(stringResource(R.string.ui_clear_filters)) }
                    },
                    modifier = Modifier.padding(top = 48.dp),
                )
            }
        }
        groups.forEach { (daysAgo, items) ->
            if (daysAgo >= 0 && items.isNotEmpty()) {
                item(key = "day:$daysAgo") {
                    Text(
                        dayLabel(daysAgo, items.first().epochMillis ?: now),
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 6.dp),
                    )
                }
            }
            itemsIndexed(items, key = { _, q -> q.key }) { i, q ->
                QuakeRow(
                    quake = q,
                    shape = SegmentedDefaults.shape(i, items.size),
                    now = now,
                    selected = highlightSelection && q.key == state.selectedKey,
                    timeOnly = daysAgo >= 0,
                    onClick = { onOpen(q.key) },
                    modifier = Modifier.padding(bottom = SegmentedDefaults.Gap).animateItem(),
                )
            }
        }
    }
}

/** The small catalogue map at the top of the phone list (design scrEvents). */
@Composable
private fun ListMapCard(state: QuakesViewModel.UiState, style: EewMapStyle, now: Long, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as? MyEarthQuakeAlertApp
    val scope = rememberCoroutineScope()
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    val markers = remember(state.all) { state.all.toMarkers() }
    EarthquakeMap(
        style = style,
        mode = EarthquakeMapMode.BROWSE,
        modifier = Modifier.fillMaxWidth().height(168.dp).padding(bottom = 10.dp).clip(RoundedCornerShape(28.dp)),
        userPoint = state.user?.point,
        quakes = markers,
        maxFitDistanceKm = 2_200.0,
        fitPaddingDp = 28,
        touchEnabled = false,
        onMapClick = { fullscreen = true },
    )
    if (fullscreen) {
        FullScreenMapDialog(
            style = style,
            onStyleChange = { s -> scope.launch { app?.settingsRepository?.updateMapStyle(s) } },
            quakes = markers,
            userPoint = state.user?.point,
            chipText = stringResource(R.string.ui_map_chip, QuakeCatalog.spanDays(state.all, now), state.all.size),
            onQuakeClick = {
                fullscreen = false
                onOpen(it.id)
            },
            onDismiss = { fullscreen = false },
        )
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 10.dp).focusRequester(focus),
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        placeholder = { Text(stringResource(R.string.ui_search_hint)) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.ui_clear)) }
            }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    )
}

private fun sortLabel(sort: QuakeSort): Int = when (sort) {
    QuakeSort.TIME -> R.string.ui_sort_time
    QuakeSort.MAGNITUDE -> R.string.ui_sort_magnitude
    QuakeSort.DISTANCE -> R.string.ui_sort_distance
}

/** 全部 / M ≥ 4 / 国内 / 500 km 内 — a connected button group (design .bgroup). */
@Composable
fun FilterButtons(selected: QuakeFilter, onSelect: (QuakeFilter) -> Unit, modifier: Modifier = Modifier) {
    val filters = QuakeFilter.entries
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        filters.forEachIndexed { i, filter ->
            ToggleButton(
                checked = selected == filter,
                onCheckedChange = { onSelect(filter) },
                shapes = when (i) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    filters.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                contentPadding = PaddingValues(horizontal = 6.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .tvFocusHighlight(RoundedCornerShape(18.dp))
                    .semantics { role = Role.RadioButton },
            ) {
                Text(
                    stringResource(
                        when (filter) {
                            QuakeFilter.ALL -> R.string.ui_filter_all
                            QuakeFilter.M4 -> R.string.ui_filter_m4
                            QuakeFilter.DOMESTIC -> R.string.ui_filter_domestic
                            QuakeFilter.NEARBY -> R.string.ui_filter_nearby
                        },
                    ),
                    maxLines = 1,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                )
            }
        }
    }
}

@Composable
private fun MessageState(
    icon: @Composable () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    action: @Composable () -> Unit = {},
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) { icon() }
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        action()
    }
}

/** Used by the 桌面 / 双屏 map panes to follow the selection of the list. */
@Composable
fun SelectedQuakeMap(vm: QuakesViewModel, modifier: Modifier = Modifier, fallback: @Composable () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val settings = LocalUserSettings.current
    val quake = state.selected
    if (quake == null) {
        fallback()
    } else {
        QuakeDetailMap(quake, state.all, state.user, settings.mapStyle, modifier, onQuakeClick = vm::select)
    }
}

/** The 双屏 companion of the list: details of the selection, or a prompt. */
@Composable
fun SelectedQuakeDetail(vm: QuakesViewModel, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsStateWithLifecycle()
    val settings = LocalUserSettings.current
    LaunchedEffect(state.visible) {
        if (state.selected == null) state.visible.firstOrNull()?.let { vm.select(it.key) }
    }
    val quake = state.selected
    if (quake == null) {
        QuakeDetailPlaceholder(modifier)
    } else {
        QuakeDetailPane(quake, state.all, state.user, settings.mapStyle, showBack = false, onBack = {}, onSelect = vm::select, modifier = modifier)
    }
}
