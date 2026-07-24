package com.yansproject.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yansproject.app.ui.theme.*
import com.yansproject.app.ui.theme.*

// Reusable Fintech indicator inside chart panel
@Composable
fun FintechIndicator(label: String, value: String, color: Color) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(color)
            )
            Text(
                text = label,
                fontSize = 10.sp,
                color = TextSecondary,
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(
            text = value,
            fontSize = 13.sp,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
    }
}

// 100% COMPLETE ULTIMATE REVOLUTIONARY DONUT CHART COMPONENT
@Composable
fun FinancialDonutChart(
    revenue: Double,
    expense: Double,
    profit: Double,
    formatRupiah: (Double) -> String,
    modifier: Modifier = Modifier
) {
    val total = revenue + expense
    val incomeRatio = if (total > 0) (revenue / total).toFloat() else 0.5f

    // Smooth entry rotation & sweeping animation
    var animationTriggered by remember { mutableStateOf(false) }
    LaunchedEffect(revenue, expense) {
        animationTriggered = true
    }

    val animProgress by animateFloatAsState(
        targetValue = if (animationTriggered) 1f else 0f,
        animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
        label = "FinancialDonutChartAnim"
    )

    val incomeSweep = incomeRatio * 360f * animProgress
    val expenseSweep = (1f - incomeRatio) * 360f * animProgress

    Card(
        modifier = modifier
            .fillMaxWidth()
            .glassCard(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 12.dp.toPx()
                    val sizeMin = size.minDimension - strokeWidth
                    val rectSize = Size(sizeMin, sizeMin)
                    val offset = strokeWidth / 2

                    // Unfilled background track
                    drawArc(
                        color = DarkTealBase,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(offset, offset),
                        size = rectSize,
                        style = Stroke(width = strokeWidth)
                    )

                    // Draw Inflow (Revenue) Track with beautiful SweepGradient
                    rotate(-90f) {
                        drawArc(
                            brush = Brush.sweepGradient(
                                colors = listOf(NeonCyan, AgedGoldLight, NeonCyan),
                                center = center
                            ),
                            startAngle = 0f,
                            sweepAngle = incomeSweep,
                            useCenter = false,
                            topLeft = Offset(offset, offset),
                            size = rectSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }

                    // Draw Outflow (Expense) Track with vibrant red Gradient
                    rotate(-90f + incomeSweep) {
                        drawArc(
                            brush = Brush.sweepGradient(
                                colors = listOf(ErrorRed, Color(0xFFFF6B6B), ErrorRed),
                                center = center
                            ),
                            startAngle = 0f,
                            sweepAngle = expenseSweep,
                            useCenter = false,
                            topLeft = Offset(offset, offset),
                            size = rectSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                }

                // Inner Stats Layer
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "PROFIT RATE",
                        fontSize = 8.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                    val percent = if (revenue > 0) ((profit / revenue) * 100).toInt() else 0
                    Text(
                        text = "$percent%",
                        fontSize = 16.sp,
                        color = PrimaryGold,
                        fontWeight = FontWeight.Black,
                        style = androidx.compose.ui.text.TextStyle(
                            shadow = androidx.compose.ui.graphics.Shadow(
                                color = NeonCyan,
                                blurRadius = 8f
                            )
                        )
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1.0f)
                    .padding(start = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FintechIndicator(
                    label = "Pemasukan (Inflow)",
                    value = formatRupiah(revenue),
                    color = NeonCyan
                )
                FintechIndicator(
                    label = "Pengeluaran (Outflow)",
                    value = formatRupiah(expense),
                    color = ErrorRed
                )
                FintechIndicator(
                    label = "Saldo Bersih",
                    value = formatRupiah(profit),
                    color = AgedGoldLight
                )
            }
        }
    }
}
