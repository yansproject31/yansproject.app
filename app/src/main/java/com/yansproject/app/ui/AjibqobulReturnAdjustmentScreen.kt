package com.yansproject.app.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yansproject.app.data.DamagedItemLog
import com.yansproject.app.data.IdrAccountingEngine
import com.yansproject.app.data.ReturnTransaction
import com.yansproject.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AjibqobulReturnAdjustmentScreen(
    onNavigateBack: () -> Unit,
    viewModel: StockManagerViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()

    // Form inputs state
    var showAdjustmentDialog by remember { mutableStateOf(false) }
    
    // Form fields
    var isAjibqobul by remember { mutableStateOf(true) }
    var selectedCatalogName by remember { mutableStateOf("RAHASIA REALITA") }
    var selectedVariantName by remember { mutableStateOf("HITAM") }
    var selectedSleeve by remember { mutableStateOf("Pendek") }
    var selectedSize by remember { mutableStateOf("M") }
    var returnedQuantityStr by remember { mutableStateOf("") }
    var selectedDestination by remember { mutableStateOf("Available Stock") }
    var selectedReason by remember { mutableStateOf("Cacat Jahitan") }
    var notes by remember { mutableStateOf("") }

    // Search/filter catalogs
    var catalogSearchQuery by remember { mutableStateOf("") }

    // List of predefined exclusive series
    val ajibqobulSeries = listOf(
        "RAHASIA REALITA",
        "HINA MULIA",
        "HILANG PULANG",
        "MADAD AULIYA 68TH"
    )

    val reasons = listOf(
        "Cacat Jahitan",
        "Sablon Rusak",
        "Sizing Missmatch",
        "Warna Kain Pudar",
        "Bahan Kain Cacat",
        "Salah Kirim (Retur)",
        "Lainnya"
    )

    LaunchedEffect(Unit) {
        viewModel.loadData()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "LOGISTICS SHIELD",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = AccentAgedGold,
                            letterSpacing = 1.5.sp
                        )
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali ke Dashboard",
                            tint = AccentAgedGold
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.loadData() },
                        modifier = Modifier.testTag("reload_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Muat Ulang Data",
                            tint = AccentAgedGold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SecondaryShadowBlackTeal
                )
            )
        },
        containerColor = BackgroundShadowBlack
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Banner (Luxury Fintech Theme Card)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceDarkTealSurface)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(SurfaceDarkTealSurface, SecondaryShadowBlackTeal)
                                )
                            )
                            .padding(20.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "NILAI PERSEDIAAN",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = TextNonActive,
                                            letterSpacing = 1.sp
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = IdrAccountingEngine.formatRupiah(state.totalInventoryValue),
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            color = AccentAgedGold
                                        )
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = "Logistics Protection Shield",
                                    tint = HighlightSoftCyan,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Divider(color = DividerDarkCyanGray, thickness = 1.dp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Aman • Terdesentralisasi • Proteksi Retur Maksimal",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = HighlightSoftCyan,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }
                }
            }

            // Quick Actions Block
            item {
                Button(
                    onClick = { showAdjustmentDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("add_return_adjustment_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryDarkTeal),
                    shape = RoundedCornerShape(12.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SettingsBackupRestore,
                        contentDescription = "Proses Retur",
                        tint = AccentAgedGold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "PROSES RETUR BARANG AJIBQOBUL",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = AccentAgedGold,
                            letterSpacing = 1.sp
                        )
                    )
                }
            }

            // Subtitle Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "KRONOLOGI RETUR & CACAT",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = AccentAgedGold,
                            letterSpacing = 1.sp
                        )
                    )
                    Text(
                        text = "Verifikasi Otomatis",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = HighlightSoftCyan
                        )
                    )
                }
            }

            // Loading indicator
            if (state.isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = HighlightSoftCyan)
                    }
                }
            } else if (state.returns.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = CardDarkCard)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Inventory,
                                contentDescription = "Tidak ada riwayat",
                                tint = TextNonActive,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Belum ada riwayat retur atau adjustment",
                                color = TextIsiSoftGray,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Gunakan tombol di atas untuk memproses retur logistik aman.",
                                color = TextNonActive,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Return items logs list
            items(state.returns) { ret ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardDarkCard),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = ret.seriesName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = AccentAgedGold
                            )
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = if (ret.destination == "Damaged Stock") StatusDangerRed.copy(alpha = 0.2f) else StatusSuccessCyan.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (ret.destination == "Damaged Stock") "BARANG RUSAK / CACAT" else "KEMBALI KE READY",
                                    color = if (ret.destination == "Damaged Stock") Color.Red else HighlightSoftCyan,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Varian: ${ret.varianName} | Lengan: ${ret.sleeve} | Size: ${ret.size}",
                                color = TextIsiSoftGray,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "${ret.returnedQuantity} Pcs",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Divider(color = DividerDarkCyanGray, thickness = 0.5.dp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Catatan: ${ret.notes}",
                                color = TextNonActive,
                                fontSize = 11.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = android.text.format.DateFormat.format("dd/MM/yyyy HH:mm", ret.timestamp).toString(),
                                color = TextNonActive,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }

    // Interactive Return Adjustment Dialog
    if (showAdjustmentDialog) {
        Dialog(
            onDismissRequest = { showAdjustmentDialog = false }
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .testTag("adjustment_dialog_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDarkTealSurface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "LOGISTICS SHIELD PROCESSOR",
                        fontWeight = FontWeight.Bold,
                        color = AccentAgedGold,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Divider(color = DividerDarkCyanGray)

                    // Exclusive Switch: Is Ajibqobul Item? (The master validation trigger)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Katalog Ajibqobul Series",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Aktifkan pengaman retur eksklusif",
                                color = TextNonActive,
                                fontSize = 10.sp
                            )
                        }
                        Switch(
                            checked = isAjibqobul,
                            onCheckedChange = { isAjibqobul = it },
                            modifier = Modifier.testTag("is_ajibqobul_switch"),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = HighlightSoftCyan,
                                checkedTrackColor = PrimaryDarkTeal
                            )
                        )
                    }

                    // RUNTIME UI VALIDATION GUARD ALERT
                    AnimatedVisibility(visible = !isAjibqobul) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = StatusDangerRed.copy(alpha = 0.2f))
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Validation Alert",
                                    tint = Color.Red,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = "GUARD ALERT: Item non-Ajibqobul (Custom Project / Outside) dilarang menggunakan fitur retur logistik ini secara hukum bisnis!",
                                    color = Color.Red,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Catalog Name Dropdown / Selector if isAjibqobul is true
                    if (isAjibqobul) {
                        Text("Pilih Katalog Seri:", color = TextNonActive, fontSize = 11.sp)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ajibqobulSeries.forEach { series ->
                                val isSelected = selectedCatalogName == series
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(
                                            color = if (isSelected) PrimaryDarkTeal else CardDarkCard,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) HighlightSoftCyan else DividerDarkCyanGray,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { selectedCatalogName = series }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = series.substringBefore(" "),
                                        color = if (isSelected) AccentAgedGold else TextIsiSoftGray,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    } else {
                        // Text Field to simulate typing Custom Project ID or Name (will cause rejection)
                        OutlinedTextField(
                            value = selectedCatalogName,
                            onValueChange = { selectedCatalogName = it },
                            label = { Text("Nama Custom Project / ID", color = TextNonActive) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color.Red,
                                unfocusedBorderColor = MutedSilver,
                                focusedContainerColor = EmeraldSlateGreen,
                                unfocusedContainerColor = EmeraldSlateGreen
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("custom_catalog_input")
                        )
                    }

                    // Varian Warna
                    OutlinedTextField(
                        value = selectedVariantName,
                        onValueChange = { selectedVariantName = it },
                        label = { Text("Varian Warna Kaos", color = TextNonActive) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = AccentAgedGold,
                            unfocusedBorderColor = MutedSilver,
                            focusedContainerColor = EmeraldSlateGreen,
                            unfocusedContainerColor = EmeraldSlateGreen
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("variant_input")
                    )

                    // Size and Sleeve selectors
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = selectedSize,
                            onValueChange = { selectedSize = it },
                            label = { Text("Size (M, L, XL, etc.)", color = TextNonActive) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = AccentAgedGold,
                                unfocusedBorderColor = MutedSilver,
                                focusedContainerColor = EmeraldSlateGreen,
                                unfocusedContainerColor = EmeraldSlateGreen
                            ),
                            modifier = Modifier.weight(1f).testTag("size_input")
                        )

                        OutlinedTextField(
                            value = selectedSleeve,
                            onValueChange = { selectedSleeve = it },
                            label = { Text("Lengan", color = TextNonActive) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = AccentAgedGold,
                                unfocusedBorderColor = MutedSilver,
                                focusedContainerColor = EmeraldSlateGreen,
                                unfocusedContainerColor = EmeraldSlateGreen
                            ),
                            modifier = Modifier.weight(1f).testTag("sleeve_input")
                        )
                    }

                    // Quantity input
                    OutlinedTextField(
                        value = returnedQuantityStr,
                        onValueChange = { returnedQuantityStr = it },
                        label = { Text("Kuantitas Retur (Pcs)", color = TextNonActive) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = AccentAgedGold,
                            unfocusedBorderColor = MutedSilver,
                            focusedContainerColor = EmeraldSlateGreen,
                            unfocusedContainerColor = EmeraldSlateGreen
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("qty_input")
                    )

                    // Allocation Destination
                    Text("Alokasi Stok Retur:", color = TextNonActive, fontSize = 11.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val destinations = listOf("Available Stock", "Damaged Stock")
                        destinations.forEach { dest ->
                            val isSel = selectedDestination == dest
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        color = if (isSel) PrimaryDarkTeal else CardDarkCard,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSel) HighlightSoftCyan else DividerDarkCyanGray,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { selectedDestination = dest }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (dest == "Available Stock") "KEMBALI READY" else "CACAT/RUSAK",
                                    color = if (isSel) HighlightSoftCyan else TextIsiSoftGray,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Alasan Reject / Damaged (Dialog Validasi Alasan Reject Produksi)
                    AnimatedVisibility(visible = selectedDestination == "Damaged Stock") {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Alasan Cacat Produksi:", color = TextNonActive, fontSize = 11.sp)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val miniReasons = listOf("Jahitan", "Sablon", "Bahan")
                                miniReasons.forEach { r ->
                                    val isSel = selectedReason.contains(r)
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(
                                                color = if (isSel) StatusDangerRed.copy(alpha = 0.2f) else CardDarkCard,
                                                shape = RoundedCornerShape(6.dp)
                                            )
                                            .clickable { selectedReason = "Cacat $r" }
                                            .padding(vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = r,
                                            color = if (isSel) Color.Red else TextIsiSoftGray,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Notes
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Keterangan Tambahan", color = TextNonActive) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = AccentAgedGold,
                            unfocusedBorderColor = MutedSilver,
                            focusedContainerColor = EmeraldSlateGreen,
                            unfocusedContainerColor = EmeraldSlateGreen
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("notes_input")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Buttons Area
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showAdjustmentDialog = false },
                            modifier = Modifier.weight(1f).testTag("cancel_adjustment_btn"),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Text("BATAL")
                        }

                        Button(
                            onClick = {
                                val qtyVal = returnedQuantityStr.toIntOrNull() ?: 0
                                if (qtyVal <= 0) {
                                    Toast.makeText(context, "Jumlah nominal retur harus valid!", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }

                                viewModel.processInventoryAdjustment(
                                    catalogId = if (isAjibqobul) ajibqobulSeries.indexOf(selectedCatalogName) + 1 else 999,
                                    isAjibqobul = isAjibqobul,
                                    catalogName = selectedCatalogName,
                                    variantId = 101 + (selectedCatalogName.hashCode() % 10).coerceAtLeast(0), // Simulating stable variant ID mappings
                                    variantName = selectedVariantName,
                                    sleeve = selectedSleeve,
                                    size = selectedSize,
                                    returnedQuantity = qtyVal,
                                    destination = selectedDestination,
                                    notes = notes,
                                    reason = if (selectedDestination == "Damaged Stock") selectedReason else "Penyesuaian Retur",
                                    context = context
                                ) { success, message ->
                                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                    if (success) {
                                        showAdjustmentDialog = false
                                        // Reset fields
                                        returnedQuantityStr = ""
                                        notes = ""
                                    }
                                }
                            },
                            // Button is enabled, but the business logic validation guard inside ViewModel will completely reject and block database mutation if isAjibqobul is false
                            modifier = Modifier.weight(1.5f).testTag("submit_adjustment_btn"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isAjibqobul) HighlightSoftCyan else Color.DarkGray,
                                contentColor = SecondaryShadowBlackTeal
                            )
                        ) {
                            Text("PROSES RETUR", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
