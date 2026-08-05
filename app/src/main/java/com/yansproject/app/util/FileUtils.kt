package com.yansproject.app.util

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object FileUtils {

    private const val TAG = "FileUtils"

    fun getRootDirectory(context: Context): File {
        val appDoc = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "YANSPROJECT.ID")
        if (!appDoc.exists()) appDoc.mkdirs()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            try {
                val publicDoc = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "YANSPROJECT.ID")
                if (!publicDoc.exists()) publicDoc.mkdirs()
                if (publicDoc.exists() && publicDoc.canWrite()) {
                    return publicDoc
                }
            } catch (e: Exception) {
                Log.w(TAG, "Public Documents directory unavailable, falling back to app-private storage: ${e.message}")
            }
        }
        return appDoc
    }

    fun initFolderStructure(context: Context) {
        val parentDir = getRootDirectory(context)
        val folders = listOf("Invoice", "Export", "Backup", "Catalog", "Project", "Report", "Log", "Import")
        try {
            folders.forEach { sub ->
                val subDir = File(parentDir, sub)
                if (!subDir.exists()) {
                    subDir.mkdirs()
                }
            }
            Log.d(TAG, "Successfully initialized application folder hierarchy.")
        } catch (e: SecurityException) {
            Log.e(TAG, "Storage permission denied initializing folder structure: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing application folder structure: ${e.message}", e)
        }
    }

    fun getExportDirectory(context: Context, type: String): File {
        initFolderStructure(context)
        val parentDir = getRootDirectory(context)
        val subFolderName = when (type.lowercase()) {
            "invoice", "invoices" -> "Invoice"
            "backup", "backups", "db" -> "Backup"
            "export", "exports", "csv", "excel", "xls", "stock", "customer", "member", "finance" -> "Export"
            "catalog", "catalogs" -> "Catalog"
            "project", "projects" -> "Project"
            "report", "reports" -> "Report"
            "log", "logs", "audit" -> "Log"
            "import", "imports" -> "Import"
            else -> "Export"
        }
        val targetDir = File(parentDir, subFolderName)
        if (!targetDir.exists()) targetDir.mkdirs()
        return targetDir
    }

    fun mirrorToDownloads(context: Context, file: File, subFolder: String = "Export"): File? {
        if (!file.exists()) {
            Log.e(TAG, "Source file does not exist for mirroring: ${file.absolutePath}")
            return null
        }
        return try {
            val publicDownloads = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "YANSPROJECT.ID/$subFolder")
            if (!publicDownloads.exists()) publicDownloads.mkdirs()
            val dest = File(publicDownloads, file.name)
            file.copyTo(dest, overwrite = true)
            Log.i(TAG, "Successfully mirrored ${file.name} to Downloads: ${dest.absolutePath}")
            dest
        } catch (e: SecurityException) {
            Log.w(TAG, "Permission denied mirroring ${file.name} to public Downloads: ${e.message}")
            null
        } catch (e: java.io.IOException) {
            Log.e(TAG, "I/O error mirroring ${file.name} to public Downloads: ${e.message}", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed mirroring file to Downloads directory: ${e.message}", e)
            null
        }
    }

    fun openFolder(context: Context, folder: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                if (folder.isDirectory) folder else (folder.parentFile ?: folder)
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "resource/folder")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            try {
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (ex: Exception) {
                Log.w(TAG, "Unable to launch system file chooser intent for ${folder.absolutePath}: ${ex.message}")
                Toast.makeText(context, "Folder tersimpan di: ${folder.absolutePath}", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening folder URI: ${e.message}", e)
            Toast.makeText(context, "Folder tersimpan di: ${folder.absolutePath}", Toast.LENGTH_LONG).show()
        }
    }

    fun openFile(context: Context, file: File) {
        if (!file.exists()) {
            Toast.makeText(context, "Berkas tidak ditemukan.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)
            val mime = context.contentResolver.getType(uri) ?: when {
                file.name.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
                file.name.endsWith(".csv", ignoreCase = true) -> "text/csv"
                file.name.endsWith(".png", ignoreCase = true) -> "image/png"
                else -> "*/*"
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            Log.w(TAG, "No suitable handler application found for file ${file.name}: ${e.message}")
            Toast.makeText(context, "Tidak ada aplikasi untuk membuka ${file.name}. Berkas tersimpan di: ${file.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed opening file via system handler: ${e.message}", e)
            Toast.makeText(context, "Berkas tersimpan di: ${file.absolutePath}", Toast.LENGTH_LONG).show()
        }
    }
}
