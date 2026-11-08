package com.github.mytv.myearthquakealert

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.github.mytv.myearthquakealert.data.api.WolfxApi
import com.github.mytv.myearthquakealert.data.repository.EewRepository
import com.github.mytv.myearthquakealert.data.repository.SettingsRepository
import com.github.mytv.myearthquakealert.data.repository.SettingsStore
import com.github.mytv.myearthquakealert.data.repository.UpdateRepository
import com.github.mytv.myearthquakealert.data.websocket.EewWebSocketClient
import com.github.mytv.myearthquakealert.util.LocationProvider
import com.github.mytv.myearthquakealert.util.LogExporter
import com.github.mytv.myearthquakealert.util.isUserUnlocked
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.osmdroid.config.Configuration
import retrofit2.Retrofit
import java.io.File

class MyEarthQuakeAlertApp : Application() {

    private var mapConfigured = false

    override fun onCreate() {
        super.onCreate()
        LogExporter.init(this)
        // The process can start before the first unlock (boot receiver and monitoring
        // service are directBootAware). Credential-encrypted storage is off limits until
        // then, so the map setup waits for the unlock broadcast.
        if (isUserUnlocked()) {
            ensureMapConfigured()
        } else {
            ContextCompat.registerReceiver(
                this,
                object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        ensureMapConfigured()
                        unregisterReceiver(this)
                    }
                },
                IntentFilter(Intent.ACTION_USER_UNLOCKED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
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

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.wolfx.jp/")
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    private val api: WolfxApi by lazy { retrofit.create(WolfxApi::class.java) }

    private val webSocketClient: EewWebSocketClient by lazy { EewWebSocketClient() }

    val eewRepository: EewRepository by lazy { EewRepository(api, webSocketClient) }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(dataStore) }

    val locationProvider: LocationProvider by lazy { LocationProvider(this, api) }

    private val okHttpClient: OkHttpClient by lazy { OkHttpClient() }

    val updateRepository: UpdateRepository by lazy {
        UpdateRepository(this, okHttpClient)
    }
}
