package com.github.mytv.myearthquakealert.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import com.github.mytv.myearthquakealert.data.api.WolfxApi
import com.github.mytv.myearthquakealert.data.model.UserLocation

class LocationProvider(
    private val context: Context,
    private val api: WolfxApi,
) {
    suspend fun getLocation(): UserLocation {
        return getGpsLocation() ?: getIpLocation()
    }

    @SuppressLint("MissingPermission")
    private fun getGpsLocation(): UserLocation? {
        return try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: return null
            UserLocation(
                latitude = location.latitude,
                longitude = location.longitude,
                source = "gps",
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "Location permission not granted", e)
            null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get GPS location", e)
            null
        }
    }

    private suspend fun getIpLocation(): UserLocation {
        val response = try {
            api.getGeoIp()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get IP location", e)
            return UserLocation(0.0, 0.0, "none")
        }
        return UserLocation(
            latitude = response.latitude ?: 0.0,
            longitude = response.longitude ?: 0.0,
            source = "ip",
        )
    }

    companion object {
        private const val TAG = "LocationProvider"
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
