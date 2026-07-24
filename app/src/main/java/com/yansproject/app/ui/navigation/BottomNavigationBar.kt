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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
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
                            color = Color(0xFFC6A15B), // Aged Gold
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
                            Color(0xFFC6A15B).copy(alpha = 0.65f),
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
        NavigationBarItem(
            selected = currentTab == AppTab.DASHBOARD,
            onClick = { handleTabSelect(AppTab.DASHBOARD) },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Dashboard,
                    contentDescription = "Dashboard",
                    modifier = Modifier.size(24.dp)
                )
            },
            label = {
                Text(
                    text = "Dashboard",
                    fontSize = 10.sp,
                    fontWeight = if (currentTab == AppTab.DASHBOARD) FontWeight.Bold else FontWeight.Normal
                )
            },
            colors = itemColors,
            modifier = Modifier.testTag("nav_dashboard")
        )

        // 2. Project
        if (canAccessProjects) {
            NavigationBarItem(
                selected = currentTab == AppTab.PROJECT,
                onClick = { handleTabSelect(AppTab.PROJECT) },
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.WorkOutline,
                        contentDescription = "Project",
                        modifier = Modifier.size(24.dp)
                    )
                },
                label = {
                    Text(
                        text = "Project",
                        fontSize = 10.sp,
                        fontWeight = if (currentTab == AppTab.PROJECT) FontWeight.Bold else FontWeight.Normal
                    )
                },
                colors = itemColors,
                modifier = Modifier.testTag("nav_project")
            )
        }

        // 3. Stock / Catalog
        NavigationBarItem(
            selected = currentTab == AppTab.STOCK,
            onClick = { handleTabSelect(AppTab.STOCK) },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Inventory2,
                    contentDescription = "Stock",
                    modifier = Modifier.size(24.dp)
                )
            },
            label = {
                Text(
                    text = if (canManageInventory) "Stock" else "Catalog",
                    fontSize = 10.sp,
                    fontWeight = if (currentTab == AppTab.STOCK) FontWeight.Bold else FontWeight.Normal
                )
            },
            colors = itemColors,
            modifier = Modifier.testTag("nav_stock")
        )

        // 4. Invoice
        if (canAccessInvoices) {
            NavigationBarItem(
                selected = currentTab == AppTab.INVOICE,
                onClick = { handleTabSelect(AppTab.INVOICE) },
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.ReceiptLong,
                        contentDescription = "Invoice",
                        modifier = Modifier.size(24.dp)
                    )
                },
                label = {
                    Text(
                        text = "Invoice",
                        fontSize = 10.sp,
                        fontWeight = if (currentTab == AppTab.INVOICE) FontWeight.Bold else FontWeight.Normal
                    )
                },
                colors = itemColors,
                modifier = Modifier.testTag("nav_invoice")
            )
        }

        // 5. Riwayat
        NavigationBarItem(
            selected = currentTab == AppTab.RIWAYAT,
            onClick = { handleTabSelect(AppTab.RIWAYAT) },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.History,
                    contentDescription = "Riwayat",
                    modifier = Modifier.size(24.dp)
                )
            },
            label = {
                Text(
                    text = "Riwayat",
                    fontSize = 10.sp,
                    fontWeight = if (currentTab == AppTab.RIWAYAT) FontWeight.Bold else FontWeight.Normal
                )
            },
            colors = itemColors,
            modifier = Modifier.testTag("nav_riwayat")
        )
    }
}
}
