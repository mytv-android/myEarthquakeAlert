package com.github.mytv.myearthquakealert.wear

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.github.mytv.myearthquakealert.wear.alert.WatchAlerts
import com.github.mytv.myearthquakealert.wear.data.PhoneLink
import com.github.mytv.myearthquakealert.wear.data.WatchStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import java.io.File

/**
 * The watch application: loads [WatchStore], creates the notification channels, keeps the
 * tile and the complication in step with the store and tracks whether an activity of the
 * app is visible (alerts then open their screen directly).
 */
class WearApp : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var started = 0

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        WatchStore.init(this)
        WatchAlerts.init(this)
        configureMap()

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                started += 1
                isForeground = true
            }

            override fun onActivityStopped(activity: Activity) {
                started = (started - 1).coerceAtLeast(0)
                isForeground = started > 0
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })

        scope.launch {
            WatchStore.surfaceInputs()
                .drop(1)
                .debounce(SURFACE_DEBOUNCE_MS)
                .collect { WearSurfaces.refresh(this@WearApp) }
        }
        scope.launch { PhoneLink.refreshConnection(this@WearApp) }
    }

    private fun configureMap() {
        try {
            val config = Configuration.getInstance()
            config.userAgentValue = packageName
            config.osmdroidBasePath = File(cacheDir, "osmdroid")
            config.osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
            // Small screen, small cache.
            config.tileFileSystemCacheMaxBytes = 30L * 1024 * 1024
            config.tileFileSystemCacheTrimBytes = 24L * 1024 * 1024
        } catch (e: Exception) {
            // The map then uses osmdroid's defaults.
        }
    }

    companion object {
        private const val SURFACE_DEBOUNCE_MS = 1_000L

        /** An activity of the app is started (visible). */
        @Volatile var isForeground: Boolean = false
            private set
    }
}
