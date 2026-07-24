package com.yansproject.app.data

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

class LocalDocumentRenderer(private val context: Context) {

    /**
     * Renders an invoice into a native PDF A4 document (595x842 postscript points) with YANSPROJECT.ID Premium Luxury DNA.
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
            val paint = Paint()
            paint.isAntiAlias = true

            // 1. Header & Title Banner (Dark Teal + Gold Accent)
            paint.color = Color.parseColor("#0F3D3E") // Primary Dark Teal
            canvas.drawRect(0f, 0f, 595f, 125f, paint)

            // Gold accent strip at bottom of header
            paint.color = Color.parseColor("#C6A15B") // Aged Gold
            canvas.drawRect(0f, 120f, 595f, 125f, paint)

            // Logo & Title
            paint.color = Color.parseColor("#C6A15B") // Accent Gold
            paint.textSize = 24f
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            canvas.drawText("YANSPROJECT.ID", 40f, 48f, paint)

            paint.color = Color.WHITE
            paint.textSize = 11f
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            canvas.drawText("MAKNA SEBELUM ESTETIKA", 40f, 70f, paint)

            paint.color = Color.parseColor("#4FD1C5") // Soft Cyan
            paint.textSize = 9.5f
            canvas.drawText("Email: yansart31@gmail.com | WhatsApp Support: +62 87777-3988-13", 40f, 88f, paint)
            canvas.drawText("Sistem Informasi ERP & Manajemen Operasional Terintegrasi", 40f, 104f, paint)

            // Right side Header Badge: FAKTUR INVOICE
            paint.color = Color.parseColor("#112B2C")
            canvas.drawRoundRect(410f, 25f, 555f, 95f, 12f, 12f, paint)

            paint.color = Color.parseColor("#C6A15B")
            paint.textSize = 12f
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            canvas.drawText("FAKTUR INVOICE", 425f, 50f, paint)

            val remaining = (invoice.totalAmount - invoice.paidAmount - invoice.discount).coerceAtLeast(0.0)
            val isPaid = remaining <= 0
            paint.color = if (isPaid) Color.parseColor("#4FD1C5") else Color.parseColor("#E53935")
            paint.textSize = 10f
            val statusText = if (isPaid) "LUNAS" else if (invoice.paidAmount > 0) "DIBAYAR SEBAGIAN" else "BELUM LUNAS"
            canvas.drawText("[$statusText]", 425f, 72f, paint)

            // 2. Invoice Meta Details & Client Box
            val metaY = 155f
            paint.color = Color.parseColor("#163536") // Dark Card
            canvas.drawRoundRect(40f, 140f, 555f, 225f, 10f, 10f, paint)

            // Subtle Security Watermark (A4)
            canvas.save()
            canvas.rotate(-30f, 297.5f, 480f)
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#0F3D3E")
            paint.alpha = 18 // ~7% subtle opacity
            paint.textSize = 42f
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            canvas.drawText("YANSPROJECT.ID", 130f, 470f, paint)

            paint.textSize = 12f
            paint.color = Color.parseColor("#C6A15B")
            paint.alpha = 24 // ~9% opacity
            canvas.drawText("OFFICIAL E-INVOICE • BY YANSPROJECT.ID", 115f, 492f, paint)
            canvas.restore()

            // Left Meta
            paint.color = Color.parseColor("#C6A15B")
            paint.textSize = 10f
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            canvas.drawText("NO. INVOICE  :", 55f, 162f, paint)
            canvas.drawText("TANGGAL      :", 55f, 182f, paint)
            canvas.drawText("JATUH TEMPO  :", 55f, 202f, paint)

            paint.color = Color.WHITE
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            canvas.drawText(invoice.invoiceNumber, 145f, 162f, paint)
            canvas.drawText(formatDate(invoice.issueDate), 145f, 182f, paint)
            canvas.drawText(formatDate(invoice.dueDate), 145f, 202f, paint)

            // Right Client
            paint.color = Color.parseColor("#C6A15B")
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            canvas.drawText("DITUJUKAN KEPADA:", 340f, 162f, paint)

            paint.color = Color.WHITE
            paint.textSize = 12f
            canvas.drawText(invoice.clientName, 340f, 182f, paint)

            paint.color = Color.parseColor("#A0A0A0")
            paint.textSize = 10f
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            val phoneText = if (invoice.clientPhone.isNotBlank()) "HP: ${invoice.clientPhone}" else "Klien Terverifikasi YANSPROJECT.ID"
            canvas.drawText(phoneText, 340f, 202f, paint)

            // 3. Table Header
            val tableHeadY = 245f
            paint.color = Color.parseColor("#0F3D3E")
            canvas.drawRect(40f, tableHeadY, 555f, tableHeadY + 25f, paint)

            paint.color = Color.parseColor("#C6A15B")
            paint.textSize = 10f
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            canvas.drawText("NO", 50f, tableHeadY + 17f, paint)
            canvas.drawText("DESKRIPSI PESANAN", 80f, tableHeadY + 17f, paint)
            canvas.drawText("QTY", 330f, tableHeadY + 17f, paint)
            canvas.drawText("HARGA (RP)", 385f, tableHeadY + 17f, paint)
            canvas.drawText("SUBTOTAL (RP)", 470f, tableHeadY + 17f, paint)

            // 4. Drawing Items List
            var currentY = tableHeadY + 45f
            paint.color = Color.BLACK
            paint.textSize = 10f

            com.yansproject.app.ui.InvoiceItemSorter.sortInvoiceItems(items.filter { !it.description.startsWith("__") }).forEachIndexed { idx, item ->
                paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                paint.color = Color.parseColor("#222222")
                canvas.drawText("${idx + 1}", 50f, currentY, paint)

                // Truncate desc if too long
                var desc = item.description
                if (desc.length > 42) desc = desc.substring(0, 39) + "..."
                canvas.drawText(desc, 80f, currentY, paint)

                canvas.drawText("${item.quantity}", 335f, currentY, paint)
                canvas.drawText(formatCompactPrice(item.price), 385f, currentY, paint)

                val subtotal = item.price * item.quantity
                paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                canvas.drawText(formatCompactPrice(subtotal), 470f, currentY, paint)

                currentY += 12f
                paint.color = Color.parseColor("#E0E0E0")
                paint.strokeWidth = 0.8f
                canvas.drawLine(40f, currentY, 555f, currentY, paint)
                currentY += 22f
            }

            // 5. Financial Summary Section
            currentY += 10f
            val summaryBoxTop = currentY
            paint.color = Color.parseColor("#F8F9FA")
            canvas.drawRoundRect(300f, summaryBoxTop, 555f, summaryBoxTop + 95f, 8f, 8f, paint)

            paint.color = Color.parseColor("#0F3D3E")
            paint.strokeWidth = 1f
            paint.style = Paint.Style.STROKE
            canvas.drawRoundRect(300f, summaryBoxTop, 555f, summaryBoxTop + 95f, 8f, 8f, paint)

            paint.style = Paint.Style.FILL
            paint.textSize = 10f

            // Total Tagihan
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            paint.color = Color.parseColor("#333333")
            canvas.drawText("Total Tagihan :", 315f, summaryBoxTop + 22f, paint)
            canvas.drawText("Rp " + formatCompactPrice(invoice.totalAmount), 440f, summaryBoxTop + 22f, paint)

            // Diskon
            if (invoice.discount > 0) {
                paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                paint.color = Color.parseColor("#888888")
                canvas.drawText("Potongan Diskon :", 315f, summaryBoxTop + 40f, paint)
                canvas.drawText("- Rp " + formatCompactPrice(invoice.discount), 440f, summaryBoxTop + 40f, paint)
            }

            // Terbayar
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            paint.color = Color.parseColor("#2E7D32")
            canvas.drawText("Jumlah Terbayar :", 315f, summaryBoxTop + 58f, paint)
            canvas.drawText("Rp " + formatCompactPrice(invoice.paidAmount), 440f, summaryBoxTop + 58f, paint)

            // Sisa Piutang
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            paint.color = if (remaining > 0) Color.parseColor("#C62828") else Color.parseColor("#2E7D32")
            canvas.drawText("Sisa Piutang   :", 315f, summaryBoxTop + 80f, paint)
            canvas.drawText("Rp " + formatCompactPrice(remaining), 440f, summaryBoxTop + 80f, paint)

            // 6. Akad Syar'i & Legal Notice Footer
            var footerY = summaryBoxTop + 130f
            paint.color = Color.parseColor("#112B2C")
            canvas.drawRoundRect(40f, footerY, 555f, footerY + 38f, 6f, 6f, paint)

            paint.color = Color.parseColor("#C6A15B")
            paint.textSize = 8.5f
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            canvas.drawText("HATUR TENGKYU", 50f, footerY + 15f, paint)

            paint.color = Color.WHITE
            paint.textSize = 8f
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
            canvas.drawText("Telah menjadi bagian perjalanan YANSPROJECT.ID.", 50f, footerY + 28f, paint)

            // 7. Signatures
            footerY += 60f
            paint.color = Color.parseColor("#333333")
            paint.textSize = 10f
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            canvas.drawText("Admin YANSPROJECT.ID", 80f, footerY, paint)
            canvas.drawText("Pemesan / Klien", 420f, footerY, paint)

            paint.color = Color.GRAY
            paint.strokeWidth = 1f
            canvas.drawLine(60f, footerY + 50f, 200f, footerY + 50f, paint)
            canvas.drawLine(390f, footerY + 50f, 510f, footerY + 50f, paint)

            paint.color = Color.parseColor("#555555")
            paint.textSize = 9f
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            canvas.drawText("( Sistem Terverifikasi )", 80f, footerY + 65f, paint)
            canvas.drawText("( ${invoice.clientName} )", 410f, footerY + 65f, paint)

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

            // Background: Deep Shadow Black
            canvas.drawColor(Color.parseColor("#0A0A0A"))

            // Decorative Top Bar (Dark Teal + Aged Gold Strip)
            paint.color = Color.parseColor("#0F3D3E")
            canvas.drawRect(0f, 0f, width.toFloat(), 220f, paint)

            paint.color = Color.parseColor("#C6A15B")
            canvas.drawRect(0f, 212f, width.toFloat(), 220f, paint)

            // Logo & Title
            paint.color = Color.parseColor("#C6A15B")
            paint.textSize = 46f
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            canvas.drawText("YANSPROJECT.ID", 60f, 90f, paint)

            paint.color = Color.WHITE
            paint.textSize = 22f
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            canvas.drawText("MAKNA SEBELUM ESTETIKA", 60f, 135f, paint)

            paint.color = Color.parseColor("#4FD1C5")
            paint.textSize = 18f
            canvas.drawText("Official E-Invoice YANSPROJECT.ID", 60f, 175f, paint)

            // Card Container (Dark Card Surface with Gold Border)
            val cardLeft = 50f
            val cardTop = 260f
            val cardRight = width - 50f
            val cardBottom = height - 120f

            paint.color = Color.parseColor("#163536")
            canvas.drawRoundRect(cardLeft, cardTop, cardRight, cardBottom, 28f, 28f, paint)

            paint.color = Color.parseColor("#C6A15B")
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            canvas.drawRoundRect(cardLeft, cardTop, cardRight, cardBottom, 28f, 28f, paint)

            paint.style = Paint.Style.FILL

            // Subtle Security Watermark (PNG HD)
            canvas.save()
            canvas.rotate(-30f, 540f, 1000f)
            paint.color = Color.parseColor("#C6A15B")
            paint.alpha = 20 // ~8% subtle opacity
            paint.textSize = 72f
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            canvas.drawText("YANSPROJECT.ID", 220f, 980f, paint)

            paint.textSize = 22f
            paint.color = Color.parseColor("#4FD1C5")
            paint.alpha = 28 // ~11% subtle opacity
            canvas.drawText("OFFICIAL E-INVOICE • BY YANSPROJECT.ID", 180f, 1020f, paint)
            canvas.restore()

            // Invoice Header Inside Card
            var curY = cardTop + 70f

            paint.color = Color.parseColor("#C6A15B")
            paint.textSize = 34f
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            canvas.drawText("FAKTUR INVOICE DETAIL", cardLeft + 40f, curY, paint)

            // Status Pill
            val remaining = (invoice.totalAmount - invoice.paidAmount - invoice.discount).coerceAtLeast(0.0)
            val isPaid = remaining <= 0
            val statusStr = if (isPaid) "LUNAS" else if (invoice.paidAmount > 0) "DIBAYAR SEBAGIAN" else "BELUM LUNAS"
            val statusBg = if (isPaid) Color.parseColor("#1B4D3E") else Color.parseColor("#4A2A18")
            val statusColor = if (isPaid) Color.parseColor("#4FD1C5") else Color.parseColor("#C6A15B")

            paint.color = statusBg
            canvas.drawRoundRect(cardRight - 320f, curY - 40f, cardRight - 40f, curY + 15f, 20f, 20f, paint)

            paint.color = statusColor
            paint.textSize = 20f
            canvas.drawText(statusStr, cardRight - 300f, curY - 5f, paint)

            // Meta Info Grid
            curY += 80f
            paint.color = Color.parseColor("#112B2C")
            canvas.drawRoundRect(cardLeft + 30f, curY, cardRight - 30f, curY + 160f, 20f, 20f, paint)

            paint.color = Color.parseColor("#A0A0A0")
            paint.textSize = 20f
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            canvas.drawText("NO. INVOICE", cardLeft + 60f, curY + 50f, paint)
            canvas.drawText("TANGGAL", cardLeft + 60f, curY + 95f, paint)
            canvas.drawText("JATUH TEMPO", cardLeft + 60f, curY + 135f, paint)

            paint.color = Color.WHITE
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            canvas.drawText(": ${invoice.invoiceNumber}", cardLeft + 230f, curY + 50f, paint)
            canvas.drawText(": ${formatDate(invoice.issueDate)}", cardLeft + 230f, curY + 95f, paint)
            canvas.drawText(": ${formatDate(invoice.dueDate)}", cardLeft + 230f, curY + 135f, paint)

            // Client Info
            canvas.drawText("PELANGGAN", cardLeft + 540f, curY + 50f, paint)
            paint.color = Color.parseColor("#C6A15B")
            canvas.drawText(invoice.clientName, cardLeft + 540f, curY + 95f, paint)

            paint.color = Color.parseColor("#A0A0A0")
            paint.textSize = 18f
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            val phoneText = if (invoice.clientPhone.isNotBlank()) invoice.clientPhone else "-"
            canvas.drawText("HP: $phoneText", cardLeft + 540f, curY + 135f, paint)

            // Items Section
            curY += 230f
            paint.color = Color.parseColor("#0F3D3E")
            canvas.drawRect(cardLeft + 30f, curY, cardRight - 30f, curY + 50f, paint)

            paint.color = Color.parseColor("#C6A15B")
            paint.textSize = 20f
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            canvas.drawText("DESKRIPSI PESANAN", cardLeft + 50f, curY + 34f, paint)
            canvas.drawText("QTY", cardLeft + 540f, curY + 34f, paint)
            canvas.drawText("HARGA", cardLeft + 640f, curY + 34f, paint)
            canvas.drawText("SUBTOTAL", cardLeft + 800f, curY + 34f, paint)

            curY += 80f
            paint.color = Color.WHITE
            paint.textSize = 20f

            com.yansproject.app.ui.InvoiceItemSorter.sortInvoiceItems(items.filter { !it.description.startsWith("__") }).take(12).forEachIndexed { idx, item ->
                paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                paint.color = Color.WHITE

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

            // Totals Box
            curY = cardBottom - 380f
            paint.color = Color.parseColor("#0F3D3E")
            canvas.drawRoundRect(cardLeft + 30f, curY, cardRight - 30f, curY + 220f, 20f, 20f, paint)

            paint.color = Color.parseColor("#C6A15B")
            paint.textSize = 22f
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            canvas.drawText("TOTAL TAGIHAN", cardLeft + 60f, curY + 50f, paint)
            canvas.drawText("Rp " + formatCompactPrice(invoice.totalAmount), cardRight - 380f, curY + 50f, paint)

            if (invoice.discount > 0) {
                paint.color = Color.parseColor("#A0A0A0")
                paint.textSize = 20f
                paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                canvas.drawText("Potongan Diskon", cardLeft + 60f, curY + 95f, paint)
                canvas.drawText("- Rp " + formatCompactPrice(invoice.discount), cardRight - 380f, curY + 95f, paint)
            }

            paint.color = Color.parseColor("#4FD1C5")
            paint.textSize = 22f
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            canvas.drawText("Total Terbayar", cardLeft + 60f, curY + 140f, paint)
            canvas.drawText("Rp " + formatCompactPrice(invoice.paidAmount), cardRight - 380f, curY + 140f, paint)

            paint.color = if (remaining > 0) Color.parseColor("#FF5252") else Color.parseColor("#4FD1C5")
            paint.textSize = 24f
            canvas.drawText("Sisa Piutang", cardLeft + 60f, curY + 190f, paint)
            canvas.drawText("Rp " + formatCompactPrice(remaining), cardRight - 380f, curY + 190f, paint)

            // Footer Legal
            val footerY = cardBottom - 120f
            paint.color = Color.parseColor("#C6A15B")
            paint.textSize = 18f
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
            canvas.drawText("Hatur Tengkyu telah menjadi bagian perjalanan YASNPROJECT.ID", cardLeft + 40f, footerY, paint)

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
        return java.text.SimpleDateFormat("dd MMMM yyyy", java.util.Locale("id", "ID")).format(java.util.Date(timestamp))
    }

    private fun formatCompactPrice(price: Double): String {
        return String.format("%,.0f", price).replace(",", ".")
    }
}

