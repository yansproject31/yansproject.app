package com.yansproject.app.data

import android.util.Log
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

class ExportValidationException(message: String) : Exception(message)

/**
 * ExportManager: Centralized file export integrity coordinator.
 * Strict contract: Never reports success unless file exists, file length > 0, flush succeeds, and close succeeds.
 */
class ExportManager private constructor() {

    private val TAG = "ExportManager"

    companion object {
        @Volatile
        private var INSTANCE: ExportManager? = null

        fun getInstance(): ExportManager {
            return INSTANCE ?: synchronized(this) {
                val instance = ExportManager()
                INSTANCE = instance
                instance
            }
        }
    }

    /**
     * Executes a file export with guaranteed stream flush, close, and size validation.
     */
    fun exportToFile(targetFile: File, writeBlock: (OutputStream) -> Unit): Result<File> {
        var outputStream: BufferedOutputStream? = null
        var flushSucceeded = false
        var closeSucceeded = false

        try {
            val parent = targetFile.parentFile
            if (parent != null && !parent.exists()) {
                parent.mkdirs()
            }

            if (targetFile.exists()) {
                targetFile.delete()
            }

            outputStream = BufferedOutputStream(FileOutputStream(targetFile))

            // Perform data write operation
            writeBlock(outputStream)

            // 1. Flush stream
            outputStream.flush()
            flushSucceeded = true

            // 2. Close stream
            outputStream.close()
            closeSucceeded = true
            outputStream = null

            // 3. Post-execution file integrity verification
            val fileExists = targetFile.exists()
            val fileLength = if (fileExists) targetFile.length() else 0L

            if (!flushSucceeded) {
                throw ExportValidationException("Flush operation failed for export file '${targetFile.name}'.")
            }
            if (!closeSucceeded) {
                throw ExportValidationException("Close operation failed for export file '${targetFile.name}'.")
            }
            if (!fileExists) {
                throw ExportValidationException("Export file '${targetFile.name}' was not created.")
            }
            if (fileLength <= 0L) {
                throw ExportValidationException("Export file '${targetFile.name}' has invalid zero byte length.")
            }

            Log.i(TAG, "Export successfully completed and verified for '${targetFile.absolutePath}' ($fileLength bytes).")
            return Result.success(targetFile)
        } catch (e: Exception) {
            Log.e(TAG, "Export failed for '${targetFile.name}': ${e.message}", e)

            // Attempt cleanup of invalid/corrupt file
            try {
                outputStream?.close()
            } catch (ignored: Exception) {}

            if (targetFile.exists()) {
                val deleted = targetFile.delete()
                Log.w(TAG, "Cleaned up invalid export file attempt: '${targetFile.name}', deleted=$deleted")
            }

            return Result.failure(
                if (e is ExportValidationException) e else ExportValidationException("Export failed: ${e.message}")
            )
        }
    }
}
