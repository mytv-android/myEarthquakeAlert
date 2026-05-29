package com.github.mytv.myearthquakealert.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.github.mytv.myearthquakealert.MainActivity
import com.github.mytv.myearthquakealert.MyEarthQuakeAlertApp
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.data.repository.SettingsRepository
import com.github.mytv.myearthquakealert.data.source.EewSource
import com.github.mytv.myearthquakealert.domain.AlertEvaluator
import com.github.mytv.myearthquakealert.domain.SeismicCalculator
import com.github.mytv.myearthquakealert.util.LocationProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class EewMonitorService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var monitorJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_START -> startMonitoring()
            ACTION_STOP -> stopMonitoring()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startMonitoring() {
        startForeground(
            NOTIFICATION_ID,
            createNotification(getString(R.string.service_monitoring)),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )

        monitorJob = serviceScope.launch {
            val app = applicationContext as MyEarthQuakeAlertApp
            val repository = app.eewRepository
            val settingsRepo = app.settingsRepository
            val locationProvider = app.locationProvider

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

        serviceScope.launch {
            val app = applicationContext as MyEarthQuakeAlertApp
            val repository = app.eewRepository
            val settingsRepo = app.settingsRepository
            val locationProvider = app.locationProvider

            repository.eewMessages.collect { event ->
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
                    val overlayIntent = Intent(this@EewMonitorService, AlertOverlayService::class.java)
                    overlayIntent.action = AlertOverlayService.ACTION_SHOW
                    startService(overlayIntent)
                }
            }
        }
    }

    private fun stopMonitoring() {
        val app = applicationContext as MyEarthQuakeAlertApp
        app.eewRepository.disconnectWebSocket()
        monitorJob?.cancel()
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
            context.startService(intent)
        }
    }
}