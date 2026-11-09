package com.github.mytv.myearthquakealert.wear.alert

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.github.mytv.myearthquakealert.wear.data.WatchStore
import com.github.mytv.myearthquakealert.wear.presentation.PhaseText
import com.github.mytv.myearthquakealert.wear.domain.Countdown
import com.github.mytv.myearthquakealert.wear.ui.AlertScreen
import com.github.mytv.myearthquakealert.wear.ui.map.MapContent
import com.github.mytv.myearthquakealert.wear.ui.map.WatchMap
import com.github.mytv.myearthquakealert.wear.ui.theme.WatchColors
import com.github.mytv.myearthquakealert.wear.ui.theme.WatchTheme

/**
 * The alert over the watch face (opened by the full-screen intent, or directly when the app
 * is in use): countdown → map. Closes when the alert ends, expires or 我已安全 is pressed.
 */
class AlertActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            WatchTheme {
                val alert by WatchStore.alert.collectAsStateWithLifecycle()
                val current = alert
                if (current == null) {
                    LaunchedEffect(Unit) { finish() }
                    return@WatchTheme
                }
                AppScaffold(timeText = {}, containerColor = WatchColors.AlertBackground) {
                    val nav = rememberSwipeDismissableNavController()
                    SwipeDismissableNavHost(navController = nav, startDestination = ROUTE_ALERT) {
                        composable(ROUTE_ALERT) {
                            AlertScreen(
                                alert = current,
                                onSafe = {
                                    WatchAlerts.dismiss(this@AlertActivity)
                                    finish()
                                },
                                onMap = { nav.navigate(ROUTE_MAP) },
                                onExpired = {
                                    WatchAlerts.expireIfDone()
                                    finish()
                                },
                            )
                        }
                        composable(ROUTE_MAP) {
                            val seconds = Countdown.snapshot(current, System.currentTimeMillis()).sSeconds
                            WatchMap(
                                content = MapContent(alert = current),
                                chip = "${PhaseText.shortPlace(current.place)} M${PhaseText.magnitude(current.magnitude)}" +
                                    if (seconds > 0) " · ${seconds}s" else "",
                            )
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val ROUTE_ALERT = "alert"
        private const val ROUTE_MAP = "map"

        fun intent(context: Context): Intent =
            Intent(context, AlertActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
