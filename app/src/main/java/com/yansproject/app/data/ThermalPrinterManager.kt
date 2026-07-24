package com.yansproject.app.data

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.UUID

class ThermalPrinterManager(private val context: Context) {

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val sPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    companion object {
        // ESC/POS Commands
        val ESC_INIT = byteArrayOf(0x1B, 0x40)
        val ESC_ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00)
        val ESC_ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01)
        val ESC_ALIGN_RIGHT = byteArrayOf(0x1B, 0x61, 0x02)
        val ESC_BOLD_ON = byteArrayOf(0x1B, 0x45, 0x01)
        val ESC_BOLD_OFF = byteArrayOf(0x1B, 0x45, 0x00)
        val ESC_DOUBLE_HEIGHT_ON = byteArrayOf(0x1B, 0x21, 0x10)
        val ESC_DOUBLE_WIDTH_ON = byteArrayOf(0x1B, 0x21, 0x20)
        val ESC_FONT_LARGE = byteArrayOf(0x1D, 0x21, 0x11)
        val ESC_FONT_NORMAL = byteArrayOf(0x1D, 0x21, 0x00)
        val FEED_PAPER_AND_CUT = byteArrayOf(0x1D, 0x56, 0x42, 0x00)
    }

    /**
     * Retrieves all currently paired Bluetooth devices that can be potentially used as printers.
     */
    fun getPairedPrinters(): List<BluetoothDevice> {
        val printers = mutableListOf<BluetoothDevice>()
        try {
            val paired = bluetoothAdapter?.bondedDevices
            if (paired != null) {
                for (device in paired) {
                    val name = device.name.lowercase()
                    if (name.contains("printer") || name.contains("thermal") || name.contains("pos") || name.contains("58") || name.contains("80")) {
                        printers.add(device)
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e("ThermalPrinterManager", "Bluetooth permission not granted", e)
        }
        return printers
    }

    /**
     * Establishes RFCOMM connection and sendscompiled ESC/POS receipt data bytes.
     */
    fun printReceipt(device: BluetoothDevice, invoice: OperationalInvoice, items: List<InvoiceItemDetail>): Boolean {
        var socket: BluetoothSocket? = null
        var outputStream: OutputStream? = null
        return try {
            socket = device.createRfcommSocketToServiceRecord(sPP_UUID)
            bluetoothAdapter?.cancelDiscovery()
            socket.connect()

            outputStream = socket.outputStream
            val receiptBytes = compileReceiptBytes(invoice, items)
            outputStream.write(receiptBytes)
            outputStream.flush()
            true
        } catch (e: Exception) {
            Log.e("ThermalPrinterManager", "Printing failed", e)
            false
        } finally {
            try {
                outputStream?.close()
                socket?.close()
            } catch (e: Exception) {
                // Ignore closing exceptions
            }
        }
    }

    /**
     * Compiles raw invoice data into structured printer-ready byte sequences (formatted with header, divider lines, product tables, and qr footer).
     */
    private fun compileReceiptBytes(invoice: OperationalInvoice, items: List<InvoiceItemDetail>): ByteArray {
        val bytes = mutableListOf<Byte>()

        fun addBytes(arr: ByteArray) {
            bytes.addAll(arr.toList())
        }

        fun addString(str: String) {
            bytes.addAll(str.toByteArray(Charset.forName("GBK")).toList())
        }

        // Initialize printer
        addBytes(ESC_INIT)

        // Header - Center aligned
        addBytes(ESC_ALIGN_CENTER)
        addBytes(ESC_BOLD_ON)
        addBytes(ESC_DOUBLE_HEIGHT_ON)
        addString("YANSPROJECT.ID\n")
        addBytes(ESC_DOUBLE_HEIGHT_ON.plus(ESC_DOUBLE_WIDTH_ON))
        addBytes(ESC_FONT_NORMAL)
        addBytes(ESC_BOLD_OFF)
        addString("Premium Konveksi & Digital Store\n")
        addString("================================\n")

        // Metadata - Left aligned
        addBytes(ESC_ALIGN_LEFT)
        addString("Invoice: ${invoice.invoiceNumber}\n")
        addString("Tanggal: ${formatDate(invoice.issueDate)}\n")
        addString("Client : ${invoice.clientName}\n")
        addString("Hp     : ${invoice.clientPhone}\n")
        addString("--------------------------------\n")

        // Product Table Header
        addBytes(ESC_BOLD_ON)
        // 32 chars width (for 58mm printer)
        // Name (16 chars), Qty (4 chars), Price (12 chars)
        addString(padString("Item Description", 16) + padString("Qty", 4) + padString("Price", 12) + "\n")
        addBytes(ESC_BOLD_OFF)
        addString("--------------------------------\n")

        // Product Rows
        for (item in items) {
            val namePart = if (item.description.length > 15) item.description.substring(0, 15) else item.description
            val qtyStr = item.quantity.toString()
            val formattedPrice = formatCompactRupiah(item.price)
            addString(padString(namePart, 16) + padString(qtyStr, 4) + padString(formattedPrice, 12) + "\n")
        }
        addString("--------------------------------\n")

        // Financial Totals - Right Aligned
        addBytes(ESC_ALIGN_RIGHT)
        addString("Total Belanja: " + formatCompactRupiah(invoice.totalAmount) + "\n")
        if (invoice.discount > 0) {
            addString("Diskon       : -" + formatCompactRupiah(invoice.discount) + "\n")
        }
        if (invoice.dpAmount > 0) {
            addString("Uang Muka/DP : " + formatCompactRupiah(invoice.dpAmount) + "\n")
        }
        addBytes(ESC_BOLD_ON)
        addString("Total Bayar  : " + formatCompactRupiah(invoice.paidAmount) + "\n")
        addString("Sisa Tagihan : " + formatCompactRupiah(invoice.remainingBalance) + "\n")
        addBytes(ESC_BOLD_OFF)
        addString("================================\n")

        // Footer & QR Placeholder text
        addBytes(ESC_ALIGN_CENTER)
        addString("Terima Kasih Atas Akad Syahdu Anda\n")
        addString("Layanan CS WhatsApp: 6281229995511\n")
        addString("Scan untuk cek status online:\n")
        
        // Custom biner QR Code sequence simulation (standard printer command for paper.id validation link)
        val trackingUrl = "https://yansproject.id/verify/${invoice.invoiceNumber}"
        addString("\n$trackingUrl\n\n")

        // Feed paper
        addString("\n\n\n")
        addBytes(FEED_PAPER_AND_CUT)

        return bytes.toByteArray()
    }

    private fun padString(str: String, size: Int): String {
        return if (str.length >= size) {
            str.substring(0, size)
        } else {
            str + " ".repeat(size - str.length)
        }
    }

    private fun formatDate(timestamp: Long): String {
        return java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
    }

    private fun formatCompactRupiah(amount: Double): String {
        return "Rp" + String.format(java.util.Locale.US, "%,.0f", amount).replace(",", ".")
    }
}
