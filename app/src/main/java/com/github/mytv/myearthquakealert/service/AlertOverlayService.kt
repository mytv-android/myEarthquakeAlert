package com.github.mytv.myearthquakealert.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.github.mytv.myearthquakealert.MyEarthQuakeAlertApp
import com.github.mytv.myearthquakealert.data.repository.UserSettings
import com.github.mytv.myearthquakealert.ui.alert.AlertOverlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The floating alert card (REMIND tier while the device is in use, and on TV) drawn over
 * other apps with the overlay permission. It shows whatever [ActiveAlertHolder] holds and
 * goes away when the holder is cleared (我已安全, Back when allowed, the presenter) or
 * [AUTO_DISMISS_AFTER_ARRIVAL_MS] after the S wave.
 *
 * Not a foreground service of its own: the monitoring service is in the foreground
 * whenever an alert is presented (monitoring on, or the drill's alert notification), which
 * lets it start this one. On TV the card does not take focus, so the remote keeps
 * controlling the program underneath (字幕条 style); on phones it takes focus so Back and
 * the D-pad reach it.
 */
class AlertOverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null
    private var autoDismissJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        // The process may have started before the first unlock; the map needs its config.
        (application as MyEarthQuakeAlertApp).ensureMapConfigured()
        serviceScope.launch {
            ActiveAlertHolder.activeAlert.collect { alert ->
                // StateFlow conflation: act on the current value, not a stale emission.
                if (alert == null && ActiveAlertHolder.activeAlert.value == null) removeAndStop()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        try {
            when (action) {
                ACTION_SHOW -> showAlert()
                ACTION_DISMISS -> dismissAlert()
                ACTION_HIDE -> removeAndStop()
                else -> if (overlayView == null) stopSelf()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onStartCommand action=$action", e)
            removeAndStop()
        }
        return START_NOT_STICKY
    }

    private fun showAlert() {
        val alert = ActiveAlertHolder.activeAlert.value
        if (alert == null) {
            removeAndStop()
            return
        }
        scheduleAutoDismiss(alert)
        if (overlayView != null) return // the composition follows the holder

        if (lifecycleRegistry.currentState == Lifecycle.State.DESTROYED) {
            lifecycleRegistry.currentState = Lifecycle.State.CREATED
        }
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED

        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = wm
        val app = application as MyEarthQuakeAlertApp
        val tv = DeviceState.isTv(this)

        val composeView = ComposeView(this).also {
            it.setViewTreeLifecycleOwner(this)
            it.setViewTreeSavedStateRegistryOwner(this)
        }

        // Raw Back key handling: this window has no Activity above it, so the framework's
        // back dispatch never reaches a dispatcher here. Consume Back always, dismiss only
        // when allowed.
        if (!tv) {
            composeView.isFocusable = true
            composeView.isFocusableInTouchMode = true
            composeView.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    if (currentSettings.allowDismissWithBack) postDismiss()
                    true
                } else {
                    false
                }
            }
        }

        val backPressedDispatcher = OnBackPressedDispatcher()
        val metrics = resources.displayMetrics
        val landscape = metrics.widthPixels > metrics.heightPixels

        composeView.setContent {
            val settings by app.settingsRepository.settings.collectAsState(initial = currentSettings)
            SideEffect { currentSettings = settings }
            CompositionLocalProvider(
                LocalOnBackPressedDispatcherOwner provides object : OnBackPressedDispatcherOwner {
                    override val lifecycle get() = lifecycleRegistry
                    override val onBackPressedDispatcher get() = backPressedDispatcher
                }
            ) {
                val alertData by ActiveAlertHolder.activeAlert.collectAsState()
                alertData?.let { data ->
                    AlertOverlay(
                        alertData = data,
                        onDismiss = { postDismiss() },
                        allowBackDismiss = settings.allowDismissWithBack,
                        intenseThreshold = settings.intenseThreshold,
                        mapStyle = settings.mapStyle,
                        wideLayout = landscape,
                    )
                }
            }
        }

        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        }

        // Compact floating card: the window wraps the content. Width is pinned here
        // (measure-driven layout guesses are unreliable for wrap windows); the card decides
        // wide/narrow from the screen shape. TV: bottom of the screen, no dim, no focus.
        val windowWidthPx = if (landscape) {
            (metrics.widthPixels * 0.66f).toInt().coerceAtMost((720 * metrics.density).toInt())
        } else {
            (metrics.widthPixels - 48 * metrics.density).toInt()
        }
        val flags = if (tv) {
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        } else {
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_DIM_BEHIND
        }
        val params = WindowManager.LayoutParams(
            windowWidthPx,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            flags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            if (tv) {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                y = (32 * metrics.density).toInt()
            } else {
                gravity = Gravity.CENTER
                dimAmount = 0.35f
            }
        }

        try {
            wm.addView(composeView, params)
            if (!tv) composeView.post { composeView.requestFocus() }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to add overlay view", e)
            overlayView = null
            lifecycleRegistry.currentState = Lifecycle.State.CREATED
            stopSelf()
            return
        }
        overlayView = composeView
    }

    @Volatile private var currentSettings = UserSettings()

    private fun scheduleAutoDismiss(alert: AlertData) {
        autoDismissJob?.cancel()
        val eventId = alert.event.eventId
        autoDismissJob = serviceScope.launch {
            delay((alert.sArrivalAt + AUTO_DISMISS_AFTER_ARRIVAL_MS - System.currentTimeMillis()).coerceAtLeast(0L))
            val current = ActiveAlertHolder.activeAlert.value
            if (current == null || current.event.eventId == eventId) {
                ActiveAlertHolder.dismissAlert()
                removeAndStop()
            } else {
                scheduleAutoDismiss(current)
            }
        }
    }

    /**
     * Defer teardown until after the current input event completes — removing the focused
     * window mid-dispatch lets the same key fall through to whatever is underneath (e.g.
     * Back finishing the activity behind the overlay).
     */
    private fun postDismiss() {
        Handler(Looper.getMainLooper()).post { dismissAlert() }
    }

    /** 我已安全 / Back: ends the alert everywhere. */
    private fun dismissAlert() {
        ActiveAlertHolder.dismissAlert()
        removeAndStop()
    }

    /** Removes the card only (the alert may continue elsewhere, e.g. escalated to full screen). */
    private fun removeAndStop() {
        autoDismissJob?.cancel()
        autoDismissJob = null
        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {
            }
        }
        overlayView = null
        showing = false
        if (lifecycleRegistry.currentState != Lifecycle.State.DESTROYED) {
            lifecycleRegistry.currentState = Lifecycle.State.CREATED
        }
        stopSelf()
    }

    override fun onDestroy() {
        autoDismissJob?.cancel()
        serviceScope.cancel()
        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {
            }
        }
        overlayView = null
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AlertOverlayService"
        const val ACTION_SHOW = "com.github.mytv.myearthquakealert.SHOW_ALERT"
        const val ACTION_DISMISS = "com.github.mytv.myearthquakealert.DISMISS_ALERT"
        /** Removes the card without ending the alert. */
        const val ACTION_HIDE = "com.github.mytv.myearthquakealert.HIDE_ALERT"
        private const val AUTO_DISMISS_AFTER_ARRIVAL_MS = 120_000L

        @Volatile private var showing = false

        /**
         * Shows the card for the alert in [ActiveAlertHolder]. Returns false when the service
         * could not be started (no foreground service holding the process in the foreground).
         */
        fun show(context: Context): Boolean = try {
            context.startService(Intent(context, AlertOverlayService::class.java).setAction(ACTION_SHOW))
            showing = true
            true
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Cannot show the overlay from the background", e)
            false
        }

        /** Ends the alert and removes the card. */
        fun dismiss(context: Context) {
            ActiveAlertHolder.dismissAlert()
            hide(context)
        }

        /** Removes the card, keeping the alert (e.g. it escalated to the full-screen alert). */
        fun hide(context: Context) {
            if (!showing) return
            showing = false
            try {
                context.startService(Intent(context, AlertOverlayService::class.java).setAction(ACTION_HIDE))
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Cannot hide the overlay", e)
            }
        }
    }
}
