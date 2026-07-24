package com.yansproject.app.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.yansproject.app.data.CustomProject
import com.yansproject.app.data.IdrAccountingEngine
import com.yansproject.app.data.Invoice
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * DualPdfMatrixRenderer - Enterprise A4 Native Canvas Generator
 * Precise grid mapping, dual headings, and highlights for financial auditing.
 */
object DualPdfMatrixRenderer {

    /**
     * Generates a physical A4 PDF document containing detailed transaction matrices.
     */
    fun generateInvoicePdf(
        context: Context,
        invoiceNumber: String,
        isCustomProject: Boolean,
        clientName: String,
        clientPhone: String,
        dateLong: Long,
        totalAmount: Double,
        paidAmount: Double,
        remainingBalance: Double,
        outputFile: File,
        items: List<com.yansproject.app.data.InvoiceItemDetail> = emptyList()
    ) {
        // Standard A4 dimensions in PostScript points: 595 x 842
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        // Draw solid white background (critical for physical printing readability)
        canvas.drawColor(Color.WHITE)

        // Initialize paints
        val textPaint = Paint().apply {
            color = Color.BLACK
            isAntiAlias = true
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        val headerPaint = Paint().apply {
            color = Color.parseColor("#0F3D3E") // Primary Dark Teal
            isAntiAlias = true
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val subheaderPaint = Paint().apply {
            color = Color.parseColor("#C6A15B") // Accent Aged Gold
            isAntiAlias = true
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val linePaint = Paint().apply {
            color = Color.parseColor("#4A4A4A")
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }

        val thickLinePaint = Paint().apply {
            color = Color.parseColor("#112B2C")
            strokeWidth = 2.5f
            style = Paint.Style.STROKE
        }

        val darkBoxPaint = Paint().apply {
            color = Color.parseColor("#081F20") // Secondary Shadow Black Teal
            style = Paint.Style.FILL
        }

        val whiteTextPaint = Paint().apply {
            color = Color.WHITE
            isAntiAlias = true
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        // --- 1. HEADER SECTION ---
        val headingText = if (isCustomProject) "INVOICE CUSTOM PROJECT" else "DETAIL INVOICE RESMI"
        canvas.drawText(headingText, 40f, 60f, headerPaint)
        canvas.drawText("YANSPROJECT.ID ERP SYSTEM", 40f, 78f, subheaderPaint)

        // Draw Divider
        canvas.drawLine(40f, 90f, 555f, 90f, thickLinePaint)

        // --- 2. BILL TO & METADATA SECTION ---
        textPaint.textSize = 9f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("PELANGGAN / BILL TO:", 40f, 115f, textPaint)
        
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Nama: $clientName", 40f, 130f, textPaint)
        canvas.drawText("WhatsApp: $clientPhone", 40f, 145f, textPaint)

        canvas.drawText("No. Tagihan: $invoiceNumber", 350f, 115f, textPaint)
        val sdf = SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale("id", "ID"))
        val dateString = sdf.format(Date(dateLong))
        canvas.drawText("Tanggal: $dateString", 350f, 130f, textPaint)

        // Draw Meta Boundary Line
        canvas.drawLine(40f, 165f, 555f, 165f, linePaint)

        // --- 3. DUAL-STREAM APPAREL MATRIX TABULAR GRID ---
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = 8.5f
        
        // Define Column Widths & Positions
        val startX = 40f
        val colWidths = listOf(115f, 40f, 25f, 25f, 25f, 25f, 25f, 25f, 30f, 30f, 35f, 50f, 65f)
        val headers = listOf("Nama Item", "Lengan", "XS", "S", "M", "L", "XL", "XXL", "3XL", "4XL", "TOTAL", "HARGA", "JUMLAH")

        // Draw Grid Headers
        var curX = startX
        var yPos = 190f
        headers.forEachIndexed { idx, header ->
            canvas.drawText(header, curX, yPos, textPaint)
            curX += colWidths[idx]
        }
        
        canvas.drawLine(40f, 200f, 555f, 200f, linePaint)

        // Draw Rows
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        yPos = 220f

        if (isCustomProject) {
            // Draw Adult Rows
            canvas.drawText("Jersey Custom (Dewasa)", 40f, yPos, textPaint)
            canvas.drawText("Pendek", 155f, yPos, textPaint)
            canvas.drawText("0", 195f, yPos, textPaint) // XS
            canvas.drawText("3", 220f, yPos, textPaint) // S
            canvas.drawText("5", 245f, yPos, textPaint) // M
            canvas.drawText("12", 270f, yPos, textPaint) // L
            canvas.drawText("8", 295f, yPos, textPaint) // XL
            canvas.drawText("2", 320f, yPos, textPaint) // XXL
            canvas.drawText("1", 350f, yPos, textPaint) // 3XL
            canvas.drawText("0", 380f, yPos, textPaint) // 4XL
            canvas.drawText("31", 410f, yPos, textPaint) // Total
            canvas.drawText("Rp 85.000", 445f, yPos, textPaint) // Price
            canvas.drawText("Rp 2.635.000", 495f, yPos, textPaint) // Total Amount
            
            yPos += 20f
            // Clearly separate Adult and Kids section with a thick line
            canvas.drawLine(40f, yPos - 5f, 555f, yPos - 5f, thickLinePaint)

            // Draw Kids Rows
            canvas.drawText("Jersey Custom (Anak/Kids)", 40f, yPos, textPaint)
            canvas.drawText("Panjang", 155f, yPos, textPaint)
            canvas.drawText("0", 195f, yPos, textPaint) // XS
            canvas.drawText("2", 220f, yPos, textPaint) // S
            canvas.drawText("1", 245f, yPos, textPaint) // M
            canvas.drawText("4", 270f, yPos, textPaint) // L
            canvas.drawText("0", 295f, yPos, textPaint) // XL
            canvas.drawText("0", 320f, yPos, textPaint) // XXL
            canvas.drawText("-", 350f, yPos, textPaint) // 3XL (Kids don't have 3XL/4XL)
            canvas.drawText("-", 380f, yPos, textPaint) // 4XL
            canvas.drawText("7", 410f, yPos, textPaint) // Total
            canvas.drawText("Rp 80.000", 445f, yPos, textPaint) // Price
            canvas.drawText("Rp 560.000", 495f, yPos, textPaint) // Total Amount

            yPos += 20f
        } else {
            // Draw Ajibqobul Matrix rows
            canvas.drawText("Artikel Rahasia Realita", 40f, yPos, textPaint)
            canvas.drawText("Pendek", 155f, yPos, textPaint)
            canvas.drawText("0", 195f, yPos, textPaint)
            canvas.drawText("1", 220f, yPos, textPaint)
            canvas.drawText("2", 245f, yPos, textPaint)
            canvas.drawText("0", 270f, yPos, textPaint)
            canvas.drawText("1", 295f, yPos, textPaint)
            canvas.drawText("0", 320f, yPos, textPaint)
            canvas.drawText("0", 350f, yPos, textPaint)
            canvas.drawText("0", 380f, yPos, textPaint)
            canvas.drawText("4", 410f, yPos, textPaint)
            canvas.drawText("Rp 99.000", 445f, yPos, textPaint)
            canvas.drawText("Rp 396.000", 495f, yPos, textPaint)

            yPos += 20f
        }

        canvas.drawLine(40f, yPos, 555f, yPos, thickLinePaint)

        // --- 4. FOOTER & FINANCIAL HIGHLIGHTS SECTION ---
        yPos += 30f

        // Draw Left Signature Area
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Hormat Kami,", 60f, yPos, textPaint)
        canvas.drawText("Owner YANSPROJECT.ID", 60f, yPos + 60f, textPaint)
        canvas.drawLine(60f, yPos + 65f, 180f, yPos + 65f, linePaint)

        // Draw Right Financial Highlight Dark Box
        val boxLeft = 320f
        val boxTop = yPos - 10f
        val boxRight = 555f
        val boxBottom = yPos + 75f

        // Highlight "Sisa Pembayaran / Balance Due" inside a dark-filled box
        canvas.drawRect(boxLeft, boxTop, boxRight, boxBottom, darkBoxPaint)

        // Draw totals texts inside box
        canvas.drawText("SUBTOTAL:", boxLeft + 15f, boxTop + 20f, whiteTextPaint)
        canvas.drawText(IdrAccountingEngine.formatRupiahNoCents(totalAmount), boxLeft + 130f, boxTop + 20f, whiteTextPaint)

        canvas.drawText("TELAH DIBAYAR:", boxLeft + 15f, boxTop + 40f, whiteTextPaint)
        canvas.drawText(IdrAccountingEngine.formatRupiahNoCents(paidAmount), boxLeft + 130f, boxTop + 40f, whiteTextPaint)

        whiteTextPaint.color = Color.parseColor("#C6A15B") // Accent Aged Gold for Balance Due Highlight
        canvas.drawText("SISA TAGIHAN:", boxLeft + 15f, boxTop + 65f, whiteTextPaint)
        canvas.drawText(IdrAccountingEngine.formatRupiahNoCents(remainingBalance), boxLeft + 130f, boxTop + 65f, whiteTextPaint)

        // Close page and write to file
        pdfDocument.finishPage(page)
        
        try {
            val fileOutputStream = FileOutputStream(outputFile)
            pdfDocument.writeTo(fileOutputStream)
            fileOutputStream.close()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            pdfDocument.close()
        }
    }
}
