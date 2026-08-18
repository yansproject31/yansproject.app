package com.yansproject.app.data

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import androidx.annotation.Keep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.UUID

@Keep
enum class BarcodeFormat {
    CODE39,
    CODE128
}

@Keep
data class PrinterProfile(
    val name: String = "Generic Thermal 100x150",
    val charsetName: String = "UTF-8",
    val paperWidthMm: Int = 100,
    val paperHeightMm: Int = 150,
    val barcodeFormat: BarcodeFormat = BarcodeFormat.CODE39,
    val barcodeHeightDots: Byte = 0x50.toByte(),
    val barcodeWidthMultiplier: Byte = 0x03.toByte(),
    val nullTerminateBarcode: Boolean = true
)

@Keep
enum class ShippingPrintStatus {
    SUCCESS,
    TIMEOUT,
    CONNECTION_FAILED,
    PRINTER_ERROR,
    INVALID_MANIFEST,
    PERMISSION_DENIED
}

@Keep
data class ShippingPrintResult(
    val status: ShippingPrintStatus,
    val message: String
)

/**
 * Compiler and driver for printing 100x150mm Shipping Labels via Bluetooth Thermal Printer.
 * Designed purely for logistics (strictly zero financial leaks, protecting dropshipper privacy).
 */
class ShippingLabelCompiler {

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val sPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    companion object {
        // ESC/POS Command Constants
        val ESC_INIT = byteArrayOf(0x1B, 0x40)
        val ESC_ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00)
        val ESC_ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01)
        val ESC_ALIGN_RIGHT = byteArrayOf(0x1B, 0x61, 0x02)
        val ESC_BOLD_ON = byteArrayOf(0x1B, 0x45, 0x01)
        val ESC_BOLD_OFF = byteArrayOf(0x1B, 0x45, 0x00)
        
        // Font sizing
        val FONT_SIZE_NORMAL = byteArrayOf(0x1D, 0x21, 0x00)
        val FONT_SIZE_MEDIUM = byteArrayOf(0x1D, 0x21, 0x11) // 2x height, 2x width
        val FONT_SIZE_LARGE = byteArrayOf(0x1D, 0x21, 0x22)  // 3x height, 3x width
        
        val GS_BARCODE_H = byteArrayOf(0x1D, 0x68, 0x50)     // Height: 80 dots
        val GS_BARCODE_W = byteArrayOf(0x1D, 0x77, 0x03)     // Width: 3 (medium)
        val GS_BARCODE_PRINT_TEXT = byteArrayOf(0x1D, 0x48, 0x02) // Text below barcode
        val GS_BARCODE_CODE39 = byteArrayOf(0x1D, 0x6B, 0x04) // CODE39 format code
        val GS_BARCODE_CODE128 = byteArrayOf(0x1D, 0x6B, 0x49) // CODE128 format code
    }

    /**
     * Compiles shipping label into precise printer-friendly bytes (ESC/POS compatible).
     * Strictly no pricing, HPP, subtotal, or financial data included.
     */
    fun compileShippingLabelBytes(
        manifest: ShippingManifest,
        profile: PrinterProfile = PrinterProfile()
    ): ByteArray {
        val bytes = mutableListOf<Byte>()

        fun addBytes(arr: ByteArray) {
            bytes.addAll(arr.toList())
        }

        fun addString(str: String) {
            val charset = try {
                Charset.forName(profile.charsetName)
            } catch (e: Exception) {
                Charset.defaultCharset()
            }
            bytes.addAll(str.toByteArray(charset).toList())
        }

        fun addNewLine() {
            addString("\n")
        }

        // Initialize printer
        addBytes(ESC_INIT)

        // Title Area (Bold, Center)
        addBytes(ESC_ALIGN_CENTER)
        addBytes(FONT_SIZE_MEDIUM)
        addBytes(ESC_BOLD_ON)
        addString("================================\n")
        addString("      YANSPROJECT.ID ERP\n")
        addString("       LOGISTIK SHIPMENT\n")
        addString("================================\n")
        addBytes(ESC_BOLD_OFF)
        addBytes(FONT_SIZE_NORMAL)
        addNewLine()

        // Cargo & Tracking Barcode
        addBytes(ESC_ALIGN_CENTER)
        addBytes(ESC_BOLD_ON)
        addString("EKSPEDISI: ${manifest.cargoProvider.uppercase()}\n")
        addBytes(ESC_BOLD_OFF)
        addNewLine()
        
        // Render 1D Barcode if manifest number is valid
        if (manifest.manifestBarcode.isNotEmpty()) {
            addBytes(byteArrayOf(0x1D, 0x68, profile.barcodeHeightDots))
            addBytes(byteArrayOf(0x1D, 0x77, profile.barcodeWidthMultiplier))
            addBytes(GS_BARCODE_PRINT_TEXT)
            if (profile.barcodeFormat == BarcodeFormat.CODE128) {
                addBytes(GS_BARCODE_CODE128)
            } else {
                addBytes(GS_BARCODE_CODE39)
            }
            addString(manifest.manifestBarcode)
            if (profile.nullTerminateBarcode) {
                bytes.add(0x00.toByte())
            }
            addNewLine()
        } else {
            addString("* MANIFES-LOGISTIK-EMPTY *\n")
        }
        addNewLine()

        // Receiver Details
        val clientNameDisplay = manifest.clientName.ifBlank { "[NAMA PELANGGAN KOSONG]" }
        val clientPhoneDisplay = manifest.clientPhone.ifBlank { "[TELEPON KOSONG]" }
        val clientAddressDisplay = manifest.clientAddress.ifBlank { "[ALAMAT PENGIRIMAN KOSONG]" }

        addBytes(ESC_ALIGN_LEFT)
        addString("--------------------------------\n")
        addBytes(ESC_BOLD_ON)
        addString("PENERIMA (CUSTOMER):\n")
        addBytes(FONT_SIZE_MEDIUM)
        addString("$clientNameDisplay\n")
        addBytes(FONT_SIZE_NORMAL)
        addString("Telp/WA: $clientPhoneDisplay\n")
        addBytes(ESC_BOLD_OFF)
        addString("Alamat Kirim:\n")
        addString("$clientAddressDisplay\n")
        addString("--------------------------------\n")
        addNewLine()

        // Shipping Items list
        addBytes(ESC_BOLD_ON)
        addString("Rincian Paket Fisik:\n")
        addBytes(ESC_BOLD_OFF)
        
        manifest.items.forEachIndexed { index, item ->
            val num = index + 1
            addString("$num. ${item.catalogName}\n")
            addString("   Ukuran: ${item.size} | Lengan: ${item.sleeve} | Qty: ${item.quantity} pcs\n")
        }
        addString("--------------------------------\n")
        addBytes(ESC_ALIGN_CENTER)
        addBytes(ESC_BOLD_ON)
        addString("LOGISTIK SHIELD SECURITY ACTIVE\n")
        addString("DILARANG MEMBUKA PAKET TANPA VIDEO\n")
        addBytes(ESC_BOLD_OFF)
        addString("Terima kasih atas kerja samanya.\n")
        addString("================================\n")
        
        // Feed & Cut commands
        addNewLine()
        addNewLine()
        addNewLine()
        addNewLine()
        addBytes(byteArrayOf(0x1D, 0x56, 0x42, 0x00)) // Paper cut

        return bytes.toByteArray()
    }

    /**
     * Finds paired Bluetooth devices and ranks thermal printer candidates first.
     * Heuristics are used for candidate ranking only, not as absolute exclusion filter.
     */
    fun getPairedPrinters(): List<BluetoothDevice> {
        val printers = mutableListOf<BluetoothDevice>()
        val nonMatching = mutableListOf<BluetoothDevice>()
        try {
            val paired = bluetoothAdapter?.bondedDevices
            if (paired != null) {
                for (device in paired) {
                    val name = device.name?.lowercase() ?: ""
                    if (name.contains("printer") || name.contains("thermal") || name.contains("pos") || name.contains("58") || name.contains("80") || name.contains("spp")) {
                        printers.add(device)
                    } else {
                        nonMatching.add(device)
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e("ShippingLabelCompiler", "Bluetooth permissions not granted", e)
        }
        return printers + nonMatching
    }

    /**
     * Executes label printing with typed result, running off Main thread on Dispatchers.IO
     * with an explicit connection timeout (default 10,000ms).
     */
    suspend fun printShippingLabelTyped(
        device: BluetoothDevice,
        manifest: ShippingManifest,
        profile: PrinterProfile = PrinterProfile(),
        timeoutMs: Long = 10000L
    ): ShippingPrintResult = withContext(Dispatchers.IO) {
        if (manifest.items.isEmpty() && manifest.clientName.isBlank()) {
            return@withContext ShippingPrintResult(
                ShippingPrintStatus.INVALID_MANIFEST,
                "Shipping manifest contains no recipient details or line items"
            )
        }

        val result = withTimeoutOrNull(timeoutMs) {
            var socket: BluetoothSocket? = null
            var outputStream: OutputStream? = null
            try {
                bluetoothAdapter?.cancelDiscovery()
                socket = device.createRfcommSocketToServiceRecord(sPP_UUID)
                socket.connect()

                outputStream = socket.outputStream
                val labelBytes = compileShippingLabelBytes(manifest, profile)
                outputStream.write(labelBytes)
                outputStream.flush()
                ShippingPrintResult(ShippingPrintStatus.SUCCESS, "Shipping label printed successfully")
            } catch (se: SecurityException) {
                Log.e("ShippingLabelCompiler", "Bluetooth permission denied", se)
                ShippingPrintResult(ShippingPrintStatus.PERMISSION_DENIED, "Bluetooth permission missing: ${se.message}")
            } catch (e: Exception) {
                Log.e("ShippingLabelCompiler", "Printing label failed: ${e.message}", e)
                ShippingPrintResult(ShippingPrintStatus.CONNECTION_FAILED, "Printer connection failed: ${e.message}")
            } finally {
                try {
                    outputStream?.close()
                    socket?.close()
                } catch (_: Exception) { }
            }
        }

        result ?: ShippingPrintResult(
            ShippingPrintStatus.TIMEOUT,
            "Printing timed out after ${timeoutMs}ms"
        )
    }

    /**
     * Synchronous/legacy wrapper returning Boolean for existing caller compatibility.
     */
    fun printShippingLabel(device: BluetoothDevice, manifest: ShippingManifest): Boolean {
        return try {
            val result = kotlinx.coroutines.runBlocking {
                printShippingLabelTyped(device, manifest)
            }
            result.status == ShippingPrintStatus.SUCCESS
        } catch (e: Exception) {
            Log.e("ShippingLabelCompiler", "Legacy printShippingLabel error", e)
            false
        }
    }
}

