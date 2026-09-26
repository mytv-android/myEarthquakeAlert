package com.github.mytv.myearthquakealert.ui.main

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.github.mytv.myearthquakealert.MyEarthQuakeAlertApp
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.data.model.EarthquakeInfo
import com.github.mytv.myearthquakealert.data.model.EewEvent
import com.github.mytv.myearthquakealert.data.repository.UserSettings
import com.github.mytv.myearthquakealert.data.source.EewSource
import com.github.mytv.myearthquakealert.domain.SeismicCalculator
import com.github.mytv.myearthquakealert.service.ActiveAlertHolder
import com.github.mytv.myearthquakealert.service.AlertData
import com.github.mytv.myearthquakealert.service.AlertOverlayService
import com.github.mytv.myearthquakealert.service.EewMonitorService
import com.github.mytv.myearthquakealert.ui.adaptive.LayoutMode
import com.github.mytv.myearthquakealert.ui.adaptive.currentLayoutMode
import com.github.mytv.myearthquakealert.ui.map.EarthquakeMap
import com.github.mytv.myearthquakealert.ui.map.EarthquakeMapMode
import com.github.mytv.myearthquakealert.ui.map.EewMapStyle
import com.github.mytv.myearthquakealert.ui.map.MapPoint
import com.github.mytv.myearthquakealert.ui.map.QuakeMarker
import com.github.mytv.myearthquakealert.ui.theme.EeqSpacing
import com.github.mytv.myearthquakealert.ui.theme.csisColor
import com.github.mytv.myearthquakealert.util.LogExporter
import com.github.mytv.myearthquakealert.util.canDrawOverlays
import com.github.mytv.myearthquakealert.util.openOverlaySettings
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    onNavigateToAbout: () -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.applicationContext as MyEarthQuakeAlertApp
    val scope = rememberCoroutineScope()
    val layoutMode = currentLayoutMode()

    val settings by app.settingsRepository.settings.collectAsState(initial = UserSettings())
    val connectionState by app.eewRepository.connectionState.collectAsState()

    val earthquakes = remember { mutableStateListOf<EarthquakeInfo>() }
    var isLoadingHistory by remember { mutableStateOf(false) }
    var historyError by remember { mutableStateOf<String?>(null) }
    var selectedQuake by remember { mutableStateOf<EarthquakeInfo?>(null) }
    var userLocation by remember { mutableStateOf<MapPoint?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }

    val loadFailedText = stringResource(R.string.history_load_failed)

    fun refreshHistory() {
        scope.launch {
            isLoadingHistory = true
            historyError = null
            try {
                val list = app.eewRepository.getEarthquakeHistory()
                earthquakes.clear()
                earthquakes.addAll(list)
            } catch (e: Exception) {
                android.util.Log.e("MainScreen", "Failed to load earthquake history", e)
                historyError = loadFailedText
            } finally {
                isLoadingHistory = false
            }
        }
    }

    fun refreshLocation() {
        scope.launch {
            try {
                val location = app.locationProvider.getLocation()
                userLocation = if (location.latitude != 0.0 || location.longitude != 0.0) {
                    MapPoint(location.latitude, location.longitude)
                } else {
                    null
                }
            } catch (_: Exception) {
                userLocation = null
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshHistory()
        refreshLocation()
    }

    var showMenu by remember { mutableStateOf(false) }

    // ── Permission launchers ──────────────────────────────────────────────
    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            EewMonitorService.start(context)
            scope.launch { app.settingsRepository.updateServiceEnabled(true) }
        } else {
            scope.launch { app.settingsRepository.updateServiceEnabled(false) }
            Toast.makeText(context, context.getString(R.string.overlay_permission_required), Toast.LENGTH_LONG).show()
        }
    }

    fun requestOverlayPermission() {
        context.openOverlaySettings()
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
               ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                              permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (locationGranted) {
            refreshLocation()
        } else {
            Toast.makeText(context, context.getString(R.string.location_permission_required), Toast.LENGTH_LONG).show()
        }
    }

    // ── Actions ───────────────────────────────────────────────────────────
    fun setServiceEnabled(enabled: Boolean) {
        scope.launch {
            if (enabled) {
                if (!context.canDrawOverlays()) {
                    app.settingsRepository.updateServiceEnabled(false)
                    Toast.makeText(context, context.getString(R.string.overlay_permission_required), Toast.LENGTH_LONG).show()
                    requestOverlayPermission()
                    return@launch
                }
                if (!hasNotificationPermission() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    return@launch
                }
                EewMonitorService.start(context)
                app.settingsRepository.updateServiceEnabled(true)
            } else {
                EewMonitorService.stop(context)
                app.settingsRepository.updateServiceEnabled(false)
            }
        }
    }

    fun runSimulation() {
        if (!context.canDrawOverlays()) {
            Toast.makeText(context, context.getString(R.string.overlay_permission_required), Toast.LENGTH_LONG).show()
            requestOverlayPermission()
            return
        }
        if (!hasLocationPermission()) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            )
            return
        }
        scope.launch {
            try {
                val location = app.locationProvider.getLocation()

                // A realistic scenario: M6.5 at 10 km depth, ~85 km away.
                val depthKm = 10.0
                val magnitude = 6.5
                val targetDistanceKm = 85.0
                val epicenter = SeismicCalculator.offsetPoint(
                    location.latitude, location.longitude, 60.0, targetDistanceKm,
                )
                val distance = SeismicCalculator.haversineDistance(
                    location.latitude, location.longitude,
                    epicenter.latitude, epicenter.longitude,
                )
                val arrival = SeismicCalculator.calcWaveArrival(depthKm, distance)
                val localCsis = SeismicCalculator.calcLocalIntensity(magnitude, depthKm, distance)

                val simEvent = EewEvent(
                    id = "sim-1",
                    eventId = "SIMULATION",
                    source = settings.selectedSource.name,
                    reportTime = "",
                    reportNum = 1,
                    originTime = "",
                    hypocenter = "模拟震源",
                    latitude = epicenter.latitude,
                    longitude = epicenter.longitude,
                    magnitude = magnitude,
                    depth = depthKm,
                    maxIntensity = 5.0,
                )

                ActiveAlertHolder.showAlert(
                    AlertData(
                        event = simEvent,
                        userLatitude = location.latitude,
                        userLongitude = location.longitude,
                        pWaveSeconds = arrival.pWaveSeconds,
                        sWaveSeconds = arrival.sWaveSeconds,
                        localCsis = localCsis,
                        distanceKm = distance,
                        isSimulation = true,
                    )
                )
                AlertOverlayService.show(context)
            } catch (e: SecurityException) {
                Toast.makeText(context, context.getString(R.string.location_permission_required), Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, e.message ?: context.getString(R.string.simulation_test), Toast.LENGTH_LONG).show()
            }
        }
    }

    fun selectQuake(quake: EarthquakeInfo) {
        selectedQuake = quake
        if (layoutMode == LayoutMode.COMPACT) {
            selectedTab = 0
        }
    }

    // ── Shared panes ──────────────────────────────────────────────────────
    val serviceSection: @Composable () -> Unit = {
        ServiceSection(
            settings = settings,
            canDrawOverlays = context.canDrawOverlays(),
            onToggle = { setServiceEnabled(it) },
            onGrantOverlay = { requestOverlayPermission() },
        )
    }

    val settingsPane: @Composable () -> Unit = {
        SettingsPane(
            settings = settings,
            onSourceSelected = { source: EewSource ->
                scope.launch { app.settingsRepository.updateSelectedSource(source) }
            },
            onMinMagnitudeChange = { scope.launch { app.settingsRepository.updateActionMinMagnitude(it) } },
            onMinIntensityChange = { scope.launch { app.settingsRepository.updateActionMinIntensity(it) } },
            onIntenseThresholdChange = { scope.launch { app.settingsRepository.updateIntenseThreshold(it) } },
            onAllowDismissChange = { scope.launch { app.settingsRepository.updateAllowDismissWithBack(it) } },
            onMapStyleSelected = { scope.launch { app.settingsRepository.updateMapStyle(it) } },
            onExportLog = { LogExporter.export(context) },
            onSimulate = { runSimulation() },
        )
    }

    val mapPane: @Composable () -> Unit = {
        MapPane(
            mapStyle = settings.mapStyle,
            earthquakes = earthquakes,
            userLocation = userLocation,
            selected = selectedQuake,
            onSelect = { selectQuake(it) },
            onClearSelection = { selectedQuake = null },
        )
    }

    val historyPane: @Composable () -> Unit = {
        EarthquakeHistoryList(
            earthquakes = earthquakes,
            selectedNo = selectedQuake?.no,
            isLoading = isLoadingHistory,
            errorMessage = historyError,
            onSelect = { selectQuake(it) },
            onRetry = { refreshHistory() },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    ConnectionStatusChip(state = connectionState)
                    Spacer(modifier = Modifier.width(EeqSpacing.sm))
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.about)) },
                            onClick = {
                                showMenu = false
                                onNavigateToAbout()
                            }
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            when (layoutMode) {
                LayoutMode.COMPACT -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        TabRow(selectedTabIndex = selectedTab) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                icon = { Icon(Icons.Filled.NotificationsActive, contentDescription = null) },
                                text = { Text(stringResource(R.string.tab_monitor)) },
                            )
                            Tab(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                icon = { Icon(Icons.Filled.History, contentDescription = null) },
                                text = { Text(stringResource(R.string.tab_recent)) },
                            )
                            Tab(
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 },
                                icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                                text = { Text(stringResource(R.string.tab_settings)) },
                            )
                        }
                        when (selectedTab) {
                            0 -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                        .padding(EeqSpacing.md),
                                    verticalArrangement = Arrangement.spacedBy(EeqSpacing.sm),
                                ) {
                                    serviceSection()
                                    Box(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                                        mapPane()
                                    }
                                }
                            }
                            1 -> {
                                Column(modifier = Modifier.fillMaxSize().padding(EeqSpacing.md)) {
                                    Box(modifier = Modifier.weight(1f)) { historyPane() }
                                }
                            }
                            else -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                        .padding(EeqSpacing.md),
                                    verticalArrangement = Arrangement.spacedBy(EeqSpacing.sm),
                                ) {
                                    settingsPane()
                                }
                            }
                        }
                    }
                }

                LayoutMode.MEDIUM -> {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .weight(0.9f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                                .padding(EeqSpacing.md),
                            verticalArrangement = Arrangement.spacedBy(EeqSpacing.sm),
                        ) {
                            serviceSection()
                            settingsPane()
                        }
                        Column(
                            modifier = Modifier
                                .weight(1.3f)
                                .fillMaxHeight()
                                .padding(EeqSpacing.md),
                            verticalArrangement = Arrangement.spacedBy(EeqSpacing.sm),
                        ) {
                            Box(modifier = Modifier.weight(1.1f).fillMaxWidth()) { mapPane() }
                            Box(modifier = Modifier.weight(1f).fillMaxWidth()) { historyPane() }
                        }
                    }
                }

                LayoutMode.EXPANDED -> {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .weight(1.6f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                                .padding(EeqSpacing.md),
                            verticalArrangement = Arrangement.spacedBy(EeqSpacing.sm),
                        ) {
                            serviceSection()
                            settingsPane()
                        }
                        Box(
                            modifier = Modifier
                                .weight(2.4f)
                                .fillMaxHeight()
                                .padding(vertical = EeqSpacing.md, horizontal = EeqSpacing.sm),
                        ) { mapPane() }
                        Column(
                            modifier = Modifier
                                .weight(1.6f)
                                .fillMaxHeight()
                                .padding(EeqSpacing.md),
                        ) { historyPane() }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Panes
// ---------------------------------------------------------------------------

@Composable
private fun ServiceSection(
    settings: UserSettings,
    canDrawOverlays: Boolean,
    onToggle: (Boolean) -> Unit,
    onGrantOverlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(EeqSpacing.sm)) {
        SectionHeader(
            icon = Icons.Filled.NotificationsActive,
            title = stringResource(R.string.monitor_status_title),
        )
        if (!canDrawOverlays) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Column(modifier = Modifier.padding(EeqSpacing.md)) {
                    Text(
                        text = stringResource(R.string.overlay_permission_required),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Spacer(modifier = Modifier.height(EeqSpacing.sm))
                    Button(
                        onClick = onGrantOverlay,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) {
                        Text(stringResource(R.string.grant_permission))
                    }
                }
            }
        }
        ServiceToggleCard(
            enabled = settings.serviceEnabled,
            onToggle = onToggle,
        )
    }
}

@Composable
private fun SettingsPane(
    settings: UserSettings,
    onSourceSelected: (EewSource) -> Unit,
    onMinMagnitudeChange: (Double) -> Unit,
    onMinIntensityChange: (Int) -> Unit,
    onIntenseThresholdChange: (Int) -> Unit,
    onAllowDismissChange: (Boolean) -> Unit,
    onMapStyleSelected: (EewMapStyle) -> Unit,
    onExportLog: () -> Unit,
    onSimulate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(EeqSpacing.sm)) {
        SourceSelector(
            selected = settings.selectedSource,
            onSelected = onSourceSelected,
        )
        Spacer(modifier = Modifier.height(EeqSpacing.xs))
        ThresholdSettings(
            minMagnitude = settings.actionMinMagnitude,
            onMinMagnitudeChange = onMinMagnitudeChange,
            minIntensity = settings.actionMinIntensity,
            onMinIntensityChange = onMinIntensityChange,
            intenseThreshold = settings.intenseThreshold,
            onIntenseThresholdChange = onIntenseThresholdChange,
            allowDismissWithBack = settings.allowDismissWithBack,
            onAllowDismissWithBackChange = onAllowDismissChange,
        )
        Spacer(modifier = Modifier.height(EeqSpacing.xs))
        MapStyleSelector(
            selected = settings.mapStyle,
            onSelected = onMapStyleSelected,
        )
        Spacer(modifier = Modifier.height(EeqSpacing.xs))
        SimulationCard(onSimulate = onSimulate)
        Spacer(modifier = Modifier.height(EeqSpacing.xs))
        LogExportCard(onExport = onExportLog)
    }
}

@Composable
private fun MapPane(
    mapStyle: EewMapStyle,
    earthquakes: List<EarthquakeInfo>,
    userLocation: MapPoint?,
    selected: EarthquakeInfo?,
    onSelect: (EarthquakeInfo) -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val quakeMarkers = earthquakes.mapNotNull { eq ->
        val lat = eq.latitude.toDoubleOrNull()
        val lon = eq.longitude.toDoubleOrNull()
        if (lat == null || lon == null || (lat == 0.0 && lon == 0.0)) return@mapNotNull null
        val magnitude = eq.magnitude.toDoubleOrNull() ?: 3.0
        QuakeMarker(
            id = eq.no.toString(),
            latitude = lat,
            longitude = lon,
            colorArgb = csisColor(eq.intensity.toDoubleOrNull() ?: 0.0).toArgb(),
            radiusDp = (7f + magnitude.toFloat()).coerceIn(9f, 15f),
        )
    }

    val selectedPoint = selected?.let { eq ->
        val lat = eq.latitude.toDoubleOrNull() ?: return@let null
        val lon = eq.longitude.toDoubleOrNull() ?: return@let null
        MapPoint(lat, lon)
    }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier.fillMaxSize(),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            EarthquakeMap(
                style = mapStyle,
                mode = EarthquakeMapMode.BROWSE,
                modifier = Modifier.fillMaxSize(),
                userPoint = userLocation,
                quakes = quakeMarkers,
                selectedQuakeId = selected?.no?.toString(),
                focusPoint = selectedPoint,
                onQuakeClick = { marker ->
                    earthquakes.firstOrNull { it.no.toString() == marker.id }?.let(onSelect)
                },
                maxFitDistanceKm = 4500.0,
            )

            if (selected != null) {
                SelectedQuakeCard(
                    quake = selected,
                    userLocation = userLocation,
                    onClose = onClearSelection,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(EeqSpacing.sm),
                )
            } else if (earthquakes.isNotEmpty()) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(EeqSpacing.sm),
                ) {
                    Text(
                        text = stringResource(R.string.map_hint_select),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectedQuakeCard(
    quake: EarthquakeInfo,
    userLocation: MapPoint?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val intensity = quake.intensity.toDoubleOrNull() ?: 0.0
    val distanceText = remember(quake.no, userLocation) {
        val lat = quake.latitude.toDoubleOrNull()
        val lon = quake.longitude.toDoubleOrNull()
        if (userLocation == null || lat == null || lon == null) {
            null
        } else {
            SeismicCalculator.haversineDistance(userLocation.latitude, userLocation.longitude, lat, lon)
        }
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        shadowElevation = 4.dp,
        modifier = modifier.widthIn(max = 320.dp),
    ) {
        Column(modifier = Modifier.padding(EeqSpacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = quake.location,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(EeqSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val chipColor = csisColor(intensity)
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = chipColor,
                ) {
                    Text(
                        text = stringResource(R.string.alert_intensity_unit) + " ${intensity.toInt()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (chipColor.luminance() > 0.45f) Color.Black else Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
                Text(
                    text = "M${quake.magnitude} · ${quake.depth}km",
                    style = MaterialTheme.typography.labelMedium,
                )
                if (distanceText != null) {
                    Text(
                        text = stringResource(R.string.distance_to_you).format("%.0f".format(distanceText)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = quake.time,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
