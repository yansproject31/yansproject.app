package com.yansproject.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * LocalDualDocumentRenderer: Manages high-performance rendering and exporting of
 * invoice documents into PNG, PDF, and temporary WhatsApp attachments, implementing
 * the Color DNA Auto-Inversion logic between digital screens and physical printouts.
 */
object LocalDualDocumentRenderer {

    private const val TAG = "LocalDualDocumentRenderer"

    /**
     * Converts raw data into an offline Bitmap image.
     * Integrates Color DNA palettes.
     */
    fun renderInvoiceToBitmap(
        projectName: String,
        clientName: String,
        amount: Double,
        remaining: Double,
        status: String,
        isForPrintInverted: Boolean = false
    ): Bitmap {
        // Create safe canvas using our Bitmap memory recycler pool
        val width = 800
        val height = 1200
        val bitmap = BitmapMemoryRecycler.createSafeBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Color Palette Auto-Inversion Selection
        val bgColor = if (isForPrintInverted) 0xFFFFFFFF.toInt() else 0xFF0A0F0D.toInt() // White vs Deep Carbon Black
        val cardColor = if (isForPrintInverted) 0xFFF0F2F1.toInt() else 0xFF121A16.toInt() // Soft gray vs Emerald Slate Green
        val textColor = if (isForPrintInverted) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() // Solid black vs Pure White
        val accentColor = if (isForPrintInverted) 0xFF9E7E38.toInt() else 0xFFD4AF37.toInt() // Darker Gold vs Luxury Gold
        val borderColor = if (isForPrintInverted) 0xFF4A4A4A.toInt() else 0xFF2A3A32.toInt() // Charcoal vs Muted Silver

        // Draw Canvas Base Background
        canvas.drawColor(bgColor)

        val paint = Paint().apply {
            isAntiAlias = true
            color = textColor
            textSize = 24f
        }

        // Draw Header Border / Accents
        paint.color = accentColor
        canvas.drawRect(0f, 0f, width.toFloat(), 15f, paint)

        // Header Title
        paint.textSize = 34f
        paint.isFakeBoldText = true
        canvas.drawText("YANSPROJECT.ID ERP INVOICE", 50f, 80f, paint)

        // Subtitle Branding
        paint.textSize = 20f
        paint.color = if (isForPrintInverted) 0xFF555555.toInt() else 0xFF8A9A92.toInt()
        paint.isFakeBoldText = false
        canvas.drawText("Sistem Manajemen Transaksi & Custom Project Terintegrasi", 50f, 115f, paint)

        // Draw Card Container Area
        paint.color = cardColor
        canvas.drawRoundRect(40f, 150f, (width - 40).toFloat(), 550f, 16f, 16f, paint)

        // Card Border
        paint.color = borderColor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        canvas.drawRoundRect(40f, 150f, (width - 40).toFloat(), 550f, 16f, 16f, paint)

        // Write Invoice Details inside Card
        paint.style = Paint.Style.FILL
        paint.color = textColor
        paint.textSize = 24f
        paint.isFakeBoldText = true
        canvas.drawText("DETAIL PROJECT CUSTOM", 70f, 200f, paint)

        paint.isFakeBoldText = false
        paint.textSize = 22f
        canvas.drawText("Nama Project: $projectName", 70f, 250f, paint)
        canvas.drawText("Nama Klien  : $clientName", 70f, 300f, paint)
        canvas.drawText("Status Kerja : $status", 70f, 350f, paint)

        // Pricing Info inside Container
        paint.color = accentColor
        paint.isFakeBoldText = true
        canvas.drawText("Total Tagihan: ${IdrAccountingEngine.formatRupiah(amount)}", 70f, 430f, paint)
        canvas.drawText("Sisa Tagihan : ${IdrAccountingEngine.formatRupiah(remaining)}", 70f, 485f, paint)

        // Draw Bottom Footer and Terms of Service
        paint.color = textColor
        paint.textSize = 18f
        paint.isFakeBoldText = false
        canvas.drawText("Sebab Akad Jual-Beli (Ajib & Qobul) Sah & Tercatat.", 50f, 650f, paint)
        canvas.drawText("Dokumen digital ini di-generate secara otomatis.", 50f, 690f, paint)

        return bitmap
    }

    /**
     * Exports a digital screen invoice to standard local storage directory.
     * Path: Internal Storage/Pictures/YansProjectID/
     */
    fun saveToPicturesGallery(context: Context, projectName: String, bitmap: Bitmap): File? {
        return try {
            val picturesDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "YansProjectID")
            if (!picturesDir.exists()) {
                picturesDir.mkdirs()
            }
            val fileName = "INV_CUSTOM_${System.currentTimeMillis()}.png"
            val file = File(picturesDir, fileName)
            val outputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            outputStream.flush()
            outputStream.close()
            Log.d(TAG, "Successfully exported digital invoice PNG to: ${file.absolutePath}")
            file
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save digital invoice into pictures gallery", e)
            null
        }
    }

    /**
     * Generates a physical PDF document using android.graphics.pdf.PdfDocument.
     * Auto-Inverts colors for ink-saving pure white background layouts.
     * Path: Internal Storage/Download/YansProjectID/
     */
    fun exportToPdfDownloads(projectName: String, clientName: String, amount: Double, remaining: Double, status: String): File? {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 Size in points
        val page = document.startPage(pageInfo)

        val canvas = page.canvas

        // Generate auto-inverted high-contrast bitmap for ink saving
        val invertedBitmap = renderInvoiceToBitmap(
            projectName = projectName,
            clientName = clientName,
            amount = amount,
            remaining = remaining,
            status = status,
            isForPrintInverted = true
        )

        // Scale and draw to PDF Canvas
        val destRect = android.graphics.Rect(30, 40, 565, 802)
        canvas.drawBitmap(invertedBitmap, null, destRect, null)

        document.finishPage(page)

        return try {
            val downloadDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "YansProjectID")
            if (!downloadDir.exists()) {
                downloadDir.mkdirs()
            }
            val fileName = "INV_PRINT_${System.currentTimeMillis()}.pdf"
            val file = File(downloadDir, fileName)
            val outputStream = FileOutputStream(file)
            document.writeTo(outputStream)
            outputStream.close()
            document.close()
            Log.d(TAG, "Successfully written inverted physical invoice PDF to: ${file.absolutePath}")
            file
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write PDF file to downloads", e)
            document.close()
            null
        } finally {
            BitmapMemoryRecycler.recycle(invertedBitmap)
        }
    }

    /**
     * Cache sharing attachment generator. Guarantees no public gallery pollution.
     * Path: App Internal Safe Cache Area: Android/data/com.yansproject.app/cache/shared_invoices/
     */
    fun createTemporaryCacheShareFile(context: Context, bitmap: Bitmap): File? {
        return try {
            val cacheShareDir = File(context.cacheDir, "shared_invoices")
            if (!cacheShareDir.exists()) {
                cacheShareDir.mkdirs()
            }
            val tempFile = File(cacheShareDir, "temp_share_invoice_${System.currentTimeMillis()}.png")
            val outputStream = FileOutputStream(tempFile)
            bitmap.compress(Bitmap.CompressFormat.PNG, 95, outputStream)
            outputStream.flush()
            outputStream.close()
            Log.d(TAG, "Temporary cache share file built at: ${tempFile.absolutePath}")
            tempFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write temporary share invoice file", e)
            null
        }
    }
}
