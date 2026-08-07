package com.yansproject.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object ShareUtils {

    private const val TAG = "ShareUtils"

    fun cleanPhoneNumber(phone: String?): String {
        if (phone.isNullOrBlank()) return ""
        val digits = phone.replace("+", "").replace("-", "").replace(" ", "").replace("(", "").replace(")", "").trim()
        return if (digits.startsWith("0")) "62" + digits.substring(1) else digits
    }

    fun shareFile(context: Context, file: File, title: String = "Bagikan Berkas YANSPROJECT.ID") {
        if (!file.exists()) {
            Toast.makeText(context, "Berkas tidak ditemukan di penyimpanan.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
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
        } catch (e: Exception) {
            Log.e(TAG, "Failed sharing file ${file.name}: ${e.message}", e)
            Toast.makeText(context, "Gagal membagikan berkas: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareFileToWhatsApp(
        context: Context,
        file: File?,
        clientPhone: String?,
        captionText: String? = null
    ) {
        val cleanPhone = cleanPhoneNumber(clientPhone)

        if (file != null && file.exists() && file.length() > 0) {
            try {
                val authority = "${context.packageName}.fileprovider"
                val uri = FileProvider.getUriForFile(context, authority, file)
                val mime = context.contentResolver.getType(uri) ?: when {
                    file.name.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
                    file.name.endsWith(".png", ignoreCase = true) -> "image/png"
                    else -> "*/*"
                }

                val intent = Intent(Intent.ACTION_SEND).apply {
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

                var launched = false
                try {
                    intent.setPackage("com.whatsapp")
                    context.startActivity(intent)
                    launched = true
                } catch (e: Exception) {
                    Log.w(TAG, "com.whatsapp direct share failed: ${e.message}")
                }

                if (!launched) {
                    try {
                        intent.setPackage("com.whatsapp.w4b")
                        context.startActivity(intent)
                        launched = true
                    } catch (e: Exception) {
                        Log.w(TAG, "com.whatsapp.w4b direct share failed: ${e.message}")
                    }
                }

                if (!launched) {
                    intent.setPackage(null)
                    val chooser = Intent.createChooser(intent, "Bagikan Dokumen Invoice via").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(chooser)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed sharing file via WhatsApp: ${e.message}", e)
                Toast.makeText(context, "Gagal membagikan ke WhatsApp: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        } else {
            if (cleanPhone.length >= 9) {
                try {
                    val encodedText = if (!captionText.isNullOrBlank()) Uri.encode(captionText) else ""
                    val waUri = Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone&text=$encodedText")
                    val intent = Intent(Intent.ACTION_VIEW, waUri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, captionText ?: "")
                    }
                    context.startActivity(Intent.createChooser(intent, "Bagikan via"))
                }
            } else if (!captionText.isNullOrBlank()) {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, captionText)
                }
                context.startActivity(Intent.createChooser(intent, "Bagikan via"))
            } else {
                Toast.makeText(context, "Nomor WhatsApp customer tidak valid atau berkas tidak tersedia.", Toast.LENGTH_LONG).show()
            }
        }
    }
}

