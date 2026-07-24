package com.yansproject.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.widget.Toast
import com.yansproject.app.data.*
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

data class ParsedStock(
    val isApparel: Boolean,
    val series: String,
    val size: String,
    val sleeve: String
)

data class ProjectItem(
    val productType: String,
    val sleeveType: String,
    val size: String,
    val qty: Int,
    val price: Double,
    val subtotal: Double
)

object ProjectItemParser {
    fun serialize(items: List<ProjectItem>): String {
        return items.joinToString(";") { item ->
            "${item.productType}|${item.sleeveType}|${item.size}|${item.qty}|${item.price}|${item.subtotal}"
        }
    }

    fun deserialize(serialized: String): List<ProjectItem> {
        if (serialized.isBlank()) return emptyList()
        val list = mutableListOf<ProjectItem>()
        val parts = serialized.split(";")
        for (part in parts) {
            val tokens = part.split("|")
            if (tokens.size >= 6) {
                list.add(
                    ProjectItem(
                        productType = tokens[0],
                        sleeveType = tokens[1],
                        size = tokens[2],
                        qty = tokens[3].toIntOrNull() ?: 0,
                        price = tokens[4].toDoubleOrNull() ?: 0.0,
                        subtotal = tokens[5].toDoubleOrNull() ?: 0.0
                    )
                )
            }
        }
        return list
    }
    
    fun getProjectDescription(fullText: String): String {
        val parts = fullText.split(" ===ITEMS_DATA=== ")
        return parts.first()
    }

    fun getProjectItems(fullText: String): List<ProjectItem> {
        val parts = fullText.split(" ===ITEMS_DATA=== ")
        if (parts.size >= 2) {
            return deserialize(parts[1])
        }
        return emptyList()
    }
}

object InvoiceItemSorter {
    val SIZE_ORDER = listOf("XS", "S", "M", "L", "XL", "XXL", "3XL", "4XL", "5XL")

    fun getSizeIndex(size: String): Int {
        val clean = size.trim().uppercase()
        val idx = SIZE_ORDER.indexOf(clean)
        return if (idx != -1) idx else 999
    }

    fun getSleeveIndex(sleeve: String): Int {
        val clean = sleeve.trim().lowercase()
        return when {
            clean.contains("pendek") || clean.contains("short") -> 0
            clean.contains("panjang") || clean.contains("long") -> 1
            else -> 2
        }
    }

    fun sortInvoiceItems(items: List<InvoiceItemDetail>): List<InvoiceItemDetail> {
        val filtered = items.filter { !it.description.startsWith("__") }
        val meta = items.filter { it.description.startsWith("__") }

        val sorted = filtered.sortedWith(
            compareBy<InvoiceItemDetail> { item ->
                val parsed = FormatUtils.parseStockItemName(item.description)
                getSleeveIndex(parsed.sleeve)
            }.thenBy { item ->
                val parsed = FormatUtils.parseStockItemName(item.description)
                getSizeIndex(parsed.size)
            }
        )
        return sorted + meta
    }
}

object FormatUtils {
    fun formatRupiah(amount: Double): String {
        return try {
            val format = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
            val formatted = format.format(amount)
            // Remove fractional cents (e.g. ,00) for clean premium dashboard presentation
            formatted.replace(",00", "").replace("Rp", "Rp ")
        } catch (e: Exception) {
            "Rp " + String.format("%,.0f", amount)
        }
    }

    fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale("id", "ID"))
        return sdf.format(Date(timestamp))
    }

    fun parseStockItemName(name: String): ParsedStock {
        val cleanName = name
            .replace("Pembelian: ", "", ignoreCase = true)
            .replace("AJIBQOBUL:", "", ignoreCase = true)
            .replace("AJIBQOBUL", "", ignoreCase = true)
            .trim()

        val parts = cleanName.split(" - ")
        if (parts.size >= 4) {
            return ParsedStock(
                isApparel = true,
                series = "${parts[0].trim()} - ${parts[1].trim()}",
                size = parts[2].trim(),
                sleeve = parts[3].trim()
            )
        } else if (parts.size >= 3) {
            return ParsedStock(
                isApparel = true,
                series = parts[0].trim(),
                size = parts[1].trim(),
                sleeve = parts[2].trim()
            )
        }
        val isApparel = name.contains("AJIBQOBUL", ignoreCase = true)
        return ParsedStock(isApparel = isApparel, series = name, size = "", sleeve = "")
    }
}

object DocumentExporter {
    fun initFolderStructure(context: Context) {
        val parentDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "YANSPROJECT.ID")
        val folders = listOf("Invoice", "Export", "Backup", "Catalog", "Project", "Report", "Log")
        try {
            if (!parentDir.exists()) {
                parentDir.mkdirs()
            }
            folders.forEach { sub ->
                val subDir = File(parentDir, sub)
                if (!subDir.exists()) {
                    subDir.mkdirs()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback inside externalFilesDir
            val fallbackParent = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "YANSPROJECT.ID")
            if (!fallbackParent.exists()) fallbackParent.mkdirs()
            folders.forEach { sub ->
                val subDir = File(fallbackParent, sub)
                if (!subDir.exists()) subDir.mkdirs()
            }
        }
    }

    fun getExportDirectory(context: Context, type: String): File {
        initFolderStructure(context)
        val parentDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "YANSPROJECT.ID")
        val subFolderName = when (type.lowercase()) {
            "invoice" -> "Invoice"
            "backup" -> "Backup"
            "export", "pdf", "png", "image" -> "Export"
            "catalog" -> "Catalog"
            "project" -> "Project"
            "report" -> "Report"
            "log" -> "Log"
            else -> "Export"
        }
        val targetDir = File(parentDir, subFolderName)
        return try {
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }
            if (targetDir.exists() && targetDir.canWrite()) {
                targetDir
            } else {
                val fallbackParent = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "YANSPROJECT.ID")
                val fallbackTarget = File(fallbackParent, subFolderName)
                if (!fallbackTarget.exists()) fallbackTarget.mkdirs()
                fallbackTarget
            }
        } catch (e: Exception) {
            val fallbackParent = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "YANSPROJECT.ID")
            val fallbackTarget = File(fallbackParent, subFolderName)
            if (!fallbackTarget.exists()) fallbackTarget.mkdirs()
            fallbackTarget
        }
    }

    fun openFolder(context: Context, folder: File) {
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                folder
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "resource/folder")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                // Fallback to general storage
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (ex: Exception) {
                Toast.makeText(context, "Tidak ada aplikasi File Manager yang kompatibel.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun exportToPdf(context: Context, invoice: Invoice, items: List<InvoiceItemDetail>, viewModel: MainViewModel? = null): File? {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 size in points
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint()

        // Draw Logo & Header
        val logoDrawable = androidx.core.content.ContextCompat.getDrawable(context, com.yansproject.app.R.drawable.ic_logo)
        if (logoDrawable != null) {
            logoDrawable.setBounds(40, 32, 75, 67) // 35x35 size
            logoDrawable.draw(canvas)
        }

        paint.color = android.graphics.Color.parseColor("#0F3D3E") // Dark Teal
        paint.textSize = 20f
        paint.isFakeBoldText = true
        canvas.drawText("YANSPROJECT.ID", 85f, 60f, paint)

        // Subheader "Premium Clothing & Custom Apparels" is removed per branding requirement

        paint.textSize = 16f
        paint.isFakeBoldText = true
        canvas.drawText("INVOICE", 400f, 60f, paint)

        paint.textSize = 10f
        paint.isFakeBoldText = false
        canvas.drawText("No: ${invoice.invoiceNumber}", 400f, 75f, paint)
        canvas.drawText("Date: ${FormatUtils.formatDate(invoice.issueDate)}", 400f, 90f, paint)

        // Draw Line
        paint.color = android.graphics.Color.GRAY
        canvas.drawLine(40f, 105f, 555f, 105f, paint)

        // Draw Customer Info
        paint.color = android.graphics.Color.BLACK
        paint.textSize = 11f
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

        // Draw Items Table Header - positioned dynamically based on fields
        val tableHeaderY = (nextY + 15f).coerceAtLeast(180f)
        paint.color = android.graphics.Color.LTGRAY
        canvas.drawRect(40f, tableHeaderY, 555f, tableHeaderY + 20f, paint)
        paint.color = android.graphics.Color.BLACK
        paint.isFakeBoldText = true
        canvas.drawText("Description", 50f, tableHeaderY + 14f, paint)
        canvas.drawText("Qty", 380f, tableHeaderY + 14f, paint)
        canvas.drawText("Price", 430f, tableHeaderY + 14f, paint)
        canvas.drawText("Total", 500f, tableHeaderY + 14f, paint)

        // Draw Items
        paint.isFakeBoldText = false
        var currentY = tableHeaderY + 40f
        val filteredItems = InvoiceItemSorter.sortInvoiceItems(items.filter { !it.description.startsWith("__") })
        for (item in filteredItems) {
            val shortDesc = if (item.description.length > 45) item.description.take(42) + "..." else item.description
            canvas.drawText(shortDesc, 50f, currentY, paint)
            canvas.drawText(item.quantity.toString(), 380f, currentY, paint)
            canvas.drawText(FormatUtils.formatRupiah(item.price), 430f, currentY, paint)
            canvas.drawText(FormatUtils.formatRupiah(item.price * item.quantity), 500f, currentY, paint)
            currentY += 20f
        }

        // Draw Summary Line
        currentY += 15f
        canvas.drawLine(40f, currentY, 555f, currentY, paint)
        currentY += 20f

        paint.isFakeBoldText = true
        canvas.drawText("Subtotal:", 380f, currentY, paint)
        paint.isFakeBoldText = false
        canvas.drawText(FormatUtils.formatRupiah(invoice.totalAmount + invoice.discount), 480f, currentY, paint)
        
        currentY += 18f
        paint.isFakeBoldText = true
        canvas.drawText("Diskon:", 380f, currentY, paint)
        paint.isFakeBoldText = false
        canvas.drawText("- " + FormatUtils.formatRupiah(invoice.discount), 480f, currentY, paint)

        currentY += 18f
        paint.isFakeBoldText = true
        canvas.drawText("Uang Muka (DP):", 380f, currentY, paint)
        paint.isFakeBoldText = false
        canvas.drawText(FormatUtils.formatRupiah(invoice.dpAmount), 480f, currentY, paint)

        currentY += 18f
        paint.isFakeBoldText = true
        canvas.drawText("Sisa Tagihan:", 380f, currentY, paint)
        paint.isFakeBoldText = false
        canvas.drawText(FormatUtils.formatRupiah(invoice.remainingPayment), 480f, currentY, paint)

        currentY += 22f
        paint.isFakeBoldText = true
        paint.textSize = 12f
        canvas.drawText("GRAND TOTAL:", 380f, currentY, paint)
        canvas.drawText(FormatUtils.formatRupiah(invoice.totalAmount), 480f, currentY, paint)

        // Draw Watermark
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

        // Admin Note - Render ONLY if present, dynamic layout
        val noteItem = items.find { it.description.startsWith("__NOTE__:") }
        val note = noteItem?.description?.removePrefix("__NOTE__:")?.trim()
        if (!note.isNullOrBlank()) {
            paint.textSize = 9f
            paint.color = android.graphics.Color.DKGRAY
            paint.isFakeBoldText = true
            canvas.drawText("Catatan Admin: $note", 40f, currentY + 30f, paint)
            currentY += 45f
        }

        // Footer Brand
        paint.textSize = 9f
        paint.isFakeBoldText = false
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("Terima kasih telah mempercayakan kebutuhan apparel Anda kepada YANSPROJECT.ID.", 297f, 790f, paint)

        pdfDocument.finishPage(page)

        return try {
            val safeNum = invoice.invoiceNumber.replace("/", "_").replace("\\", "_")
            val dir = getExportDirectory(context, "invoice")
            val file = File(dir, "Invoice-${safeNum}.pdf")
            val outputStream = FileOutputStream(file)
            pdfDocument.writeTo(outputStream)
            pdfDocument.close()
            outputStream.close()

            // Also copy to public Downloads directory for immediate user download access
            try {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val downloadFile = File(downloadsDir, "Invoice-${safeNum}.pdf")
                file.copyTo(downloadFile, overwrite = true)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            if (viewModel != null) {
                viewModel.showGlobalSnackbar("Ringkasan PDF Invoice-${safeNum} berhasil diunduh.", "Buka Folder") {
                    openFolder(context, dir)
                }
            } else {
                Toast.makeText(context, "Ringkasan PDF Invoice-${safeNum} berhasil diunduh ke Downloads!", Toast.LENGTH_LONG).show()
            }

            // Log Export PDF Event
            val params = android.os.Bundle().apply {
                putString("invoice_number", invoice.invoiceNumber)
                putString("type", "Invoice")
            }
            com.yansproject.app.data.FirebaseSyncManager.logEvent("export_pdf", params)
            file
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Gagal mengekspor PDF: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            null
        }
    }

    fun exportToPng(context: Context, invoice: Invoice, items: List<InvoiceItemDetail>, viewModel: MainViewModel? = null): File? {
        val width = 800
        val height = 1100
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()

        // Fill White background
        paint.color = android.graphics.Color.WHITE
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        // Draw elegant printable invoice
        paint.color = android.graphics.Color.rgb(33, 33, 33)
        paint.textSize = 26f
        paint.isFakeBoldText = true
        canvas.drawText("YANSPROJECT.ID", 60f, 80f, paint)

        // Subheader "Premium Clothing & Custom Apparels" is removed per branding requirement

        paint.textSize = 22f
        paint.isFakeBoldText = true
        canvas.drawText("INVOICE", 550f, 80f, paint)

        paint.textSize = 13f
        paint.isFakeBoldText = false
        canvas.drawText("No: ${invoice.invoiceNumber}", 550f, 105f, paint)
        canvas.drawText("Date: ${FormatUtils.formatDate(invoice.issueDate)}", 550f, 125f, paint)

        paint.color = android.graphics.Color.LTGRAY
        canvas.drawLine(60f, 150f, 740f, 150f, paint)

        // Customer Info
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

        // Draw Items Table Header - dynamic position
        val tableHeaderY = (nextY + 20f).coerceAtLeast(260f)
        paint.color = android.graphics.Color.rgb(240, 240, 240)
        canvas.drawRect(60f, tableHeaderY, 740f, tableHeaderY + 30f, paint)
        paint.color = android.graphics.Color.BLACK
        paint.isFakeBoldText = true
        canvas.drawText("Description", 75f, tableHeaderY + 20f, paint)
        canvas.drawText("Qty", 520f, tableHeaderY + 20f, paint)
        canvas.drawText("Price", 580f, tableHeaderY + 20f, paint)
        canvas.drawText("Total", 670f, tableHeaderY + 20f, paint)

        paint.isFakeBoldText = false
        var currentY = tableHeaderY + 50f
        val filteredItems = items.filter { !it.description.startsWith("__") }
        for (item in filteredItems) {
            val shortDesc = if (item.description.length > 45) item.description.take(42) + "..." else item.description
            canvas.drawText(shortDesc, 75f, currentY, paint)
            canvas.drawText(item.quantity.toString(), 520f, currentY, paint)
            canvas.drawText(FormatUtils.formatRupiah(item.price), 580f, currentY, paint)
            canvas.drawText(FormatUtils.formatRupiah(item.price * item.quantity), 670f, currentY, paint)
            currentY += 25f
        }

        currentY += 20f
        canvas.drawLine(60f, currentY, 740f, currentY, paint)
        currentY += 30f

        paint.isFakeBoldText = true
        canvas.drawText("Subtotal:", 500f, currentY, paint)
        paint.isFakeBoldText = false
        canvas.drawText(FormatUtils.formatRupiah(invoice.totalAmount + invoice.discount), 650f, currentY, paint)

        currentY += 25f
        paint.isFakeBoldText = true
        canvas.drawText("Diskon:", 500f, currentY, paint)
        paint.isFakeBoldText = false
        canvas.drawText("- " + FormatUtils.formatRupiah(invoice.discount), 650f, currentY, paint)

        currentY += 25f
        paint.isFakeBoldText = true
        canvas.drawText("Uang Muka (DP):", 500f, currentY, paint)
        paint.isFakeBoldText = false
        canvas.drawText(FormatUtils.formatRupiah(invoice.dpAmount), 650f, currentY, paint)

        currentY += 25f
        paint.isFakeBoldText = true
        canvas.drawText("Sisa Tagihan:", 500f, currentY, paint)
        paint.isFakeBoldText = false
        canvas.drawText(FormatUtils.formatRupiah(invoice.remainingPayment), 650f, currentY, paint)

        currentY += 35f
        paint.isFakeBoldText = true
        paint.textSize = 15f
        canvas.drawText("GRAND TOTAL:", 500f, currentY, paint)
        canvas.drawText(FormatUtils.formatRupiah(invoice.totalAmount), 650f, currentY, paint)

        // Draw Watermark
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

        // Admin Note - Render ONLY if present, dynamic layout
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

        // Footer Brand
        paint.textSize = 12f
        paint.isFakeBoldText = false
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("Terima kasih telah mempercayakan kebutuhan apparel Anda kepada YANSPROJECT.ID.", 400f, 1040f, paint)

        return try {
            val dir = getExportDirectory(context, "invoice")
            val file = File(dir, "Invoice-${invoice.invoiceNumber}.png")
            val outputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            outputStream.close()
            if (viewModel != null) {
                viewModel.showGlobalSnackbar("Dokumen berhasil disimpan.", "Buka Folder") {
                    openFolder(context, dir)
                }
            } else {
                Toast.makeText(context, "Gambar PNG disimpan di: ${file.absolutePath}", Toast.LENGTH_LONG).show()
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Gagal mengekspor Gambar: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
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
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 size in points
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint()

        // Header Background
        paint.color = android.graphics.Color.parseColor("#121212") // Dark theme color
        canvas.drawRect(0f, 0f, 595f, 110f, paint)

        // Draw Logo
        val logoDrawable = androidx.core.content.ContextCompat.getDrawable(context, com.yansproject.app.R.drawable.ic_logo)
        if (logoDrawable != null) {
            logoDrawable.setBounds(40, 25, 80, 65) // 40x40 size
            logoDrawable.draw(canvas)
        }

        // Store Name
        paint.color = android.graphics.Color.parseColor("#C6A15B") // AgedGold
        paint.textSize = 22f
        paint.isFakeBoldText = true
        canvas.drawText(AppSettings.getStoreName(context).uppercase(), 95f, 50f, paint)

        paint.color = android.graphics.Color.WHITE
        paint.textSize = 10f
        paint.isFakeBoldText = false
        canvas.drawText("RINGKASAN LAPORAN KEUANGAN & OPERASIONAL", 95f, 70f, paint)
        canvas.drawText("Periode: $period | Tanggal Cetak: ${FormatUtils.formatDate(System.currentTimeMillis())}", 95f, 85f, paint)

        // Reset Paint
        paint.color = android.graphics.Color.BLACK
        
        // --- 1. RINGKASAN METRIK ---
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

        // Column 2 inside metrics
        paint.isFakeBoldText = false
        canvas.drawText("Project Custom Aktif:", 350f, 170f, paint)
        paint.isFakeBoldText = true
        canvas.drawText("$activeProjectsCount Project", 480f, 170f, paint)
        
        paint.isFakeBoldText = false
        canvas.drawText("Barang Stok Menipis:", 350f, 185f, paint)
        paint.isFakeBoldText = true
        canvas.drawText("$lowStockCount Item", 480f, 185f, paint)

        // --- 2. PIUTANG (INVOICE BELUM LUNAS) ---
        var yPos = 245f
        paint.color = android.graphics.Color.BLACK
        paint.textSize = 12f
        paint.isFakeBoldText = true
        canvas.drawText("2. DAFTAR PIUTANG (BELUM LUNAS)", 40f, yPos, paint)
        
        yPos += 12f
        paint.color = android.graphics.Color.parseColor("#C39B4B") // AgedGold/Brown accent
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

        // --- 3. PROJECT AKTIF ---
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

        // Footer Note
        yPos = 780f
        paint.color = android.graphics.Color.GRAY
        canvas.drawLine(40f, yPos, 555f, yPos, paint)
        paint.textSize = 8f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("Dokumen ini dihasilkan secara otomatis oleh YANSPROJECT.ID. All Rights Reserved.", 297f, yPos + 15f, paint)

        pdfDocument.finishPage(page)

        // Save file
        try {
            val documentsDir = getExportDirectory(context, "export")
            val file = File(documentsDir, "YANS_LAPORAN_KEUANGAN_${System.currentTimeMillis()}.pdf")
            pdfDocument.writeTo(file.outputStream())
            if (viewModel != null) {
                viewModel.showGlobalSnackbar("Dokumen berhasil disimpan.", "Buka Folder") {
                    openFolder(context, documentsDir)
                }
            } else {
                Toast.makeText(context, "PDF Laporan berhasil disimpan di: ${file.name}", Toast.LENGTH_LONG).show()
            }

            // Log Export PDF Event
            val params = android.os.Bundle().apply {
                putString("type", "Financial_Summary")
                putString("filename", file.name)
            }
            com.yansproject.app.data.FirebaseSyncManager.logEvent("export_pdf", params)
        } catch (e: Exception) {
            Toast.makeText(context, "Gagal ekspor PDF: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        } finally {
            pdfDocument.close()
        }
    }

    fun exportOrderSummaryToPdf(
        context: Context,
        clientName: String,
        clientPhone: String,
        items: List<com.yansproject.app.ui.MemberCartItem>,
        notes: String,
        viewModel: MainViewModel? = null
    ): File? {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint()

        // Logo
        val logoDrawable = androidx.core.content.ContextCompat.getDrawable(context, com.yansproject.app.R.drawable.ic_logo)
        if (logoDrawable != null) {
            logoDrawable.setBounds(40, 32, 75, 67)
            logoDrawable.draw(canvas)
        }

        paint.color = android.graphics.Color.parseColor("#0F3D3E") // Dark Teal
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

        // Line Divider
        paint.color = android.graphics.Color.GRAY
        canvas.drawLine(40f, 105f, 555f, 105f, paint)

        // Customer Info
        paint.color = android.graphics.Color.BLACK
        paint.textSize = 11f
        paint.isFakeBoldText = true
        canvas.drawText("CUSTOMER INFO:", 40f, 130f, paint)
        paint.isFakeBoldText = false
        canvas.drawText("Name: $clientName", 40f, 145f, paint)
        canvas.drawText("WhatsApp: $clientPhone", 40f, 160f, paint)

        // Table Header
        val tableHeaderY = 185f
        paint.color = android.graphics.Color.LTGRAY
        canvas.drawRect(40f, tableHeaderY, 555f, tableHeaderY + 20f, paint)
        paint.color = android.graphics.Color.BLACK
        paint.isFakeBoldText = true
        canvas.drawText("Item / Product Details", 50f, tableHeaderY + 14f, paint)
        canvas.drawText("Qty", 350f, tableHeaderY + 14f, paint)
        canvas.drawText("Price", 410f, tableHeaderY + 14f, paint)
        canvas.drawText("Total", 490f, tableHeaderY + 14f, paint)

        // Items
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

        // Summary Line
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

        // Footer Brand
        paint.color = android.graphics.Color.GRAY
        paint.textSize = 9f
        paint.isFakeBoldText = false
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("Terima kasih telah melakukan pemesanan melalui YANSPROJECT.ID.", 297f, 790f, paint)

        pdfDocument.finishPage(page)

        return try {
            val dir = getExportDirectory(context, "pdf")
            val file = File(dir, "Order-Summary-${System.currentTimeMillis()}.pdf")
            val outputStream = FileOutputStream(file)
            pdfDocument.writeTo(outputStream)
            pdfDocument.close()
            outputStream.close()
            if (viewModel != null) {
                viewModel.showGlobalSnackbar("Dokumen berhasil disimpan.", "Buka Folder") {
                    openFolder(context, dir)
                }
            } else {
                Toast.makeText(context, "PDF disimpan di: ${file.absolutePath}", Toast.LENGTH_LONG).show()
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Gagal mengekspor PDF: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            null
        }
    }

    fun exportOrderSummaryToPng(
        context: Context,
        clientName: String,
        clientPhone: String,
        items: List<com.yansproject.app.ui.MemberCartItem>,
        notes: String,
        viewModel: MainViewModel? = null
    ): File? {
        val width = 800
        val height = 1100
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()

        // Background
        paint.color = android.graphics.Color.WHITE
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        // Logo / Title
        paint.color = android.graphics.Color.rgb(15, 61, 62) // Dark Teal
        paint.textSize = 26f
        paint.isFakeBoldText = true
        canvas.drawText("YANSPROJECT.ID", 60f, 80f, paint)

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

        // Customer Info
        paint.color = android.graphics.Color.BLACK
        paint.textSize = 14f
        paint.isFakeBoldText = true
        canvas.drawText("CUSTOMER INFO:", 60f, 180f, paint)
        paint.isFakeBoldText = false
        canvas.drawText("Name: $clientName", 60f, 205f, paint)
        canvas.drawText("WhatsApp: $clientPhone", 60f, 225f, paint)

        // Table Header
        val tableHeaderY = 255f
        paint.color = android.graphics.Color.rgb(240, 240, 240)
        canvas.drawRect(60f, tableHeaderY, 740f, tableHeaderY + 30f, paint)
        paint.color = android.graphics.Color.BLACK
        paint.isFakeBoldText = true
        canvas.drawText("Item / Product Details", 75f, tableHeaderY + 20f, paint)
        canvas.drawText("Qty", 480f, tableHeaderY + 20f, paint)
        canvas.drawText("Price", 560f, tableHeaderY + 20f, paint)
        canvas.drawText("Total", 660f, tableHeaderY + 20f, paint)

        // Items
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

        // Footer
        paint.color = android.graphics.Color.GRAY
        paint.textSize = 12f
        paint.isFakeBoldText = false
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("Terima kasih telah melakukan pemesanan melalui YANSPROJECT.ID.", 400f, 1040f, paint)

        return try {
            val dir = getExportDirectory(context, "image")
            val file = File(dir, "Order-Summary-${System.currentTimeMillis()}.png")
            val outputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            outputStream.close()
            if (viewModel != null) {
                viewModel.showGlobalSnackbar("Dokumen berhasil disimpan.", "Buka Folder") {
                    openFolder(context, dir)
                }
            } else {
                Toast.makeText(context, "Gambar disimpan di: ${file.absolutePath}", Toast.LENGTH_LONG).show()
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Gagal mengekspor Gambar: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            null
        }
    }
}

object YansBluetoothPrinter {
    private val SPP_UUID = java.util.UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    fun findBluetoothPrinter(): android.bluetooth.BluetoothDevice? {
        val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter() ?: return null
        if (!adapter.isEnabled) return null
        val pairedDevices = try {
            adapter.bondedDevices
        } catch (e: SecurityException) {
            null
        } ?: return null

        val printerKeywords = listOf("printer", "thermal", "pos", "mpt", "rpp", "xp", "bluetooth")
        for (device in pairedDevices) {
            val name = try { device.name } catch (e: SecurityException) { "" } ?: ""
            if (printerKeywords.any { name.lowercase().contains(it) }) {
                return device
            }
        }
        return pairedDevices.firstOrNull()
    }

    fun printInvoice(context: android.content.Context, invoice: com.yansproject.app.data.Invoice, items: List<com.yansproject.app.data.InvoiceItemDetail>) {
        val device = findBluetoothPrinter()
        if (device == null) {
            android.widget.Toast.makeText(context, "Printer Thermal Bluetooth tidak ditemukan. Pastikan sudah pairing Bluetooth Printer Anda.", android.widget.Toast.LENGTH_LONG).show()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            var socket: android.bluetooth.BluetoothSocket? = null
            try {
                socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                socket.connect()

                val os = socket.outputStream

                val initPrinter = byteArrayOf(0x1B, 0x40)
                val alignCenter = byteArrayOf(0x1B, 0x61, 0x01)
                val alignLeft = byteArrayOf(0x1B, 0x61, 0x00)
                val boldOn = byteArrayOf(0x1B, 0x45, 0x01)
                val boldOff = byteArrayOf(0x1B, 0x45, 0x00)
                val doubleSizeOn = byteArrayOf(0x1D, 0x21, 0x11)
                val doubleSizeOff = byteArrayOf(0x1D, 0x21, 0x00)

                os.write(initPrinter)

                os.write(alignCenter)
                os.write(doubleSizeOn)
                os.write(boldOn)
                os.write("YANSPROJECT.ID\n".toByteArray(java.nio.charset.Charset.forName("GBK")))
                os.write(doubleSizeOff)
                os.write(boldOff)
                os.write("Custom Apparel & Sablon\n".toByteArray(java.nio.charset.Charset.forName("GBK")))
                os.write("WhatsApp: ${com.yansproject.app.ui.AppSettings.getWhatsApp(context)}\n".toByteArray(java.nio.charset.Charset.forName("GBK")))
                os.write("--------------------------------\n".toByteArray(java.nio.charset.Charset.forName("GBK")))

                os.write(alignLeft)
                os.write("No: ${invoice.invoiceNumber}\n".toByteArray(java.nio.charset.Charset.forName("GBK")))
                os.write("Tgl: ${com.yansproject.app.ui.FormatUtils.formatDate(invoice.issueDate)}\n".toByteArray(java.nio.charset.Charset.forName("GBK")))
                os.write("Klien: ${invoice.clientName}\n".toByteArray(java.nio.charset.Charset.forName("GBK")))
                if (invoice.clientPhone.isNotEmpty()) {
                    os.write("WA: ${invoice.clientPhone}\n".toByteArray(java.nio.charset.Charset.forName("GBK")))
                }
                os.write("--------------------------------\n".toByteArray(java.nio.charset.Charset.forName("GBK")))

                val cleanItems = items.filter { !it.description.startsWith("__ADDRESS__:") && !it.description.startsWith("__NOTE__:") }
                for (item in cleanItems) {
                    val desc = item.description
                    val qty = item.quantity
                    val price = item.price
                    val sub = qty * price
                    os.write("$desc\n".toByteArray(java.nio.charset.Charset.forName("GBK")))
                    os.write("   $qty x ${com.yansproject.app.ui.FormatUtils.formatRupiah(price)} = ${com.yansproject.app.ui.FormatUtils.formatRupiah(sub)}\n".toByteArray(java.nio.charset.Charset.forName("GBK")))
                }
                os.write("--------------------------------\n".toByteArray(java.nio.charset.Charset.forName("GBK")))

                if (invoice.discount > 0.0) {
                    os.write("Diskon: -${com.yansproject.app.ui.FormatUtils.formatRupiah(invoice.discount)}\n".toByteArray(java.nio.charset.Charset.forName("GBK")))
                }
                os.write(boldOn)
                os.write("TOTAL: ${com.yansproject.app.ui.FormatUtils.formatRupiah(invoice.totalAmount)}\n".toByteArray(java.nio.charset.Charset.forName("GBK")))
                os.write("Bayar: ${com.yansproject.app.ui.FormatUtils.formatRupiah(invoice.paidAmount)}\n".toByteArray(java.nio.charset.Charset.forName("GBK")))
                if (invoice.dpAmount > 0.0 && invoice.status == "DP") {
                    os.write("DP: ${com.yansproject.app.ui.FormatUtils.formatRupiah(invoice.dpAmount)}\n".toByteArray(java.nio.charset.Charset.forName("GBK")))
                }
                os.write("Sisa: ${com.yansproject.app.ui.FormatUtils.formatRupiah(invoice.remainingPayment)}\n".toByteArray(java.nio.charset.Charset.forName("GBK")))
                os.write("Status: ${invoice.status}\n".toByteArray(java.nio.charset.Charset.forName("GBK")))
                os.write(boldOff)
                os.write("--------------------------------\n".toByteArray(java.nio.charset.Charset.forName("GBK")))

                os.write(alignCenter)
                os.write("Terima kasih atas pesanan Anda!\n".toByteArray(java.nio.charset.Charset.forName("GBK")))
                os.write("Barang yang sudah dibeli\n".toByteArray(java.nio.charset.Charset.forName("GBK")))
                os.write("tidak dapat ditukar/dikembalikan.\n".toByteArray(java.nio.charset.Charset.forName("GBK")))
                os.write("\n\n\n\n".toByteArray())

                os.flush()
                os.close()

                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Cetak Invoice Berhasil!", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Koneksi Printer Gagal: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            } finally {
                try {
                    socket?.close()
                } catch (se: Exception) {}
            }
        }
    }

    fun printProject(context: android.content.Context, project: com.yansproject.app.data.ProjectCustom) {
        val device = findBluetoothPrinter()
        if (device == null) {
            android.widget.Toast.makeText(context, "Printer Thermal Bluetooth tidak ditemukan. Pastikan sudah pairing Bluetooth Printer Anda.", android.widget.Toast.LENGTH_LONG).show()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            var socket: android.bluetooth.BluetoothSocket? = null
            try {
                socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                socket.connect()

                val os = socket.outputStream

                val initPrinter = byteArrayOf(0x1B, 0x40)
                val alignCenter = byteArrayOf(0x1B, 0x61, 0x01)
                val alignLeft = byteArrayOf(0x1B, 0x61, 0x00)
                val boldOn = byteArrayOf(0x1B, 0x45, 0x01)
                val boldOff = byteArrayOf(0x1B, 0x45, 0x00)
                val doubleSizeOn = byteArrayOf(0x1D, 0x21, 0x11)
                val doubleSizeOff = byteArrayOf(0x1D, 0x21, 0x00)

                os.write(initPrinter)

                os.write(alignCenter)
                os.write(doubleSizeOn)
                os.write(boldOn)
                os.write("SURAT PERINTAH KERJA\n".toByteArray(java.nio.charset.Charset.forName("GBK")))
                os.write(doubleSizeOff)
                os.write("YANSPROJECT.ID\n".toByteArray(java.nio.charset.Charset.forName("GBK")))
                os.write(boldOff)
                os.write("--------------------------------\n".toByteArray(java.nio.charset.Charset.forName("GBK")))

                os.write(alignLeft)
                os.write("Project: ${project.projectName}\n".toByteArray(java.nio.charset.Charset.forName("GBK")))
                os.write("Klien: ${project.clientName}\n".toByteArray(java.nio.charset.Charset.forName("GBK")))
                os.write("Deadline: ${com.yansproject.app.ui.FormatUtils.formatDate(project.endDate)}\n".toByteArray(java.nio.charset.Charset.forName("GBK")))
                os.write("Status: ${project.status}\n".toByteArray(java.nio.charset.Charset.forName("GBK")))
                os.write("--------------------------------\n".toByteArray(java.nio.charset.Charset.forName("GBK")))

                val items = com.yansproject.app.ui.ProjectItemParser.getProjectItems(project.description)
                val rawDesc = com.yansproject.app.ui.ProjectItemParser.getProjectDescription(project.description)
                if (rawDesc.trim().isNotEmpty()) {
                    os.write("Deskripsi:\n$rawDesc\n\n".toByteArray(java.nio.charset.Charset.forName("GBK")))
                }

                if (items.isNotEmpty()) {
                    os.write(boldOn)
                    os.write("DAFTAR WORKFLOW / PRODUKSI:\n".toByteArray(java.nio.charset.Charset.forName("GBK")))
                    os.write(boldOff)
                    for (item in items) {
                        os.write("- ${item.productType} (${item.sleeveType})\n".toByteArray(java.nio.charset.Charset.forName("GBK")))
                        os.write("  Size: ${item.size} | Qty: ${item.qty} Pcs\n".toByteArray(java.nio.charset.Charset.forName("GBK")))
                    }
                }
                os.write("--------------------------------\n".toByteArray(java.nio.charset.Charset.forName("GBK")))

                os.write(alignCenter)
                os.write("SPK YansProject.id\n".toByteArray(java.nio.charset.Charset.forName("GBK")))
                os.write("Harap diproduksi tepat waktu!\n".toByteArray(java.nio.charset.Charset.forName("GBK")))
                os.write("\n\n\n\n".toByteArray())

                os.flush()
                os.close()

                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Cetak SPK Berhasil!", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Koneksi Printer Gagal: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            } finally {
                try {
                    socket?.close()
                } catch (se: Exception) {}
            }
        }
    }
}
