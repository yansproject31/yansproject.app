package com.yansproject.app.ui.security

import android.content.Context
import android.util.Log
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.yansproject.app.ui.AuthoritativeSessionManager
import java.util.concurrent.Executor

enum class BiometricResultState {
    SUCCESS,
    FAILED_ATTEMPT,
    CANCELLED,
    LOCKED_OUT,
    ERROR
}

object BiometricAuthManager {
    private const val TAG = "BiometricAuthManager"

    fun authenticateWithBiometrics(
        context: Context,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onAttemptFailed: (() -> Unit)? = null
    ) {
        val initialSessionGen = AuthoritativeSessionManager.sessionState.value.sessionGeneration
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
                executePrompt(foundActivity, initialSessionGen, onSuccess, onError, onAttemptFailed)
            } else {
                val errorMsg = "Sistem memerlukan FragmentActivity untuk autentikasi sidik jari."
                Log.e(TAG, errorMsg)
                onError(errorMsg)
            }
            return
        }
        executePrompt(activity, initialSessionGen, onSuccess, onError, onAttemptFailed)
    }

    private fun executePrompt(
        activity: FragmentActivity,
        boundSessionGen: Long,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onAttemptFailed: (() -> Unit)? = null
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
                    private fun isSessionValid(): Boolean {
                        val currentGen = AuthoritativeSessionManager.sessionState.value.sessionGeneration
                        if (currentGen != boundSessionGen) {
                            Log.w(TAG, "Session generation changed ($boundSessionGen -> $currentGen). Invalidating biometric callback.")
                            return false
                        }
                        return true
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        if (!isSessionValid()) return

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
                        if (!isSessionValid()) return
                        Log.i(TAG, "Biometric authentication succeeded for session generation $boundSessionGen.")
                        onSuccess()
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        if (!isSessionValid()) return
                        Log.w(TAG, "Biometric sample not recognized. Prompt remains active for retry (FAILED_ATTEMPT).")
                        // Do not terminate retry capability on FAILED_ATTEMPT
                        onAttemptFailed?.invoke()
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
