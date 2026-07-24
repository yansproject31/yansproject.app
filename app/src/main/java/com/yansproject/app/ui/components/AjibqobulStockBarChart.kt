package com.yansproject.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yansproject.app.ui.theme.*

data class SeriesStockData(
    val seriesName: String,
    val stockCount: Int,
    val readyStock: Int = stockCount,
    val reservedStock: Int = 0
)

@Composable
fun AjibqobulStockBarChart(
    seriesList: List<SeriesStockData>,
    modifier: Modifier = Modifier,
    title: String = "GRAFIK SISA STOK SERI AJIBQOBUL",
    onSeriesSelected: ((SeriesStockData) -> Unit)? = null
) {
    var selectedSeries by remember { mutableStateOf<SeriesStockData?>(null) }
    var animationPlayed by remember { mutableStateOf(false) }

    LaunchedEffect(seriesList) {
        animationPlayed = true
    }

    val animatedProgress by animateFloatAsState(
        targetValue = if (animationPlayed) 1f else 0f,
        animationSpec = tween(durationMillis = 1000),
        label = "BarChartAnimation"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDarkTealSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderGrey),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row
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
                            .clip(CircleShape)
                            .background(SecondaryShadowBlackTeal),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.BarChart,
                            contentDescription = "Grafik Batang",
                            tint = AccentAgedGold,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = title,
                            color = AccentAgedGold,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Monitoring Ketersediaan Barang Real-Time",
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                    }
                }

                Surface(
                    color = SecondaryShadowBlackTeal,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, BorderGrey)
                ) {
                    Text(
                        text = "${seriesList.size} Series",
                        color = HighlightSoftCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (seriesList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Belum Ada Data Seri Produk AJIBQOBUL",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            } else {
                val maxStock = (seriesList.maxOfOrNull { it.stockCount } ?: 1).coerceAtLeast(10)

                // Interactive Chart Container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                ) {
                    // Canvas Bar Chart
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(seriesList) {
                                detectTapGestures { offset ->
                                    val barWidth = size.width / seriesList.size
                                    val index = (offset.x / barWidth).toInt().coerceIn(0, seriesList.size - 1)
                                    val tapped = seriesList[index]
                                    selectedSeries = if (selectedSeries == tapped) null else tapped
                                    onSeriesSelected?.invoke(tapped)
                                }
                            }
                    ) {
                        val canvasWidth = size.width
                        val canvasHeight = size.height
                        val totalBars = seriesList.size
                        val barSpacing = 16.dp.toPx()
                        val availableWidth = canvasWidth - (barSpacing * (totalBars + 1))
                        val barWidth = (availableWidth / totalBars).coerceAtLeast(12.dp.toPx())

                        // Draw background grid lines (25%, 50%, 75%, 100%)
                        val gridLevels = 4
                        val strokePathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                        for (i in 1..gridLevels) {
                            val y = canvasHeight * (1f - (i.toFloat() / gridLevels))
                            drawLine(
                                color = BorderGrey.copy(alpha = 0.4f),
                                start = Offset(0f, y),
                                end = Offset(canvasWidth, y),
                                strokeWidth = 1f,
                                pathEffect = strokePathEffect
                            )
                        }

                        // Draw Bars
                        seriesList.forEachIndexed { index, data ->
                            val x = barSpacing + index * (barWidth + barSpacing)
                            val barHeightRatio = (data.stockCount.toFloat() / maxStock.toFloat()).coerceIn(0.05f, 1f)
                            val targetBarHeight = (canvasHeight - 30.dp.toPx()) * barHeightRatio * animatedProgress
                            val y = canvasHeight - targetBarHeight - 20.dp.toPx()

                            val isSelected = selectedSeries == data
                            val isLowStock = data.stockCount <= 10

                            // Color Brush setup
                            val barBrush = if (isLowStock) {
                                Brush.verticalGradient(
                                    colors = listOf(AlertOrange, AgedGold)
                                )
                            } else if (isSelected) {
                                Brush.verticalGradient(
                                    colors = listOf(HighlightSoftCyan, PrimaryDarkTeal)
                                )
                            } else {
                                Brush.verticalGradient(
                                    colors = listOf(AgedGold, PrimaryDarkTeal)
                                )
                            }

                            // Draw Shadow / Outer Glow for Selected Bar
                            if (isSelected) {
                                drawRoundRect(
                                    color = HighlightSoftCyan.copy(alpha = 0.3f),
                                    topLeft = Offset(x - 4f, y - 4f),
                                    size = Size(barWidth + 8f, targetBarHeight + 8f),
                                    cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
                                )
                            }

                            // Draw Bar Background Container
                            drawRoundRect(
                                color = SecondaryShadowBlackTeal,
                                topLeft = Offset(x, 0f),
                                size = Size(barWidth, canvasHeight - 20.dp.toPx()),
                                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                            )

                            // Draw Active Bar
                            drawRoundRect(
                                brush = barBrush,
                                topLeft = Offset(x, y),
                                size = Size(barWidth, targetBarHeight),
                                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                            )
                        }
                    }
                }

                // X-Axis Series Label Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    seriesList.forEach { data ->
                        val isSelected = selectedSeries == data
                        Text(
                            text = data.seriesName,
                            color = if (isSelected) HighlightSoftCyan else TextMuted,
                            fontSize = 9.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    selectedSeries = if (selectedSeries == data) null else data
                                    onSeriesSelected?.invoke(data)
                                }
                        )
                    }
                }

                // Selected Series Tooltip / Detail Banner
                selectedSeries?.let { series ->
                    Surface(
                        color = SecondaryShadowBlackTeal,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, HighlightSoftCyan.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "SERI: ${series.seriesName.uppercase()}",
                                    color = AgedGold,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Ready: ${series.readyStock} Pcs | Terpesan: ${series.reservedStock} Pcs",
                                    color = TextLight,
                                    fontSize = 10.sp
                                )
                            }

                            Surface(
                                color = if (series.stockCount <= 10) AlertOrange.copy(alpha = 0.2f) else AlertGreen.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    0.5.dp,
                                    if (series.stockCount <= 10) AlertOrange else AlertGreen
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (series.stockCount <= 10) Icons.Outlined.Warning else Icons.Outlined.Info,
                                        contentDescription = null,
                                        tint = if (series.stockCount <= 10) AlertOrange else AlertGreen,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "${series.stockCount} Pcs Sisa",
                                        color = if (series.stockCount <= 10) AlertOrange else AlertGreen,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // Legend Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LegendItem(color = AgedGold, label = "Stok Normal")
                        LegendItem(color = AlertOrange, label = "Stok Menipis (<=10)")
                    }
                    Text(
                        text = "Ketuk batang untuk detail",
                        color = TextMuted,
                        fontSize = 9.5.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            color = TextMuted,
            fontSize = 9.5.sp
        )
    }
}
