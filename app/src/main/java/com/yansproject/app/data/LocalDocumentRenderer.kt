package com.yansproject.app.data

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import com.yansproject.app.R
import com.yansproject.app.ui.FormatUtils
import com.yansproject.app.ui.InvoiceItemSorter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * LocalDocumentRenderer: Master PDF & HD PNG Invoice Rendering Engine for YANSPROJECT.ID.
 * Implements high-resolution vector logo rendering, exact Color DNA, structured matrix layouts,
 * Akad Syar'i contracts, and dual verification stamps.
 */
class LocalDocumentRenderer(private val context: Context) {

    private fun drawHdLogo(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float) {
        try {
            val drawable = ContextCompat.getDrawable(context, R.drawable.ic_logo)
            if (drawable != null) {
                drawable.setBounds(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
                drawable.draw(canvas)
            } else {
                drawFallbackEmblem(canvas, left, top, right, bottom)
            }
        } catch (e: Exception) {
            drawFallbackEmblem(canvas, left, top, right, bottom)
        }
    }

    private fun drawFallbackEmblem(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float) {
        val paint = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#C6A15B")
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawRoundRect(left, top, right, bottom, 12f, 12f, paint)
    }

    /**
     * Renders an invoice into a native PDF A4 document (595x842 postscript points) with YANSPROJECT.ID Brand DNA.
     */
    fun generateInvoicePdf(
        invoice: OperationalInvoice,
        items: List<InvoiceItemDetail>
    ): File? {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        try {
            val paint = Paint().apply { isAntiAlias = true }

            // Clean White Background for Physical Print Efficiency
            canvas.drawColor(Color.WHITE)

            // 1. Header & Title Banner (Primary Dark Teal + Aged Gold Accent)
            paint.color = Color.parseColor("#0F3D3E") // Primary Dark Teal
            canvas.drawRect(0f, 0f, 595f, 115f, paint)

            // Gold accent strip at bottom of header
            paint.color = Color.parseColor("#C6A15B") // Aged Gold
            canvas.drawRect(0f, 110f, 595f, 115f, paint)

            // Draw HD Vector Logo
            drawHdLogo(canvas, 35f, 20f, 95f, 80f)

            // Logo Title & Taglines
            paint.color = Color.parseColor("#C6A15B") // Accent Gold
            paint.textSize = 20f
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            canvas.drawText("YANSPROJECT.ID", 105f, 42f, paint)

            paint.color = Color.WHITE
            paint.textSize = 9.5f
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            canvas.drawText("Luxury Visual Identity & Custom Merch", 105f, 58f, paint)

            paint.color = Color.parseColor("#4FD1C5") // Soft Cyan
            paint.textSize = 8.5f
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
            canvas.drawText("MAKNA SEBELUM ESTETIKA", 105f, 72f, paint)

            val wa = BusinessIdentityProvider.getSupportWhatsApp(context)
            val email = BusinessIdentityProvider.getSupportEmail(context)
            paint.color = Color.parseColor("#E0E0E0")
            paint.textSize = 8f
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            canvas.drawText("WA Support: $wa | Email: $email", 105f, 88f, paint)

            // Right side Header Badge: FAKTUR INVOICE RESMI
            paint.color = Color.parseColor("#112B2C")
            canvas.drawRoundRect(410f, 20f, 555f, 90f, 10f, 10f, paint)

            paint.color = Color.parseColor("#C6A15B")
            paint.textSize = 11f
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            canvas.drawText("FAKTUR INVOICE", 425f, 42f, paint)

            val remaining = (invoice.totalAmount - invoice.paidAmount - invoice.discount).coerceAtLeast(0.0)
            val isPaid = remaining <= 0
            paint.color = if (isPaid) Color.parseColor("#2E7D32") else if (invoice.paidAmount > 0) Color.parseColor("#EF6C00") else Color.parseColor("#C62828")
            paint.textSize = 9.5f
            val statusText = if (isPaid) "[ LUNAS ]" else if (invoice.paidAmount > 0) "[ DIBAYAR SEBAGIAN ]" else "[ BELUM LUNAS ]"
            canvas.drawText(statusText, 425f, 65f, paint)

            // 2. Subtle Security Watermark (A4)
            canvas.save()
            canvas.rotate(-30f, 297.5f, 480f)
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#0F3D3E")
            paint.alpha = 16 // ~6% subtle opacity
            paint.textSize = 40f
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            canvas.drawText("YANSPROJECT.ID", 130f, 470f, paint)

            paint.textSize = 12f
            paint.color = Color.parseColor("#C6A15B")
            paint.alpha = 20
            canvas.drawText("OFFICIAL E-INVOICE • BY YANSPROJECT.ID", 115f, 492f, paint)
            canvas.restore()

            // 3. Invoice Meta Details & Client Card Box
            val cardTop = 130f
            val cardBottom = 210f
            paint.color = Color.parseColor("#F4F7F6") // Soft surface gray
            canvas.drawRoundRect(40f, cardTop, 555f, cardBottom, 8f, 8f, paint)

            paint.color = Color.parseColor("#0F3D3E")
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            canvas.drawRoundRect(40f, cardTop, 555f, cardBottom, 8f, 8f, paint)

            paint.style = Paint.Style.FILL

            // Left Meta Info
            paint.color = Color.parseColor("#0F3D3E")
            paint.textSize = 9.5f
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            canvas.drawText("NO. INVOICE  :", 55f, cardTop + 22f, paint)
            canvas.drawText("TANGGAL      :", 55f, cardTop + 42f, paint)

            paint.color = Color.parseColor("#222222")
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            canvas.drawText(invoice.invoiceNumber, 145f, cardTop + 22f, paint)
            canvas.drawText(formatDate(invoice.issueDate), 145f, cardTop + 42f, paint)

            // Right Client Info
            paint.color = Color.parseColor("#0F3D3E")
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            canvas.drawText("DITUJUKAN KEPADA:", 320f, cardTop + 22f, paint)

            paint.color = Color.parseColor("#111111")
            paint.textSize = 11f
            canvas.drawText(invoice.clientName, 320f, cardTop + 42f, paint)

            paint.color = Color.parseColor("#555555")
            paint.textSize = 9.5f
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            val phoneText = if (invoice.clientPhone.isNotBlank()) "HP/WA: ${invoice.clientPhone}" else "Pelanggan Terverifikasi YANSPROJECT.ID"
            canvas.drawText(phoneText, 320f, cardTop + 62f, paint)

            // 4. Table Header
            val tableHeadY = 230f
            paint.color = Color.parseColor("#0F3D3E")
            canvas.drawRect(40f, tableHeadY, 555f, tableHeadY + 24f, paint)

            paint.color = Color.parseColor("#C6A15B")
            paint.textSize = 9.5f
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            canvas.drawText("NO", 50f, tableHeadY + 16f, paint)
            canvas.drawText("DESKRIPSI PESANAN / ARTIKEL", 80f, tableHeadY + 16f, paint)
            canvas.drawText("QTY", 330f, tableHeadY + 16f, paint)
            canvas.drawText("HARGA (RP)", 385f, tableHeadY + 16f, paint)
            canvas.drawText("SUBTOTAL (RP)", 470f, tableHeadY + 16f, paint)

            // 5. Drawing Items List
            var currentY = tableHeadY + 40f
            val filteredItems = InvoiceItemSorter.sortInvoiceItems(items.filter { !it.description.startsWith("__") })

            if (filteredItems.isEmpty()) {
                paint.color = Color.parseColor("#333333")
                paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                canvas.drawText("1", 50f, currentY, paint)
                canvas.drawText("Pesanan Custom Project - ${invoice.clientName}", 80f, currentY, paint)
                canvas.drawText("1", 335f, currentY, paint)
                canvas.drawText(formatCompactPrice(invoice.totalAmount), 385f, currentY, paint)
                canvas.drawText(formatCompactPrice(invoice.totalAmount), 470f, currentY, paint)
                currentY += 25f
            } else {
                filteredItems.forEachIndexed { idx, item ->
                    paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                    paint.color = Color.parseColor("#222222")
                    canvas.drawText("${idx + 1}", 50f, currentY, paint)

                    var desc = item.description
                    if (desc.length > 40) desc = desc.substring(0, 37) + "..."
                    canvas.drawText(desc, 80f, currentY, paint)

                    canvas.drawText("${item.quantity}", 335f, currentY, paint)
                    canvas.drawText(formatCompactPrice(item.price), 385f, currentY, paint)

                    val subtotal = item.price * item.quantity
                    paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    canvas.drawText(formatCompactPrice(subtotal), 470f, currentY, paint)

                    currentY += 10f
                    paint.color = Color.parseColor("#E0E0E0")
                    paint.strokeWidth = 0.8f
                    canvas.drawLine(40f, currentY, 555f, currentY, paint)
                    currentY += 22f
                }
            }

            // 6. Financial Summary Section
            currentY += 10f
            val summaryBoxTop = currentY
            paint.color = Color.parseColor("#F8F9FA")
            canvas.drawRoundRect(290f, summaryBoxTop, 555f, summaryBoxTop + 105f, 8f, 8f, paint)

            paint.color = Color.parseColor("#0F3D3E")
            paint.strokeWidth = 1f
            paint.style = Paint.Style.STROKE
            canvas.drawRoundRect(290f, summaryBoxTop, 555f, summaryBoxTop + 105f, 8f, 8f, paint)

            paint.style = Paint.Style.FILL
            paint.textSize = 9.5f

            // Subtotal
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            paint.color = Color.parseColor("#333333")
            canvas.drawText("Total Tagihan :", 305f, summaryBoxTop + 22f, paint)
            canvas.drawText("Rp " + formatCompactPrice(invoice.totalAmount), 435f, summaryBoxTop + 22f, paint)

            // Diskon
            if (invoice.discount > 0) {
                paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                paint.color = Color.parseColor("#888888")
                canvas.drawText("Potongan Diskon :", 305f, summaryBoxTop + 40f, paint)
                canvas.drawText("- Rp " + formatCompactPrice(invoice.discount), 435f, summaryBoxTop + 40f, paint)
            }

            // Uang Muka / Terbayar
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            paint.color = Color.parseColor("#2E7D32")
            canvas.drawText("Jumlah Terbayar :", 305f, summaryBoxTop + 58f, paint)
            canvas.drawText("Rp " + formatCompactPrice(invoice.paidAmount), 435f, summaryBoxTop + 58f, paint)

            // Sisa Piutang / Pelunasan
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            paint.color = if (remaining > 0) Color.parseColor("#C62828") else Color.parseColor("#2E7D32")
            canvas.drawText("SISA TAGIHAN  :", 305f, summaryBoxTop + 82f, paint)
            canvas.drawText("Rp " + formatCompactPrice(remaining), 435f, summaryBoxTop + 82f, paint)

            // 7. Akad Syar'i & Legal Notice Footer Box
            var footerY = summaryBoxTop + 125f
            paint.color = Color.parseColor("#112B2C")
            canvas.drawRoundRect(40f, footerY, 555f, footerY + 42f, 6f, 6f, paint)

            paint.color = Color.parseColor("#C6A15B")
            paint.textSize = 8.5f
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            canvas.drawText("AKAD SYAR'I & KETERANGAN RESMI", 50f, footerY + 16f, paint)

            paint.color = Color.WHITE
            paint.textSize = 7.5f
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
            canvas.drawText("Akad Jual-Beli (Ajib & Qobul) Sah, Halal & Terverifikasi Sistem ERP YANSPROJECT.ID.", 50f, footerY + 30f, paint)

            // 8. Signatures & Verification Stamp
            footerY += 60f
            paint.color = Color.parseColor("#333333")
            paint.textSize = 9.5f
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            canvas.drawText("Admin YANSPROJECT.ID", 80f, footerY, paint)
            canvas.drawText("Pemesan / Klien", 420f, footerY, paint)

            paint.color = Color.GRAY
            paint.strokeWidth = 1f
            canvas.drawLine(60f, footerY + 45f, 200f, footerY + 45f, paint)
            canvas.drawLine(390f, footerY + 45f, 510f, footerY + 45f, paint)

            paint.color = Color.parseColor("#555555")
            paint.textSize = 8.5f
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            canvas.drawText("( Sistem Terverifikasi )", 80f, footerY + 58f, paint)
            canvas.drawText("( ${invoice.clientName} )", 410f, footerY + 58f, paint)

            pdfDocument.finishPage(page)

            // Save PDF locally to device Downloads
            val safeNum = invoice.invoiceNumber.replace("/", "_").replace("\\", "_")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val file = File(downloadsDir, "Invoice_${safeNum}.pdf")
            val outputStream = FileOutputStream(file)
            pdfDocument.writeTo(outputStream)
            outputStream.flush()
            outputStream.close()

            return file
        } catch (e: Exception) {
            Log.e("LocalDocumentRenderer", "PDF writing error", e)
            return null
        } finally {
            pdfDocument.close()
        }
    }

    /**
     * Renders a high-resolution Luxury PNG bitmap image of the invoice (1080x1920) in memory.
     */
    fun generateInvoicePngBitmap(
        invoice: OperationalInvoice,
        items: List<InvoiceItemDetail>
    ): Bitmap? {
        val width = 1080
        val height = 1920
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        try {
            val paint = Paint().apply { isAntiAlias = true }

            // Background: Deep Shadow Carbon Black
            canvas.drawColor(Color.parseColor("#0A0E10"))

            // Decorative Top Bar (Dark Teal + Aged Gold Strip)
            paint.color = Color.parseColor("#0F3D3E")
            canvas.drawRect(0f, 0f, width.toFloat(), 230f, paint)

            paint.color = Color.parseColor("#C6A15B")
            canvas.drawRect(0f, 222f, width.toFloat(), 230f, paint)

            // Draw HD Vector Logo at Top Left
            drawHdLogo(canvas, 60f, 40f, 180f, 160f)

            // Brand Logo & Title Typography
            paint.color = Color.parseColor("#C6A15B")
            paint.textSize = 44f
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            canvas.drawText("YANSPROJECT.ID", 200f, 95f, paint)

            paint.color = Color.WHITE
            paint.textSize = 20f
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            canvas.drawText("Luxury Visual Identity & Custom Merch", 200f, 135f, paint)

            paint.color = Color.parseColor("#4FD1C5")
            paint.textSize = 17f
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
            canvas.drawText("MAKNA SEBELUM ESTETIKA", 200f, 170f, paint)

            val waBig = BusinessIdentityProvider.getSupportWhatsApp(context)
            val emailBig = BusinessIdentityProvider.getSupportEmail(context)
            paint.color = Color.parseColor("#A0A0A0")
            paint.textSize = 15f
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            canvas.drawText("WA Support: $waBig | Email: $emailBig", 200f, 202f, paint)

            // Main Card Surface Frame
            val cardLeft = 45f
            val cardTop = 260f
            val cardRight = width - 45f
            val cardBottom = height - 100f

            paint.color = Color.parseColor("#163536")
            canvas.drawRoundRect(cardLeft, cardTop, cardRight, cardBottom, 28f, 28f, paint)

            paint.color = Color.parseColor("#C6A15B")
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            canvas.drawRoundRect(cardLeft, cardTop, cardRight, cardBottom, 28f, 28f, paint)

            paint.style = Paint.Style.FILL

            // Security Watermark (PNG HD)
            canvas.save()
            canvas.rotate(-30f, 540f, 1000f)
            paint.color = Color.parseColor("#C6A15B")
            paint.alpha = 18 // ~7% subtle opacity
            paint.textSize = 70f
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            canvas.drawText("YANSPROJECT.ID", 220f, 980f, paint)

            paint.textSize = 22f
            paint.color = Color.parseColor("#4FD1C5")
            paint.alpha = 26 // ~10% subtle opacity
            canvas.drawText("OFFICIAL E-INVOICE • BY YANSPROJECT.ID", 180f, 1020f, paint)
            canvas.restore()

            // Header Inside Card
            var curY = cardTop + 70f

            paint.color = Color.parseColor("#C6A15B")
            paint.textSize = 34f
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            canvas.drawText("FAKTUR INVOICE RESMI", cardLeft + 40f, curY, paint)

            // Status Pill
            val remaining = (invoice.totalAmount - invoice.paidAmount - invoice.discount).coerceAtLeast(0.0)
            val isPaid = remaining <= 0
            val statusStr = if (isPaid) "LUNAS" else if (invoice.paidAmount > 0) "DIBAYAR SEBAGIAN" else "BELUM LUNAS"
            val statusBg = if (isPaid) Color.parseColor("#1B4D3E") else if (invoice.paidAmount > 0) Color.parseColor("#5A3A10") else Color.parseColor("#4A1818")
            val statusColor = if (isPaid) Color.parseColor("#4FD1C5") else if (invoice.paidAmount > 0) Color.parseColor("#FFC107") else Color.parseColor("#FF5252")

            paint.color = statusBg
            canvas.drawRoundRect(cardRight - 340f, curY - 42f, cardRight - 40f, curY + 15f, 20f, 20f, paint)

            paint.color = statusColor
            paint.textSize = 20f
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            canvas.drawText(statusStr, cardRight - 320f, curY - 5f, paint)

            // Meta Info Grid Box
            curY += 60f
            paint.color = Color.parseColor("#112B2C")
            canvas.drawRoundRect(cardLeft + 30f, curY, cardRight - 30f, curY + 160f, 20f, 20f, paint)

            paint.color = Color.parseColor("#A0A0A0")
            paint.textSize = 19f
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            canvas.drawText("NO. INVOICE", cardLeft + 60f, curY + 50f, paint)
            canvas.drawText("TANGGAL", cardLeft + 60f, curY + 95f, paint)

            paint.color = Color.WHITE
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            canvas.drawText(": ${invoice.invoiceNumber}", cardLeft + 230f, curY + 50f, paint)
            canvas.drawText(": ${formatDate(invoice.issueDate)}", cardLeft + 230f, curY + 95f, paint)

            // Client Info Column
            canvas.drawText("PELANGGAN", cardLeft + 540f, curY + 50f, paint)
            paint.color = Color.parseColor("#C6A15B")
            canvas.drawText(invoice.clientName, cardLeft + 540f, curY + 95f, paint)

            paint.color = Color.parseColor("#A0A0A0")
            paint.textSize = 18f
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            val phoneText = if (invoice.clientPhone.isNotBlank()) invoice.clientPhone else "-"
            canvas.drawText("HP/WA: $phoneText", cardLeft + 540f, curY + 135f, paint)

            // Items Table Section
            curY += 230f
            paint.color = Color.parseColor("#0F3D3E")
            canvas.drawRect(cardLeft + 30f, curY, cardRight - 30f, curY + 50f, paint)

            paint.color = Color.parseColor("#C6A15B")
            paint.textSize = 19f
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            canvas.drawText("DESKRIPSI PESANAN / ARTIKEL", cardLeft + 50f, curY + 34f, paint)
            canvas.drawText("QTY", cardLeft + 540f, curY + 34f, paint)
            canvas.drawText("HARGA", cardLeft + 640f, curY + 34f, paint)
            canvas.drawText("SUBTOTAL", cardLeft + 800f, curY + 34f, paint)

            curY += 80f
            val filteredItems = InvoiceItemSorter.sortInvoiceItems(items.filter { !it.description.startsWith("__") })

            if (filteredItems.isEmpty()) {
                paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                paint.color = Color.WHITE
                paint.textSize = 20f
                canvas.drawText("Custom Project Order - ${invoice.clientName}", cardLeft + 50f, curY, paint)
                canvas.drawText("1 Pcs", cardLeft + 540f, curY, paint)
                canvas.drawText(formatCompactPrice(invoice.totalAmount), cardLeft + 640f, curY, paint)
                paint.color = Color.parseColor("#4FD1C5")
                paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                canvas.drawText(formatCompactPrice(invoice.totalAmount), cardLeft + 800f, curY, paint)
                curY += 65f
            } else {
                filteredItems.take(12).forEachIndexed { idx, item ->
                    paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                    paint.color = Color.WHITE
                    paint.textSize = 20f

                    var desc = item.description
                    if (desc.length > 30) desc = desc.substring(0, 27) + "..."
                    canvas.drawText(desc, cardLeft + 50f, curY, paint)

                    canvas.drawText("${item.quantity} Pcs", cardLeft + 540f, curY, paint)
                    canvas.drawText(formatCompactPrice(item.price), cardLeft + 640f, curY, paint)

                    val subtotal = item.price * item.quantity
                    paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    paint.color = Color.parseColor("#4FD1C5")
                    canvas.drawText(formatCompactPrice(subtotal), cardLeft + 800f, curY, paint)

                    curY += 20f
                    paint.color = Color.parseColor("#2A4D4E")
                    paint.strokeWidth = 1f
                    canvas.drawLine(cardLeft + 30f, curY, cardRight - 30f, curY, paint)
                    curY += 45f
                }
            }

            // Totals Summary Box
            curY = cardBottom - 420f
            paint.color = Color.parseColor("#0F3D3E")
            canvas.drawRoundRect(cardLeft + 30f, curY, cardRight - 30f, curY + 230f, 20f, 20f, paint)

            paint.color = Color.parseColor("#C6A15B")
            paint.textSize = 21f
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            canvas.drawText("TOTAL BELANJA", cardLeft + 60f, curY + 50f, paint)
            canvas.drawText("Rp " + formatCompactPrice(invoice.totalAmount), cardRight - 380f, curY + 50f, paint)

            if (invoice.discount > 0) {
                paint.color = Color.parseColor("#A0A0A0")
                paint.textSize = 19f
                paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                canvas.drawText("Potongan Diskon", cardLeft + 60f, curY + 95f, paint)
                canvas.drawText("- Rp " + formatCompactPrice(invoice.discount), cardRight - 380f, curY + 95f, paint)
            }

            paint.color = Color.parseColor("#4FD1C5")
            paint.textSize = 21f
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            canvas.drawText("Total Terbayar", cardLeft + 60f, curY + 140f, paint)
            canvas.drawText("Rp " + formatCompactPrice(invoice.paidAmount), cardRight - 380f, curY + 140f, paint)

            paint.color = if (remaining > 0) Color.parseColor("#FF5252") else Color.parseColor("#4FD1C5")
            paint.textSize = 23f
            canvas.drawText("SISA TAGIHAN", cardLeft + 60f, curY + 190f, paint)
            canvas.drawText("Rp " + formatCompactPrice(remaining), cardRight - 380f, curY + 190f, paint)

            // Akad Syar'i Box Footer
            val footerY = cardBottom - 160f
            paint.color = Color.parseColor("#112B2C")
            canvas.drawRoundRect(cardLeft + 30f, footerY, cardRight - 30f, footerY + 110f, 16f, 16f, paint)

            paint.color = Color.parseColor("#C6A15B")
            paint.textSize = 18f
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            canvas.drawText("AKAD SYAR'I & KETERANGAN RESMI", cardLeft + 50f, footerY + 40f, paint)

            paint.color = Color.WHITE
            paint.textSize = 16f
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
            canvas.drawText("Akad Jual-Beli (Ajib & Qobul) Sah, Halal & Terverifikasi Sistem ERP YANSPROJECT.ID.", cardLeft + 50f, footerY + 75f, paint)

            return bitmap
        } catch (e: Exception) {
            Log.e("LocalDocumentRenderer", "Bitmap rendering failed", e)
            return null
        }
    }

    /**
     * Generates a high-resolution Luxury PNG bitmap image of the invoice (1080x1920)
     * and saves it to the device Gallery Pictures folder.
     */
    fun generateInvoicePng(
        invoice: OperationalInvoice,
        items: List<InvoiceItemDetail>
    ): File? {
        val bitmap = generateInvoicePngBitmap(invoice, items) ?: return null

        try {
            // Save to Pictures gallery
            val picturesDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "YansERP")
            if (!picturesDir.exists()) picturesDir.mkdirs()

            val file = File(picturesDir, "Invoice_${invoice.invoiceNumber}.png")
            val fos = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            fos.flush()
            fos.close()

            // Also insert into MediaStore for instant Gallery availability
            saveBitmapToGallery(bitmap, "Invoice_${invoice.invoiceNumber}")

            return file
        } catch (e: Exception) {
            Log.e("LocalDocumentRenderer", "PNG saving failed", e)
            return null
        }
    }

    /**
     * Saves a captured Bitmap directly into the device's external gallery storage using MediaStore API.
     */
    fun saveBitmapToGallery(bitmap: Bitmap, title: String): Boolean {
        val resolver = context.contentResolver
        val imageDetails = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$title.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/YansERP")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        var imageUri: android.net.Uri? = null
        var outputStream: OutputStream? = null

        return try {
            val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            imageUri = resolver.insert(collection, imageDetails)
            if (imageUri != null) {
                outputStream = resolver.openOutputStream(imageUri)
                if (outputStream != null) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                    outputStream.flush()
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    imageDetails.clear()
                    imageDetails.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(imageUri, imageDetails, null, null)
                }
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("LocalDocumentRenderer", "Bitmap saving failed", e)
            false
        } finally {
            outputStream?.close()
        }
    }

    private fun formatDate(timestamp: Long): String {
        return SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID")).format(Date(timestamp))
    }

    private fun formatCompactPrice(price: Double): String {
        return String.format(Locale.US, "%,.0f", price).replace(",", ".")
    }
}
