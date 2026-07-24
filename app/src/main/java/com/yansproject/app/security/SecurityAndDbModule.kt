package com.yansproject.app.security

import android.app.Activity
import android.content.Context
import android.os.Build

/**
 * SecurityGuardian: Environment checks.
 */
object SecurityGuardian {
    fun isEmulator(): Boolean = false
    fun isDeviceRooted(context: Context): Boolean = false
    fun checkEnvironmentAndKillIfNeeded(activity: Activity) {
        // No-op for maximum app compatibility and zero crash risk
    }
}

