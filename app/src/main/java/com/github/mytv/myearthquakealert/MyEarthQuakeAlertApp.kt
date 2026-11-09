package com.github.mytv.myearthquakealert

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Process
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.github.mytv.myearthquakealert.data.api.WolfxApi
import com.github.mytv.myearthquakealert.data.repository.EewRepository
import com.github.mytv.myearthquakealert.data.repository.SettingsRepository
import com.github.mytv.myearthquakealert.data.repository.SettingsStore
import com.github.mytv.myearthquakealert.data.repository.UpdateRepository
import com.github.mytv.myearthquakealert.diagnostics.ClockSync
import com.github.mytv.myearthquakealert.diagnostics.HealthRecorder
import com.github.mytv.myearthquakealert.map.offline.OfflineMaps
import com.github.mytv.myearthquakealert.service.AppNotifications
import com.github.mytv.myearthquakealert.service.EewHistory
import com.github.mytv.myearthquakealert.service.WatchdogWorker
import com.github.mytv.myearthquakealert.update.UpdateCheckWorker
import com.github.mytv.myearthquakealert.util.LocationProvider
import com.github.mytv.myearthquakealert.util.LogExporter
import com.github.mytv.myearthquakealert.util.isUserUnlocked
import com.github.mytv.myearthquakealert.widget.WidgetUpdater
import com.github.mytv.myearthquakealert.wear.WearBridge
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.osmdroid.config.Configuration
import retrofit2.Retrofit
import java.io.File
import java.util.concurrent.TimeUnit

class MyEarthQuakeAlertApp : Application() {

    private var mapConfigured = false

    /** Wall-clock time this process started (诊断 › 检测进程). */
    val processStartedAt: Long = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        System.currentTimeMillis() - (SystemClock.uptimeMillis() - Process.getStartUptimeMillis())
    } else {
        System.currentTimeMillis()
    }

    /**
     * Process-lifetime scope on the main thread for work that must outlive the screen or
     * service that started it (settings writes, the diagnostics export).
     */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        LogExporter.init(this)
        // Diagnostics history lives in device-protected storage: readable before the first
        // unlock, so the service can record from boot on and the 诊断 page shows the past
        // even when the service is not running.
        HealthRecorder.init(this)
        ClockSync.init(this)
        EewHistory.init(this)
        AppNotifications.ensureChannels(this)
        // Mirrors status, recent events and alerts to a paired Wear OS watch (no-op without Play services).
        WearBridge.start(this)
        // The process can start before the first unlock (boot receiver and monitoring
        // service are directBootAware). Credential-encrypted storage (osmdroid config,
        // WorkManager's database) is off limits until then.
        if (isUserUnlocked()) {
            onUserUnlocked()
        } else {
            ContextCompat.registerReceiver(
                this,
                object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        onUserUnlocked()
                        unregisterReceiver(this)
                    }
                },
                IntentFilter(Intent.ACTION_USER_UNLOCKED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
    }

    private fun onUserUnlocked() {
        ensureMapConfigured()
        WidgetUpdater.start(this)
        OfflineMaps.start(this)
        // The watchdog follows the monitoring switch.
        appScope.launch {
            settingsRepository.settings
                .map { it.serviceEnabled }
                .distinctUntilChanged()
                .collect { enabled ->
                    if (enabled) WatchdogWorker.ensure(this@MyEarthQuakeAlertApp) else WatchdogWorker.cancel(this@MyEarthQuakeAlertApp)
                }
        }
        // The daily update check follows 自动检查更新.
        UpdateCheckWorker.follow(this)
    }

    /** Configures osmdroid once the user is unlocked. Safe to call repeatedly. */
    @Synchronized
    fun ensureMapConfigured() {
        if (mapConfigured || !isUserUnlocked()) return
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(osmdroidBasePath, "tiles")
            load(this@MyEarthQuakeAlertApp, getSharedPreferences("osmdroid", MODE_PRIVATE))
        }
        mapConfigured = true
    }

    private val dataStore: DataStore<Preferences> by lazy { SettingsStore.create(this) }

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val apiClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.wolfx.jp/")
            .client(apiClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    /** Wolfx HTTP API (catalog, GeoIP, ntp.json). */
    val wolfxApi: WolfxApi by lazy { retrofit.create(WolfxApi::class.java) }

    val eewRepository: EewRepository by lazy { EewRepository(wolfxApi) }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(dataStore) }

    val locationProvider: LocationProvider by lazy { LocationProvider(this, wolfxApi, settingsRepository.settings) }

    private val okHttpClient: OkHttpClient by lazy { OkHttpClient() }

    val updateRepository: UpdateRepository by lazy {
        UpdateRepository(okHttpClient)
    }
}
