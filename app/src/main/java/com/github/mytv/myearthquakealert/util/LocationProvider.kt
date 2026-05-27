package com.github.mytv.myearthquakealert.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.net.Uri
import android.provider.Settings
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
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            ?: return null
        return UserLocation(
            latitude = location.latitude,
            longitude = location.longitude,
            source = "gps",
        )
    }

    private suspend fun getIpLocation(): UserLocation {
        val response = try {
            api.getGeoIp()
        } catch (_: Exception) {
            return UserLocation(0.0, 0.0, "none")
        }
        return UserLocation(
            latitude = response.latitude ?: 0.0,
            longitude = response.longitude ?: 0.0,
            source = "ip",
        )
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
