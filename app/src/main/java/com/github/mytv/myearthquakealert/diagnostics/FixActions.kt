package com.github.mytv.myearthquakealert.diagnostics

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.github.mytv.myearthquakealert.service.DeliveryChecks
import com.github.mytv.myearthquakealert.service.DeliveryRequirement
import com.github.mytv.myearthquakealert.util.AutoStartSettings

/**
 * Where the 修复 entry of a failed self-test step leads ([FixAction]). Requirements that
 * [DeliveryChecks] knows reuse its screens; the rest open the matching system settings.
 */
object FixActions {

    /** The delivery requirement [action] resolves, if it is one. */
    fun requirementOf(action: FixAction): DeliveryRequirement? = when (action) {
        FixAction.NOTIFICATION_SETTINGS -> DeliveryRequirement.NOTIFICATIONS
        FixAction.OVERLAY_SETTINGS -> DeliveryRequirement.OVERLAY
        FixAction.FULL_SCREEN_SETTINGS -> DeliveryRequirement.FULL_SCREEN_INTENT
        FixAction.LOCATION_PERMISSION -> DeliveryRequirement.LOCATION
        FixAction.BATTERY_OPTIMIZATION -> DeliveryRequirement.BATTERY_OPTIMIZATION
        FixAction.AUTO_START -> DeliveryRequirement.AUTO_START
        FixAction.NETWORK_SETTINGS, FixAction.DATE_SETTINGS -> null
    }

    /** The fix action for an unmet [requirement] (null for DND access, which has no step). */
    fun forRequirement(requirement: DeliveryRequirement): FixAction? = when (requirement) {
        DeliveryRequirement.NOTIFICATIONS -> FixAction.NOTIFICATION_SETTINGS
        DeliveryRequirement.OVERLAY -> FixAction.OVERLAY_SETTINGS
        DeliveryRequirement.FULL_SCREEN_INTENT -> FixAction.FULL_SCREEN_SETTINGS
        DeliveryRequirement.LOCATION -> FixAction.LOCATION_PERMISSION
        DeliveryRequirement.BATTERY_OPTIMIZATION -> FixAction.BATTERY_OPTIMIZATION
        DeliveryRequirement.AUTO_START -> FixAction.AUTO_START
        DeliveryRequirement.DND_ACCESS -> null
    }

    /**
     * The settings screen for [action]. LOCATION_PERMISSION opens the app's details page;
     * a UI that can still ask for the runtime permission should do that instead.
     */
    fun intent(context: Context, action: FixAction): Intent? {
        requirementOf(action)?.let { req -> DeliveryChecks.fixIntent(context, req)?.let { return it } }
        val pkg = Uri.parse("package:${context.packageName}")
        return when (action) {
            FixAction.LOCATION_PERMISSION -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
            FixAction.NETWORK_SETTINGS -> Intent(Settings.ACTION_WIRELESS_SETTINGS)
            FixAction.DATE_SETTINGS -> Intent(Settings.ACTION_DATE_SETTINGS)
            else -> null
        }?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Opens the screen for [action], falling back to the system settings. Returns false if nothing opened. */
    fun open(context: Context, action: FixAction): Boolean {
        if (action == FixAction.AUTO_START) {
            AutoStartSettings.open(context)
            return true
        }
        val primary = intent(context, action)
        val fallback = Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        for (candidate in listOfNotNull(primary, fallback)) {
            try {
                context.startActivity(candidate)
                return true
            } catch (_: Exception) {
            }
        }
        return false
    }
}
