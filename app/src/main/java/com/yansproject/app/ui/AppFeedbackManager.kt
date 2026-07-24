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
    private var isLoaded = false

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
            Log.d(TAG, "Vibrator initialized: ${vibrator != null}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Vibrator: ${e.message}")
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
                        Log.d(TAG, "Sound pool loaded sample successfully: $sampleId")
                    } else {
                        Log.e(TAG, "Sound pool failed loading sample: $sampleId with status: $status")
                    }
                }

                successSoundId = pool.load(appContext, R.raw.success, 1)
                warningSoundId = pool.load(appContext, R.raw.warning, 1)
                errorSoundId = pool.load(appContext, R.raw.error, 1)
                isLoaded = true
                Log.d(TAG, "SoundPool loading scheduled for success, warning, error.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize SoundPool: ${e.message}", e)
        }
    }

    fun triggerSuccess() {
        playSuccessSound()
        vibrateSuccess()
    }

    fun triggerWarning() {
        playWarningSound()
        vibrateWarning()
    }

    fun triggerError() {
        playErrorSound()
        vibrateError()
    }

    // --- SOUND METHODS ---
    private fun playSuccessSound() {
        soundPool?.let { pool ->
            if (successSoundId != -1) {
                pool.play(successSoundId, 1.0f, 1.0f, 0, 0, 1.0f)
            }
        }
    }

    private fun playWarningSound() {
        soundPool?.let { pool ->
            if (warningSoundId != -1) {
                pool.play(warningSoundId, 1.0f, 1.0f, 0, 0, 1.0f)
            }
        }
    }

    private fun playErrorSound() {
        soundPool?.let { pool ->
            if (errorSoundId != -1) {
                pool.play(errorSoundId, 1.0f, 1.0f, 0, 0, 1.0f)
            }
        }
    }

    // --- VIBRATION METHODS ---
    private fun vibrateSuccess() {
        val vib = vibrator ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Success is a light double pulse (quick tap)
                val timings = longArrayOf(0, 30, 40, 30)
                val amplitudes = intArrayOf(0, 180, 0, 220)
                vib.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(longArrayOf(0, 30, 40, 30), -1)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Vibrate permission missing: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Vibration failed: ${e.message}")
        }
    }

    private fun vibrateWarning() {
        val vib = vibrator ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Warning is a single medium-duration warning pulse
                vib.vibrate(VibrationEffect.createOneShot(120, 180))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(120)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Vibrate permission missing: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Vibration failed: ${e.message}")
        }
    }

    private fun vibrateError() {
        val vib = vibrator ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Error is three heavy urgent pulses
                val timings = longArrayOf(0, 100, 80, 100, 80, 150)
                val amplitudes = intArrayOf(0, 255, 0, 255, 0, 255)
                vib.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(longArrayOf(0, 100, 80, 100, 80, 150), -1)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Vibrate permission missing: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Vibration failed: ${e.message}")
        }
    }
}
