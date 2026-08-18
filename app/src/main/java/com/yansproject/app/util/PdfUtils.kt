package com.yansproject.app.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.yansproject.app.R
import com.yansproject.app.data.ExportManager
import com.yansproject.app.data.FirebaseSyncManager
import com.yansproject.app.data.HistoricalDataState
import com.yansproject.app.data.Invoice
import com.yansproject.app.data.InvoiceItemDetail
import com.yansproject.app.data.InvoicePresentationModel
import com.yansproject.app.data.ProjectCustom
import com.yansproject.app.data.toPresentationModel
import com.yansproject.app.ui.AppSettings
import com.yansproject.app.ui.FormatUtils
import com.yansproject.app.ui.InvoiceItemSorter
import com.yansproject.app.ui.MainViewModel
import com.yansproject.app.ui.MemberCartItem
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfUtils {

    private const val TAG = "PdfUtils"

    fun exportToPdf(
        context: Context,
        invoice: Invoice,
        items: List<InvoiceItemDetail>,
        viewModel: MainViewModel? = null
    ): File? {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint()

        val logoDrawable = ContextCompat.getDrawable(context, R.drawable.ic_logo)
        if (logoDrawable != null) {
            logoDrawable.setBounds(40, 32, 75, 67)
            logoDrawable.draw(canvas)
        }

        paint.color = android.graphics.Color.parseColor("#0F3D3E")
        paint.textSize = 20f
        paint.isFakeBoldText = true
        canvas.drawText("YANSPROJECT.ID", 85f, 60f, paint)

        paint.textSize = 14f
        paint.isFakeBoldText = true
        canvas.drawText("INVOICE", 450f, 60f, paint)

        paint.textSize = 9f
        paint.isFakeBoldText = false
        // Financial presentation model from authoritative InvoiceFinancialCalculator
        val presentation = invoice.toPresentationModel(items)

        val formattedDate = presentation.formattedDate
        canvas.drawText("No: ${invoice.invoiceNumber}", 450f, 75f, paint)
        canvas.drawText("Date: $formattedDate", 450f, 88f, paint)

        paint.color = android.graphics.Color.GRAY
        canvas.drawLine(40f, 105f, 555f, 105f, paint)

        paint.color = android.graphics.Color.BLACK
        paint.textSize = 10f
        paint.isFakeBoldText = true
        canvas.drawText("CUSTOMER INFO:", 40f, 130f, paint)
        paint.isFakeBoldText = false
        canvas.drawText("Name: ${invoice.clientName}", 40f, 145f, paint)

        var nextY = 160f
        if (!invoice.clientPhone.isNullOrBlank()) {
            canvas.drawText("WhatsApp: ${invoice.clientPhone}", 40f, nextY, paint)
            nextY += 15f
        }

        val addressItem = items.find { it.description.startsWith("__ADDRESS__:") }
        val address = addressItem?.description?.removePrefix("__ADDRESS__:")?.trim()
        if (!address.isNullOrBlank()) {
            canvas.drawText("Address: $address", 40f, nextY, paint)
            nextY += 15f
        }

        val (catalogName, colorName) = InvoiceItemSorter.extractCatalogAndColor(items)
        nextY += 5f
        paint.color = android.graphics.Color.parseColor("#0F3D3E")
        paint.isFakeBoldText = true
        paint.textSize = 9.5f
        canvas.drawText("CATALOG : $catalogName", 40f, nextY, paint)
        nextY += 14f
        canvas.drawText("WARNA   : $colorName", 40f, nextY, paint)
        nextY += 10f

        val tableHeaderY = (nextY + 10f).coerceAtLeast(200f)
        paint.color = android.graphics.Color.LTGRAY
        canvas.drawRect(40f, tableHeaderY, 555f, tableHeaderY + 20f, paint)
        paint.color = android.graphics.Color.BLACK
        paint.isFakeBoldText = true
        canvas.drawText("RINCIAN PEMESANAN", 50f, tableHeaderY + 14f, paint)
        canvas.drawText("QTY", 380f, tableHeaderY + 14f, paint)
        canvas.drawText("PRICE", 430f, tableHeaderY + 14f, paint)
        canvas.drawText("TOTAL", 500f, tableHeaderY + 14f, paint)

        paint.isFakeBoldText = false
        var currentY = tableHeaderY + 35f
        val filteredItems = InvoiceItemSorter.sortInvoiceItems(items.filter { !it.description.startsWith("__") })
        if (filteredItems.isEmpty()) {
            val emptyNotice = when (presentation.dataState) {
                HistoricalDataState.NO_ITEMS -> "(Tidak ada rincian item / NO_ITEMS)"
                HistoricalDataState.INVALID_DATA -> "(Data rincian tidak valid / INVALID_DATA)"
                HistoricalDataState.RECOVERY_REQUIRED -> "(Memerlukan pemulihan / RECOVERY_REQUIRED)"
                else -> "(Tidak ada rincian item)"
            }
            paint.color = android.graphics.Color.DKGRAY
            canvas.drawText(emptyNotice, 50f, currentY, paint)
            currentY += 20f
        } else {
            for (item in filteredItems) {
                val cleanDesc = InvoiceItemSorter.cleanDescriptionForDisplay(item.description)
                val shortDesc = if (cleanDesc.length > 45) cleanDesc.take(42) + "..." else cleanDesc
                canvas.drawText(shortDesc, 50f, currentY, paint)
                canvas.drawText(item.quantity.toString(), 380f, currentY, paint)
                canvas.drawText(FormatUtils.formatRupiah(item.price), 430f, currentY, paint)
                canvas.drawText(FormatUtils.formatRupiah(item.price * item.quantity), 500f, currentY, paint)
                currentY += 20f
            }
        }

        currentY += 15f
        canvas.drawLine(40f, currentY, 555f, currentY, paint)
        currentY += 20f

        val shortQty = InvoiceItemSorter.getShortSleeveTotalQty(filteredItems)
        val longQty = InvoiceItemSorter.getLongSleeveTotalQty(filteredItems)
        val globalQty = if (filteredItems.isNotEmpty()) InvoiceItemSorter.getGlobalTotalQty(filteredItems) else presentation.quantity

        // Quantity summary on left
        paint.textSize = 9.5f
        paint.isFakeBoldText = true
        paint.color = android.graphics.Color.parseColor("#0F3D3E")
        canvas.drawText("RINGKASAN KUANTITAS:", 50f, currentY, paint)

        paint.textSize = 8.5f
        paint.isFakeBoldText = false
        paint.color = android.graphics.Color.BLACK
        canvas.drawText("QTY PENDEK  : $shortQty Pcs", 50f, currentY + 16f, paint)
        canvas.drawText("QTY PANJANG : $longQty Pcs", 50f, currentY + 32f, paint)
        paint.isFakeBoldText = true
        canvas.drawText("TOTAL QTY   : $globalQty Pcs", 50f, currentY + 48f, paint)

        // Financial summary on right using canonical InvoicePresentationModel
        paint.textSize = 8.5f
        paint.isFakeBoldText = true
        paint.color = android.graphics.Color.BLACK
        canvas.drawText("SUB TOTAL :", 340f, currentY, paint)
        paint.isFakeBoldText = false
        canvas.drawText(FormatUtils.formatRupiah(presentation.subtotalDouble), 450f, currentY, paint)

        currentY += 16f
        paint.isFakeBoldText = true
        canvas.drawText("DISKON :", 340f, currentY, paint)
        paint.isFakeBoldText = false
        canvas.drawText("- " + FormatUtils.formatRupiah(presentation.discountDouble), 450f, currentY, paint)

        currentY += 18f
        // SUB HERO TOTAL
        paint.isFakeBoldText = true
        paint.color = android.graphics.Color.parseColor("#0F3D3E")
        canvas.drawText("TOTAL :", 340f, currentY, paint)
        canvas.drawText(FormatUtils.formatRupiah(presentation.grandTotalDouble), 450f, currentY, paint)

        currentY += 16f
        paint.isFakeBoldText = true
        paint.color = android.graphics.Color.BLACK
        canvas.drawText("PEMBAYARAN :", 340f, currentY, paint)
        paint.isFakeBoldText = false
        canvas.drawText(FormatUtils.formatRupiah(presentation.paidDouble), 450f, currentY, paint)

        currentY += 18f
        // HERO INFORMASI SISA PEMBAYARAN
        paint.isFakeBoldText = true
        val remainingColor = if (presentation.remaining > 0) android.graphics.Color.parseColor("#C62828") else android.graphics.Color.parseColor("#2E7D32")
        paint.color = remainingColor
        canvas.drawText("SISA PEMBAYARAN :", 340f, currentY, paint)
        canvas.drawText(FormatUtils.formatRupiah(presentation.remainingDouble), 450f, currentY, paint)

        paint.textSize = 50f
        paint.color = when (invoice.status) {
            "LUNAS" -> android.graphics.Color.argb(35, 46, 125, 50)
            "DP" -> android.graphics.Color.argb(35, 239, 108, 0)
            "BATAL" -> android.graphics.Color.argb(35, 120, 120, 120)
            else -> android.graphics.Color.argb(35, 198, 40, 40)
        }
        paint.isFakeBoldText = true
        canvas.save()
        canvas.rotate(-35f, 300f, 500f)
        canvas.drawText(invoice.status, 200f, 500f, paint)
        canvas.restore()

        val noteItem = items.find { it.description.startsWith("__NOTE__:") }
        val note = noteItem?.description?.removePrefix("__NOTE__:")?.trim()
        if (!note.isNullOrBlank()) {
            paint.textSize = 9f
            paint.color = android.graphics.Color.DKGRAY
            paint.isFakeBoldText = true
            canvas.drawText("Catatan Admin: $note", 40f, currentY + 30f, paint)
            currentY += 45f
        }

        paint.textSize = 9f
        paint.isFakeBoldText = false
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("Hatur Tengkyu telah menjadi bagian dari perjalanan YANSPROJECT.ID", 297f, 790f, paint)

        pdfDocument.finishPage(page)

        return try {
            val safeNum = invoice.invoiceNumber.replace("/", "_").replace("\\", "_")
            val dir = FileUtils.getExportDirectory(context, "invoice")
            val file = File(dir, "Invoice-${safeNum}.pdf")
            
            val exportResult = ExportManager.getInstance().exportToFile(file) { outputStream ->
                pdfDocument.writeTo(outputStream)
            }
            pdfDocument.close()

            val exportedFile = exportResult.getOrThrow()
            if (!exportedFile.exists() || exportedFile.length() <= 0L) {
                throw java.io.IOException("Validasi berkas PDF gagal: berkas tidak ditemukan atau ukuran 0 byte.")
            }

            // Separate mirror copy to public Downloads folder using FileUtils
            val mirrorResult = FileUtils.mirrorToDownloads(context, exportedFile, "Invoice")
            if (mirrorResult != MirrorResult.FAILED) {
                Log.i(TAG, "Invoice PDF successfully mirrored to public Downloads: ${exportedFile.name}")
            } else {
                Log.w(TAG, "Primary Invoice PDF saved at ${exportedFile.absolutePath}, but public Downloads mirror skipped or failed.")
            }

            if (viewModel != null) {
                viewModel.showGlobalSnackbar("Ringkasan PDF Invoice-${safeNum} berhasil disimpan.", "Buka Folder") {
                    FileUtils.openFolder(context, dir)
                }
            } else {
                Toast.makeText(context, "Ringkasan PDF Invoice-${safeNum} berhasil disimpan!", Toast.LENGTH_LONG).show()
            }

            val params = android.os.Bundle().apply {
                putString("invoice_number", invoice.invoiceNumber)
                putString("type", "Invoice")
            }
            FirebaseSyncManager.logEvent("export_pdf", params)
            exportedFile
        } catch (e: Exception) {
            Log.e(TAG, "Technical failure during PDF invoice export: ${e.message}", e)
            Toast.makeText(context, "Gagal membuat berkas PDF Invoice. Silakan coba beberapa saat lagi.", Toast.LENGTH_SHORT).show()
            null
        }
    }

    fun exportFinancialSummaryToPdf(
        context: Context,
        period: String,
        totalRevenue: Double,
        totalReceivables: Double,
        activeProjectsCount: Int,
        lowStockCount: Int,
        totalOrdersCount: Int,
        unpaidInvoices: List<Invoice>,
        activeProjects: List<ProjectCustom>,
        viewModel: MainViewModel? = null
    ) {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint()

        paint.color = android.graphics.Color.parseColor("#121212")
        canvas.drawRect(0f, 0f, 595f, 110f, paint)

        val logoDrawable = ContextCompat.getDrawable(context, R.drawable.ic_logo)
        if (logoDrawable != null) {
            logoDrawable.setBounds(40, 25, 80, 65)
            logoDrawable.draw(canvas)
        }

        paint.color = android.graphics.Color.parseColor("#C6A15B")
        paint.textSize = 22f
        paint.isFakeBoldText = true
        canvas.drawText(AppSettings.getStoreName(context).uppercase(), 95f, 50f, paint)

        paint.color = android.graphics.Color.WHITE
        paint.textSize = 10f
        paint.isFakeBoldText = false
        canvas.drawText("RINGKASAN LAPORAN KEUANGAN & OPERASIONAL", 95f, 70f, paint)
        canvas.drawText("Periode: $period | Tanggal Cetak: ${FormatUtils.formatDate(System.currentTimeMillis())}", 95f, 85f, paint)

        paint.color = android.graphics.Color.BLACK
        paint.textSize = 12f
        paint.isFakeBoldText = true
        canvas.drawText("1. RINGKASAN KINERJA KEUANGAN", 40f, 140f, paint)

        paint.color = android.graphics.Color.parseColor("#EEEEEE")
        canvas.drawRect(40f, 150f, 555f, 215f, paint)

        paint.color = android.graphics.Color.BLACK
        paint.textSize = 10f
        paint.isFakeBoldText = false

        canvas.drawText("Total Pendapatan (Lunas/DP):", 50f, 170f, paint)
        paint.isFakeBoldText = true
        canvas.drawText(FormatUtils.formatRupiah(totalRevenue), 230f, 170f, paint)

        paint.isFakeBoldText = false
        canvas.drawText("Total Piutang (Belum Lunas):", 50f, 185f, paint)
        paint.isFakeBoldText = true
        canvas.drawText(FormatUtils.formatRupiah(totalReceivables), 230f, 185f, paint)

        paint.isFakeBoldText = false
        canvas.drawText("Total Transaksi AJIBQOBUL:", 50f, 200f, paint)
        paint.isFakeBoldText = true
        canvas.drawText("$totalOrdersCount Transaksi", 230f, 200f, paint)

        paint.isFakeBoldText = false
        canvas.drawText("Project Custom Aktif:", 350f, 170f, paint)
        paint.isFakeBoldText = true
        canvas.drawText("$activeProjectsCount Project", 480f, 170f, paint)

        paint.isFakeBoldText = false
        canvas.drawText("Barang Stok Menipis:", 350f, 185f, paint)
        paint.isFakeBoldText = true
        canvas.drawText("$lowStockCount Item", 480f, 185f, paint)

        var yPos = 245f
        paint.color = android.graphics.Color.BLACK
        paint.textSize = 12f
        paint.isFakeBoldText = true
        canvas.drawText("2. DAFTAR PIUTANG (BELUM LUNAS)", 40f, yPos, paint)

        yPos += 12f
        paint.color = android.graphics.Color.parseColor("#C39B4B")
        canvas.drawRect(40f, yPos, 555f, yPos + 18f, paint)

        paint.color = android.graphics.Color.WHITE
        paint.textSize = 9f
        paint.isFakeBoldText = true
        canvas.drawText("No. Invoice", 45f, yPos + 12f, paint)
        canvas.drawText("Klien", 160f, yPos + 12f, paint)
        canvas.drawText("Total Tagihan", 300f, yPos + 12f, paint)
        canvas.drawText("Sisa Piutang", 420f, yPos + 12f, paint)
        canvas.drawText("Status", 510f, yPos + 12f, paint)

        yPos += 18f
        paint.color = android.graphics.Color.BLACK
        paint.isFakeBoldText = false
        val unpaidLimit = unpaidInvoices.take(8)
        if (unpaidLimit.isEmpty()) {
            canvas.drawText("Tidak ada piutang outstanding.", 50f, yPos + 15f, paint)
            yPos += 20f
        } else {
            for (inv in unpaidLimit) {
                yPos += 15f
                canvas.drawText(inv.invoiceNumber, 45f, yPos, paint)
                val displayClient = if (inv.clientName.length > 18) inv.clientName.take(16) + ".." else inv.clientName
                canvas.drawText(displayClient, 160f, yPos, paint)
                canvas.drawText(FormatUtils.formatRupiah(inv.totalAmount), 300f, yPos, paint)
                canvas.drawText(FormatUtils.formatRupiah(inv.remainingPayment), 420f, yPos, paint)
                canvas.drawText(inv.status, 510f, yPos, paint)
            }
        }

        yPos += 30f
        paint.color = android.graphics.Color.BLACK
        paint.textSize = 12f
        paint.isFakeBoldText = true
        canvas.drawText("3. PROYEK CUSTOM AKTIF", 40f, yPos, paint)

        yPos += 12f
        paint.color = android.graphics.Color.parseColor("#121212")
        canvas.drawRect(40f, yPos, 555f, yPos + 18f, paint)

        paint.color = android.graphics.Color.WHITE
        paint.textSize = 9f
        paint.isFakeBoldText = true
        canvas.drawText("Nama Project", 45f, yPos + 12f, paint)
        canvas.drawText("Klien", 210f, yPos + 12f, paint)
        canvas.drawText("Total Biaya", 350f, yPos + 12f, paint)
        canvas.drawText("Uang Muka", 450f, yPos + 12f, paint)

        yPos += 18f
        paint.color = android.graphics.Color.BLACK
        paint.isFakeBoldText = false
        val activeLimit = activeProjects.filter { it.status != "Completed" }.take(8)
        if (activeLimit.isEmpty()) {
            canvas.drawText("Tidak ada proyek aktif saat ini.", 50f, yPos + 15f, paint)
            yPos += 20f
        } else {
            for (proj in activeLimit) {
                yPos += 15f
                val displayProj = if (proj.projectName.length > 25) proj.projectName.take(23) + ".." else proj.projectName
                canvas.drawText(displayProj, 45f, yPos, paint)
                val displayClient = if (proj.clientName.length > 20) proj.clientName.take(18) + ".." else proj.clientName
                canvas.drawText(displayClient, 210f, yPos, paint)
                canvas.drawText(FormatUtils.formatRupiah(proj.totalCost), 350f, yPos, paint)
                canvas.drawText(FormatUtils.formatRupiah(proj.paidAmount), 450f, yPos, paint)
            }
        }

        yPos = 780f
        paint.color = android.graphics.Color.GRAY
        canvas.drawLine(40f, yPos, 555f, yPos, paint)
        paint.textSize = 8f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("Dokumen ini dihasilkan secara otomatis oleh YANSPROJECT.ID. All Rights Reserved.", 297f, yPos + 15f, paint)

        pdfDocument.finishPage(page)

        try {
            val documentsDir = FileUtils.getExportDirectory(context, "export")
            val file = File(documentsDir, "YANS_LAPORAN_KEUANGAN_${System.currentTimeMillis()}.pdf")
            pdfDocument.writeTo(file.outputStream())
            if (viewModel != null) {
                viewModel.showGlobalSnackbar("Dokumen berhasil disimpan.", "Buka Folder") {
                    FileUtils.openFolder(context, documentsDir)
                }
            } else {
                Toast.makeText(context, "PDF Laporan berhasil disimpan di: ${file.name}", Toast.LENGTH_LONG).show()
            }

            val params = android.os.Bundle().apply {
                putString("type", "Financial_Summary")
                putString("filename", file.name)
            }
            FirebaseSyncManager.logEvent("export_pdf", params)
        } catch (e: Exception) {
            Log.e(TAG, "Failed exporting financial summary PDF: ${e.message}", e)
            Toast.makeText(context, "Gagal ekspor PDF: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        } finally {
            pdfDocument.close()
        }
    }

    fun exportOrderSummaryToPdf(
        context: Context,
        clientName: String,
        clientPhone: String,
        items: List<MemberCartItem>,
        notes: String,
        viewModel: MainViewModel? = null
    ): File? {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint()

        val logoDrawable = ContextCompat.getDrawable(context, R.drawable.ic_logo)
        if (logoDrawable != null) {
            logoDrawable.setBounds(40, 32, 75, 67)
            logoDrawable.draw(canvas)
        }

        paint.color = android.graphics.Color.parseColor("#0F3D3E")
        paint.textSize = 20f
        paint.isFakeBoldText = true
        canvas.drawText("YANSPROJECT.ID", 85f, 60f, paint)

        paint.textSize = 14f
        paint.isFakeBoldText = true
        canvas.drawText("ORDER SUMMARY", 400f, 60f, paint)

        paint.textSize = 10f
        paint.isFakeBoldText = false
        val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        val dateString = dateFormat.format(Date())
        canvas.drawText("Date: $dateString", 400f, 78f, paint)

        paint.color = android.graphics.Color.GRAY
        canvas.drawLine(40f, 105f, 555f, 105f, paint)

        paint.color = android.graphics.Color.BLACK
        paint.textSize = 11f
        paint.isFakeBoldText = true
        canvas.drawText("CUSTOMER INFO:", 40f, 130f, paint)
        paint.isFakeBoldText = false
        canvas.drawText("Name: $clientName", 40f, 145f, paint)
        canvas.drawText("WhatsApp: $clientPhone", 40f, 160f, paint)

        val tableHeaderY = 185f
        paint.color = android.graphics.Color.LTGRAY
        canvas.drawRect(40f, tableHeaderY, 555f, tableHeaderY + 20f, paint)
        paint.color = android.graphics.Color.BLACK
        paint.isFakeBoldText = true
        canvas.drawText("Item / Product Details", 50f, tableHeaderY + 14f, paint)
        canvas.drawText("Qty", 350f, tableHeaderY + 14f, paint)
        canvas.drawText("Price", 410f, tableHeaderY + 14f, paint)
        canvas.drawText("Total", 490f, tableHeaderY + 14f, paint)

        paint.isFakeBoldText = false
        var currentY = tableHeaderY + 35f
        for (item in items) {
            val details = "${item.catalogName} - ${item.varianName} (${item.size}, ${item.sleeve})"
            val shortDesc = if (details.length > 42) details.take(39) + "..." else details
            canvas.drawText(shortDesc, 50f, currentY, paint)
            canvas.drawText("${item.qty} Pcs", 350f, currentY, paint)
            canvas.drawText(FormatUtils.formatRupiah(item.price), 410f, currentY, paint)
            canvas.drawText(FormatUtils.formatRupiah(item.price * item.qty), 490f, currentY, paint)
            currentY += 20f
        }

        currentY += 10f
        canvas.drawLine(40f, currentY, 555f, currentY, paint)
        currentY += 20f

        val totalQty = items.sumOf { it.qty }
        val totalPrice = items.sumOf { it.qty * it.price }

        paint.isFakeBoldText = true
        canvas.drawText("Total Items:", 300f, currentY, paint)
        paint.isFakeBoldText = false
        canvas.drawText("$totalQty Pcs", 420f, currentY, paint)

        currentY += 18f
        paint.isFakeBoldText = true
        paint.textSize = 12f
        paint.color = android.graphics.Color.parseColor("#0F3D3E")
        canvas.drawText("ESTIMASI TOTAL:", 300f, currentY, paint)
        canvas.drawText(FormatUtils.formatRupiah(totalPrice), 420f, currentY, paint)

        if (notes.isNotEmpty()) {
            paint.color = android.graphics.Color.DKGRAY
            paint.textSize = 9f
            paint.isFakeBoldText = true
            canvas.drawText("Catatan: $notes", 40f, currentY + 30f, paint)
        }

        paint.color = android.graphics.Color.GRAY
        paint.textSize = 9f
        paint.isFakeBoldText = false
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("Terima kasih telah melakukan pemesanan melalui YANSPROJECT.ID.", 297f, 790f, paint)

        pdfDocument.finishPage(page)

        return try {
            val dir = FileUtils.getExportDirectory(context, "pdf")
            val file = File(dir, "Order-Summary-${System.currentTimeMillis()}.pdf")
            val outputStream = FileOutputStream(file)
            pdfDocument.writeTo(outputStream)
            pdfDocument.close()
            outputStream.close()
            if (viewModel != null) {
                viewModel.showGlobalSnackbar("Dokumen berhasil disimpan.", "Buka Folder") {
                    FileUtils.openFolder(context, dir)
                }
            } else {
                Toast.makeText(context, "PDF disimpan di: ${file.absolutePath}", Toast.LENGTH_LONG).show()
            }
            file
        } catch (e: Exception) {
            Log.e(TAG, "Failed exporting Order Summary PDF: ${e.message}", e)
            Toast.makeText(context, "Gagal mengekspor PDF: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            null
        }
    }
}
