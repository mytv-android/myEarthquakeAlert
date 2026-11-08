package com.github.mytv.myearthquakealert.service

import android.app.AlarmManager
import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.github.mytv.myearthquakealert.MyEarthQuakeAlertApp
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.data.repository.SettingsStore
import com.github.mytv.myearthquakealert.diagnostics.Diagnostics
import com.github.mytv.myearthquakealert.diagnostics.HealthLevel
import com.github.mytv.myearthquakealert.diagnostics.LogTags
import com.github.mytv.myearthquakealert.domain.DrillScenario
import com.github.mytv.myearthquakealert.util.isUserUnlocked
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * All earthquake detection runs here, never in an Activity (design §3): the
 * [MonitorController] connects every subscribed source, keeps the links alive, de-duplicates
 * and evaluates reports and presents alerts; the UI only reads [MonitorStatus] /
 * [com.github.mytv.myearthquakealert.diagnostics.Diagnostics] and sends the commands of the
 * companion object.
 *
 * Foreground: while monitoring is on — also while PAUSED, so the resume alarm can bring the
 * links back without a background foreground-service start (not allowed from an inexact
 * alarm on Android 12+) — the service holds the ongoing notification. With monitoring off
 * it only runs for a drill (foreground with the alert notification), a self test or a
 * command, and stops itself when idle.
 *
 * The service is directBootAware: after a reboot the boot receiver starts it before the
 * first unlock; settings live in device-protected storage, and alerts in that window are
 * presented as notifications because no window can be drawn yet.
 */
class EewMonitorService : Service(), MonitorController.Host {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var controller: MonitorController
    private lateinit var monitorNotification: MonitorNotification

    /** 0 = not foreground; else the id of the notification the foreground state is tied to. */
    private var foregroundId = 0
    /** stopSelf was requested by the service itself (not an external stopService). */
    private var stopping = false
    private var destroyed = false
    /** Commands still reading settings; the service must not stop meanwhile. */
    private var pendingCommands = 0
    private var lastStartId = 0

    private val app: MyEarthQuakeAlertApp get() = applicationContext as MyEarthQuakeAlertApp

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        AppNotifications.ensureChannels(this)
        monitorNotification = MonitorNotification(this)
        controller = MonitorController(this, scope, this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        stopping = false
        val action = intent?.action
        try {
            when (action) {
                ACTION_START -> handleStart(originOf(intent, StartOrigin.USER))
                ACTION_STOP -> handleStop()
                ACTION_PAUSE -> handlePause(intent)
                ACTION_RESUME -> handleResume(originOf(intent, StartOrigin.USER))
                ACTION_SELF_TEST -> controller.selfTest()
                ACTION_DRILL -> controller.drill(DrillScenario.Scenario.fromId(intent.getStringExtra(EXTRA_SCENARIO)))
                ACTION_ALERT_MUTE -> controller.muteAlert()
                ACTION_ALERT_DISMISS -> controller.dismissAlert()
                ACTION_RECONNECT -> controller.reconnect(intent.getStringExtra(EXTRA_REASON) ?: getString(R.string.svc_reason_watchdog))
                // START_STICKY restart after the process was killed: the intent is not redelivered.
                null -> resumeAfterRestart()
                else -> Log.w(TAG, "Unknown action $action")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onStartCommand action=$action", e)
            Diagnostics.log(HealthLevel.ERROR, LogTags.ERROR, "$action: ${e.message}")
        }
        // A command that did not keep the service busy (e.g. mute with no alert) ends it.
        onIdleCheck()
        return START_STICKY
    }

    override fun onDestroy() {
        destroyed = true
        instance = null
        if (controller.running && !stopping) {
            // stopService() from a UI (EewMonitorService.stop): that is the user switching it off.
            app.appScope.launch {
                app.settingsRepository.updateServiceEnabled(false)
                app.settingsRepository.updatePauseUntil(0L)
            }
            cancelPauseAlarm(this)
            WatchdogWorker.cancel(this)
        }
        controller.release(getString(R.string.svc_stop_user))
        monitorNotification.cancelPending()
        scope.cancel()
        super.onDestroy()
    }

    // ── Commands ────────────────────────────────────────────────────────────

    /** Runs [block] (reads settings) while keeping the service from stopping as idle. */
    private fun launchCommand(block: suspend () -> Unit) {
        pendingCommands++
        scope.launch {
            try {
                block()
            } catch (e: Exception) {
                Log.e(TAG, "command failed", e)
            } finally {
                pendingCommands--
                onIdleCheck()
            }
        }
    }

    private fun handleStart(origin: StartOrigin, pauseUntil: Long? = null) {
        if (!enterForeground(monitorNotification.build(MonitorStatus.state.value.copy(phase = MonitorPhase.CONNECTING)))) {
            onForegroundDenied(origin)
            return
        }
        AppNotifications.cancelBootNotice(this)
        BootStartRecord.recordServiceStart(this)
        if (origin == StartOrigin.BOOT) BootStartRecord.recordAutoStart(this)
        // The start command means "monitoring on" (the switch may write it a moment later).
        app.appScope.launch { app.settingsRepository.updateServiceEnabled(true) }
        if (isUserUnlocked()) WatchdogWorker.ensure(this)
        launchCommand { controller.start(origin, userInitiated = origin in USER_ORIGINS, pauseUntil = pauseUntil) }
    }

    private fun handleStop() {
        stopping = true
        app.appScope.launch {
            app.settingsRepository.updateServiceEnabled(false)
            app.settingsRepository.updatePauseUntil(0L)
        }
        cancelPauseAlarm(this)
        WatchdogWorker.cancel(this)
        controller.stop(getString(R.string.svc_stop_user))
        leaveForeground()
        stopSelf()
    }

    private fun handlePause(intent: Intent) {
        val now = System.currentTimeMillis()
        val until = when {
            intent.hasExtra(EXTRA_PAUSE_UNTIL) -> intent.getLongExtra(EXTRA_PAUSE_UNTIL, 0L)
            intent.hasExtra(EXTRA_PAUSE_DURATION) -> now + intent.getLongExtra(EXTRA_PAUSE_DURATION, 0L)
            else -> 0L
        }
        if (controller.running) {
            controller.pause(until)
            return
        }
        // Not running (e.g. killed): a pause only makes sense for enabled monitoring.
        launchCommand {
            val settings = app.settingsRepository.settings.first()
            if (settings.serviceEnabled && until > System.currentTimeMillis()) handleStart(StartOrigin.USER, pauseUntil = until)
        }
    }

    private fun handleResume(origin: StartOrigin) {
        if (controller.running) {
            // Re-post the foreground notification: the alarm starts us as a foreground service.
            if (!enterForeground(monitorNotification.build(MonitorStatus.state.value))) {
                onForegroundDenied(origin)
                return
            }
            controller.resume(origin)
            return
        }
        launchCommand {
            val settings = app.settingsRepository.settings.first()
            if (settings.serviceEnabled) handleStart(origin)
        }
    }

    private fun resumeAfterRestart() {
        if (!SettingsStore.isReadable(this)) return
        launchCommand {
            val enabled = try {
                app.settingsRepository.settings.first().serviceEnabled
            } catch (e: Exception) {
                Log.e(TAG, "Cannot read settings after restart", e)
                false
            }
            if (enabled && !controller.running) handleStart(StartOrigin.STICKY_RESTART)
        }
    }

    private fun originOf(intent: Intent, default: StartOrigin): StartOrigin =
        intent.getStringExtra(EXTRA_ORIGIN)?.let { name -> StartOrigin.entries.firstOrNull { it.name == name } } ?: default

    /**
     * The system refused the foreground start (Android 12+ background limits, e.g. a
     * watchdog or sticky restart without an exemption): tell the user, a tap is allowed.
     */
    private fun onForegroundDenied(origin: StartOrigin) {
        Diagnostics.log(HealthLevel.ERROR, LogTags.START, getString(R.string.svc_log_start_denied, origin.name))
        AppNotifications.showStoppedNotice(this)
        if (!controller.running && !controller.busy) {
            stopping = true
            stopSelf()
        }
    }

    // ── Foreground ──────────────────────────────────────────────────────────

    private fun enterForeground(notification: Notification, id: Int = NOTIFICATION_ID): Boolean = try {
        ServiceCompat.startForeground(
            this, id, notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        foregroundId = id
        if (id == NOTIFICATION_ID) monitorNotification.markPosted(MonitorStatus.state.value)
        true
    } catch (e: Exception) {
        val denied = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException
        Log.e(TAG, "Cannot enter foreground (denied=$denied)", e)
        false
    }

    private fun leaveForeground() {
        if (foregroundId == 0) return
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        foregroundId = 0
        monitorNotification.cancelPending()
    }

    // ── MonitorController.Host ──────────────────────────────────────────────

    override fun onStateChanged(state: MonitorState) {
        if (!controller.running) return
        if (foregroundId == NOTIFICATION_ID) {
            monitorNotification.update(state)
        } else {
            // Monitoring started during a monitoring-off drill: move the foreground to the
            // monitoring notification (the alert notification stays posted on its own).
            enterForeground(monitorNotification.build(state))
        }
    }

    override fun holdForeground(notificationId: Int, notification: Notification): Boolean {
        if (controller.running || foregroundId != 0) return false
        return enterForeground(notification, notificationId)
    }

    override fun releaseAlertForeground() {
        if (foregroundId == AppNotifications.ID_ALERT) leaveForeground()
    }

    override fun onPauseChanged(until: Long) {
        if (until > 0L) schedulePauseAlarm(this, until) else cancelPauseAlarm(this)
    }

    override fun onIdle() {
        onIdleCheck()
    }

    private fun onIdleCheck() {
        if (destroyed || stopping || pendingCommands > 0 || controller.running || controller.busy) return
        leaveForeground()
        // Only if no newer start request is pending (stopSelfResult checks the start id).
        stopping = stopSelfResult(lastStartId)
    }

    companion object {
        private const val TAG = "EewMonitorService"
        const val CHANNEL_ID = AppNotifications.CHANNEL_MONITOR
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "com.github.mytv.myearthquakealert.START_MONITOR"
        const val ACTION_STOP = "com.github.mytv.myearthquakealert.STOP_MONITOR"
        const val ACTION_DRILL = "com.github.mytv.myearthquakealert.RUN_DRILL"
        const val ACTION_PAUSE = "com.github.mytv.myearthquakealert.PAUSE_MONITOR"
        const val ACTION_RESUME = "com.github.mytv.myearthquakealert.RESUME_MONITOR"
        const val ACTION_SELF_TEST = "com.github.mytv.myearthquakealert.SELF_TEST"
        /** 静音 on the alert notification: stops sound, vibration and speech. */
        const val ACTION_ALERT_MUTE = "com.github.mytv.myearthquakealert.ALERT_MUTE"
        /** 我已安全 on the alert notification: dismisses the alert. */
        const val ACTION_ALERT_DISMISS = "com.github.mytv.myearthquakealert.ALERT_DISMISS"
        /** Watchdog: replace every socket now (heartbeat stalled). */
        const val ACTION_RECONNECT = "com.github.mytv.myearthquakealert.RECONNECT"
        /** Long, epoch millis: end of the pause for [ACTION_PAUSE]. */
        const val EXTRA_PAUSE_UNTIL = "pause_until"
        /** Long, millis: pause length for [ACTION_PAUSE] (alternative to [EXTRA_PAUSE_UNTIL]). */
        const val EXTRA_PAUSE_DURATION = "pause_duration"
        /** String, [com.github.mytv.myearthquakealert.domain.DrillScenario] id for [ACTION_DRILL]. */
        const val EXTRA_SCENARIO = "scenario"
        /** String, [StartOrigin] name for [ACTION_START] (and [ACTION_RESUME]). */
        const val EXTRA_ORIGIN = "origin"
        /** String, log reason for [ACTION_RECONNECT]. */
        const val EXTRA_REASON = "reason"

        private const val REQUEST_PAUSE_ALARM = 401
        private val USER_ORIGINS = setOf(StartOrigin.USER, StartOrigin.TILE, StartOrigin.NOTIFICATION, StartOrigin.WATCH)

        @Volatile private var instance: EewMonitorService? = null

        /** The service exists in this process (monitoring, a drill or a self test). */
        val isRunning: Boolean get() = instance != null

        /** Monitoring is on in this process (also while paused). */
        val isMonitoring: Boolean get() = instance?.let { it.controller.running } ?: false

        fun startIntent(context: Context, origin: StartOrigin = StartOrigin.USER): Intent =
            Intent(context, EewMonitorService::class.java).setAction(ACTION_START).putExtra(EXTRA_ORIGIN, origin.name)

        /** An intent for [action] (no extras), e.g. for a notification action. */
        fun commandIntent(context: Context, action: String): Intent =
            Intent(context, EewMonitorService::class.java).setAction(action)

        /**
         * Starts monitoring (and records it as switched on). Idempotent: a running service
         * ignores repeated starts. Call from the foreground, or from a context exempt from
         * background foreground-service limits (boot broadcast, notification tap).
         */
        fun start(context: Context, origin: StartOrigin = StartOrigin.USER) {
            try {
                ContextCompat.startForegroundService(context, startIntent(context, origin))
            } catch (e: IllegalStateException) {
                // ForegroundServiceStartNotAllowedException is an IllegalStateException.
                Log.w(TAG, "Cannot start monitoring from the background ($origin)", e)
                AppNotifications.showStoppedNotice(context)
            }
        }

        /**
         * Stops monitoring. Uses stopService rather than a STOP intent, so a service that
         * is not running is never started just to stop (which would owe a foreground start).
         * The service then records monitoring as switched off.
         */
        fun stop(context: Context) {
            context.stopService(Intent(context, EewMonitorService::class.java))
        }

        /** Runs an alert drill in the service. Call while the app is in the foreground. */
        fun drill(context: Context, scenario: DrillScenario.Scenario = DrillScenario.Scenario.LUSHAN): Boolean =
            send(context, Intent(context, EewMonitorService::class.java).setAction(ACTION_DRILL).putExtra(EXTRA_SCENARIO, scenario.id))

        /** Pauses monitoring until [untilEpochMillis]; an alarm resumes it automatically. */
        fun pause(context: Context, untilEpochMillis: Long): Boolean =
            send(context, Intent(context, EewMonitorService::class.java).setAction(ACTION_PAUSE).putExtra(EXTRA_PAUSE_UNTIL, untilEpochMillis))

        /** Pauses monitoring for [durationMillis] (暂停 1 小时 = 3 600 000). */
        fun pauseFor(context: Context, durationMillis: Long): Boolean =
            pause(context, System.currentTimeMillis() + durationMillis)

        /** Ends a pause early. */
        fun resume(context: Context): Boolean =
            send(context, Intent(context, EewMonitorService::class.java).setAction(ACTION_RESUME))

        /** Runs the 8-step end-to-end self test; progress is published in Diagnostics.selfTest. */
        fun selfTest(context: Context): Boolean =
            send(context, Intent(context, EewMonitorService::class.java).setAction(ACTION_SELF_TEST))

        /** Asks a running service to replace its sockets (watchdog). No-op when not running. */
        fun reconnect(context: Context, reason: String): Boolean {
            if (!isRunning) return false
            return send(context, commandIntent(context, ACTION_RECONNECT).putExtra(EXTRA_REASON, reason))
        }

        private fun send(context: Context, intent: Intent): Boolean = try {
            context.startService(intent)
            true
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Cannot send ${intent.action} from the background", e)
            false
        }

        /**
         * Arms the alarm that ends the pause at [until] (also after a reboot, which clears
         * alarms). Allowed while idle; Doze may defer it by a few minutes.
         */
        fun schedulePauseAlarm(context: Context, until: Long) {
            val alarm = context.getSystemService(AlarmManager::class.java) ?: return
            val pi = pauseAlarmIntent(context)
            try {
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, until, pi)
            } catch (e: Exception) {
                Log.w(TAG, "pause alarm", e)
                alarm.set(AlarmManager.RTC_WAKEUP, until, pi)
            }
        }

        fun cancelPauseAlarm(context: Context) {
            context.getSystemService(AlarmManager::class.java)?.cancel(pauseAlarmIntent(context))
        }

        private fun pauseAlarmIntent(context: Context): PendingIntent {
            val intent = Intent(context, EewMonitorService::class.java)
                .setAction(ACTION_RESUME)
                .putExtra(EXTRA_ORIGIN, StartOrigin.PAUSE_ENDED.name)
            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(context, REQUEST_PAUSE_ALARM, intent, flags)
            } else {
                PendingIntent.getService(context, REQUEST_PAUSE_ALARM, intent, flags)
            }
        }
    }
}
