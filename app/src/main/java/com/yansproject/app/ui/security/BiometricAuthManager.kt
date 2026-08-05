package com.yansproject.app.ui.security

import android.content.Context
import android.util.Log
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.util.concurrent.Executor

object BiometricAuthManager {
    private const val TAG = "BiometricAuthManager"

    fun authenticateWithBiometrics(
        context: Context,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val activity = context as? FragmentActivity
        if (activity == null) {
            // Find parent activity recursively if wrapped in ContextWrapper
            var tempContext = context
            var foundActivity: FragmentActivity? = null
            while (tempContext is android.content.ContextWrapper) {
                if (tempContext is FragmentActivity) {
                    foundActivity = tempContext
                    break
                }
                tempContext = tempContext.baseContext
            }
            if (foundActivity != null) {
                executePrompt(foundActivity, onSuccess, onError)
            } else {
                val errorMsg = "Sistem memerlukan FragmentActivity untuk autentikasi sidik jari."
                Log.e(TAG, errorMsg)
                onError(errorMsg)
            }
            return
        }
        executePrompt(activity, onSuccess, onError)
    }

    private fun executePrompt(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            if (activity.isFinishing || activity.isDestroyed) {
                val errorMsg = "Aktivitas tidak aktif untuk biometrik."
                Log.w(TAG, errorMsg)
                onError(errorMsg)
                return
            }
            val executor: Executor = ContextCompat.getMainExecutor(activity)
            val biometricPrompt = BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        val formattedMsg = when (errorCode) {
                            BiometricPrompt.ERROR_USER_CANCELED,
                            BiometricPrompt.ERROR_CANCELED,
                            BiometricPrompt.ERROR_NEGATIVE_BUTTON -> {
                                Log.i(TAG, "Biometric authentication cancelled by user/system (code=$errorCode)")
                                "Verifikasi dibatalkan oleh pengguna."
                            }
                            BiometricPrompt.ERROR_LOCKOUT -> {
                                Log.w(TAG, "Biometric authentication locked out temporarily (code=$errorCode)")
                                "Terlalu banyak percobaan gagal. Silakan coba lagi nanti atau gunakan PIN."
                            }
                            BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> {
                                Log.e(TAG, "Biometric authentication locked out permanently (code=$errorCode)")
                                "Biometrik terkunci permanen. Masukkan PIN atau kata sandi perangkat Anda."
                            }
                            BiometricPrompt.ERROR_NO_BIOMETRICS -> {
                                Log.w(TAG, "No biometrics enrolled on device (code=$errorCode)")
                                "Perangkat belum mendaftarkan data sidik jari/biometrik."
                            }
                            BiometricPrompt.ERROR_HW_UNAVAILABLE,
                            BiometricPrompt.ERROR_HW_NOT_PRESENT -> {
                                Log.w(TAG, "Biometric hardware unavailable or not present (code=$errorCode)")
                                "Perangkat keras biometrik tidak tersedia."
                            }
                            else -> {
                                Log.e(TAG, "Biometric error code $errorCode: $errString")
                                errString.toString().ifBlank { "Autentikasi biometrik gagal (Kode $errorCode)." }
                            }
                        }
                        onError(formattedMsg)
                    }

                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        Log.i(TAG, "Biometric authentication succeeded.")
                        onSuccess()
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        Log.w(TAG, "Biometric sample not recognized.")
                        onError("Sidik jari tidak dikenali. Silakan coba lagi.")
                    }
                }
            )

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Verifikasi Keamanan Owner")
                .setSubtitle("Gunakan Sidik Jari Anda untuk melanjutkan")
                .setNegativeButtonText("Batal / Gunakan PIN")
                .build()

            biometricPrompt.authenticate(promptInfo)
        } catch (t: Throwable) {
            Log.e(TAG, "Biometric execution error: ${t.message}", t)
            onError("Gagal membuka verifikasi biometrik: ${t.message}")
        }
    }
}
