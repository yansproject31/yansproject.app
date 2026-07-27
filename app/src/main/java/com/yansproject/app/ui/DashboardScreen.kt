package com.yansproject.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yansproject.app.data.*
import com.yansproject.app.ui.theme.*
import com.yansproject.app.ui.navigation.Routes
import com.yansproject.app.ui.settings.MemberViewModel
import java.text.SimpleDateFormat
import java.util.*
import android.widget.Toast
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.yansproject.app.R
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.CubicBezierEasing

private val LuxuryMotionEasing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

// Data class representasi aktivitas log di Dashboard
data class DashboardActivity(
    val title: String,
    val description: String,
    val date: Long,
    val type: String, // "Invoice", "Project", "Pemesanan", "StockMasuk", "StockKeluar", "Pemasukan", "Pengeluaran"
    val amount: Double? = null,
    val category: String? = null
)

@Composable
fun HeroCardSaldoKasUtama(
    saldoKas: Double,
    totalPemasukan: Double,
    totalPengeluaran: Double,
    isLoading: Boolean = false,
    onWalletClick: () -> Unit,
    onPemasukanClick: () -> Unit,
    onPengeluaranClick: () -> Unit
) {
    val cardShape = RoundedCornerShape(24.dp)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 16.dp,
                shape = cardShape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.8f),
                spotColor = PrimaryDarkTeal.copy(alpha = 0.4f)
            )
            .clip(cardShape),
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(
            width = 1.2.dp,
            brush = Brush.verticalGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    PrimaryDarkTeal.copy(alpha = 0.2f)
                )
            )
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.98f)
                        )
                    )
                )
        ) {
            Column {
                // Top section of Hero Card
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, top = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "SALDO KAS UTAMA",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = AgedGold,
                                letterSpacing = 2.sp
                            )
                            if (isLoading) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(AgedGold.copy(alpha = 0.15f))
                                        .border(0.8.dp, AgedGold.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "MEMUAT DATA...",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AgedGold
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (isLoading) "Memuat data keuangan..." else "Kas Riil Tersedia",
                            fontSize = 13.sp,
                            color = TextSecondary.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    // Wallet Shortcut Button: rounded-square (digital wallet style)
                    Card(
                        modifier = Modifier
                            .size(44.dp)
                            .shadow(
                                elevation = 6.dp,
                                shape = RoundedCornerShape(12.dp),
                                clip = false,
                                ambientColor = Color.Black.copy(alpha = 0.3f),
                                spotColor = AgedGold.copy(alpha = 0.2f)
                            )
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onWalletClick),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = CardDarkCard.copy(alpha = 0.8f)
                        ),
                        border = BorderStroke(1.2.dp, AgedGold.copy(alpha = 0.6f))
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AccountBalanceWallet,
                                contentDescription = "Global Financial Center",
                                tint = AgedGold,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                
                // Balance Text with Shimmer Loading & Empty Data Differentiation
                Spacer(modifier = Modifier.height(18.dp))
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 24.dp)
                            .width(220.dp)
                            .height(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .shimmerEffect()
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        Text(
                            text = FormatUtils.formatRupiah(saldoKas),
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Black,
                            color = AgedGold,
                            letterSpacing = (-1.5).sp
                        )
                        if (saldoKas == 0.0) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .border(0.8.dp, YansDivider.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "Kas Kosong",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = YansTextSecondary
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                
                // Full horizontal divider separating top & bottom parts
                HorizontalDivider(
                    color = DividerDarkCyanGray.copy(alpha = 0.35f), 
                    thickness = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Bottom Interactive Areas
                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Total Pemasukan area (Indikator Cyan)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClick = onPemasukanClick)
                            .padding(vertical = 16.dp, horizontal = 20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(HighlightSoftCyan)
                            )
                            Column {
                                Text(
                                    text = "Total Pemasukan",
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                if (isLoading) {
                                    Box(
                                        modifier = Modifier
                                            .width(90.dp)
                                            .height(16.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .shimmerEffect()
                                    )
                                } else {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = FormatUtils.formatRupiah(totalPemasukan),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = HighlightSoftCyan
                                        )
                                        if (totalPemasukan == 0.0) {
                                            Text(
                                                text = "(Nihil)",
                                                fontSize = 9.sp,
                                                color = TextSecondary.copy(alpha = 0.6f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    // Vertical dividing line
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(54.dp)
                            .background(DividerDarkCyanGray.copy(alpha = 0.35f))
                            .align(Alignment.CenterVertically)
                    )
                    
                    // Total Pengeluaran area (Indikator Deep Red)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClick = onPengeluaranClick)
                            .padding(vertical = 16.dp, horizontal = 20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(ErrorRed)
                            )
                            Column {
                                Text(
                                    text = "Total Pengeluaran",
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                if (isLoading) {
                                    Box(
                                        modifier = Modifier
                                            .width(90.dp)
                                            .height(16.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .shimmerEffect()
                                    )
                                } else {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = FormatUtils.formatRupiah(totalPengeluaran),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = ErrorRed
                                        )
                                        if (totalPengeluaran == 0.0) {
                                            Text(
                                                text = "(Nihil)",
                                                fontSize = 9.sp,
                                                color = TextSecondary.copy(alpha = 0.6f)
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
    }
}

@Composable
fun HeroCardMember(
    totalStockPieces: Int,
    projectAktifCount: Int,
    invoiceBelumLunasCount: Int,
    onCatalogClick: () -> Unit,
    onOrderClick: () -> Unit
) {
    val cardShape = RoundedCornerShape(24.dp)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 16.dp,
                shape = cardShape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.8f),
                spotColor = PrimaryDarkTeal.copy(alpha = 0.4f)
            )
            .clip(cardShape),
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(
            width = 1.2.dp,
            brush = Brush.verticalGradient(
                colors = listOf(
                    AgedGold.copy(alpha = 0.5f),
                    PrimaryDarkTeal.copy(alpha = 0.2f)
                )
            )
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            SurfaceDarkTeal.copy(alpha = 0.92f),
                            SecondaryShadowBlackTeal.copy(alpha = 0.98f)
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "YANSPROJECT.ID MEMBER CENTER",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = AgedGold,
                            letterSpacing = 2.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Katalog & Operasional Kemitraan",
                            fontSize = 13.sp,
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(AgedGold.copy(alpha = 0.15f))
                            .border(1.dp, AgedGold, RoundedCornerShape(20.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "AKUN MEMBER",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = AgedGold
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onCatalogClick,
                        colors = ButtonDefaults.buttonColors(containerColor = AgedGold, contentColor = ShadowBlack),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.Checklist, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Katalog Produk", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onOrderClick,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = HighlightSoftCyan),
                        border = BorderStroke(1.dp, HighlightSoftCyan),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.ShoppingCart, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Order Saya", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassmorphicGridCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    iconColor: Color,
    isLoading: Boolean = false,
    isEmpty: Boolean = false,
    onClick: () -> Unit
) {
    val cardShape = RoundedCornerShape(20.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "press_scale"
    )

    Card(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .height(118.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (isPressed) 4.dp else 10.dp,
                shape = cardShape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.6f),
                spotColor = iconColor.copy(alpha = 0.35f)
            ),
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.primary.copy(alpha = if (isPressed) 0.6f else 0.4f),
                    YansDivider.copy(alpha = 0.15f)
                )
            )
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.98f)
                        ),
                        radius = 400f,
                        center = Offset(200f, 200f)
                    )
                )
                .padding(14.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = YansTextSecondary,
                        letterSpacing = 0.8.sp,
                        maxLines = 1
                    )
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(iconColor.copy(alpha = 0.12f))
                            .border(0.5.dp, iconColor.copy(alpha = 0.25f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoading) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .shimmerEffect()
                            )
                        } else {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = iconColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                Column {
                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .width(85.dp)
                                .height(18.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .shimmerEffect()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .width(60.dp)
                                .height(10.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .shimmerEffect()
                        )
                    } else {
                        val isZeroOrEmpty = isEmpty || value == "Rp 0" || value == "0 Pcs" || value == "0 Invoice" || value == "0 Project" || value == "0 Proyek" || value == "0 Member" || value == "0 Mitra"
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = value,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                color = if (isZeroOrEmpty) Color.White.copy(alpha = 0.75f) else Color.White,
                                maxLines = 1
                            )
                            if (isZeroOrEmpty) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .border(0.5.dp, YansDivider.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "NIHIL",
                                        fontSize = 7.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = YansTextSecondary
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            fontSize = 9.sp,
                            color = YansTextSecondary.copy(alpha = 0.8f),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

data class GridCardData(
    val title: String,
    val value: String,
    val subtitle: String,
    val icon: ImageVector,
    val iconColor: Color,
    val isEmpty: Boolean = false
)

@Composable
fun GridOperasionalOwner(
    modalAwal: Double,
    modalBerjalan: Double,
    saldoKas: Double,
    totalProfit: Double,
    totalPenjualan: Double,
    totalSalesQuantity: Int = 0,
    totalSalesTxCount: Int = 0,
    totalGrossProfit: Double = 0.0,
    totalPengeluaran: Double,
    nilaiTotalStock: Double,
    totalStockPieces: Int,
    invoiceBelumLunasAmount: Double,
    invoiceBelumLunasCount: Int,
    projectAktifCount: Int,
    totalMembersCount: Int,
    isLoading: Boolean = false,
    onCardClick: (String) -> Unit
) {
    val primaryAccent = MaterialTheme.colorScheme.primary
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "RINGKASAN OPERASIONAL & KINERJA",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = primaryAccent,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(top = 8.dp)
        )

        // 12 Cards arranged in 6 Rows of 2 Cards each
        val rowData = listOf(
            listOf(
                GridCardData("MODAL AWAL", FormatUtils.formatRupiah(modalAwal), if (modalAwal == 0.0) "Belum ada modal awal" else "Investasi kas awal", Icons.Outlined.AccountBalanceWallet, primaryAccent, isEmpty = (modalAwal == 0.0)),
                GridCardData("MODAL BERJALAN", FormatUtils.formatRupiah(modalBerjalan), if (modalBerjalan == 0.0) "Belum ada transaksi modal" else "Estimasi modal bergulir", Icons.Outlined.TrendingUp, HighlightSoftCyan, isEmpty = (modalBerjalan == 0.0))
            ),
            listOf(
                GridCardData("KAS AKTIF", FormatUtils.formatRupiah(saldoKas), if (saldoKas == 0.0) "Kas riil kosong" else "Sisa dana kas riil", Icons.Outlined.AccountBalance, primaryAccent, isEmpty = (saldoKas == 0.0)),
                GridCardData("PROFIT BERSIH", FormatUtils.formatRupiah(totalProfit), if (totalProfit == 0.0) "Laba nihil periode ini" else "Laba bersih setelah HPP", Icons.Outlined.MonetizationOn, HighlightSoftCyan, isEmpty = (totalProfit == 0.0))
            ),
            listOf(
                GridCardData(
                    "TOTAL PENJUALAN",
                    FormatUtils.formatRupiah(totalPenjualan),
                    if (totalPenjualan == 0.0) "Belum ada penjualan" else "$totalSalesQuantity Pcs • $totalSalesTxCount Tx • Profit ${FormatUtils.formatRupiah(totalGrossProfit)}",
                    Icons.Outlined.Leaderboard,
                    HighlightSoftCyan,
                    isEmpty = (totalPenjualan == 0.0)
                ),
                GridCardData("TOTAL PENGELUARAN", FormatUtils.formatRupiah(totalPengeluaran), if (totalPengeluaran == 0.0) "Belum ada pengeluaran" else "Biaya operasional & HPP", Icons.Outlined.TrendingDown, ErrorRed, isEmpty = (totalPengeluaran == 0.0))
            ),
            listOf(
                GridCardData("NILAI PERSEDIAAN", FormatUtils.formatRupiah(nilaiTotalStock), if (nilaiTotalStock == 0.0) "Stok gudang kosong" else "Aset stock gudang", Icons.Outlined.Inventory, primaryAccent, isEmpty = (nilaiTotalStock == 0.0)),
                GridCardData("STOK AJIBQOBUL", "$totalStockPieces Pcs", if (totalStockPieces == 0) "Belum ada stok fisik" else "Total unit kaos fisik", Icons.Outlined.Inventory2, primaryAccent, isEmpty = (totalStockPieces == 0))
            ),
            listOf(
                GridCardData("PIUTANG DAGANG", FormatUtils.formatRupiah(invoiceBelumLunasAmount), if (invoiceBelumLunasAmount == 0.0) "Tidak ada piutang outstanding" else "Sisa tagihan outstanding", Icons.Outlined.AssignmentLate, StatusWarningGold, isEmpty = (invoiceBelumLunasAmount == 0.0)),
                GridCardData("INVOICE UNPAID", "$invoiceBelumLunasCount Invoice", if (invoiceBelumLunasCount == 0) "Semua invoice telah lunas" else "Penagihan belum lunas", Icons.Outlined.ErrorOutline, StatusWarningGold, isEmpty = (invoiceBelumLunasCount == 0))
            ),
            listOf(
                GridCardData("PROJECT AKTIF", "$projectAktifCount Project", if (projectAktifCount == 0) "Tidak ada proyek aktif" else "Proyek sedang diproduksi", Icons.Outlined.Assignment, HighlightSoftCyan, isEmpty = (projectAktifCount == 0)),
                GridCardData("TOTAL MEMBER", "$totalMembersCount Mitra", if (totalMembersCount == 0) "Belum ada mitra terdaftar" else "Jumlah akun member aktif", Icons.Outlined.People, primaryAccent, isEmpty = (totalMembersCount == 0))
            )
        )

        rowData.forEach { pairOfCards ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                pairOfCards.forEach { card ->
                    Box(modifier = Modifier.weight(1f)) {
                        GlassmorphicGridCard(
                            title = card.title,
                            value = card.value,
                            subtitle = card.subtitle,
                            icon = card.icon,
                            iconColor = card.iconColor,
                            isLoading = isLoading,
                            isEmpty = card.isEmpty,
                            onClick = { onCardClick(card.title) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GridOperasionalMember(
    totalStockPieces: Int,
    lowStockSize: Int,
    projectAktifCount: Int,
    invoiceBelumLunasCount: Int,
    isLoading: Boolean = false,
    onCardClick: (String) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "DASHBOARD STOK & PESANAN MEMBER",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = AgedGold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(top = 8.dp)
        )

        val rowData = listOf(
            listOf(
                GridCardData("STOK AJIBQOBUL", "$totalStockPieces Pcs", if (totalStockPieces == 0) "Belum ada stok fisik" else "Total unit kaos fisik di gudang", Icons.Outlined.Inventory2, AgedGold, isEmpty = (totalStockPieces == 0)),
                GridCardData("VARIAN AKTIF", if (lowStockSize > 0) "$lowStockSize Varian Menipis" else "Semua Varian Aman", "Status ketersediaan varian", Icons.Outlined.Category, HighlightSoftCyan, isEmpty = false)
            ),
            listOf(
                GridCardData("PROJECT AKTIF", "$projectAktifCount Proyek", if (projectAktifCount == 0) "Tidak ada proyek aktif" else "Proyek pesanan berjalan", Icons.Outlined.Assignment, HighlightSoftCyan, isEmpty = (projectAktifCount == 0)),
                GridCardData("INVOICE UNPAID", "$invoiceBelumLunasCount Invoice", if (invoiceBelumLunasCount == 0) "Semua invoice telah lunas" else "Penagihan belum lunas", Icons.Outlined.ErrorOutline, StatusWarningGold, isEmpty = (invoiceBelumLunasCount == 0))
            )
        )

        rowData.forEach { pairOfCards ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                pairOfCards.forEach { card ->
                    Box(modifier = Modifier.weight(1f)) {
                        GlassmorphicGridCard(
                            title = card.title,
                            value = card.value,
                            subtitle = card.subtitle,
                            icon = card.icon,
                            iconColor = card.iconColor,
                            isLoading = isLoading,
                            isEmpty = card.isEmpty,
                            onClick = { onCardClick(card.title) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    navController: androidx.navigation.NavHostController? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val currentUser by FirebaseSyncManager.currentUser.collectAsState()
    val userRole = currentUser?.role ?: UserRole.MEMBER

    androidx.compose.runtime.DisposableEffect(viewModel) {
        FirebaseSyncManager.startActiveDashboardListener(context) {
            // Real-time snapshot synchronized to database
        }
        onDispose {
            FirebaseSyncManager.stopActiveDashboardListener()
        }
    }

    val allAuditLogs by viewModel.allAuditLogs.collectAsState()

    var activeLedgerPage by remember { mutableStateOf<String?>(null) } // "pemasukan", "pengeluaran", "kas", "profit", "piutang", "produksi", "laporan"
    var selectedInvoiceForDetail by remember { mutableStateOf<Invoice?>(null) }

    // CSV Import Launchers
    val importStockLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent(),
        onResult = { uri ->
            if (uri != null) {
                DataImportExportHelper.importStockFromCsv(context, uri, viewModel) { count ->
                    if (count > 0) {
                        Toast.makeText(context, "Berhasil mengimpor $count data stok!", Toast.LENGTH_LONG).show()
                        viewModel.addAuditLog("Import Stock", "Berhasil mengimpor $count data stok via CSV.")
                    } else {
                        Toast.makeText(context, "Gagal mengimpor data stok!", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    )

    val importCatalogLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent(),
        onResult = { uri ->
            if (uri != null) {
                DataImportExportHelper.importCatalogFromCsv(context, uri, viewModel) { count ->
                    if (count > 0) {
                        Toast.makeText(context, "Berhasil mengimpor $count katalog baru!", Toast.LENGTH_LONG).show()
                        viewModel.addAuditLog("Import Catalog", "Berhasil mengimpor $count data katalog via CSV.")
                    } else {
                        Toast.makeText(context, "Gagal mengimpor data katalog!", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    )

    val importCustomerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent(),
        onResult = { uri ->
            if (uri != null) {
                DataImportExportHelper.importCustomerFromCsv(context, uri, viewModel) { count ->
                    if (count > 0) {
                        Toast.makeText(context, "Berhasil mengimpor $count customer baru!", Toast.LENGTH_LONG).show()
                        viewModel.addAuditLog("Import Customer", "Berhasil mengimpor $count data pelanggan via CSV.")
                    } else {
                        Toast.makeText(context, "Gagal mengimpor data pelanggan!", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    )

    // DB Restore Launcher
    val restoreLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                val success = DatabaseBackupHelper.restoreDatabase(context, uri)
                if (success) {
                    AppFeedbackManager.triggerSuccess()
                    Toast.makeText(context, "Database berhasil dipulihkan! Silakan restart aplikasi.", Toast.LENGTH_LONG).show()
                    viewModel.addAuditLog("Pemulihan Database", "Database dipulihkan dari file eksternal.")
                    viewModel.triggerNotification("Restore Berhasil", "Sistem berhasil memulihkan database dari file cadangan.", "Sistem", "SETTINGS")
                } else {
                    AppFeedbackManager.triggerError()
                    Toast.makeText(context, "Gagal memulihkan database!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    )

    if (userRole != com.yansproject.app.data.UserRole.OWNER && activeLedgerPage in listOf("pemasukan", "modal_awal", "modal_berjalan", "pengeluaran", "kas", "profit", "piutang")) {
        activeLedgerPage = null
        Toast.makeText(context, "AKSES DITOLAK: Halaman rincian keuangan kas hanya untuk OWNER", Toast.LENGTH_SHORT).show()
    }

    if (activeLedgerPage == "pemasukan") {
        RiwayatPemasukanScreen(viewModel = viewModel, onBack = { activeLedgerPage = null })
        return
    } else if (activeLedgerPage == "modal_awal") {
        RiwayatModalAwalScreen(viewModel = viewModel, onBack = { activeLedgerPage = null })
        return
    } else if (activeLedgerPage == "modal_berjalan") {
        RiwayatModalBerjalanScreen(viewModel = viewModel, onBack = { activeLedgerPage = null })
        return
    } else if (activeLedgerPage == "pengeluaran") {
        RiwayatPengeluaranScreen(viewModel = viewModel, onBack = { activeLedgerPage = null })
        return
    } else if (activeLedgerPage == "kas") {
        RiwayatKasScreen(viewModel = viewModel, onBack = { activeLedgerPage = null })
        return
    } else if (activeLedgerPage == "profit") {
        DetailProfitScreen(viewModel = viewModel, onBack = { activeLedgerPage = null })
        return
    } else if (activeLedgerPage == "penjualan") {
        RiwayatPenjualanUnifiedScreen(viewModel = viewModel, onBack = { activeLedgerPage = null })
        return
    } else if (activeLedgerPage == "piutang") {
        RiwayatPiutangScreen(
            viewModel = viewModel,
            onBack = { activeLedgerPage = null },
            onNavigateToInvoice = { _ ->
                viewModel.setTab(AppTab.INVOICE)
                activeLedgerPage = null
            }
        )
        return
    } else if (activeLedgerPage == "produksi") {
        RiwayatProduksiScreen(
            viewModel = viewModel,
            onBack = { activeLedgerPage = null },
            onNavigateToProject = {
                viewModel.setTab(AppTab.PROJECT)
                activeLedgerPage = null
            }
        )
        return
    } else if (activeLedgerPage == "laporan") {
        RiwayatLaporanScreen(
            viewModel = viewModel,
            onBack = { activeLedgerPage = null }
        )
        return
    }



    // Koleksi aliran data secara reaktif dari ViewModel
    val invoices by viewModel.allInvoices.collectAsState()
    val projects by viewModel.allProjects.collectAsState()
    val stockItems by viewModel.allStock.collectAsState()
    val orders by viewModel.allOrders.collectAsState()
    val expenses by viewModel.allExpenses.collectAsState()
    val inflows by viewModel.allInflows.collectAsState()
    val inventorySummaries by viewModel.allInventorySummary.collectAsState()
    val allPayments by viewModel.allInvoicePayments.collectAsState()

    // Status Filter Aktif: "Hari Ini", "7 Hari", "30 Hari", "Bulan Ini", "Semua"
    var selectedFilter by remember { mutableStateOf("Semua") }

    // Dialog state untuk pencatatan Pengeluaran Baru
    var showAddExpenseDialog by remember { mutableStateOf(false) }
    var showAddInflowDialog by remember { mutableStateOf(false) }
    var showLowStockDialog by remember { mutableStateOf(false) }



    // Jam & Tanggal Real-Time Ticking Clock (Berdetik setiap detik)
    var currentTimeMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTimeMillis = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000)
        }
    }

    // Format Jam dan Tanggal Bahasa Indonesia (Menggunakan Locale.forLanguageTag agar tidak deprecated)
    val clockString = remember(currentTimeMillis) {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.forLanguageTag("id-ID"))
        sdf.format(Date(currentTimeMillis))
    }
    val dateString = remember(currentTimeMillis) {
        val sdf = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.forLanguageTag("id-ID"))
        sdf.format(Date(currentTimeMillis))
    }

    // Fungsi utilitas lokal untuk menyaring rentang tanggal transaksi
    fun isTimestampInFilter(timestamp: Long, filter: String): Boolean {
        val now = System.currentTimeMillis()
        val calendarNow = Calendar.getInstance().apply { timeInMillis = now }
        val calendarTarget = Calendar.getInstance().apply { timeInMillis = timestamp }

        return when (filter) {
            "Hari Ini" -> {
                calendarNow.get(Calendar.YEAR) == calendarTarget.get(Calendar.YEAR) &&
                        calendarNow.get(Calendar.DAY_OF_YEAR) == calendarTarget.get(Calendar.DAY_OF_YEAR)
            }
            "7 Hari" -> {
                val diffMs = now - timestamp
                diffMs in 0..(7L * 24 * 60 * 60 * 1000)
            }
            "30 Hari" -> {
                val diffMs = now - timestamp
                diffMs in 0..(30L * 24 * 60 * 60 * 1000)
            }
            "Bulan Ini" -> {
                calendarNow.get(Calendar.YEAR) == calendarTarget.get(Calendar.YEAR) &&
                        calendarNow.get(Calendar.MONTH) == calendarTarget.get(Calendar.MONTH)
            }
            else -> true // "Semua"
        }
    }

    // --- KALKULASI REAL-TIME (Sesuai Filter & Flow Database Rill) ---

    // 1. Modal Awal (All-time Inflows kategori "Modal")
    val modalAwal = remember(inflows) {
        inflows.filter { !it.isDeleted && it.category.contains("Modal", ignoreCase = true) }.sumOf { it.amount }
    }

    // 2. Transaksi Terfilter Sesuai Rentang Waktu (Abaikan invoice & data dibatalkan/dihapus)
    val filteredInvoices = remember(invoices, selectedFilter) {
        invoices.filter {
            !it.isDeleted &&
            isTimestampInFilter(it.issueDate, selectedFilter) &&
            !it.status.equals("Dibatalkan", ignoreCase = true) &&
            !it.status.equals("Cancelled", ignoreCase = true)
        }
    }
    val filteredInflows = remember(inflows, selectedFilter) {
        inflows.filter { !it.isDeleted && isTimestampInFilter(it.date, selectedFilter) }
    }
    val filteredExpenses = remember(expenses, selectedFilter) {
        expenses.filter { !it.isDeleted && isTimestampInFilter(it.date, selectedFilter) }
    }
    val filteredOrders = remember(orders, selectedFilter) {
        orders.filter { !it.isDeleted && isTimestampInFilter(it.orderDate, selectedFilter) }
    }

    // Order POS yang belum/tidak memiliki Invoice
    val filteredStandaloneOrders = remember(filteredOrders, invoices) {
        filteredOrders.filter { ord ->
            invoices.none { inv -> inv.orderId == ord.id }
        }
    }

    // 3. Total Penjualan Terfilter (Omset Operasional Bruto: Paid Invoices + Standalone Orders + Non-Invoice Sales Inflows)
    val salesSummary = remember(filteredInvoices, filteredStandaloneOrders, filteredInflows, allPayments) {
        calculateUnifiedSalesSummary(filteredInvoices, filteredStandaloneOrders, filteredInflows, allPayments)
    }
    val totalPenjualan = salesSummary.totalRevenue

    // Total Pemasukan Terfilter (Seluruh Uang Masuk Kas: Paid Invoices + Standalone Orders + Direct Inflows)
    val totalPemasukan = remember(filteredInvoices, filteredInflows, filteredStandaloneOrders, allPayments) {
        val invoicePaid = filteredInvoices.sumOf { calculateInvoicePaid(it, allPayments) }
        val orderPaid = filteredStandaloneOrders.sumOf { getEffectiveOrderPaid(it) }
        val nonInvoiceInflows = filteredInflows.filter { 
            !it.category.contains("Pembayaran Customer", ignoreCase = true) &&
            !it.notes.contains("[PAY_") &&
            !it.notes.contains("Pembayaran Invoice")
        }.sumOf { it.amount }
        invoicePaid + orderPaid + nonInvoiceInflows
    }

    // 4. Total Pengeluaran Terfilter (Pengeluaran Operasional)
    val totalPengeluaran = remember(filteredExpenses) {
        filteredExpenses.sumOf { it.amount }
    }

    // 5. Total Profit Terfilter (Net Operational Profit = Total Penjualan - Total Pengeluaran)
    val totalProfit = totalPenjualan - totalPengeluaran

    // 6. Kas Aktif & Modal Berjalan All-Time
    val allTimeInvoicesPaid = remember(invoices, allPayments) {
        invoices.filter {
            !it.isDeleted &&
            !it.status.equals("Dibatalkan", ignoreCase = true) &&
            !it.status.equals("BATAL", ignoreCase = true) &&
            !it.status.equals("Cancelled", ignoreCase = true)
        }.sumOf { calculateInvoicePaid(it, allPayments) }
    }
    val allTimeStandaloneOrdersPaid = remember(orders, invoices) {
        orders.filter { ord ->
            !ord.isDeleted && invoices.none { inv -> inv.orderId == ord.id }
        }.sumOf { getEffectiveOrderPaid(it) }
    }
    val allTimeInflowsAmount = remember(inflows) { 
        inflows.filter { 
            !it.isDeleted && 
            !it.category.contains("Pembayaran Customer", ignoreCase = true) &&
            !it.notes.contains("[PAY_") &&
            !it.notes.contains("Pembayaran Invoice")
        }.sumOf { it.amount } 
    }
    val allTimeExpensesAmount = remember(expenses) { expenses.filter { !it.isDeleted }.sumOf { it.amount } }

    // Saldo Kas Aktif = Total Uang Masuk All-Time - Total Pengeluaran All-Time
    val saldoKas = (allTimeInvoicesPaid + allTimeStandaloneOrdersPaid + allTimeInflowsAmount - allTimeExpensesAmount).coerceAtLeast(0.0)

    // Modal Berjalan = Modal Awal + Laba Bersih Operasional Akumulatif
    val allTimeNonModalInflows = remember(inflows) {
        inflows.filter { 
            !it.isDeleted && 
            !it.category.contains("Modal", ignoreCase = true) &&
            !it.category.contains("Pembayaran Customer", ignoreCase = true) &&
            !it.notes.contains("[PAY_") &&
            !it.notes.contains("Pembayaran Invoice")
        }.sumOf { it.amount }
    }
    val allTimeNetProfit = (allTimeInvoicesPaid + allTimeStandaloneOrdersPaid + allTimeNonModalInflows) - allTimeExpensesAmount
    val modalBerjalan = modalAwal + allTimeNetProfit

    // 7. Stok AJIBQOBUL & Nilai Persediaan Gudang (Data Rill Inventory Summary / Stock Items)
    val totalStockPieces = remember(inventorySummaries, stockItems) {
        val summarySum = inventorySummaries.sumOf { it.availableStock }
        if (summarySum > 0 || inventorySummaries.isNotEmpty()) {
            summarySum
        } else {
            stockItems.filter { !it.isDeleted }.sumOf { it.stockCount }
        }
    }
    val nilaiTotalStock = remember(inventorySummaries, stockItems) {
        val summaryNilai = inventorySummaries.sumOf { it.nilaiPersediaan }
        if (summaryNilai > 0 || inventorySummaries.isNotEmpty()) {
            summaryNilai
        } else {
            stockItems.filter { !it.isDeleted }.sumOf { (it.stockCount * it.costPrice) }
        }
    }

    // 8. Piutang Dagang & Invoice Unpaid (Hanya invoice aktif yang belum lunas)
    val unpaidInvoices = remember(invoices, allPayments) {
        invoices.filter { inv ->
            calculateInvoiceSisaPiutang(inv, allPayments) > 0.01
        }
    }
    val invoiceBelumLunasCount = unpaidInvoices.size
    val invoiceBelumLunasAmount = remember(unpaidInvoices, allPayments) {
        unpaidInvoices.sumOf { inv -> calculateInvoiceSisaPiutang(inv, allPayments) }
    }

    // 9. Project Aktif (Jumlah proyek berjalan)
    val projectAktifCount = remember(projects) {
        projects.count { proj ->
            val st = proj.status.trim().lowercase()
            st != "completed" && st != "selesai" && st != "dibatalkan" && st != "cancelled"
        }
    }

    // 10. Total Member Mitra (Real-time & Sync Tanpa Owner)
    val memberViewModel: MemberViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    LaunchedEffect(Unit) {
        memberViewModel.loadMembers(context)
    }
    val membersList by memberViewModel.members.collectAsState()
    val totalMembersCount = remember(membersList) {
        membersList.size
    }

    // --- TIMELINE AKTIVITAS TERBARU DUA ARAH ---
    val activities = remember(invoices, projects, stockItems, orders, expenses, selectedFilter) {
        val list = mutableListOf<DashboardActivity>()

        // A. Project Baru
        projects.forEach { proj ->
            if (isTimestampInFilter(proj.startDate, selectedFilter)) {
                list.add(
                    DashboardActivity(
                        title = "Project Baru",
                        description = "${proj.projectName} - Klien: ${proj.clientName}",
                        date = proj.startDate,
                        type = "Project",
                        amount = proj.totalCost
                    )
                )
            }
        }

        // B. Invoice Baru
        invoices.forEach { inv ->
            if (isTimestampInFilter(inv.issueDate, selectedFilter)) {
                list.add(
                    DashboardActivity(
                        title = "Invoice Baru",
                        description = "No: ${inv.invoiceNumber} - Klien: ${inv.clientName}",
                        date = inv.issueDate,
                        type = "Invoice",
                        amount = inv.totalAmount
                    )
                )
            }
        }

        // C. Penjualan AJIBQOBUL
        orders.forEach { ord ->
            if (isTimestampInFilter(ord.orderDate, selectedFilter)) {
                list.add(
                    DashboardActivity(
                        title = "Penjualan AJIBQOBUL",
                        description = "Transaksi penjualan ke ${ord.clientName}",
                        date = ord.orderDate,
                        type = "Pemesanan",
                        amount = ord.totalAmount
                    )
                )
            }
        }

        // D. Stock Masuk Rill
        stockItems.filter { it.stockCount > 0 }.take(5).forEach { item ->
            val updateDate = if (item.lastUpdated > 0) item.lastUpdated else System.currentTimeMillis()
            if (isTimestampInFilter(updateDate, selectedFilter)) {
                list.add(
                    DashboardActivity(
                        title = "Stock Gudang",
                        description = "Item: ${item.name}",
                        date = updateDate,
                        type = "StockMasuk",
                        amount = item.stockCount.toDouble()
                    )
                )
            }
        }

        // E. Stock Keluar (Diambil dari transaksi pemesanan / penjualan real-time)
        orders.forEach { ord ->
            if (isTimestampInFilter(ord.orderDate, selectedFilter)) {
                list.add(
                    DashboardActivity(
                        title = "Stock Keluar",
                        description = "Pengiriman barang ke ${ord.clientName}",
                        date = ord.orderDate + 1000, // sedikit di-offset
                        type = "StockKeluar",
                        amount = ord.totalAmount
                    )
                )
            }
        }

        // F. Pemasukan (Diambil dari invoice yang sudah di-bayar / LUNAS / DP)
        invoices.forEach { inv ->
            val paid = calculateInvoicePaid(inv, allPayments)
            if (paid > 0 && isTimestampInFilter(inv.issueDate, selectedFilter)) {
                list.add(
                    DashboardActivity(
                        title = "Pemasukan",
                        description = "Pembayaran Invoice No: ${inv.invoiceNumber}",
                        date = inv.issueDate + 2000,
                        type = "Pemasukan",
                        amount = paid
                    )
                )
            }
        }

        // Pemasukan POS / Order Standalone
        orders.forEach { ord ->
            val paid = getEffectiveOrderPaid(ord)
            val isNotCoveredByInvoice = invoices.none { inv -> inv.orderId == ord.id }
            if (paid > 0 && isNotCoveredByInvoice && isTimestampInFilter(ord.orderDate, selectedFilter)) {
                list.add(
                    DashboardActivity(
                        title = "Pemasukan POS",
                        description = "Penjualan Order #${ord.id} - Klien: ${ord.clientName}",
                        date = ord.orderDate + 1500,
                        type = "Pemasukan",
                        amount = paid
                    )
                )
            }
        }

        // G. Pengeluaran (Diambil dari database pengeluaran riil)
        expenses.forEach { exp ->
            if (isTimestampInFilter(exp.date, selectedFilter)) {
                list.add(
                    DashboardActivity(
                        title = "Pengeluaran",
                        description = "[${exp.category}] ${exp.notes}",
                        date = exp.date,
                        type = "Pengeluaran",
                        amount = exp.amount,
                        category = exp.category
                    )
                )
            }
        }

        // H. Pemasukan Manual
        inflows.forEach { inf ->
            if (isTimestampInFilter(inf.date, selectedFilter)) {
                list.add(
                    DashboardActivity(
                        title = "Pemasukan",
                        description = "[${inf.category}] ${inf.notes}",
                        date = inf.date,
                        type = "Pemasukan",
                        amount = inf.amount,
                        category = inf.category
                    )
                )
            }
        }

        // Urutkan berdasarkan waktu terbaru di atas
        list.sortByDescending { it.date }
        list.distinctBy { it.description + it.title + it.date }.take(15)
    }

    val lowStockItems = stockItems.filter { it.stockCount <= 5 }
    val criticalItems = remember(lowStockItems) {
        lowStockItems.sortedBy { it.stockCount }.take(3)
    }

    val syncManager = remember { com.yansproject.app.data.YansSyncManager.getInstance(context) }
    val isSyncingState by syncManager.isSyncing.collectAsState()
    val syncStatusState by syncManager.syncStatus.collectAsState()

    val infiniteTransition = rememberInfiniteTransition(label = "sync_rotation")
    val rotationAngle by if (isSyncingState) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "rotation"
        )
    } else {
        remember { mutableStateOf(0f) }
    }

    PullToRefreshBox(
        isRefreshing = isSyncingState,
        onRefresh = {
            viewModel.refreshData(context) { success, error ->
                if (success) {
                    viewModel.showGlobalSnackbar("Data berhasil diperbarui.")
                } else {
                    viewModel.showGlobalSnackbar("Sinkronisasi gagal: $error")
                }
            }
        },
        modifier = modifier
    ) {
        if (isSyncingState && invoices.isEmpty() && projects.isEmpty() && stockItems.isEmpty()) {
            DashboardSkeleton()
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                if (userRole.canAccessFinancials()) {
                    val startOfToday = remember(currentTimeMillis) {
                        val cal = Calendar.getInstance()
                        cal.set(Calendar.HOUR_OF_DAY, 0)
                        cal.set(Calendar.MINUTE, 0)
                        cal.set(Calendar.SECOND, 0)
                        cal.set(Calendar.MILLISECOND, 0)
                        cal.timeInMillis
                    }
                    val endOfToday = remember(currentTimeMillis) {
                        val cal = Calendar.getInstance()
                        cal.set(Calendar.HOUR_OF_DAY, 23)
                        cal.set(Calendar.MINUTE, 59)
                        cal.set(Calendar.SECOND, 59)
                        cal.set(Calendar.MILLISECOND, 999)
                        cal.timeInMillis
                    }

                    val alertItems = remember(stockItems, invoices, allAuditLogs) {
                        val alerts = mutableListOf<AlertData>()
                        
                        // 1. Invoice Jatuh Tempo Alert (STRICTLY REMOVED from main dashboard view to eliminate cognitive overload)

                        // 2. Backup Belum Dilakukan Alert (> 3 Hari)
                        val lastBackupLog = allAuditLogs.find { it.activity == "Pencadangan Database" }
                        val backupNeeded = lastBackupLog == null || (System.currentTimeMillis() - lastBackupLog.timestamp > 3L * 24 * 60 * 60 * 1000)
                        if (backupNeeded) {
                            val daysStr = if (lastBackupLog == null) "Belum pernah" else {
                                val diffMs = System.currentTimeMillis() - lastBackupLog.timestamp
                                "${diffMs / (24 * 60 * 60 * 1000)} hari yang lalu"
                            }
                            alerts.add(
                                AlertData(
                                    title = "Rekomendasi Backup Data",
                                    description = "Pencadangan database terakhir dilakukan: $daysStr. Disarankan melakukan backup secara berkala untuk mengamankan data lokal Anda ke Cloud.",
                                    icon = Icons.Outlined.CloudUpload,
                                    color = HighlightSoftCyan,
                                    actionText = "Backup Sekarang",
                                    onClick = {
                                        val backupFile = DatabaseBackupHelper.backupDatabase(context)
                                        if (backupFile != null) {
                                            Toast.makeText(context, "Database berhasil dicadangkan lokal: ${backupFile.name}", Toast.LENGTH_LONG).show()
                                            viewModel.addAuditLog("Pencadangan Database", "Database berhasil diekspor ke file: ${backupFile.name}")
                                            FirebaseSyncManager.uploadBackupToCloud(context, backupFile) { ok, msg ->
                                                if (ok) {
                                                    Toast.makeText(context, "Backup berhasil diunggah ke Cloud Firebase!", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        } else {
                                            Toast.makeText(context, "Gagal melakukan pencadangan database!", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            )
                        }

                        alerts
                    }

                    // Pre-compute today's metrics for Sprint 7C
                    val orderHariIni = remember(invoices, startOfToday, endOfToday) { invoices.count { !it.isDeleted && it.issueDate in startOfToday..endOfToday } }
                    val invoiceBelumBayar = remember(invoices) { invoices.count { !it.isDeleted && it.status == "BELUM LUNAS" } }
                    val invoiceSebagian = remember(invoices) { invoices.count { !it.isDeleted && it.status == "DP" } }
                    val invoiceLunas = remember(invoices) { invoices.count { !it.isDeleted && it.status == "LUNAS" } }
                    val totalPenjualanHariIni = remember(invoices, startOfToday, endOfToday) { invoices.filter { !it.isDeleted && it.issueDate in startOfToday..endOfToday }.sumOf { it.totalAmount } }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
            // --- 1. GREETING WITH SYNC STATUS ---
            item {
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (userRole == UserRole.OWNER) "Halo, Yans" else "Halo, Dulurs",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Jangan Lupa Bersholawat",
                            fontSize = 14.sp,
                            color = AgedGold,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.5.sp
                        )
                    }
                    
                    // Cloud Sync Status Indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(CardDarkCard)
                            .border(1.dp, DividerDarkCyanGray.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CloudSync,
                            contentDescription = "Cloud Sync",
                            tint = HighlightSoftCyan,
                            modifier = Modifier
                                .size(14.dp)
                                .graphicsLayer { rotationZ = rotationAngle }
                        )
                        Text(
                            text = syncStatusState,
                            fontSize = 9.sp,
                            color = TextIsiSoftGray,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 11.sp
                        )
                    }
                }
            }

            // --- 2. UNLIMITED SHOLAWAT SLIDE BANNER ---
            item {
                SholawatMarqueeBanner()
            }

            // --- 3. FILTER TIME PERIOD BAR ---
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val periods = listOf("Hari Ini", "7 Hari", "30 Hari", "Bulan Ini", "Semua")
                    periods.forEach { period ->
                        val isSelected = selectedFilter == period
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) AgedGold else DarkGrey)
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) AgedGold else BorderGrey,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { selectedFilter = period }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .testTag("filter_chip_$period")
                        ) {
                            Text(
                                text = period,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) ShadowBlack else TextLight
                            )
                        }
                    }
                }
            }

            // --- 4. HERO SECTION (Atas) ---
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (userRole == com.yansproject.app.data.UserRole.OWNER) {
                        HeroCardSaldoKasUtama(
                            saldoKas = saldoKas,
                            totalPemasukan = totalPemasukan,
                            totalPengeluaran = totalPengeluaran,
                            isLoading = isSyncingState,
                            onWalletClick = {
                                if (navController != null) {
                                    navController.navigate(Routes.GlobalLedger)
                                }
                            },
                            onPemasukanClick = { activeLedgerPage = "pemasukan" },
                            onPengeluaranClick = { activeLedgerPage = "pengeluaran" }
                        )
                    } else {
                        HeroCardMember(
                            totalStockPieces = totalStockPieces,
                            projectAktifCount = projectAktifCount,
                            invoiceBelumLunasCount = invoiceBelumLunasCount,
                            onCatalogClick = { viewModel.setTab(AppTab.STOCK) },
                            onOrderClick = { viewModel.setTab(AppTab.INVOICE) }
                        )
                    }

                    val lowStockSize = stockItems.count { !it.isDeleted && it.stockCount <= 5 }
                    if (lowStockSize > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(StatusDangerRed.copy(alpha = 0.15f))
                                .border(1.dp, StatusDangerRed.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .clickable { viewModel.setTab(AppTab.STOCK) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Warning,
                                contentDescription = null,
                                tint = StatusDangerRed,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Peringatan Stok Menipis: Ada $lowStockSize varian warna <= 5 Pcs (Kelola)",
                                color = StatusDangerRed,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // --- 6. BUSINESS ALERTS ---
            if (alertItems.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "BUSINESS ALERTS & SYSTEM STATUS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AgedGold,
                            letterSpacing = 1.sp
                        )
                        alertItems.forEach { alert ->
                            BusinessAlertCard(alert = alert)
                        }
                    }
                }
            }

            // --- 7. OPERATIONAL SUMMARY (Bawah) ---
            item {
                if (userRole == com.yansproject.app.data.UserRole.OWNER) {
                    GridOperasionalOwner(
                        modalAwal = modalAwal,
                        modalBerjalan = modalBerjalan,
                        saldoKas = saldoKas,
                        totalProfit = totalProfit,
                        totalPenjualan = totalPenjualan,
                        totalSalesQuantity = salesSummary.totalQuantityPcs,
                        totalSalesTxCount = salesSummary.totalTransactionCount,
                        totalGrossProfit = salesSummary.totalGrossProfit,
                        totalPengeluaran = totalPengeluaran,
                        nilaiTotalStock = nilaiTotalStock,
                        totalStockPieces = totalStockPieces,
                        invoiceBelumLunasAmount = invoiceBelumLunasAmount,
                        invoiceBelumLunasCount = invoiceBelumLunasCount,
                        projectAktifCount = projectAktifCount,
                        totalMembersCount = totalMembersCount,
                        isLoading = isSyncingState,
                        onCardClick = { cardTitle ->
                            when (cardTitle) {
                                "MODAL AWAL" -> activeLedgerPage = "modal_awal"
                                "MODAL BERJALAN" -> activeLedgerPage = "modal_berjalan"
                                "KAS AKTIF" -> activeLedgerPage = "kas"
                                "PROFIT BERSIH" -> activeLedgerPage = "profit"
                                "TOTAL PENJUALAN" -> activeLedgerPage = "penjualan"
                                "TOTAL PENGELUARAN" -> activeLedgerPage = "pengeluaran"
                                "NILAI PERSEDIAAN" -> viewModel.setTab(AppTab.STOCK)
                                "STOK AJIBQOBUL" -> viewModel.setTab(AppTab.STOCK)
                                "PIUTANG DAGANG" -> activeLedgerPage = "piutang"
                                "INVOICE UNPAID" -> {
                                    viewModel.invoiceStatusFilter.value = "Belum Dibayar"
                                    viewModel.setTab(AppTab.INVOICE)
                                }
                                "PROJECT AKTIF" -> viewModel.setTab(AppTab.PROJECT)
                                "TOTAL MEMBER" -> {
                                    navController?.safeNavigate("settings_member") ?: run {
                                        Toast.makeText(context, "Buka menu Pengaturan untuk mengelola member", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    )
                } else {
                    val lowStockSize = stockItems.count { !it.isDeleted && it.stockCount <= 5 }
                    GridOperasionalMember(
                        totalStockPieces = totalStockPieces,
                        lowStockSize = lowStockSize,
                        projectAktifCount = projectAktifCount,
                        invoiceBelumLunasCount = invoiceBelumLunasCount,
                        isLoading = isSyncingState,
                        onCardClick = { cardTitle ->
                            when (cardTitle) {
                                "STOK AJIBQOBUL", "VARIAN AKTIF" -> viewModel.setTab(AppTab.STOCK)
                                "PROJECT AKTIF" -> viewModel.setTab(AppTab.PROJECT)
                                "INVOICE UNPAID" -> {
                                    viewModel.invoiceStatusFilter.value = "Belum Dibayar"
                                    viewModel.setTab(AppTab.INVOICE)
                                }
                            }
                        }
                    )
                }
            }

            // Export PDF Premium Card under the 12 Grid Cards (OWNER ONLY)
            if (userRole == com.yansproject.app.data.UserRole.OWNER) {
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    SharedPremiumCard(
                        modifier = Modifier.fillMaxWidth(),
                        padding = 16.dp,
                        borderGlowColor = AgedGold.copy(alpha = 0.4f),
                        onClick = {
                            DocumentExporter.exportFinancialSummaryToPdf(
                                context = context,
                                period = selectedFilter,
                                totalRevenue = totalPenjualan,
                                totalReceivables = invoiceBelumLunasAmount,
                                activeProjectsCount = projectAktifCount,
                                lowStockCount = stockItems.count { !it.isDeleted && it.stockCount <= 5 },
                                totalOrdersCount = orders.count { it.status == "Completed" },
                                unpaidInvoices = unpaidInvoices,
                                activeProjects = projects,
                                viewModel = viewModel
                            )
                        }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(AgedGold.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.PictureAsPdf,
                                        contentDescription = "Ekspor PDF",
                                        tint = AgedGold,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "EKSPOR LAPORAN KEUANGAN & OPERASIONAL",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White,
                                        letterSpacing = 0.5.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Unduh rangkuman PDF lengkap untuk periode $selectedFilter",
                                        fontSize = 10.sp,
                                        color = TextSecondary
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Outlined.Download,
                                contentDescription = "Unduh",
                                tint = AgedGold,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // --- 8. RINGKASAN PERSENTASE KEUANGAN (OWNER ONLY) ---
                item {
                    DashboardRingkasanKeuanganCard(
                        totalPemasukan = totalPemasukan,
                        totalPengeluaran = totalPengeluaran,
                        filteredInflows = filteredInflows,
                        filteredInvoices = filteredInvoices,
                        filteredExpenses = filteredExpenses,
                        filteredStandaloneOrders = filteredStandaloneOrders,
                        allPayments = allPayments
                    )
                }
            }

            // --- 9. AKTIVITAS TERBARU ---
            item {
                Text(
                    text = "Aktivitas Terbaru (${selectedFilter})",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (activities.isEmpty()) {
                item {
                    EmptyStateView(
                        icon = Icons.Outlined.Timeline,
                        title = "Tidak Ada Aktivitas",
                        description = "Semua riwayat keuangan dan proyek operasional terfilter akan tampil di sini secara real-time saat transaksi mulai dicatat."
                    )
                }
            } else {
                items(activities) { activity ->
                    ActivityRow(activity = activity)
                }
            }

            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
        } else if (userRole == UserRole.STAFF) {
            StaffDashboardView(
                currentUser = currentUser,
                clockString = clockString,
                dateString = dateString,
                criticalItems = criticalItems,
                viewModel = viewModel
            )
        } else {
            ClientPortalDashboardView(
                currentUser = currentUser,
                userRole = userRole,
                clockString = clockString,
                dateString = dateString,
                invoices = invoices,
                viewModel = viewModel,
                onInvoiceClick = { selectedInvoiceForDetail = it }
            )
        }
    }
}
}

    if (selectedInvoiceForDetail != null) {
        DetailRiwayatBottomSheet(
            invoice = selectedInvoiceForDetail!!,
            onDismiss = { selectedInvoiceForDetail = null },
            onNavigateToInvoice = {
                viewModel.setTab(AppTab.INVOICE)
                selectedInvoiceForDetail = null
            },
            viewModel = viewModel
        )
    }

    // --- FORM DIALOG TAMBAH PENGELUARAN (ALERT DIALOG MODEREN) ---
    if (showAddExpenseDialog) {
        var selectedCategory by remember { mutableStateOf("Operasional") }
        var nominalStr by remember { mutableStateOf("") }
        var dateSelected by remember { mutableStateOf(System.currentTimeMillis()) }
        var notesStr by remember { mutableStateOf("") }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var showDatePickerDialog by remember { mutableStateOf(false) }

        val formattedDate = remember(dateSelected) {
            val sdf = SimpleDateFormat("dd MMMM yyyy", Locale.forLanguageTag("id-ID"))
            sdf.format(Date(dateSelected))
        }

        AlertDialog(
            onDismissRequest = { showAddExpenseDialog = false },
            containerColor = DarkGrey,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.border(1.dp, AgedGold.copy(alpha = 0.5f), RoundedCornerShape(20.dp)),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.TrendingDown,
                        contentDescription = null,
                        tint = AlertRed,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Catat Pengeluaran Baru",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Pilihan Kategori (Chips)
                    Column {
                        Text(
                            text = "Kategori Pengeluaran",
                            fontSize = 12.sp,
                            color = TextMuted,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        val categories = listOf("Produksi", "Aksesories", "Transport", "Operasional", "Lainnya")
                        
                        // Menampilkan chips dalam dua baris horizontal agar pas dan responsif
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            categories.take(3).forEach { cat ->
                                val isCatSelected = selectedCategory == cat
                                FilterChip(
                                    selected = isCatSelected,
                                    onClick = { selectedCategory = cat },
                                    label = { Text(cat, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AgedGold,
                                        selectedLabelColor = ShadowBlack,
                                        containerColor = CardGrey,
                                        labelColor = TextLight
                                    ),
                                    modifier = Modifier.testTag("chip_cat_$cat")
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            categories.drop(3).forEach { cat ->
                                val isCatSelected = selectedCategory == cat
                                FilterChip(
                                    selected = isCatSelected,
                                    onClick = { selectedCategory = cat },
                                    label = { Text(cat, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AgedGold,
                                        selectedLabelColor = ShadowBlack,
                                        containerColor = CardGrey,
                                        labelColor = TextLight
                                    ),
                                    modifier = Modifier.testTag("chip_cat_$cat")
                                )
                            }
                        }
                        if (selectedCategory == "Aksesories" || selectedCategory == "Aksesoris") {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Kategori Aksesories mencakup Packing, Ziplock, Hang Tag, Label, Sticker, dll.",
                                fontSize = 10.sp,
                                color = TextMuted
                            )
                        }
                    }

                    // Input Nominal
                    OutlinedTextField(
                        value = nominalStr,
                        onValueChange = { input ->
                            // Hanya perbolehkan angka
                            if (input.all { it.isDigit() }) {
                                nominalStr = input
                            }
                        },
                        label = { Text("Nominal Pengeluaran (Rp)", color = TextMuted) },
                        placeholder = { Text("Contoh: 150000", color = TextMuted.copy(alpha = 0.5f)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AgedGold,
                            unfocusedBorderColor = BorderGrey,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_nominal_expense")
                    )

                    // Pilihan Tanggal
                    Column {
                        Text(
                            text = "Tanggal Transaksi",
                            fontSize = 12.sp,
                            color = TextMuted,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(CardGrey)
                                .border(1.dp, BorderGrey, RoundedCornerShape(8.dp))
                                .clickable { showDatePickerDialog = true }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.CalendarMonth,
                                    contentDescription = null,
                                    tint = AgedGold,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = formattedDate,
                                    fontSize = 13.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Text(
                                text = "UBAH",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = AgedGold
                            )
                        }
                    }

                    // Input Catatan
                    OutlinedTextField(
                        value = notesStr,
                        onValueChange = { notesStr = it },
                        label = { Text("Catatan / Keterangan", color = TextMuted) },
                        placeholder = { Text("Ketik detail pengeluaran...", color = TextMuted.copy(alpha = 0.5f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AgedGold,
                            unfocusedBorderColor = BorderGrey,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_notes_expense")
                    )

                    // Error Message
                    errorMessage?.let { error ->
                        Text(
                            text = error,
                            fontSize = 11.sp,
                            color = AlertRed,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amountVal = nominalStr.toDoubleOrNull()
                        if (amountVal == null || amountVal <= 0.0) {
                            errorMessage = "Nominal harus berupa angka lebih besar dari 0!"
                        } else if (notesStr.trim().isEmpty()) {
                            errorMessage = "Catatan tidak boleh kosong!"
                        } else {
                            // Masukkan ke ViewModel reaktif
                            viewModel.addExpense(
                                category = selectedCategory,
                                amount = amountVal,
                                date = dateSelected,
                                notes = notesStr.trim()
                            )
                            showAddExpenseDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AgedGold),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("btn_simpan_expense")
                ) {
                    Text("Simpan", color = ShadowBlack, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAddExpenseDialog = false }
                ) {
                    Text("Batal", color = TextMuted)
                }
            }
        )

        // Date Picker Dialog M3
        if (showDatePickerDialog) {
            val datePickerState = rememberDatePickerState(initialSelectedDateMillis = dateSelected)
            DatePickerDialog(
                onDismissRequest = { showDatePickerDialog = false },
                confirmButton = {
                    TextButton(onClick = {
                        datePickerState.selectedDateMillis?.let {
                            dateSelected = it
                        }
                        showDatePickerDialog = false
                    }) {
                        Text("Pilih", color = AgedGold, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePickerDialog = false }) {
                        Text("Batal", color = TextMuted)
                    }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }
    }

    if (showAddInflowDialog) {
        AddInflowDialog(
            onDismiss = { showAddInflowDialog = false },
            onSave = { category, amount, date, notes, photoUrl ->
                viewModel.addInflow(category, amount, date, notes, photoUrl)
                showAddInflowDialog = false
            }
        )
    }

    if (showLowStockDialog) {
        LowStockDetailsDialog(
            lowStockItems = lowStockItems,
            onViewAllStock = {
                viewModel.setTab(AppTab.STOCK)
                showLowStockDialog = false
            },
            onDismiss = { showLowStockDialog = false }
        )
    }
}

@Composable
fun DashboardMetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardGrey),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderGrey),
        modifier = if (onClick != null) modifier.clickable { onClick() } else modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(color.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(16.dp)
                )
            }
            Column {
                Text(
                    text = title,
                    fontSize = 9.sp,
                    color = TextMuted,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = value,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
fun ActivityRow(activity: DashboardActivity) {
    val (icon, color) = when (activity.type) {
        "Invoice" -> Icons.AutoMirrored.Outlined.ReceiptLong to AlertOrange
        "Pemasukan" -> Icons.Outlined.TrendingUp to AlertGreen
        "Project" -> Icons.Outlined.Assignment to AlertBlue
        "Pemesanan" -> Icons.Outlined.ShoppingCart to AgedGold
        "StockMasuk" -> Icons.Outlined.AddCircleOutline to AlertGreen
        "StockKeluar" -> Icons.Outlined.RemoveCircleOutline to AlertOrange
        "Pengeluaran" -> Icons.Outlined.TrendingDown to AlertRed
        else -> Icons.Outlined.SyncAlt to AgedGold
    }

    val itemShape = RoundedCornerShape(16.dp)
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = itemShape,
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(
                    AgedGold.copy(alpha = 0.2f),
                    Color.Transparent
                )
            )
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .shadow(
                elevation = 4.dp,
                shape = itemShape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.3f),
                spotColor = color.copy(alpha = 0.15f)
            )
            .clip(itemShape)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            SurfaceDarkTeal.copy(alpha = 0.85f),
                            SecondaryShadowBlackTeal.copy(alpha = 0.95f)
                        )
                    )
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(color.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = activity.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = activity.description,
                        fontSize = 11.sp,
                        color = TextSecondary.copy(alpha = 0.7f)
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    if (activity.amount != null) {
                        if (activity.type == "StockMasuk") {
                            Text(
                                text = "+${activity.amount.toInt()} Pcs",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = AlertGreen
                            )
                        } else if (activity.type == "StockKeluar") {
                            Text(
                                text = "Keluar Pcs",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = AlertOrange
                            )
                        } else {
                            val isNegative = activity.type == "Pengeluaran"
                            Text(
                                text = (if (isNegative) "-" else "") + FormatUtils.formatRupiah(activity.amount),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = when (activity.type) {
                                    "Pemasukan" -> AlertGreen
                                    "Pengeluaran" -> AlertRed
                                    else -> Color.White
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = FormatUtils.formatDate(activity.date),
                        fontSize = 9.sp,
                        color = TextSecondary.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
fun StaffDashboardView(
    currentUser: UserSession?,
    clockString: String,
    dateString: String,
    criticalItems: List<StockItem>,
    viewModel: MainViewModel
) {
    val stockItems by viewModel.allStock.collectAsState()
    val projects by viewModel.allProjects.collectAsState()
    val activeProjectsCount = remember(projects) { projects.count { it.status == "In Progress" || it.status == "Produksi" || it.status == "Desain" } }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Halo, ${currentUser?.displayName ?: "Rekan Staff"} 👋",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Text(
                        text = "Akses Operational Portal — ${currentUser?.role?.name}",
                        fontSize = 12.sp,
                        color = TextMuted,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val context = androidx.compose.ui.platform.LocalContext.current
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CloudSync,
                            contentDescription = "Cloud Sync",
                            tint = HighlightSoftCyan,
                            modifier = Modifier.size(12.dp)
                        )
                        val lastSync = AppSettings.getLastSync(context).ifEmpty { "Belum sinkron" }
                        Text(
                            text = "Sinkron Terakhir: $lastSync",
                            fontSize = 10.sp,
                            color = TextIsiSoftGray,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkGrey),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AgedGold.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(text = clockString, fontSize = 14.sp, fontWeight = FontWeight.Black, color = AgedGold)
                        Text(text = dateString, fontSize = 8.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Operational stats summary cards
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Active Projects card
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { viewModel.setTab(AppTab.PROJECT) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkGrey),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderGrey)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Icon(Icons.Outlined.WorkOutline, contentDescription = null, tint = AgedGold, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = activeProjectsCount.toString(), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(text = "Project Aktif", fontSize = 10.sp, color = TextMuted)
                    }
                }

                // Low Stock Alerts count card
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { viewModel.setTab(AppTab.STOCK) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkGrey),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderGrey)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Icon(Icons.Outlined.Warning, contentDescription = null, tint = if (criticalItems.isNotEmpty()) AlertOrange else AlertGreen, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = criticalItems.size.toString(), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(text = "Stok Menipis", fontSize = 10.sp, color = TextMuted)
                    }
                }
            }
        }

        // Warning Section for Low Stock (Critical Items)
        if (criticalItems.isNotEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkGrey),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AlertOrange.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Warning, contentDescription = null, tint = AlertOrange, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("PERINGATAN STOK MENIPIS (SEGERA RE-STOCK)", fontSize = 11.sp, fontWeight = FontWeight.Black, color = AlertOrange)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        criticalItems.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(item.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text("${item.stockCount} pcs tersisa", fontSize = 11.sp, color = AlertRed, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Task List / Info Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkGrey),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderGrey),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("PANDUAN OPERASIONAL STAFF", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AgedGold)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("1. Lakukan update stok secara berkala di tab 'Stock' saat ada barang datang atau keluar.", fontSize = 11.sp, color = TextLight)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("2. Periksa status pengerjaan proyek custom sablon dan konfeksi di tab 'Project'.", fontSize = 11.sp, color = TextLight)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("3. Jika ada pengeluaran sablon/operasional baru, laporkan langsung kepada Owner agar dicatat dalam database keuangan.", fontSize = 11.sp, color = TextLight)
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
fun ClientPortalDashboardView(
    currentUser: UserSession?,
    userRole: UserRole,
    clockString: String,
    dateString: String,
    invoices: List<Invoice>,
    viewModel: MainViewModel,
    onInvoiceClick: (Invoice) -> Unit
) {
    com.yansproject.app.ui.member.DashboardMemberScreen(
        currentUser = currentUser,
        userRole = userRole,
        clockString = clockString,
        dateString = dateString,
        invoices = invoices,
        viewModel = viewModel,
        onInvoiceClick = onInvoiceClick,
        isLoading = false
    )
}

@Composable
fun LowStockDetailsDialog(
    lowStockItems: List<StockItem>,
    onViewAllStock: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = ShadowBlack),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, AlertRed.copy(alpha = 0.4f)),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Detail Stok Menipis",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = AlertRed
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Outlined.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(lowStockItems) { item ->
                        val parts = item.name.split("-").map { it.trim() }
                        val series = parts.getOrNull(0) ?: item.name
                        val warna = parts.getOrNull(1) ?: "-"
                        val ukuran = parts.getOrNull(2) ?: "-"
                        val sleeve = parts.getOrNull(3) ?: "-"
                        
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CardGrey),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, AgedGold.copy(alpha = 0.2f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = series,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AgedGold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(text = "Warna: $warna", fontSize = 11.sp, color = TextLight)
                                        Text(text = "Ukuran: $ukuran", fontSize = 11.sp, color = TextLight)
                                        Text(text = "Tipe: $sleeve", fontSize = 11.sp, color = TextLight)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "${item.stockCount} Pcs",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Black,
                                            color = if (item.stockCount == 0) AlertRed else AlertOrange
                                        )
                                        Text(
                                            text = if (item.stockCount == 0) "KOSONG" else "KRITIS",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (item.stockCount == 0) AlertRed else AlertOrange
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onViewAllStock,
                    colors = ButtonDefaults.buttonColors(containerColor = AgedGold),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Inventory2,
                        contentDescription = null,
                        tint = ShadowBlack,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "LIHAT SEMUA STOK",
                        color = ShadowBlack,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddInflowDialog(
    onDismiss: () -> Unit,
    onSave: (category: String, amount: Double, date: Long, notes: String, photoUrl: String) -> Unit
) {
    var selectedCategory by remember { mutableStateOf("Modal") }
    var nominalStr by remember { mutableStateOf("") }
    var dateSelected by remember { mutableStateOf(System.currentTimeMillis()) }
    var notesStr by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showDatePickerDialog by remember { mutableStateOf(false) }

    val formattedDate = remember(dateSelected) {
        val sdf = SimpleDateFormat("dd MMMM yyyy", Locale.forLanguageTag("id-ID"))
        sdf.format(Date(dateSelected))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkGrey,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.border(1.dp, AgedGold.copy(alpha = 0.5f), RoundedCornerShape(20.dp)),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.TrendingUp,
                    contentDescription = null,
                    tint = HighlightSoftCyan,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Catat Pemasukan Baru",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Pilihan Kategori (Chips)
                Column {
                    Text(
                        text = "Kategori Pemasukan",
                        fontSize = 12.sp,
                        color = TextMuted,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val categories = listOf("Modal", "Penjualan", "Lainnya")
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        categories.forEach { cat ->
                            val isCatSelected = selectedCategory == cat
                            FilterChip(
                                selected = isCatSelected,
                                onClick = { selectedCategory = cat },
                                label = { Text(cat, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AgedGold,
                                    selectedLabelColor = ShadowBlack,
                                    containerColor = CardGrey,
                                    labelColor = TextLight
                                ),
                                modifier = Modifier.testTag("chip_cat_inflow_$cat")
                            )
                        }
                    }
                }

                // Input Nominal
                OutlinedTextField(
                    value = nominalStr,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() }) {
                            nominalStr = input
                        }
                    },
                    label = { Text("Nominal Pemasukan (Rp)", color = TextMuted) },
                    placeholder = { Text("Contoh: 150000", color = TextMuted.copy(alpha = 0.5f)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AgedGold,
                        unfocusedBorderColor = BorderGrey,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_nominal_inflow")
                )

                // Pilihan Tanggal
                Column {
                    Text(
                        text = "Tanggal Transaksi",
                        fontSize = 12.sp,
                        color = TextMuted,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(CardGrey)
                            .border(1.dp, BorderGrey, RoundedCornerShape(8.dp))
                            .clickable { showDatePickerDialog = true }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.CalendarMonth,
                                contentDescription = null,
                                tint = AgedGold,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = formattedDate,
                                fontSize = 13.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Text(
                            text = "UBAH",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AgedGold
                        )
                    }
                }

                // Input Catatan
                OutlinedTextField(
                    value = notesStr,
                    onValueChange = { notesStr = it },
                    label = { Text("Catatan / Keterangan", color = TextMuted) },
                    placeholder = { Text("Ketik detail pemasukan...", color = TextMuted.copy(alpha = 0.5f)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AgedGold,
                        unfocusedBorderColor = BorderGrey,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_notes_inflow")
                )

                // Error Message
                errorMessage?.let { error ->
                    Text(
                        text = error,
                        fontSize = 11.sp,
                        color = AlertRed,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amountVal = nominalStr.toDoubleOrNull()
                    if (amountVal == null || amountVal <= 0.0) {
                        errorMessage = "Nominal harus berupa angka lebih besar dari 0!"
                    } else if (notesStr.trim().isEmpty()) {
                        errorMessage = "Catatan tidak boleh kosong!"
                    } else {
                        onSave(selectedCategory, amountVal, dateSelected, notesStr.trim(), "")
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AgedGold),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("btn_simpan_inflow")
            ) {
                Text("Simpan", color = ShadowBlack, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("Batal", color = TextMuted)
            }
        }
    )

    // Date Picker Dialog M3
    if (showDatePickerDialog) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = dateSelected)
        DatePickerDialog(
            onDismissRequest = { showDatePickerDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        dateSelected = it
                    }
                    showDatePickerDialog = false
                }) {
                    Text("Pilih", color = AgedGold, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerDialog = false }) {
                    Text("Batal", color = TextMuted)
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

// --- NEW DONUT CHART AND INTEGRATED SUMMARY CARD ---
data class DonutSlice(
    val label: String,
    val amount: Double,
    val color: Color
)

@Composable
fun InteractiveDonutChart(
    slices: List<DonutSlice>,
    modifier: Modifier = Modifier
) {
    val animatedProgress = remember { Animatable(0f) }
    
    LaunchedEffect(slices) {
        animatedProgress.snapTo(0f)
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
        )
    }
    
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 22.dp.toPx()
            val diameter = size.minDimension - strokeWidth
            val rect = Rect(
                left = (size.width - diameter) / 2f,
                top = (size.height - diameter) / 2f,
                right = (size.width + diameter) / 2f,
                bottom = (size.height + diameter) / 2f
            )
            
            var startAngle = -90f
            
            val total = slices.sumOf { it.amount }
            
            // Background thin track circle
            drawArc(
                color = Color.White.copy(alpha = 0.05f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth),
                topLeft = Offset(rect.left, rect.top),
                size = Size(rect.width, rect.height)
            )
            
            if (slices.isEmpty() || total == 0.0) {
                // If empty, percentage 0% and display gray donut ring as requested
                drawArc(
                    color = Color.Gray.copy(alpha = 0.2f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth),
                    topLeft = Offset(rect.left, rect.top),
                    size = Size(rect.width, rect.height)
                )
            } else {
                slices.forEach { slice ->
                    val percentage = (slice.amount / total).toFloat()
                    val sweepAngle = percentage * 360f * animatedProgress.value
                    
                    if (sweepAngle > 0f) {
                        drawArc(
                            color = slice.color,
                            startAngle = startAngle,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            style = Stroke(width = strokeWidth),
                            topLeft = Offset(rect.left, rect.top),
                            size = Size(rect.width, rect.height)
                        )
                        startAngle += sweepAngle
                    }
                }
            }
        }
        
        // Centered info inside the donut hole
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val totalAmount = slices.sumOf { it.amount }
            Text(
                text = "TOTAL",
                fontSize = 10.sp,
                color = TextMuted,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Text(
                text = FormatUtils.formatRupiah(totalAmount),
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
        }
    }
}

// Helper untuk kalkulasi nominal terbayar invoice yang akurat
fun getEffectiveInvoicePaid(inv: Invoice): Double {
    if (inv.isDeleted) return 0.0
    val st = inv.status.trim().uppercase()
    if (st in listOf("CANCELLED", "VOID", "DIBATALKAN", "DRAFT")) return 0.0
    if (inv.paidAmount > 0.0) return inv.paidAmount
    if (st in listOf("LUNAS", "PAID", "SELESAI", "LUNAS (PAID)")) {
        return if (inv.totalAmount > 0.0) inv.totalAmount else 0.0
    }
    if (inv.dpAmount > 0.0) return inv.dpAmount
    return 0.0
}

fun calculateInvoicePaid(inv: Invoice, allPayments: List<InvoicePayment> = emptyList()): Double {
    if (inv.isDeleted) return 0.0
    val st = inv.status.trim().uppercase()
    if (st in listOf("CANCELLED", "VOID", "DIBATALKAN", "DRAFT")) return 0.0
    if (st in listOf("LUNAS", "PAID", "SELESAI", "LUNAS (PAID)")) {
        return if (inv.totalAmount > 0.0) inv.totalAmount else 0.0
    }
    val paymentsForInv = allPayments.filter { p ->
        (p.invoiceId == inv.invoiceNumber && inv.invoiceNumber.isNotBlank()) || 
        p.invoiceId == inv.id.toString()
    }
    val paymentRecordsSum = paymentsForInv
        .distinctBy { p -> p.id.ifEmpty { "${p.date}_${p.amount}_${p.paymentMethod}" } }
        .sumOf { p -> p.amount }
        
    return maxOf(getEffectiveInvoicePaid(inv), paymentRecordsSum)
}

fun calculateInvoiceSisaPiutang(inv: Invoice, allPayments: List<InvoicePayment> = emptyList()): Double {
    if (inv.isDeleted) return 0.0
    val st = inv.status.trim().uppercase()
    if (st in listOf("CANCELLED", "VOID", "DIBATALKAN", "BATAL", "DRAFT", "LUNAS", "PAID", "SELESAI", "LUNAS (PAID)")) return 0.0
    
    val paid = calculateInvoicePaid(inv, allPayments)
    return maxOf(0.0, inv.totalAmount - paid)
}

// Helper untuk kalkulasi nominal terbayar order direct/POS
fun getEffectiveOrderPaid(ord: OrderHistory): Double {
    if (ord.isDeleted) return 0.0
    if (ord.paidAmount > 0.0) return ord.paidAmount
    val st = ord.status.trim().uppercase()
    if (st in listOf("COMPLETED", "SELESAI", "LUNAS", "PAID", "DISETUJUI", "TERBAYAR")) {
        return if (ord.totalAmount > 0.0) ord.totalAmount else 0.0
    }
    return 0.0
}

@Composable
fun DashboardRingkasanKeuanganCard(
    totalPemasukan: Double,
    totalPengeluaran: Double,
    filteredInflows: List<Inflow>,
    filteredInvoices: List<Invoice>,
    filteredExpenses: List<Expense>,
    filteredStandaloneOrders: List<OrderHistory> = emptyList(),
    allPayments: List<InvoicePayment> = emptyList()
) {
    var activeTab by remember { mutableStateOf("SEMUA") }

    // Computations for Inflows
    val modalAmt = remember(filteredInflows) {
        filteredInflows.filter { !it.isDeleted && it.category.contains("Modal", ignoreCase = true) }.sumOf { it.amount }
    }
    val lainnyaInAmt = remember(filteredInflows) {
        filteredInflows.filter { !it.isDeleted && it.category.contains("Lainnya", ignoreCase = true) }.sumOf { it.amount }
    }
    val penjualanAmt = remember(filteredInvoices, filteredInflows, filteredStandaloneOrders, allPayments) {
        val invoicePaid = filteredInvoices.sumOf { calculateInvoicePaid(it, allPayments) }
        val orderPaid = filteredStandaloneOrders.sumOf { getEffectiveOrderPaid(it) }
        val manualSalesInflows = filteredInflows.filter { 
            !it.isDeleted && 
            !it.category.contains("Modal", ignoreCase = true) &&
            !it.category.contains("Lainnya", ignoreCase = true) &&
            !it.category.contains("Pembayaran Customer", ignoreCase = true) &&
            !it.notes.contains("[PAY_") &&
            !it.notes.contains("Pembayaran Invoice")
        }.sumOf { it.amount }
        invoicePaid + orderPaid + manualSalesInflows
    }

    // Computations for Expenses
    val produksiAmt = remember(filteredExpenses) {
        filteredExpenses.filter { it.category.contains("Produksi", ignoreCase = true) || it.category.contains("Sablon", ignoreCase = true) }.sumOf { it.amount }
    }
    val aksesoriesAmt = remember(filteredExpenses) {
        filteredExpenses.filter { it.category.contains("Aksesories", ignoreCase = true) || it.category.contains("Aksesoris", ignoreCase = true) || it.category.contains("Packing", ignoreCase = true) }.sumOf { it.amount }
    }
    val transportAmt = remember(filteredExpenses) {
        filteredExpenses.filter { it.category.contains("Transport", ignoreCase = true) }.sumOf { it.amount }
    }
    val operasionalAmt = remember(filteredExpenses) {
        filteredExpenses.filter { it.category.contains("Operasional", ignoreCase = true) }.sumOf { it.amount }
    }
    val lainnyaOutAmt = remember(filteredExpenses) {
        filteredExpenses.filter { exp ->
            !exp.category.contains("Produksi", ignoreCase = true) &&
            !exp.category.contains("Sablon", ignoreCase = true) &&
            !exp.category.contains("Aksesories", ignoreCase = true) &&
            !exp.category.contains("Aksesoris", ignoreCase = true) &&
            !exp.category.contains("Packing", ignoreCase = true) &&
            !exp.category.contains("Transport", ignoreCase = true) &&
            !exp.category.contains("Operasional", ignoreCase = true)
        }.sumOf { it.amount }
    }

    // Determine current slices based on activeTab
    val slices = remember(activeTab, totalPemasukan, totalPengeluaran, modalAmt, penjualanAmt, lainnyaInAmt, produksiAmt, aksesoriesAmt, transportAmt, operasionalAmt, lainnyaOutAmt) {
        when (activeTab) {
            "SEMUA" -> {
                listOf(
                    DonutSlice("Pemasukan", totalPemasukan, HighlightSoftCyan),
                    DonutSlice("Pengeluaran", totalPengeluaran, AgedGold)
                )
            }
            "PEMASUKAN" -> {
                listOf(
                    DonutSlice("Modal", modalAmt, HighlightSoftCyan),
                    DonutSlice("Penjualan", penjualanAmt, AgedGold),
                    DonutSlice("Lainnya", lainnyaInAmt, Color(0xFF319795))
                )
            }
            "PENGELUARAN" -> {
                listOf(
                    DonutSlice("Produksi", produksiAmt, AgedGold),
                    DonutSlice("Aksesories", aksesoriesAmt, Color(0xFF3182CE)),
                    DonutSlice("Transport", transportAmt, Color(0xFFECC94B)),
                    DonutSlice("Operasional", operasionalAmt, AlertRed),
                    DonutSlice("Lainnya", lainnyaOutAmt, Color(0xFF805AD5))
                )
            }
            else -> emptyList()
        }
    }

    val cardShape = RoundedCornerShape(24.dp)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 12.dp,
                shape = cardShape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.6f),
                spotColor = PrimaryDarkTeal.copy(alpha = 0.3f)
            )
            .clip(cardShape),
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        border = BorderStroke(
            width = 1.2.dp,
            brush = Brush.verticalGradient(
                colors = listOf(
                    AgedGold.copy(alpha = 0.35f),
                    Color.Transparent
                )
            )
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            SurfaceDarkTeal.copy(alpha = 0.92f),
                            SecondaryShadowBlackTeal.copy(alpha = 0.98f)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title
                Text(
                    text = "RINGKASAN KEUANGAN",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AgedGold,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.align(Alignment.Start)
                )

                // 1. Donut Chart (Animated)
                Box(
                    modifier = Modifier
                        .size(170.dp)
                        .align(Alignment.CenterHorizontally),
                    contentAlignment = Alignment.Center
                ) {
                    InteractiveDonutChart(slices = slices, modifier = Modifier.fillMaxSize())
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 2. Nominal Details (Rincian)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val totalAmount = slices.sumOf { it.amount }
                    
                    when (activeTab) {
                        "SEMUA" -> {
                            val selisih = totalPemasukan - totalPengeluaran
                            val persenPemasukan = if (totalAmount > 0) (totalPemasukan / totalAmount * 100).toInt() else 0
                            val persenPengeluaran = if (totalAmount > 0) (totalPengeluaran / totalAmount * 100).toInt() else 0

                            RincianItemRow(
                                label = "Total Pemasukan",
                                percentage = persenPemasukan,
                                amount = totalPemasukan,
                                color = HighlightSoftCyan
                            )
                            RincianItemRow(
                                label = "Total Pengeluaran",
                                percentage = persenPengeluaran,
                                amount = totalPengeluaran,
                                color = AgedGold
                            )
                            
                            HorizontalDivider(
                                color = DividerDarkCyanGray.copy(alpha = 0.35f), 
                                thickness = 1.dp,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Saldo Bersih",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = (if (selisih < 0) "-" else "") + FormatUtils.formatRupiah(kotlin.math.abs(selisih)),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (selisih >= 0) HighlightSoftCyan else ErrorRed
                                )
                            }
                        }
                        "PEMASUKAN" -> {
                            val pModal = if (totalAmount > 0) (modalAmt / totalAmount * 100).toInt() else 0
                            val pPenjualan = if (totalAmount > 0) (penjualanAmt / totalAmount * 100).toInt() else 0
                            val pLainnya = if (totalAmount > 0) (lainnyaInAmt / totalAmount * 100).toInt() else 0

                            RincianItemRow("Modal", pModal, modalAmt, HighlightSoftCyan)
                            RincianItemRow("Penjualan", pPenjualan, penjualanAmt, AgedGold)
                            RincianItemRow("Lainnya", pLainnya, lainnyaInAmt, Color(0xFF319795))
                        }
                        "PENGELUARAN" -> {
                            val pProduksi = if (totalAmount > 0) (produksiAmt / totalAmount * 100).toInt() else 0
                            val pAksesories = if (totalAmount > 0) (aksesoriesAmt / totalAmount * 100).toInt() else 0
                            val pTransport = if (totalAmount > 0) (transportAmt / totalAmount * 100).toInt() else 0
                            val pOperasional = if (totalAmount > 0) (operasionalAmt / totalAmount * 100).toInt() else 0
                            val pLainnya = if (totalAmount > 0) (lainnyaOutAmt / totalAmount * 100).toInt() else 0

                            RincianItemRow("Produksi", pProduksi, produksiAmt, AgedGold)
                            RincianItemRow("Aksesories", pAksesories, aksesoriesAmt, Color(0xFF3182CE))
                            RincianItemRow("Transport", pTransport, transportAmt, Color(0xFFECC94B))
                            RincianItemRow("Operasional", pOperasional, operasionalAmt, ErrorRed)
                            RincianItemRow("Lainnya", pLainnya, lainnyaOutAmt, Color(0xFF805AD5))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 3. Tab Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SecondaryShadowBlackTeal.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                        .border(1.dp, DividerDarkCyanGray.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("SEMUA", "PEMASUKAN", "PENGELUARAN").forEach { tab ->
                        val isSelected = activeTab == tab
                        val bgSelectedColor = if (isSelected) SurfaceDarkTeal.copy(alpha = 0.9f) else Color.Transparent
                        val textSelectedColor = if (isSelected) AgedGold else TextSecondary.copy(alpha = 0.6f)
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(bgSelectedColor)
                                .clickable { activeTab = tab }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tab,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = textSelectedColor,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RincianItemRow(
    label: String,
    percentage: Int,
    amount: Double,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                text = "$label ($percentage%)",
                fontSize = 12.sp,
                color = TextLight,
                fontWeight = FontWeight.Medium
            )
        }
        Text(
            text = FormatUtils.formatRupiah(amount),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

// ==========================================
// ENTERPRISE ERP COMMAND CENTER CUSTOM UI
// ==========================================

@Composable
fun SearchBarOwner(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardDarkCard)
            .border(1.dp, DividerDarkCyanGray.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = "Cari",
                tint = AgedGold,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "Cari Invoice, Project, Produk, Stock, Ledger...",
                color = TextIsiSoftGray,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

data class AlertData(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val color: Color,
    val actionText: String,
    val onClick: () -> Unit
)

@Composable
fun BusinessAlertCard(
    alert: AlertData,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = alert.color.copy(alpha = 0.08f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, alert.color.copy(alpha = 0.4f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = alert.icon,
                    contentDescription = null,
                    tint = alert.color,
                    modifier = Modifier.size(20.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = alert.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = alert.color
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = alert.description,
                        fontSize = 11.sp,
                        color = TextLight,
                        lineHeight = 15.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick = alert.onClick,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${alert.actionText} >",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AgedGold
                    )
                }
            }
        }
    }
}

@Composable
fun TodayMetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(16.dp)
    Card(
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(
                    color.copy(alpha = 0.4f),
                    Color.Transparent
                )
            )
        ),
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 6.dp,
                shape = cardShape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.3f),
                spotColor = color.copy(alpha = 0.2f)
            )
            .clip(cardShape)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            SurfaceDarkTeal.copy(alpha = 0.9f),
                            SecondaryShadowBlackTeal.copy(alpha = 0.95f)
                        )
                    )
                )
                .padding(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title.uppercase(),
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = AgedGold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = value,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        maxLines = 1
                    )
                }
            }
        }
    }
}



@Composable
fun RiwayatProduksiScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onNavigateToProject: () -> Unit
) {
    val context = LocalContext.current
    val projects by viewModel.allProjects.collectAsState()
    val activeQueue = remember(projects) {
        projects.filter { !it.isDeleted && it.status in listOf("Planning", "In Progress", "Produksi", "Desain") }
            .sortedBy { it.endDate }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // App Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkTeal)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Outlined.ArrowBack,
                        contentDescription = "Kembali",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ANTREAN PRODUKSI ERP",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "${activeQueue.size} Project Sedang Berjalan",
                        fontSize = 11.sp,
                        color = AgedGold,
                        fontWeight = FontWeight.Medium
                    )
                }
                TextButton(onClick = onNavigateToProject) {
                    Text("Kelola Project >", color = AgedGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (activeQueue.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.PrecisionManufacturing,
                            contentDescription = null,
                            tint = TextIsiSoftGray.copy(alpha = 0.4f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Tidak Ada Antrean Produksi Berjalan",
                            color = TextIsiSoftGray,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(activeQueue) { proj ->
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = CardDarkCard),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DividerDarkCyanGray.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = proj.projectName.uppercase(),
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Product: ${proj.productType} (${proj.sleeveType})",
                                            fontSize = 11.sp,
                                            color = TextIsiSoftGray
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                when (proj.status) {
                                                    "Produksi" -> AlertOrange.copy(alpha = 0.15f)
                                                    "In Progress" -> HighlightSoftCyan.copy(alpha = 0.15f)
                                                    "Desain" -> AlertBlue.copy(alpha = 0.15f)
                                                    else -> AgedGold.copy(alpha = 0.15f)
                                                }
                                            )
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = proj.status,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = when (proj.status) {
                                                "Produksi" -> AlertOrange
                                                "In Progress" -> HighlightSoftCyan
                                                "Desain" -> AlertBlue
                                                else -> AgedGold
                                            }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                Divider(color = DividerDarkCyanGray.copy(alpha = 0.2f))
                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("CLIENT", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = AgedGold)
                                        Text(proj.clientName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        Text(proj.clientPhone, fontSize = 11.sp, color = TextLight)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        IconButton(
                                            onClick = {
                                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${proj.clientPhone}"))
                                                context.startActivity(intent)
                                            },
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(AgedGold.copy(alpha = 0.12f))
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Phone,
                                                contentDescription = "Telepon",
                                                tint = AgedGold,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                val cleanPhone = proj.clientPhone.replace("+", "").replace(" ", "").replace("-", "")
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone"))
                                                context.startActivity(intent)
                                            },
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(AlertGreen.copy(alpha = 0.12f))
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Chat,
                                                contentDescription = "WhatsApp",
                                                tint = AlertGreen,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                Text("SIZE BREAKDOWNS (QUANTITY)", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = AgedGold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    val sizes = listOf(
                                        "XS" to proj.qtyXS, "S" to proj.qtyS, "M" to proj.qtyM,
                                        "L" to proj.qtyL, "XL" to proj.qtyXL, "XXL" to proj.qtyXXL,
                                        "3XL" to proj.qty3XL, "4XL" to proj.qty4XL
                                    )
                                    sizes.forEach { (sz, qty) ->
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (qty > 0) DarkTeal.copy(alpha = 0.2f) else CardGrey)
                                                .border(1.dp, if (qty > 0) DarkTeal else BorderGrey, RoundedCornerShape(6.dp))
                                                .padding(vertical = 4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(sz, fontSize = 8.sp, color = if (qty > 0) HighlightSoftCyan else TextIsiSoftGray, fontWeight = FontWeight.Bold)
                                                Text("$qty", fontSize = 10.sp, color = if (qty > 0) Color.White else TextIsiSoftGray, fontWeight = FontWeight.ExtraBold)
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("TOTAL KUANTITAS", fontSize = 9.sp, color = TextIsiSoftGray)
                                        Text("${proj.totalQty} Pcs", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color.White)
                                    }
                                    if (proj.remainingPayment > 0) {
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("SISA TAGIHAN", fontSize = 9.sp, color = AlertOrange)
                                            Text(FormatUtils.formatRupiah(proj.remainingPayment), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AlertOrange)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                Text("UBAH STATUS PRODUKSI", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = AgedGold)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf("Desain", "In Progress", "Produksi", "Completed").forEach { st ->
                                        val isCurrent = proj.status == st
                                        Button(
                                            onClick = {
                                                if (!isCurrent) {
                                                    viewModel.updateProject(proj.copy(status = st))
                                                    Toast.makeText(context, "Status Project '${proj.projectName}' berhasil diubah ke: $st!", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isCurrent) AgedGold else CardGrey,
                                                contentColor = if (isCurrent) ShadowBlack else Color.White
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.weight(1f),
                                            contentPadding = PaddingValues(vertical = 4.dp)
                                        ) {
                                            Text(st, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RiwayatLaporanScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val invoices by viewModel.allInvoices.collectAsState()
    val expenses by viewModel.allExpenses.collectAsState()
    val inflows by viewModel.allInflows.collectAsState()
    val orders by viewModel.allOrders.collectAsState()

    var selectedPeriod by remember { mutableStateOf("Semua") }
    var selectedCategoryFilter by remember { mutableStateOf<String?>(null) }
    var selectedLogForReceipt by remember { mutableStateOf<DashboardActivity?>(null) }

    val filteredLogs = remember(invoices, expenses, inflows, orders, selectedPeriod, selectedCategoryFilter) {
        val list = mutableListOf<DashboardActivity>()
        
        fun isTimeInFilter(time: Long): Boolean {
            return when (selectedPeriod) {
                "Hari Ini" -> {
                    val today = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    time >= today
                }
                "7 Hari" -> time >= System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
                "30 Hari" -> time >= System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
                else -> true
            }
        }

        // Pemasukan dari Invoices (Lunas/paidAmount > 0)
        invoices.forEach { inv ->
            val paid = if (inv.paidAmount > 0.0) inv.paidAmount
            else if (inv.status.equals("LUNAS", ignoreCase = true) || inv.status.equals("PAID", ignoreCase = true) || inv.status.equals("SELESAI", ignoreCase = true)) inv.totalAmount
            else if (inv.dpAmount > 0.0) inv.dpAmount
            else 0.0
            if (!inv.isDeleted && paid > 0 && isTimeInFilter(inv.issueDate)) {
                list.add(
                    DashboardActivity(
                        title = "Pembayaran Invoice",
                        description = "No: ${inv.invoiceNumber} - Client: ${inv.clientName}",
                        date = inv.issueDate,
                        type = "Pemasukan",
                        amount = paid,
                        category = "Invoice"
                    )
                )
            }
        }

        // Pemasukan dari POS Direct Orders
        orders.forEach { ord ->
            val st = ord.status.trim().uppercase()
            val paid = if (ord.paidAmount > 0.0) ord.paidAmount
            else if (st == "COMPLETED" || st == "SELESAI" || st == "LUNAS" || st == "PAID" || st == "DISETUJUI" || st == "TERBAYAR") ord.totalAmount
            else 0.0
            val isNotCoveredByInvoice = invoices.none { inv -> inv.orderId == ord.id }
            if (!ord.isDeleted && paid > 0 && isNotCoveredByInvoice && isTimeInFilter(ord.orderDate)) {
                list.add(
                    DashboardActivity(
                        title = "Penjualan POS Direct",
                        description = "Order #${ord.id} - Client: ${ord.clientName}",
                        date = ord.orderDate,
                        type = "Pemasukan",
                        amount = paid,
                        category = "Penjualan"
                    )
                )
            }
        }

        // Pemasukan manual
        inflows.forEach { inf ->
            if (!inf.isDeleted && isTimeInFilter(inf.date)) {
                list.add(
                    DashboardActivity(
                        title = "Pemasukan Manual",
                        description = inf.notes,
                        date = inf.date,
                        type = "Pemasukan",
                        amount = inf.amount,
                        category = inf.category
                    )
                )
            }
        }

        // Pengeluaran
        expenses.forEach { exp ->
            if (!exp.isDeleted && isTimeInFilter(exp.date)) {
                list.add(
                    DashboardActivity(
                        title = "Pengeluaran",
                        description = exp.notes,
                        date = exp.date,
                        type = "Pengeluaran",
                        amount = exp.amount,
                        category = exp.category
                    )
                )
            }
        }

        list.sortByDescending { it.date }
        
        if (selectedCategoryFilter != null) {
            list.filter { it.category == selectedCategoryFilter }
        } else {
            list
        }
    }

    val totalRevenue = remember(filteredLogs) {
        filteredLogs.filter { it.type == "Pemasukan" }.sumOf { it.amount ?: 0.0 }
    }
    val totalExpense = remember(filteredLogs) {
        filteredLogs.filter { it.type == "Pengeluaran" }.sumOf { it.amount ?: 0.0 }
    }
    val netMargin = totalRevenue - totalExpense

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // App Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkTeal)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Outlined.ArrowBack,
                        contentDescription = "Kembali",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "ANALISIS & LAPORAN ERP",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Ringkasan Keuangan Komprehensif",
                        fontSize = 11.sp,
                        color = AgedGold,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Period Filters
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Hari Ini", "7 Hari", "30 Hari", "Semua").forEach { pr ->
                            val isSelected = selectedPeriod == pr
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) AgedGold else CardGrey)
                                    .clickable {
                                        selectedPeriod = pr
                                        selectedCategoryFilter = null // reset category filter
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = pr,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) ShadowBlack else Color.White
                                )
                            }
                        }
                    }
                }

                // Financial Overview Metrics Cards
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "RINGKASAN REKAP KAS ($selectedPeriod)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AgedGold,
                            letterSpacing = 1.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = CardDarkCard),
                                border = androidx.compose.foundation.BorderStroke(1.dp, AlertGreen.copy(alpha = 0.3f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("PENDAPATAN KAS", fontSize = 9.sp, color = TextIsiSoftGray, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(FormatUtils.formatRupiah(totalRevenue), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = AlertGreen)
                                }
                            }
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = CardDarkCard),
                                border = androidx.compose.foundation.BorderStroke(1.dp, AlertRed.copy(alpha = 0.3f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("PENGELUARAN KAS", fontSize = 9.sp, color = TextIsiSoftGray, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(FormatUtils.formatRupiah(totalExpense), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = AlertRed)
                                }
                            }
                        }
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkTeal.copy(alpha = 0.1f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AgedGold.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("LABA BERSIH (NET MARGIN)", fontSize = 10.sp, color = AgedGold, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(FormatUtils.formatRupiah(netMargin), fontSize = 20.sp, fontWeight = FontWeight.Black, color = if (netMargin >= 0) HighlightSoftCyan else AlertRed)
                                }
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(AgedGold.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (netMargin >= 0) Icons.Outlined.TrendingUp else Icons.Outlined.TrendingDown,
                                        contentDescription = null,
                                        tint = if (netMargin >= 0) HighlightSoftCyan else AlertRed,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Interactive Category Breakdown
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "URUTAN KATEGORI TRANSAKSI (Ketuk untuk Filter)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AgedGold,
                            letterSpacing = 1.sp
                        )
                        
                        val categoryMap = filteredLogs.groupBy { it.category ?: "Lainnya" }
                        if (categoryMap.isEmpty()) {
                            Text("Tidak ada kategori transaksi pada periode ini.", fontSize = 11.sp, color = TextIsiSoftGray)
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val topCategories = categoryMap.map { entry -> entry.key to entry.value.sumOf { it.amount ?: 0.0 } }
                                    .sortedByDescending { it.second }
                                    .take(4)
                                
                                topCategories.forEach { (cat, total) ->
                                    val isSelected = selectedCategoryFilter == cat
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isSelected) AgedGold.copy(alpha = 0.15f) else CardDarkCard)
                                            .border(1.dp, if (isSelected) AgedGold else DividerDarkCyanGray.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                                            .clickable {
                                                selectedCategoryFilter = if (isSelected) null else cat
                                            }
                                            .padding(10.dp)
                                    ) {
                                        Column {
                                            Text(cat.uppercase(), fontSize = 8.sp, fontWeight = FontWeight.Bold, color = AgedGold, maxLines = 1)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(FormatUtils.formatRupiah(total), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Transaction Logs
                item {
                    Text(
                        text = "LOG JURNAL TRANSAKSI KEUANGAN",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AgedGold,
                        letterSpacing = 1.sp
                    )
                }

                if (filteredLogs.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Tidak ada transaksi ditemukan.", color = TextIsiSoftGray, fontSize = 12.sp)
                        }
                    }
                } else {
                    items(filteredLogs) { log ->
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = CardDarkCard),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedLogForReceipt = log }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(if (log.type == "Pemasukan") AlertGreen.copy(alpha = 0.12f) else AlertRed.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (log.type == "Pemasukan") Icons.Outlined.TrendingUp else Icons.Outlined.TrendingDown,
                                            contentDescription = null,
                                            tint = if (log.type == "Pemasukan") AlertGreen else AlertRed,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    Column {
                                        Text(log.title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        Text(log.description, fontSize = 10.sp, color = TextLight, maxLines = 1)
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = (if (log.type == "Pemasukan") "+" else "-") + FormatUtils.formatRupiah(log.amount ?: 0.0),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (log.type == "Pemasukan") AlertGreen else AlertRed
                                    )
                                    Text(
                                        text = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(log.date)),
                                        fontSize = 8.sp,
                                        color = TextIsiSoftGray
                                    )
                                }
                            }
                        }
                    }
                }
            } // end of LazyColumn
        }

        // Receipt Details Dialog
        selectedLogForReceipt?.let { log ->
            Dialog(onDismissRequest = { selectedLogForReceipt = null }) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardGrey),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AgedGold.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("BUKTI KEUANGAN ERP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AgedGold, letterSpacing = 2.sp)
                        Text("YANSPROJECT.ID", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        
                        Divider(color = DividerDarkCyanGray.copy(alpha = 0.3f), thickness = 1.dp)

                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Jenis Aliran", fontSize = 11.sp, color = TextIsiSoftGray)
                                Text(log.type, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (log.type == "Pemasukan") AlertGreen else AlertRed)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Judul Transaksi", fontSize = 11.sp, color = TextIsiSoftGray)
                                Text(log.title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Detail / Deskripsi", fontSize = 11.sp, color = TextIsiSoftGray)
                                Text(log.description, fontSize = 11.sp, color = Color.White, textAlign = TextAlign.End, modifier = Modifier.width(160.dp))
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Kategori", fontSize = 11.sp, color = TextIsiSoftGray)
                                Text(log.category ?: "Lainnya", fontSize = 11.sp, color = AgedGold, fontWeight = FontWeight.Bold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Tanggal & Waktu", fontSize = 11.sp, color = TextIsiSoftGray)
                                Text(SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale.getDefault()).format(Date(log.date)), fontSize = 11.sp, color = Color.White)
                            }
                        }

                        Divider(color = DividerDarkCyanGray.copy(alpha = 0.3f), thickness = 1.dp)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("NOMINAL TOTAL", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AgedGold)
                            Text(FormatUtils.formatRupiah(log.amount ?: 0.0), fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color.White)
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { selectedLogForReceipt = null },
                            colors = ButtonDefaults.buttonColors(containerColor = AgedGold, contentColor = ShadowBlack),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Tutup Bukti", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
