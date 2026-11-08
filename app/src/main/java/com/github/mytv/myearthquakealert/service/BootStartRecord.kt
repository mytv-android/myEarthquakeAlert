package com.github.mytv.myearthquakealert.service

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import com.github.mytv.myearthquakealert.util.deviceProtectedContext
import kotlin.math.abs

/**
 * Remembers when monitoring was started automatically after a boot, so the settings
 * screen can show "started 9 s after boot" and the user can tell boot start works.
 * Stored in device-protected storage because the write happens before the first unlock.
 */
object BootStartRecord {

    private const val PREFS = "boot_start_record"
    private const val KEY_BOOT_COUNT = "boot_count"
    private const val KEY_BOOT_WALL_TIME = "boot_wall_time"
    private const val KEY_DELAY_MS = "delay_ms"
    private const val SAME_BOOT_TOLERANCE_MS = 120_000L

    /** Records the first automatic start of this boot; repeated boot broadcasts are ignored. */
    fun recordAutoStart(context: Context) {
        val prefs = prefs(context)
        if (isCurrentBoot(context, prefs)) return
        prefs.edit()
            .putInt(KEY_BOOT_COUNT, bootCount(context))
            .putLong(KEY_BOOT_WALL_TIME, bootWallTime())
            .putLong(KEY_DELAY_MS, SystemClock.elapsedRealtime())
            .apply()
    }

    /** Seconds from boot to the automatic start, or null if monitoring was not auto-started in this boot. */
    fun delaySecondsThisBoot(context: Context): Long? {
        val prefs = prefs(context)
        if (!isCurrentBoot(context, prefs)) return null
        return prefs.getLong(KEY_DELAY_MS, 0L) / 1000
    }

    private fun isCurrentBoot(context: Context, prefs: SharedPreferences): Boolean {
        if (!prefs.contains(KEY_DELAY_MS)) return false
        val count = bootCount(context)
        if (count >= 0) return prefs.getInt(KEY_BOOT_COUNT, -1) == count
        return abs(prefs.getLong(KEY_BOOT_WALL_TIME, 0L) - bootWallTime()) < SAME_BOOT_TOLERANCE_MS
    }

    private fun bootCount(context: Context): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
        } else {
            -1
        }

    private fun bootWallTime(): Long = System.currentTimeMillis() - SystemClock.elapsedRealtime()

    private fun prefs(context: Context): SharedPreferences =
        context.deviceProtectedContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
