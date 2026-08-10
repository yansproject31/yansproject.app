package com.yansproject.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.Log
import android.widget.Toast
import com.yansproject.app.data.Invoice
import com.yansproject.app.data.InvoiceItemDetail
import com.yansproject.app.ui.FormatUtils
import com.yansproject.app.ui.InvoiceItemSorter
import com.yansproject.app.ui.MainViewModel
import com.yansproject.app.ui.MemberCartItem
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BitmapUtils {

    private const val TAG = "BitmapUtils"

    fun exportToPng(
        context: Context,
        invoice: Invoice,
        items: List<InvoiceItemDetail>,
        viewModel: MainViewModel? = null
    ): File? {
        val width = 800
        val height = 1100
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()

        paint.color = android.graphics.Color.WHITE
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        val logoDrawable = androidx.core.content.ContextCompat.getDrawable(context, com.yansproject.app.R.drawable.ic_logo)
        if (logoDrawable != null) {
            logoDrawable.setBounds(60, 45, 125, 110)
            logoDrawable.draw(canvas)
        }

        paint.color = android.graphics.Color.rgb(15, 61, 62)
        paint.textSize = 26f
        paint.isFakeBoldText = true
        canvas.drawText(com.yansproject.app.data.BusinessIdentityProvider.getCompanyName(context), 140f, 85f, paint)

        paint.textSize = 22f
        paint.isFakeBoldText = true
        canvas.drawText("INVOICE", 550f, 80f, paint)

        paint.textSize = 13f
        paint.isFakeBoldText = false
        canvas.drawText("No: ${invoice.invoiceNumber}", 550f, 105f, paint)
        canvas.drawText("Date: ${FormatUtils.formatDate(invoice.issueDate)}", 550f, 125f, paint)

        paint.color = android.graphics.Color.LTGRAY
        canvas.drawLine(60f, 150f, 740f, 150f, paint)

        paint.color = android.graphics.Color.BLACK
        paint.textSize = 14f
        paint.isFakeBoldText = true
        canvas.drawText("CUSTOMER INFO:", 60f, 190f, paint)
        paint.isFakeBoldText = false
        canvas.drawText("Name: ${invoice.clientName}", 60f, 215f, paint)
        
        var nextY = 235f
        if (!invoice.clientPhone.isNullOrBlank()) {
            canvas.drawText("WhatsApp: ${invoice.clientPhone}", 60f, nextY, paint)
            nextY += 20f
        }
        
        val addressItem = items.find { it.description.startsWith("__ADDRESS__:") }
        val address = addressItem?.description?.removePrefix("__ADDRESS__:")?.trim()
        if (!address.isNullOrBlank()) {
            canvas.drawText("Address: $address", 60f, nextY, paint)
            nextY += 20f
        }

        val (catName, colorName) = com.yansproject.app.ui.InvoiceItemSorter.extractCatalogAndColor(items)
        nextY += 10f
        paint.color = android.graphics.Color.rgb(15, 61, 62)
        paint.isFakeBoldText = true
        paint.textSize = 14f
        canvas.drawText("CATALOG : $catName", 60f, nextY, paint)
        nextY += 22f
        canvas.drawText("WARNA   : $colorName", 60f, nextY, paint)
        nextY += 15f

        val tableHeaderY = (nextY + 15f).coerceAtLeast(290f)
        paint.color = android.graphics.Color.rgb(240, 240, 240)
        canvas.drawRect(60f, tableHeaderY, 740f, tableHeaderY + 30f, paint)
        paint.color = android.graphics.Color.BLACK
        paint.isFakeBoldText = true
        canvas.drawText("RINCIAN PEMESANAN", 75f, tableHeaderY + 20f, paint)
        canvas.drawText("QTY", 520f, tableHeaderY + 20f, paint)
        canvas.drawText("PRICE", 580f, tableHeaderY + 20f, paint)
        canvas.drawText("TOTAL", 670f, tableHeaderY + 20f, paint)

        paint.isFakeBoldText = false
        var currentY = tableHeaderY + 50f
        val filteredItems = com.yansproject.app.ui.InvoiceItemSorter.sortInvoiceItems(items.filter { !it.description.startsWith("__") })
        for (item in filteredItems) {
            val cleanDesc = com.yansproject.app.ui.InvoiceItemSorter.cleanDescriptionForDisplay(item.description)
            val shortDesc = if (cleanDesc.length > 45) cleanDesc.take(42) + "..." else cleanDesc
            canvas.drawText(shortDesc, 75f, currentY, paint)
            canvas.drawText(item.quantity.toString(), 520f, currentY, paint)
            canvas.drawText(FormatUtils.formatRupiah(item.price), 580f, currentY, paint)
            canvas.drawText(FormatUtils.formatRupiah(item.price * item.quantity), 670f, currentY, paint)
            currentY += 25f
        }

        currentY += 20f
        canvas.drawLine(60f, currentY, 740f, currentY, paint)
        currentY += 25f

        val shortQty = InvoiceItemSorter.getShortSleeveTotalQty(items)
        val longQty = InvoiceItemSorter.getLongSleeveTotalQty(items)
        val globalQty = InvoiceItemSorter.getGlobalTotalQty(items)

        val calculatedSubtotal = InvoiceItemSorter.calcSubtotal(items)
        val subtotalVal = if (calculatedSubtotal > 0.0) calculatedSubtotal else (invoice.totalAmount + invoice.discount)
        val totalVal = (subtotalVal - invoice.discount).coerceAtLeast(0.0)

        // Left Box: Qty Breakdown
        paint.textSize = 13f
        paint.isFakeBoldText = true
        paint.color = android.graphics.Color.parseColor("#0F3D3E")
        canvas.drawText("RINGKASAN QTY:", 60f, currentY, paint)

        paint.textSize = 12f
        paint.isFakeBoldText = false
        paint.color = android.graphics.Color.BLACK
        canvas.drawText("• QTY PENDEK  : $shortQty Pcs", 60f, currentY + 22f, paint)
        canvas.drawText("• QTY PANJANG : $longQty Pcs", 60f, currentY + 44f, paint)
        paint.isFakeBoldText = true
        canvas.drawText("• TOTAL QTY   : $globalQty Pcs", 60f, currentY + 66f, paint)

        // Right Box: Financial Breakdown
        paint.textSize = 12f
        paint.isFakeBoldText = true
        paint.color = android.graphics.Color.BLACK
        canvas.drawText("SUB TOTAL :", 460f, currentY, paint)
        paint.isFakeBoldText = false
        canvas.drawText(FormatUtils.formatRupiah(subtotalVal), 610f, currentY, paint)

        currentY += 22f
        paint.isFakeBoldText = true
        canvas.drawText("DISKON :", 460f, currentY, paint)
        paint.isFakeBoldText = false
        canvas.drawText("- " + FormatUtils.formatRupiah(invoice.discount), 610f, currentY, paint)

        currentY += 22f
        // SUB HERO TOTAL
        paint.isFakeBoldText = true
        paint.textSize = 14f
        paint.color = android.graphics.Color.parseColor("#0F3D3E")
        canvas.drawText("TOTAL :", 460f, currentY, paint)
        canvas.drawText(FormatUtils.formatRupiah(totalVal), 610f, currentY, paint)

        currentY += 22f
        paint.textSize = 12f
        paint.isFakeBoldText = true
        paint.color = android.graphics.Color.BLACK
        canvas.drawText("PEMBAYARAN :", 460f, currentY, paint)
        paint.isFakeBoldText = false
        canvas.drawText(FormatUtils.formatRupiah(invoice.paidAmount), 610f, currentY, paint)

        currentY += 24f
        // HERO INFORMASI SISA PEMBAYARAN
        paint.isFakeBoldText = true
        paint.textSize = 13f
        val remColor = if (invoice.remainingPayment > 0) android.graphics.Color.parseColor("#C62828") else android.graphics.Color.parseColor("#2E7D32")
        paint.color = remColor
        canvas.drawText("SISA PEMBAYARAN :", 460f, currentY, paint)
        canvas.drawText(FormatUtils.formatRupiah(invoice.remainingPayment), 610f, currentY, paint)

        // Watermark
        paint.textSize = 70f
        paint.color = when (invoice.status) {
            "LUNAS" -> android.graphics.Color.argb(35, 46, 125, 50)
            "DP" -> android.graphics.Color.argb(35, 239, 108, 0)
            "BATAL" -> android.graphics.Color.argb(35, 120, 120, 120)
            else -> android.graphics.Color.argb(35, 198, 40, 40)
        }
        paint.isFakeBoldText = true
        canvas.save()
        canvas.rotate(-35f, 400f, 650f)
        canvas.drawText(invoice.status, 250f, 650f, paint)
        canvas.restore()

        val noteItem = items.find { it.description.startsWith("__NOTE__:") }
        val note = noteItem?.description?.removePrefix("__NOTE__:")?.trim()
        if (!note.isNullOrBlank()) {
            paint.textSize = 12f
            paint.color = android.graphics.Color.DKGRAY
            paint.isFakeBoldText = true
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("Catatan Admin: $note", 60f, currentY + 30f, paint)
            currentY += 50f
        }

        paint.textSize = 12f
        paint.isFakeBoldText = false
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("Hatur Tengkyu telah menjadi bagian dari perjalanan YANSPROJECT.ID", 400f, 1040f, paint)

        return try {
            val safeNum = invoice.invoiceNumber.replace("/", "_").replace("\\", "_").replace(":", "_").ifEmpty { invoice.id.toString() }
            val dir = FileUtils.getExportDirectory(context, "invoice")
            val file = File(dir, "Invoice-${safeNum}.png")
            FileOutputStream(file).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                outputStream.flush()
            }
            FileUtils.mirrorToDownloads(context, file, "Invoice")
            if (viewModel != null) {
                viewModel.showGlobalSnackbar("Gambar PNG Invoice-${safeNum} berhasil disimpan.", "Buka Folder") {
                    FileUtils.openFolder(context, dir)
                }
            } else {
                Toast.makeText(context, "Gambar PNG Invoice-${safeNum} berhasil disimpan di: ${file.absolutePath}", Toast.LENGTH_LONG).show()
            }
            file
        } catch (e: Exception) {
            Log.e(TAG, "Failed exporting PNG: ${e.message}", e)
            Toast.makeText(context, "Gagal mengekspor Gambar: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            null
        } finally {
            try { bitmap.recycle() } catch (_: Exception) {}
        }
    }

    fun exportOrderSummaryToPng(
        context: Context,
        clientName: String,
        clientPhone: String,
        items: List<MemberCartItem>,
        notes: String,
        viewModel: MainViewModel? = null
    ): File? {
        val width = 800
        val height = 1100
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()

        paint.color = android.graphics.Color.WHITE
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        paint.color = android.graphics.Color.rgb(15, 61, 62)
        paint.textSize = 26f
        paint.isFakeBoldText = true
        canvas.drawText(com.yansproject.app.data.BusinessIdentityProvider.getCompanyName(context), 60f, 80f, paint)

        paint.textSize = 22f
        canvas.drawText("ORDER SUMMARY", 500f, 80f, paint)

        paint.textSize = 13f
        paint.isFakeBoldText = false
        paint.color = android.graphics.Color.DKGRAY
        val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        val dateString = dateFormat.format(Date())
        canvas.drawText("Date: $dateString", 500f, 110f, paint)

        paint.color = android.graphics.Color.LTGRAY
        canvas.drawLine(60f, 140f, 740f, 140f, paint)

        paint.color = android.graphics.Color.BLACK
        paint.textSize = 14f
        paint.isFakeBoldText = true
        canvas.drawText("CUSTOMER INFO:", 60f, 180f, paint)
        paint.isFakeBoldText = false
        canvas.drawText("Name: $clientName", 60f, 205f, paint)
        canvas.drawText("WhatsApp: $clientPhone", 60f, 225f, paint)

        val tableHeaderY = 255f
        paint.color = android.graphics.Color.rgb(240, 240, 240)
        canvas.drawRect(60f, tableHeaderY, 740f, tableHeaderY + 30f, paint)
        paint.color = android.graphics.Color.BLACK
        paint.isFakeBoldText = true
        canvas.drawText("Item / Product Details", 75f, tableHeaderY + 20f, paint)
        canvas.drawText("Qty", 480f, tableHeaderY + 20f, paint)
        canvas.drawText("Price", 560f, tableHeaderY + 20f, paint)
        canvas.drawText("Total", 660f, tableHeaderY + 20f, paint)

        paint.isFakeBoldText = false
        var currentY = tableHeaderY + 50f
        for (item in items) {
            val details = "${item.catalogName} - ${item.varianName} (${item.size}, ${item.sleeve})"
            val shortDesc = if (details.length > 45) details.take(42) + "..." else details
            canvas.drawText(shortDesc, 75f, currentY, paint)
            canvas.drawText("${item.qty} Pcs", 480f, currentY, paint)
            canvas.drawText(FormatUtils.formatRupiah(item.price), 560f, currentY, paint)
            canvas.drawText(FormatUtils.formatRupiah(item.price * item.qty), 660f, currentY, paint)
            currentY += 25f
        }

        currentY += 15f
        paint.color = android.graphics.Color.LTGRAY
        canvas.drawLine(60f, currentY, 740f, currentY, paint)
        currentY += 30f

        val totalQty = items.sumOf { it.qty }
        val totalPrice = items.sumOf { it.qty * it.price }

        paint.color = android.graphics.Color.BLACK
        paint.isFakeBoldText = true
        canvas.drawText("Total Items:", 450f, currentY, paint)
        paint.isFakeBoldText = false
        canvas.drawText("$totalQty Pcs", 600f, currentY, paint)

        currentY += 25f
        paint.isFakeBoldText = true
        paint.textSize = 16f
        paint.color = android.graphics.Color.rgb(15, 61, 62)
        canvas.drawText("ESTIMASI TOTAL:", 450f, currentY, paint)
        canvas.drawText(FormatUtils.formatRupiah(totalPrice), 600f, currentY, paint)

        if (notes.isNotEmpty()) {
            paint.color = android.graphics.Color.DKGRAY
            paint.textSize = 12f
            paint.isFakeBoldText = true
            canvas.drawText("Catatan: $notes", 60f, currentY + 40f, paint)
        }

        paint.color = android.graphics.Color.GRAY
        paint.textSize = 12f
        paint.isFakeBoldText = false
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("Terima kasih telah melakukan pemesanan melalui ${com.yansproject.app.data.BusinessIdentityProvider.getCompanyName(context)}.", 400f, 1040f, paint)

        return try {
            val dir = FileUtils.getExportDirectory(context, "image")
            val file = File(dir, "Order-Summary-${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }
            if (viewModel != null) {
                viewModel.showGlobalSnackbar("Dokumen berhasil disimpan.", "Buka Folder") {
                    FileUtils.openFolder(context, dir)
                }
            } else {
                Toast.makeText(context, "Gambar disimpan di: ${file.absolutePath}", Toast.LENGTH_LONG).show()
            }
            file
        } catch (e: Exception) {
            Log.e(TAG, "Failed exporting Order Summary PNG: ${e.message}", e)
            Toast.makeText(context, "Gagal mengekspor Gambar: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            null
        } finally {
            try { bitmap.recycle() } catch (_: Exception) {}
        }
    }
}
