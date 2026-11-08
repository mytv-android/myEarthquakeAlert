package com.github.mytv.myearthquakealert.service

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.github.mytv.myearthquakealert.util.AutoStartSettings

/**
 * 预检 (design §3 预检与降级): which delivery requirements are not met right now, and the
 * system screen that fixes each one. The overview preflight sheet and the self test use
 * this; [refresh] republishes the result into [MonitorStatus] (call it from onResume).
 */
object DeliveryChecks {

    fun missing(context: Context): Set<DeliveryRequirement> = buildSet {
        if (!notificationsAllowed(context)) add(DeliveryRequirement.NOTIFICATIONS)
        if (!Settings.canDrawOverlays(context)) add(DeliveryRequirement.OVERLAY)
        if (Build.VERSION.SDK_INT >= 34 &&
            !context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        ) add(DeliveryRequirement.FULL_SCREEN_INTENT)
        if (!locationGranted(context)) add(DeliveryRequirement.LOCATION)
        if (!context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)) {
            add(DeliveryRequirement.BATTERY_OPTIMIZATION)
        }
        if (!context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted) {
            add(DeliveryRequirement.DND_ACCESS)
        }
        // Not detectable: listed on ROMs known to block boot broadcasts (a reminder, never blocking).
        if (AutoStartSettings.isRestricted()) add(DeliveryRequirement.AUTO_START)
    }

    /** Recomputes [missing] and publishes it in [MonitorStatus.state]. */
    fun refresh(context: Context) {
        val missing = missing(context)
        MonitorStatus.update { it.copy(missing = missing) }
    }

    /**
     * The settings screen that resolves [requirement], or null when there is none: LOCATION
     * is a runtime permission the caller requests itself, AUTO_START goes through [fix].
     */
    fun fixIntent(context: Context, requirement: DeliveryRequirement): Intent? {
        val pkg = Uri.parse("package:${context.packageName}")
        return when (requirement) {
            DeliveryRequirement.NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= 26) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
            }
            DeliveryRequirement.OVERLAY -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg)
            DeliveryRequirement.FULL_SCREEN_INTENT -> if (Build.VERSION.SDK_INT >= 34) {
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg)
            } else {
                null
            }
            DeliveryRequirement.LOCATION -> null
            @Suppress("BatteryLife")
            DeliveryRequirement.BATTERY_OPTIMIZATION -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg)
            DeliveryRequirement.DND_ACCESS -> Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            DeliveryRequirement.AUTO_START -> null
        }?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /**
     * Opens the screen that fixes [requirement]. Returns false when the caller must
     * request a runtime permission instead (LOCATION; NOTIFICATIONS on 33+ when not yet asked).
     */
    fun fix(context: Context, requirement: DeliveryRequirement): Boolean {
        if (requirement == DeliveryRequirement.AUTO_START) {
            AutoStartSettings.open(context)
            return true
        }
        val intent = fixIntent(context, requirement) ?: return false
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun notificationsAllowed(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    private fun locationGranted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
}
