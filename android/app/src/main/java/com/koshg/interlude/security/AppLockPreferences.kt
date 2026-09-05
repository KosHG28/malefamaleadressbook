package com.koshg.interlude.security

import android.content.Context

private const val PREFS_NAME = "app_lock_prefs"
private const val KEY_ENABLED = "enabled"

/** Whether the app should gate itself behind biometric/device-credential auth. Off by default. */
class AppLockPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var isEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()
}
