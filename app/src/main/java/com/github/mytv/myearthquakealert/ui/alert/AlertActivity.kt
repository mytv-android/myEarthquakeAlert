package com.github.mytv.myearthquakealert.ui.alert

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.mytv.myearthquakealert.service.ActiveAlertHolder
import com.github.mytv.myearthquakealert.ui.theme.MyEarthQuakeAlertTheme

/**
 * Full-screen alert (design §4: lock screen and STRONG tier). Launched by the monitoring
 * service through a full-screen intent; shows over the lock screen and turns the screen
 * on. Renders ActiveAlertHolder.activeAlert and finishes when it is cleared.
 */
class AlertActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            MyEarthQuakeAlertTheme {
                val alert by ActiveAlertHolder.activeAlert.collectAsStateWithLifecycle()
                val data = alert
                if (data == null) {
                    finish()
                } else {
                    AlertOverlay(
                        alertData = data,
                        onDismiss = { ActiveAlertHolder.dismissAlert() },
                        allowBackDismiss = true,
                        intenseThreshold = 5,
                        mapStyle = com.github.mytv.myearthquakealert.ui.map.EewMapStyle.AMAP,
                        wideLayout = resources.configuration.screenWidthDp > resources.configuration.screenHeightDp,
                    )
                }
            }
        }
    }
}
