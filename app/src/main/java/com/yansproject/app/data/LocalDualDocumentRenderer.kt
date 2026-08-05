package com.yansproject.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.util.Log
import androidx.core.content.ContextCompat
import com.yansproject.app.R
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
     * Integrates Color DNA palettes, HD Vector Logo, and Akad Syar'i contract.
     */
    fun renderInvoiceToBitmap(
        projectName: String,
        clientName: String,
        amount: Double,
        remaining: Double,
        status: String,
        context: Context? = null,
        isForPrintInverted: Boolean = false
    ): Bitmap {
        val width = 800
        val height = 1200
        val bitmap = BitmapMemoryRecycler.createSafeBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Color Palette Auto-Inversion Selection
        val bgColor = if (isForPrintInverted) 0xFFFFFFFF.toInt() else 0xFF0A0F0D.toInt() // White vs Deep Carbon Black
        val cardColor = if (isForPrintInverted) 0xFFF0F2F1.toInt() else 0xFF121A16.toInt() // Soft gray vs Emerald Slate Green
        val textColor = if (isForPrintInverted) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() // Solid black vs Pure White
        val accentColor = if (isForPrintInverted) 0xFF9E7E38.toInt() else 0xFFC6A15B.toInt() // Darker Gold vs Luxury Gold
        val borderColor = if (isForPrintInverted) 0xFF4A4A4A.toInt() else 0xFF2A3A32.toInt() // Charcoal vs Muted Silver

        // Draw Canvas Base Background
        canvas.drawColor(bgColor)

        val paint = Paint().apply {
            isAntiAlias = true
            color = textColor
            textSize = 24f
        }

        // Draw Header Banner
        paint.color = 0xFF0F3D3E.toInt() // Primary Dark Teal
        canvas.drawRect(0f, 0f, width.toFloat(), 130f, paint)

        paint.color = accentColor
        canvas.drawRect(0f, 125f, width.toFloat(), 130f, paint)

        // Draw HD Vector Logo if context available
        if (context != null) {
            try {
                val logo = ContextCompat.getDrawable(context, R.drawable.ic_logo)
                if (logo != null) {
                    logo.setBounds(40, 20, 110, 90)
                    logo.draw(canvas)
                }
            } catch (e: Exception) {
                // Fallback drawing ignore
            }
        }

        // Header Title
        val companyTitle = if (context != null) BusinessIdentityProvider.getCompanyName(context) else BusinessIdentityProvider.DEFAULT_COMPANY_NAME
        val tagline = BusinessIdentityProvider.DEFAULT_STORE_TAGLINE
        val supportWa = if (context != null) BusinessIdentityProvider.getSupportWhatsApp(context) else BusinessIdentityProvider.DEFAULT_SUPPORT_WHATSAPP

        paint.color = accentColor
        paint.textSize = 30f
        paint.isFakeBoldText = true
        canvas.drawText(companyTitle, 130f, 55f, paint)

        // Subtitle Branding
        paint.textSize = 15f
        paint.color = 0xFFFFFFFF.toInt()
        paint.isFakeBoldText = false
        canvas.drawText(tagline, 130f, 78f, paint)

        paint.textSize = 13f
        paint.color = 0xFF4FD1C5.toInt()
        canvas.drawText("MAKNA SEBELUM ESTETIKA • CS: $supportWa", 130f, 100f, paint)

        // Draw Card Container Area
        paint.color = cardColor
        canvas.drawRoundRect(40f, 160f, (width - 40).toFloat(), 600f, 20f, 20f, paint)

        // Card Border
        paint.color = borderColor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        canvas.drawRoundRect(40f, 160f, (width - 40).toFloat(), 600f, 20f, 20f, paint)

        // Write Invoice Details inside Card
        paint.style = Paint.Style.FILL
        paint.color = textColor
        paint.textSize = 22f
        paint.isFakeBoldText = true
        canvas.drawText("DETAIL PROJECT CUSTOM & TRANSAKSI", 70f, 210f, paint)

        paint.isFakeBoldText = false
        paint.textSize = 20f
        canvas.drawText("Nama Project: $projectName", 70f, 260f, paint)
        canvas.drawText("Nama Klien  : $clientName", 70f, 310f, paint)
        canvas.drawText("Status Kerja : $status", 70f, 360f, paint)

        // Pricing Info inside Container
        paint.color = accentColor
        paint.isFakeBoldText = true
        canvas.drawText("Total Tagihan: ${IdrAccountingEngine.formatRupiah(amount)}", 70f, 440f, paint)
        
        paint.color = if (remaining > 0) 0xFFFF5252.toInt() else 0xFF4FD1C5.toInt()
        canvas.drawText("Sisa Tagihan : ${IdrAccountingEngine.formatRupiah(remaining)}", 70f, 500f, paint)

        // Draw Akad Syar'i Footer Box
        paint.color = 0xFF112B2C.toInt()
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(40f, 630f, (width - 40).toFloat(), 730f, 16f, 16f, paint)

        paint.color = accentColor
        paint.textSize = 17f
        paint.isFakeBoldText = true
        canvas.drawText("AKAD SYAR'I & KETERANGAN RESMI", 60f, 665f, paint)

        paint.color = 0xFFFFFFFF.toInt()
        paint.textSize = 15f
        paint.isFakeBoldText = false
        canvas.drawText("Akad Jual-Beli (Ajib & Qobul) Sah, Halal & Terverifikasi Sistem ERP YANSPROJECT.ID.", 60f, 700f, paint)

        return bitmap
    }

    /**
     * Exports a digital screen invoice to standard local storage directory.
     */
    fun saveToPicturesGallery(context: Context, projectName: String, bitmap: Bitmap): File? {
        return try {
            var picturesDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "YansProjectID")
            if (!picturesDir.exists() && !picturesDir.mkdirs()) {
                picturesDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
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
            Log.e(TAG, "Failed to save digital invoice into pictures gallery, attempting app internal files", e)
            try {
                val fallbackDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
                val file = File(fallbackDir, "INV_CUSTOM_${System.currentTimeMillis()}.png")
                val outputStream = FileOutputStream(file)
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                outputStream.flush()
                outputStream.close()
                file
            } catch (fallbackEx: Exception) {
                Log.e(TAG, "Fallback save also failed", fallbackEx)
                null
            }
        }
    }

    /**
     * Generates a physical PDF document using android.graphics.pdf.PdfDocument.
     */
    fun exportToPdfDownloads(context: Context, projectName: String, clientName: String, amount: Double, remaining: Double, status: String): File? {
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
            context = context,
            isForPrintInverted = true
        )

        val destRect = android.graphics.Rect(30, 40, 565, 802)
        canvas.drawBitmap(invertedBitmap, null, destRect, null)

        document.finishPage(page)

        return try {
            var downloadDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "YansProjectID")
            if (!downloadDir.exists() && !downloadDir.mkdirs()) {
                downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
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
            try {
                val fallbackDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
                val file = File(fallbackDir, "INV_PRINT_${System.currentTimeMillis()}.pdf")
                val outputStream = FileOutputStream(file)
                document.writeTo(outputStream)
                outputStream.close()
                document.close()
                file
            } catch (fallbackEx: Exception) {
                document.close()
                null
            }
        } finally {
            BitmapMemoryRecycler.recycle(invertedBitmap)
        }
    }

    /**
     * Cache sharing attachment generator.
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
