package com.github.mytv.myearthquakealert.wear

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailabilityLight
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Thin, failure-proof wrapper over the Wearable Data Layer for the phone side.
 *
 * Many phones (most in mainland China) have no Google Play services, or have them without
 * the Wear OS companion: then every call here is a cheap no-op. Each call blocks the
 * calling thread for at most [TIMEOUT_S] seconds and must run off the main thread
 * (the bridge uses Dispatchers.IO, listener callbacks run on a binder worker).
 * Nothing here throws.
 */
internal class WearDataLayer(context: Context) {

    private val app = context.applicationContext

    /** Set once the Wearable API reported itself unavailable; the bridge then stays silent. */
    @Volatile
    var unavailable: Boolean = !hasPlayServices(app)
        private set

    /** Watches with the watch app (capability), else any connected node. Empty on failure. */
    fun watchNodes(): List<String> {
        if (unavailable) return emptyList()
        val capable = call("capability") {
            Wearable.getCapabilityClient(app).getCapability(WearPaths.CAPABILITY_WATCH, CapabilityClient.FILTER_REACHABLE)
        }?.nodes?.map { it.id }.orEmpty()
        if (capable.isNotEmpty()) return capable
        return call("nodes") { Wearable.getNodeClient(app).connectedNodes }?.map { it.id }.orEmpty()
    }

    /** Sends [data] to [nodeId]; true when the Data Layer accepted it. */
    fun send(nodeId: String, path: String, data: ByteArray): Boolean {
        if (unavailable) return false
        return call("send $path") { Wearable.getMessageClient(app).sendMessage(nodeId, path, data) } != null
    }

    /** Sends [data] to every watch; returns how many accepted it. */
    fun broadcast(path: String, data: ByteArray): Int = watchNodes().count { send(it, path, data) }

    /** Puts an urgent DataItem at [path] with [payload] under [WearPaths.KEY_PAYLOAD]. */
    fun put(path: String, payload: ByteArray): Boolean {
        if (unavailable) return false
        return call("put $path") {
            val request = PutDataMapRequest.create(path).apply {
                dataMap.putByteArray(WearPaths.KEY_PAYLOAD, payload)
            }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(app).putDataItem(request)
        } != null
    }

    /** Deletes this node's DataItem at [path]. */
    fun delete(path: String): Boolean {
        if (unavailable) return false
        val uri = Uri.Builder().scheme("wear").path(path).build()
        return call("delete $path") { Wearable.getDataClient(app).deleteDataItems(uri) } != null
    }

    private fun <T> call(what: String, block: () -> Task<T>): T? {
        if (unavailable) return null
        return try {
            Tasks.await(block(), TIMEOUT_S, TimeUnit.SECONDS)
        } catch (e: ExecutionException) {
            onFailure(what, e.cause ?: e)
            null
        } catch (e: TimeoutException) {
            Log.w(TAG, "$what timed out")
            null
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            null
        } catch (e: Exception) {
            // SecurityException (signature mismatch), IllegalStateException (main thread), …
            onFailure(what, e)
            null
        }
    }

    private fun onFailure(what: String, e: Throwable) {
        val code = (e as? ApiException)?.statusCode
        if (code == CommonStatusCodes.API_NOT_CONNECTED || code == ConnectionResult.API_UNAVAILABLE ||
            code == ConnectionResult.SERVICE_MISSING || code == ConnectionResult.SERVICE_INVALID
        ) {
            if (!unavailable) Log.i(TAG, "Wearable API unavailable on this phone ($code); watch bridge off")
            unavailable = true
        } else {
            Log.w(TAG, "$what failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "WearDataLayer"
        private const val TIMEOUT_S = 5L

        /** Google Play services installed and usable (the Data Layer lives in it). */
        fun hasPlayServices(context: Context): Boolean = try {
            GoogleApiAvailabilityLight.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
        } catch (e: Exception) {
            false
        }
    }
}
