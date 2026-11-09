package com.github.mytv.myearthquakealert.wear.service

import com.github.mytv.myearthquakealert.wear.data.PhoneLink
import com.github.mytv.myearthquakealert.wear.data.WatchStore
import com.github.mytv.myearthquakealert.wear.data.WearPaths
import com.github.mytv.myearthquakealert.wear.alert.WatchAlerts
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

/**
 * Receives what the phone's WearBridge publishes: status / quakes / alert DataItems, the
 * low-latency alert and alert-ended messages, command results, and reachability changes of
 * the phone. Everything lands in [WatchStore], which drives the app, the tile, the
 * complication and the alert notification.
 */
class WatchListenerService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        val now = System.currentTimeMillis()
        // The buffer is only valid during this call: read every item before returning.
        for (event in dataEvents) {
            val item = event.dataItem
            val path = item.uri.path ?: continue
            when (event.type) {
                DataEvent.TYPE_CHANGED -> {
                    val bytes = try {
                        DataMapItem.fromDataItem(item).dataMap.getByteArray(WearPaths.KEY_PAYLOAD)
                    } catch (e: Exception) {
                        null
                    }
                    PhoneLink.applyPayload(this, path, bytes, now)
                }
                DataEvent.TYPE_DELETED -> if (path == WearPaths.ALERT) {
                    // The phone's alert ended (the message may have been missed while away).
                    WatchStore.alert.value?.takeIf { it.viaPhone }?.let { WatchAlerts.onPhoneAlertEnded(this, it.eventId) }
                }
            }
        }
        WatchStore.setPhoneConnected(true)
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        val path = messageEvent.path
        if (!path.startsWith("/eew")) return
        PhoneLink.applyPayload(this, path, messageEvent.data, System.currentTimeMillis())
        WatchStore.setPhoneConnected(true)
    }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        if (capabilityInfo.name != WearPaths.CAPABILITY_PHONE) return
        WatchStore.setPhoneConnected(capabilityInfo.nodes.isNotEmpty())
    }
}
