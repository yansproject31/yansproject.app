package com.yansproject.app.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import com.yansproject.app.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

enum class CredentialProvisionStatus {
    CREATED, PENDING, DUPLICATE, INVALID, FAILED
}

data class MemberImportItem(
    val stableIdentity: String, // email or external ID
    val displayName: String,
    val role: String = "MEMBER",
    val priceCategory: String = "Member"
)

data class MemberImportValidationReport(
    val validItems: List<MemberImportItem>,
    val duplicateItems: List<MemberImportItem>,
    val invalidItems: List<MemberImportItem>
)

data class MemberProvisioningResult(
    val item: MemberImportItem,
    val status: CredentialProvisionStatus,
    val message: String
)

object DataImportExportHelper {

    // --- EXPORT TO CSV / EXCEL (TSV) ---

    fun LocalReportExporter.ExportResult.getOrNullFile(): File? = when (this) {
        is LocalReportExporter.ExportResult.Success -> file
        is LocalReportExporter.ExportResult.Failure -> null
    }

    fun exportStockToCsv(context: Context, stocks: List<MasterStock>, variants: List<MasterVarianWarna>, catalogs: List<MasterCatalog>, useExcelFormat: Boolean = false): File? {
        val result = exportStockToCsvDetailed(context, stocks, variants, catalogs, useExcelFormat)
        return result.getOrNullFile()
    }

    fun exportStockToCsvDetailed(context: Context, stocks: List<MasterStock>, variants: List<MasterVarianWarna>, catalogs: List<MasterCatalog>, useExcelFormat: Boolean = false): LocalReportExporter.ExportResult {
        try {
            val dir = DocumentExporter.getExportDirectory(context, "export")
            val ext = if (useExcelFormat) "xls" else "csv"
            val file = File(dir, "YANS_EKSPORT_STOK_${System.currentTimeMillis()}.$ext")
            val delimiter = if (useExcelFormat) "\t" else ","

            file.bufferedWriter().use { writer ->
                writer.write(listOf(
                    "ID Varian", "Nama Catalog", "Nama Warna", 
                    "XS Pendek", "XS Panjang", "S Pendek", "S Panjang", 
                    "M Pendek", "M Panjang", "L Pendek", "L Panjang", 
                    "XL Pendek", "XL Panjang", "XXL Pendek", "XXL Panjang", 
                    "3XL Pendek", "3XL Panjang", "4XL Pendek", "4XL Panjang", 
                    "HPP", "Harga Member", "Harga Retail", "Harga Reseller", "Harga Custom"
                ).joinToString(delimiter) + "\n")

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
            DocumentExporter.mirrorToDownloads(context, file, "Export")
            return LocalReportExporter.ExportResult.Success(file, stocks.size)
        } catch (e: Exception) {
            Log.e("DataImportExportHelper", "Error exporting stock CSV/Excel: ${e.message}", e)
            return LocalReportExporter.ExportResult.Failure("Gagal mengeskpor data stok: ${e.localizedMessage}", e)
        }
    }

    fun exportCatalogToCsv(context: Context, catalogs: List<MasterCatalog>, useExcelFormat: Boolean = false): File? {
        val result = exportCatalogToCsvDetailed(context, catalogs, useExcelFormat)
        return result.getOrNullFile()
    }

    fun exportCatalogToCsvDetailed(context: Context, catalogs: List<MasterCatalog>, useExcelFormat: Boolean = false): LocalReportExporter.ExportResult {
        try {
            val dir = DocumentExporter.getExportDirectory(context, "catalog")
            val ext = if (useExcelFormat) "xls" else "csv"
            val file = File(dir, "YANS_EKSPORT_KATALOG_${System.currentTimeMillis()}.$ext")
            val delimiter = if (useExcelFormat) "\t" else ","

            file.bufferedWriter().use { writer ->
                writer.write(listOf("ID Catalog", "Nama Catalog", "Deskripsi", "Status").joinToString(delimiter) + "\n")
                catalogs.forEach {
                    writer.write(listOf(it.id_catalog, it.nama_catalog, it.deskripsi, it.status).joinToString(delimiter) + "\n")
                }
            }
            DocumentExporter.mirrorToDownloads(context, file, "Export")
            return LocalReportExporter.ExportResult.Success(file, catalogs.size)
        } catch (e: Exception) {
            Log.e("DataImportExportHelper", "Error exporting catalog CSV/Excel: ${e.message}", e)
            return LocalReportExporter.ExportResult.Failure("Gagal mengekspor katalog: ${e.localizedMessage}", e)
        }
    }

    fun exportCustomersToCsv(context: Context, projects: List<ProjectCustom>, orders: List<OrderHistory>, useExcelFormat: Boolean = false): File? {
        val result = exportCustomersToCsvDetailed(context, projects, orders, useExcelFormat)
        return result.getOrNullFile()
    }

    fun exportCustomersToCsvDetailed(context: Context, projects: List<ProjectCustom>, orders: List<OrderHistory>, useExcelFormat: Boolean = false): LocalReportExporter.ExportResult {
        try {
            val dir = DocumentExporter.getExportDirectory(context, "customer")
            val ext = if (useExcelFormat) "xls" else "csv"
            val file = File(dir, "YANS_EKSPORT_PELANGGAN_${System.currentTimeMillis()}.$ext")
            val delimiter = if (useExcelFormat) "\t" else ","

            val customers = mutableMapOf<String, String>()
            projects.forEach { if (it.clientName.isNotBlank()) customers[it.clientName.trim()] = it.clientPhone }
            orders.forEach { if (it.clientName.isNotBlank()) customers[it.clientName.trim()] = it.clientPhone }

            file.bufferedWriter().use { writer ->
                writer.write(listOf("Nama Customer", "Nomor WhatsApp").joinToString(delimiter) + "\n")
                customers.forEach { (name, phone) ->
                    writer.write(listOf(name, phone).joinToString(delimiter) + "\n")
                }
            }
            DocumentExporter.mirrorToDownloads(context, file, "Export")
            return LocalReportExporter.ExportResult.Success(file, customers.size)
        } catch (e: Exception) {
            Log.e("DataImportExportHelper", "Error exporting customers CSV/Excel: ${e.message}", e)
            return LocalReportExporter.ExportResult.Failure("Gagal mengekspor pelanggan: ${e.localizedMessage}", e)
        }
    }

    fun exportMembersToCsv(context: Context, members: Set<String>, useExcelFormat: Boolean = false): File? {
        val result = exportMembersToCsvDetailed(context, members, useExcelFormat)
        return result.getOrNullFile()
    }

    fun exportMembersToCsvDetailed(context: Context, members: Set<String>, useExcelFormat: Boolean = false): LocalReportExporter.ExportResult {
        try {
            val dir = DocumentExporter.getExportDirectory(context, "member")
            val ext = if (useExcelFormat) "xls" else "csv"
            val file = File(dir, "YANS_EKSPORT_MEMBER_${System.currentTimeMillis()}.$ext")
            val delimiter = if (useExcelFormat) "\t" else ","

            file.bufferedWriter().use { writer ->
                writer.write(listOf("Nama Member", "Role", "Kategori Harga").joinToString(delimiter) + "\n")
                members.forEach { name ->
                    writer.write(listOf(name, "MEMBER", "Member").joinToString(delimiter) + "\n")
                }
            }
            return LocalReportExporter.ExportResult.Success(file, members.size)
        } catch (e: Exception) {
            Log.e("DataImportExportHelper", "Error exporting members CSV/Excel: ${e.message}", e)
            return LocalReportExporter.ExportResult.Failure("Gagal mengekspor member: ${e.localizedMessage}", e)
        }
    }

    // --- STAGED MEMBER CSV IMPORT PIPELINE ---

    suspend fun readAndValidateMembersCsv(context: Context, uri: Uri): MemberImportValidationReport = withContext(Dispatchers.IO) {
        val validItems = mutableListOf<MemberImportItem>()
        val duplicateItems = mutableListOf<MemberImportItem>()
        val invalidItems = mutableListOf<MemberImportItem>()

        val existingMembers = AppSettings.getMembers(context).map { it.lowercase().trim() }.toSet()

        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream))
                val header = reader.readLine() ?: return@use
                val delimiter = if (header.contains("\t")) "\t" else ","

                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    kotlin.coroutines.coroutineContext.ensureActive()
                    val currentLine = line ?: break
                    val parts = currentLine.split(delimiter)
                    if (parts.isNotEmpty()) {
                        val col0 = parts[0].removeSurrounding("\"").trim()
                        if (col0.isBlank()) {
                            invalidItems.add(MemberImportItem("", "Baris Kosong"))
                            continue
                        }

                        val displayName: String
                        val stableId: String
                        if (col0.contains("@")) {
                            stableId = col0.lowercase()
                            displayName = if (parts.size > 1) parts[1].removeSurrounding("\"").trim().ifEmpty { col0.substringBefore("@") } else col0.substringBefore("@")
                        } else if (parts.size > 1 && parts[1].contains("@")) {
                            displayName = col0
                            stableId = parts[1].removeSurrounding("\"").trim().lowercase()
                        } else {
                            // Never derive internal identity only from displayName! Require email or stable external ID.
                            invalidItems.add(MemberImportItem(stableIdentity = "", displayName = col0))
                            continue
                        }

                        val item = MemberImportItem(
                            stableIdentity = stableId,
                            displayName = displayName
                        )

                        if (existingMembers.contains(displayName.lowercase().trim()) || existingMembers.contains(stableId)) {
                            duplicateItems.add(item)
                        } else {
                            validItems.add(item)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("DataImportExportHelper", "Error validating member CSV: ${e.message}", e)
        }

        MemberImportValidationReport(validItems, duplicateItems, invalidItems)
    }

    suspend fun commitMemberImport(context: Context, validItems: List<MemberImportItem>): List<MemberProvisioningResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<MemberProvisioningResult>()

        validItems.forEach { item ->
            kotlin.coroutines.coroutineContext.ensureActive()
            try {
                AppSettings.addMember(context, item.displayName)
                val provisionedPin = BusinessIdentityProvider.getSecureProvisionedPin(item.stableIdentity, context)
                    ?: (100000..999999).random().toString()

                FirebaseSyncManager.registerMemberOnCloud(
                    context = context,
                    email = item.stableIdentity,
                    passwordOrPin = provisionedPin,
                    displayName = item.displayName,
                    priceCategory = item.priceCategory
                )

                results.add(
                    MemberProvisioningResult(
                        item = item,
                        status = CredentialProvisionStatus.CREATED,
                        message = "Akun member berhasil dibuat dengan PIN terprovisi."
                    )
                )
            } catch (e: Exception) {
                Log.e("DataImportExportHelper", "Failed to provision member ${item.displayName}: ${e.message}", e)
                results.add(
                    MemberProvisioningResult(
                        item = item,
                        status = CredentialProvisionStatus.FAILED,
                        message = "Gagal memprovisi akun: ${e.localizedMessage}"
                    )
                )
            }
        }

        results
    }

    // --- SUSPEND IMPORT APIs (NO DETACHED LAUNCH) ---

    suspend fun importCatalogFromCsv(context: Context, uri: Uri, viewModel: MainViewModel, onComplete: (Int) -> Unit) = withContext(Dispatchers.IO) {
        var count = 0
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream))
                val header = reader.readLine() ?: return@use
                val delimiter = if (header.contains("\t")) "\t" else ","

                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    kotlin.coroutines.coroutineContext.ensureActive()
                    val currentLine = line ?: break
                    val parts = currentLine.split(delimiter)
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
            withContext(Dispatchers.Main) { onComplete(count) }
        } catch (e: Exception) {
            Log.e("DataImportExportHelper", "Error importing catalog CSV: ${e.message}", e)
            withContext(Dispatchers.Main) { onComplete(-1) }
        }
    }

    suspend fun importStockFromCsv(context: Context, uri: Uri, viewModel: MainViewModel, onComplete: (Int) -> Unit) = withContext(Dispatchers.IO) {
        var count = 0
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream))
                val header = reader.readLine() ?: return@use
                val delimiter = if (header.contains("\t")) "\t" else ","

                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    kotlin.coroutines.coroutineContext.ensureActive()
                    val currentLine = line ?: break
                    val parts = currentLine.split(delimiter)
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

                        val parsedHpp = parts[19].trim().toDoubleOrNull()
                        val parsedMember = parts[20].trim().toDoubleOrNull()
                        val parsedRetail = parts[21].trim().toDoubleOrNull()
                        val parsedReseller = parts[22].trim().toDoubleOrNull()
                        val parsedCustom = parts[23].trim().toDoubleOrNull()

                        // Stock import: invalid price must NOT silently use current AppSettings price.
                        // Require explicit valid prices or keep existing values if valid.
                        if (parsedHpp == null || parsedHpp < 0.0 || parsedMember == null || parsedMember < 0.0 ||
                            parsedRetail == null || parsedRetail < 0.0 || parsedReseller == null || parsedReseller < 0.0 ||
                            parsedCustom == null || parsedCustom < 0.0) {
                            Log.w("DataImportExportHelper", "Skipping stock row $idVarian due to invalid/missing price values (No silent fallback allowed).")
                            continue
                        }

                        val hpp = parsedHpp
                        val m_price = parsedMember
                        val r_price = parsedRetail
                        val s_price = parsedReseller
                        val c_price = parsedCustom

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
            withContext(Dispatchers.Main) { onComplete(count) }
        } catch (e: Exception) {
            Log.e("DataImportExportHelper", "Error importing stock CSV: ${e.message}", e)
            withContext(Dispatchers.Main) { onComplete(-1) }
        }
    }

    suspend fun importCustomerFromCsv(context: Context, uri: Uri, viewModel: MainViewModel, onComplete: (Int) -> Unit) = withContext(Dispatchers.IO) {
        val repo = CustomerRepository(context)
        val preview = repo.parseAndPreviewCustomerCsv(uri)
        val report = repo.commitCustomerImportTransactional(preview.rows)
        withContext(Dispatchers.Main) { onComplete(report.createdCount) }
    }

    suspend fun importMembersFromCsv(context: Context, uri: Uri, viewModel: MainViewModel, onComplete: (Int) -> Unit) = withContext(Dispatchers.IO) {
        val report = readAndValidateMembersCsv(context, uri)
        val commitResults = commitMemberImport(context, report.validItems)
        val createdCount = commitResults.count { it.status == CredentialProvisionStatus.CREATED }
        withContext(Dispatchers.Main) {
            onComplete(createdCount)
        }
    }

    fun exportInflowsToCsv(context: Context, inflows: List<Inflow>, useExcelFormat: Boolean = false): File? {
        val result = exportInflowsToCsvDetailed(context, inflows, useExcelFormat)
        return result.getOrNullFile()
    }

    fun exportInflowsToCsvDetailed(context: Context, inflows: List<Inflow>, useExcelFormat: Boolean = false): LocalReportExporter.ExportResult {
        try {
            val dir = DocumentExporter.getExportDirectory(context, "finance")
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
            DocumentExporter.mirrorToDownloads(context, file, "Export")
            return LocalReportExporter.ExportResult.Success(file, inflows.size)
        } catch (e: Exception) {
            Log.e("DataImportExportHelper", "Error exporting inflows CSV/Excel: ${e.message}", e)
            return LocalReportExporter.ExportResult.Failure("Gagal mengekspor pemasukan: ${e.localizedMessage}", e)
        }
    }

    fun exportExpensesToCsv(context: Context, expenses: List<Expense>, useExcelFormat: Boolean = false): File? {
        val result = exportExpensesToCsvDetailed(context, expenses, useExcelFormat)
        return result.getOrNullFile()
    }

    fun exportExpensesToCsvDetailed(context: Context, expenses: List<Expense>, useExcelFormat: Boolean = false): LocalReportExporter.ExportResult {
        try {
            val dir = DocumentExporter.getExportDirectory(context, "finance")
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
            DocumentExporter.mirrorToDownloads(context, file, "Export")
            return LocalReportExporter.ExportResult.Success(file, expenses.size)
        } catch (e: Exception) {
            Log.e("DataImportExportHelper", "Error exporting expenses CSV/Excel: ${e.message}", e)
            return LocalReportExporter.ExportResult.Failure("Gagal mengekspor pengeluaran: ${e.localizedMessage}", e)
        }
    }

    fun exportCashLedgerToCsv(context: Context, transactions: List<UnifiedTxItem>, useExcelFormat: Boolean = false): File? {
        val result = exportCashLedgerToCsvDetailed(context, transactions, useExcelFormat)
        return result.getOrNullFile()
    }

    fun exportCashLedgerToCsvDetailed(context: Context, transactions: List<UnifiedTxItem>, useExcelFormat: Boolean = false): LocalReportExporter.ExportResult {
        try {
            val dir = DocumentExporter.getExportDirectory(context, "finance")
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
            DocumentExporter.mirrorToDownloads(context, file, "Export")
            return LocalReportExporter.ExportResult.Success(file, transactions.size)
        } catch (e: Exception) {
            Log.e("DataImportExportHelper", "Error exporting cash ledger CSV/Excel: ${e.message}", e)
            return LocalReportExporter.ExportResult.Failure("Gagal mengekspor buku kas: ${e.localizedMessage}", e)
        }
    }

    fun exportAjibqobulOrderHistoryToCsv(context: Context, invoices: List<Invoice>, useExcelFormat: Boolean = false): File? {
        val result = exportAjibqobulOrderHistoryToCsvDetailed(context, invoices, useExcelFormat)
        return result.getOrNullFile()
    }

    fun exportAjibqobulOrderHistoryToCsvDetailed(context: Context, invoices: List<Invoice>, useExcelFormat: Boolean = false): LocalReportExporter.ExportResult {
        try {
            val dir = DocumentExporter.getExportDirectory(context, "report")
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

            DocumentExporter.mirrorToDownloads(context, file, "Export")
            return LocalReportExporter.ExportResult.Success(file, invoices.size)
        } catch (e: Exception) {
            Log.e("DataImportExportHelper", "Error exporting Ajibqobul history CSV/Excel: ${e.message}", e)
            return LocalReportExporter.ExportResult.Failure("Gagal mengekspor riwayat transaksi: ${e.localizedMessage}", e)
        }
    }
}

