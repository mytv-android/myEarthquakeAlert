package com.github.mytv.myearthquakealert

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.github.mytv.myearthquakealert.data.api.GitHubApi
import com.github.mytv.myearthquakealert.data.api.WolfxApi
import com.github.mytv.myearthquakealert.data.repository.EewRepository
import com.github.mytv.myearthquakealert.data.repository.SettingsRepository
import com.github.mytv.myearthquakealert.data.repository.UpdateRepository
import com.github.mytv.myearthquakealert.data.websocket.EewWebSocketClient
import com.github.mytv.myearthquakealert.util.LocationProvider
import com.github.mytv.myearthquakealert.util.LogExporter
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.osmdroid.config.Configuration
import retrofit2.Retrofit
import java.io.File

class MyEarthQuakeAlertApp : Application() {

    override fun onCreate() {
        super.onCreate()
        LogExporter.init(this)
        initOsmdroid()
    }

    private fun initOsmdroid() {
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(osmdroidBasePath, "tiles")
            load(this@MyEarthQuakeAlertApp, getSharedPreferences("osmdroid", MODE_PRIVATE))
        }
    }

    private val dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

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

    private val githubRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(GitHubApi.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    private val githubApi: GitHubApi by lazy { githubRetrofit.create(GitHubApi::class.java) }

    val updateRepository: UpdateRepository by lazy {
        UpdateRepository(githubApi, okHttpClient, this)
    }
}
