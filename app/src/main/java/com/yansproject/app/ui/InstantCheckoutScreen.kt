package com.yansproject.app.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yansproject.app.data.AjibqobulSeries
import com.yansproject.app.data.IdrAccountingEngine
import com.yansproject.app.data.SleeveType
import com.yansproject.app.data.UserTier
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstantCheckoutScreen(
    stockViewModel: AjibqobulStockViewModel = viewModel(),
    invoiceViewModel: DualInvoiceManagerViewModel = viewModel(),
    onCheckoutSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val stockState by stockViewModel.state.collectAsStateWithLifecycle()

    // Shopping Cart state
    var selectedTier by remember { mutableStateOf(UserTier.MEMBER) }
    val cartItems = remember { mutableStateListOf<CartItem>() }

    // Selected catalog configuration to add to cart
    var selectedSeries by remember { mutableStateOf(AjibqobulSeries.RAHASIA_REALITA) }
    var selectedColor by remember { mutableStateOf("HITAM") }
    var selectedSleeve by remember { mutableStateOf(SleeveType.PENDEK) }
    var selectedSize by remember { mutableStateOf(com.yansproject.app.data.ApparelSize.L) }
    var quantityInput by remember { mutableStateOf(1) }

    // Selected item matching properties
    val matchedStockItem = stockState.items.find {
        it.series == selectedSeries && it.color == selectedColor && it.sleeve == selectedSleeve && it.size == selectedSize
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "IN-STORE INSTANT CHECKOUT",
                        color = LuxuryGold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        letterSpacing = 1.sp
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepCarbonBlack)
            )
        },
        containerColor = DeepCarbonBlack
    ) { paddingValues ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Left Column: Catalog selection form
            Column(
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "PILIH PRODUK & TIER HARGA",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                // Select User Tier
                Card(
                    colors = CardDefaults.cardColors(containerColor = EmeraldSlateGreen),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Customer Tier", color = Color.Gray, fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            UserTier.values().forEach { tier ->
                                val active = selectedTier == tier
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (active) LuxuryGold else DeepCarbonBlack)
                                        .clickable { selectedTier = tier }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = tier.name,
                                        color = if (active) DeepCarbonBlack else Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // Select Product Series
                Card(
                    colors = CardDefaults.cardColors(containerColor = EmeraldSlateGreen),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Seri Ajibqobul", color = Color.Gray, fontSize = 11.sp)
                        
                        AjibqobulSeries.values().forEach { series ->
                            val active = selectedSeries == series
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (active) LuxuryGold.copy(alpha = 0.15f) else Color.Transparent)
                                    .clickable {
                                        selectedSeries = series
                                        selectedColor = series.allowedColors.first()
                                    }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = active,
                                    onClick = {
                                        selectedSeries = series
                                        selectedColor = series.allowedColors.first()
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = LuxuryGold)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = series.displayName,
                                    color = if (active) LuxuryGold else Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Size & Sleeve & Color selection
                Card(
                    colors = CardDefaults.cardColors(containerColor = EmeraldSlateGreen),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Sleeve selection
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Lengan", color = Color.Gray, fontSize = 11.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    SleeveType.values().forEach { sleeve ->
                                        val active = selectedSleeve == sleeve
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (active) LuxuryGold else DeepCarbonBlack)
                                                .clickable { selectedSleeve = sleeve }
                                                .padding(vertical = 6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                sleeve.name,
                                                color = if (active) DeepCarbonBlack else Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            // Color selection
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Warna", color = Color.Gray, fontSize = 11.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    selectedSeries.allowedColors.forEach { color ->
                                        val active = selectedColor == color
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (active) LuxuryGold else DeepCarbonBlack)
                                                .clickable { selectedColor = color }
                                                .padding(vertical = 6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                color,
                                                color = if (active) DeepCarbonBlack else Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Size selections
                        Text("Ukuran", color = Color.Gray, fontSize = 11.sp)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(com.yansproject.app.data.ApparelSize.M, com.yansproject.app.data.ApparelSize.L, com.yansproject.app.data.ApparelSize.XL, com.yansproject.app.data.ApparelSize.XXL).forEach { size ->
                                val active = selectedSize == size
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (active) LuxuryGold else DeepCarbonBlack)
                                        .clickable { selectedSize = size }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        size.name,
                                        color = if (active) DeepCarbonBlack else Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // Stock Indicator & Add button
                if (matchedStockItem != null) {
                    val priceBigDecimal = stockViewModel.getTierPrice(matchedStockItem, selectedTier)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = EmeraldSlateGreen),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (matchedStockItem.readyStock > 0) HijauMint else MerahCrimson)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Fisik: ${matchedStockItem.readyStock} Pcs",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = IdrAccountingEngine.formatRupiah(priceBigDecimal),
                                    color = LuxuryGold,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                IconButton(
                                    onClick = { if (quantityInput > 1) quantityInput-- },
                                    colors = IconButtonDefaults.iconButtonColors(containerColor = DeepCarbonBlack)
                                ) {
                                    Icon(imageVector = Icons.Default.Remove, contentDescription = null, tint = Color.White)
                                }
                                Text("$quantityInput", color = Color.White, fontWeight = FontWeight.Bold)
                                IconButton(
                                    onClick = { if (quantityInput < matchedStockItem.readyStock) quantityInput++ },
                                    colors = IconButtonDefaults.iconButtonColors(containerColor = DeepCarbonBlack)
                                ) {
                                    Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = Color.White)
                                }

                                Button(
                                    onClick = {
                                        if (matchedStockItem.readyStock <= 0) {
                                            Toast.makeText(context, "Stock habis!", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        val existing = cartItems.find {
                                            it.series == selectedSeries && it.color == selectedColor &&
                                            it.sleeve == selectedSleeve && it.size == selectedSize
                                        }
                                        if (existing != null) {
                                            val newQty = (existing.qty + quantityInput).coerceAtMost(matchedStockItem.readyStock)
                                            cartItems.remove(existing)
                                            cartItems.add(existing.copy(qty = newQty))
                                        } else {
                                            cartItems.add(
                                                CartItem(
                                                    series = selectedSeries,
                                                    color = selectedColor,
                                                    sleeve = selectedSleeve,
                                                    size = selectedSize,
                                                    qty = quantityInput,
                                                    unitPrice = priceBigDecimal
                                                )
                                            )
                                        }
                                        Toast.makeText(context, "Dimasukkan ke keranjang!", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = LuxuryGold),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Tambah", color = DeepCarbonBlack, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Right Column: Active Cart & Checkout Button
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "KERANJANG BELANJA INSTAN",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                // List of items in cart
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(EmeraldSlateGreen)
                        .border(1.dp, MutedSilver, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    if (cartItems.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(imageVector = Icons.Default.ShoppingCart, contentDescription = null, tint = MutedSilver, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Keranjang Kosong", color = Color.Gray, fontSize = 13.sp)
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(cartItems) { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${item.series.displayName} (${item.size.name})",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${item.sleeve.name} | ${item.color} | Qty: ${item.qty}",
                                            color = Color.Gray,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = IdrAccountingEngine.formatRupiah(item.unitPrice.multiply(BigDecimal.valueOf(item.qty.toLong()))),
                                            color = LuxuryGold,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        IconButton(onClick = { cartItems.remove(item) }) {
                                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Hapus", tint = MerahCrimson, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Summary and Instant checkout triggers
                val totalCartSum = cartItems.fold(BigDecimal.ZERO) { sum, item ->
                    sum.add(item.unitPrice.multiply(BigDecimal.valueOf(item.qty.toLong())))
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = EmeraldSlateGreen),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("TOTAL ORDER", color = Color.Gray, fontSize = 12.sp)
                            Text(
                                text = IdrAccountingEngine.formatRupiah(totalCartSum),
                                color = LuxuryGold,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = {
                                if (cartItems.isEmpty()) {
                                    Toast.makeText(context, "Keranjang belanja kosong!", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                // Decrease stock count in ViewModel
                                cartItems.forEach { item ->
                                    val match = stockState.items.find {
                                        it.series == item.series && it.color == item.color && it.sleeve == item.sleeve && it.size == item.size
                                    }
                                    if (match != null) {
                                        stockViewModel.updateStockQuantity(
                                            series = item.series,
                                            color = item.color,
                                            sleeve = item.sleeve,
                                            size = item.size,
                                            newReadyStock = (match.readyStock - item.qty).coerceAtLeast(0)
                                        )
                                    }
                                }

                                // Record Akad verification
                                stockViewModel.verifyAjibqobulAkad(
                                    seriesName = cartItems.first().series.displayName,
                                    clientName = "Tatap Muka ${selectedTier.name}",
                                    totalAmount = totalCartSum.toDouble()
                                )

                                Toast.makeText(context, "INSTANT CO BERHASIL! Qobul sah terverifikasi.", Toast.LENGTH_LONG).show()
                                cartItems.clear()
                                onCheckoutSuccess()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = LuxuryGold),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("instant_checkout_co_button")
                        ) {
                            Text(
                                text = "CO INSTAN TATAP MUKA",
                                color = DeepCarbonBlack,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

data class CartItem(
    val series: AjibqobulSeries,
    val color: String,
    val sleeve: SleeveType,
    val size: com.yansproject.app.data.ApparelSize,
    val qty: Int,
    val unitPrice: BigDecimal
)
