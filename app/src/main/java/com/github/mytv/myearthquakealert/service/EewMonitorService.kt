package com.github.mytv.myearthquakealert.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.github.mytv.myearthquakealert.MainActivity
import com.github.mytv.myearthquakealert.MyEarthQuakeAlertApp
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.data.source.EewSource
import com.github.mytv.myearthquakealert.data.websocket.EewWebSocketClient
import com.github.mytv.myearthquakealert.domain.AlertEvaluator
import com.github.mytv.myearthquakealert.domain.SeismicCalculator
import com.github.mytv.myearthquakealert.util.LocationProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class EewMonitorService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var monitorJob: Job? = null
    private var alertJob: Job? = null
    private var reconnectJob: Job? = null
    private var isMonitoring = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        try {
            when (action) {
                ACTION_START -> startMonitoring()
                ACTION_STOP -> stopMonitoring()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onStartCommand action=$action", e)
            if (action == ACTION_START) {
                // Ensure foreground notification even on error to avoid ANR
                try {
                    startForeground(
                        NOTIFICATION_ID,
                        createNotification(getString(R.string.service_monitoring)),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                    )
                } catch (_: Exception) {}
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isMonitoring = false
        monitorJob?.cancel()
        alertJob?.cancel()
        reconnectJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startMonitoring() {
        if (isMonitoring) return
        isMonitoring = true

        startForeground(
            NOTIFICATION_ID,
            createNotification(getString(R.string.service_monitoring)),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )

        val app = applicationContext as MyEarthQuakeAlertApp
        val repository = app.eewRepository
        val settingsRepo = app.settingsRepository

        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            var currentSource: EewSource? = null

            settingsRepo.settings.collect { settings ->
                val newSource = settings.selectedSource
                if (newSource != currentSource) {
                    repository.disconnectWebSocket()
                    repository.connectWebSocket(newSource)
                    currentSource = newSource
                }
            }
        }

        reconnectJob?.cancel()
        reconnectJob = serviceScope.launch {
            var reconnectAttempts = 0
            repository.connectionState.collect { state ->
                if (state == EewWebSocketClient.ConnectionState.DISCONNECTED) {
                    val settings = settingsRepo.settings.first()
                    if (!settings.serviceEnabled) return@collect

                    reconnectAttempts++
                    val delaySeconds = minOf(30L, 2L shl (reconnectAttempts - 1))
                    Log.w(TAG, "WebSocket disconnected, reconnecting in ${delaySeconds}s (attempt $reconnectAttempts)")
                    delay(delaySeconds * 1000)
                    if (isMonitoring) {
                        repository.connectWebSocket(settings.selectedSource)
                    }
                } else if (state == EewWebSocketClient.ConnectionState.CONNECTED) {
                    reconnectAttempts = 0
                }
            }
        }

        alertJob?.cancel()
        alertJob = serviceScope.launch {
            val locationProvider = app.locationProvider

            repository.eewMessages.collect { event ->
                try {
                    val settings = settingsRepo.settings.first()
                    val location = locationProvider.getLocation()

                    val distance = SeismicCalculator.haversineDistance(
                        location.latitude, location.longitude,
                        event.latitude, event.longitude
                    )
                    val depth = event.depth ?: 10.0
                    val arrival = SeismicCalculator.calcWaveArrival(depth, distance)
                    val localCsis = SeismicCalculator.calcLocalIntensity(
                        event.magnitude, depth, distance
                    )

                    val shouldAlert = AlertEvaluator.shouldAlert(
                        localCsis = localCsis,
                        magnitude = event.magnitude,
                        minIntensity = settings.actionMinIntensity,
                        minMagnitude = settings.actionMinMagnitude,
                    )

                    if (shouldAlert) {
                        ActiveAlertHolder.showAlert(
                            AlertData(
                                event = event,
                                userLatitude = location.latitude,
                                userLongitude = location.longitude,
                                pWaveSeconds = arrival.pWaveSeconds,
                                sWaveSeconds = arrival.sWaveSeconds,
                                localCsis = localCsis,
                            )
                        )
                        AlertOverlayService.show(this@EewMonitorService)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing EEW message", e)
                }
            }
        }
    }

    private fun stopMonitoring() {
        isMonitoring = false
        val app = applicationContext as MyEarthQuakeAlertApp
        app.eewRepository.disconnectWebSocket()
        serviceScope.launch { app.settingsRepository.updateServiceEnabled(false) }
        monitorJob?.cancel()
        alertJob?.cancel()
        reconnectJob?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .build()
    }

    companion object {
        private const val TAG = "EewMonitorService"
        const val CHANNEL_ID = "eew_monitor"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "com.github.mytv.myearthquakealert.START_MONITOR"
        const val ACTION_STOP = "com.github.mytv.myearthquakealert.STOP_MONITOR"

        fun start(context: Context) {
            val intent = Intent(context, EewMonitorService::class.java)
            intent.action = ACTION_START
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, EewMonitorService::class.java)
            intent.action = ACTION_STOP
            // Use startForegroundService to satisfy Android 12+ requirements
            // since the service is already in foreground state
            context.startForegroundService(intent)
        }
    }
}
