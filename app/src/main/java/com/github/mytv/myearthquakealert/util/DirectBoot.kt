package com.github.mytv.myearthquakealert.util

import android.content.Context
import android.os.Build
import android.os.UserManager

/**
 * True once credential-encrypted storage is available, i.e. the user has unlocked the
 * device at least once since boot. Always true before API 24 (no Direct Boot).
 */
fun Context.isUserUnlocked(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return true
    return getSystemService(UserManager::class.java)?.isUserUnlocked ?: true
}

/**
 * Storage that stays readable before the first unlock after boot. Falls back to the
 * regular context before API 24, where all storage is available at boot.
 */
fun Context.deviceProtectedContext(): Context =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        applicationContext.createDeviceProtectedStorageContext()
    } else {
        applicationContext
    }
