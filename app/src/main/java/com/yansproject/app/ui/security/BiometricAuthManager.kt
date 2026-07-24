package com.yansproject.app.ui.security

import android.content.Context
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.util.concurrent.Executor

object BiometricAuthManager {

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
                onError("Sistem memerlukan FragmentActivity untuk autentikasi sidik jari.")
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
        val executor: Executor = ContextCompat.getMainExecutor(activity)
        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onError(errString.toString())
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onError("Autentikasi sidik jari gagal.")
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Verifikasi Keamanan Owner")
            .setSubtitle("Gunakan Sidik Jari Anda untuk melanjutkan")
            .setNegativeButtonText("Batal / Gunakan PIN")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}
