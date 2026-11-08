@file:OptIn(ExperimentalMaterial3Api::class)

package com.github.mytv.myearthquakealert.ui.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.data.repository.UserSettings
import com.github.mytv.myearthquakealert.diagnostics.Diagnostics
import com.github.mytv.myearthquakealert.diagnostics.HealthMetrics
import com.github.mytv.myearthquakealert.service.MonitorPhase
import com.github.mytv.myearthquakealert.service.MonitorState
import com.github.mytv.myearthquakealert.service.MonitorStatus
import com.github.mytv.myearthquakealert.ui.adaptive.HingeSplitRow
import com.github.mytv.myearthquakealert.ui.components.SectionHeader
import com.github.mytv.myearthquakealert.ui.components.effectivePhase
import com.github.mytv.myearthquakealert.ui.components.rememberNow
import com.github.mytv.myearthquakealert.ui.diagnostics.DiagnosticsScreen
import com.github.mytv.myearthquakealert.ui.main.LocalShellNavigator
import com.github.mytv.myearthquakealert.ui.main.LocalUserSettings
import com.github.mytv.myearthquakealert.ui.navigation.Destination
import com.github.mytv.myearthquakealert.ui.quakes.QuakesViewModel

/** Everything the overview renders, collected once per screen. */
@Immutable
data class OverviewModel(
    val settings: UserSettings,
    val monitor: MonitorState,
    val metrics: HealthMetrics,
    val quakes: QuakesViewModel.UiState,
    val now: Long,
    val phase: MonitorPhase,
)

@Composable
fun collectOverviewModel(vm: QuakesViewModel): OverviewModel {
    val settings = LocalUserSettings.current
    val monitor by MonitorStatus.state.collectAsStateWithLifecycle()
    val metrics by Diagnostics.metrics.collectAsStateWithLifecycle()
    val quakes by vm.state.collectAsStateWithLifecycle()
    val now = rememberNow()
    return OverviewModel(settings, monitor, metrics, quakes, now, effectivePhase(monitor, settings, now))
}

private enum class MonitorSheet { NONE, PREFLIGHT, FIX, PAUSE }

/**
 * The status card wired to the service commands, with the preflight (开启前检查) and the
 * pause / off sheets.
 */
@Composable
fun MonitorPanel(
    model: OverviewModel,
    actions: MonitorActions,
    modifier: Modifier = Modifier,
    shapeSize: Dp = 56.dp,
) {
    val nav = LocalShellNavigator.current
    var sheet by rememberSaveable { mutableStateOf(MonitorSheet.NONE) }
    val missing = model.monitor.missing
    MonitorStatusCard(
        phase = model.phase,
        state = model.monitor,
        settings = model.settings,
        metrics = model.metrics,
        onSwitch = { on ->
            if (on) {
                if (preflightRequirements(missing).any { it in missing }) sheet = MonitorSheet.PREFLIGHT else actions.turnOn()
            } else {
                sheet = MonitorSheet.PAUSE
            }
        },
        onPause = { sheet = MonitorSheet.PAUSE },
        onResume = actions::resume,
        onDiagnostics = { nav.open(Destination.DIAGNOSTICS) },
        onFix = { sheet = MonitorSheet.FIX },
        modifier = modifier,
        shapeSize = shapeSize,
    )
    when (sheet) {
        MonitorSheet.PREFLIGHT, MonitorSheet.FIX -> PreflightSheet(
            missing = missing,
            bootStart = model.settings.bootStart,
            onFix = actions::fix,
            onBootStartChange = actions::setBootStart,
            onConfirm = {
                if (sheet == MonitorSheet.PREFLIGHT) actions.turnOn()
                sheet = MonitorSheet.NONE
            },
            onDismiss = { sheet = MonitorSheet.NONE },
            alreadyOn = sheet == MonitorSheet.FIX,
        )
        MonitorSheet.PAUSE -> PauseSheet(
            now = model.now,
            onPause = {
                actions.pause(it)
                sheet = MonitorSheet.NONE
            },
            onTurnOff = {
                actions.turnOff()
                sheet = MonitorSheet.NONE
            },
            onDismiss = { sheet = MonitorSheet.NONE },
        )
        MonitorSheet.NONE -> Unit
    }
}

/** How the overview arranges itself for the current layout. */
enum class OverviewVariant {
    /** Single column (手机, 双屏 start screen). */
    PHONE,
    /** Rail + two panes: status and list | map (600–840 dp, landscape phone). */
    MEDIUM,
    /** Map main area + 360 dp side pane (≥ 840 dp). */
    TABLET,
    /** 书本: status and list | map, split at the fold. */
    BOOK,
    /** 桌面 lower half: status and the newest quakes (the map is on the upper half). */
    TABLETOP,
}

@Composable
fun OverviewScreen(
    vm: QuakesViewModel,
    actions: MonitorActions,
    variant: OverviewVariant,
    modifier: Modifier = Modifier,
    hinge: Rect? = null,
) {
    val model = collectOverviewModel(vm)
    when (variant) {
        OverviewVariant.PHONE -> PhoneOverview(model, vm, actions, modifier)
        OverviewVariant.MEDIUM -> TwoPaneOverview(model, vm, actions, modifier, hinge = null)
        OverviewVariant.BOOK -> TwoPaneOverview(model, vm, actions, modifier, hinge = hinge)
        OverviewVariant.TABLET -> TabletOverview(model, vm, actions, modifier)
        OverviewVariant.TABLETOP -> TabletopOverview(model, vm, actions, modifier)
    }
}

@Composable
private fun PhoneOverview(model: OverviewModel, vm: QuakesViewModel, actions: MonitorActions, modifier: Modifier) {
    val nav = LocalShellNavigator.current
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            OverviewTopBar(
                location = locationText(model.quakes.user, model.quakes.locationResolved, model.metrics),
                scrollBehavior = scroll,
                onSearch = nav.searchQuakes,
                onChooseLayout = nav.chooseLayout,
                onAbout = { nav.openSettingsPage("update") },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MonitorPanel(model, actions)
            OverviewMapCard(
                quakes = model.quakes,
                style = model.settings.mapStyle,
                now = model.now,
                onOpenQuake = nav.openQuake,
                modifier = Modifier.fillMaxWidth().height(250.dp),
            )
            RecentQuakesSection(model.quakes, model.now, count = 5, onOpenQuake = nav.openQuake, onShowAll = { nav.open(Destination.QUAKES) }, onRetry = vm::refresh)
            if (model.phase != MonitorPhase.OFF) LatestReportCard(model.monitor.latestReport)
        }
    }
}

/** Status and list on the start pane, the map on the end pane; split at [hinge] when given. */
@Composable
private fun TwoPaneOverview(model: OverviewModel, vm: QuakesViewModel, actions: MonitorActions, modifier: Modifier, hinge: Rect?) {
    val nav = LocalShellNavigator.current
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val start: @Composable () -> Unit = {
        Scaffold(
            modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
            containerColor = MaterialTheme.colorScheme.surface,
            contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom),
            topBar = {
                OverviewTopBar(
                    location = locationText(model.quakes.user, model.quakes.locationResolved, model.metrics),
                    scrollBehavior = scroll,
                    onSearch = nav.searchQuakes,
                    onChooseLayout = nav.chooseLayout,
                    onAbout = { nav.openSettingsPage("update") },
                )
            },
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(padding)
                    .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MonitorPanel(model, actions)
                RecentQuakesSection(model.quakes, model.now, count = 6, onOpenQuake = nav.openQuake, onShowAll = { nav.open(Destination.QUAKES) }, onRetry = vm::refresh)
                if (model.phase != MonitorPhase.OFF) LatestReportCard(model.monitor.latestReport)
            }
        }
    }
    val end: @Composable () -> Unit = {
        OverviewMapCard(
            quakes = model.quakes,
            style = model.settings.mapStyle,
            now = model.now,
            onOpenQuake = nav.openQuake,
            interactive = true,
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Bottom + WindowInsetsSides.End))
                .padding(top = 8.dp, end = 12.dp, bottom = 12.dp, start = if (hinge == null) 4.dp else 0.dp),
        )
    }
    if (hinge != null) {
        HingeSplitRow(hinge = hinge, modifier = modifier.fillMaxSize(), start = start, end = end)
    } else {
        Row(modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) { start() }
            Box(Modifier.weight(1f)) { end() }
        }
    }
}

/** 平板: map main area + 360 dp side pane with status, recent quakes and 服务状态. */
@Composable
private fun TabletOverview(model: OverviewModel, vm: QuakesViewModel, actions: MonitorActions, modifier: Modifier) {
    val nav = LocalShellNavigator.current
    Row(
        modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.End + WindowInsetsSides.Bottom)),
    ) {
        OverviewMapCard(
            quakes = model.quakes,
            style = model.settings.mapStyle,
            now = model.now,
            onOpenQuake = nav.openQuake,
            interactive = true,
            toolbarAlignment = androidx.compose.ui.Alignment.BottomCenter,
            chipWithSource = true,
            modifier = Modifier.weight(1f).fillMaxHeight().padding(top = 8.dp, bottom = 16.dp),
        )
        SidePane(Modifier.width(SIDE_PANE_WIDTH)) {
            PaneHeader(
                title = stringResource(R.string.app_name),
                subtitle = locationText(model.quakes.user, model.quakes.locationResolved, model.metrics),
                actions = { OverviewMenu(nav.chooseLayout) { nav.openSettingsPage("update") } },
            )
            MonitorPanel(model, actions)
            RecentQuakesSection(model.quakes, model.now, count = 6, onOpenQuake = nav.openQuake, onShowAll = { nav.open(Destination.QUAKES) }, onRetry = vm::refresh)
            ServiceStatusSection()
        }
    }
}

@Composable
private fun TabletopOverview(model: OverviewModel, vm: QuakesViewModel, actions: MonitorActions, modifier: Modifier) {
    val nav = LocalShellNavigator.current
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PaddingValues(horizontal = 16.dp, vertical = 12.dp)),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MonitorPanel(model, actions, shapeSize = 48.dp)
        RecentQuakesSection(model.quakes, model.now, count = 3, onOpenQuake = nav.openQuake, onShowAll = { nav.open(Destination.QUAKES) }, onRetry = vm::refresh)
    }
}

/** Width of the tablet side pane (design §6 平板). */
val SIDE_PANE_WIDTH = 360.dp

/** Scrolling column for side panes and companion screens. */
@Composable
fun SidePane(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

/** 服务状态 · 诊断 with the compact diagnostics summary. */
@Composable
fun ServiceStatusSection(modifier: Modifier = Modifier) {
    val nav = LocalShellNavigator.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(
            stringResource(R.string.ui_service_status),
            link = stringResource(R.string.ui_diagnostics),
            onLink = { nav.open(Destination.DIAGNOSTICS) },
        )
        DiagnosticsScreen(Modifier.fillMaxWidth(), compact = true)
    }
}

/** 双屏 end screen next to the overview: recent quakes and the diagnostics summary. */
@Composable
fun OverviewCompanion(vm: QuakesViewModel, modifier: Modifier = Modifier) {
    val nav = LocalShellNavigator.current
    val model = collectOverviewModel(vm)
    SidePane(modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.End + WindowInsetsSides.Bottom))) {
        PaneHeader(title = stringResource(R.string.earthquake_history), subtitle = null)
        RecentQuakesSection(
            model.quakes, model.now, count = 6, onOpenQuake = nav.openQuake, onShowAll = { nav.open(Destination.QUAKES) }, onRetry = vm::refresh,
            title = stringResource(R.string.ui_catalogue),
        )
        ServiceStatusSection()
    }
}
