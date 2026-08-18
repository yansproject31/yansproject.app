package com.yansproject.app.security

import android.app.Activity
import android.content.Context
import android.util.Log

/**
 * SecurityGuardian: Authoritative environment verification module.
 * Evaluates explicit security state without killing the application as recovery.
 */
object SecurityGuardian {

    @Volatile
    private var currentState: SecurityState = SecurityState.UNKNOWN

    fun evaluateEnvironment(context: Context): SecurityState {
        currentState = SecurityState.CHECKING
        val result = try {
            OmniverseSecurity.evaluateSecurityState(context)
        } catch (e: Exception) {
            SecurityState.CHECK_FAILED
        }
        currentState = result
        return result
    }

    fun getSecurityState(): SecurityState = currentState

    fun isEmulator(): Boolean {
        return currentState == SecurityState.EMULATOR
    }

    fun isDeviceRooted(context: Context): Boolean {
        return evaluateEnvironment(context) == SecurityState.ROOTED
    }

    fun checkEnvironmentAndKillIfNeeded(activity: Activity) {
        val state = evaluateEnvironment(activity)
        Log.i("SecurityGuardian", "Evaluated security environment state: $state")
    }
}

