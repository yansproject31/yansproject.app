package com.yansproject.app.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yansproject.app.ui.AppTab

@Composable
fun BottomNavigationBar(
    currentTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
    canAccessProjects: Boolean,
    canAccessInvoices: Boolean,
    canManageInventory: Boolean,
    isSyncing: Boolean = false,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val handleTabSelect: (AppTab) -> Unit = { tab ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onTabSelected(tab)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Subtle, premium-styled Firestore Sync Progress Indicator during background reconciliation
        AnimatedVisibility(
            visible = isSyncing,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Surface(
                color = Color(0xFF081F20), // Shadow Black Teal
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(10.dp),
                            color = Color(0xFF4FD1C5), // Highlight Soft Cyan
                            strokeWidth = 1.5.dp
                        )
                        Text(
                            text = "Sinkronisasi Cloud Firestore...",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 0.5.sp
                        )
                    }
                    Text(
                        text = "REKONSILIASI OFFLINE",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF4FD1C5).copy(alpha = 0.8f)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = isSyncing,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = Color(0xFF4FD1C5),
                trackColor = Color(0xFF0F3D3E)
            )
        }

        // Glassmorphism Top Golden-Cyan Divider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.5.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                            Color(0xFF4FD1C5).copy(alpha = 0.65f),
                            Color.Transparent
                        )
                    )
                )
        )

        NavigationBar(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("bottom_navigation_bar"),
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            tonalElevation = 0.dp
        ) {
        // Material3 NavigationBarItem colors:
        // Dynamic Accent color when selected, and Light Gray when unselected
        val itemColors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            unselectedIconColor = Color(0xFF8E9A9A),
            selectedTextColor = MaterialTheme.colorScheme.primary,
            unselectedTextColor = Color(0xFF8E9A9A),
            indicatorColor = MaterialTheme.colorScheme.surfaceVariant
        )

        // 1. Dashboard
        val isDashSelected = currentTab == AppTab.DASHBOARD
        val dashScale by animateFloatAsState(
            targetValue = if (isDashSelected) 1.15f else 1.0f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
            label = "dash_scale"
        )
        NavigationBarItem(
            selected = isDashSelected,
            onClick = { handleTabSelect(AppTab.DASHBOARD) },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Dashboard,
                    contentDescription = "Dashboard",
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer { scaleX = dashScale; scaleY = dashScale }
                )
            },
            label = {
                Text(
                    text = "Dashboard",
                    fontSize = 10.sp,
                    fontWeight = if (isDashSelected) FontWeight.Bold else FontWeight.Normal
                )
            },
            colors = itemColors,
            modifier = Modifier.testTag("nav_dashboard")
        )

        // 2. Project
        if (canAccessProjects) {
            val isProjSelected = currentTab == AppTab.PROJECT
            val projScale by animateFloatAsState(
                targetValue = if (isProjSelected) 1.15f else 1.0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
                label = "proj_scale"
            )
            NavigationBarItem(
                selected = isProjSelected,
                onClick = { handleTabSelect(AppTab.PROJECT) },
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.WorkOutline,
                        contentDescription = "Project",
                        modifier = Modifier
                            .size(24.dp)
                            .graphicsLayer { scaleX = projScale; scaleY = projScale }
                    )
                },
                label = {
                    Text(
                        text = "Project",
                        fontSize = 10.sp,
                        fontWeight = if (isProjSelected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                colors = itemColors,
                modifier = Modifier.testTag("nav_project")
            )
        }

        // 3. Stock / Catalog
        val isStockSelected = currentTab == AppTab.STOCK
        val stockScale by animateFloatAsState(
            targetValue = if (isStockSelected) 1.15f else 1.0f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
            label = "stock_scale"
        )
        NavigationBarItem(
            selected = isStockSelected,
            onClick = { handleTabSelect(AppTab.STOCK) },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Inventory2,
                    contentDescription = "Stock",
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer { scaleX = stockScale; scaleY = stockScale }
                )
            },
            label = {
                Text(
                    text = if (canManageInventory) "Stock" else "Catalog",
                    fontSize = 10.sp,
                    fontWeight = if (isStockSelected) FontWeight.Bold else FontWeight.Normal
                )
            },
            colors = itemColors,
            modifier = Modifier.testTag("nav_stock")
        )

        // 4. Invoice
        if (canAccessInvoices) {
            val isInvSelected = currentTab == AppTab.INVOICE
            val invScale by animateFloatAsState(
                targetValue = if (isInvSelected) 1.15f else 1.0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
                label = "inv_scale"
            )
            NavigationBarItem(
                selected = isInvSelected,
                onClick = { handleTabSelect(AppTab.INVOICE) },
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.ReceiptLong,
                        contentDescription = "Invoice",
                        modifier = Modifier
                            .size(24.dp)
                            .graphicsLayer { scaleX = invScale; scaleY = invScale }
                    )
                },
                label = {
                    Text(
                        text = "Invoice",
                        fontSize = 10.sp,
                        fontWeight = if (isInvSelected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                colors = itemColors,
                modifier = Modifier.testTag("nav_invoice")
            )
        }

        // 5. Riwayat (Khusus Owner / Admin)
        if (canManageInventory) {
            val isRiwSelected = currentTab == AppTab.RIWAYAT
            val riwScale by animateFloatAsState(
                targetValue = if (isRiwSelected) 1.15f else 1.0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
                label = "riw_scale"
            )
            NavigationBarItem(
                selected = isRiwSelected,
                onClick = { handleTabSelect(AppTab.RIWAYAT) },
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.History,
                        contentDescription = "Riwayat",
                        modifier = Modifier
                            .size(24.dp)
                            .graphicsLayer { scaleX = riwScale; scaleY = riwScale }
                    )
                },
                label = {
                    Text(
                        text = "Riwayat",
                        fontSize = 10.sp,
                        fontWeight = if (isRiwSelected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                colors = itemColors,
                modifier = Modifier.testTag("nav_riwayat")
            )
        }
    }
}
}
