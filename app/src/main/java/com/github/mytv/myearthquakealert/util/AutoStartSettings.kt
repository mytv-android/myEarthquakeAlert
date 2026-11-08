package com.github.mytv.myearthquakealert.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * Several Chinese ROMs (MIUI / HyperOS, ColorOS, OriginOS, EMUI / MagicOS) block boot
 * broadcasts for third-party apps until the user allows "auto-start". These pages are
 * not public API, so each is tried in turn, falling back to the app details screen.
 */
object AutoStartSettings {

    private const val TAG = "AutoStartSettings"

    private val xiaomi = listOf(
        ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
    )
    private val oppo = listOf(
        ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
        ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
        ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
    )
    private val vivo = listOf(
        ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
        ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
    )
    private val huawei = listOf(
        ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
    )

    private val byManufacturer = mapOf(
        "xiaomi" to xiaomi, "redmi" to xiaomi, "poco" to xiaomi,
        "oppo" to oppo, "realme" to oppo, "oneplus" to oppo,
        "vivo" to vivo, "iqoo" to vivo,
        "huawei" to huawei, "honor" to huawei,
    )

    private fun candidates(): List<ComponentName>? =
        byManufacturer[Build.MANUFACTURER.lowercase()] ?: byManufacturer[Build.BRAND.lowercase()]

    /** True on ROMs known to block boot broadcasts by default. */
    fun isRestricted(): Boolean = candidates() != null

    /** Opens the ROM's auto-start page, or the app details screen when none can be opened. */
    fun open(context: Context) {
        for (component in candidates().orEmpty()) {
            try {
                context.startActivity(Intent().setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (e: Exception) {
                // ActivityNotFoundException, or SecurityException for non-exported pages.
                Log.d(TAG, "Auto-start page unavailable: ${component.flattenToShortString()}")
            }
        }
        try {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            Log.w(TAG, "Cannot open app details", e)
        }
    }
}
