package com.yansproject.app.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.yansproject.app.R

object AppFeedbackManager {
    private const val TAG = "AppFeedbackManager"

    private var soundPool: SoundPool? = null
    private var successSoundId: Int = -1
    private var warningSoundId: Int = -1
    private var errorSoundId: Int = -1
    
    @Volatile
    private var isSuccessLoaded = false
    @Volatile
    private var isWarningLoaded = false
    @Volatile
    private var isErrorLoaded = false

    private var vibrator: Vibrator? = null

    fun initialize(context: Context) {
        val appContext = context.applicationContext

        // 1. Initialize Vibrator
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            val hasVib = vibrator?.hasVibrator() == true
            Log.d(TAG, "Vibrator initialized: servicePresent=${vibrator != null}, hardwarePresent=$hasVib")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Vibrator: ${e.message}", e)
        }

        // 2. Initialize SoundPool
        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            soundPool = SoundPool.Builder()
                .setMaxStreams(5)
                .setAudioAttributes(audioAttributes)
                .build()

            soundPool?.let { pool ->
                pool.setOnLoadCompleteListener { _, sampleId, status ->
                    if (status == 0) {
                        Log.d(TAG, "SoundPool sample loaded successfully: sampleId=$sampleId")
                        when (sampleId) {
                            successSoundId -> isSuccessLoaded = true
                            warningSoundId -> isWarningLoaded = true
                            errorSoundId -> isErrorLoaded = true
                        }
                    } else {
                        Log.e(TAG, "SoundPool sample load failed: sampleId=$sampleId with status=$status")
                    }
                }

                successSoundId = pool.load(appContext, R.raw.success, 1)
                warningSoundId = pool.load(appContext, R.raw.warning, 1)
                errorSoundId = pool.load(appContext, R.raw.error, 1)
                Log.d(TAG, "SoundPool sample load requested: successId=$successSoundId, warningId=$warningSoundId, errorId=$errorSoundId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize SoundPool: ${e.message}", e)
        }
    }

    fun triggerSuccess() {
        val soundOk = playSuccessSound()
        val vibOk = vibrateSuccess()
        Log.i(TAG, "Feedback triggered [SUCCESS]: soundPlayed=$soundOk, vibrated=$vibOk")
    }

    fun triggerWarning() {
        val soundOk = playWarningSound()
        val vibOk = vibrateWarning()
        Log.i(TAG, "Feedback triggered [WARNING]: soundPlayed=$soundOk, vibrated=$vibOk")
    }

    fun triggerError() {
        val soundOk = playErrorSound()
        val vibOk = vibrateError()
        Log.i(TAG, "Feedback triggered [ERROR]: soundPlayed=$soundOk, vibrated=$vibOk")
    }

    // --- SOUND METHODS ---
    private fun playSuccessSound(): Boolean {
        val pool = soundPool
        if (pool == null) {
            Log.w(TAG, "playSuccessSound skipped: SoundPool not initialized")
            return false
        }
        if (successSoundId == -1 || !isSuccessLoaded) {
            Log.w(TAG, "playSuccessSound skipped: sample not loaded yet (id=$successSoundId, loaded=$isSuccessLoaded)")
            return false
        }
        return try {
            val streamId = pool.play(successSoundId, 1.0f, 1.0f, 0, 0, 1.0f)
            if (streamId == 0) {
                Log.w(TAG, "playSuccessSound failed: SoundPool returned streamId 0")
                false
            } else {
                true
            }
        } catch (e: Throwable) {
            Log.e(TAG, "playSuccessSound error: ${e.message}", e)
            false
        }
    }

    private fun playWarningSound(): Boolean {
        val pool = soundPool
        if (pool == null) {
            Log.w(TAG, "playWarningSound skipped: SoundPool not initialized")
            return false
        }
        if (warningSoundId == -1 || !isWarningLoaded) {
            Log.w(TAG, "playWarningSound skipped: sample not loaded yet (id=$warningSoundId, loaded=$isWarningLoaded)")
            return false
        }
        return try {
            val streamId = pool.play(warningSoundId, 1.0f, 1.0f, 0, 0, 1.0f)
            if (streamId == 0) {
                Log.w(TAG, "playWarningSound failed: SoundPool returned streamId 0")
                false
            } else {
                true
            }
        } catch (e: Throwable) {
            Log.e(TAG, "playWarningSound error: ${e.message}", e)
            false
        }
    }

    private fun playErrorSound(): Boolean {
        val pool = soundPool
        if (pool == null) {
            Log.w(TAG, "playErrorSound skipped: SoundPool not initialized")
            return false
        }
        if (errorSoundId == -1 || !isErrorLoaded) {
            Log.w(TAG, "playErrorSound skipped: sample not loaded yet (id=$errorSoundId, loaded=$isErrorLoaded)")
            return false
        }
        return try {
            val streamId = pool.play(errorSoundId, 1.0f, 1.0f, 0, 0, 1.0f)
            if (streamId == 0) {
                Log.w(TAG, "playErrorSound failed: SoundPool returned streamId 0")
                false
            } else {
                true
            }
        } catch (e: Throwable) {
            Log.e(TAG, "playErrorSound error: ${e.message}", e)
            false
        }
    }

    // --- VIBRATION METHODS ---
    private fun vibrateSuccess(): Boolean {
        val vib = vibrator
        if (vib == null) {
            Log.w(TAG, "vibrateSuccess skipped: Vibrator service unavailable")
            return false
        }
        if (!vib.hasVibrator()) {
            Log.w(TAG, "vibrateSuccess skipped: Device lacks vibration hardware")
            return false
        }
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timings = longArrayOf(0, 30, 40, 30)
                val amplitudes = intArrayOf(0, 180, 0, 220)
                vib.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(longArrayOf(0, 30, 40, 30), -1)
            }
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "Vibrate permission missing: ${e.message}", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "vibrateSuccess failed: ${e.message}", e)
            false
        }
    }

    private fun vibrateWarning(): Boolean {
        val vib = vibrator
        if (vib == null) {
            Log.w(TAG, "vibrateWarning skipped: Vibrator service unavailable")
            return false
        }
        if (!vib.hasVibrator()) {
            Log.w(TAG, "vibrateWarning skipped: Device lacks vibration hardware")
            return false
        }
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createOneShot(120, 180))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(120)
            }
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "Vibrate permission missing: ${e.message}", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "vibrateWarning failed: ${e.message}", e)
            false
        }
    }

    private fun vibrateError(): Boolean {
        val vib = vibrator
        if (vib == null) {
            Log.w(TAG, "vibrateError skipped: Vibrator service unavailable")
            return false
        }
        if (!vib.hasVibrator()) {
            Log.w(TAG, "vibrateError skipped: Device lacks vibration hardware")
            return false
        }
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timings = longArrayOf(0, 100, 80, 100, 80, 150)
                val amplitudes = intArrayOf(0, 255, 0, 255, 0, 255)
                vib.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(longArrayOf(0, 100, 80, 100, 80, 150), -1)
            }
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "Vibrate permission missing: ${e.message}", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "vibrateError failed: ${e.message}", e)
            false
        }
    }
}
