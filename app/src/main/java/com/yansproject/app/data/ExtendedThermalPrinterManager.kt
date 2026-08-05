package com.yansproject.app.data

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import java.io.OutputStream
import java.util.UUID

/**
 * ExtendedThermalPrinterManager: Hardware interface manager for sending ESC/POS formatted
 * byte streams to Bluetooth Thermal Receipt Printers (58mm or 80mm width standard) with YANSPROJECT.ID Brand DNA.
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
     * Structured result for thermal printer operation states
     */
    sealed class PrinterResult {
        object Success : PrinterResult()
        object AdapterUnavailable : PrinterResult()
        object BluetoothDisabled : PrinterResult()
        data class DeviceNotFound(val address: String) : PrinterResult()
        data class ConnectionFailed(val message: String) : PrinterResult()
        data class ConnectionTimeout(val message: String) : PrinterResult()
        data class PrintFailed(val error: String) : PrinterResult()
    }

    /**
     * Connects to a paired bluetooth device and streams the formatted invoice commands with detailed PrinterResult.
     */
    fun printInvoiceBluetoothDetailed(
        context: Context,
        deviceAddress: String,
        projectName: String,
        clientName: String,
        totalAmount: Double,
        paidAmount: Double,
        remainingBalance: Double,
        status: String,
        isPaper80mm: Boolean = false
    ): PrinterResult {
        val bluetoothAdapter = try {
            BluetoothAdapter.getDefaultAdapter()
        } catch (e: SecurityException) {
            Log.e(TAG, "Bluetooth security permission missing: ${e.message}", e)
            return PrinterResult.AdapterUnavailable
        } ?: return PrinterResult.AdapterUnavailable

        if (!bluetoothAdapter.isEnabled) {
            Log.w(TAG, "Bluetooth hardware adapter is disabled")
            return PrinterResult.BluetoothDisabled
        }

        if (deviceAddress.isBlank()) {
            Log.w(TAG, "Printer MAC device address is blank")
            return PrinterResult.DeviceNotFound(deviceAddress)
        }

        var socket: BluetoothSocket? = null
        var outputStream: OutputStream? = null

        return try {
            val device: BluetoothDevice = try {
                bluetoothAdapter.getRemoteDevice(deviceAddress)
            } catch (e: Exception) {
                Log.e(TAG, "Invalid printer device address: $deviceAddress", e)
                return PrinterResult.DeviceNotFound(deviceAddress)
            }

            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            try {
                socket.connect()
            } catch (e: java.io.IOException) {
                val errMsg = e.message ?: "IO Socket Exception"
                if (errMsg.lowercase().contains("timeout")) {
                    Log.e(TAG, "Printer connection timeout to $deviceAddress", e)
                    return PrinterResult.ConnectionTimeout(errMsg)
                }
                Log.e(TAG, "Failed connecting RFCOMM socket to $deviceAddress: $errMsg", e)
                return PrinterResult.ConnectionFailed(errMsg)
            }

            outputStream = socket.outputStream

            // 1. Initialize printer and alignments
            outputStream.write(ESC_INIT)
            outputStream.write(ESC_ALIGN_CENTER)
            
            // 2. Double-size Title
            val storeName = BusinessIdentityProvider.getCompanyName(context)
            val csWa = BusinessIdentityProvider.getSupportWhatsApp(context)
            outputStream.write(ESC_TEXT_DOUBLE_HEIGHT)
            outputStream.write(ESC_TEXT_DOUBLE_WIDTH)
            outputStream.write("$storeName\n".toByteArray(Charsets.US_ASCII))
            
            // 3. Subtitle / Tagline
            outputStream.write(ESC_TEXT_NORMAL)
            outputStream.write("${BusinessIdentityProvider.DEFAULT_STORE_TAGLINE}\n".toByteArray(Charsets.US_ASCII))
            outputStream.write("Makna Sebelum Estetika\n".toByteArray(Charsets.US_ASCII))
            outputStream.write("CS WA: $csWa\n".toByteArray(Charsets.US_ASCII))
            
            val lineCharLimit = if (isPaper80mm) 48 else 32
            val dividerLine = "=".repeat(lineCharLimit) + "\n"
            outputStream.write(dividerLine.toByteArray(Charsets.US_ASCII))

            // 4. Details (Left Aligned)
            outputStream.write(ESC_ALIGN_LEFT)
            outputStream.write("No. Invoice: INV-PRJ-${System.currentTimeMillis().toString().takeLast(6)}\n".toByteArray(Charsets.US_ASCII))
            outputStream.write("Project    : $projectName\n".toByteArray(Charsets.US_ASCII))
            outputStream.write("Pelanggan  : $clientName\n".toByteArray(Charsets.US_ASCII))
            outputStream.write("Status     : $status\n".toByteArray(Charsets.US_ASCII))
            outputStream.write("-".repeat(lineCharLimit).toByteArray(Charsets.US_ASCII) + "\n".toByteArray(Charsets.US_ASCII))

            // 5. High-Precision Totals
            outputStream.write(ESC_TEXT_BOLD_ON)
            outputStream.write(formatLineItem("TOTAL BELANJA", IdrAccountingEngine.formatRupiah(totalAmount), lineCharLimit).toByteArray(Charsets.US_ASCII))
            outputStream.write(formatLineItem("TERBAYAR", IdrAccountingEngine.formatRupiah(paidAmount), lineCharLimit).toByteArray(Charsets.US_ASCII))
            outputStream.write(formatLineItem("SISA TAGIHAN", IdrAccountingEngine.formatRupiah(remainingBalance), lineCharLimit).toByteArray(Charsets.US_ASCII))
            outputStream.write(ESC_TEXT_NORMAL)
            outputStream.write(dividerLine.toByteArray(Charsets.US_ASCII))

            // 6. Centered Akad / Qobul Footer Contract
            outputStream.write(ESC_ALIGN_CENTER)
            outputStream.write("Akad Jual-Beli (Ajib & Qobul) Sah,\n".toByteArray(Charsets.US_ASCII))
            outputStream.write("Halal & Terverifikasi YANSPROJECT.ID\n\n".toByteArray(Charsets.US_ASCII))
            outputStream.write("Hatur Tengkyu atas kepercayaan Anda!\n".toByteArray(Charsets.US_ASCII))

            // Feed and Cut paper commands
            outputStream.write(ESC_FEED_LINES_4)
            outputStream.flush()
            Log.d(TAG, "Thermal receipt printed successfully to $deviceAddress")
            PrinterResult.Success
        } catch (e: Exception) {
            val errorMsg = e.localizedMessage ?: e.message ?: "Unknown Printing Exception"
            Log.e(TAG, "Bluetooth ESC/POS printing failed: $errorMsg", e)
            PrinterResult.PrintFailed(errorMsg)
        } finally {
            try {
                outputStream?.close()
                socket?.close()
            } catch (ex: Exception) {
                Log.e(TAG, "Failed closing Bluetooth socket streams: ${ex.message}", ex)
            }
        }
    }

    /**
     * Backward-compatible boolean wrapper for printInvoiceBluetooth
     */
    fun printInvoiceBluetooth(
        context: Context,
        deviceAddress: String,
        projectName: String,
        clientName: String,
        totalAmount: Double,
        paidAmount: Double,
        remainingBalance: Double,
        status: String,
        isPaper80mm: Boolean = false
    ): Boolean {
        return printInvoiceBluetoothDetailed(
            context, deviceAddress, projectName, clientName, totalAmount, paidAmount, remainingBalance, status, isPaper80mm
        ) is PrinterResult.Success
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
