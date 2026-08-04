package com.yansproject.app.util

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object FileUtils {

    private const val TAG = "FileUtils"

    fun getRootDirectory(context: Context): File {
        val publicDoc = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "YANSPROJECT.ID")
        return try {
            if (!publicDoc.exists()) publicDoc.mkdirs()
            if (publicDoc.exists() && publicDoc.canWrite()) {
                publicDoc
            } else {
                val appDoc = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "YANSPROJECT.ID")
                if (!appDoc.exists()) appDoc.mkdirs()
                appDoc
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed accessing public Documents directory, falling back to app-private storage: ${e.message}")
            val appDoc = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "YANSPROJECT.ID")
            if (!appDoc.exists()) appDoc.mkdirs()
            appDoc
        }
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
        return try {
            val publicDownloads = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "YANSPROJECT.ID/$subFolder")
            if (!publicDownloads.exists()) publicDownloads.mkdirs()
            val dest = File(publicDownloads, file.name)
            file.copyTo(dest, overwrite = true)
            Log.d(TAG, "Mirrored ${file.name} to Downloads folder: ${dest.absolutePath}")
            dest
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
        } catch (e: Exception) {
            try {
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (ex: Exception) {
                Log.w(TAG, "Unable to launch system file chooser intent for ${folder.absolutePath}: ${ex.message}")
                Toast.makeText(context, "Membuka folder: ${folder.absolutePath}", Toast.LENGTH_LONG).show()
            }
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
            val mime = context.contentResolver.getType(uri) ?: "*/*"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed opening file via system handler: ${e.message}")
            Toast.makeText(context, "Berkas tersimpan di: ${file.absolutePath}", Toast.LENGTH_LONG).show()
        }
    }
}
