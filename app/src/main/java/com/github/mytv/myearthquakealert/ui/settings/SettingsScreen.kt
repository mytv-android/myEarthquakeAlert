package com.github.mytv.myearthquakealert.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.HingePolicy
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldDefaults
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldDestinationItem
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.calculateThreePaneScaffoldValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.mytv.myearthquakealert.MyEarthQuakeAlertApp
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.data.model.LayoutOverride
import com.github.mytv.myearthquakealert.data.model.ThemeMode
import com.github.mytv.myearthquakealert.data.repository.UserSettings
import com.github.mytv.myearthquakealert.diagnostics.Diagnostics
import com.github.mytv.myearthquakealert.diagnostics.HealthMetrics
import com.github.mytv.myearthquakealert.diagnostics.LogEntry
import com.github.mytv.myearthquakealert.service.DeliveryChecks
import com.github.mytv.myearthquakealert.service.DeliveryRequirement
import com.github.mytv.myearthquakealert.service.MonitorState
import com.github.mytv.myearthquakealert.service.MonitorStatus
import com.github.mytv.myearthquakealert.ui.about.AboutViewModel
import com.github.mytv.myearthquakealert.ui.adaptive.LayoutKind
import com.github.mytv.myearthquakealert.ui.adaptive.LocalResolvedLayout
import com.github.mytv.myearthquakealert.ui.adaptive.Posture
import com.github.mytv.myearthquakealert.ui.adaptive.ResolvedLayout
import com.github.mytv.myearthquakealert.ui.adaptive.SessionLayout
import com.github.mytv.myearthquakealert.ui.map.EewMapStyle
import com.github.mytv.myearthquakealert.ui.settings.components.ChoiceDialog
import com.github.mytv.myearthquakealert.ui.settings.components.ChoiceOption
import com.github.mytv.myearthquakealert.ui.settings.components.Formats
import com.github.mytv.myearthquakealert.ui.settings.components.pauseEnd
import com.github.mytv.myearthquakealert.ui.settings.components.rememberFormats
import com.github.mytv.myearthquakealert.ui.settings.components.rememberNow

/**
 * 设置 destination with its own sub-pages (后台监测, 开机与锁屏, 数据源, 预警阈值, 位置,
 * 界面布局, 关于与更新, 关于). Phones show the list and push sub-pages; MEDIUM, TABLET and
 * TV layouts show the list and the open sub-page side by side.
 *
 * @param openPage optional sub-page to open directly: "monitor", "boot", "layout", "update"
 *   (also "sources", "alerts", "location", "about").
 * @param onOpenDiagnostics switches to the 诊断 destination (工具 › 诊断 row).
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    openPage: String? = null,
    onOpenDiagnostics: () -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.applicationContext as MyEarthQuakeAlertApp
    val loaded by app.settingsRepository.settings.collectAsStateWithLifecycle<UserSettings?>(initialValue = null)
    val settings = loaded
    var handledOpenPage by rememberSaveable { mutableStateOf<String?>(null) }
    // A cleared request re-arms the page, so the same page can be opened again later.
    SideEffect { if (openPage == null) handledOpenPage = null }
    if (settings == null) {
        Box(modifier.fillMaxSize())
        return
    }
    val monitor by MonitorStatus.state.collectAsStateWithLifecycle()
    val metrics by Diagnostics.metrics.collectAsStateWithLifecycle()
    val log by Diagnostics.log.collectAsStateWithLifecycle()
    val layout = LocalResolvedLayout.current
    val formats = rememberFormats()
    val now = rememberNow()
    val scope = rememberCoroutineScope()

    var missing by remember { mutableStateOf(DeliveryChecks.missing(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        missing = DeliveryChecks.missing(context)
        DeliveryChecks.refresh(context)
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        missing = DeliveryChecks.missing(context)
        DeliveryChecks.refresh(context)
    }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        missing = DeliveryChecks.missing(context)
        DeliveryChecks.refresh(context)
    }
    val actions = remember(context, scope) {
        SettingsActions(
            context = context,
            scope = scope,
            requestNotificationPermission = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    DeliveryChecks.fix(context, DeliveryRequirement.NOTIFICATIONS)
                }
            },
            requestLocationPermission = {
                locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            },
        )
    }
    val updateViewModel: AboutViewModel = viewModel(key = "settings-update", factory = AboutViewModel.factory(context))
    LaunchedEffect(settings.updateAutoCheck) { updateViewModel.autoCheckIfDue(settings.updateAutoCheck) }

    val env = SettingsEnv(settings, monitor, metrics, log, missing, layout, now, formats, actions, updateViewModel)

    // Navigation: a small stack of sub-pages, saved across configuration changes.
    var stackKeys by rememberSaveable { mutableStateOf("") }
    val stack = remember(stackKeys) { stackKeys.split(',').mapNotNull { SettingsPage.fromKey(it) } }
    fun setStack(pages: List<SettingsPage>) {
        stackKeys = pages.joinToString(",") { it.key }
    }
    LaunchedEffect(openPage) {
        if (openPage != null && openPage != handledOpenPage) {
            handledOpenPage = openPage
            SettingsPage.fromKey(openPage)?.let { setStack(listOf(it)) }
        }
    }

    val twoPane = layout.kind == LayoutKind.MEDIUM || layout.kind == LayoutKind.TABLET || layout.kind == LayoutKind.TV
    val current = stack.lastOrNull()
    val shown = current ?: if (twoPane) SettingsPage.MONITOR else null
    val goBack: () -> Unit = { setStack(stack.dropLast(1)) }
    BackHandler(enabled = stack.isNotEmpty() && !(twoPane && stack.size == 1 && stack[0] == SettingsPage.MONITOR), onBack = goBack)

    val directive = rememberSettingsDirective(layout, twoPane, settings.spanHinge)
    val value = calculateThreePaneScaffoldValue(
        directive.maxHorizontalPartitions,
        ListDetailPaneScaffoldDefaults.adaptStrategies(),
        ThreePaneScaffoldDestinationItem<Unit>(if (current == null) ListDetailPaneScaffoldRole.List else ListDetailPaneScaffoldRole.Detail),
    )

    var dialog by rememberSaveable { mutableStateOf<SettingsDialog?>(null) }

    ListDetailPaneScaffold(
        directive = directive,
        value = value,
        modifier = modifier.fillMaxSize(),
        listPane = {
            AnimatedPane {
                SettingsRoot(
                    env = env,
                    selected = if (twoPane) shown else null,
                    onOpen = { setStack(listOf(it)) },
                    onOpenDiagnostics = onOpenDiagnostics,
                    onShowDialog = { dialog = it },
                )
            }
        },
        detailPane = {
            AnimatedPane {
                val page = shown
                if (page != null) {
                    val back: (() -> Unit)? = if (twoPane && stack.size <= 1) null else goBack
                    val push: (SettingsPage) -> Unit = { next -> setStack((if (stack.isEmpty()) listOf(page) else stack) + next) }
                    key(page) {
                        SettingsPageContent(
                            page = page,
                            env = env,
                            onBack = back,
                            onOpenPage = push,
                            onOpenDiagnostics = onOpenDiagnostics,
                            onShowDialog = { dialog = it },
                        )
                    }
                }
            }
        },
    )

    SettingsDialogHost(dialog = dialog, env = env, onDismiss = { dialog = null })
}

/** Everything a settings page renders, collected once by [SettingsScreen]. */
@Immutable
internal class SettingsEnv(
    val settings: UserSettings,
    val monitor: MonitorState,
    val metrics: HealthMetrics,
    val log: List<LogEntry>,
    val missing: Set<DeliveryRequirement>,
    val layout: ResolvedLayout,
    val now: Long,
    val formats: Formats,
    val actions: SettingsActions,
    val updateViewModel: AboutViewModel,
) {
    val pauseUntil: Long get() = pauseEnd(monitor, settings)
}

internal enum class SettingsDialog { START_CHECK, PAUSE_OR_OFF, PAUSE, THEME, MAP_STYLE, DRILL, SESSION_LAYOUT }

@Composable
private fun SettingsPageContent(
    page: SettingsPage,
    env: SettingsEnv,
    onBack: (() -> Unit)?,
    onOpenPage: (SettingsPage) -> Unit,
    onOpenDiagnostics: () -> Unit,
    onShowDialog: (SettingsDialog) -> Unit,
) {
    when (page) {
        SettingsPage.MONITOR -> MonitorPage(env, onBack, onOpenPage, onOpenDiagnostics, onShowDialog)
        SettingsPage.BOOT -> BootPage(env, onBack)
        SettingsPage.SOURCES -> SourcesPage(env, onBack)
        SettingsPage.ALERTS -> AlertsPage(env, onBack)
        SettingsPage.LOCATION -> LocationPage(env, onBack)
        SettingsPage.LAYOUT -> LayoutPage(env, onBack, onShowDialog)
        SettingsPage.UPDATE -> UpdatePage(env, onBack, onOpenAbout = { onOpenPage(SettingsPage.ABOUT) })
        SettingsPage.ABOUT -> AboutPage(env, onBack)
    }
}

@Composable
private fun SettingsDialogHost(dialog: SettingsDialog?, env: SettingsEnv, onDismiss: () -> Unit) {
    val settings = env.settings
    val actions = env.actions
    when (dialog) {
        null -> Unit
        SettingsDialog.PAUSE_OR_OFF, SettingsDialog.PAUSE -> PauseDialog(
            formats = env.formats,
            now = env.now,
            pausedUntil = env.pauseUntil,
            includeOff = dialog == SettingsDialog.PAUSE_OR_OFF,
            onPause = actions::pause,
            onResume = actions::resume,
            onTurnOff = actions::stopMonitoring,
            onDismiss = onDismiss,
        )
        SettingsDialog.THEME -> ChoiceDialog(
            title = stringResource(R.string.pg_theme),
            options = ThemeMode.entries.map { ChoiceOption(it, themeLabel(it)) },
            selected = settings.themeMode,
            onSelect = { mode -> actions.edit { updateThemeMode(mode) }; onDismiss() },
            onDismiss = onDismiss,
        )
        SettingsDialog.MAP_STYLE -> ChoiceDialog(
            title = stringResource(R.string.map_tile_style),
            options = EewMapStyle.entries.map { ChoiceOption(it, mapStyleLabel(it), mapStyleDescription(it)) },
            selected = settings.mapStyle,
            onSelect = { style -> actions.edit { updateMapStyle(style) }; onDismiss() },
            onDismiss = onDismiss,
        )
        SettingsDialog.DRILL -> DrillDialog(onRun = actions::drill, onDismiss = onDismiss)
        SettingsDialog.START_CHECK -> StartCheckDialog(
            env = env,
            onConfirm = { actions.startMonitoring(); onDismiss() },
            onDismiss = onDismiss,
        )
        SettingsDialog.SESSION_LAYOUT -> {
            val session by SessionLayout.override.collectAsStateWithLifecycle()
            ChoiceDialog(
                title = stringResource(R.string.pg_layout_session_title),
                description = stringResource(R.string.pg_layout_session_desc),
                options = listOf(ChoiceOption<LayoutOverride?>(null, stringResource(R.string.pg_layout_session_none), stringResource(R.string.pg_layout_session_none_desc))) +
                    LayoutOverride.entries.map { ChoiceOption<LayoutOverride?>(it, overrideLabel(it), overrideDescription(it)) },
                selected = session,
                onSelect = { SessionLayout.override.value = it; onDismiss() },
                onDismiss = onDismiss,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun rememberSettingsDirective(layout: ResolvedLayout, twoPane: Boolean, spanHinge: Boolean) =
    run {
        val info = currentWindowAdaptiveInfo()
        val density = LocalDensity.current
        val base = calculatePaneScaffoldDirective(info, if (spanHinge) HingePolicy.AlwaysAvoid else HingePolicy.AvoidSeparating)
        val hinge = layout.hingeBounds
        val listWidth = when {
            spanHinge && hinge != null && layout.hingeIsVertical && (layout.posture == Posture.BOOK || layout.posture == Posture.DUAL) ->
                with(density) { hinge.left.toDp() }
            layout.kind == LayoutKind.TV -> 420.dp
            layout.kind == LayoutKind.TABLET -> 400.dp
            else -> 360.dp
        }
        base.copy(
            maxHorizontalPartitions = if (twoPane) 2 else 1,
            horizontalPartitionSpacerSize = if (twoPane) 16.dp else 0.dp,
            defaultPanePreferredWidth = listWidth,
        )
    }

@Composable
internal fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> stringResource(R.string.pg_theme_system)
    ThemeMode.LIGHT -> stringResource(R.string.pg_theme_light)
    ThemeMode.DARK -> stringResource(R.string.pg_theme_dark)
}

@Composable
internal fun mapStyleLabel(style: EewMapStyle): String = when (style.name) {
    "AMAP" -> stringResource(R.string.pg_map_amap)
    "AMAP_SAT" -> stringResource(R.string.pg_map_amap_sat)
    "OSM" -> stringResource(R.string.map_style_osm)
    else -> style.name.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase() }
}

@Composable
internal fun mapStyleDescription(style: EewMapStyle): String? = when (style.name) {
    "AMAP" -> stringResource(R.string.pg_map_amap_desc)
    "AMAP_SAT" -> stringResource(R.string.pg_map_amap_sat_desc)
    "OSM" -> stringResource(R.string.pg_map_osm_desc)
    else -> null
}
