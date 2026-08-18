package com.yansproject.app.data

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.IOException

class LocalReportExporter(private val context: Context) {

    companion object {
        private const val TAG = "LocalReportExporter"
    }

    /**
     * Export result class providing structured failure reporting.
     */
    sealed class ExportResult {
        data class Success(val file: File, val rowCount: Int) : ExportResult()
        data class Failure(val reason: String, val cause: Throwable? = null) : ExportResult()
    }

    /**
     * Exports a list of structured data rows as a local CSV file saved within the app's secure cache directory.
     * @param fileName Name of the resulting CSV file (e.g., "Invoice_Report_2026.csv")
     * @param headers List of column header names
     * @param data List of maps where each map represents a row with key-value pairs matching the headers
     * @return File handle pointing to the newly generated file, or null if generation fails
     */
    fun exportToCsv(
        fileName: String,
        headers: List<String>,
        data: List<Map<String, String>>
    ): File? {
        val result = exportToCsvDetailed(fileName, headers, data)
        return when (result) {
            is ExportResult.Success -> result.file
            is ExportResult.Failure -> {
                Log.e(TAG, "exportToCsv failed: ${result.reason}", result.cause)
                null
            }
        }
    }

    /**
     * Detailed exporter returning structured ExportResult with full validation.
     */
    fun exportToCsvDetailed(
        fileName: String,
        headers: List<String>,
        data: List<Map<String, String>>
    ): ExportResult {
        if (fileName.isBlank()) {
            return ExportResult.Failure("Filename cannot be blank")
        }
        if (headers.isEmpty()) {
            return ExportResult.Failure("Header list cannot be empty")
        }

        return try {
            val exportDir = File(context.cacheDir, "share")
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }

            val sanitizedName = sanitizeCsvFilename(fileName)
            val file = File(exportDir, sanitizedName)

            // Verify canonical path to prevent path traversal outside app sandbox
            val canonicalExportDir = exportDir.canonicalPath
            val canonicalTargetFile = file.canonicalPath

            if (!canonicalTargetFile.startsWith(canonicalExportDir)) {
                return ExportResult.Failure(
                    "Security Violation: Target path escapes application sandbox: $fileName",
                    SecurityException("Path traversal attempt detected")
                )
            }

            if (file.exists()) {
                file.delete()
            }
            if (!file.createNewFile()) {
                return ExportResult.Failure("Failed to create file at ${file.absolutePath}")
            }

            FileWriter(file).use { writer ->
                // 1. Write headers
                val headerRow = headers.joinToString(separator = ",", postfix = "\n") { escapeCsvField(it) }
                writer.write(headerRow)

                // 2. Write rows
                for (row in data) {
                    val rowString = headers.joinToString(separator = ",", postfix = "\n") { header ->
                        val value = row[header] ?: ""
                        escapeCsvField(value)
                    }
                    writer.write(rowString)
                }
                writer.flush()
            }

            Log.i(TAG, "CSV Export created successfully at ${file.absolutePath} (${data.size} rows)")
            ExportResult.Success(file, data.size)
        } catch (e: IOException) {
            Log.e(TAG, "IO error while exporting CSV: ${e.message}", e)
            ExportResult.Failure("IO Error writing file: ${e.localizedMessage}", e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error while exporting CSV: ${e.message}", e)
            ExportResult.Failure("Unexpected Error: ${e.localizedMessage}", e)
        }
    }

    /**
     * Sanitizes CSV filenames to prevent path traversal vulnerabilities.
     */
    private fun sanitizeCsvFilename(rawName: String): String {
        val baseName = rawName
            .replace("\\", "/")
            .split("/")
            .last()
            .replace("..", "")
            .replace("\u0000", "")
            .replace(Regex("[^a-zA-Z0-9._\\-]"), "_")
            .trim()
        val withExt = if (baseName.endsWith(".csv", ignoreCase = true)) baseName else "$baseName.csv"
        return if (withExt == ".csv" || withExt.isBlank()) "export_${System.currentTimeMillis()}.csv" else withExt
    }

    /**
     * Escapes individual text fields to ensure valid CSV syntax structure under standard RFC-4180 specifications.
     */
    private fun escapeCsvField(field: String): String {
        val containsComma = field.contains(",")
        val containsQuote = field.contains("\"")
        val containsNewline = field.contains("\n") || field.contains("\r")

        return if (containsComma || containsQuote || containsNewline) {
            val escaped = field.replace("\"", "\"\"")
            "\"$escaped\""
        } else {
            field
        }
    }
}
