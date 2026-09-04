package com.yansproject.app.data

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
        object Connected : PrinterResult()
        object DataSent : PrinterResult()
        data class Failed(val error: String, val cause: Throwable? = null) : PrinterResult()
        data class Timeout(val message: String) : PrinterResult()
        data class DeviceNotFound(val address: String) : PrinterResult()
        object BluetoothDisabled : PrinterResult()

        // Backward compatibility getters
        object Success : PrinterResult()
        val isSuccess: Boolean get() = this is DataSent || this is Connected || this is Success
    }

    /**
     * Connects to a paired bluetooth device on Dispatchers.IO and streams the formatted invoice commands.
     * Uses real persisted invoice numbers exclusively (never generates timestamps inside printer code).
     */
    suspend fun printInvoiceBluetoothDetailed(
        context: Context,
        deviceAddress: String,
        invoiceNumber: String,
        projectName: String,
        clientName: String,
        totalAmount: Double,
        paidAmount: Double,
        remainingBalance: Double,
        status: String,
        isPaper80mm: Boolean = false,
        encodingName: String = "UTF-8"
    ): PrinterResult = withContext(Dispatchers.IO) {
        if (deviceAddress.isBlank()) {
            Log.w(TAG, "Printer MAC device address is blank")
            return@withContext PrinterResult.DeviceNotFound(deviceAddress)
        }

        val bluetoothAdapter = try {
            BluetoothAdapter.getDefaultAdapter()
        } catch (e: SecurityException) {
            Log.e(TAG, "Bluetooth security permission missing: ${e.message}", e)
            return@withContext PrinterResult.Failed("Bluetooth security permission missing: ${e.message}", e)
        } ?: return@withContext PrinterResult.Failed("Bluetooth hardware adapter unavailable")

        if (!bluetoothAdapter.isEnabled) {
            Log.w(TAG, "Bluetooth hardware adapter is disabled")
            return@withContext PrinterResult.BluetoothDisabled
        }

        val device: BluetoothDevice = try {
            bluetoothAdapter.getRemoteDevice(deviceAddress)
        } catch (e: Exception) {
            Log.e(TAG, "Invalid printer device address: $deviceAddress", e)
            return@withContext PrinterResult.DeviceNotFound(deviceAddress)
        }

        val charset = try {
            java.nio.charset.Charset.forName(encodingName)
        } catch (e: Exception) {
            try {
                java.nio.charset.Charset.forName("GBK")
            } catch (_: Exception) {
                Charsets.UTF_8
            }
        }

        var socket: BluetoothSocket? = null
        var outputStream: OutputStream? = null

        return@withContext try {
            // Enforce explicit socket connect timeout (10 seconds)
            val connectCompleted = withTimeoutOrNull(10000L) {
                try {
                    socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                    bluetoothAdapter.cancelDiscovery()
                    socket?.connect()
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "RFCOMM socket connection attempt failed to $deviceAddress: ${e.message}", e)
                    false
                }
            }

            if (connectCompleted != true || socket?.isConnected != true) {
                try { socket?.close() } catch (_: Exception) {}
                Log.e(TAG, "Printer connection timeout (10000ms) to $deviceAddress")
                return@withContext PrinterResult.Timeout("Printer connection timeout (10000ms)")
            }

            outputStream = socket?.outputStream
                ?: return@withContext PrinterResult.Failed("Failed acquiring printer output stream")

            // 1. Initialize printer and alignments
            outputStream.write(ESC_INIT)
            outputStream.write(ESC_ALIGN_CENTER)
            
            // 2. Double-size Title
            val storeName = BusinessIdentityProvider.getCompanyName(context)
            val csWa = BusinessIdentityProvider.getSupportWhatsApp(context)
            outputStream.write(ESC_TEXT_DOUBLE_HEIGHT)
            outputStream.write(ESC_TEXT_DOUBLE_WIDTH)
            outputStream.write("$storeName\n".toByteArray(charset))
            
            // 3. Subtitle / Tagline
            outputStream.write(ESC_TEXT_NORMAL)
            outputStream.write("${BusinessIdentityProvider.DEFAULT_STORE_TAGLINE}\n".toByteArray(charset))
            outputStream.write("Makna Sebelum Estetika\n".toByteArray(charset))
            outputStream.write("CS WA: $csWa\n".toByteArray(charset))
            
            val lineCharLimit = if (isPaper80mm) 48 else 32
            val dividerLine = "=".repeat(lineCharLimit) + "\n"
            outputStream.write(dividerLine.toByteArray(charset))

            // 4. Details (Left Aligned) - Uses actual persisted invoice number
            outputStream.write(ESC_ALIGN_LEFT)
            outputStream.write("No. Invoice: $invoiceNumber\n".toByteArray(charset))
            outputStream.write("Project    : $projectName\n".toByteArray(charset))
            outputStream.write("Pelanggan  : $clientName\n".toByteArray(charset))
            outputStream.write("Status     : $status\n".toByteArray(charset))
            outputStream.write("-".repeat(lineCharLimit).toByteArray(charset) + "\n".toByteArray(charset))

            // 5. High-Precision Totals
            outputStream.write(ESC_TEXT_BOLD_ON)
            outputStream.write(formatLineItem("TOTAL BELANJA", IdrAccountingEngine.formatRupiah(totalAmount), lineCharLimit).toByteArray(charset))
            outputStream.write(formatLineItem("TERBAYAR", IdrAccountingEngine.formatRupiah(paidAmount), lineCharLimit).toByteArray(charset))
            outputStream.write(formatLineItem("SISA TAGIHAN", IdrAccountingEngine.formatRupiah(remainingBalance), lineCharLimit).toByteArray(charset))
            outputStream.write(ESC_TEXT_NORMAL)
            outputStream.write(dividerLine.toByteArray(charset))

            // 6. Centered Akad / Qobul Footer Contract
            outputStream.write(ESC_ALIGN_CENTER)
            outputStream.write("Akad Jual-Beli (Ajib & Qobul) Sah,\n".toByteArray(charset))
            outputStream.write("Halal & Terverifikasi YANSPROJECT.ID\n\n".toByteArray(charset))
            outputStream.write("Hatur Tengkyu atas kepercayaan Anda!\n".toByteArray(charset))

            // Feed paper commands
            outputStream.write(ESC_FEED_LINES_4)
            outputStream.flush()
            Log.d(TAG, "Thermal receipt byte stream handed to printer output for invoice $invoiceNumber")
            PrinterResult.DataSent
        } catch (e: Exception) {
            val errorMsg = e.localizedMessage ?: e.message ?: "Unknown Printing Exception"
            Log.e(TAG, "Bluetooth ESC/POS printing failed: $errorMsg", e)
            PrinterResult.Failed(errorMsg, e)
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
    suspend fun printInvoiceBluetooth(
        context: Context,
        deviceAddress: String,
        invoiceNumber: String = "",
        projectName: String,
        clientName: String,
        totalAmount: Double,
        paidAmount: Double,
        remainingBalance: Double,
        status: String,
        isPaper80mm: Boolean = false
    ): Boolean {
        val safeInvNumber = if (invoiceNumber.isBlank()) "INV-OFFLINE" else invoiceNumber
        return printInvoiceBluetoothDetailed(
            context = context,
            deviceAddress = deviceAddress,
            invoiceNumber = safeInvNumber,
            projectName = projectName,
            clientName = clientName,
            totalAmount = totalAmount,
            paidAmount = paidAmount,
            remainingBalance = remainingBalance,
            status = status,
            isPaper80mm = isPaper80mm
        ).isSuccess
    }

    /**
     * Formats left aligned name and right aligned price into a single line based on printer column width.
     */
    private fun formatLineItem(leftText: String, rightText: String, lineCharLimit: Int): String {
        val totalLen = leftText.length + rightText.length
        return if (totalLen >= lineCharLimit) {
            val maxLeftLen = (lineCharLimit - rightText.length - 3).coerceAtLeast(0)
            val trimLeft = if (leftText.length > (lineCharLimit - rightText.length - 2)) {
                if (maxLeftLen > 0) leftText.take(maxLeftLen) + ".." else ".."
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
