package com.yansproject.app.data

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import java.io.OutputStream
import java.math.BigDecimal
import java.util.UUID

/**
 * ExtendedThermalPrinterManager: Hardware interface manager for sending ESC/POS formatted
 * byte streams to Bluetooth Thermal Receipt Printers (58mm or 80mm width standard).
 */
object ExtendedThermalPrinterManager {

    private const val TAG = "ThermalPrinterManager"
    
    // Standard SPP UUID for Serial Bluetooth devices
    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    // ESC/POS Commands Constants
    private val ESC_ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00)
    private val ESC_ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01)
    private val ESC_ALIGN_RIGHT = byteArrayOf(0x1B, 0x61, 0x02)
    private val ESC_TEXT_NORMAL = byteArrayOf(0x1B, 0x21, 0x00)
    private val ESC_TEXT_BOLD_ON = byteArrayOf(0x1B, 0x21, 0x08)
    private val ESC_TEXT_DOUBLE_HEIGHT = byteArrayOf(0x1B, 0x21, 0x10)
    private val ESC_TEXT_DOUBLE_WIDTH = byteArrayOf(0x1B, 0x21, 0x20)
    private val ESC_INIT = byteArrayOf(0x1B, 0x40)
    private val ESC_FEED_LINES_4 = byteArrayOf(0x1B, 0x64, 0x04)

    /**
     * Connects to a paired bluetooth device and streams the formatted invoice commands.
     */
    fun printInvoiceBluetooth(
        deviceAddress: String,
        projectName: String,
        clientName: String,
        totalAmount: Double,
        paidAmount: Double,
        remainingBalance: Double,
        status: String,
        isPaper80mm: Boolean = false
    ): Boolean {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter() ?: return false
        if (!bluetoothAdapter.isEnabled) return false

        var socket: BluetoothSocket? = null
        var outputStream: OutputStream? = null

        return try {
            val device: BluetoothDevice = bluetoothAdapter.getRemoteDevice(deviceAddress)
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket.connect()
            outputStream = socket.outputStream

            // 1. Initialize printer and alignments
            outputStream.write(ESC_INIT)
            outputStream.write(ESC_ALIGN_CENTER)
            
            // 2. Double-size Title
            outputStream.write(ESC_TEXT_DOUBLE_HEIGHT)
            outputStream.write(ESC_TEXT_DOUBLE_WIDTH)
            outputStream.write("YANSPROJECT.ID\n".toByteArray(Charsets.US_ASCII))
            
            // 3. Subtitle / Tagline
            outputStream.write(ESC_TEXT_NORMAL)
            outputStream.write("Konveksi & Custom Project\n".toByteArray(Charsets.US_ASCII))
            outputStream.write("Telp: +62 822-1926-2026\n".toByteArray(Charsets.US_ASCII))
            
            val lineCharLimit = if (isPaper80mm) 48 else 32
            val dividerLine = "-".repeat(lineCharLimit) + "\n"
            outputStream.write(dividerLine.toByteArray(Charsets.US_ASCII))

            // 4. Details (Left Aligned)
            outputStream.write(ESC_ALIGN_LEFT)
            outputStream.write("Invoice No : INV-PRJ-${System.currentTimeMillis().toString().substring(6)}\n".toByteArray(Charsets.US_ASCII))
            outputStream.write("Project    : $projectName\n".toByteArray(Charsets.US_ASCII))
            outputStream.write("Klien      : $clientName\n".toByteArray(Charsets.US_ASCII))
            outputStream.write("Status     : $status\n".toByteArray(Charsets.US_ASCII))
            outputStream.write(dividerLine.toByteArray(Charsets.US_ASCII))

            // 5. High-Precision Totals
            outputStream.write(ESC_TEXT_BOLD_ON)
            outputStream.write(formatLineItem("GRAND TOTAL", IdrAccountingEngine.formatRupiah(totalAmount), lineCharLimit).toByteArray(Charsets.US_ASCII))
            outputStream.write(formatLineItem("TERBAYAR", IdrAccountingEngine.formatRupiah(paidAmount), lineCharLimit).toByteArray(Charsets.US_ASCII))
            outputStream.write(formatLineItem("SISA TAGIHAN", IdrAccountingEngine.formatRupiah(remainingBalance), lineCharLimit).toByteArray(Charsets.US_ASCII))
            outputStream.write(ESC_TEXT_NORMAL)
            outputStream.write(dividerLine.toByteArray(Charsets.US_ASCII))

            // 6. Centered Akad / Qobul Footer Contract
            outputStream.write(ESC_ALIGN_CENTER)
            outputStream.write("DENGAN PERSETUJUAN INI,\n".toByteArray(Charsets.US_ASCII))
            outputStream.write("KEDUA BELAH PIHAK TELAH MENYATAKAN\n".toByteArray(Charsets.US_ASCII))
            outputStream.write("SAH AKAD JUAL BELI SECARA ADIL.\n\n".toByteArray(Charsets.US_ASCII))
            outputStream.write("Syukron Katsiron atas kepercayaan Anda!\n".toByteArray(Charsets.US_ASCII))

            // Feed and Cut paper commands
            outputStream.write(ESC_FEED_LINES_4)
            outputStream.flush()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Bluetooth ESC/POS printing failed", e)
            false
        } finally {
            try {
                outputStream?.close()
                socket?.close()
            } catch (ex: Exception) {
                Log.e(TAG, "Failed closing Bluetooth socket streams", ex)
            }
        }
    }

    /**
     * Formats left aligned name and right aligned price into a single line based on printer column width.
     */
    private fun formatLineItem(leftText: String, rightText: String, lineCharLimit: Int): String {
        val totalLen = leftText.length + rightText.length
        return if (totalLen >= lineCharLimit) {
            val trimLeft = if (leftText.length > (lineCharLimit - rightText.length - 2)) {
                leftText.substring(0, lineCharLimit - rightText.length - 3) + ".."
            } else {
                leftText
            }
            val padding = " ".repeat((lineCharLimit - trimLeft.length - rightText.length).coerceAtLeast(1))
            trimLeft + padding + rightText + "\n"
        } else {
            val padding = " ".repeat(lineCharLimit - totalLen)
            leftText + padding + rightText + "\n"
        }
    }
}
