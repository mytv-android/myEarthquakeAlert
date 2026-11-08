package com.github.mytv.myearthquakealert.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.github.mytv.myearthquakealert.MainActivity
import com.github.mytv.myearthquakealert.MyEarthQuakeAlertApp
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.data.model.EewEvent
import com.github.mytv.myearthquakealert.data.model.UserLocation
import com.github.mytv.myearthquakealert.data.repository.SettingsStore
import com.github.mytv.myearthquakealert.data.source.EewSource
import com.github.mytv.myearthquakealert.data.websocket.EewWebSocketClient
import com.github.mytv.myearthquakealert.domain.AlertEvaluator
import com.github.mytv.myearthquakealert.domain.DrillScenario
import com.github.mytv.myearthquakealert.domain.SeismicCalculator
import com.github.mytv.myearthquakealert.util.isUserUnlocked
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/**
 * All earthquake detection runs here, never in an Activity: connecting to the data
 * source, reconnecting, evaluating each report against the user's location and
 * thresholds, and presenting the alert. The UI only reads state and sends commands
 * (start, stop, drill).
 *
 * The service is directBootAware: after a reboot the boot receiver can start it before
 * the first unlock. Settings live in device-protected storage, and alerts raised in that
 * window are posted as a notification because the overlay cannot be drawn yet.
 */
class EewMonitorService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var monitorJob: Job? = null
    private var alertJob: Job? = null
    private var reconnectJob: Job? = null

    @Volatile
    private var isMonitoring = false

    private val app: MyEarthQuakeAlertApp get() = applicationContext as MyEarthQuakeAlertApp

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
                ACTION_DRILL -> runDrill(startId)
                // START_STICKY restart after the process was killed: the intent is not redelivered.
                null -> resumeAfterRestart(startId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onStartCommand action=$action", e)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        if (isMonitoring) app.eewRepository.disconnectWebSocket()
        isMonitoring = false
        monitorJob?.cancel()
        alertJob?.cancel()
        reconnectJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun resumeAfterRestart(startId: Int) {
        if (!SettingsStore.isReadable(this)) {
            stopSelf(startId)
            return
        }
        serviceScope.launch {
            val enabled = try {
                app.settingsRepository.settings.first().serviceEnabled
            } catch (e: Exception) {
                Log.e(TAG, "Cannot read settings after restart", e)
                false
            }
            withContext(Dispatchers.Main) {
                when {
                    enabled -> startMonitoring()
                    !isMonitoring -> stopSelf(startId)
                }
            }
        }
    }

    private fun startMonitoring() {
        if (isMonitoring) return
        if (!enterForeground()) {
            stopSelf()
            return
        }
        isMonitoring = true
        AppNotifications.cancelBootNotice(this)

        val repository = app.eewRepository
        val settingsRepo = app.settingsRepository

        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            var currentSource: EewSource? = null

            settingsRepo.settings.collect { settings ->
                val newSource = settings.primarySource
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
                        repository.connectWebSocket(settings.primarySource)
                    }
                } else if (state == EewWebSocketClient.ConnectionState.CONNECTED) {
                    reconnectAttempts = 0
                }
            }
        }

        alertJob?.cancel()
        alertJob = serviceScope.launch {
            repository.eewMessages.collect { event ->
                try {
                    // Guard against malformed reports with a null-island epicenter.
                    if (event.latitude == 0.0 && event.longitude == 0.0) {
                        Log.w(TAG, "Skipping EEW report with empty epicenter: ${event.eventId}")
                        return@collect
                    }

                    val settings = settingsRepo.settings.first()
                    val alert = buildAlert(event, app.locationProvider.getLocation(), isDrill = false)
                    val shouldAlert = AlertEvaluator.shouldAlert(
                        localCsis = alert.localCsis,
                        magnitude = event.magnitude,
                        minIntensity = settings.actionMinIntensity,
                        minMagnitude = settings.actionMinMagnitude,
                    )
                    if (shouldAlert) present(alert)
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing EEW message", e)
                }
            }
        }
    }

    private fun stopMonitoring() {
        isMonitoring = false
        app.eewRepository.disconnectWebSocket()
        serviceScope.launch { app.settingsRepository.updateServiceEnabled(false) }
        monitorJob?.cancel()
        alertJob?.cancel()
        reconnectJob?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * Runs the alert drill through the same evaluate → present path as a real report.
     * Thresholds are skipped so the drill always shows. When monitoring is off the
     * service was started only for the drill and stops itself afterwards.
     */
    private fun runDrill(startId: Int) {
        serviceScope.launch {
            try {
                val settings = app.settingsRepository.settings.first()
                val location = app.locationProvider.getLocation()
                val event = DrillScenario.create(
                    userLatitude = location.latitude,
                    userLongitude = location.longitude,
                    source = settings.primarySource.name,
                    hypocenter = getString(R.string.drill_hypocenter),
                )
                present(buildAlert(event, location, isDrill = true))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Drill failed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, R.string.drill_failed, Toast.LENGTH_LONG).show()
                }
            } finally {
                if (!isMonitoring) stopSelf(startId)
            }
        }
    }

    private fun buildAlert(event: EewEvent, location: UserLocation, isDrill: Boolean): AlertData {
        val distance = SeismicCalculator.haversineDistance(
            location.latitude, location.longitude,
            event.latitude, event.longitude,
        )
        val depth = event.depth ?: 10.0
        val arrival = SeismicCalculator.calcWaveArrival(depth, distance)
        return AlertData(
            event = event,
            userLatitude = location.latitude,
            userLongitude = location.longitude,
            pWaveSeconds = arrival.pWaveSeconds,
            sWaveSeconds = arrival.sWaveSeconds,
            localCsis = SeismicCalculator.calcLocalIntensity(event.magnitude, depth, distance),
            distanceKm = distance,
            isSimulation = isDrill,
        )
    }

    private fun present(alert: AlertData) {
        if (isUserUnlocked()) {
            ActiveAlertHolder.showAlert(alert)
            AlertOverlayService.show(this)
        } else {
            // Before the first unlock the overlay window and its map are unavailable.
            AppNotifications.showLockedAlert(this, alert)
        }
    }

    private fun enterForeground(): Boolean = try {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            createNotification(getString(R.string.service_monitoring)),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            },
        )
        true
    } catch (e: Exception) {
        // e.g. ForegroundServiceStartNotAllowedException on a background restart.
        Log.e(TAG, "Cannot enter foreground", e)
        false
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
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
        const val ACTION_DRILL = "com.github.mytv.myearthquakealert.RUN_DRILL"
        const val ACTION_PAUSE = "com.github.mytv.myearthquakealert.PAUSE_MONITOR"
        const val ACTION_RESUME = "com.github.mytv.myearthquakealert.RESUME_MONITOR"
        const val ACTION_SELF_TEST = "com.github.mytv.myearthquakealert.SELF_TEST"
        /** Long, epoch millis: end of the pause for [ACTION_PAUSE]. */
        const val EXTRA_PAUSE_UNTIL = "pause_until"
        /** String, [com.github.mytv.myearthquakealert.domain.DrillScenario] id for [ACTION_DRILL]. */
        const val EXTRA_SCENARIO = "scenario"
        /** String, [StartOrigin] name for [ACTION_START]. */
        const val EXTRA_ORIGIN = "origin"

        fun startIntent(context: Context): Intent =
            Intent(context, EewMonitorService::class.java).setAction(ACTION_START)

        /** Starts monitoring. Idempotent: a running service ignores repeated starts. */
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, startIntent(context))
        }

        /**
         * Stops monitoring. Uses stopService rather than a STOP intent, so a service that
         * is not running is never started just to stop (which would owe a foreground start).
         */
        fun stop(context: Context) {
            context.stopService(Intent(context, EewMonitorService::class.java))
        }

        /** Runs the alert drill in the service. Call while the app is in the foreground. */
        fun drill(context: Context): Boolean = try {
            context.startService(Intent(context, EewMonitorService::class.java).setAction(ACTION_DRILL))
            true
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Cannot start drill from the background", e)
            false
        }
    }
}
