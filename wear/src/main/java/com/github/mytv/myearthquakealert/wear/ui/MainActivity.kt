package com.github.mytv.myearthquakealert.wear.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.github.mytv.myearthquakealert.wear.data.PhoneLink
import com.github.mytv.myearthquakealert.wear.data.WatchSettings
import com.github.mytv.myearthquakealert.wear.data.WatchStore
import com.github.mytv.myearthquakealert.wear.presentation.PhaseText
import com.github.mytv.myearthquakealert.wear.standalone.StandaloneMonitorService
import com.github.mytv.myearthquakealert.wear.standalone.WatchLocation
import com.github.mytv.myearthquakealert.wear.ui.map.MapContent
import com.github.mytv.myearthquakealert.wear.ui.map.WatchMap
import com.github.mytv.myearthquakealert.wear.ui.theme.WatchTheme
import kotlinx.coroutines.launch

/** The watch app: 状态 → 地图 / 设置. */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val askNotifications = savedInstanceState == null && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        setContent {
            WatchTheme {
                if (askNotifications) {
                    // Alerts arrive as notifications: ask once on first launch.
                    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
                    LaunchedEffect(Unit) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                }
                WatchApp()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            PhoneLink.refreshConnection(this@MainActivity)
            PhoneLink.syncDataItems(this@MainActivity)
        }
        lifecycleScope.launch {
            val settings = WatchSettings.current(this@MainActivity)
            WatchStore.updateStandalone { it.copy(enabled = settings.standaloneEnabled, source = settings.standaloneSource) }
            // The app is visible: a start refused from the background (boot) succeeds now.
            if (settings.standaloneEnabled && !WatchStore.standalone.value.running) {
                StandaloneMonitorService.start(this@MainActivity)
            }
        }
    }
}

@Composable
private fun WatchApp() {
    val nav = rememberSwipeDismissableNavController()
    var focusEventId by rememberSaveable { mutableStateOf<String?>(null) }
    AppScaffold {
        SwipeDismissableNavHost(navController = nav, startDestination = ROUTE_STATUS) {
            composable(ROUTE_STATUS) {
                StatusScreen(
                    onMap = { eventId ->
                        focusEventId = eventId
                        nav.navigate(ROUTE_MAP)
                    },
                    onSettings = { nav.navigate(ROUTE_SETTINGS) },
                )
            }
            composable(ROUTE_MAP) {
                val context = LocalContext.current
                val quakes by WatchStore.quakes.collectAsStateWithLifecycle()
                val status by WatchStore.status.collectAsStateWithLifecycle()
                // The watch's own fix when it has one, else the phone's synced location.
                val user = remember(status) { WatchLocation.current(context) }
                val focused = quakes.firstOrNull { it.eventId == focusEventId }
                WatchMap(
                    content = MapContent(
                        quakes = quakes,
                        focusEventId = focusEventId,
                        userLatitude = user?.latitude,
                        userLongitude = user?.longitude,
                    ),
                    chip = focused?.let { PhaseText.quakeChip(it) },
                )
            }
            composable(ROUTE_SETTINGS) { SettingsScreen() }
        }
    }
}

private const val ROUTE_STATUS = "status"
private const val ROUTE_MAP = "map"
private const val ROUTE_SETTINGS = "settings"
