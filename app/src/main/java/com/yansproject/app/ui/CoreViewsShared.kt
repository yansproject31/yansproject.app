package com.yansproject.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yansproject.app.data.DomainInvoice
import com.yansproject.app.data.DomainProject

// ==========================================
// YANSPROJECT.ID COLOR DNA PALETTE DEFINITION
// ==========================================
val YansPrimary = Color(0xFF0F3D3E)       // Dark Teal
val YansSecondary = Color(0xFF081F20)     // Shadow Black Teal
val YansAccent = Color(0xFFC6A15B)        // Aged Gold (Premium)
val YansHighlight = Color(0xFF4FD1C5)     // Soft Cyan (Interactive/Active)
val YansBackground = Color(0xFF0A0A0A)    // Shadow Black
val YansSurface = Color(0xFF112B2C)       // Dark Teal Surface
val YansCard = Color(0xFF163536)          // Dark Card

/**
 * 1. LEVITATING CARD
 * Custom premium design system card component.
 * Features elevation offset shading, gradient background textures, and subtle aged gold outlines.
 */
@Composable
fun LevitatingCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    borderStroke: BorderStroke? = BorderStroke(1.dp, Brush.linearGradient(listOf(YansAccent.copy(alpha = 0.3f), Color.Transparent))),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = YansAccent.copy(alpha = 0.15f),
                spotColor = YansSecondary
            )
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(YansAccent.copy(alpha = 0.4f), Color.Transparent)
                ),
                shape = RoundedCornerShape(16.dp)
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = YansCard
        )
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.radialGradient(
                        colors = listOf(YansCard, YansSecondary),
                        radius = 1200f
                    )
                )
                .padding(16.dp),
            content = content
        )
    }
}

/**
 * 2. PREMIUM DASHBOARD CARD
 * High-impact metric displaying card.
 * Uses spacious layout density, aged gold headings, and clear indicators.
 */
@Composable
fun DashboardCard(
    title: String,
    value: String,
    subText: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    trendUp: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    LevitatingCard(
        modifier = modifier,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title.uppercase(),
                    color = YansAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    fontFamily = FontFamily.SansSerif
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = value,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.SansSerif
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (trendUp) YansHighlight else YansAccent)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = subText,
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(YansPrimary)
                    .border(0.5.dp, YansAccent.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = YansAccent,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * 3. PROJECT PROGRESS BAR
 * Highly modern custom milestone bar showing multi-stage ERP milestones.
 * Includes dynamic stage annotations and percentage markers.
 */
@Composable
fun ProjectProgressBar(
    project: DomainProject,
    modifier: Modifier = Modifier
) {
    val stages = listOf(
        "Project Dibuat",
        "Invoice",
        "DP Awal",
        "Desain",
        "Produksi",
        "QC",
        "Project Closed"
    )
    val currentIndex = stages.indexOf(project.currentStage).coerceAtLeast(0)
    val progress = (currentIndex + 1).toFloat() / stages.size
    val animatedProgress by animateFloatAsState(targetValue = progress)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Tahap: ${project.currentStage}",
                color = YansHighlight,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${(animatedProgress * 100).toInt()}%",
                color = YansAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        // Progress track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(YansSecondary)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(YansPrimary, YansHighlight)
                        )
                    )
                    .border(0.5.dp, YansAccent.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            )
        }
    }
}

/**
 * 4. INVOICE ROW ITEM
 * Dynamic row for listing invoices, showing paper.id webhook link and n8n syncing indicators.
 */
@Composable
fun InvoiceRowItem(
    invoice: DomainInvoice,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LevitatingCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = invoice.invoiceNumber,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // Webhook State Badge
                    val badgeColor = when (invoice.status) {
                        "LOCAL_SAVED" -> YansAccent.copy(alpha = 0.15f)
                        "Tersinkronisasi" -> YansHighlight.copy(alpha = 0.15f)
                        else -> YansPrimary
                    }
                    val badgeText = when (invoice.status) {
                        "LOCAL_SAVED" -> "n8n Pending"
                        "Tersinkronisasi" -> "Paper.id Ready"
                        else -> invoice.status
                    }
                    val textColor = when (invoice.status) {
                        "LOCAL_SAVED" -> YansAccent
                        "Tersinkronisasi" -> YansHighlight
                        else -> Color.LightGray
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(badgeColor)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = badgeText,
                            color = textColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = invoice.clientName,
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "IDR ${String.format("%,.0f", invoice.totalAmount)}",
                    color = YansAccent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            // Link icon indicator or arrow navigation
            Icon(
                imageVector = if (invoice.status == "Tersinkronisasi") Icons.Default.CheckCircle else Icons.Default.ArrowForward,
                contentDescription = null,
                tint = if (invoice.status == "Tersinkronisasi") YansHighlight else YansAccent,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
