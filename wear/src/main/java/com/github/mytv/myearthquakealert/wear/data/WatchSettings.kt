package com.github.mytv.myearthquakealert.wear.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.github.mytv.myearthquakealert.wear.source.EewSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.watchSettings: DataStore<Preferences> by preferencesDataStore(name = "watch_settings")

/** The watch's own settings (手表 › 设置). */
object WatchSettings {

    data class Values(
        /** 独立监测: watch a Wolfx feed itself while the phone is out of reach. */
        val standaloneEnabled: Boolean = false,
        val standaloneSource: EewSource = EewSource.CENC,
    )

    private val STANDALONE = booleanPreferencesKey("standalone_enabled")
    private val SOURCE = stringPreferencesKey("standalone_source")

    fun flow(context: Context): Flow<Values> = context.applicationContext.watchSettings.data
        .catch { emit(emptyPreferences()) }
        .map { p ->
            Values(
                standaloneEnabled = p[STANDALONE] ?: false,
                standaloneSource = p[SOURCE]?.let { name -> EewSource.entries.firstOrNull { it.name == name } } ?: EewSource.CENC,
            )
        }

    suspend fun current(context: Context): Values = flow(context).first()

    suspend fun setStandalone(context: Context, enabled: Boolean) {
        context.applicationContext.watchSettings.edit { it[STANDALONE] = enabled }
    }

    suspend fun setSource(context: Context, source: EewSource) {
        context.applicationContext.watchSettings.edit { it[SOURCE] = source.name }
    }
}
