package com.github.mytv.myearthquakealert.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.github.mytv.myearthquakealert.data.api.WolfxApi
import com.github.mytv.myearthquakealert.data.model.LocationMode
import com.github.mytv.myearthquakealert.data.model.UserLocation
import com.github.mytv.myearthquakealert.data.repository.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Resolves the user's location (design 设置 › 位置 › 位置来源):
 * - MANUAL: the coordinates picked in settings (TVs have no GPS).
 * - AUTO: the newest last-known fix of the device; when it is older than the caller allows,
 *   a single fresh network/GPS fix; then the Wolfx GeoIP lookup; finally the last good
 *   result, mirrored in device-protected storage so it is available before the first unlock.
 */
class LocationProvider(
    private val context: Context,
    private val api: WolfxApi,
    private val settings: Flow<UserSettings>? = null,
) {
    /** Current location honouring the location mode; never throws. */
    suspend fun getLocation(): UserLocation {
        val current = try {
            settings?.first()
        } catch (e: Exception) {
            null
        }
        return resolve(current)
    }

    /**
     * Resolves the location for [settings] (AUTO when null). With [maxFixAgeMs], a device fix
     * older than that triggers one active fix request of at most [ACTIVE_FIX_TIMEOUT_MS].
     */
    suspend fun resolve(settings: UserSettings?, maxFixAgeMs: Long? = null): UserLocation {
        if (settings != null && settings.locationMode == LocationMode.MANUAL &&
            settings.manualLatitude != null && settings.manualLongitude != null
        ) {
            return UserLocation(
                latitude = settings.manualLatitude,
                longitude = settings.manualLongitude,
                source = UserLocation.SOURCE_MANUAL,
                name = settings.manualLocationName,
            ).also(::remember)
        }
        val now = System.currentTimeMillis()
        var device = lastKnownDeviceFix()
        if (maxFixAgeMs != null && (device == null || (device.ageMs(now) ?: Long.MAX_VALUE) > maxFixAgeMs)) {
            freshDeviceFix()?.let { device = it }
        }
        device?.let { return it.also(::remember) }
        ipLocation()?.let { return it.also(::remember) }
        return cached() ?: UserLocation(0.0, 0.0, UserLocation.SOURCE_NONE)
    }

    /** The last location resolved by any caller, from device-protected storage. */
    fun cached(): UserLocation? {
        val p = prefs()
        if (!p.contains(KEY_LAT)) return null
        return UserLocation(
            latitude = Double.fromBits(p.getLong(KEY_LAT, 0L)),
            longitude = Double.fromBits(p.getLong(KEY_LON, 0L)),
            source = p.getString(KEY_SOURCE, UserLocation.SOURCE_NONE) ?: UserLocation.SOURCE_NONE,
            accuracyM = if (p.contains(KEY_ACCURACY)) p.getFloat(KEY_ACCURACY, 0f) else null,
            fixTime = if (p.contains(KEY_TIME)) p.getLong(KEY_TIME, 0L) else null,
            name = p.getString(KEY_NAME, "") ?: "",
        )
    }

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun locationManager(): LocationManager? =
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    @SuppressLint("MissingPermission") // checked by hasPermission()
    private fun lastKnownDeviceFix(): UserLocation? {
        if (!hasPermission()) return null
        val manager = locationManager() ?: return null
        val providers = buildList {
            add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
        }
        val newest = providers.mapNotNull { provider ->
            try {
                manager.getLastKnownLocation(provider)
            } catch (e: SecurityException) {
                null
            } catch (e: IllegalArgumentException) {
                null // provider absent on this device (e.g. no GPS on TVs)
            }
        }.maxByOrNull { it.time } ?: return null
        return newest.toUserLocation()
    }

    @SuppressLint("MissingPermission") // checked by hasPermission()
    private suspend fun freshDeviceFix(): UserLocation? {
        if (!hasPermission()) return null
        val manager = locationManager() ?: return null
        if (!LocationManagerCompat.isLocationEnabled(manager)) return null
        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { p -> try { manager.isProviderEnabled(p) } catch (e: Exception) { false } }
            ?: return null
        return withTimeoutOrNull(ACTIVE_FIX_TIMEOUT_MS) {
            suspendCancellableCoroutine<Location?> { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                try {
                    LocationManagerCompat.getCurrentLocation(
                        manager, provider, signal, ContextCompat.getMainExecutor(context),
                    ) { location -> if (cont.isActive) cont.resume(location) }
                } catch (e: Exception) {
                    // SecurityException when background location is not allowed.
                    Log.w(TAG, "Active location fix unavailable: ${e.message}")
                    if (cont.isActive) cont.resume(null)
                }
            }
        }?.toUserLocation()
    }

    private fun Location.toUserLocation() = UserLocation(
        latitude = latitude,
        longitude = longitude,
        source = if (provider == LocationManager.GPS_PROVIDER) UserLocation.SOURCE_GPS else UserLocation.SOURCE_NETWORK,
        accuracyM = if (hasAccuracy()) accuracy else null,
        fixTime = time,
    )

    private suspend fun ipLocation(): UserLocation? {
        val response = try {
            api.getGeoIp()
        } catch (e: Exception) {
            Log.w(TAG, "IP location failed: ${e.message}")
            return null
        }
        val lat = response.latitude ?: return null
        val lon = response.longitude ?: return null
        if (lat == 0.0 && lon == 0.0) return null
        return UserLocation(lat, lon, UserLocation.SOURCE_IP, fixTime = System.currentTimeMillis())
    }

    private fun remember(location: UserLocation) {
        if (!location.isKnown) return
        val editor = prefs().edit()
            .putLong(KEY_LAT, location.latitude.toRawBits())
            .putLong(KEY_LON, location.longitude.toRawBits())
            .putString(KEY_SOURCE, location.source)
            .putString(KEY_NAME, location.name)
        if (location.accuracyM != null) editor.putFloat(KEY_ACCURACY, location.accuracyM) else editor.remove(KEY_ACCURACY)
        if (location.fixTime != null) editor.putLong(KEY_TIME, location.fixTime) else editor.remove(KEY_TIME)
        editor.apply()
    }

    private fun prefs(): SharedPreferences =
        context.deviceProtectedContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "LocationProvider"
        private const val PREFS = "last_location"
        private const val KEY_LAT = "lat"
        private const val KEY_LON = "lon"
        private const val KEY_SOURCE = "source"
        private const val KEY_ACCURACY = "accuracy"
        private const val KEY_TIME = "time"
        private const val KEY_NAME = "name"
        private const val ACTIVE_FIX_TIMEOUT_MS = 20_000L
    }
}

fun Context.canDrawOverlays(): Boolean {
    return Settings.canDrawOverlays(this)
}

fun Context.openOverlaySettings() {
    val intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:$packageName")
    )
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
}
