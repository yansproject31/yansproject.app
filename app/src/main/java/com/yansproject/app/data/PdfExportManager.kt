package com.yansproject.app.data

import android.graphics.pdf.PdfDocument
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

class PdfExportException(message: String) : Exception(message)

data class PdfExportResult(
    val file: File,
    val pageCount: Int,
    val fileSize: Long,
    val checksum: String
)

/**
 * PdfExportManager: Validates PDF document page count, header structure, file size, and checksum.
 * Prevents exposing corrupt or zero-page PDF files as successful exports.
 */
class PdfExportManager private constructor() {

    private val TAG = "PdfExportManager"
    private val exportManager = ExportManager.getInstance()

    companion object {
        @Volatile
        private var INSTANCE: PdfExportManager? = null

        fun getInstance(): PdfExportManager {
            return INSTANCE ?: synchronized(this) {
                val instance = PdfExportManager()
                INSTANCE = instance
                instance
            }
        }
    }

    /**
     * Generates a PDF document and validates its structure, page count, header signature, and checksum.
     */
    fun createAndValidatePdf(
        targetFile: File,
        expectedPageCount: Int,
        title: String,
        author: String = "YANSPROJECT.ID ERP",
        renderBlock: (PdfDocument) -> Unit
    ): Result<PdfExportResult> {
        if (expectedPageCount <= 0) {
            return Result.failure(PdfExportException("PDF expected page count must be greater than zero."))
        }

        val pdfDocument = PdfDocument()

        try {
            // Render pages into PDF document
            renderBlock(pdfDocument)

            val actualPages = pdfDocument.pages.size
            if (actualPages < expectedPageCount) {
                pdfDocument.close()
                return Result.failure(
                    PdfExportException("PDF rendering page mismatch. Rendered $actualPages pages, expected $expectedPageCount.")
                )
            }

            // Write PDF to target file via ExportManager stream safety pipeline
            val exportResult = exportManager.exportToFile(targetFile) { outputStream ->
                pdfDocument.writeTo(outputStream)
            }

            pdfDocument.close()

            if (exportResult.isFailure) {
                return Result.failure(
                    exportResult.exceptionOrNull() ?: PdfExportException("PDF file stream write failed.")
                )
            }

            val file = exportResult.getOrThrow()

            // 1. Validate File Size (minimum valid PDF header/trailer overhead is ~128 bytes)
            if (file.length() < 128) {
                file.delete()
                return Result.failure(PdfExportException("Corrupt PDF file: size is under minimum valid header threshold (${file.length()} bytes)."))
            }

            // 2. Validate PDF Magic Header Bytes ("%PDF-")
            if (!verifyPdfHeader(file)) {
                file.delete()
                return Result.failure(PdfExportException("Corrupt PDF file: magic header bytes '%PDF-' not found."))
            }

            // 3. Calculate Checksum
            val checksum = calculateFileChecksum(file)
            if (checksum.isBlank()) {
                file.delete()
                return Result.failure(PdfExportException("Corrupt PDF file: checksum calculation failed."))
            }

            Log.i(TAG, "PDF Export validated successfully: '${file.name}' ($actualPages pages, ${file.length()} bytes, SHA-256: ${checksum.take(8)}...)")
            return Result.success(
                PdfExportResult(
                    file = file,
                    pageCount = actualPages,
                    fileSize = file.length(),
                    checksum = checksum
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "PDF Export failed: ${e.message}", e)
            try {
                pdfDocument.close()
            } catch (ignored: Exception) {}

            if (targetFile.exists()) {
                targetFile.delete()
            }

            return Result.failure(
                if (e is PdfExportException) e else PdfExportException("PDF export execution failed: ${e.message}")
            )
        }
    }

    private fun verifyPdfHeader(file: File): Boolean {
        return try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(5)
                val read = fis.read(header)
                read == 5 &&
                        header[0] == '%'.code.toByte() &&
                        header[1] == 'P'.code.toByte() &&
                        header[2] == 'D'.code.toByte() &&
                        header[3] == 'F'.code.toByte() &&
                        header[4] == '-'.code.toByte()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking PDF header signature: ${e.message}", e)
            false
        }
    }

    private fun calculateFileChecksum(file: File): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error computing file checksum: ${e.message}", e)
            ""
        }
    }
}
