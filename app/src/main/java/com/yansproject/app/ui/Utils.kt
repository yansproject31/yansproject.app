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
import android.graphics.Typeface
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

object FontUtils {
    @Volatile
    private var cachedArabicFontFamily: FontFamily? = null
    @Volatile
    private var cachedRuqaaFontFamily: FontFamily? = null
    @Volatile
    private var cachedScheherazadeFontFamily: FontFamily? = null
    @Volatile
    private var cachedAmiriFontFamily: FontFamily? = null

    private fun buildComposeFontFamily(context: Context, assetPath: String): FontFamily? {
        return try {
            // HAPUS BARIS context.assets.open(assetPath).close() AGAR TIDAK FREEZE/ANR DI UI THREAD
            FontFamily(
                Font(assetManager = context.assets, path = assetPath, weight = FontWeight.Normal),
                Font(assetManager = context.assets, path = assetPath, weight = FontWeight.Medium),
                Font(assetManager = context.assets, path = assetPath, weight = FontWeight.SemiBold),
                Font(assetManager = context.assets, path = assetPath, weight = FontWeight.Bold),
                Font(assetManager = context.assets, path = assetPath, weight = FontWeight.ExtraBold),
                Font(assetManager = context.assets, path = assetPath, weight = FontWeight.Black)
            )
        } catch (_: Exception) {
            null
        }
    }

    fun getPremiumArabicFontFamily(context: Context): FontFamily {
        cachedArabicFontFamily?.let { return it }

        val assetFontNames = listOf(
            "fonts/aref_ruqaa_bold.ttf",
            "fonts/scheherazade_bold.ttf",
            "fonts/amiri_quran.ttf"
        )
        for (assetPath in assetFontNames) {
            val family = buildComposeFontFamily(context, assetPath)
            if (family != null) {
                cachedArabicFontFamily = family
                return family
            }
        }

        var selectedTf: Typeface? = null
        val typefaces = listOf("serif-arabic", "sans-serif-arabic", "arabic", "amiri", "scheherazade", "cairo")
        for (fontName in typefaces) {
            try {
                val tf = Typeface.create(fontName, Typeface.BOLD)
                if (tf != Typeface.DEFAULT) {
                    selectedTf = tf
                    break
                }
            } catch (_: Exception) {}
        }
        val fontFamily = selectedTf?.let { FontFamily(it) } ?: FontFamily.Serif
        cachedArabicFontFamily = fontFamily
        return fontFamily
    }

    fun getArabicRuqaaCalligraphyFontFamily(context: Context): FontFamily {
        cachedRuqaaFontFamily?.let { return it }
        val family = buildComposeFontFamily(context, "fonts/aref_ruqaa_bold.ttf") ?: getPremiumArabicFontFamily(context)
        cachedRuqaaFontFamily = family
        return family
    }

    fun getArabicScheherazadeFontFamily(context: Context): FontFamily {
        cachedScheherazadeFontFamily?.let { return it }
        val family = buildComposeFontFamily(context, "fonts/scheherazade_bold.ttf") ?: getPremiumArabicFontFamily(context)
        cachedScheherazadeFontFamily = family
        return family
    }

    fun getArabicAmiriQuranFontFamily(context: Context): FontFamily {
        cachedAmiriFontFamily?.let { return it }
        val family = buildComposeFontFamily(context, "fonts/amiri_quran.ttf") ?: getPremiumArabicFontFamily(context)
        cachedAmiriFontFamily = family
        return family
    }
}

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
    fun serialize(items: List<ProjectItem>?): String {
        if (items == null) return ""
        return items.joinToString(";") { item ->
            "${item.productType}|${item.sleeveType}|${item.size}|${item.qty}|${item.price}|${item.subtotal}"
        }
    }

    fun deserialize(serialized: String?): List<ProjectItem> {
        if (serialized.isNullOrBlank()) return emptyList()
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
    
    fun getProjectDescription(fullText: String?): String {
        if (fullText.isNullOrBlank()) return ""
        val parts = fullText.split(" ===ITEMS_DATA=== ")
        return parts.firstOrNull() ?: ""
    }

    fun getProjectItems(fullText: String?): List<ProjectItem> {
        if (fullText.isNullOrBlank()) return emptyList()
        val parts = fullText.split(" ===ITEMS_DATA=== ")
        if (parts.size >= 2) {
            return deserialize(parts[1])
        }
        return emptyList()
    }
}

object InvoiceItemSorter {
    val SIZE_ORDER = listOf("XS", "S", "M", "L", "XL", "XXL", "3XL", "4XL")

    fun getSizeIndex(size: String?): Int {
        if (size.isNullOrBlank()) return 999
        val clean = size.trim().uppercase()
        val idx = SIZE_ORDER.indexOf(clean)
        return if (idx != -1) idx else 999
    }

    fun getSleeveIndex(sleeve: String?): Int {
        if (sleeve.isNullOrBlank()) return 2
        val clean = sleeve.trim().lowercase()
        return when {
            clean.contains("pendek") || clean.contains("short") -> 0
            clean.contains("panjang") || clean.contains("long") -> 1
            else -> 2
        }
    }

    fun sortInvoiceItems(items: List<InvoiceItemDetail>?): List<InvoiceItemDetail> {
        if (items.isNullOrEmpty()) return emptyList()
        val filtered = items.filter { !(it.description ?: "").startsWith("__") }
        val meta = items.filter { (it.description ?: "").startsWith("__") }

        val sorted = try {
            filtered.sortedWith(
                compareBy<InvoiceItemDetail> { item ->
                    val parsed = FormatUtils.parseStockItemName(item.description ?: "")
                    getSleeveIndex(parsed.sleeve)
                }.thenBy { item ->
                    val parsed = FormatUtils.parseStockItemName(item.description ?: "")
                    getSizeIndex(parsed.size)
                }
            )
        } catch (e: Exception) {
            filtered
        }
        return sorted + meta
    }
}

object FormatUtils {
    fun formatRupiah(amount: Double?): String {
        val valAmount = amount ?: 0.0
        return try {
            val format = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
            val formatted = format.format(valAmount)
            formatted.replace(",00", "").replace("Rp", "Rp ")
        } catch (e: Exception) {
            "Rp " + String.format("%,.0f", valAmount)
        }
    }

    fun formatDate(timestamp: Long?): String {
        val valTime = timestamp ?: System.currentTimeMillis()
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale("id", "ID"))
        return sdf.format(Date(valTime))
    }

    fun parseStockItemName(name: String?): ParsedStock {
        val safeName = name ?: ""
        val cleanName = safeName
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
        val isApparel = safeName.contains("AJIBQOBUL", ignoreCase = true)
        return ParsedStock(isApparel = isApparel, series = safeName, size = "", sleeve = "")
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
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint()

        val logoDrawable = androidx.core.content.ContextCompat.getDrawable(context, com.yansproject.app.R.drawable.ic_logo)
        if (logoDrawable != null) {
            logoDrawable.setBounds(40, 32, 75, 67)
            logoDrawable.draw(canvas)
        }

        paint.color = android.graphics.Color.parseColor("#0F3D3E")
        paint.textSize = 20f
        paint.isFakeBoldText = true
        canvas.drawText("YANSPROJECT.ID", 85f, 60f, paint)

        paint.textSize = 16f
        paint.isFakeBoldText = true
        canvas.drawText("INVOICE", 400f, 60f, paint)

        paint.textSize = 10f
        paint.isFakeBoldText = false
        canvas.drawText("No: ${invoice.invoiceNumber ?: ""}", 400f, 75f, paint)
        canvas.drawText("Date: ${FormatUtils.formatDate(invoice.issueDate)}", 400f, 90f, paint)

        paint.color = android.graphics.Color.GRAY
        canvas.drawLine(40f, 105f, 555f, 105f, paint)

        paint.color = android.graphics.Color.BLACK
        paint.textSize = 11f
        paint.isFakeBoldText = true
        canvas.drawText("CUSTOMER INFO:", 40f, 130f, paint)
        paint.isFakeBoldText = false
        canvas.drawText("Name: ${invoice.clientName ?: ""}", 40f, 145f, paint)
        
        var nextY = 160f
        if (!invoice.clientPhone.isNullOrBlank()) {
            canvas.drawText("WhatsApp: ${invoice.clientPhone}", 40f, nextY, paint)
            nextY += 15f
        }
        
        val addressItem = items.find { (it.description ?: "").startsWith("__ADDRESS__:") }
        val address = addressItem?.description?.removePrefix("__ADDRESS__:")?.trim()
        if (!address.isNullOrBlank()) {
            canvas.drawText("Address: $address", 40f, nextY, paint)
            nextY += 15f
        }

        val tableHeaderY = (nextY + 15f).coerceAtLeast(180f)
        paint.color = android.graphics.Color.LTGRAY
        canvas.drawRect(40f, tableHeaderY, 555f, tableHeaderY + 20f, paint)
        paint.color = android.graphics.Color.BLACK
        paint.isFakeBoldText = true
        canvas.drawText("Description", 50f, tableHeaderY + 14f, paint)
        canvas.drawText("Qty", 380f, tableHeaderY + 14f, paint)
        canvas.drawText("Price", 430f, tableHeaderY + 14f, paint)
        canvas.drawText("Total", 500f, tableHeaderY + 14f, paint)

        paint.isFakeBoldText = false
        var currentY = tableHeaderY + 40f
        val filteredItems = InvoiceItemSorter.sortInvoiceItems(items.filter { !(it.description ?: "").startsWith("__") })
        for (item in filteredItems) {
            val descText = item.description ?: ""
            val shortDesc = if (descText.length > 45) descText.take(42) + "..." else descText
            canvas.drawText(shortDesc, 50f, currentY, paint)
            canvas.drawText(item.quantity.toString(), 380f, currentY, paint)
            canvas.drawText(FormatUtils.formatRupiah(item.price), 430f, currentY, paint)
            canvas.drawText(FormatUtils.formatRupiah(item.price * item.quantity), 500f, currentY, paint)
            currentY += 20f
        }

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

        paint.textSize = 50f
        val safeStatus = invoice.status ?: ""
        paint.color = when (safeStatus) {
            "LUNAS" -> android.graphics.Color.argb(35, 46, 125, 50)
            "DP" -> android.graphics.Color.argb(35, 239, 108, 0)
            "BATAL" -> android.graphics.Color.argb(35, 120, 120, 120)
            else -> android.graphics.Color.argb(35, 198, 40, 40)
        }
        paint.isFakeBoldText = true
        canvas.save()
        canvas.rotate(-35f, 300f, 500f)
        canvas.drawText(safeStatus, 200f, 500f, paint)
        canvas.restore()

        val noteItem = items.find { (it.description ?: "").startsWith("__NOTE__:") }
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
        canvas.drawText("Terima kasih telah mempercayakan kebutuhan apparel Anda kepada YANSPROJECT.ID.", 297f, 790f, paint)

        pdfDocument.finishPage(page)

        return try {
            val safeNum = (invoice.invoiceNumber ?: "INV").replace("/", "_").replace("\\", "_")
            val dir = getExportDirectory(context, "invoice")
            val file = File(dir, "Invoice-${safeNum}.pdf")
            val outputStream = FileOutputStream(file)
            pdfDocument.writeTo(outputStream)
            pdfDocument.close()
            outputStream.close()

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

            val params = android.os.Bundle().apply {
                putString("invoice_number", invoice.invoiceNumber ?: "")
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

        paint.color = android.graphics.Color.WHITE
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        paint.color = android.graphics.Color.rgb(33, 33, 33)
        paint.textSize = 26f
        paint.isFakeBoldText = true
        canvas.drawText("YANSPROJECT.ID", 60f, 80f, paint)

        paint.textSize = 22f
        paint.isFakeBoldText = true
        canvas.drawText("INVOICE", 550f, 80f, paint)

        paint.textSize = 13f
        paint.isFakeBoldText = false
        canvas.drawText("No: ${invoice.invoiceNumber ?: ""}", 550f, 105f, paint)
        canvas.drawText("Date: ${FormatUtils.formatDate(invoice.issueDate)}", 550f, 125f, paint)

        paint.color = android.graphics.Color.LTGRAY
        canvas.drawLine(60f, 150f, 740f, 150f, paint)

        paint.color = android.graphics.Color.BLACK
        paint.textSize = 14f
        paint.isFakeBoldText = true
        canvas.drawText("CUSTOMER INFO:", 60f, 190f, paint)
        paint.isFakeBoldText = false
        canvas.drawText("Name: ${invoice.clientName ?: ""}", 60f, 215f, paint)
        
        var nextY = 235f
        if (!invoice.clientPhone.isNullOrBlank()) {
            canvas.drawText("WhatsApp: ${invoice.clientPhone}", 60f, nextY, paint)
            nextY += 20f
        }
        
        val addressItem = items.find { (it.description ?: "").startsWith("__ADDRESS__:") }
        val address = addressItem?.description?.removePrefix("__ADDRESS__:")?.trim()
        if (!address.isNullOrBlank()) {
            canvas.drawText("Address: $address", 60f, nextY, paint)
            nextY += 20f
        }

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
        val filteredItems = items.filter { !(it.description ?: "").startsWith("__") }
        for (item in filteredItems) {
            val descText = item.description ?: ""
            val shortDesc = if (descText.length > 45) descText.take(42) + "..." else descText
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

        paint.textSize = 70f
        val safeStatus = invoice.status ?: ""
        paint.color = when (safeStatus) {
            "LUNAS" -> android.graphics.Color.argb(35, 46, 125, 50)
            "DP" -> android.graphics.Color.argb(35, 239, 108, 0)
            "BATAL" -> android.graphics.Color.argb(35, 120, 120, 120)
            else -> android.graphics.Color.argb(35, 198, 40, 40)
        }
        paint.isFakeBoldText = true
        canvas.save()
        canvas.rotate(-35f, 400f, 650f)
        canvas.drawText(safeStatus, 250f, 650f, paint)
        canvas.restore()

        val noteItem = items.find { (it.description ?: "").startsWith("__NOTE__:") }
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
        canvas.drawText("Terima kasih telah mempercayakan kebutuhan apparel Anda kepada YANSPROJECT.ID.", 400f, 1040f, paint)

        return try {
            val dir = getExportDirectory(context, "invoice")
            val safeNum = (invoice.invoiceNumber ?: "INV").replace("/", "_").replace("\\", "_")
            val file = File(dir, "Invoice-${safeNum}.png")
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
                os.write("No: ${invoice.invoiceNumber ?: ""}\n".toByteArray(java.nio.charset.Charset.forName("GBK")))
                os.write("Tgl: ${com.yansproject.app.ui.FormatUtils.formatDate(invoice.issueDate)}\n".toByteArray(java.nio.charset.Charset.forName("GBK")))
                os.write("Klien: ${invoice.clientName ?: ""}\n".toByteArray(java.nio.charset.Charset.forName("GBK")))
                if (!invoice.clientPhone.isNullOrBlank()) {
                    os.write("WA: ${invoice.clientPhone}\n".toByteArray(java.nio.charset.Charset.forName("GBK")))
                }
                os.write("--------------------------------\n".toByteArray(java.nio.charset.Charset.forName("GBK")))

                val cleanItems = items.filter { !(it.description ?: "").startsWith("__ADDRESS__:") && !(it.description ?: "").startsWith("__NOTE__:") }
                for (item in cleanItems) {
                    val desc = item.description ?: ""
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
                val safeStatus = invoice.status ?: ""
                if (invoice.dpAmount > 0.0 && safeStatus == "DP") {
                    os.write("DP: ${com.yansproject.app.ui.FormatUtils.formatRupiah(invoice.dpAmount)}\n".toByteArray(java.nio.charset.Charset.forName("GBK")))
                }
                os.write("Sisa: ${com.yansproject.app.ui.FormatUtils.formatRupiah(invoice.remainingPayment)}\n".toByteArray(java.nio.charset.Charset.forName("GBK")))
                os.write("Status: ${safeStatus}\n".toByteArray(java.nio.charset.Charset.forName("GBK")))
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
                os.write("Project: ${project.projectName ?: ""}\n".toByteArray(java.nio.charset.Charset.forName("GBK")))
                os.write("Klien: ${project.clientName ?: ""}\n".toByteArray(java.nio.charset.Charset.forName("GBK")))
                os.write("Deadline: ${com.yansproject.app.ui.FormatUtils.formatDate(project.endDate)}\n".toByteArray(java.nio.charset.Charset.forName("GBK")))
                os.write("Status: ${project.status ?: ""}\n".toByteArray(java.nio.charset.Charset.forName("GBK")))
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
                        os.write("- ${item.productType ?: ""} (${item.sleeveType ?: ""})\n".toByteArray(java.nio.charset.Charset.forName("GBK")))
                        os.write("  Size: ${item.size ?: ""} | Qty: ${item.qty} Pcs\n".toByteArray(java.nio.charset.Charset.forName("GBK")))
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