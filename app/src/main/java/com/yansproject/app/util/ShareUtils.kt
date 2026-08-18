package com.yansproject.app.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

enum class ShareResult {
    SUCCESS,
    CANCELLED,
    FAILED
}

object ShareUtils {

    private const val TAG = "ShareUtils"
    const val PACKAGE_WHATSAPP_BUSINESS = "com.whatsapp.w4b"
    const val PACKAGE_WHATSAPP_STANDARD = "com.whatsapp"

    fun cleanPhoneNumber(phone: String?): String {
        return SecureWhatsAppShareUtil.validatePhoneNumber(phone).normalizedNumber
    }

    fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return SecureWhatsAppShareUtil.isPackageInstalled(context, packageName)
    }

    fun shareFile(context: Context, file: File, title: String = "Bagikan Berkas YANSPROJECT.ID"): ShareResult {
        if (!file.exists()) {
            Log.w(TAG, "File does not exist for share: ${file.absolutePath}")
            return ShareResult.FAILED
        }
        return try {
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)
            val mime = context.contentResolver.getType(uri) ?: "*/*"
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, title).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            ShareResult.SUCCESS
        } catch (e: Exception) {
            Log.e(TAG, "Failed sharing file ${file.name}: ${e.message}", e)
            ShareResult.FAILED
        }
    }

    fun shareFileToWhatsApp(
        context: Context,
        file: File?,
        clientPhone: String?,
        captionText: String? = null
    ): ShareResult {
        val cleanPhone = cleanPhoneNumber(clientPhone)
        val hasFile = file != null && file.exists() && file.length() > 0

        val isW4bInstalled = isPackageInstalled(context, PACKAGE_WHATSAPP_BUSINESS)
        val isWaInstalled = isPackageInstalled(context, PACKAGE_WHATSAPP_STANDARD)

        if (hasFile && file != null) {
            try {
                val authority = "${context.packageName}.fileprovider"
                val uri = FileProvider.getUriForFile(context, authority, file)
                val mime = context.contentResolver.getType(uri) ?: when {
                    file.name.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
                    file.name.endsWith(".png", ignoreCase = true) -> "image/png"
                    else -> "*/*"
                }

                fun buildBaseIntent(): Intent {
                    return Intent(Intent.ACTION_SEND).apply {
                        type = mime
                        putExtra(Intent.EXTRA_STREAM, uri)
                        if (!captionText.isNullOrBlank()) {
                            putExtra(Intent.EXTRA_TEXT, captionText)
                        }
                        if (cleanPhone.length >= 9) {
                            putExtra("jid", "$cleanPhone@s.whatsapp.net")
                        }
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }

                launchWhatsAppIntent(
                    context = context,
                    isW4bInstalled = isW4bInstalled,
                    isWaInstalled = isWaInstalled,
                    buildIntentForPackage = { pkg ->
                        buildBaseIntent().apply { setPackage(pkg) }
                    },
                    fallbackLaunch = {
                        val chooser = Intent.createChooser(buildBaseIntent(), "Bagikan Dokumen Invoice via").apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(chooser)
                    }
                )
                return ShareResult.SUCCESS
            } catch (e: Exception) {
                Log.e(TAG, "Failed sharing file via WhatsApp: ${e.message}", e)
                return ShareResult.FAILED
            }
        } else {
            return openWhatsAppText(context, clientPhone, captionText)
        }
    }

    fun openWhatsAppText(context: Context, clientPhone: String?, text: String? = null): ShareResult {
        val cleanPhone = cleanPhoneNumber(clientPhone)
        val encodedText = if (!text.isNullOrBlank()) Uri.encode(text) else ""

        val isW4bInstalled = isPackageInstalled(context, PACKAGE_WHATSAPP_BUSINESS)
        val isWaInstalled = isPackageInstalled(context, PACKAGE_WHATSAPP_STANDARD)

        val uriStr = if (cleanPhone.length >= 9) {
            "https://api.whatsapp.com/send?phone=$cleanPhone${if (encodedText.isNotBlank()) "&text=$encodedText" else ""}"
        } else {
            "https://api.whatsapp.com/send?text=$encodedText"
        }
        val waUri = Uri.parse(uriStr)

        fun buildBaseIntent(): Intent {
            return Intent(Intent.ACTION_VIEW, waUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        return try {
            launchWhatsAppIntent(
                context = context,
                isW4bInstalled = isW4bInstalled,
                isWaInstalled = isWaInstalled,
                buildIntentForPackage = { pkg ->
                    buildBaseIntent().apply { setPackage(pkg) }
                },
                fallbackLaunch = {
                    val intent = buildBaseIntent()
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text ?: "")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Bagikan via"))
                    }
                }
            )
            ShareResult.SUCCESS
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open WhatsApp: ${e.message}", e)
            ShareResult.FAILED
        }
    }

    private fun launchWhatsAppIntent(
        context: Context,
        isW4bInstalled: Boolean,
        isWaInstalled: Boolean,
        buildIntentForPackage: (String) -> Intent,
        fallbackLaunch: () -> Unit
    ) {
        if (isW4bInstalled && isWaInstalled) {
            // Both installed: Present native system resolver dialog with "Sekali" / "Selalu"
            val w4bIntent = buildIntentForPackage(PACKAGE_WHATSAPP_BUSINESS)
            val waIntent = buildIntentForPackage(PACKAGE_WHATSAPP_STANDARD)

            val chooser = Intent.createChooser(w4bIntent, "Pilih Aplikasi WhatsApp").apply {
                putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(waIntent))
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } else if (isW4bInstalled) {
            // Only WhatsApp Business installed
            val w4bIntent = buildIntentForPackage(PACKAGE_WHATSAPP_BUSINESS)
            context.startActivity(w4bIntent)
        } else if (isWaInstalled) {
            // Only Standard WhatsApp installed
            val waIntent = buildIntentForPackage(PACKAGE_WHATSAPP_STANDARD)
            context.startActivity(waIntent)
        } else {
            // Fallback launch
            fallbackLaunch()
        }
    }
}
