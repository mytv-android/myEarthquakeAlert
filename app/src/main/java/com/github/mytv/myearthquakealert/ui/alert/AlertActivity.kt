package com.github.mytv.myearthquakealert.ui.alert

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.mytv.myearthquakealert.MyEarthQuakeAlertApp
import com.github.mytv.myearthquakealert.data.repository.UserSettings
import com.github.mytv.myearthquakealert.service.ActiveAlertHolder
import com.github.mytv.myearthquakealert.ui.adaptive.LocalResolvedLayout
import com.github.mytv.myearthquakealert.ui.adaptive.rememberResolvedLayout
import com.github.mytv.myearthquakealert.ui.theme.MyEarthQuakeAlertTheme

/**
 * Full-screen alert (design §4: lock screen and STRONG tier). Launched by the monitoring
 * service through a full-screen intent; shows over the lock screen, turns the screen on
 * and keeps it on. Renders [ActiveAlertHolder.activeAlert] in the layout of the device
 * (phone, wide, dual screen, tabletop, TV) and finishes once the alert is cleared.
 */
class AlertActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val settingsFlow = (application as MyEarthQuakeAlertApp).settingsRepository.settings
        setContent {
            val alert by ActiveAlertHolder.activeAlert.collectAsStateWithLifecycle()
            val stored by settingsFlow.collectAsStateWithLifecycle<UserSettings?>(initialValue = null)
            val resolved = rememberResolvedLayout()
            val television = remember { isTelevisionDevice(this@AlertActivity) }
            val layout = if (television && !resolved.isTelevision) resolved.copy(isTelevision = true, isTouch = false) else resolved
            val data = alert

            LaunchedEffect(data == null) {
                if (data == null) finish()
            }

            MyEarthQuakeAlertTheme(darkTheme = true) {
                CompositionLocalProvider(LocalResolvedLayout provides layout) {
                    if (data != null) {
                        // Never wait for settings: an alert renders with the defaults until they load.
                        val settings = stored ?: UserSettings()
                        AlertOverlay(
                            alertData = data,
                            onDismiss = { ActiveAlertHolder.dismissAlert() },
                            allowBackDismiss = settings.allowDismissWithBack,
                            intenseThreshold = settings.intenseThreshold,
                            mapStyle = settings.mapStyle,
                            wideLayout = true,
                            presentation = AlertPresentation.FULL_SCREEN,
                            layout = layout,
                            tabletopSplit = settings.tabletopSplit,
                        )
                    }
                }
            }
        }
    }
}
