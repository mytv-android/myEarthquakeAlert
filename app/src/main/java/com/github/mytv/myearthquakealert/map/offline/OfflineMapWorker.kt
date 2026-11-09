package com.github.mytv.myearthquakealert.map.offline

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.github.mytv.myearthquakealert.MyEarthQuakeAlertApp
import com.github.mytv.myearthquakealert.util.isUserUnlocked
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Prefetches the current style's tiles (and the satellite label layer) for z5–z9 within
 * 300 km of the last known location into osmdroid's tile cache. Tiles already cached with
 * enough life left are skipped, so a repeated run only fetches what is missing or old.
 * Progress is published as [OfflineMaps.PROGRESS_DONE] / [OfflineMaps.PROGRESS_TOTAL].
 *
 * Runs with network (or Wi-Fi only), battery-not-low and storage-not-low constraints; when
 * Data Saver blocks background data on the current metered network, the run hands over to
 * one that waits for an unmetered network.
 */
class OfflineMapWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private sealed interface Outcome {
        data class Present(val bytes: Long) : Outcome
        data object Failed : Outcome
    }

    override suspend fun doWork(): Result {
        val app = applicationContext as MyEarthQuakeAlertApp
        if (!app.isUserUnlocked()) return Result.retry()
        val store = OfflineMapStore.get(app)
        val prefs = store.read()
        val style = app.settingsRepository.settings.first().mapStyle
        val layers = OfflineMaps.layersFor(style)
        if (layers.isEmpty()) return Result.success()
        if (OfflineMaps.dataSaverOn(app) && OfflineMaps.isMetered(app)) {
            Log.i(TAG, "Data Saver on a metered network: waiting for an unmetered one")
            OfflineMaps.enqueue(app, wifiOnly = true, policy = ExistingWorkPolicy.APPEND_OR_REPLACE)
            return Result.success()
        }
        val location = app.locationProvider.cached()?.takeIf { it.isKnown }
        if (location == null) {
            Log.i(TAG, "no known location yet")
            return Result.success()
        }
        app.ensureMapConfigured()

        val now = System.currentTimeMillis()
        val tiles = TileMath.tilesWithin(location.latitude, location.longitude, PrecachePolicy.RADIUS_KM, PrecachePolicy.ZOOMS)
        val jobs = layers.flatMap { source -> tiles.map { source to it } }
        val total = jobs.size
        val cache = TileCache()
        var done = 0
        var present = 0
        var bytes = 0L
        var failed = 0
        var stoppedEarly = true
        setProgress(workDataOf(OfflineMaps.PROGRESS_DONE to 0, OfflineMaps.PROGRESS_TOTAL to total))
        try {
            for (chunk in jobs.chunked(PARALLEL)) {
                if (isStopped) break
                val outcomes = coroutineScope {
                    chunk.map { (source, tile) -> async(Dispatchers.IO) { fetch(cache, source, tile, now) } }.awaitAll()
                }
                for (o in outcomes) {
                    when (o) {
                        is Outcome.Present -> {
                            present++
                            bytes += o.bytes
                        }
                        Outcome.Failed -> failed++
                    }
                }
                done += chunk.size
                setProgress(workDataOf(OfflineMaps.PROGRESS_DONE to done, OfflineMaps.PROGRESS_TOTAL to total))
            }
            stoppedEarly = done < total
        } finally {
            // Also when stopped (constraints lost, 取消): the status shows what is cached.
            // An area with missing tiles is retried after PrecachePolicy.RETRY_INCOMPLETE_MS.
            withContext(NonCancellable) {
                if (done > 0) {
                    val end = System.currentTimeMillis()
                    store.saveArea(CachedArea(style.name, location.latitude, location.longitude, present, total, bytes, end), prefs.generation)
                    if (present == 0 && failed > 0) store.markFailed(end, prefs.generation)
                }
            }
        }
        Log.i(TAG, "precache ${style.name}: $present/$total tiles, $bytes bytes, $failed failed, stopped=$stoppedEarly")
        return when {
            stoppedEarly -> Result.retry()
            present == 0 && failed > 0 && runAttemptCount < MAX_ATTEMPTS -> Result.retry()
            else -> Result.success()
        }
    }

    private fun fetch(cache: TileCache, source: OnlineTileSourceBase, tile: TileCoord, now: Long): Outcome {
        val index = MapTileIndex.getTileIndex(tile.zoom, tile.x, tile.y)
        val existing = cache.entry(source, index)
        if (existing != null && PrecachePolicy.isFresh(existing.expiresAt, now)) return Outcome.Present(existing.bytes)
        val url = source.getTileURLString(index) ?: return Outcome.Failed
        return try {
            val config = Configuration.getInstance()
            val request = Request.Builder().url(url).apply {
                // As osmdroid's own TileDownloader does for this source.
                val agent = if (source.tileSourcePolicy.normalizesUserAgent()) config.normalizedUserAgent else config.userAgentValue
                if (!agent.isNullOrBlank()) header(config.userAgentHttpHeader ?: "User-Agent", agent)
                config.additionalHttpRequestProperties?.forEach { (k, v) -> header(k, v) }
            }.build()
            client.newCall(request).execute().use { response ->
                val body = if (response.isSuccessful) response.body?.bytes() else null
                if (body == null || body.isEmpty() || !isImage(body)) {
                    Log.d(TAG, "tile $tile of ${source.name()}: HTTP ${response.code}")
                    // An old copy is still better than nothing offline.
                    return if (existing != null) Outcome.Present(existing.bytes) else Outcome.Failed
                }
                if (cache.saveFile(source, index, ByteArrayInputStream(body), PrecachePolicy.tileExpiry(now))) {
                    Outcome.Present(body.size.toLong())
                } else {
                    Outcome.Failed
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.d(TAG, "tile $tile of ${source.name()}: ${e.message}")
            if (existing != null) Outcome.Present(existing.bytes) else Outcome.Failed
        } catch (e: RuntimeException) {
            // A bad URL or a cache write error costs this tile, not the run.
            Log.w(TAG, "tile $tile of ${source.name()} failed", e)
            if (existing != null) Outcome.Present(existing.bytes) else Outcome.Failed
        }
    }

    private fun isImage(bytes: ByteArray): Boolean {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        return options.outWidth > 0 && options.outHeight > 0
    }

    private companion object {
        const val TAG = "OfflineMapWorker"
        const val PARALLEL = 4
        const val MAX_ATTEMPTS = 3

        val client: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .callTimeout(30, TimeUnit.SECONDS)
                .build()
        }
    }
}
