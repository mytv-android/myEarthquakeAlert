package com.github.mytv.myearthquakealert

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.github.mytv.myearthquakealert.data.api.WolfxApi
import com.github.mytv.myearthquakealert.data.repository.EewRepository
import com.github.mytv.myearthquakealert.data.repository.SettingsRepository
import com.github.mytv.myearthquakealert.data.websocket.EewWebSocketClient
import com.github.mytv.myearthquakealert.util.LocationProvider
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit

class MyEarthQuakeAlertApp : Application() {

    private val dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

    private val json = Json { ignoreUnknownKeys = true }

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
}
