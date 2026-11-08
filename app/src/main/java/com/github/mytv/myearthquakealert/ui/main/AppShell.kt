@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3AdaptiveNavigationSuiteApi::class)

package com.github.mytv.myearthquakealert.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.ExperimentalMaterial3AdaptiveNavigationSuiteApi
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.data.model.LayoutOverride
import com.github.mytv.myearthquakealert.data.repository.UserSettings
import com.github.mytv.myearthquakealert.ui.adaptive.HingeSplitColumn
import com.github.mytv.myearthquakealert.ui.adaptive.HingeSplitRow
import com.github.mytv.myearthquakealert.ui.adaptive.LayoutKind
import com.github.mytv.myearthquakealert.ui.adaptive.LayoutResolver
import com.github.mytv.myearthquakealert.ui.adaptive.ResolvedLayout
import com.github.mytv.myearthquakealert.ui.adaptive.SessionLayout
import com.github.mytv.myearthquakealert.ui.adaptive.ShellArrangement
import com.github.mytv.myearthquakealert.ui.diagnostics.DiagnosticsScreen
import com.github.mytv.myearthquakealert.ui.kiosk.KioskScreen
import com.github.mytv.myearthquakealert.ui.navigation.Destination
import com.github.mytv.myearthquakealert.ui.navigation.DestinationIcon
import com.github.mytv.myearthquakealert.ui.navigation.label
import com.github.mytv.myearthquakealert.ui.overview.MonitorActions
import com.github.mytv.myearthquakealert.ui.overview.OverviewCompanion
import com.github.mytv.myearthquakealert.ui.overview.OverviewMapCard
import com.github.mytv.myearthquakealert.ui.overview.OverviewScreen
import com.github.mytv.myearthquakealert.ui.overview.OverviewVariant
import com.github.mytv.myearthquakealert.ui.overview.collectOverviewModel
import com.github.mytv.myearthquakealert.ui.overview.rememberMonitorActions
import com.github.mytv.myearthquakealert.ui.quakes.QuakesMode
import com.github.mytv.myearthquakealert.ui.quakes.QuakesRequest
import com.github.mytv.myearthquakealert.ui.quakes.QuakesScreen
import com.github.mytv.myearthquakealert.ui.quakes.QuakesViewModel
import com.github.mytv.myearthquakealert.ui.quakes.SelectedQuakeDetail
import com.github.mytv.myearthquakealert.ui.quakes.SelectedQuakeMap
import com.github.mytv.myearthquakealert.ui.settings.SettingsScreen
import com.github.mytv.myearthquakealert.ui.tv.TvHome
import com.github.mytv.myearthquakealert.ui.tv.TvShell

/** A start destination delivered by an intent; [id] changes for every intent. */
data class DestinationRequest(val id: Long, val destination: Destination)

/**
 * The app shell (design §2): the four destinations, hosted by the navigation component of
 * the resolved layout — short navigation bar, collapsed or expanded rail, TV drawer, or no
 * chrome at all for the kiosk — and arranged around folds and hinges.
 */
@Composable
fun AppShell(
    layout: ResolvedLayout,
    settings: UserSettings,
    vm: QuakesViewModel,
    request: DestinationRequest?,
) {
    var current by rememberSaveable { mutableStateOf(request?.destination ?: Destination.OVERVIEW) }
    var settingsPage by rememberSaveable { mutableStateOf<String?>(null) }
    var quakesRequest by remember { mutableStateOf<QuakesRequest?>(null) }
    var requestSeq by rememberSaveable { mutableLongStateOf(0L) }
    var chooser by rememberSaveable { mutableStateOf(false) }
    // The intent request already applied, so a configuration change does not re-apply it.
    var handledRequest by rememberSaveable { mutableLongStateOf(request?.id ?: 0L) }
    val session by SessionLayout.override.collectAsStateWithLifecycle()

    LaunchedEffect(request?.id) {
        val r = request ?: return@LaunchedEffect
        if (r.id == handledRequest) return@LaunchedEffect
        handledRequest = r.id
        settingsPage = null
        current = r.destination
    }

    val navigator = remember {
        ShellNavigator(
            open = { d ->
                if (d != Destination.SETTINGS) settingsPage = null
                current = d
            },
            openQuake = { key ->
                quakesRequest = QuakesRequest(++requestSeq, key = key)
                settingsPage = null
                current = Destination.QUAKES
            },
            searchQuakes = {
                quakesRequest = QuakesRequest(++requestSeq, search = true)
                settingsPage = null
                current = Destination.QUAKES
            },
            openSettingsPage = { page ->
                settingsPage = page
                current = Destination.SETTINGS
            },
            chooseLayout = { chooser = true },
        )
    }
    val actions = rememberMonitorActions(onLocationGranted = vm::refreshLocation)
    val holder = rememberSaveableStateHolder()

    // Back from any other destination returns to 概览; registered before the destinations so
    // their own handlers (detail pane, search field) take precedence.
    BackHandler(enabled = current != Destination.OVERVIEW && layout.kind != LayoutKind.KIOSK) {
        navigator.open(Destination.OVERVIEW)
    }

    CompositionLocalProvider(LocalShellNavigator provides navigator) {
        val arrangement = LayoutResolver.arrangement(layout, settings.spanHinge, settings.tabletopSplit, settings.singleScreen)
        val destination: @Composable (Destination, DestinationSlots) -> Unit = { d, slots ->
            holder.SaveableStateProvider(d.route) {
                when (d) {
                    Destination.OVERVIEW -> slots.overview()
                    Destination.QUAKES -> slots.quakes()
                    Destination.DIAGNOSTICS -> DiagnosticsScreen(Modifier.fillMaxSize(), compact = false)
                    Destination.SETTINGS -> SettingsScreen(
                        modifier = Modifier.fillMaxSize(),
                        openPage = settingsPage,
                        onOpenDiagnostics = { navigator.open(Destination.DIAGNOSTICS) },
                    )
                }
            }
        }

        when {
            layout.kind == LayoutKind.KIOSK -> KioskScreen(
                vm = vm,
                onExit = {
                    // A persistent 看板 setting is overridden with 自动 for the rest of the session.
                    SessionLayout.override.value = if (settings.layoutOverride == LayoutOverride.KIOSK) LayoutOverride.AUTO else null
                },
            )
            layout.kind == LayoutKind.TV -> TvShell(
                vm = vm,
                actions = actions,
                current = current,
                onNavigate = navigator.open,
                onChooseLayout = navigator.chooseLayout,
            ) { d ->
                destination(d, DestinationSlots(
                    overview = { TvHome(vm, actions) },
                    quakes = { QuakesScreen(vm, QuakesMode.TWO_PANE, quakesRequest, Modifier.fillMaxSize()) },
                ))
            }
            arrangement == ShellArrangement.BOOK_SPLIT -> NavigationShell(
                type = NavigationSuiteType.WideNavigationRailCollapsed,
                current = current,
                onNavigate = navigator.open,
                actions = actions,
            ) {
                destination(current, DestinationSlots(
                    overview = { OverviewScreen(vm, actions, OverviewVariant.BOOK, Modifier.fillMaxSize(), hinge = layout.hingeBounds) },
                    quakes = { QuakesScreen(vm, QuakesMode.HINGE, quakesRequest, Modifier.fillMaxSize()) },
                ))
            }
            arrangement == ShellArrangement.TABLETOP_SPLIT -> HingeSplitColumn(
                hinge = layout.hingeBounds,
                modifier = Modifier.fillMaxSize(),
                top = { TabletopMap(vm, current) },
                bottom = {
                    // The lower half starts below the hinge: no status bar to pad for.
                    NavigationShell(
                        type = NavigationSuiteType.ShortNavigationBarMedium,
                        current = current,
                        onNavigate = navigator.open,
                        actions = actions,
                        modifier = Modifier.consumeWindowInsets(WindowInsets.statusBars),
                    ) {
                        destination(current, DestinationSlots(
                            overview = { OverviewScreen(vm, actions, OverviewVariant.TABLETOP, Modifier.fillMaxSize()) },
                            quakes = { QuakesScreen(vm, QuakesMode.SINGLE, quakesRequest, Modifier.fillMaxSize()) },
                        ))
                    }
                },
            )
            arrangement == ShellArrangement.DUAL_SPAN || arrangement == ShellArrangement.DUAL_SINGLE -> HingeSplitRow(
                hinge = layout.hingeBounds,
                modifier = Modifier.fillMaxSize(),
                minGap = 0.dp,
                start = {
                    NavigationShell(
                        type = NavigationSuiteType.ShortNavigationBarCompact,
                        current = current,
                        onNavigate = navigator.open,
                        actions = actions,
                    ) {
                        destination(current, DestinationSlots(
                            overview = { OverviewScreen(vm, actions, OverviewVariant.PHONE, Modifier.fillMaxSize()) },
                            quakes = {
                                QuakesScreen(
                                    vm,
                                    if (arrangement == ShellArrangement.DUAL_SPAN) QuakesMode.LIST_ONLY else QuakesMode.SINGLE,
                                    quakesRequest,
                                    Modifier.fillMaxSize(),
                                )
                            },
                        ))
                    }
                },
                end = {
                    if (arrangement == ShellArrangement.DUAL_SPAN) {
                        DualCompanion(vm, current)
                    } else {
                        // 双屏时只用一块屏: the second screen stays empty.
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLowest))
                    }
                },
            )
            else -> {
                val wide = layout.widthDp >= LayoutResolver.EXPANDED_MIN_WIDTH_DP
                val kind = if (layout.kind == LayoutKind.TABLET && !wide) LayoutKind.MEDIUM else layout.kind
                NavigationShell(
                    type = when (kind) {
                        LayoutKind.PHONE -> if (layout.widthDp >= LayoutResolver.MEDIUM_MIN_WIDTH_DP) {
                            NavigationSuiteType.ShortNavigationBarMedium
                        } else {
                            NavigationSuiteType.ShortNavigationBarCompact
                        }
                        LayoutKind.TABLET -> NavigationSuiteType.WideNavigationRailExpanded
                        else -> NavigationSuiteType.WideNavigationRailCollapsed
                    },
                    current = current,
                    onNavigate = navigator.open,
                    actions = actions,
                ) {
                    val content: @Composable () -> Unit = {
                        destination(current, DestinationSlots(
                            overview = {
                                OverviewScreen(
                                    vm,
                                    actions,
                                    when (kind) {
                                        LayoutKind.PHONE -> OverviewVariant.PHONE
                                        LayoutKind.TABLET -> OverviewVariant.TABLET
                                        else -> OverviewVariant.MEDIUM
                                    },
                                    Modifier.fillMaxSize(),
                                )
                            },
                            quakes = {
                                QuakesScreen(vm, if (kind == LayoutKind.PHONE) QuakesMode.SINGLE else QuakesMode.TWO_PANE, quakesRequest, Modifier.fillMaxSize())
                            },
                        ))
                    }
                    if (kind == LayoutKind.PHONE && layout.widthDp > PHONE_MAX_CONTENT_DP) {
                        // 手机 forced on a wide window: content limited to 600 dp, centred.
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow), contentAlignment = Alignment.TopCenter) {
                            Box(Modifier.fillMaxHeight().widthIn(max = PHONE_MAX_CONTENT_DP.dp).background(MaterialTheme.colorScheme.surface)) { content() }
                        }
                    } else {
                        content()
                    }
                }
            }
        }

        if (chooser) {
            LayoutChooserSheet(
                layout = layout,
                selected = currentOverride(layout, session, settings.layoutOverride),
                onSelect = {
                    SessionLayout.override.value = it
                    chooser = false
                },
                onDismiss = { chooser = false },
            )
        }
    }
}

private const val PHONE_MAX_CONTENT_DP = 600

/** Destination bodies that differ between arrangements. */
class DestinationSlots(
    val overview: @Composable () -> Unit,
    val quakes: @Composable () -> Unit,
)

private fun NavigationSuiteType.isRail(): Boolean =
    this == NavigationSuiteType.WideNavigationRailCollapsed || this == NavigationSuiteType.WideNavigationRailExpanded

/**
 * NavigationSuiteScaffold with the four destinations. Rails also carry the 模拟演练 FAB and a
 * 切换布局 item (design scrTablet / scrBook).
 */
@Composable
private fun NavigationShell(
    type: NavigationSuiteType,
    current: Destination,
    onNavigate: (Destination) -> Unit,
    actions: MonitorActions,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val nav = LocalShellNavigator.current
    val rail = type.isRail()
    NavigationSuiteScaffold(
        modifier = modifier,
        navigationSuiteType = type,
        containerColor = MaterialTheme.colorScheme.surface,
        navigationItems = {
            Destination.entries.forEach { d ->
                NavigationSuiteItem(
                    selected = d == current,
                    onClick = { onNavigate(d) },
                    icon = { DestinationIcon(d, selected = d == current) },
                    label = { Text(d.label()) },
                    navigationSuiteType = type,
                )
            }
            if (rail) {
                NavigationSuiteItem(
                    selected = false,
                    onClick = nav.chooseLayout,
                    icon = { Icon(Icons.Outlined.Dashboard, contentDescription = null) },
                    label = { Text(stringResource(R.string.ui_switch_layout)) },
                    navigationSuiteType = type,
                )
            }
        },
        primaryActionContent = {
            if (rail) {
                val drill = stringResource(R.string.ui_drill)
                if (type == NavigationSuiteType.WideNavigationRailExpanded) {
                    ExtendedFloatingActionButton(
                        onClick = { actions.drill() },
                        icon = { Icon(Icons.Outlined.Science, contentDescription = null) },
                        text = { Text(drill) },
                    )
                } else {
                    FloatingActionButton(onClick = { actions.drill() }) {
                        Icon(Icons.Outlined.Science, contentDescription = drill)
                    }
                }
            }
        },
    ) {
        val consumed = if (rail) {
            WindowInsets.navigationBars.only(WindowInsetsSides.Start).union(WindowInsets.displayCutout.only(WindowInsetsSides.Start))
        } else {
            WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
        }
        Box(Modifier.fillMaxSize().consumeWindowInsets(consumed)) { content() }
    }
}

/** 桌面 upper half: the catalogue map, or the selected quake while in 地震. */
@Composable
private fun TabletopMap(vm: QuakesViewModel, current: Destination) {
    val model = collectOverviewModel(vm)
    val nav = LocalShellNavigator.current
    val modifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
        .padding(12.dp)
    val catalogue: @Composable () -> Unit = {
        OverviewMapCard(
            quakes = model.quakes,
            style = model.settings.mapStyle,
            now = model.now,
            onOpenQuake = nav.openQuake,
            interactive = true,
            modifier = modifier,
        )
    }
    if (current == Destination.QUAKES) {
        SelectedQuakeMap(vm, modifier.clip(RoundedCornerShape(28.dp)), fallback = catalogue)
    } else {
        catalogue()
    }
}

/** 双屏 end screen: quake details next to the list, the overview companion otherwise. */
@Composable
private fun DualCompanion(vm: QuakesViewModel, current: Destination) {
    if (current == Destination.QUAKES) {
        SelectedQuakeDetail(vm, Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.End + WindowInsetsSides.Bottom)))
    } else {
        OverviewCompanion(vm, Modifier.fillMaxSize())
    }
}
