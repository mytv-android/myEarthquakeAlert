package com.github.mytv.myearthquakealert.data.repository

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.github.mytv.myearthquakealert.util.deviceProtectedContext
import com.github.mytv.myearthquakealert.util.isUserUnlocked
import java.io.File

/**
 * The settings DataStore lives in device-protected storage so the boot receiver and the
 * monitoring service can read the switches, source and thresholds before the first
 * unlock after a reboot (Direct Boot). Nothing in it is sensitive.
 *
 * Versions up to 1.0 kept the file in credential-encrypted storage; it is moved over the
 * first time the store is opened while the user is unlocked.
 */
object SettingsStore {

    private const val TAG = "SettingsStore"
    private const val FILE = "datastore/settings.preferences_pb"

    fun create(context: Context): DataStore<Preferences> {
        val app = context.applicationContext
        migrateLegacyFile(app)
        return PreferenceDataStoreFactory.create(produceFile = { deviceFile(app) })
    }

    /** Whether the settings can be read now without touching credential-encrypted storage. */
    fun isReadable(context: Context): Boolean =
        context.isUserUnlocked() || deviceFile(context.applicationContext).exists()

    // Built by hand: preferencesDataStoreFile() resolves against applicationContext, which
    // is always credential-encrypted, even when called on a device-protected context.
    private fun deviceFile(app: Context): File = File(app.deviceProtectedContext().filesDir, FILE)

    private fun legacyFile(app: Context): File = File(app.filesDir, FILE)

    private fun migrateLegacyFile(app: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || !app.isUserUnlocked()) return
        val legacy = legacyFile(app)
        if (!legacy.exists()) return
        val target = deviceFile(app)
        try {
            if (!target.exists() || legacy.lastModified() > target.lastModified()) {
                target.parentFile?.mkdirs()
                legacy.copyTo(target, overwrite = true)
            }
            legacy.delete()
            Log.i(TAG, "Moved settings to device-protected storage")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to move settings to device-protected storage", e)
        }
    }
}
