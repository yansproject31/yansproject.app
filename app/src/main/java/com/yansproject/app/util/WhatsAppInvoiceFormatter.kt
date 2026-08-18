package com.yansproject.app.util

import android.content.Context
import com.yansproject.app.data.BusinessIdentityProvider
import com.yansproject.app.data.Invoice
import com.yansproject.app.data.InvoiceItemDetail
import com.yansproject.app.data.OperationalInvoice
import com.yansproject.app.ui.FormatUtils
import com.yansproject.app.ui.InvoiceItemSorter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Single-Source-Of-Truth formatter for WhatsApp Share Message Texts across the YANSPROJECT.ID app.
 * Guarantees brand identity, structured sections, itemized details, Akad Syar'i notice, and financial precision.
 */
object WhatsAppInvoiceFormatter {

    private const val BRAND_HEADER = "🧾 *FAKTUR INVOICE OFFICIAL YANSPROJECT.ID*"
    private const val BRAND_SUBTITLE = "_Luxury Visual Identity & Custom Merch_"
    private const val BRAND_SLOGAN = "• *Makna Sebelum Estetika *"
    private const val DIVIDER_DOUBLE = "══════════════════════════════════"
    private const val DIVIDER_SINGLE = "----------------------------------"
    private const val BRAND_FOOTER = "_Akad Jual-Beli (Ajib & Qobul) Sah, Halal & Terverifikasi Sistem ERP YANSPROJECT.ID._\n_Hatur Tengkyu telah menjadi bagian perjalanan YANSPROJECT.ID._"

    fun buildWhatsAppText(
        invoice: OperationalInvoice,
        items: List<InvoiceItemDetail>,
        context: Context? = null
    ): String {
        val presentation = com.yansproject.app.data.InvoiceFinancialCalculator.calculateFromOperational(invoice)
        val sdf = SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID"))
        val isPaid = presentation.remaining == 0L && presentation.grandTotal > 0L

        val statusEmoji = if (isPaid) "🟢" else if (presentation.paid > 0) "🟡" else "🔴"
        val statusText = if (isPaid) "LUNAS (PAID)" else if (presentation.paid > 0) "DIBAYAR SEBAGIAN (DP)" else "BELUM LUNAS (UNPAID)"

        val filteredItems = InvoiceItemSorter.sortInvoiceItems(items.filter { !it.description.startsWith("__") })

        val supportEmail = if (context != null) BusinessIdentityProvider.getSupportEmail(context) else BusinessIdentityProvider.DEFAULT_SUPPORT_EMAIL
        val supportPhone = if (context != null) BusinessIdentityProvider.getSupportWhatsApp(context) else BusinessIdentityProvider.DEFAULT_SUPPORT_WHATSAPP
        val supportContactText = "📞 *LAYANAN DUKUNGAN CS & LOKASI*\n• *WhatsApp CS* : $supportPhone\n• *Email Support*: $supportEmail"

        val sb = StringBuilder()
        sb.append(BRAND_HEADER).append("\n")
        sb.append(BRAND_SUBTITLE).append("\n")
        sb.append(BRAND_SLOGAN).append("\n")
        sb.append(DIVIDER_DOUBLE).append("\n\n")

        val displayInvNumber = if (presentation.invoiceNumber.isNotBlank()) presentation.invoiceNumber else "INV-PENDING"
        val issueDateFormatted = presentation.formattedDate

        sb.append("📋 *INFORMASI TRANSAKSI*\n")
        sb.append("• *No. Invoice*  : ").append(displayInvNumber).append("\n")
        sb.append("• *Tanggal*       : ").append(issueDateFormatted).append("\n")
        sb.append("• *Status*        : ").append(statusEmoji).append(" ").append(statusText).append("\n\n")

        val clientDisplayName = if (presentation.clientName.isNotBlank()) presentation.clientName else "Pelanggan General"
        sb.append("👤 *INFORMASI PELANGGAN*\n")
        sb.append("• *Nama Klien*    : ").append(clientDisplayName).append("\n")
        val phoneStr = if (presentation.clientPhone.isNotBlank()) presentation.clientPhone else "-"
        sb.append("• *No. HP/WA*     : ").append(phoneStr).append("\n\n")

        sb.append("🛒 *RINCIAN PESANAN / ARTIKEL*\n")
        if (filteredItems.isEmpty()) {
            sb.append("• 1x Custom Project Order - ").append(FormatUtils.formatRupiah(presentation.grandTotal.toDouble())).append("\n")
        } else {
            val shortItems = filteredItems.filter { InvoiceItemSorter.extractSleeve(it.description) == "Pendek" }
            val longItems = filteredItems.filter { InvoiceItemSorter.extractSleeve(it.description) == "Panjang" }

            var itemNum = 1
            if (shortItems.isNotEmpty()) {
                sb.append("👕 *LENGAN PENDEK:*\n")
                shortItems.forEach { item ->
                    val qty = if (item.quantity > 0) item.quantity else 1
                    val sub = item.price * qty
                    sb.append(" ${itemNum++}. *${item.description}*\n")
                    sb.append("    └ $qty Pcs @ ${FormatUtils.formatRupiah(item.price)} = *${FormatUtils.formatRupiah(sub)}*\n")
                }
            }

            if (longItems.isNotEmpty()) {
                if (shortItems.isNotEmpty()) sb.append("\n")
                sb.append("👔 *LENGAN PANJANG:*\n")
                longItems.forEach { item ->
                    val qty = if (item.quantity > 0) item.quantity else 1
                    val sub = item.price * qty
                    sb.append(" ${itemNum++}. *${item.description}*\n")
                    sb.append("    └ $qty Pcs @ ${FormatUtils.formatRupiah(item.price)} = *${FormatUtils.formatRupiah(sub)}*\n")
                }
            }
        }
        sb.append("\n").append(DIVIDER_SINGLE).append("\n")

        val shortQty = InvoiceItemSorter.getShortSleeveTotalQty(filteredItems)
        val longQty = InvoiceItemSorter.getLongSleeveTotalQty(filteredItems)
        val globalQty = if (filteredItems.isNotEmpty()) InvoiceItemSorter.getGlobalTotalQty(filteredItems) else presentation.quantity

        sb.append("📊 *RINCIAN KUANTITAS & KEUANGAN*\n")
        sb.append("• QTY PENDEK : ").append(shortQty).append(" Pcs\n")
        sb.append("• QTY PANJANG : ").append(longQty).append(" Pcs\n")
        sb.append("• *TOTAL QTY* : *").append(globalQty).append(" Pcs*\n")
        sb.append(DIVIDER_SINGLE).append("\n")

        sb.append("• *SUB TOTAL* : ").append(FormatUtils.formatRupiah(presentation.subtotal.toDouble())).append("\n")
        if (presentation.discount > 0L) {
            sb.append("• *DISKON* : - ").append(FormatUtils.formatRupiah(presentation.discount.toDouble())).append("\n")
        }
        sb.append("• *TOTAL* : *").append(FormatUtils.formatRupiah(presentation.grandTotal.toDouble())).append("*\n")
        sb.append("• *PEMBAYARAN* : ").append(FormatUtils.formatRupiah(presentation.paid.toDouble())).append("\n")
        sb.append(DIVIDER_SINGLE).append("\n")
        sb.append("🔥 *SISA PEMBAYARAN* : *").append(FormatUtils.formatRupiah(presentation.remaining.toDouble())).append("*\n")
        sb.append(DIVIDER_DOUBLE).append("\n\n")

        sb.append("🤝 *AKAD SYAR'I & KETERANGAN*\n")
        sb.append(BRAND_FOOTER).append("\n\n")

        sb.append(supportContactText).append("\n")
        sb.append("• *Link Verifikasi*: https://yansproject.id/verify/").append(displayInvNumber)

        return sb.toString()
    }

    fun buildWhatsAppText(
        invoice: Invoice,
        items: List<InvoiceItemDetail>,
        context: Context? = null
    ): String {
        val presentation = com.yansproject.app.data.InvoiceFinancialCalculator.calculatePresentation(invoice, items)
        val isPaid = presentation.remaining == 0L && presentation.grandTotal > 0L

        val statusEmoji = if (isPaid) "🟢" else if (presentation.paid > 0) "🟡" else "🔴"
        val statusText = if (isPaid) "LUNAS (PAID)" else if (presentation.paid > 0) "DIBAYAR SEBAGIAN (DP)" else "BELUM LUNAS (UNPAID)"

        val filteredItems = InvoiceItemSorter.sortInvoiceItems(items.filter { !it.description.startsWith("__") })

        val supportEmail = if (context != null) BusinessIdentityProvider.getSupportEmail(context) else BusinessIdentityProvider.DEFAULT_SUPPORT_EMAIL
        val supportPhone = if (context != null) BusinessIdentityProvider.getSupportWhatsApp(context) else BusinessIdentityProvider.DEFAULT_SUPPORT_WHATSAPP
        val supportContactText = "📞 *LAYANAN DUKUNGAN CS & LOKASI*\n• *WhatsApp CS* : $supportPhone\n• *Email Support*: $supportEmail"

        val sb = StringBuilder()
        sb.append(BRAND_HEADER).append("\n")
        sb.append(BRAND_SUBTITLE).append("\n")
        sb.append(BRAND_SLOGAN).append("\n")
        sb.append(DIVIDER_DOUBLE).append("\n\n")

        val displayInvNumber = if (presentation.invoiceNumber.isNotBlank()) presentation.invoiceNumber else "INV-PENDING"
        val issueDateFormatted = presentation.formattedDate

        sb.append("📋 *INFORMASI TRANSAKSI*\n")
        sb.append("• *No. Invoice*  : ").append(displayInvNumber).append("\n")
        sb.append("• *Tanggal*       : ").append(issueDateFormatted).append("\n")
        sb.append("• *Status*        : ").append(statusEmoji).append(" ").append(statusText).append("\n\n")

        val clientDisplayName = if (presentation.clientName.isNotBlank()) presentation.clientName else "Pelanggan General"
        sb.append("👤 *INFORMASI PELANGGAN*\n")
        sb.append("• *Nama Klien*    : ").append(clientDisplayName).append("\n")
        val phoneStr = if (presentation.clientPhone.isNotBlank()) presentation.clientPhone else "-"
        sb.append("• *No. HP/WA*     : ").append(phoneStr).append("\n\n")

        sb.append("🛒 *RINCIAN PESANAN / ARTIKEL*\n")
        if (filteredItems.isEmpty()) {
            sb.append("• 1x Custom Project Order - ").append(FormatUtils.formatRupiah(presentation.grandTotal.toDouble())).append("\n")
        } else {
            val shortItems = filteredItems.filter { InvoiceItemSorter.extractSleeve(it.description) == "Pendek" }
            val longItems = filteredItems.filter { InvoiceItemSorter.extractSleeve(it.description) == "Panjang" }

            var itemNum = 1
            if (shortItems.isNotEmpty()) {
                sb.append("👕 *LENGAN PENDEK:*\n")
                shortItems.forEach { item ->
                    val qty = if (item.quantity > 0) item.quantity else 1
                    val sub = item.price * qty
                    sb.append(" ${itemNum++}. *${item.description}*\n")
                    sb.append("    └ $qty Pcs @ ${FormatUtils.formatRupiah(item.price)} = *${FormatUtils.formatRupiah(sub)}*\n")
                }
            }

            if (longItems.isNotEmpty()) {
                if (shortItems.isNotEmpty()) sb.append("\n")
                sb.append("👔 *LENGAN PANJANG:*\n")
                longItems.forEach { item ->
                    val qty = if (item.quantity > 0) item.quantity else 1
                    val sub = item.price * qty
                    sb.append(" ${itemNum++}. *${item.description}*\n")
                    sb.append("    └ $qty Pcs @ ${FormatUtils.formatRupiah(item.price)} = *${FormatUtils.formatRupiah(sub)}*\n")
                }
            }
        }
        sb.append("\n").append(DIVIDER_SINGLE).append("\n")

        val shortQty = InvoiceItemSorter.getShortSleeveTotalQty(filteredItems)
        val longQty = InvoiceItemSorter.getLongSleeveTotalQty(filteredItems)
        val globalQty = if (filteredItems.isNotEmpty()) InvoiceItemSorter.getGlobalTotalQty(filteredItems) else presentation.quantity

        sb.append("📊 *RINCIAN KUANTITAS & KEUANGAN*\n")
        sb.append("• QTY PENDEK : ").append(shortQty).append(" Pcs\n")
        sb.append("• QTY PANJANG : ").append(longQty).append(" Pcs\n")
        sb.append("• *TOTAL QTY* : *").append(globalQty).append(" Pcs*\n")
        sb.append(DIVIDER_SINGLE).append("\n")

        sb.append("• *SUB TOTAL* : ").append(FormatUtils.formatRupiah(presentation.subtotal.toDouble())).append("\n")
        if (presentation.discount > 0L) {
            sb.append("• *DISKON* : - ").append(FormatUtils.formatRupiah(presentation.discount.toDouble())).append("\n")
        }
        sb.append("• *TOTAL* : *").append(FormatUtils.formatRupiah(presentation.grandTotal.toDouble())).append("*\n")
        sb.append("• *PEMBAYARAN* : ").append(FormatUtils.formatRupiah(presentation.paid.toDouble())).append("\n")
        sb.append(DIVIDER_SINGLE).append("\n")
        sb.append("🔥 *SISA PEMBAYARAN* : *").append(FormatUtils.formatRupiah(presentation.remaining.toDouble())).append("*\n")
        sb.append(DIVIDER_DOUBLE).append("\n\n")

        sb.append("🤝 *AKAD SYAR'I & KETERANGAN*\n")
        sb.append(BRAND_FOOTER).append("\n\n")

        sb.append(supportContactText).append("\n")
        sb.append("• *Link Verifikasi*: https://yansproject.id/verify/").append(displayInvNumber)

        return sb.toString()
    }
}
