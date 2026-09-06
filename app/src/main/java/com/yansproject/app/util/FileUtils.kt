package com.yansproject.app.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object FileUtils {

    private const val TAG = "FileUtils"

    fun getRootDirectory(context: Context): File {
        val publicDoc = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "YANSPROJECT.ID")
        try {
            if (!publicDoc.exists()) {
                publicDoc.mkdirs()
            }
            if (publicDoc.exists()) {
                return publicDoc
            }
        } catch (e: Exception) {
            Log.w(TAG, "Public Documents directory creation failed, falling back to app-private storage: ${e.message}")
        }

        val appDoc = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "YANSPROJECT.ID")
        if (!appDoc.exists()) {
            appDoc.mkdirs()
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val mimeType = when {
                    file.name.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
                    file.name.endsWith(".png", ignoreCase = true) -> "image/png"
                    file.name.endsWith(".csv", ignoreCase = true) -> "text/csv"
                    else -> "*/*"
                }
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/YANSPROJECT.ID/$subFolder")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                val itemUri = resolver.insert(collection, contentValues)
                if (itemUri != null) {
                    resolver.openOutputStream(itemUri)?.use { outStream ->
                        file.inputStream().use { inStream ->
                            inStream.copyTo(outStream)
                        }
                    }
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(itemUri, contentValues, null, null)
                    Log.i(TAG, "Successfully mirrored ${file.name} to MediaStore Downloads: $itemUri")
                }
            }

            // Always maintain a physical copy in app-specific public Downloads directory
            val publicDownloads = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "YANSPROJECT.ID/$subFolder")
            if (!publicDownloads.exists()) publicDownloads.mkdirs()
            val dest = File(publicDownloads, file.name)
            file.copyTo(dest, overwrite = true)
            Log.i(TAG, "Successfully mirrored ${file.name} to app-specific Downloads: ${dest.absolutePath}")
            dest
        } catch (e: SecurityException) {
            Log.w(TAG, "Permission denied mirroring ${file.name} to public Downloads: ${e.message}")
            file
        } catch (e: Exception) {
            Log.e(TAG, "Failed mirroring file to Downloads directory: ${e.message}", e)
            file
        }
    }

    fun openFolder(context: Context, folder: File) {
        val targetDir = if (folder.isDirectory) folder else (folder.parentFile ?: folder)
        if (!targetDir.exists()) {
            try { targetDir.mkdirs() } catch (_: Exception) {}
        }

        var launched = false

        // Build relative path for SAF DocumentUri
        val rootPublicPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).absolutePath + "/YANSPROJECT.ID"
        val relativeSubPath = when {
            targetDir.absolutePath.startsWith(rootPublicPath) -> {
                val sub = targetDir.absolutePath.removePrefix(rootPublicPath).trim('/')
                if (sub.isEmpty()) "Documents/YANSPROJECT.ID" else "Documents/YANSPROJECT.ID/$sub"
            }
            targetDir.name.equals("YANSPROJECT.ID", ignoreCase = true) -> "Documents/YANSPROJECT.ID"
            else -> "Documents/YANSPROJECT.ID/${targetDir.name}"
        }

        val safDocumentUri = Uri.parse("content://com.android.externalstorage.documents/document/primary:$relativeSubPath")
        val safTreeUri = Uri.parse("content://com.android.externalstorage.documents/tree/primary:$relativeSubPath")

        // Strategy 1: Open SAF Documents UI directly to the target folder
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(safDocumentUri, "vnd.android.document/directory")
                putExtra("android.provider.extra.INITIAL_URI", safDocumentUri)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    putExtra(DocumentsContract.EXTRA_INITIAL_URI, safDocumentUri)
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            launched = true
        } catch (e: Exception) {
            Log.w(TAG, "SAF document/directory view failed: ${e.message}")
        }

        // Strategy 2: FileProvider Uri with resource/folder MIME
        if (!launched) {
            try {
                val authority = "${context.packageName}.fileprovider"
                val folderUri = FileProvider.getUriForFile(context, authority, targetDir)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(folderUri, "resource/folder")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                launched = true
            } catch (e: Exception) {
                Log.w(TAG, "FileProvider resource/folder view failed: ${e.message}")
            }
        }

        // Strategy 3: Open Files / DocumentsUI package or Downloads intent
        if (!launched) {
            try {
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                    putExtra("android.provider.extra.INITIAL_URI", safTreeUri)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        putExtra(DocumentsContract.EXTRA_INITIAL_URI, safTreeUri)
                    }
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(intent, "Buka Folder YANSPROJECT.ID"))
                launched = true
            } catch (e: Exception) {
                Log.w(TAG, "GET_CONTENT folder fallback failed: ${e.message}")
            }
        }

        if (!launched) {
            Toast.makeText(context, "Folder YANSPROJECT.ID: ${targetDir.absolutePath}", Toast.LENGTH_LONG).show()
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
            Toast.makeText(context, "Tidak ada aplikasi untuk membuka ${file.name}. Berkas tersimpan di: ${file.name}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed opening file via system handler: ${e.message}", e)
            Toast.makeText(context, "Berkas tersimpan di: ${file.name}", Toast.LENGTH_LONG).show()
        }
    }
}
