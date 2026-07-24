package com.yansproject.app.data

import android.content.Context
import java.io.File
import java.io.FileWriter

class LocalReportExporter(private val context: Context) {

    /**
     * Exporteert a list of structured data rows as a local CSV file saved within the app's secure cache directory.
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
        return try {
            val cacheDir = context.cacheDir ?: return null
            val file = File(cacheDir, fileName)
            
            if (file.exists()) {
                file.delete()
            }
            file.createNewFile()

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
            file
        } catch (e: Exception) {
            null
        }
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
