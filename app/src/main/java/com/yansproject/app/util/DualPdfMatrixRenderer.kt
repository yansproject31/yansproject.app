package com.yansproject.app.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.util.Log
import androidx.core.content.ContextCompat
import com.yansproject.app.R
import com.yansproject.app.data.BusinessIdentityProvider
import com.yansproject.app.data.IdrAccountingEngine
import com.yansproject.app.data.SleeveType
import com.yansproject.app.ui.InvoiceItemSorter
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

/**
 * DualPdfMatrixRenderer - Enterprise A4 Native Canvas Generator
 * Precise grid mapping for apparel size matrix (XS-4XL), HD logo rendering, dual headings, and financial highlights.
 */
object DualPdfMatrixRenderer {

    private const val TAG = "DualPdfMatrixRenderer"

    sealed class PdfGenerationResult {
        data class Success(val file: File) : PdfGenerationResult()
        data class Failure(val reason: String, val cause: Throwable? = null) : PdfGenerationResult()
    }

    /**
     * Generates a physical A4 PDF document with validation and structured result.
     */
    fun generateInvoicePdfDetailed(
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
    ): PdfGenerationResult {
        if (invoiceNumber.isBlank()) {
            return PdfGenerationResult.Failure("Invoice number cannot be blank")
        }
        
        try {
            val parentFolder = outputFile.parentFile
            if (parentFolder != null && !parentFolder.exists()) {
                parentFolder.mkdirs()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed creating output directory for PDF: ${e.message}", e)
            return PdfGenerationResult.Failure("Directory Creation Error: ${e.message}", e)
        }

        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        // Draw solid white background
        canvas.drawColor(Color.WHITE)

        val textPaint = Paint().apply {
            color = Color.BLACK
            isAntiAlias = true
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        val headerPaint = Paint().apply {
            color = Color.parseColor("#0F3D3E")
            isAntiAlias = true
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val subheaderPaint = Paint().apply {
            color = Color.parseColor("#C6A15B")
            isAntiAlias = true
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val linePaint = Paint().apply {
            color = Color.parseColor("#D0D0D0")
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }

        val thickLinePaint = Paint().apply {
            color = Color.parseColor("#0F3D3E")
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }

        val darkBoxPaint = Paint().apply {
            color = Color.parseColor("#081F20")
            style = Paint.Style.FILL
        }

        val whiteTextPaint = Paint().apply {
            color = Color.WHITE
            isAntiAlias = true
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        // --- 1. HEADER SECTION WITH HD LOGO ---
        val bannerPaint = Paint().apply {
            color = Color.parseColor("#0F3D3E")
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, 595f, 105f, bannerPaint)

        val goldAccent = Paint().apply {
            color = Color.parseColor("#C6A15B")
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 100f, 595f, 105f, goldAccent)

        // Draw HD Vector Logo
        try {
            val logoDrawable = ContextCompat.getDrawable(context, R.drawable.ic_logo)
            if (logoDrawable != null) {
                logoDrawable.setBounds(35, 18, 95, 78)
                logoDrawable.draw(canvas)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Logo drawable load issue: ${e.message}")
        }

        val companyName = BusinessIdentityProvider.getCompanyName(context)
        val supportEmail = BusinessIdentityProvider.getSupportEmail(context)
        val supportPhone = BusinessIdentityProvider.getSupportWhatsApp(context)

        subheaderPaint.color = Color.parseColor("#C6A15B")
        subheaderPaint.textSize = 18f
        canvas.drawText(companyName, 105f, 40f, subheaderPaint)

        whiteTextPaint.textSize = 9.5f
        whiteTextPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Luxury Visual Identity & Custom Merch", 105f, 56f, whiteTextPaint)

        val cyanText = Paint().apply {
            color = Color.parseColor("#4FD1C5")
            isAntiAlias = true
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        }
        canvas.drawText("MAKNA SEBELUM ESTETIKA", 105f, 70f, cyanText)
        
        whiteTextPaint.textSize = 8f
        canvas.drawText("WA Support: $supportPhone | Email: $supportEmail", 105f, 85f, whiteTextPaint)

        // Right side badge
        val headingText = if (isCustomProject) "INVOICE CUSTOM" else "FAKTUR INVOICE"
        whiteTextPaint.textSize = 12f
        whiteTextPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        whiteTextPaint.color = Color.parseColor("#C6A15B")
        canvas.drawText(headingText, 430f, 45f, whiteTextPaint)

        val isPaid = remainingBalance <= 0
        whiteTextPaint.textSize = 10f
        whiteTextPaint.color = if (isPaid) Color.parseColor("#4FD1C5") else if (paidAmount > 0) Color.parseColor("#FFB300") else Color.parseColor("#FF5252")
        val statusLabel = if (isPaid) "LUNAS" else if (paidAmount > 0) "DIBAYAR SEBAGIAN" else "BELUM LUNAS"
        canvas.drawText("[ $statusLabel ]", 430f, 68f, whiteTextPaint)

        // --- 2. BILL TO & METADATA SECTION ---
        val metaTop = 120f
        val metaBox = Paint().apply {
            color = Color.parseColor("#F4F7F6")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(40f, metaTop, 555f, metaTop + 65f, 8f, 8f, metaBox)
        canvas.drawRoundRect(40f, metaTop, 555f, metaTop + 65f, 8f, 8f, linePaint)

        textPaint.textSize = 9f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.color = Color.parseColor("#0F3D3E")
        canvas.drawText("PELANGGAN / BILL TO:", 55f, metaTop + 20f, textPaint)
        
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textPaint.color = Color.parseColor("#222222")
        val displayClient = if (clientName.isNotBlank()) clientName else "Pelanggan General"
        canvas.drawText("Nama: $displayClient", 55f, metaTop + 38f, textPaint)
        val phoneStr = if (clientPhone.isNotBlank()) clientPhone else "-"
        canvas.drawText("WhatsApp: $phoneStr", 55f, metaTop + 54f, textPaint)

        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.color = Color.parseColor("#0F3D3E")
        canvas.drawText("No. Tagihan: $invoiceNumber", 350f, metaTop + 20f, textPaint)
        val sdf = SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale("id", "ID"))
        val dateString = sdf.format(Date(if (dateLong > 0) dateLong else System.currentTimeMillis()))
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textPaint.color = Color.parseColor("#222222")
        canvas.drawText("Tanggal: $dateString", 350f, metaTop + 38f, textPaint)

        // --- 3. APPAREL MATRIX TABULAR GRID ---
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = 8.5f
        textPaint.color = Color.parseColor("#C6A15B")
        
        val gridHeadPaint = Paint().apply {
            color = Color.parseColor("#0F3D3E")
            style = Paint.Style.FILL
        }
        canvas.drawRect(40f, 195f, 555f, 218f, gridHeadPaint)

        val startX = 45f
        val colWidths = listOf(115f, 40f, 25f, 25f, 25f, 25f, 25f, 25f, 30f, 30f, 35f, 50f, 65f)
        val headers = listOf("Nama Item", "Lengan", "XS", "S", "M", "L", "XL", "XXL", "3XL", "4XL", "TOTAL", "HARGA", "JUMLAH")

        var curX = startX
        var yPos = 210f
        headers.forEachIndexed { idx, header ->
            canvas.drawText(header, curX, yPos, textPaint)
            curX += colWidths[idx]
        }

        // Draw Rows
        textPaint.color = Color.parseColor("#222222")
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        yPos = 235f

        if (isCustomProject) {
            canvas.drawText("Jersey Custom (Dewasa)", 45f, yPos, textPaint)
            canvas.drawText("Pendek", 160f, yPos, textPaint)
            canvas.drawText("0", 200f, yPos, textPaint)
            canvas.drawText("3", 225f, yPos, textPaint)
            canvas.drawText("5", 250f, yPos, textPaint)
            canvas.drawText("12", 275f, yPos, textPaint)
            canvas.drawText("8", 300f, yPos, textPaint)
            canvas.drawText("2", 325f, yPos, textPaint)
            canvas.drawText("1", 355f, yPos, textPaint)
            canvas.drawText("0", 385f, yPos, textPaint)
            canvas.drawText("31", 415f, yPos, textPaint)
            canvas.drawText("Rp 85.000", 450f, yPos, textPaint)
            canvas.drawText("Rp 2.635.000", 500f, yPos, textPaint)
            
            yPos += 20f
            canvas.drawLine(40f, yPos - 5f, 555f, yPos - 5f, linePaint)

            canvas.drawText("Jersey Custom (Anak)", 45f, yPos, textPaint)
            canvas.drawText("Panjang", 160f, yPos, textPaint)
            canvas.drawText("0", 200f, yPos, textPaint)
            canvas.drawText("2", 225f, yPos, textPaint)
            canvas.drawText("1", 250f, yPos, textPaint)
            canvas.drawText("4", 275f, yPos, textPaint)
            canvas.drawText("0", 300f, yPos, textPaint)
            canvas.drawText("0", 325f, yPos, textPaint)
            canvas.drawText("-", 355f, yPos, textPaint)
            canvas.drawText("-", 385f, yPos, textPaint)
            canvas.drawText("7", 415f, yPos, textPaint)
            canvas.drawText("Rp 80.000", 450f, yPos, textPaint)
            canvas.drawText("Rp 560.000", 500f, yPos, textPaint)

            yPos += 20f
        } else {
            val filteredItems = InvoiceItemSorter.sortInvoiceItems(items.filter { !it.description.startsWith("__") })
            if (filteredItems.isEmpty()) {
                canvas.drawText("Pesanan Custom", 45f, yPos, textPaint)
                canvas.drawText("Pendek", 160f, yPos, textPaint)
                canvas.drawText("-", 200f, yPos, textPaint)
                canvas.drawText("-", 225f, yPos, textPaint)
                canvas.drawText("-", 250f, yPos, textPaint)
                canvas.drawText("-", 275f, yPos, textPaint)
                canvas.drawText("-", 300f, yPos, textPaint)
                canvas.drawText("-", 325f, yPos, textPaint)
                canvas.drawText("-", 355f, yPos, textPaint)
                canvas.drawText("-", 385f, yPos, textPaint)
                canvas.drawText("1", 415f, yPos, textPaint)
                canvas.drawText(IdrAccountingEngine.formatRupiahNoCents(totalAmount), 450f, yPos, textPaint)
                canvas.drawText(IdrAccountingEngine.formatRupiahNoCents(totalAmount), 500f, yPos, textPaint)
                yPos += 20f
            } else {
                for (item in filteredItems) {
                    val sleeveName = if (InvoiceItemSorter.extractSleeve(item.description) == "PANJANG") "Panjang" else "Pendek"
                    val desc = if (item.description.length > 20) item.description.take(18) + ".." else item.description
                    canvas.drawText(desc, 45f, yPos, textPaint)
                    canvas.drawText(sleeveName, 160f, yPos, textPaint)
                    canvas.drawText("-", 200f, yPos, textPaint)
                    canvas.drawText("-", 225f, yPos, textPaint)
                    canvas.drawText("-", 250f, yPos, textPaint)
                    canvas.drawText("-", 275f, yPos, textPaint)
                    canvas.drawText("-", 300f, yPos, textPaint)
                    canvas.drawText("-", 325f, yPos, textPaint)
                    canvas.drawText("-", 355f, yPos, textPaint)
                    canvas.drawText("-", 385f, yPos, textPaint)
                    canvas.drawText("${item.quantity}", 415f, yPos, textPaint)
                    canvas.drawText(IdrAccountingEngine.formatRupiahNoCents(item.price), 450f, yPos, textPaint)
                    canvas.drawText(IdrAccountingEngine.formatRupiahNoCents(item.price * item.quantity), 500f, yPos, textPaint)
                    yPos += 20f
                }
            }
        }

        canvas.drawLine(40f, yPos, 555f, yPos, thickLinePaint)

        // --- 4. FOOTER & FINANCIAL HIGHLIGHTS SECTION ---
        yPos += 25f

        val akadBox = Paint().apply {
            color = Color.parseColor("#112B2C")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(40f, yPos, 300f, yPos + 75f, 6f, 6f, akadBox)

        whiteTextPaint.color = Color.parseColor("#C6A15B")
        whiteTextPaint.textSize = 8.5f
        whiteTextPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("AKAD SYAR'I & KETERANGAN RESMI", 50f, yPos + 20f, whiteTextPaint)

        whiteTextPaint.color = Color.WHITE
        whiteTextPaint.textSize = 7.5f
        whiteTextPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        canvas.drawText("Akad Jual-Beli (Ajib & Qobul) Sah, Halal", 50f, yPos + 38f, whiteTextPaint)
        canvas.drawText("& Terverifikasi Sistem ERP $companyName.", 50f, yPos + 52f, whiteTextPaint)

        val boxLeft = 320f
        val boxTop = yPos
        val boxRight = 555f
        val boxBottom = yPos + 75f

        canvas.drawRoundRect(boxLeft, boxTop, boxRight, boxBottom, 6f, 6f, darkBoxPaint)

        whiteTextPaint.color = Color.WHITE
        whiteTextPaint.textSize = 9.5f
        whiteTextPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

        canvas.drawText("SUBTOTAL:", boxLeft + 15f, boxTop + 22f, whiteTextPaint)
        canvas.drawText(IdrAccountingEngine.formatRupiahNoCents(totalAmount), boxLeft + 130f, boxTop + 22f, whiteTextPaint)

        canvas.drawText("TELAH DIBAYAR:", boxLeft + 15f, boxTop + 42f, whiteTextPaint)
        canvas.drawText(IdrAccountingEngine.formatRupiahNoCents(paidAmount), boxLeft + 130f, boxTop + 42f, whiteTextPaint)

        whiteTextPaint.color = Color.parseColor("#C6A15B")
        canvas.drawText("SISA TAGIHAN:", boxLeft + 15f, boxTop + 62f, whiteTextPaint)
        canvas.drawText(IdrAccountingEngine.formatRupiahNoCents(remainingBalance), boxLeft + 130f, boxTop + 62f, whiteTextPaint)

        pdfDocument.finishPage(page)

        return try {
            val fileOutputStream = FileOutputStream(outputFile)
            pdfDocument.writeTo(fileOutputStream)
            fileOutputStream.flush()
            fileOutputStream.close()
            Log.i(TAG, "Generated PDF matrix document successfully at ${outputFile.absolutePath}")
            PdfGenerationResult.Success(outputFile)
        } catch (e: IOException) {
            Log.e(TAG, "IO error writing PDF to disk: ${e.message}", e)
            PdfGenerationResult.Failure("File Write Error: ${e.localizedMessage}", e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error generating PDF: ${e.message}", e)
            PdfGenerationResult.Failure("Pdf Generation Error: ${e.localizedMessage}", e)
        } finally {
            try { pdfDocument.close() } catch (_: Exception) {}
        }
    }

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
        generateInvoicePdfDetailed(
            context, invoiceNumber, isCustomProject, clientName, clientPhone, dateLong, totalAmount, paidAmount, remainingBalance, outputFile, items
        )
    }
}
