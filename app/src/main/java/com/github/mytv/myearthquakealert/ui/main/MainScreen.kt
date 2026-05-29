package com.github.mytv.myearthquakealert.ui.main

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.mytv.myearthquakealert.MyEarthQuakeAlertApp
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.data.model.EewEvent
import com.github.mytv.myearthquakealert.data.model.EarthquakeInfo
import com.github.mytv.myearthquakealert.data.repository.UserSettings
import com.github.mytv.myearthquakealert.data.source.EewSource
import com.github.mytv.myearthquakealert.domain.SeismicCalculator
import com.github.mytv.myearthquakealert.service.ActiveAlertHolder
import com.github.mytv.myearthquakealert.service.AlertData
import com.github.mytv.myearthquakealert.service.AlertOverlayService
import com.github.mytv.myearthquakealert.service.EewMonitorService
import com.github.mytv.myearthquakealert.ui.adaptive.AdaptiveLayout
import com.github.mytv.myearthquakealert.ui.theme.EeqSpacing
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

    val settings by app.settingsRepository.settings.collectAsState(initial = UserSettings())
    val connectionState by app.eewRepository.connectionState.collectAsState()
    val earthquakes = remember { mutableStateListOf<EarthquakeInfo>() }
    val isLoadingHistory = remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            isLoadingHistory.value = true
            earthquakes.clear()
            earthquakes.addAll(app.eewRepository.getEarthquakeHistory())
        } catch (_: Exception) {
        } finally {
            isLoadingHistory.value = false
        }
    }

    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
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
        modifier = modifier,
    ) { padding ->
        val settingsPane: @Composable () -> Unit = {
            Column(
                modifier = Modifier.padding(EeqSpacing.md),
                verticalArrangement = Arrangement.spacedBy(EeqSpacing.md),
            ) {
                if (!context.canDrawOverlays()) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                    ) {
                        Column(modifier = Modifier.padding(EeqSpacing.md)) {
                            Text(
                                text = stringResource(R.string.overlay_permission_required),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(modifier = Modifier.height(EeqSpacing.sm))
                            Card(
                                onClick = { context.openOverlaySettings() },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError,
                                ),
                            ) {
                                Text(
                                    text = stringResource(R.string.grant_permission),
                                    modifier = Modifier.padding(horizontal = EeqSpacing.md, vertical = EeqSpacing.sm),
                                )
                            }
                        }
                    }
                }

                ServiceToggleCard(
                    enabled = settings.serviceEnabled,
                    onToggle = { enabled ->
                        scope.launch {
                            app.settingsRepository.updateServiceEnabled(enabled)
                            if (enabled) {
                                if (context.canDrawOverlays()) {
                                    EewMonitorService.start(context)
                                } else {
                                    app.settingsRepository.updateServiceEnabled(false)
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.overlay_permission_required),
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                            } else {
                                EewMonitorService.stop(context)
                            }
                        }
                    },
                )

                SourceSelector(
                    selected = settings.selectedSource,
                    onSelected = { source ->
                        scope.launch { app.settingsRepository.updateSelectedSource(source) }
                    },
                )

                ThresholdSettings(
                    minMagnitude = settings.actionMinMagnitude,
                    onMinMagnitudeChange = { scope.launch { app.settingsRepository.updateActionMinMagnitude(it) } },
                    minIntensity = settings.actionMinIntensity,
                    onMinIntensityChange = { scope.launch { app.settingsRepository.updateActionMinIntensity(it) } },
                    intenseThreshold = settings.intenseThreshold,
                    onIntenseThresholdChange = { scope.launch { app.settingsRepository.updateIntenseThreshold(it) } },
                    allowDismissWithBack = settings.allowDismissWithBack,
                    onAllowDismissWithBackChange = { scope.launch { app.settingsRepository.updateAllowDismissWithBack(it) } },
                )

                SimulationCard(
                    onSimulate = {
                        if (!context.canDrawOverlays()) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.overlay_permission_required),
                                Toast.LENGTH_LONG,
                            ).show()
                            return@SimulationCard
                        }
                        scope.launch {
                            try {
                                val location = app.locationProvider.getLocation()
                                val simEvent = EewEvent(
                                    id = "sim-1",
                                    eventId = "SIMULATION",
                                    source = EewSource.CENC.name,
                                    reportTime = "",
                                    reportNum = 1,
                                    originTime = "",
                                    hypocenter = "模拟震源",
                                    latitude = 0.0,
                                    longitude = 0.0,
                                    magnitude = 3.0,
                                    depth = 10.0,
                                    maxIntensity = 3,
                                )

                                val arrival = SeismicCalculator.calcWaveArrival(10.0, 40.0)

                                ActiveAlertHolder.showAlert(
                                    AlertData(
                                        event = simEvent,
                                        userLatitude = location.latitude,
                                        userLongitude = location.longitude,
                                        pWaveSeconds = arrival.pWaveSeconds,
                                        sWaveSeconds = arrival.sWaveSeconds,
                                        localCsis = 3.0,
                                        isSimulation = true,
                                    )
                                )
                                AlertOverlayService.show(context)
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    e.message ?: context.getString(R.string.simulation_test),
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        }
                    },
                )
            }
        }

        val listPane: @Composable () -> Unit = {
            Column(modifier = Modifier.padding(EeqSpacing.md)) {
                Text(
                    text = stringResource(R.string.earthquake_history),
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(modifier = Modifier.height(EeqSpacing.sm))
                if (isLoadingHistory.value) {
                    CircularProgressIndicator()
                } else {
                    EarthquakeHistoryList(earthquakes = earthquakes)
                }
            }
        }

        val detailPane: @Composable () -> Unit = {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.earthquake_history),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        AdaptiveLayout(
            settingsPane = settingsPane,
            listPane = listPane,
            detailPane = detailPane,
            contentPadding = padding,
        )
    }
}
