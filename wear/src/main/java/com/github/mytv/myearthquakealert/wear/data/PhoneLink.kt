package com.github.mytv.myearthquakealert.wear.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.github.mytv.myearthquakealert.wear.alert.WatchAlerts
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Watch → phone over the Wearable Data Layer: reachability of a phone with the app
 * (capability [WearPaths.CAPABILITY_PHONE]), the 暂停 / 恢复 / 开启 commands, the initial
 * sync of the phone's DataItems, and opening a page of the phone app.
 */
object PhoneLink {

    private const val TAG = "PhoneLink"
    private const val REPLY_TIMEOUT_MS = 12_000L
    private const val CALL_TIMEOUT_MS = 8_000L

    /** Reachable phones with the app, nearby ones first; empty when none (or no Play services). */
    suspend fun phoneNodes(context: Context): List<Node> = try {
        withTimeoutOrNull(CALL_TIMEOUT_MS) {
            Wearable.getCapabilityClient(context)
                .getCapability(WearPaths.CAPABILITY_PHONE, CapabilityClient.FILTER_REACHABLE)
                .await()
                .nodes
                .sortedByDescending { it.isNearby }
        } ?: emptyList()
    } catch (e: Exception) {
        Log.w(TAG, "capability query failed", e)
        emptyList()
    }

    /** Re-checks the phone and publishes the answer to [WatchStore.phoneConnected]. */
    suspend fun refreshConnection(context: Context): Boolean {
        val connected = phoneNodes(context).isNotEmpty()
        WatchStore.setPhoneConnected(connected)
        return connected
    }

    /**
     * Sends [path] (one of the `WearPaths.CMD_*`) to the phone and waits for its answer. The
     * outcome is also posted to [WatchStore.commandOutcome] for the confirmation dialog.
     */
    suspend fun send(context: Context, path: String, durationMs: Long = 0L): CommandOutcome? {
        if (WatchStore.pendingCommand.value != null) return null
        WatchStore.setPending(path)
        try {
            val node = phoneNodes(context).firstOrNull()
            if (node == null) {
                WatchStore.setPhoneConnected(false)
                return post(path, false, CommandOutcome.ERR_NO_PHONE)
            }
            val payload = WearCodec.encode(WireCommand(durationMs = durationMs, sentAt = System.currentTimeMillis()))
            val result = coroutineScope {
                val reply = async(start = CoroutineStart.UNDISPATCHED) {
                    withTimeoutOrNull(REPLY_TIMEOUT_MS) { WatchStore.commandResults.first { it.command == path } }
                }
                val sent = try {
                    withTimeoutOrNull(CALL_TIMEOUT_MS) {
                        Wearable.getMessageClient(context).sendMessage(node.id, path, payload).await()
                    } != null
                } catch (e: Exception) {
                    Log.w(TAG, "command $path not sent", e)
                    false
                }
                if (!sent) {
                    reply.cancel()
                    null
                } else {
                    reply.await() ?: WireCommandResult(command = path, ok = false, error = CommandOutcome.ERR_NO_REPLY)
                }
            } ?: return post(path, false, CommandOutcome.ERR_NO_PHONE)
            return post(path, result.ok, result.error)
        } finally {
            WatchStore.setPending(null)
        }
    }

    private fun post(path: String, ok: Boolean, error: String?): CommandOutcome {
        WatchStore.postOutcome(path, ok, error)
        return WatchStore.commandOutcome.value ?: CommandOutcome(0, path, ok, error)
    }

    /**
     * Reads the phone's current DataItems (status, quakes, alert): the listener service only
     * hears changes, so a freshly started app catches up here.
     */
    suspend fun syncDataItems(context: Context) {
        val client = try {
            Wearable.getDataClient(context)
        } catch (e: Exception) {
            return
        }
        val now = System.currentTimeMillis()
        for (path in listOf(WearPaths.STATUS, WearPaths.QUAKES, WearPaths.ALERT)) {
            val uri = Uri.Builder().scheme("wear").authority("*").path(path).build()
            try {
                val buffer = withTimeoutOrNull(CALL_TIMEOUT_MS) { client.getDataItems(uri).await() } ?: continue
                try {
                    for (item in buffer) {
                        val bytes = DataMapItem.fromDataItem(item).dataMap.getByteArray(WearPaths.KEY_PAYLOAD)
                        applyPayload(context, path, bytes, now)
                    }
                } finally {
                    buffer.release()
                }
            } catch (e: Exception) {
                Log.w(TAG, "sync of $path failed", e)
            }
        }
    }

    /** Applies one DataItem / message payload from the phone. */
    fun applyPayload(context: Context, path: String, bytes: ByteArray?, receivedAt: Long) {
        when (path) {
            WearPaths.STATUS -> WearCodec.decodeStatus(bytes)?.let(WatchStore::setStatus)
            WearPaths.QUAKES -> WearCodec.decodeQuakes(bytes)?.let(WatchStore::setPhoneQuakes)
            WearPaths.ALERT -> WearCodec.decodeAlert(bytes)?.let { WatchAlerts.onPhoneAlert(context, it, receivedAt) }
            WearPaths.ALERT_END -> WearCodec.decodeAlertEnd(bytes)?.let { WatchAlerts.onPhoneAlertEnded(context, it.eventId) }
            WearPaths.CMD_RESULT -> WearCodec.decodeCommandResult(bytes)?.let(WatchStore::onCommandResult)
        }
    }

    /**
     * Opens `myearthquakealert://open/<route>` on the phone (the phone app's WearLinkActivity
     * turns it into a destination). Returns false when no phone could be asked.
     */
    suspend fun openOnPhone(context: Context, route: String): Boolean {
        val node = phoneNodes(context).firstOrNull() ?: return false
        val intent = Intent(Intent.ACTION_VIEW)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setData(Uri.parse("$LINK_PREFIX$route"))
        val executor = Executors.newSingleThreadExecutor()
        return try {
            withTimeoutOrNull(CALL_TIMEOUT_MS) {
                RemoteActivityHelper(context, executor).startRemoteActivity(intent, node.id).awaitDone()
                true
            } ?: false
        } catch (e: Exception) {
            Log.w(TAG, "remote activity failed", e)
            false
        } finally {
            executor.shutdown()
        }
    }

    const val LINK_PREFIX = "myearthquakealert://open/"
    const val ROUTE_SETTINGS = "settings"

    private suspend fun <T> ListenableFuture<T>.awaitDone(): T = suspendCancellableCoroutine { cont ->
        addListener({
            try {
                cont.resume(get())
            } catch (e: Exception) {
                cont.resumeWithException(e.cause ?: e)
            }
        }, Runnable::run)
        cont.invokeOnCancellation { cancel(false) }
    }
}
