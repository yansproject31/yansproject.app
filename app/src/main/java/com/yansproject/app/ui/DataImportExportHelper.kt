package com.yansproject.app.ui

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import com.yansproject.app.data.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

object DataImportExportHelper {

    // --- EXPORT TO CSV / EXCEL (TSV) ---

    fun exportStockToCsv(context: Context, stocks: List<MasterStock>, variants: List<MasterVarianWarna>, catalogs: List<MasterCatalog>, useExcelFormat: Boolean = false): File? {
        try {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
            val ext = if (useExcelFormat) "xls" else "csv"
            val file = File(dir, "YANS_EXKSPORT_STOK_${System.currentTimeMillis()}.$ext")
            val delimiter = if (useExcelFormat) "\t" else ","

            file.bufferedWriter().use { writer ->
                // Write Header
                writer.write(listOf(
                    "ID Varian", "Nama Catalog", "Nama Warna", 
                    "XS Pendek", "XS Panjang", "S Pendek", "S Panjang", 
                    "M Pendek", "M Panjang", "L Pendek", "L Panjang", 
                    "XL Pendek", "XL Panjang", "XXL Pendek", "XXL Panjang", 
                    "3XL Pendek", "3XL Panjang", "4XL Pendek", "4XL Panjang", 
                    "HPP", "Harga Member", "Harga Retail", "Harga Reseller", "Harga Custom"
                ).joinToString(delimiter) + "\n")

                // Write rows
                stocks.forEach { stock ->
                    val variant = variants.find { it.id_varian == stock.id_varian }
                    val catalog = catalogs.find { it.id_catalog == variant?.id_catalog }
                    val catalogName = catalog?.nama_catalog ?: "Tidak Diketahui"
                    val colorName = variant?.nama_warna ?: "Tidak Diketahui"

                    writer.write(listOf(
                        stock.id_varian, catalogName, colorName,
                        stock.xs_pendek, stock.xs_panjang, stock.s_pendek, stock.s_panjang,
                        stock.m_pendek, stock.m_panjang, stock.l_pendek, stock.l_panjang,
                        stock.xl_pendek, stock.xl_panjang, stock.xxl_pendek, stock.xxl_panjang,
                        stock.three_xl_pendek, stock.three_xl_panjang, stock.four_xl_pendek, stock.four_xl_panjang,
                        stock.hpp, stock.harga_member, stock.harga_retail, stock.harga_reseller, stock.harga_custom
                    ).joinToString(delimiter) + "\n")
                }
            }
            return file
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun exportCatalogToCsv(context: Context, catalogs: List<MasterCatalog>, useExcelFormat: Boolean = false): File? {
        try {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
            val ext = if (useExcelFormat) "xls" else "csv"
            val file = File(dir, "YANS_EKSPORT_KATALOG_${System.currentTimeMillis()}.$ext")
            val delimiter = if (useExcelFormat) "\t" else ","

            file.bufferedWriter().use { writer ->
                writer.write(listOf("ID Catalog", "Nama Catalog", "Deskripsi", "Status").joinToString(delimiter) + "\n")
                catalogs.forEach {
                    writer.write(listOf(it.id_catalog, it.nama_catalog, it.deskripsi, it.status).joinToString(delimiter) + "\n")
                }
            }
            return file
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun exportCustomersToCsv(context: Context, projects: List<ProjectCustom>, orders: List<OrderHistory>, useExcelFormat: Boolean = false): File? {
        try {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
            val ext = if (useExcelFormat) "xls" else "csv"
            val file = File(dir, "YANS_EKSPORT_PELANGGAN_${System.currentTimeMillis()}.$ext")
            val delimiter = if (useExcelFormat) "\t" else ","

            // Compile unique customers
            val customers = mutableMapOf<String, String>() // Name -> Phone
            projects.forEach { if (it.clientName.isNotBlank()) customers[it.clientName.trim()] = it.clientPhone }
            orders.forEach { if (it.clientName.isNotBlank()) customers[it.clientName.trim()] = it.clientPhone }

            file.bufferedWriter().use { writer ->
                writer.write(listOf("Nama Customer", "Nomor WhatsApp").joinToString(delimiter) + "\n")
                customers.forEach { (name, phone) ->
                    writer.write(listOf(name, phone).joinToString(delimiter) + "\n")
                }
            }
            return file
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun exportMembersToCsv(context: Context, members: Set<String>, useExcelFormat: Boolean = false): File? {
        try {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
            val ext = if (useExcelFormat) "xls" else "csv"
            val file = File(dir, "YANS_EKSPORT_MEMBER_${System.currentTimeMillis()}.$ext")
            val delimiter = if (useExcelFormat) "\t" else ","

            file.bufferedWriter().use { writer ->
                writer.write(listOf("Nama Member", "Role", "Kategori Harga").joinToString(delimiter) + "\n")
                members.forEach { name ->
                    writer.write(listOf(name, "MEMBER", "Member").joinToString(delimiter) + "\n")
                }
            }
            return file
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    // --- IMPORT FROM CSV / EXCEL (TSV) ---

    fun importCatalogFromCsv(context: Context, uri: Uri, viewModel: MainViewModel, onComplete: (Int) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            var count = 0
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    val header = reader.readLine() ?: return@use
                    val delimiter = if (header.contains("\t")) "\t" else ","

                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val parts = line!!.split(delimiter)
                        if (parts.size >= 2) {
                            val name = parts[1].removeSurrounding("\"").trim()
                            val desc = if (parts.size > 2) parts[2].removeSurrounding("\"").trim() else ""
                            if (name.isNotEmpty()) {
                                viewModel.addCatalog(name, desc)
                                count++
                            }
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    onComplete(count)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    onComplete(-1)
                }
            }
        }
    }

    fun importStockFromCsv(context: Context, uri: Uri, viewModel: MainViewModel, onComplete: (Int) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            var count = 0
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    val header = reader.readLine() ?: return@use
                    val delimiter = if (header.contains("\t")) "\t" else ","

                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val parts = line!!.split(delimiter)
                        if (parts.size >= 24) {
                            val idVarian = parts[0].trim().toIntOrNull() ?: continue
                            val xs_pdk = parts[3].trim().toIntOrNull() ?: 0
                            val xs_pjg = parts[4].trim().toIntOrNull() ?: 0
                            val s_pdk = parts[5].trim().toIntOrNull() ?: 0
                            val s_pjg = parts[6].trim().toIntOrNull() ?: 0
                            val m_pdk = parts[7].trim().toIntOrNull() ?: 0
                            val m_pjg = parts[8].trim().toIntOrNull() ?: 0
                            val l_pdk = parts[9].trim().toIntOrNull() ?: 0
                            val l_pjg = parts[10].trim().toIntOrNull() ?: 0
                            val xl_pdk = parts[11].trim().toIntOrNull() ?: 0
                            val xl_pjg = parts[12].trim().toIntOrNull() ?: 0
                            val xxl_pdk = parts[13].trim().toIntOrNull() ?: 0
                            val xxl_pjg = parts[14].trim().toIntOrNull() ?: 0
                            val three_pdk = parts[15].trim().toIntOrNull() ?: 0
                            val three_pjg = parts[16].trim().toIntOrNull() ?: 0
                            val four_pdk = parts[17].trim().toIntOrNull() ?: 0
                            val four_pjg = parts[18].trim().toIntOrNull() ?: 0
                            
                            val hpp = parts[19].trim().toDoubleOrNull() ?: 95000.0
                            val m_price = parts[20].trim().toDoubleOrNull() ?: 85000.0
                            val r_price = parts[21].trim().toDoubleOrNull() ?: 100000.0
                            val s_price = parts[22].trim().toDoubleOrNull() ?: 90000.0
                            val c_price = parts[23].trim().toDoubleOrNull() ?: 80000.0

                            val total = xs_pdk + xs_pjg + s_pdk + s_pjg + m_pdk + m_pjg + l_pdk + l_pjg + xl_pdk + xl_pjg + xxl_pdk + xxl_pjg + three_pdk + three_pjg + four_pdk + four_pjg

                            val ms = MasterStock(
                                id_varian = idVarian,
                                xs_pendek = xs_pdk, xs_panjang = xs_pjg,
                                s_pendek = s_pdk, s_panjang = s_pjg,
                                m_pendek = m_pdk, m_panjang = m_pjg,
                                l_pendek = l_pdk, l_panjang = l_pjg,
                                xl_pendek = xl_pdk, xl_panjang = xl_pjg,
                                xxl_pendek = xxl_pdk, xxl_panjang = xxl_pjg,
                                three_xl_pendek = three_pdk, three_xl_panjang = three_pjg,
                                four_xl_pendek = four_pdk, four_xl_panjang = four_pjg,
                                hpp = hpp, harga_member = m_price, harga_retail = r_price,
                                harga_reseller = s_price, harga_custom = c_price,
                                total_stock = total, updated_at = System.currentTimeMillis()
                            )
                            viewModel.saveVarianStockMatrix(idVarian, ms)
                            count++
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    onComplete(count)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    onComplete(-1)
                }
            }
        }
    }

    fun importCustomerFromCsv(context: Context, uri: Uri, viewModel: MainViewModel, onComplete: (Int) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            var count = 0
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    val header = reader.readLine() ?: return@use
                    val delimiter = if (header.contains("\t")) "\t" else ","

                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val parts = line!!.split(delimiter)
                        if (parts.size >= 2) {
                            val name = parts[0].removeSurrounding("\"").trim()
                            val phone = parts[1].removeSurrounding("\"").trim()
                            if (name.isNotEmpty()) {
                                // Add as an inactive ProjectCustom stub to represent client in database search
                                viewModel.addProject(
                                    projectName = "Imported Customer Info",
                                    clientName = name,
                                    clientPhone = phone,
                                    description = "Customer data imported via CSV/Excel",
                                    totalCost = 0.0,
                                    paidAmount = 0.0,
                                    status = "Completed",
                                    startDate = System.currentTimeMillis(),
                                    endDate = System.currentTimeMillis()
                                )
                                count++
                            }
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    onComplete(count)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    onComplete(-1)
                }
            }
        }
    }

    fun importMembersFromCsv(context: Context, uri: Uri, viewModel: MainViewModel, onComplete: (Int) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            var count = 0
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    val header = reader.readLine() ?: return@use
                    val delimiter = if (header.contains("\t")) "\t" else ","

                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val parts = line!!.split(delimiter)
                        if (parts.size >= 1) {
                            val name = parts[0].removeSurrounding("\"").trim()
                            if (name.isNotEmpty()) {
                                AppSettings.addMember(context, name)
                                // Also create in firestore if cloud is active
                                FirebaseSyncManager.registerMemberOnCloud(
                                    context = context,
                                    email = "${name.lowercase().replace(" ", "")}@yansproject.id",
                                    passwordOrPin = "member123",
                                    displayName = name,
                                    priceCategory = "Member"
                                )
                                count++
                            }
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    onComplete(count)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    onComplete(-1)
                }
            }
        }
    }

    fun exportInflowsToCsv(context: Context, inflows: List<Inflow>, useExcelFormat: Boolean = false): File? {
        try {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
            val ext = if (useExcelFormat) "xls" else "csv"
            val file = File(dir, "YANS_EKSPORT_PEMASUKAN_${System.currentTimeMillis()}.$ext")
            val delimiter = if (useExcelFormat) "\t" else ","

            file.bufferedWriter().use { writer ->
                writer.write(listOf("Nomor Transaksi", "Tanggal", "Kategori", "Metode Pembayaran", "Jumlah", "Catatan", "Dibuat Oleh").joinToString(delimiter) + "\n")
                inflows.forEach {
                    val formattedDate = FormatUtils.formatDate(it.date)
                    writer.write(listOf(
                        it.transactionNumber,
                        formattedDate,
                        it.category,
                        it.paymentMethod,
                        it.amount,
                        it.notes,
                        it.createdBy
                    ).joinToString(delimiter) + "\n")
                }
            }
            return file
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun exportExpensesToCsv(context: Context, expenses: List<Expense>, useExcelFormat: Boolean = false): File? {
        try {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
            val ext = if (useExcelFormat) "xls" else "csv"
            val file = File(dir, "YANS_EKSPORT_PENGELUARAN_${System.currentTimeMillis()}.$ext")
            val delimiter = if (useExcelFormat) "\t" else ","

            file.bufferedWriter().use { writer ->
                writer.write(listOf("Nomor Transaksi", "Tanggal", "Kategori", "Metode Pembayaran", "Jumlah", "Catatan", "Dibuat Oleh").joinToString(delimiter) + "\n")
                expenses.forEach {
                    val formattedDate = FormatUtils.formatDate(it.date)
                    writer.write(listOf(
                        it.transactionNumber,
                        formattedDate,
                        it.category,
                        it.paymentMethod,
                        it.amount,
                        it.notes,
                        it.createdBy
                    ).joinToString(delimiter) + "\n")
                }
            }
            return file
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun exportCashLedgerToCsv(context: Context, transactions: List<UnifiedTxItem>, useExcelFormat: Boolean = false): File? {
        try {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
            val ext = if (useExcelFormat) "xls" else "csv"
            val file = File(dir, "YANS_EKSPORT_BUKU_KAS_${System.currentTimeMillis()}.$ext")
            val delimiter = if (useExcelFormat) "\t" else ","

            file.bufferedWriter().use { writer ->
                writer.write(listOf("Nomor Dokumen", "Tanggal", "Tipe", "Kategori", "Jumlah", "Keterangan", "Operator").joinToString(delimiter) + "\n")
                transactions.forEach {
                    val formattedDate = FormatUtils.formatDate(it.date)
                    val displayAmount = if (it.type == "EXPENSE") "-${it.amount}" else "+${it.amount}"
                    writer.write(listOf(
                        it.docNumber,
                        formattedDate,
                        it.type,
                        it.category,
                        displayAmount,
                        it.notes,
                        it.user
                    ).joinToString(delimiter) + "\n")
                }
            }
            return file
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun exportAjibqobulOrderHistoryToCsv(context: Context, invoices: List<Invoice>, useExcelFormat: Boolean = false): File? {
        try {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
            val ext = if (useExcelFormat) "xls" else "csv"
            val timeStamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
            val fileName = "YANS_RIWAYAT_AJIBQOBUL_$timeStamp.$ext"
            val file = File(dir, fileName)
            val delimiter = if (useExcelFormat) "\t" else ","
            val converters = AppTypeConverters()

            file.bufferedWriter().use { writer ->
                val headers = listOf(
                    "No. Invoice",
                    "Tanggal Issue",
                    "Jatuh Tempo",
                    "Nama Pelanggan",
                    "No. WhatsApp",
                    "Status",
                    "Detail Item AJIBQOBUL",
                    "Total Tagihan (Rp)",
                    "Diskon (Rp)",
                    "DP (Rp)",
                    "Jumlah Dibayar (Rp)",
                    "Sisa Tagihan (Rp)"
                )
                writer.write(headers.joinToString(delimiter) + "\n")

                invoices.forEach { inv ->
                    val issueDateStr = FormatUtils.formatDate(inv.issueDate)
                    val dueDateStr = FormatUtils.formatDate(inv.dueDate)
                    val items = converters.toInvoiceItemList(inv.itemsJson)
                    val itemDetails = items.joinToString(" | ") { item ->
                        val cleanDesc = item.description.removePrefix("Pembelian: ")
                        "$cleanDesc (Qty: ${item.quantity}, @ ${FormatUtils.formatRupiah(item.price)})"
                    }.replace(delimiter, " ").replace("\n", " ").replace("\r", "")

                    val cleanClientName = inv.clientName.replace(delimiter, " ").replace("\n", " ")
                    val cleanPhone = inv.clientPhone.replace(delimiter, " ").replace("\n", " ")
                    val cleanInvoiceNum = inv.invoiceNumber.replace(delimiter, " ").replace("\n", " ")

                    writer.write(
                        listOf(
                            cleanInvoiceNum,
                            issueDateStr,
                            dueDateStr,
                            cleanClientName,
                            cleanPhone,
                            inv.status,
                            "\"$itemDetails\"",
                            inv.totalAmount.toLong(),
                            inv.discount.toLong(),
                            inv.dpAmount.toLong(),
                            inv.paidAmount.toLong(),
                            inv.remainingPayment.toLong()
                        ).joinToString(delimiter) + "\n"
                    )
                }
            }

            try {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val publicFile = File(downloadsDir, fileName)
                file.copyTo(publicFile, overwrite = true)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            return file
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
