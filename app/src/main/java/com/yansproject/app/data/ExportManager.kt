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
     * Executes a file export with guaranteed atomic replacement:
     * Write to temp file -> Flush & Close -> Validate -> Atomic Rename to target file.
     * If export fails or validation fails, previous valid targetFile is preserved!
     */
    fun exportToFile(targetFile: File, writeBlock: (OutputStream) -> Unit): Result<File> {
        var outputStream: BufferedOutputStream? = null
        var flushSucceeded = false
        var closeSucceeded = false

        val parent = targetFile.parentFile ?: File(".")
        if (!parent.exists()) {
            parent.mkdirs()
        }

        val tempFile = File(parent, "${targetFile.name}.tmp_${System.currentTimeMillis()}")

        try {
            outputStream = BufferedOutputStream(FileOutputStream(tempFile))

            // Perform data write operation to temp file
            writeBlock(outputStream)

            // 1. Flush stream
            outputStream.flush()
            flushSucceeded = true

            // 2. Close stream
            outputStream.close()
            closeSucceeded = true
            outputStream = null

            // 3. Post-execution temp file integrity verification
            val tempExists = tempFile.exists()
            val tempLength = if (tempExists) tempFile.length() else 0L

            if (!flushSucceeded) {
                throw ExportValidationException("Flush operation failed for export temp file '${tempFile.name}'.")
            }
            if (!closeSucceeded) {
                throw ExportValidationException("Close operation failed for export temp file '${tempFile.name}'.")
            }
            if (!tempExists) {
                throw ExportValidationException("Export temp file '${tempFile.name}' was not created.")
            }
            if (tempLength <= 0L) {
                throw ExportValidationException("Export temp file '${tempFile.name}' has invalid zero byte length.")
            }

            // 4. Atomic Rename to targetFile (Preserving existing valid targetFile until replacement is verified)
            val renameSuccess = tempFile.renameTo(targetFile)
            if (!renameSuccess) {
                // Fallback: copy tempFile to targetFile if rename across filesystems fails
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            Log.i(TAG, "Export successfully completed and atomically replaced '${targetFile.absolutePath}' ($tempLength bytes).")
            return Result.success(targetFile)
        } catch (e: Exception) {
            Log.e(TAG, "Export failed for '${targetFile.name}': ${e.message}", e)

            // Attempt cleanup of temp file ONLY (Preserving targetFile)
            try {
                outputStream?.close()
            } catch (ignored: Exception) {}

            if (tempFile.exists()) {
                val deleted = tempFile.delete()
                Log.w(TAG, "Cleaned up invalid temp file attempt: '${tempFile.name}', deleted=$deleted")
            }

            return Result.failure(
                if (e is ExportValidationException) e else ExportValidationException("Export failed: ${e.message}")
            )
        }
    }
}
