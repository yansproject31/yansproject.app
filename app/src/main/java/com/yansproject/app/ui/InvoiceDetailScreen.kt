package com.yansproject.app.ui

import android.bluetooth.BluetoothDevice
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.yansproject.app.data.*
import com.yansproject.app.ui.theme.*
import java.util.*
import org.json.JSONArray
import org.json.JSONObject

data class OrderItemParsed(
    val seriesName: String,
    val size: String,
    val qty: Int,
    val sleeve: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceDetailScreen(
    invoice: Invoice,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val converters = remember { AppTypeConverters() }
    val printerManager = remember { ThermalPrinterManager(context) }
    val documentRenderer = remember { LocalDocumentRenderer(context) }

    // Parse items from serialized JSON using OrderItemParsed structure via org.json
    val parsedItems = remember(invoice.itemsJson) {
        val list = mutableListOf<OrderItemParsed>()
        try {
            val array = JSONArray(invoice.itemsJson)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                if (obj.has("seriesName") && obj.has("size")) {
                    list.add(
                        OrderItemParsed(
                            seriesName = obj.optString("seriesName", ""),
                            size = obj.optString("size", "-"),
                            qty = obj.optInt("qty", obj.optInt("quantity", 0)),
                            sleeve = obj.optString("sleeve", "-")
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        if (list.isEmpty()) {
            try {
                val rawItems = converters.toInvoiceItemList(invoice.itemsJson)
                rawItems.filter { !it.description.startsWith("__") }.forEach { item ->
                    val desc = item.description
                    var seriesName = desc
                    var size = "-"
                    var sleeve = "-"
                    if (desc.startsWith("AJIBQOBUL:", ignoreCase = true)) {
                        val cleanDesc = desc.removePrefix("AJIBQOBUL:").removePrefix("ajibqobul:").trim()
                        val parts = cleanDesc.split(" - ")
                        if (parts.size >= 4) {
                            seriesName = "${parts[0]} (${parts[1]})"
                            size = parts[2].trim()
                            sleeve = parts[3].trim()
                        } else if (parts.size == 3) {
                            seriesName = parts[0].trim()
                            size = parts[1].trim()
                            sleeve = parts[2].trim()
                        } else if (parts.size == 2) {
                            seriesName = parts[0].trim()
                            size = parts[1].trim()
                        }
                    } else if (desc.startsWith("Custom:", ignoreCase = true)) {
                        val cleanDesc = desc.removePrefix("Custom:").trim()
                        val parts = cleanDesc.split(" - ")
                        if (parts.size >= 3) {
                            seriesName = "Custom: ${parts[0]}"
                            sleeve = parts[1].trim()
                            size = parts[2].trim()
                        } else if (parts.size == 2) {
                            seriesName = "Custom: ${parts[0]}"
                            size = parts[1].trim()
                        }
                    } else if (desc.startsWith("Pembelian:", ignoreCase = true)) {
                        val cleanDesc = desc.removePrefix("Pembelian:").trim()
                        val parts = cleanDesc.split(" - ")
                        if (parts.size >= 4) {
                            seriesName = "${parts[0]} (${parts[1]})"
                            size = parts[2].trim()
                            sleeve = parts[3].trim()
                        } else if (parts.size == 3) {
                            seriesName = parts[0].trim()
                            size = parts[1].trim()
                            sleeve = parts[2].trim()
                        } else if (parts.size == 2) {
                            seriesName = parts[0].trim()
                            size = parts[1].trim()
                        }
                    }
                    list.add(
                        OrderItemParsed(
                            seriesName = seriesName,
                            size = size,
                            qty = item.quantity,
                            sleeve = sleeve
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        list.sortedWith(
            compareBy<OrderItemParsed> { com.yansproject.app.ui.InvoiceItemSorter.getSleeveIndex(it.sleeve) }
                .thenBy { com.yansproject.app.ui.InvoiceItemSorter.getSizeIndex(it.size) }
        )
    }

    val invoiceItems = remember(invoice.itemsJson) {
        converters.toInvoiceItemList(invoice.itemsJson)
    }

    // Dynamic state trackers for reactive updates
    var currentPaidAmount by remember(invoice.paidAmount) { mutableStateOf(invoice.paidAmount) }
    val remainingBalance = invoice.totalAmount - currentPaidAmount - invoice.discount

    // Staged Payment Calculations (Tenor Splits)
    val tenor1Amount = if (invoice.dpAmount > 0) invoice.dpAmount else (invoice.totalAmount * 0.3)
    val tenor2Amount = (invoice.totalAmount * 0.4)
    val tenor3Amount = (invoice.totalAmount - tenor1Amount - tenor2Amount).coerceAtLeast(0.0)

    val isTenor1Paid = currentPaidAmount >= tenor1Amount
    val isTenor2Paid = currentPaidAmount >= (tenor1Amount + tenor2Amount)
    val isTenor3Paid = currentPaidAmount >= (invoice.totalAmount - invoice.discount)

    // Bluetooth Printer & Preview dialog states
    var showPrinterDialog by remember { mutableStateOf(false) }
    var showDocumentPreviewDialog by remember { mutableStateOf(false) }
    var pairedPrinters by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(AgedGold.copy(alpha = 0.15f))
                                .border(0.8.dp, AgedGold.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ReceiptLong,
                                contentDescription = null,
                                tint = AgedGold,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "FAKTUR INVOICE DETAIL",
                                fontWeight = FontWeight.Black,
                                color = AgedGold,
                                fontSize = 15.sp,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "YANSPROJECT.ID ERP SYSTEM",
                                fontSize = 9.sp,
                                color = HighlightSoftCyan,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Outlined.ArrowBack,
                            contentDescription = "Kembali",
                            tint = AgedGold
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showDocumentPreviewDialog = true },
                        modifier = Modifier.testTag("preview_doc_topbar_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Visibility,
                            contentDescription = "Pratinjau Dokumen",
                            tint = AgedGold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SecondaryShadowBlackTeal.copy(alpha = 0.95f)
                )
            )
        },
        containerColor = Color(0xFF0A0A0A) // Shadow Black
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Status Overview Header Card (Glassmorphism M3)
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.2.dp,
                            brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                colors = listOf(
                                    AgedGold.copy(alpha = 0.6f),
                                    HighlightSoftCyan.copy(alpha = 0.4f),
                                    AgedGold.copy(alpha = 0.6f)
                                )
                            ),
                            shape = RoundedCornerShape(20.dp)
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                    colors = listOf(
                                        CardDarkCard.copy(alpha = 0.95f),
                                        SurfaceDarkTeal.copy(alpha = 0.9f),
                                        SecondaryShadowBlackTeal.copy(alpha = 0.98f)
                                    )
                                )
                            )
                            .padding(18.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // NOMINAL (Primary Focus)
                            Text(
                                text = "TOTAL TAGIHAN FAKTUR",
                                color = TextNonActive,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.5.sp
                            )
                            Text(
                                text = FormatUtils.formatRupiah(invoice.totalAmount),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black,
                                color = AgedGold,
                                letterSpacing = 0.5.sp
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            // STATUS BADGE (Secondary Focus)
                            val badgeColor = if (isTenor3Paid) HighlightSoftCyan else AgedGold
                            val badgeBg = if (isTenor3Paid) HighlightSoftCyan.copy(alpha = 0.15f) else AgedGold.copy(alpha = 0.15f)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(30.dp))
                                    .background(badgeBg)
                                    .border(1.dp, badgeColor.copy(alpha = 0.5f), RoundedCornerShape(30.dp))
                                    .padding(horizontal = 18.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(badgeColor)
                                    )
                                    Text(
                                        text = if (isTenor3Paid) "LUNAS (PAID)" else if (currentPaidAmount > 0) "DIBAYAR SEBAGIAN" else "BELUM LUNAS",
                                        color = badgeColor,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            HorizontalDivider(color = AgedGold.copy(alpha = 0.2f), thickness = 1.dp)
                            Spacer(modifier = Modifier.height(4.dp))

                            // CUSTOMER & INVOICE # Grid
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(imageVector = Icons.Outlined.Person, contentDescription = null, tint = HighlightSoftCyan, modifier = Modifier.size(12.dp))
                                        Text(
                                            text = "PELANGGAN",
                                            color = TextNonActive,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        )
                                    }
                                    Text(
                                        text = invoice.clientName,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (invoice.clientPhone.isNotBlank()) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Icon(imageVector = Icons.Outlined.Phone, contentDescription = null, tint = TextNonActive, modifier = Modifier.size(11.dp))
                                            Text(
                                                text = invoice.clientPhone,
                                                color = TextNonActive,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "NO. INVOICE",
                                        color = TextNonActive,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                    Text(
                                        text = invoice.invoiceNumber,
                                        color = AgedGold,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    val sdf = remember { java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale("id", "ID")) }
                                    Text(
                                        text = "Tgl: ${sdf.format(java.util.Date(invoice.issueDate))}",
                                        color = TextNonActive,
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(2.dp))
                            HorizontalDivider(color = AgedGold.copy(alpha = 0.15f), thickness = 1.dp)
                            Spacer(modifier = Modifier.height(4.dp))

                            // Secondary payment breakdown
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Sudah Dibayar", color = TextNonActive, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                    Text(FormatUtils.formatRupiah(currentPaidAmount), color = HighlightSoftCyan, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Sisa Piutang", color = TextNonActive, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                    Text(FormatUtils.formatRupiah(remainingBalance), color = if (remainingBalance > 0) AlertRed else HighlightSoftCyan, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // 2. Staged Payments Progress Trackers
            item {
                Text(
                    text = "RENCANA PEMBAYARAN BERTAHAP (STAGED)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AgedGold,
                    letterSpacing = 1.sp
                )
            }

            // Tenor 1: Down Payment
            item {
                StagedTenorCard(
                    tenorNumber = 1,
                    label = "Down Payment / Uang Muka (30%)",
                    amount = tenor1Amount,
                    isPaid = isTenor1Paid,
                    onPay = {
                        val updatedInvoice = invoice.copy(
                            paidAmount = tenor1Amount,
                            status = if (tenor1Amount >= invoice.totalAmount) "LUNAS" else "BELUM LUNAS"
                        )
                        viewModel.updateInvoiceFully(updatedInvoice)
                        currentPaidAmount = tenor1Amount
                        viewModel.addAuditLog("Bayar Staged", "Pembayaran Tenor 1 (DP) Invoice ${invoice.invoiceNumber} berhasil.")
                        Toast.makeText(context, "Tenor 1 DP Berhasil Dibayar!", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            // Tenor 2: Mid-Production
            item {
                StagedTenorCard(
                    tenorNumber = 2,
                    label = "Termin Produksi (40%)",
                    amount = tenor2Amount,
                    isPaid = isTenor2Paid,
                    onPay = {
                        val newAmount = tenor1Amount + tenor2Amount
                        val updatedInvoice = invoice.copy(
                            paidAmount = newAmount,
                            status = if (newAmount >= invoice.totalAmount) "LUNAS" else "BELUM LUNAS"
                        )
                        viewModel.updateInvoiceFully(updatedInvoice)
                        currentPaidAmount = newAmount
                        viewModel.addAuditLog("Bayar Staged", "Pembayaran Tenor 2 (Termin) Invoice ${invoice.invoiceNumber} berhasil.")
                        Toast.makeText(context, "Tenor 2 Termin Berhasil Dibayar!", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            // Tenor 3: Final Settlement
            item {
                StagedTenorCard(
                    tenorNumber = 3,
                    label = "Pelunasan & Serah Terima (Sisa)",
                    amount = tenor3Amount,
                    isPaid = isTenor3Paid,
                    onPay = {
                        val updatedInvoice = invoice.copy(
                            paidAmount = invoice.totalAmount - invoice.discount,
                            status = "LUNAS"
                        )
                        viewModel.updateInvoiceFully(updatedInvoice)
                        currentPaidAmount = invoice.totalAmount - invoice.discount
                        viewModel.addAuditLog("Bayar Staged", "Pelunasan Invoice ${invoice.invoiceNumber} berhasil.")
                        Toast.makeText(context, "Invoice Berhasil Dilunasi!", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            // Size Matrix Table Title
            item {
                Text("RINCIAN UKURAN / MATRIX KUANTITAS", color = AgedGold, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }

            // Sizes Horizontal Matrix Table
            item {
                val gridShape = RoundedCornerShape(16.dp)
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(
                                    AgedGold.copy(alpha = 0.4f),
                                    Color.Transparent
                                )
                            ),
                            shape = gridShape
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                    colors = listOf(
                                        SurfaceDarkTeal.copy(alpha = 0.85f),
                                        SecondaryShadowBlackTeal.copy(alpha = 0.95f)
                                    )
                                )
                            )
                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Matrix Lengan Pendek (XS - 4XL)",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    val totalQty = parsedItems.sumOf { it.qty }
                                    Text(
                                        text = "Total: $totalQty Pcs",
                                        color = AgedGold,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 12.sp
                                    )
                                }
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    val sizes = listOf("XS", "S", "M", "L", "XL", "XXL", "3XL", "4XL")
                                    sizes.forEach { size ->
                                        val qty = parsedItems.filter { it.size.equals(size, ignoreCase = true) && !it.sleeve.contains("Panjang", ignoreCase = true) }.sumOf { it.qty }
                                        val cellBg = if (qty > 0) HighlightSoftCyan.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.02f)
                                        val cellBorderColor = if (qty > 0) HighlightSoftCyan.copy(alpha = 0.35f) else DividerDarkCyanGray.copy(alpha = 0.15f)
                                        val textColor = if (qty > 0) HighlightSoftCyan else Color.White.copy(alpha = 0.8f)
                                        
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(cellBg)
                                                .border(0.8.dp, cellBorderColor, RoundedCornerShape(8.dp))
                                                .padding(vertical = 6.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                text = size,
                                                color = AgedGold,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "$qty",
                                                color = textColor,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Black
                                            )
                                        }
                                    }
                                }

                                val hasPanjang = parsedItems.any { it.sleeve.contains("Panjang", ignoreCase = true) }
                                if (hasPanjang) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Matrix Lengan Panjang (XS - 4XL)",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        val sizes = listOf("XS", "S", "M", "L", "XL", "XXL", "3XL", "4XL")
                                        sizes.forEach { size ->
                                            val qty = parsedItems.filter { it.size.equals(size, ignoreCase = true) && it.sleeve.contains("Panjang", ignoreCase = true) }.sumOf { it.qty }
                                            val cellBg = if (qty > 0) HighlightSoftCyan.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.02f)
                                            val cellBorderColor = if (qty > 0) HighlightSoftCyan.copy(alpha = 0.35f) else DividerDarkCyanGray.copy(alpha = 0.15f)
                                            val textColor = if (qty > 0) HighlightSoftCyan else Color.White.copy(alpha = 0.8f)
                                            
                                            Column(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(cellBg)
                                                    .border(0.8.dp, cellBorderColor, RoundedCornerShape(8.dp))
                                                    .padding(vertical = 6.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Text(
                                                    text = size,
                                                    color = AgedGold,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "$qty",
                                                    color = textColor,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Black
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Document Itemisation block
            item {
                Text(
                    text = "RINCIAN BARANG / KREATIF JASA",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AgedGold,
                    letterSpacing = 1.sp
                )
            }

            items(parsedItems) { item ->
                SharedPremiumCard(
                    modifier = Modifier.fillMaxWidth(),
                    padding = 14.dp,
                    borderGlowColor = AgedGold.copy(alpha = 0.25f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(SurfaceDarkTeal)
                                    .border(0.8.dp, HighlightSoftCyan.copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CheckCircle,
                                    contentDescription = null,
                                    tint = HighlightSoftCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = item.seriesName,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(AgedGold.copy(alpha = 0.12f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "Lengan: ${item.sleeve}",
                                            color = AgedGold,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(HighlightSoftCyan.copy(alpha = 0.12f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "Size: ${item.size}",
                                            color = HighlightSoftCyan,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${item.qty} Pcs",
                                color = AgedGold,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }

            // 4. Hardware, PDF, PNG Export & WhatsApp Share Actions
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "EKSPOR & BAGIKAN DOKUMEN FAKTUR",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AgedGold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Featured Live Preview Toggle Button
                    Button(
                        onClick = { showDocumentPreviewDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("preview_doc_featured_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = SecondaryShadowBlackTeal),
                        border = androidx.compose.foundation.BorderStroke(1.2.dp, AgedGold),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Outlined.Visibility, contentDescription = null, tint = AgedGold, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pratinjau Dokumen (Live PDF / PNG)", color = AgedGold, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 0.5.sp)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Cetak Thermal
                        Button(
                            onClick = {
                                pairedPrinters = printerManager.getPairedPrinters()
                                showPrinterDialog = true
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("print_thermal_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = CardDarkCard),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AgedGold.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Outlined.Print, contentDescription = null, tint = AgedGold, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Struk Thermal", color = AgedGold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        // Unduh PDF
                        Button(
                            onClick = {
                                val operationalInvoice = OperationalInvoice(
                                    id = invoice.id.toString(),
                                    invoiceNumber = invoice.invoiceNumber,
                                    clientName = invoice.clientName,
                                    clientPhone = invoice.clientPhone,
                                    issueDate = invoice.issueDate,
                                    dueDate = invoice.dueDate,
                                    totalAmount = invoice.totalAmount,
                                    paidAmount = currentPaidAmount,
                                    discount = invoice.discount,
                                    dpAmount = invoice.dpAmount,
                                    itemsJson = invoice.itemsJson
                                )
                                val file = documentRenderer.generateInvoicePdf(operationalInvoice, invoiceItems)
                                if (file != null) {
                                    Toast.makeText(context, "Faktur PDF A4 berhasil diunduh ke Downloads!", Toast.LENGTH_LONG).show()
                                    viewModel.addAuditLog("Unduh PDF", "Faktur Invoice ${invoice.invoiceNumber} berhasil dirender sebagai PDF.")
                                } else {
                                    Toast.makeText(context, "Gagal merender PDF secara lokal!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("download_pdf_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = AgedGold, contentColor = ShadowBlack),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Outlined.PictureAsPdf, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Unduh PDF (A4)", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Simpan PNG (HD)
                        Button(
                            onClick = {
                                val operationalInvoice = OperationalInvoice(
                                    id = invoice.id.toString(),
                                    invoiceNumber = invoice.invoiceNumber,
                                    clientName = invoice.clientName,
                                    clientPhone = invoice.clientPhone,
                                    issueDate = invoice.issueDate,
                                    dueDate = invoice.dueDate,
                                    totalAmount = invoice.totalAmount,
                                    paidAmount = currentPaidAmount,
                                    discount = invoice.discount,
                                    dpAmount = invoice.dpAmount,
                                    itemsJson = invoice.itemsJson
                                )
                                val file = documentRenderer.generateInvoicePng(operationalInvoice, invoiceItems)
                                if (file != null) {
                                    Toast.makeText(context, "Gambar Invoice PNG berhasil disimpan di Galeri / Pictures!", Toast.LENGTH_LONG).show()
                                    viewModel.addAuditLog("Ekspor PNG", "Gambar Invoice ${invoice.invoiceNumber} disimpan ke Galeri.")
                                } else {
                                    Toast.makeText(context, "Gagal merender PNG secara lokal!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("export_png_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceDarkTeal),
                            border = androidx.compose.foundation.BorderStroke(1.dp, HighlightSoftCyan.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Outlined.Image, contentDescription = null, tint = HighlightSoftCyan, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Simpan PNG (HD)", color = HighlightSoftCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        // Kirim WhatsApp
                        Button(
                            onClick = {
                                val clientPhoneFormatted = invoice.clientPhone.replace("+", "").replace("-", "").replace(" ", "")
                                val waPhone = if (clientPhoneFormatted.startsWith("0")) "62" + clientPhoneFormatted.substring(1) else clientPhoneFormatted
                                val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale("id", "ID"))
                                val shareMsg = """
                                    *FAKTUR INVOICE OFFICIAL YANSPROJECT.ID*
                                    ----------------------------------------
                                    No. Invoice: ${invoice.invoiceNumber}
                                    Klien: ${invoice.clientName}
                                    Tanggal: ${sdf.format(java.util.Date(invoice.issueDate))}
                                    Jatuh Tempo: ${sdf.format(java.util.Date(invoice.dueDate))}

                                    *Total Tagihan:* ${FormatUtils.formatRupiah(invoice.totalAmount)}
                                    *Sudah Dibayar:* ${FormatUtils.formatRupiah(currentPaidAmount)}
                                    *Sisa Tagihan:* ${FormatUtils.formatRupiah(remainingBalance)}
                                    *Status:* ${if (isTenor3Paid) "LUNAS (PAID)" else if (currentPaidAmount > 0) "DIBAYAR SEBAGIAN" else "BELUM LUNAS"}

                                    Akad Jual-Beli (Ajib & Qobul) Sah, Halal & Terverifikasi Sistem ERP YANSPROJECT.ID.
                                    Terima kasih atas kerja samanya.
                                """.trimIndent()

                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                    data = android.net.Uri.parse("https://api.whatsapp.com/send?phone=$waPhone&text=${android.net.Uri.encode(shareMsg)}")
                                }
                                try {
                                    context.startActivity(intent)
                                    viewModel.addAuditLog("Kirim WhatsApp", "Invoice ${invoice.invoiceNumber} dibagikan ke WhatsApp $waPhone")
                                } catch (e: Exception) {
                                    val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(android.content.Intent.EXTRA_TEXT, shareMsg)
                                    }
                                    context.startActivity(android.content.Intent.createChooser(shareIntent, "Kirim Invoice via"))
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("share_whatsapp_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = AlertGreen, contentColor = Color.White),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Outlined.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Kirim WhatsApp", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    // Bluetooth Printer Selection Dialog
    if (showPrinterDialog) {
        Dialog(onDismissRequest = { showPrinterDialog = false }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                shape = RoundedCornerShape(16.dp),
                color = CardDarkCard,
                border = androidx.compose.foundation.BorderStroke(1.dp, AgedGold.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "PILIH PRINTER BLUETOOTH",
                        color = AgedGold,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    if (pairedPrinters.isEmpty()) {
                        Text(
                            text = "Tidak ada printer Bluetooth yang berpasangan (paired). Silakan pasangkan terlebih dahulu di pengaturan HP Anda.",
                            color = TextIsiSoftGray,
                            fontSize = 12.sp
                        )
                    } else {
                        pairedPrinters.forEach { device ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        val operationalInvoice = OperationalInvoice(
                                            id = invoice.id.toString(),
                                            invoiceNumber = invoice.invoiceNumber,
                                            clientName = invoice.clientName,
                                            clientPhone = invoice.clientPhone,
                                            issueDate = invoice.issueDate,
                                            dueDate = invoice.dueDate,
                                            totalAmount = invoice.totalAmount,
                                            paidAmount = currentPaidAmount,
                                            discount = invoice.discount,
                                            dpAmount = invoice.dpAmount,
                                            itemsJson = invoice.itemsJson
                                        )
                                        val success = printerManager.printReceipt(device, operationalInvoice, invoiceItems)
                                        if (success) {
                                            Toast.makeText(context, "Mencetak struk...", Toast.LENGTH_SHORT).show()
                                            viewModel.addAuditLog("Cetak Struk", "Invoice ${invoice.invoiceNumber} berhasil dicetak.")
                                        } else {
                                            Toast.makeText(context, "Gagal mencetak struk! Pastikan printer aktif.", Toast.LENGTH_LONG).show()
                                        }
                                        showPrinterDialog = false
                                    }
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(imageVector = Icons.Outlined.Bluetooth, contentDescription = null, tint = HighlightSoftCyan)
                                Column {
                                    Text(text = device.name ?: "Unknown Device", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(text = device.address, color = TextNonActive, fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    TextButton(
                        onClick = { showPrinterDialog = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Tutup", color = AgedGold)
                    }
                }
            }
        }
    }

    // Real-Time Live Document Preview Dialog
    if (showDocumentPreviewDialog) {
        val operationalInvoice = remember(invoice, currentPaidAmount) {
            OperationalInvoice(
                id = invoice.id.toString(),
                invoiceNumber = invoice.invoiceNumber,
                clientName = invoice.clientName,
                clientPhone = invoice.clientPhone,
                issueDate = invoice.issueDate,
                dueDate = invoice.dueDate,
                totalAmount = invoice.totalAmount,
                paidAmount = currentPaidAmount,
                discount = invoice.discount,
                dpAmount = invoice.dpAmount,
                itemsJson = invoice.itemsJson
            )
        }

        val previewBitmap = remember(operationalInvoice, invoiceItems) {
            documentRenderer.generateInvoicePngBitmap(operationalInvoice, invoiceItems)
        }

        Dialog(
            onDismissRequest = { showDocumentPreviewDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF0A0A0A),
                border = androidx.compose.foundation.BorderStroke(1.2.dp, AgedGold.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Header Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(AgedGold.copy(alpha = 0.15f))
                                    .border(0.8.dp, AgedGold.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Visibility,
                                    contentDescription = null,
                                    tint = AgedGold,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "PRATINJAU REAL-TIME DOKUMEN",
                                    fontWeight = FontWeight.Black,
                                    color = AgedGold,
                                    fontSize = 14.sp,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "YANSPROJECT.ID E-INVOICE RENDERING",
                                    fontSize = 9.sp,
                                    color = HighlightSoftCyan,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp
                                )
                            }
                        }

                        IconButton(onClick = { showDocumentPreviewDialog = false }) {
                            Icon(imageVector = Icons.Outlined.Close, contentDescription = "Tutup", tint = AgedGold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Scrollable Live Document Canvas View
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(CardDarkCard)
                            .border(1.dp, SurfaceDarkTeal, RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (previewBitmap != null) {
                            val scrollState = rememberScrollState()
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(scrollState),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Image(
                                    bitmap = previewBitmap.asImageBitmap(),
                                    contentDescription = "Pratinjau Faktur Invoice",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .wrapContentHeight()
                                        .padding(8.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                            }
                        } else {
                            CircularProgressIndicator(color = AgedGold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Action Bar inside preview
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val file = documentRenderer.generateInvoicePdf(operationalInvoice, invoiceItems)
                                if (file != null) {
                                    Toast.makeText(context, "Faktur PDF A4 berhasil diunduh ke Downloads!", Toast.LENGTH_LONG).show()
                                    viewModel.addAuditLog("Unduh PDF", "Faktur Invoice ${invoice.invoiceNumber} diunduh dari Pratinjau.")
                                } else {
                                    Toast.makeText(context, "Gagal merender PDF secara lokal!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AgedGold, contentColor = Color.Black),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                        ) {
                            Icon(imageVector = Icons.Outlined.PictureAsPdf, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Unduh PDF", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }

                        Button(
                            onClick = {
                                val file = documentRenderer.generateInvoicePng(operationalInvoice, invoiceItems)
                                if (file != null) {
                                    Toast.makeText(context, "Gambar PNG disimpan di Pictures!", Toast.LENGTH_LONG).show()
                                    viewModel.addAuditLog("Ekspor PNG", "Invoice ${invoice.invoiceNumber} disimpan ke Galeri dari Pratinjau.")
                                } else {
                                    Toast.makeText(context, "Gagal merender PNG secara lokal!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceDarkTeal, contentColor = HighlightSoftCyan),
                            border = androidx.compose.foundation.BorderStroke(1.dp, HighlightSoftCyan.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                        ) {
                            Icon(imageVector = Icons.Outlined.Image, contentDescription = null, tint = HighlightSoftCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Simpan PNG", color = HighlightSoftCyan, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StagedTenorCard(
    tenorNumber: Int,
    label: String,
    amount: Double,
    isPaid: Boolean,
    onPay: () -> Unit
) {
    SharedPremiumCard(
        modifier = Modifier.fillMaxWidth(),
        padding = 12.dp,
        borderGlowColor = if (isPaid) HighlightSoftCyan.copy(alpha = 0.2f) else AgedGold.copy(alpha = 0.2f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "TENOR #$tenorNumber",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isPaid) HighlightSoftCyan else AgedGold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = label,
                    color = TextWhite,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = FormatUtils.formatRupiah(amount),
                    color = TextIsiSoftGray,
                    fontSize = 12.sp
                )
            }

            if (isPaid) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(HighlightSoftCyan.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("LUNAS", color = HighlightSoftCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = onPay,
                    colors = ButtonDefaults.buttonColors(containerColor = AgedGold),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Bayar", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
