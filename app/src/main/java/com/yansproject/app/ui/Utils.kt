package com.yansproject.app.ui

import android.content.Context
import android.graphics.Typeface
import android.util.Log
import androidx.compose.ui.text.font.FontFamily
import com.yansproject.app.data.Invoice
import com.yansproject.app.data.InvoiceItemDetail
import com.yansproject.app.data.ProjectCustom
import com.yansproject.app.util.BitmapUtils
import com.yansproject.app.util.FileUtils
import com.yansproject.app.util.PdfUtils
import com.yansproject.app.util.ShareUtils
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object FontUtils {
    fun getJetBrainsMonoBold(): Typeface {
        return Typeface.create("sans-serif-monospace", Typeface.BOLD)
    }

    fun getInterMedium(): Typeface {
        return Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    fun getPremiumArabicFontFamily(context: Context? = null): FontFamily {
        return FontFamily.Serif
    }
}

data class ParsedStock(
    val size: String,
    val qty: Int
)

data class ParsedApparelItem(
    val series: String = "",
    val sleeve: String = "Pendek",
    val size: String = "M",
    val quantity: Int = 1,
    val isApparel: Boolean = true
)

data class ProjectItem(
    val productType: String = "Custom Apparel",
    val sleeveType: String = "Pendek",
    val size: String = "M",
    val qty: Int = 0,
    val price: Double = 0.0,
    val subtotal: Double = qty * price
)

object ProjectItemParser {

    fun serialize(items: List<ProjectItem>): String {
        return items.joinToString(";") { "${it.productType}|${it.sleeveType}|${it.size}|${it.qty}|${it.price}|${it.subtotal}" }
    }

    /**
     * Parses description format or serialized format
     */
    fun getProjectItems(rawDesc: String?): List<ProjectItem> {
        if (rawDesc.isNullOrBlank()) return emptyList()
        val items = mutableListOf<ProjectItem>()

        if (rawDesc.contains("===ITEMS_DATA===")) {
            val serialized = rawDesc.substringAfter("===ITEMS_DATA===").trim()
            val entries = serialized.split(";")
            for (entry in entries) {
                val parts = entry.split("|")
                if (parts.size >= 4) {
                    val pType = parts[0].trim()
                    val sType = parts[1].trim()
                    val sz = parts[2].trim()
                    val q = parts[3].trim().toIntOrNull() ?: 0
                    val pr = if (parts.size >= 5) parts[4].trim().toDoubleOrNull() ?: 0.0 else 0.0
                    val sub = if (parts.size >= 6) parts[5].trim().toDoubleOrNull() ?: (q * pr) else (q * pr)
                    if (sz.isNotEmpty() && q > 0) {
                        items.add(ProjectItem(pType, sType, sz, q, pr, sub))
                    }
                }
            }
            if (items.isNotEmpty()) return items
        }

        val lines = rawDesc.split("\n")
        for (line in lines) {
            if (line.contains(" - Size: ")) {
                try {
                    val parts = line.split(" - Size: ")
                    val typeAndSleeve = parts[0].trim()
                    val sizeQtyPart = parts[1].trim()

                    var pType = "Custom Apparel"
                    var sType = "Pendek"

                    if (typeAndSleeve.contains("(") && typeAndSleeve.contains(")")) {
                        pType = typeAndSleeve.substringBefore("(").trim()
                        sType = typeAndSleeve.substringAfter("(").substringBefore(")").trim()
                    } else {
                        pType = typeAndSleeve
                    }

                    val entries = sizeQtyPart.split(",")
                    for (entry in entries) {
                        val sizeStr = entry.substringBefore("(").trim()
                        val qtyStr = entry.substringAfter("(").substringBefore("Pcs").substringBefore("pcs").trim()
                        val qtyVal = qtyStr.toIntOrNull() ?: 0
                        if (sizeStr.isNotEmpty() && qtyVal > 0) {
                            items.add(ProjectItem(pType, sType, sizeStr, qtyVal, 0.0, 0.0))
                        }
                    }
                } catch (e: Exception) {
                    Log.w("ProjectItemParser", "Failed parsing project item line '$line': ${e.message}")
                }
            }
        }
        return items
    }

    fun getProjectDescription(rawDesc: String?): String {
        if (rawDesc.isNullOrBlank()) return ""
        val clean = if (rawDesc.contains("===ITEMS_DATA===")) rawDesc.substringBefore("===ITEMS_DATA===").trim() else rawDesc
        val lines = clean.split("\n")
        val descLines = lines.filter { !it.contains(" - Size: ") }
        return descLines.joinToString("\n").trim()
    }

    fun parseStockDetails(detailsStr: String?): List<ParsedStock> {
        if (detailsStr.isNullOrBlank()) return emptyList()
        val list = mutableListOf<ParsedStock>()
        val entries = detailsStr.split(",")
        for (entry in entries) {
            if (entry.contains(":")) {
                val parts = entry.split(":")
                val sz = parts[0].trim()
                val q = parts[1].trim().toIntOrNull() ?: 0
                if (sz.isNotEmpty()) {
                    list.add(ParsedStock(sz, q))
                }
            }
        }
        return list
    }
}

object InvoiceItemSorter {
    private val SIZE_ORDER = mapOf(
        "XS" to 1, "S" to 2, "M" to 3, "L" to 4,
        "XL" to 5, "XXL" to 6, "2XL" to 6,
        "3XL" to 7, "4XL" to 8, "5XL" to 9
    )

    fun getSleeveIndex(sleeve: String?): Int {
        return FormatUtils.getSleeveIndex(sleeve)
    }

    fun getSizeIndex(size: String?): Int {
        return FormatUtils.getSizeIndex(size)
    }

    fun extractSleeve(description: String): String {
        return if (description.contains("Panjang", ignoreCase = true) || description.contains("Long", ignoreCase = true)) {
            "Panjang"
        } else {
            "Pendek"
        }
    }

    fun extractSleeveIndex(description: String): Int {
        return if (extractSleeve(description) == "Panjang") 1 else 0
    }

    fun extractSize(description: String): String {
        val uppercaseDesc = description.uppercase()
        val sizeRegex = Regex("""(?i)\b(XS|S|M|L|XL|XXL|2XL|3XL|4XL|5XL)\b""")
        val match = sizeRegex.find(uppercaseDesc)
        if (match != null) {
            return match.value.uppercase()
        }
        for (size in SIZE_ORDER.keys) {
            if (uppercaseDesc.contains("($size)") || uppercaseDesc.contains("SIZE $size") || uppercaseDesc.contains("UKURAN $size") || uppercaseDesc.contains(" $size ") || uppercaseDesc.endsWith(" $size")) {
                return size
            }
        }
        return ""
    }

    fun extractSizeIndex(description: String): Int {
        val sz = extractSize(description)
        return SIZE_ORDER[sz] ?: 99
    }

    fun sortInvoiceItems(items: List<InvoiceItemDetail>): List<InvoiceItemDetail> {
        return items.sortedWith { a, b ->
            val sleeveIdxA = extractSleeveIndex(a.description)
            val sleeveIdxB = extractSleeveIndex(b.description)

            if (sleeveIdxA != sleeveIdxB) {
                sleeveIdxA.compareTo(sleeveIdxB)
            } else {
                val sizeIdxA = extractSizeIndex(a.description)
                val sizeIdxB = extractSizeIndex(b.description)

                if (sizeIdxA != sizeIdxB) {
                    sizeIdxA.compareTo(sizeIdxB)
                } else {
                    a.description.compareTo(b.description, ignoreCase = true)
                }
            }
        }
    }

    fun getShortSleeveTotalQty(items: List<InvoiceItemDetail>): Int {
        return items.filter { !it.description.startsWith("__") && extractSleeve(it.description) == "Pendek" }
            .sumOf { if (it.quantity > 0) it.quantity else 1 }
    }

    fun getLongSleeveTotalQty(items: List<InvoiceItemDetail>): Int {
        return items.filter { !it.description.startsWith("__") && extractSleeve(it.description) == "Panjang" }
            .sumOf { if (it.quantity > 0) it.quantity else 1 }
    }

    fun getGlobalTotalQty(items: List<InvoiceItemDetail>): Int {
        val sum = items.filter { !it.description.startsWith("__") }
            .sumOf { if (it.quantity > 0) it.quantity else 1 }
        return if (sum > 0) sum else 1
    }

    fun calcSubtotal(items: List<InvoiceItemDetail>): Double {
        val validItems = items.filter { !it.description.startsWith("__") }
        if (validItems.isEmpty()) return 0.0
        return validItems.sumOf { (if (it.quantity > 0) it.quantity else 1) * it.price }
    }
}

object FormatUtils {
    fun sanitizeNotes(notes: String?): String {
        if (notes.isNullOrBlank()) return ""
        return notes.replace(Regex("\\[PAY_REF:[^\\]]+\\]"), "").replace("  ", " ").trim()
    }

    fun formatRupiah(amount: Double): String {
        val localeID = Locale("in", "ID")
        val format = NumberFormat.getCurrencyInstance(localeID)
        format.maximumFractionDigits = 0
        return format.format(amount)
    }

    fun formatDate(timestamp: Long): String {
        if (timestamp <= 0) return "-"
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale("id", "ID"))
        return sdf.format(Date(timestamp))
    }

    fun formatDateTime(timestamp: Long): String {
        if (timestamp <= 0) return "-"
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("id", "ID"))
        return sdf.format(Date(timestamp))
    }

    fun formatPaymentMethod(method: String?, detail: String? = null): String {
        if (method.isNullOrBlank()) return "Tunai"
        val base = when (method.trim().uppercase()) {
            "CASH", "TUNAI" -> "Tunai"
            "TRANSFER", "BANK" -> "Transfer Bank"
            "QRIS" -> "QRIS"
            "DP", "UANG_MUKA" -> "Uang Muka (DP)"
            "PIUTANG" -> "Piutang"
            else -> method
        }
        return if (!detail.isNullOrBlank()) "$base ($detail)" else base
    }

    fun getSleeveIndex(sleeve: String?): Int {
        if (sleeve.isNullOrBlank()) return 0
        return if (sleeve.contains("Panjang", ignoreCase = true)) 1 else 0
    }

    fun getSizeIndex(size: String?): Int {
        if (size.isNullOrBlank()) return 1
        return when (size.trim().uppercase()) {
            "XS" -> 0
            "S" -> 1
            "M" -> 2
            "L" -> 3
            "XL" -> 4
            "XXL", "2XL" -> 5
            "3XL" -> 6
            "4XL" -> 7
            "5XL" -> 8
            else -> 1
        }
    }

    fun parseStockItemName(itemName: String): ParsedApparelItem {
        if (itemName.isBlank()) return ParsedApparelItem()
        val clean = itemName.trim()
        val isNonApparel = clean.contains("Stiker", ignoreCase = true) ||
                clean.contains("Spanduk", ignoreCase = true) ||
                clean.contains("Banner", ignoreCase = true) ||
                clean.contains("Non-Apparel", ignoreCase = true)

        val sleeve = if (clean.contains("Panjang", ignoreCase = true)) "Panjang" else "Pendek"

        val series = clean.substringBefore("-").substringBefore("(").trim()

        var size = "M"
        val sizeRegex = Regex("""(?i)\b(XS|S|M|L|XL|XXL|2XL|3XL|4XL|5XL)\b""")
        val match = sizeRegex.find(clean)
        if (match != null) {
            size = match.value.uppercase()
        }

        var qty = 1
        val qtyRegex = Regex("""(\d+)\s*(Pcs|pcs)""")
        val qtyMatch = qtyRegex.find(clean)
        if (qtyMatch != null) {
            qty = qtyMatch.groupValues[1].toIntOrNull() ?: 1
        }

        return ParsedApparelItem(
            series = series.ifBlank { clean },
            sleeve = sleeve,
            size = size,
            quantity = qty,
            isApparel = !isNonApparel
        )
    }
}

object DocumentExporter {

    fun getRootDirectory(context: Context): File {
        return FileUtils.getRootDirectory(context)
    }

    fun initFolderStructure(context: Context) {
        FileUtils.initFolderStructure(context)
    }

    fun getExportDirectory(context: Context, type: String): File {
        return FileUtils.getExportDirectory(context, type)
    }

    fun mirrorToDownloads(context: Context, file: File, subFolder: String = "Export"): File? {
        return FileUtils.mirrorToDownloads(context, file, subFolder)
    }

    fun openFolder(context: Context, folder: File) {
        FileUtils.openFolder(context, folder)
    }

    fun shareFile(context: Context, file: File, title: String = "Bagikan Berkas YANSPROJECT.ID") {
        ShareUtils.shareFile(context, file, title)
    }

    fun shareFileToWhatsApp(context: Context, file: File?, clientPhone: String?, captionText: String? = null) {
        ShareUtils.shareFileToWhatsApp(context, file, clientPhone, captionText)
    }

    fun openFile(context: Context, file: File) {
        FileUtils.openFile(context, file)
    }

    fun exportToPdf(context: Context, invoice: Invoice, items: List<InvoiceItemDetail>, viewModel: MainViewModel? = null): File? {
        return PdfUtils.exportToPdf(context, invoice, items, viewModel)
    }

    fun exportToPng(context: Context, invoice: Invoice, items: List<InvoiceItemDetail>, viewModel: MainViewModel? = null): File? {
        return BitmapUtils.exportToPng(context, invoice, items, viewModel)
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
        PdfUtils.exportFinancialSummaryToPdf(
            context, period, totalRevenue, totalReceivables, activeProjectsCount, lowStockCount, totalOrdersCount, unpaidInvoices, activeProjects, viewModel
        )
    }

    fun exportOrderSummaryToPdf(
        context: Context,
        clientName: String,
        clientPhone: String,
        items: List<MemberCartItem>,
        notes: String,
        viewModel: MainViewModel? = null
    ): File? {
        return PdfUtils.exportOrderSummaryToPdf(context, clientName, clientPhone, items, notes, viewModel)
    }

    fun exportOrderSummaryToPng(
        context: Context,
        clientName: String,
        clientPhone: String,
        items: List<MemberCartItem>,
        notes: String,
        viewModel: MainViewModel? = null
    ): File? {
        return BitmapUtils.exportOrderSummaryToPng(context, clientName, clientPhone, items, notes, viewModel)
    }
}

object YansBluetoothPrinter {
    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    fun findBluetoothPrinter(): android.bluetooth.BluetoothDevice? {
        val adapter = try {
            android.bluetooth.BluetoothAdapter.getDefaultAdapter()
        } catch (e: SecurityException) {
            Log.w("YansBluetoothPrinter", "Security Exception accessing Bluetooth adapter: ${e.message}")
            null
        } ?: return null

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

    fun printInvoice(context: Context, invoice: Invoice, items: List<InvoiceItemDetail>) {
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
                os.write("WhatsApp: ${AppSettings.getWhatsApp(context)}\n".toByteArray(java.nio.charset.Charset.forName("GBK")))
                os.write("--------------------------------\n".toByteArray(java.nio.charset.Charset.forName("GBK")))

                os.write(alignLeft)
                os.write("No: ${invoice.invoiceNumber}\n".toByteArray(java.nio.charset.Charset.forName("GBK")))
                os.write("Tgl: ${FormatUtils.formatDate(invoice.issueDate)}\n".toByteArray(java.nio.charset.Charset.forName("GBK")))
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
                    os.write("   $qty x ${FormatUtils.formatRupiah(price)} = ${FormatUtils.formatRupiah(sub)}\n".toByteArray(java.nio.charset.Charset.forName("GBK")))
                }
                os.write("--------------------------------\n".toByteArray(java.nio.charset.Charset.forName("GBK")))

                if (invoice.discount > 0.0) {
                    os.write("Diskon: -${FormatUtils.formatRupiah(invoice.discount)}\n".toByteArray(java.nio.charset.Charset.forName("GBK")))
                }
                os.write(boldOn)
                os.write("TOTAL: ${FormatUtils.formatRupiah(invoice.totalAmount)}\n".toByteArray(java.nio.charset.Charset.forName("GBK")))
                os.write("Bayar: ${FormatUtils.formatRupiah(invoice.paidAmount)}\n".toByteArray(java.nio.charset.Charset.forName("GBK")))
                if (invoice.dpAmount > 0.0 && invoice.status == "DP") {
                    os.write("DP: ${FormatUtils.formatRupiah(invoice.dpAmount)}\n".toByteArray(java.nio.charset.Charset.forName("GBK")))
                }
                os.write("Sisa: ${FormatUtils.formatRupiah(invoice.remainingPayment)}\n".toByteArray(java.nio.charset.Charset.forName("GBK")))
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
                Log.e("YansBluetoothPrinter", "Bluetooth printer connection failed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Koneksi Printer Gagal: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            } finally {
                try {
                    socket?.close()
                } catch (se: Exception) {
                    Log.w("Utils", "Failed closing Bluetooth socket for invoice: ${se.message}")
                }
            }
        }
    }

    fun printProject(context: Context, project: ProjectCustom) {
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
                os.write("Deadline: ${FormatUtils.formatDate(project.endDate)}\n".toByteArray(java.nio.charset.Charset.forName("GBK")))
                os.write("Status: ${project.status}\n".toByteArray(java.nio.charset.Charset.forName("GBK")))
                os.write("--------------------------------\n".toByteArray(java.nio.charset.Charset.forName("GBK")))

                val items = ProjectItemParser.getProjectItems(project.description)
                val rawDesc = ProjectItemParser.getProjectDescription(project.description)
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
                Log.e("YansBluetoothPrinter", "SPK printing failed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Koneksi Printer Gagal: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            } finally {
                try {
                    socket?.close()
                } catch (se: Exception) {
                    Log.w("Utils", "Failed closing Bluetooth socket for SPK: ${se.message}")
                }
            }
        }
    }
}
