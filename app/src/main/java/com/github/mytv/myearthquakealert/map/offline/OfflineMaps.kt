package com.github.mytv.myearthquakealert.map.offline

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.github.mytv.myearthquakealert.MyEarthQuakeAlertApp
import com.github.mytv.myearthquakealert.ui.map.EewMapStyle
import com.github.mytv.myearthquakealert.util.isUserUnlocked
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import java.util.concurrent.TimeUnit

/** What 设置 › 显示 › 离线地图 shows. */
sealed interface OfflineMapStatus {
    /** The style's tile server forbids bulk downloads (OSM tile usage policy). */
    data object Unsupported : OfflineMapStatus

    /** Downloading: [done] of [total] tiles checked or fetched. */
    data class Running(val done: Int, val total: Int) : OfflineMapStatus

    /** Queued until its constraints hold (network / Wi-Fi, battery, storage). */
    data object Waiting : OfflineMapStatus

    /** Tiles of [area] are in the cache (possibly not all of them, see [CachedArea.complete]). */
    data class Cached(val area: CachedArea, val failedAt: Long) : OfflineMapStatus

    /** Nothing prefetched; [failedAt] > 0 when the last attempt fetched nothing. */
    data class NotCached(val failedAt: Long) : OfflineMapStatus
}

/**
 * Offline map precache (design §7 离线预缓存): keeps the tiles of the current style for z5–z9
 * within 300 km of the user in osmdroid's tile cache, so an alert can draw its map without
 * network. Checked at app start, whenever the monitoring service resolves a location and when
 * the map style changes; the work itself is [OfflineMapWorker].
 */
object OfflineMaps {
    private const val TAG = "OfflineMaps"
    const val WORK_NAME = "offline_map_precache"
    internal const val PROGRESS_DONE = "done"
    internal const val PROGRESS_TOTAL = "total"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var started = false

    /**
     * Tile layers prefetched for [style]: the base tiles plus the label layer for satellite.
     * Empty when a layer's tile source does not accept bulk downloads (OSM).
     */
    fun layersFor(style: EewMapStyle): List<OnlineTileSourceBase> {
        val layers = listOfNotNull(style.tileSource(), style.labelOverlay())
        val online = layers.filterIsInstance<OnlineTileSourceBase>()
        if (online.size != layers.size || online.any { !it.tileSourcePolicy.acceptsBulkDownload() }) return emptyList()
        return online
    }

    fun supportsPrecache(style: EewMapStyle): Boolean = layersFor(style).isNotEmpty()

    /**
     * App start, after the first unlock: checks with the last known location and again on
     * every map style change. Idempotent.
     */
    fun start(app: MyEarthQuakeAlertApp) {
        if (started) return
        started = true
        scope.launch {
            app.settingsRepository.settings
                .map { it.mapStyle }
                .distinctUntilChanged()
                .collect { style ->
                    val location = app.locationProvider.cached()?.takeIf { it.isKnown } ?: return@collect
                    check(app, style, location.latitude, location.longitude)
                }
        }
    }

    /** The monitoring service resolved the user's location. Cheap; returns at once. */
    fun onLocation(context: Context, latitude: Double, longitude: Double) {
        val app = context.applicationContext as? MyEarthQuakeAlertApp ?: return
        if (!app.isUserUnlocked() || (latitude == 0.0 && longitude == 0.0)) return
        scope.launch {
            val style = try {
                app.settingsRepository.settings.first().mapStyle
            } catch (e: Exception) {
                Log.w(TAG, "settings unreadable", e)
                return@launch
            }
            check(app, style, latitude, longitude)
        }
    }

    private suspend fun check(app: MyEarthQuakeAlertApp, style: EewMapStyle, latitude: Double, longitude: Double) {
        try {
            if (!supportsPrecache(style)) return
            val prefs = OfflineMapStore.get(app).read()
            val reason = PrecachePolicy.reason(prefs.area, style.name, latitude, longitude, System.currentTimeMillis()) ?: return
            Log.i(TAG, "precache due: $reason")
            // A style change replaces a run still busy with the old style; anything else keeps it.
            enqueue(app, prefs.wifiOnly, if (reason == PrecacheReason.STYLE) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP)
        } catch (e: Exception) {
            Log.w(TAG, "precache check failed", e)
        }
    }

    /** 立即更新: (re)starts a run for the current style and location. */
    fun updateNow(context: Context) {
        val app = context.applicationContext as MyEarthQuakeAlertApp
        scope.launch {
            try {
                enqueue(app, OfflineMapStore.get(app).read().wifiOnly, ExistingWorkPolicy.REPLACE)
            } catch (e: Exception) {
                Log.w(TAG, "update failed", e)
            }
        }
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    /** 清除缓存: stops a run, deletes every cached tile and forgets the cached area. */
    suspend fun clear(context: Context) {
        val app = context.applicationContext as MyEarthQuakeAlertApp
        cancel(app)
        OfflineMapStore.get(app).clearArea()
        withContext(Dispatchers.IO) {
            app.ensureMapConfigured()
            TileCache().clearAll()
        }
    }

    /** 仅 Wi-Fi: saves the switch and re-queues a waiting run with the new network constraint. */
    suspend fun setWifiOnly(context: Context, on: Boolean) {
        val app = context.applicationContext as MyEarthQuakeAlertApp
        OfflineMapStore.get(app).setWifiOnly(on)
        val waiting = withContext(Dispatchers.IO) {
            WorkManager.getInstance(app).getWorkInfosForUniqueWork(WORK_NAME).get()
                .any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }
        }
        if (waiting) enqueue(app, on, ExistingWorkPolicy.REPLACE)
    }

    /** Bytes of all tiles in the cache (browsing and precache). Blocking. */
    fun storedBytes(context: Context): Long {
        (context.applicationContext as MyEarthQuakeAlertApp).ensureMapConfigured()
        return TileCache().storedBytes()
    }

    /** Live status for the settings rows. */
    fun status(context: Context, style: Flow<EewMapStyle>): Flow<OfflineMapStatus> {
        val app = context.applicationContext
        return combine(
            OfflineMapStore.get(app).prefs,
            WorkManager.getInstance(app).getWorkInfosForUniqueWorkFlow(WORK_NAME),
            style,
        ) { prefs, infos, current ->
            val info = infos.firstOrNull { !it.state.isFinished }
            val area = prefs.area?.takeIf { it.style == current.name }
            when {
                !supportsPrecache(current) -> OfflineMapStatus.Unsupported
                info?.state == WorkInfo.State.RUNNING -> OfflineMapStatus.Running(
                    info.progress.getInt(PROGRESS_DONE, 0),
                    info.progress.getInt(PROGRESS_TOTAL, 0),
                )
                info != null -> OfflineMapStatus.Waiting
                area != null && area.tiles > 0 -> OfflineMapStatus.Cached(area, prefs.failedAt)
                else -> OfflineMapStatus.NotCached(prefs.failedAt)
            }
        }.distinctUntilChanged()
    }

    /** Whether the switch or Data Saver holds the run back on the current (metered) network. */
    fun waitsForUnmetered(context: Context, wifiOnly: Boolean): Boolean =
        (wifiOnly || dataSaverOn(context)) && isMetered(context)

    internal fun enqueue(context: Context, wifiOnly: Boolean, policy: ExistingWorkPolicy) {
        if (!context.isUserUnlocked()) return
        val unmetered = wifiOnly || dataSaverOn(context)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (unmetered) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()
        val request = OneTimeWorkRequestBuilder<OfflineMapWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .addTag(WORK_NAME)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, policy, request)
    }

    /** Data Saver is on and the app is not exempt: no background data on metered networks. */
    internal fun dataSaverOn(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        return cm.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
    }

    internal fun isMetered(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return cm.isActiveNetworkMetered
        return !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }
}
