package com.github.mytv.myearthquakealert.service

import android.app.KeyguardManager
import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.PowerManager

/** Device state that decides how an alert is presented (design §4: 锁屏、前台应用、电视). */
internal object DeviceState {

    fun isTv(context: Context): Boolean {
        val uiMode = context.getSystemService(UiModeManager::class.java)
        return uiMode?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }

    fun isInteractive(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isInteractive ?: true

    fun isKeyguardLocked(context: Context): Boolean =
        context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked ?: false

    /** Screen on and unlocked: the user is looking at another app or the launcher. */
    fun inUse(context: Context): Boolean = isInteractive(context) && !isKeyguardLocked(context)
}
