package com.github.mytv.myearthquakealert.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
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
import com.github.mytv.myearthquakealert.ui.alert.AlertOverlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_SHOW -> showAlert()
            ACTION_DISMISS -> dismissAlert()
        }
        return START_NOT_STICKY
    }

    private fun showAlert() {
        if (overlayView != null) return

        lifecycleRegistry.currentState = Lifecycle.State.RESUMED

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val composeView = ComposeView(this).also {
            it.setViewTreeLifecycleOwner(this)
            it.setViewTreeSavedStateRegistryOwner(this)
        }

        composeView.setContent {
            val alertData by ActiveAlertHolder.activeAlert.collectAsState()
            if (alertData != null) {
                AlertOverlay(
                    alertData = alertData!!,
                    onDismiss = { dismissAlert() },
                )
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 100
        }

        windowManager?.addView(composeView, params)
        overlayView = composeView

        val alertData = ActiveAlertHolder.activeAlert.value ?: return
        autoDismissJob = serviceScope.launch {
            delay(((alertData.sWaveSeconds + 10) * 1000).toLong())
            dismissAlert()
        }
    }

    private fun dismissAlert() {
        autoDismissJob?.cancel()
        ActiveAlertHolder.dismissAlert()

        overlayView?.let {
            windowManager?.removeView(it)
        }
        overlayView = null

        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        stopSelf()
    }

    override fun onDestroy() {
        autoDismissJob?.cancel()
        serviceScope.cancel()
        overlayView?.let { windowManager?.removeView(it) }
        overlayView = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_SHOW = "com.github.mytv.myearthquakealert.SHOW_ALERT"
        const val ACTION_DISMISS = "com.github.mytv.myearthquakealert.DISMISS_ALERT"

        fun show(context: android.content.Context) {
            val intent = Intent(context, AlertOverlayService::class.java)
            intent.action = ACTION_SHOW
            context.startService(intent)
        }

        fun dismiss(context: android.content.Context) {
            val intent = Intent(context, AlertOverlayService::class.java)
            intent.action = ACTION_DISMISS
            context.startService(intent)
        }
    }
}
