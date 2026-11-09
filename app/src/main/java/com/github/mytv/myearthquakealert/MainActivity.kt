package com.github.mytv.myearthquakealert

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.mytv.myearthquakealert.data.repository.UserSettings
import com.github.mytv.myearthquakealert.service.DeliveryChecks
import com.github.mytv.myearthquakealert.service.EewMonitorService
import com.github.mytv.myearthquakealert.service.MonitorPhase
import com.github.mytv.myearthquakealert.service.MonitorStatus
import com.github.mytv.myearthquakealert.ui.about.UpdateProgressOverlay
import com.github.mytv.myearthquakealert.ui.adaptive.LayoutKind
import com.github.mytv.myearthquakealert.ui.adaptive.LocalResolvedLayout
import com.github.mytv.myearthquakealert.ui.adaptive.rememberResolvedLayout
import com.github.mytv.myearthquakealert.ui.main.AppShell
import com.github.mytv.myearthquakealert.ui.main.DestinationRequest
import com.github.mytv.myearthquakealert.ui.main.LocalUserSettings
import com.github.mytv.myearthquakealert.ui.navigation.Destination
import com.github.mytv.myearthquakealert.ui.quakes.QuakesViewModel
import com.github.mytv.myearthquakealert.ui.theme.MyEarthQuakeAlertTheme
import com.github.mytv.myearthquakealert.ui.theme.isDarkFor
import com.github.mytv.myearthquakealert.update.UpdateController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** The destination asked for by the launching intent (诊断 notification action, tiles…). */
    private val destinationRequest = MutableStateFlow<DestinationRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A recreated activity keeps its own navigation state; only a fresh launch applies the extra.
        if (savedInstanceState == null) deliver(intent)
        // 应用内更新: the start-time check (自动检查更新, at most hourly).
        if (savedInstanceState == null) UpdateController.get(this).checkOnStart()

        val app = application as MyEarthQuakeAlertApp
        setContent {
            val loaded by remember(app) { app.settingsRepository.settings }
                .collectAsStateWithLifecycle(initialValue = null)
            val settings: UserSettings = loaded ?: return@setContent

            val layout = rememberResolvedLayout(settings.layoutOverride)
            val tenFoot = layout.kind == LayoutKind.TV || layout.kind == LayoutKind.KIOSK
            val dark = tenFoot || isDarkFor(settings.themeMode, isSystemInDarkTheme())
            LaunchedEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                )
            }

            val vm: QuakesViewModel = viewModel(factory = QuakesViewModel.Factory)
            val request by destinationRequest.collectAsStateWithLifecycle()
            LifecycleResumeEffect(Unit) {
                vm.refreshIfStale()
                onPauseOrDispose { }
            }

            MyEarthQuakeAlertTheme(darkTheme = dark, tv = layout.kind == LayoutKind.TV) {
                CompositionLocalProvider(
                    LocalResolvedLayout provides layout,
                    LocalUserSettings provides settings,
                ) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        AppShell(layout = layout, settings = settings, vm = vm, request = request)
                        if (layout.kind == LayoutKind.TV) UpdateProgressOverlay(Modifier.align(Alignment.BottomEnd))
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deliver(intent)
    }

    override fun onResume() {
        super.onResume()
        // Permissions, battery and DND may have changed in the system settings meanwhile.
        DeliveryChecks.refresh(this)
        healMonitoring()
    }

    private fun deliver(intent: Intent?) {
        val destination = Destination.fromRoute(intent?.getStringExtra(Destination.EXTRA)) ?: return
        // Unique across process restarts, so a saved "already handled" id never matches a new intent.
        val page = intent?.getStringExtra(Destination.EXTRA_PAGE)?.takeIf { destination == Destination.SETTINGS }
        destinationRequest.value = DestinationRequest(SystemClock.elapsedRealtimeNanos(), destination, page)
    }

    /**
     * 后台持续监测 is on but the service is not running (killed with the app, or never started
     * on this boot because 开机自动启动 is off): start it again. Starting is idempotent.
     */
    private fun healMonitoring() {
        val app = application as MyEarthQuakeAlertApp
        lifecycleScope.launch {
            val s = app.settingsRepository.settings.first()
            val now = System.currentTimeMillis()
            if (s.serviceEnabled && !s.isPaused(now) && MonitorStatus.state.value.phase == MonitorPhase.OFF) {
                EewMonitorService.start(this@MainActivity)
            }
        }
    }
}
