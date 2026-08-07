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
        val sdf = SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID"))
        val remaining = (invoice.totalAmount - invoice.paidAmount - invoice.discount).coerceAtLeast(0.0)
        val isPaid = remaining <= 0

        val statusEmoji = if (isPaid) "🟢" else if (invoice.paidAmount > 0) "🟡" else "🔴"
        val statusText = if (isPaid) "LUNAS (PAID)" else if (invoice.paidAmount > 0) "DIBAYAR SEBAGIAN (DP)" else "BELUM LUNAS (UNPAID)"

        val filteredItems = InvoiceItemSorter.sortInvoiceItems(items.filter { !it.description.startsWith("__") })

        val supportEmail = if (context != null) BusinessIdentityProvider.getSupportEmail(context) else BusinessIdentityProvider.DEFAULT_SUPPORT_EMAIL
        val supportPhone = if (context != null) BusinessIdentityProvider.getSupportWhatsApp(context) else BusinessIdentityProvider.DEFAULT_SUPPORT_WHATSAPP
        val supportContactText = "📞 *LAYANAN DUKUNGAN CS & LOKASI*\n• *WhatsApp CS* : $supportPhone\n• *Email Support*: $supportEmail"

        val sb = StringBuilder()
        sb.append(BRAND_HEADER).append("\n")
        sb.append(BRAND_SUBTITLE).append("\n")
        sb.append(BRAND_SLOGAN).append("\n")
        sb.append(DIVIDER_DOUBLE).append("\n\n")

        val displayInvNumber = if (invoice.invoiceNumber.isNotBlank()) invoice.invoiceNumber else "INV-PENDING"
        val issueTime = if (invoice.issueDate > 0) invoice.issueDate else System.currentTimeMillis()

        sb.append("📋 *INFORMASI TRANSAKSI*\n")
        sb.append("• *No. Invoice*  : ").append(displayInvNumber).append("\n")
        sb.append("• *Tanggal*       : ").append(sdf.format(Date(issueTime))).append("\n")
        sb.append("• *Status*        : ").append(statusEmoji).append(" ").append(statusText).append("\n\n")

        val clientDisplayName = if (invoice.clientName.isNotBlank()) invoice.clientName else "Pelanggan General"
        sb.append("👤 *INFORMASI PELANGGAN*\n")
        sb.append("• *Nama Klien*    : ").append(clientDisplayName).append("\n")
        val phoneStr = if (invoice.clientPhone.isNotBlank()) invoice.clientPhone else "-"
        sb.append("• *No. HP/WA*     : ").append(phoneStr).append("\n\n")

        sb.append("🛒 *RINCIAN PESANAN / ARTIKEL*\n")
        if (filteredItems.isEmpty()) {
            sb.append("• 1x Custom Project Order - ").append(FormatUtils.formatRupiah(invoice.totalAmount)).append("\n")
        } else {
            filteredItems.forEachIndexed { idx, item ->
                val qty = if (item.quantity > 0) item.quantity else 1
                val subtotal = item.price * qty
                sb.append("${idx + 1}. *${item.description}*\n")
                sb.append("   └ $qty Pcs @ ${FormatUtils.formatRupiah(item.price)} = *${FormatUtils.formatRupiah(subtotal)}*\n")
            }
        }
        sb.append("\n").append(DIVIDER_DOUBLE).append("\n")

        sb.append("💳 *RINGKASAN PEMBAYARAN*\n")
        sb.append("• *Total Belanja* : ").append(FormatUtils.formatRupiah(invoice.totalAmount)).append("\n")
        if (invoice.discount > 0) {
            sb.append("• *Potongan Diskon*: - ").append(FormatUtils.formatRupiah(invoice.discount)).append("\n")
        }
        if (invoice.dpAmount > 0) {
            sb.append("• *Uang Muka (DP)* : ").append(FormatUtils.formatRupiah(invoice.dpAmount)).append("\n")
        }
        sb.append("• *Total Terbayar*: ").append(FormatUtils.formatRupiah(invoice.paidAmount)).append("\n")
        sb.append(DIVIDER_SINGLE).append("\n")
        sb.append("▶️ *SISA TAGIHAN*  : *").append(FormatUtils.formatRupiah(remaining)).append("*\n")
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
        val opInvoice = OperationalInvoice(
            id = invoice.id.toString(),
            invoiceNumber = invoice.invoiceNumber,
            clientName = invoice.clientName,
            clientPhone = invoice.clientPhone,
            issueDate = invoice.issueDate,
            dueDate = invoice.dueDate,
            totalAmount = invoice.totalAmount,
            paidAmount = invoice.paidAmount,
            status = invoice.status,
            discount = invoice.discount,
            dpAmount = invoice.dpAmount,
            itemsJson = invoice.itemsJson
        )
        return buildWhatsAppText(opInvoice, items, context)
    }
}
