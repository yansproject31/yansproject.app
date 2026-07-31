package com.yansproject.app.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import com.yansproject.app.R
import com.yansproject.app.ui.AppTab
import com.yansproject.app.ui.ConnectivityStatusBadge
import com.yansproject.app.ui.MainViewModel
import com.yansproject.app.ui.Screen
import com.yansproject.app.ui.safeNavigate
import com.yansproject.app.ui.theme.*

/**
 * MainScaffold: The universal reusable framework wrapper for all major ERP modules.
 * Incorporates: Top App Bar, Bottom Navigation, Rotating FAB, and Dynamic Action Overlay Menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(
    viewModel: MainViewModel,
    navController: NavHostController,
    currentTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
    showGlobalSearch: () -> Unit,
    showNotifications: () -> Unit,
    showLogout: () -> Unit,
    unreadNotificationsCount: Int,
    isOwner: Boolean,
    canAccessProjects: Boolean,
    canAccessInvoices: Boolean,
    canManageInventory: Boolean,
    snackbarHostState: SnackbarHostState,
    content: @Composable (PaddingValues) -> Unit
) {
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val savedFileEvent by viewModel.savedFileEvent.collectAsStateWithLifecycle()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isSubRoute = currentRoute != null && currentRoute !in listOf(
        Routes.Dashboard,
        Routes.Project,
        Routes.Stock,
        Routes.Invoice,
        Routes.History
    )
    val hideHeaderAndBottomBar = currentTab == AppTab.KITAB || currentTab == AppTab.SETTINGS || isSubRoute

    if (savedFileEvent != null) {
        val event = savedFileEvent!!
        com.yansproject.app.ui.components.FileSavedSuccessDialog(
            file = event.file,
            folder = event.folder,
            title = event.title,
            onDismiss = { viewModel.dismissSavedFileDialog() }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState) { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = Color.White,
                        actionColor = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .padding(12.dp)
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                    )
                }
            },
            topBar = {
                if (!hideHeaderAndBottomBar) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
                                    )
                                )
                            )
                    ) {
                        Surface(
                            color = Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .height(76.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // YANSPROJECT.ID Brand Title & Logo with Gold Aura
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onTabSelected(AppTab.KITAB) }
                                        .padding(vertical = 4.dp, horizontal = 2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                Brush.radialGradient(
                                                    colors = listOf(
                                                        AgedGold.copy(alpha = 0.25f),
                                                        Color(0xFF163536)
                                                    )
                                                )
                                            )
                                            .border(
                                                width = 1.dp,
                                                brush = Brush.linearGradient(
                                                    colors = listOf(AgedGold, HighlightSoftCyan)
                                                ),
                                                shape = RoundedCornerShape(10.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(
                                            painter = painterResource(id = R.drawable.ic_logo),
                                            contentDescription = "Logo",
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "YANSPROJECT.ID",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.primary,
                                            letterSpacing = 0.8.sp,
                                            modifier = Modifier.testTag("yansproject_header")
                                        )

                                        ConnectivityStatusBadge(
                                            isOnline = isOnline,
                                            modifier = Modifier.testTag("connectivity_status_badge")
                                        )
                                    }
                                }

                                // Glassmorphic Action Buttons
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Search
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0x25163536))
                                            .border(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                                    ) {
                                        IconButton(
                                            onClick = showGlobalSearch,
                                            modifier = Modifier.fillMaxSize().testTag("global_search_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Search,
                                                contentDescription = "Global Search",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    // Notifications
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0x25163536))
                                            .border(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                                    ) {
                                        IconButton(
                                            onClick = showNotifications,
                                            modifier = Modifier.fillMaxSize().testTag("notification_bell_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Notifications,
                                                contentDescription = "Notification Center",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        if (unreadNotificationsCount > 0) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(top = 4.dp, end = 4.dp)
                                                    .size(14.dp)
                                                    .background(AlertRed, RoundedCornerShape(50))
                                                    .border(0.8.dp, ShadowBlack, RoundedCornerShape(50)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = if (unreadNotificationsCount > 99) "99+" else "$unreadNotificationsCount",
                                                    fontSize = 7.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }

                                    // Settings / Profile
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0x25163536))
                                            .border(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                                    ) {
                                        IconButton(
                                            onClick = { onTabSelected(AppTab.SETTINGS) },
                                            modifier = Modifier.fillMaxSize().testTag("settings_button")
                                        ) {
                                            Icon(
                                                imageVector = if (isOwner) Icons.Outlined.Settings else Icons.Outlined.Person,
                                                contentDescription = if (isOwner) "Settings" else "Profile",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    // Logout
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0x25163536))
                                            .border(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                                    ) {
                                        IconButton(
                                            onClick = showLogout,
                                            modifier = Modifier.fillMaxSize().testTag("logout_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Logout,
                                                contentDescription = "Logout",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Luxury Golden-Cyan Gradient Divider Line
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.5.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                            HighlightSoftCyan.copy(alpha = 0.6f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                    }
                }
            },
            bottomBar = {
                if (!hideHeaderAndBottomBar) {
                    BottomNavigationBar(
                        currentTab = currentTab,
                        onTabSelected = onTabSelected,
                        canAccessProjects = canAccessProjects,
                        canAccessInvoices = canAccessInvoices,
                        canManageInventory = canManageInventory,
                        isSyncing = isSyncing
                    )
                }
            },
            content = content
        )
    }
}
