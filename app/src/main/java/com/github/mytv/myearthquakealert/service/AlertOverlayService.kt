package com.github.mytv.myearthquakealert.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.github.mytv.myearthquakealert.MainActivity
import com.github.mytv.myearthquakealert.MyEarthQuakeAlertApp
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.ui.alert.AlertOverlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AlertOverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null
    private var autoDismissJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        try {
            when (action) {
                ACTION_SHOW -> showAlert()
                ACTION_DISMISS -> dismissAlert()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onStartCommand action=$action", e)
            // Ensure foreground state to avoid crash
            if (action == ACTION_SHOW) {
                try {
                    startForeground(OVERLAY_NOTIFICATION_ID, buildNotification())
                } catch (_: Exception) {}
            }
        }
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            OVERLAY_CHANNEL_ID,
            getString(R.string.alert_title),
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, OVERLAY_CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.alert_title))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
    }

    private fun showAlert() {
        // If already showing, just update the auto-dismiss timer
        if (overlayView != null) {
            val alertData = ActiveAlertHolder.activeAlert.value ?: return
            autoDismissJob?.cancel()
            autoDismissJob = serviceScope.launch {
                delay(((alertData.sWaveSeconds + 10) * 1000).toLong())
                dismissAlert()
            }
            return
        }

        // Start as foreground service first, before any other work
        startForeground(OVERLAY_NOTIFICATION_ID, buildNotification())

        // Ensure lifecycle is at least CREATED before moving to RESUMED
        if (lifecycleRegistry.currentState == Lifecycle.State.DESTROYED) {
            lifecycleRegistry.currentState = Lifecycle.State.CREATED
        }
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val app = application as MyEarthQuakeAlertApp

        val composeView = ComposeView(this).also {
            it.setViewTreeLifecycleOwner(this)
            it.setViewTreeSavedStateRegistryOwner(this)
        }

        // Raw Back key handling: this window has no Activity above it, so the
        // framework's back dispatch never reaches a dispatcher here. Intercept the
        // key at the view level: consume it always, dismiss only when allowed.
        composeView.isFocusable = true
        composeView.isFocusableInTouchMode = true
        composeView.setOnKeyListener { _, keyCode, event ->
            if (keyCode == android.view.KeyEvent.KEYCODE_BACK && event.action == android.view.KeyEvent.ACTION_UP) {
                Log.i(TAG, "Back key received by overlay")
                serviceScope.launch {
                    val allow = app.settingsRepository.settings.first().allowDismissWithBack
                    Log.i(TAG, "Back key: allowDismissWithBack=$allow")
                    if (allow) postDismiss()
                }
                true
            } else {
                false
            }
        }

        val backPressedDispatcher = OnBackPressedDispatcher()

        val metrics = resources.displayMetrics
        val landscape = metrics.widthPixels > metrics.heightPixels

        composeView.setContent {
            val settings by app.settingsRepository.settings.collectAsState(
                initial = com.github.mytv.myearthquakealert.data.repository.UserSettings()
            )
            CompositionLocalProvider(
                LocalOnBackPressedDispatcherOwner provides object :
                    androidx.activity.OnBackPressedDispatcherOwner {
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

        // Compact floating card: the window wraps the content, dims the backdrop
        // slightly, and stays focusable so Back / D-pad reach the overlay.
        // Width is pinned here (measure-driven layout guesses are unreliable for
        // wrap windows); the card decides wide/narrow from the screen shape.
        val windowWidthPx = if (landscape) {
            (metrics.widthPixels * 0.66f).toInt()
                .coerceAtMost((720 * metrics.density).toInt())
        } else {
            (metrics.widthPixels - 48 * metrics.density).toInt()
        }
        val params = WindowManager.LayoutParams(
            windowWidthPx,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DIM_BEHIND,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
            dimAmount = 0.35f
        }

        try {
            windowManager?.addView(composeView, params)
            composeView.post {
                composeView.requestFocus()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to add overlay view", e)
            overlayView = null
            lifecycleRegistry.currentState = Lifecycle.State.CREATED
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        overlayView = composeView

        val alertData = ActiveAlertHolder.activeAlert.value ?: return
        autoDismissJob = serviceScope.launch {
            delay(((alertData.sWaveSeconds + 10) * 1000).toLong())
            dismissAlert()
        }
    }

    /**
     * Defer teardown until after the current input event completes — removing the
     * focused window mid-dispatch lets the same key fall through to whatever is
     * underneath (e.g. Back finishing the activity behind the overlay).
     */
    private fun postDismiss() {
        Handler(Looper.getMainLooper()).post { dismissAlert() }
    }

    private fun dismissAlert() {
        Log.i(TAG, "dismissAlert")
        autoDismissJob?.cancel()
        ActiveAlertHolder.dismissAlert()

        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {
            }
        }
        overlayView = null

        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        stopForeground(STOP_FOREGROUND_REMOVE)
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
        private const val OVERLAY_CHANNEL_ID = "alert_overlay"
        private const val OVERLAY_NOTIFICATION_ID = 2
        const val ACTION_SHOW = "com.github.mytv.myearthquakealert.SHOW_ALERT"
        const val ACTION_DISMISS = "com.github.mytv.myearthquakealert.DISMISS_ALERT"

        fun show(context: android.content.Context) {
            val intent = Intent(context, AlertOverlayService::class.java)
            intent.action = ACTION_SHOW
            context.startForegroundService(intent)
        }

        fun dismiss(context: android.content.Context) {
            val intent = Intent(context, AlertOverlayService::class.java)
            intent.action = ACTION_DISMISS
            context.startForegroundService(intent)
        }
    }
}
