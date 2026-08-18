package com.yansproject.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.TrendingDown
import androidx.compose.material.icons.outlined.TrendingUp
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yansproject.app.ui.WeeklyStockDepletionPoint
import com.yansproject.app.ui.theme.*
import java.text.NumberFormat
import java.util.Locale

/**
 * Interactive Weekly Stock Depletion Trend Chart for Jetpack Compose.
 * Renders weekly depletion trends, line graphs, gradient fills, and interactive point selection tooltips.
 */
@Composable
fun StockDepletionTrendChart(
    weeklyPoints: List<WeeklyStockDepletionPoint>,
    modifier: Modifier = Modifier,
    title: String = "GRAFIK TREN DEPLESI STOK MINGGUAN",
    accentColor: Color = AccentAgedGold,
    highlightColor: Color = AlertGreen
) {
    var selectedPoint by remember { mutableStateOf<WeeklyStockDepletionPoint?>(null) }
    var animationPlayed by remember { mutableStateOf(false) }

    LaunchedEffect(weeklyPoints) {
        animationPlayed = true
    }

    val animatedProgress by animateFloatAsState(
        targetValue = if (animationPlayed) 1f else 0f,
        animationSpec = tween(durationMillis = 1000),
        label = "StockTrendChartAnim"
    )

    val rupiahFormat = remember { NumberFormat.getCurrencyInstance(Locale("id", "ID")) }

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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
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
                            .background(accentColor.copy(alpha = 0.15f), CircleShape)
                            .border(1.dp, accentColor.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ShowChart,
                            contentDescription = "Stock Trends",
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color = AccentAgedGold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Laju pengurangan unit stok per minggu",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            if (weeklyPoints.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(SecondaryShadowBlackTeal, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Belum ada riwayat transaksi penjualan untuk menghitung tren deplesi.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                val maxDepletion = (weeklyPoints.maxOfOrNull { it.depletedQty } ?: 1).coerceAtLeast(1)

                // Chart Canvas
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(SecondaryShadowBlackTeal, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 16.dp)
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(weeklyPoints) {
                                detectTapGestures { tapOffset ->
                                    val widthPerPage = size.width / (weeklyPoints.size.coerceAtLeast(1))
                                    val index = (tapOffset.x / widthPerPage)
                                        .toInt()
                                        .coerceIn(0, weeklyPoints.size - 1)
                                    selectedPoint = weeklyPoints.getOrNull(index)
                                }
                            }
                    ) {
                        val canvasWidth = size.width
                        val canvasHeight = size.height
                        val pointsCount = weeklyPoints.size
                        val stepX = if (pointsCount > 1) canvasWidth / (pointsCount - 1) else canvasWidth / 2

                        // Draw background horizontal gridlines
                        val gridLines = 4
                        val strokePath = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                        for (i in 0..gridLines) {
                            val y = canvasHeight * (i.toFloat() / gridLines)
                            drawLine(
                                color = BorderGrey.copy(alpha = 0.4f),
                                start = Offset(0f, y),
                                end = Offset(canvasWidth, y),
                                strokeWidth = 1f,
                                pathEffect = strokePath
                            )
                        }

                        // Calculate curve coordinates
                        val offsets = weeklyPoints.mapIndexed { index, point ->
                            val x = if (pointsCount == 1) canvasWidth / 2f else index * stepX
                            val normalizedY = (point.depletedQty.toFloat() / maxDepletion) * animatedProgress
                            val y = canvasHeight - (normalizedY * (canvasHeight - 20f)) - 10f
                            Offset(x, y)
                        }

                        // Draw smooth line chart & area gradient fill
                        if (offsets.size > 1) {
                            val path = Path().apply {
                                moveTo(offsets.first().x, offsets.first().y)
                                for (i in 0 until offsets.size - 1) {
                                    val p1 = offsets[i]
                                    val p2 = offsets[i + 1]
                                    val controlPoint1 = Offset(p1.x + (p2.x - p1.x) / 2f, p1.y)
                                    val controlPoint2 = Offset(p1.x + (p2.x - p1.x) / 2f, p2.y)
                                    cubicTo(controlPoint1.x, controlPoint1.y, controlPoint2.x, controlPoint2.y, p2.x, p2.y)
                                }
                            }

                            // Fill under line
                            val fillPath = Path().apply {
                                addPath(path)
                                lineTo(offsets.last().x, canvasHeight)
                                lineTo(offsets.first().x, canvasHeight)
                                close()
                            }

                            drawPath(
                                path = fillPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(accentColor.copy(alpha = 0.35f), Color.Transparent),
                                    startY = 0f,
                                    endY = canvasHeight
                                )
                            )

                            // Stroke line
                            drawPath(
                                path = path,
                                color = accentColor,
                                style = Stroke(width = 3.dp.toPx())
                            )
                        }

                        // Draw data point dots & bars
                        offsets.forEachIndexed { index, point ->
                            val isSelected = selectedPoint?.weekIndex == weeklyPoints[index].weekIndex

                            // Bar indicator behind dot
                            val barWidth = 12.dp.toPx()
                            val barHeight = (canvasHeight - point.y)
                            drawRoundRect(
                                color = if (isSelected) highlightColor.copy(alpha = 0.25f) else accentColor.copy(alpha = 0.12f),
                                topLeft = Offset(point.x - (barWidth / 2), point.y),
                                size = Size(barWidth, barHeight),
                                cornerRadius = CornerRadius(4.dp.toPx())
                            )

                            // Point Circle
                            drawCircle(
                                color = if (isSelected) highlightColor else accentColor,
                                radius = if (isSelected) 7.dp.toPx() else 4.5.dp.toPx(),
                                center = point
                            )

                            drawCircle(
                                color = ShadowBlack,
                                radius = if (isSelected) 3.5.dp.toPx() else 2.dp.toPx(),
                                center = point
                            )
                        }
                    }
                }

                // Selected Point Detail Tooltip
                selectedPoint?.let { point ->
                    Surface(
                        color = DeepTeal,
                        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor),
                        shape = RoundedCornerShape(10.dp),
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
                                Text(
                                    text = point.weekLabel,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextLight,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${point.totalInvoicesCount} transaksi invoice",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "Deplesi Stok",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextMuted,
                                        fontSize = 10.sp
                                    )
                                    Text(
                                        text = "${point.depletedQty} Pcs",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = AlertGreen,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "Nilai Penjualan",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextMuted,
                                        fontSize = 10.sp
                                    )
                                    Text(
                                        text = rupiahFormat.format(point.invoicedValue),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = AccentAgedGold,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
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
