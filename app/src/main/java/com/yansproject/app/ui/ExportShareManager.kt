package com.yansproject.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

class ExportShareManager(private val context: Context) {

    /**
     * Shares a local file (such as a CSV report) directly via the Android System Share Sheet.
     * Generates a secure Content URI with temporary read permission flags.
     * @param file The File object located in internal storage or cache to share
     * @param title The title shown on the system share dialog chooser
     */
    fun shareLocalFile(file: File?, title: String = "Bagikan Laporan ERP") {
        if (file == null || !file.exists()) {
            Toast.makeText(context, "Berkas laporan tidak ditemukan / gagal di-generate.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val authority = "${context.packageName}.fileprovider"
            val fileUri: Uri = FileProvider.getUriForFile(context, authority, file)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, fileUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooserIntent = Intent.createChooser(shareIntent, title).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(chooserIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Gagal membagikan berkas: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}
