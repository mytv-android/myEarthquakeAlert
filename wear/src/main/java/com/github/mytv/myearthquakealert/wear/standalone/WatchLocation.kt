package com.github.mytv.myearthquakealert.wear.standalone

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.github.mytv.myearthquakealert.wear.data.WatchStore
import com.github.mytv.myearthquakealert.wear.domain.StandaloneEvaluator
import java.util.concurrent.Executors

/**
 * Where the user is, for standalone monitoring: the watch's own last known fix (refreshed
 * with a single current-location request now and then), else the rounded location the phone
 * last synced. Null when neither is known: reports are then listed without a countdown.
 */
object WatchLocation {

    private const val TAG = "WatchLocation"
    private const val REFRESH_MS = 15 * 60_000L

    @Volatile private var fresh: Location? = null
    @Volatile private var requestedAt = 0L
    private val executor by lazy { Executors.newSingleThreadExecutor() }

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun current(context: Context): StandaloneEvaluator.Location? {
        watchFix(context)?.let { return StandaloneEvaluator.Location(it.latitude, it.longitude) }
        val s = WatchStore.status.value
        val lat = s?.userLatitude
        val lon = s?.userLongitude
        return if (lat != null && lon != null) StandaloneEvaluator.Location(lat, lon) else null
    }

    @SuppressLint("MissingPermission")
    private fun watchFix(context: Context): Location? {
        if (!hasPermission(context)) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= 31) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
        }
        val known = providers.mapNotNull { p ->
            try {
                if (lm.allProviders.contains(p)) lm.getLastKnownLocation(p) else null
            } catch (e: Exception) {
                null
            }
        }
        val best = (known + listOfNotNull(fresh)).maxByOrNull { it.time }
        refresh(context, lm)
        return best
    }

    /** One current-location request at most every [REFRESH_MS]; the answer lands in [fresh]. */
    @SuppressLint("MissingPermission")
    private fun refresh(context: Context, lm: LocationManager) {
        val now = System.currentTimeMillis()
        if (now - requestedAt < REFRESH_MS) return
        requestedAt = now
        val provider = when {
            Build.VERSION.SDK_INT >= 31 && lm.allProviders.contains(LocationManager.FUSED_PROVIDER) -> LocationManager.FUSED_PROVIDER
            lm.allProviders.contains(LocationManager.NETWORK_PROVIDER) && lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            lm.allProviders.contains(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            else -> return
        }
        try {
            lm.getCurrentLocation(provider, null, executor) { location -> if (location != null) fresh = location }
        } catch (e: Exception) {
            Log.w(TAG, "location request failed", e)
        }
    }
}
