package com.yansproject.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.yansproject.app.data.FirebaseSyncManager
import com.yansproject.app.data.UserRole
import com.yansproject.app.ui.*
import com.yansproject.app.ui.theme.*

class MainActivity : androidx.fragment.app.FragmentActivity() {
  override fun onNewIntent(intent: android.content.Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        ErrorBoundaryWrapper {
          var showSplash by remember { mutableStateOf(true) }
          val viewModel: MainViewModel = viewModel()

          val context = androidx.compose.ui.platform.LocalContext.current
          val activity = remember(context) {
              var ctx = context
              while (ctx is android.content.ContextWrapper) {
                  if (ctx is MainActivity) break
                  ctx = ctx.baseContext
              }
              ctx as? MainActivity
          }
          val isLoggedIn by viewModel.isLoggedIn.collectAsState()
          LaunchedEffect(isLoggedIn, activity?.intent) {
            if (isLoggedIn && activity != null) {
              activity.intent.getStringExtra("TARGET_TAB")?.let { target ->
                try {
                  val tab = AppTab.valueOf(target)
                  viewModel.setTab(tab)
                  activity.intent.removeExtra("TARGET_TAB")
                } catch (e: Exception) {
                  android.util.Log.e("MainActivity", "Error parsing TARGET_TAB value '$target': ${e.message}", e)
                }
              }
            }
          }

          LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(1500) // 1.5 seconds splash duration
            showSplash = false
          }

          Crossfade(
            targetState = showSplash,
            animationSpec = tween(600),
            label = "SplashTransition"
          ) { isSplash ->
            if (isSplash) {
              SplashScreen()
            } else {
              val isLoggedIn by viewModel.isLoggedIn.collectAsState()
              val currentTab by viewModel.currentTab.collectAsState()

              val securityPrefs = remember { context.getSharedPreferences("yans_security_prefs", android.content.Context.MODE_PRIVATE) }
              val appLockEnabled = remember { securityPrefs.getBoolean("app_lock_enabled", false) || securityPrefs.getBoolean("pin_lock_enabled", false) }
              val biometricEnabled = remember { securityPrefs.getBoolean("biometric_enabled", false) }
              var isAppUnlocked by remember { mutableStateOf(!(appLockEnabled || biometricEnabled)) }

              LaunchedEffect(isLoggedIn) {
                if (!isLoggedIn) {
                  isAppUnlocked = false
                } else {
                  val latestAppLock = securityPrefs.getBoolean("app_lock_enabled", false) || securityPrefs.getBoolean("pin_lock_enabled", false)
                  val latestBiometric = securityPrefs.getBoolean("biometric_enabled", false)
                  isAppUnlocked = !(latestAppLock || latestBiometric)
                }
              }

              Crossfade(
                targetState = isLoggedIn,
                animationSpec = tween(400),
                modifier = Modifier.fillMaxSize(),
                label = "LoginTransition"
              ) { loggedIn ->
                if (!loggedIn) {
                  LoginScreen(
                    viewModel = viewModel,
                    onLoginSuccess = { /* Handled reactively by Flow */ }
                  )
                } else {
                  if (!isAppUnlocked) {
                    AppLockScreen(
                      isBiometricEnabled = biometricEnabled,
                      onUnlockSuccess = { isAppUnlocked = true },
                      viewModel = viewModel
                    )
                  } else {
                    SessionTimeoutWrapper(
                      isLoggedIn = true
                    ) {
                      com.yansproject.app.ui.PermissionGuard {
                        MainAppContainer(
                          viewModel = viewModel,
                          currentTab = currentTab
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
}

@Composable
fun SplashScreen() {
  val alpha = remember { androidx.compose.animation.core.Animatable(0f) }
  LaunchedEffect(Unit) {
    alpha.animateTo(
      targetValue = 1f,
      animationSpec = tween(1000) // Smooth 1s fade-in
    )
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(com.yansproject.app.ui.theme.YansCanvasGradient),
    contentAlignment = androidx.compose.ui.Alignment.Center
  ) {
    Column(
      horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
      modifier = Modifier.graphicsLayer { this.alpha = alpha.value }
    ) {
      Icon(
        painter = painterResource(id = R.drawable.ic_logo),
        contentDescription = "YANSPROJECT.ID Logo",
        tint = AgedGold,
        modifier = Modifier.size(130.dp)
      )
      Spacer(modifier = Modifier.height(24.dp))
      Text(
        text = "YANSPROJECT.ID",
        fontSize = 28.sp,
        fontWeight = FontWeight.ExtraBold,
        color = AgedGold,
        letterSpacing = 2.sp
      )
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = "Internal Operations Portal",
        fontSize = 12.sp,
        color = TextMuted,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.sp
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContainer(
  viewModel: MainViewModel,
  currentTab: AppTab
) {
  var showGlobalSearchDialog by remember { mutableStateOf(false) }
  var showLogoutConfirmDialog by remember { mutableStateOf(false) }
  var showNotificationDialog by remember { mutableStateOf(false) }
  val notifications by viewModel.notifications.collectAsState()
  val unreadCount = remember(notifications) { notifications.count { !it.isRead } }
  val currentUser by FirebaseSyncManager.currentUser.collectAsState()
  val userRole = currentUser?.role ?: UserRole.MEMBER
  val isOwner = userRole.hasFullERPChainAccess()
  val canAccessDashboard = userRole.canAccessFinancials()
  val canAccessProjects = userRole.canManageProjects()
  val canAccessInvoices = userRole.canManageInvoices()

  // Back button navigation implementation (Android Standards)
  val context = androidx.compose.ui.platform.LocalContext.current
  var lastBackTime by remember { mutableStateOf(0L) }

  val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
  val coroutineScope = rememberCoroutineScope()

  LaunchedEffect(Unit) {
    viewModel.snackbarEvent.collect { event ->
      val result = snackbarHostState.showSnackbar(
        message = event.message,
        actionLabel = event.actionLabel,
        duration = androidx.compose.material3.SnackbarDuration.Long
      )
      if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
        event.onAction?.invoke()
      }
    }
  }

  BackHandler(enabled = true) {
    if (currentTab != AppTab.DASHBOARD) {
      viewModel.setTab(AppTab.DASHBOARD)
    } else {
      val now = System.currentTimeMillis()
      if (now - lastBackTime < 2000) {
        (context as? android.app.Activity)?.finish()
      } else {
        lastBackTime = now
        Toast.makeText(context, "Tekan sekali lagi untuk keluar aplikasi.", Toast.LENGTH_SHORT).show()
      }
    }
  }

  // Restrict navigation bounds adaptively
  LaunchedEffect(currentUser, currentTab) {
    val role = currentUser?.role ?: UserRole.MEMBER
    val isOwner = role == UserRole.OWNER
    if (!isOwner) {
      if (currentTab == AppTab.PROJECT || currentTab == AppTab.INVOICE) {
        viewModel.setTab(AppTab.DASHBOARD)
      }
    }
  }

  val navController = rememberNavController()

  // Keep state in sync from Tab to Navigation Host
  LaunchedEffect(currentTab) {
      val targetRoute = when (currentTab) {
          AppTab.DASHBOARD -> Screen.Dashboard.route
          AppTab.PROJECT -> Screen.Project.route
          AppTab.STOCK -> Screen.Stock.route
          AppTab.INVOICE -> Screen.Invoice.route
          AppTab.RIWAYAT -> Screen.Riwayat.route
          AppTab.SETTINGS -> "settings"
          AppTab.KITAB -> Screen.KitabDigital.route
      }
      val currentRoute = navController.currentBackStackEntry?.destination?.route

      val isAlreadyInTab = when (currentTab) {
          AppTab.SETTINGS -> currentRoute == "settings" || currentRoute?.startsWith("settings") == true || currentRoute == "admin_profile" || currentRoute == "app_settings" || currentRoute == "app_info" || currentRoute == "system_health" || currentRoute == "telemetry" || currentRoute == "security_log"
          AppTab.DASHBOARD -> currentRoute == Screen.Dashboard.route
          AppTab.PROJECT -> currentRoute == Screen.Project.route || currentRoute == "add_project" || currentRoute == "custom_project_main" || currentRoute == "custom_project_create" || currentRoute?.startsWith("custom_project_detail") == true
          AppTab.STOCK -> currentRoute == Screen.Stock.route || currentRoute == "add_stock" || currentRoute == "instant_checkout" || currentRoute == "luxury_cart" || currentRoute == "ajib_return"
          AppTab.INVOICE -> currentRoute == Screen.Invoice.route || currentRoute == "add_invoice"
          AppTab.RIWAYAT -> currentRoute == Screen.Riwayat.route || currentRoute == "global_ledger" || currentRoute == "income_ledger" || currentRoute == "expense_ledger"
          AppTab.KITAB -> currentRoute == Screen.KitabDigital.route
      }

      if (!isAlreadyInTab) {
          try {
              navController.navigate(targetRoute) {
                  popUpTo(Screen.Dashboard.route) {
                      saveState = true
                  }
                  launchSingleTop = true
                  restoreState = true
              }
          } catch (e: Exception) {
              navController.safeNavigate(targetRoute)
          }
      }
  }

  // Keep currentTab in sync with NavController back stack changes (e.g. popBackStack, hardware back, etc.)
  DisposableEffect(navController) {
      val listener = androidx.navigation.NavController.OnDestinationChangedListener { _, destination, _ ->
          val route = destination.route ?: return@OnDestinationChangedListener
          val mappedTab = when {
              route == Screen.Dashboard.route -> AppTab.DASHBOARD
              route == Screen.Project.route -> AppTab.PROJECT
              route == Screen.Stock.route -> AppTab.STOCK
              route == Screen.Invoice.route -> AppTab.INVOICE
              route == Screen.Riwayat.route -> AppTab.RIWAYAT
              route == Screen.KitabDigital.route -> AppTab.KITAB
              route == "settings" || route.startsWith("settings/") || route.startsWith("settings_") -> AppTab.SETTINGS
              else -> null
          }
          if (mappedTab != null && mappedTab != viewModel.currentTab.value) {
              viewModel.setTab(mappedTab)
          }
      }
      navController.addOnDestinationChangedListener(listener)
      onDispose {
          navController.removeOnDestinationChangedListener(listener)
      }
  }

  com.yansproject.app.ui.navigation.MainScaffold(
    viewModel = viewModel,
    navController = navController,
    currentTab = currentTab,
    onTabSelected = { viewModel.setTab(it) },
    showGlobalSearch = { showGlobalSearchDialog = true },
    showNotifications = { showNotificationDialog = true },
    showLogout = { showLogoutConfirmDialog = true },
    unreadNotificationsCount = unreadCount,
    isOwner = isOwner,
    canAccessProjects = canAccessProjects,
    canAccessInvoices = canAccessInvoices,
    canManageInventory = userRole.canManageInventory(),
    snackbarHostState = snackbarHostState
  ) { paddingValues ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(com.yansproject.app.ui.theme.YansCanvasGradient)
        .padding(if (currentTab == AppTab.KITAB || currentTab == AppTab.SETTINGS) PaddingValues(0.dp) else paddingValues)
    ) {
      com.yansproject.app.ui.navigation.YansNavHost(
          navController = navController,
          viewModel = viewModel
      )
    }
  }

  if (showGlobalSearchDialog) {
    GlobalSearchDialog(
      viewModel = viewModel,
      isOwner = isOwner,
      onDismiss = { showGlobalSearchDialog = false }
    )
  }

  if (showLogoutConfirmDialog) {
    AlertDialog(
      onDismissRequest = { showLogoutConfirmDialog = false },
      title = {
        Text(
          text = "Konfirmasi Logout",
          fontSize = 16.sp,
          fontWeight = FontWeight.Bold,
          color = AgedGold
        )
      },
      text = {
        Text(
          text = "Apakah Anda yakin ingin keluar dari akun ini?",
          fontSize = 13.sp,
          color = Color.White
        )
      },
      confirmButton = {
        Button(
          onClick = {
            showLogoutConfirmDialog = false
            viewModel.logout()
          },
          colors = ButtonDefaults.buttonColors(containerColor = AlertRed, contentColor = Color.White),
          shape = RoundedCornerShape(8.dp)
        ) {
          Text("Logout", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(
          onClick = { showLogoutConfirmDialog = false },
          colors = ButtonDefaults.textButtonColors(contentColor = TextMuted)
        ) {
          Text("Batal", fontSize = 12.sp)
        }
      },
      containerColor = CardGrey
    )
  }

  if (showNotificationDialog) {
    NotificationCenterDialog(
      notifications = notifications,
      isOwner = userRole == UserRole.OWNER,
      onDismiss = { showNotificationDialog = false },
      onMarkRead = { id -> viewModel.markNotificationAsRead(id) },
      onMarkAllRead = { viewModel.markAllNotificationsAsRead() },
      onClearAll = { viewModel.clearAllNotifications() },
      onDeleteNotification = { id -> viewModel.deleteNotification(id) },
      onNavigate = { tab -> viewModel.setTab(tab) },
      triggerPromoBroadcast = { title, msg ->
        viewModel.triggerNotification(
          title = title,
          message = msg,
          category = "Sistem",
          targetTab = "INVOICE",
          roleTarget = "MEMBER",
          userId = "ALL",
          priority = "HIGH",
          createdBy = currentUser?.email ?: "OWNER"
        )
      }
    )
  }
}

@Composable
fun GlobalSearchDialog(
  viewModel: MainViewModel,
  isOwner: Boolean,
  onDismiss: () -> Unit
) {
  var query by remember { mutableStateOf("") }
  var searchTabMode by remember { mutableStateOf(0) } // 0 = Global (Lokal), 1 = Produksi (Firestore Snapshot)

  val catalogs by viewModel.allCatalogs.collectAsState()
  val projects by viewModel.allProjects.collectAsState()
  val invoices by viewModel.allInvoices.collectAsState()

  // Firestore Search States
  val prodSeries by viewModel.productionFilterSeries.collectAsState()
  val prodCode by viewModel.productionFilterCode.collectAsState()
  val prodColor by viewModel.productionFilterColor.collectAsState()
  val prodStatus by viewModel.productionFilterStatus.collectAsState()
  val prodResults by viewModel.productionSearchResults.collectAsState()

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Surface(
      modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)
        .border(1.dp, AgedGold.copy(alpha = 0.35f), RoundedCornerShape(20.dp)),
      shape = RoundedCornerShape(20.dp),
      color = SecondaryShadowBlackTeal
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(16.dp)
      ) {
        // Search Header Bar
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(
            text = "Pencarian Realtime YANS",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = AgedGold
          )
          IconButton(onClick = onDismiss) {
            Icon(imageVector = Icons.Default.Close, contentDescription = "Tutup", tint = AgedGold)
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Dual-Tab Navigation Bar
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .background(ShadowBlack, RoundedCornerShape(8.dp))
            .padding(4.dp),
          horizontalArrangement = Arrangement.SpaceEvenly
        ) {
          Box(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(6.dp))
              .background(if (searchTabMode == 0) AgedGold else Color.Transparent)
              .clickable { searchTabMode = 0 }
              .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = "Pencarian Lokal",
              color = if (searchTabMode == 0) ShadowBlack else Color.White,
              fontWeight = FontWeight.Bold,
              fontSize = 12.sp
            )
          }
          Box(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(6.dp))
              .background(if (searchTabMode == 1) AgedGold else Color.Transparent)
              .clickable { searchTabMode = 1 }
              .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = "Produksi (Firestore)",
              color = if (searchTabMode == 1) ShadowBlack else Color.White,
              fontWeight = FontWeight.Bold,
              fontSize = 12.sp
            )
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (searchTabMode == 0) {
          // --- Tab 0: Local Global Search ---
          // Search Input field
          OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Cari WA, invoice, nama klien, atau catalog...", color = TextMuted, fontSize = 13.sp) },
            leadingIcon = { Icon(imageVector = Icons.Outlined.Search, contentDescription = "Cari", tint = AgedGold) },
            modifier = Modifier.fillMaxWidth().testTag("global_search_input"),
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
              focusedTextColor = Color.White,
              unfocusedTextColor = Color.White,
              focusedBorderColor = AgedGold,
              unfocusedBorderColor = BorderGrey
            )
          )

          Spacer(modifier = Modifier.height(16.dp))

          // Match Results list
          LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            if (query.isNotEmpty()) {
              // 1. Catalogs section
              val filteredCatalogs = catalogs.filter { it.nama_catalog.contains(query, ignoreCase = true) }
              if (filteredCatalogs.isNotEmpty()) {
                item {
                  Text(text = "CATALOG & STOCK", fontWeight = FontWeight.Bold, color = AgedGold, fontSize = 12.sp)
                  Spacer(modifier = Modifier.height(4.dp))
                }
                items(filteredCatalogs) { cat ->
                  Card(
                    modifier = Modifier
                      .fillMaxWidth()
                      .clickable {
                        viewModel.setTab(AppTab.STOCK)
                        onDismiss()
                      },
                    colors = CardDefaults.cardColors(containerColor = CardGrey)
                  ) {
                    Row(
                      modifier = Modifier.padding(12.dp),
                      horizontalArrangement = Arrangement.spacedBy(10.dp),
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Icon(imageVector = Icons.Outlined.Inventory, contentDescription = "Cat", tint = AgedGold)
                      Column {
                        Text(text = cat.nama_catalog, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(text = cat.deskripsi, fontSize = 11.sp, color = TextMuted)
                      }
                    }
                  }
                }
              }

              // 2. Projects section (Owner only)
              if (isOwner) {
                val filteredProjects = projects.filter {
                  it.projectName.contains(query, ignoreCase = true) ||
                  it.clientName.contains(query, ignoreCase = true) ||
                  it.clientPhone.contains(query, ignoreCase = true)
                }
                if (filteredProjects.isNotEmpty()) {
                  item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "PROJECTS", fontWeight = FontWeight.Bold, color = AgedGold, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                  }
                  items(filteredProjects) { proj ->
                    Card(
                      modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                          viewModel.setTab(AppTab.PROJECT)
                          onDismiss()
                        },
                      colors = CardDefaults.cardColors(containerColor = CardGrey)
                    ) {
                      Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                      ) {
                        Icon(imageVector = Icons.Outlined.WorkOutline, contentDescription = "Proj", tint = AgedGold)
                        Column {
                          Text(text = proj.projectName, fontWeight = FontWeight.Bold, color = Color.White)
                          Text(text = "Klien: ${proj.clientName} (${proj.clientPhone})", fontSize = 11.sp, color = TextMuted)
                        }
                      }
                    }
                  }
                }
              }

              // 3. Invoices / History section
              val filteredInvoices = invoices.filter {
                it.invoiceNumber.contains(query, ignoreCase = true) ||
                it.clientName.contains(query, ignoreCase = true) ||
                it.clientPhone.contains(query, ignoreCase = true)
              }
              if (filteredInvoices.isNotEmpty()) {
                item {
                  Spacer(modifier = Modifier.height(8.dp))
                  Text(text = "INVOICES & TRANSAKSI", fontWeight = FontWeight.Bold, color = AgedGold, fontSize = 12.sp)
                  Spacer(modifier = Modifier.height(4.dp))
                }
                items(filteredInvoices) { inv ->
                  Card(
                    modifier = Modifier
                      .fillMaxWidth()
                      .clickable {
                        if (isOwner) viewModel.setTab(AppTab.INVOICE) else viewModel.setTab(AppTab.DASHBOARD)
                        onDismiss()
                      },
                    colors = CardDefaults.cardColors(containerColor = CardGrey)
                  ) {
                    Row(
                      modifier = Modifier.padding(12.dp),
                      horizontalArrangement = Arrangement.spacedBy(10.dp),
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Icon(imageVector = Icons.Outlined.ReceiptLong, contentDescription = "Inv", tint = AgedGold)
                      Column {
                        Text(text = inv.invoiceNumber, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(text = "Klien: ${inv.clientName} | Status: ${inv.status}", fontSize = 11.sp, color = TextMuted)
                      }
                    }
                  }
                }
              }
            } else {
              item {
                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(40.dp),
                  contentAlignment = Alignment.Center
                ) {
                  Text(text = "Ketik sesuatu untuk memulai pencarian realtime...", color = TextMuted, fontSize = 13.sp)
                }
              }
            }
          }
        } else {
          // --- Tab 1: Realtime Production Search & Compound Filtering (Firestore) ---
          Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              OutlinedTextField(
                value = prodSeries,
                onValueChange = { viewModel.productionFilterSeries.value = it },
                label = { Text("Nama Series", color = TextMuted, fontSize = 11.sp) },
                placeholder = { Text("misal: RAHASIA REALITA", fontSize = 11.sp, color = TextMuted) },
                modifier = Modifier.weight(1f).testTag("prod_filter_series"),
                shape = RoundedCornerShape(8.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                  focusedTextColor = Color.White,
                  unfocusedTextColor = Color.White,
                  focusedBorderColor = AgedGold,
                  unfocusedBorderColor = BorderGrey
                )
              )
              OutlinedTextField(
                value = prodCode,
                onValueChange = { viewModel.productionFilterCode.value = it },
                label = { Text("Kode (Batch)", color = TextMuted, fontSize = 11.sp) },
                placeholder = { Text("misal: AQ-RR-01", fontSize = 11.sp, color = TextMuted) },
                modifier = Modifier.weight(1f).testTag("prod_filter_code"),
                shape = RoundedCornerShape(8.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                  focusedTextColor = Color.White,
                  unfocusedTextColor = Color.White,
                  focusedBorderColor = AgedGold,
                  unfocusedBorderColor = BorderGrey
                )
              )
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              OutlinedTextField(
                value = prodColor,
                onValueChange = { viewModel.productionFilterColor.value = it },
                label = { Text("Varian / Warna", color = TextMuted, fontSize = 11.sp) },
                placeholder = { Text("misal: Dark Teal", fontSize = 11.sp, color = TextMuted) },
                modifier = Modifier.weight(1f).testTag("prod_filter_color"),
                shape = RoundedCornerShape(8.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                  focusedTextColor = Color.White,
                  unfocusedTextColor = Color.White,
                  focusedBorderColor = AgedGold,
                  unfocusedBorderColor = BorderGrey
                )
              )
              OutlinedTextField(
                value = prodStatus,
                onValueChange = { viewModel.productionFilterStatus.value = it },
                label = { Text("Status Stock", color = TextMuted, fontSize = 11.sp) },
                placeholder = { Text("misal: Selesai QC", fontSize = 11.sp, color = TextMuted) },
                modifier = Modifier.weight(1f).testTag("prod_filter_status"),
                shape = RoundedCornerShape(8.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                  focusedTextColor = Color.White,
                  unfocusedTextColor = Color.White,
                  focusedBorderColor = AgedGold,
                  unfocusedBorderColor = BorderGrey
                )
              )
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Button(
                onClick = { viewModel.clearProductionFilters() },
                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray, contentColor = Color.White),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f).testTag("prod_filter_clear")
              ) {
                Icon(imageVector = Icons.Outlined.DeleteSweep, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Reset Filter", fontSize = 12.sp)
              }

              Button(
                onClick = { viewModel.populateSampleProductionData() },
                colors = ButtonDefaults.buttonColors(containerColor = AgedGold, contentColor = ShadowBlack),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f).testTag("prod_populate_btn")
              ) {
                Icon(imageVector = Icons.Outlined.CloudDownload, contentDescription = "Populate", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Populasi Data", fontSize = 12.sp)
              }
            }
          }

          Spacer(modifier = Modifier.height(12.dp))

          // Live Realtime Firestore Results List
          LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            if (prodResults.isEmpty()) {
              item {
                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(40.dp),
                  contentAlignment = Alignment.Center
                ) {
                  Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                      text = "Tidak ada hasil / Database Firestore kosong.",
                      color = TextMuted,
                      fontSize = 13.sp,
                      textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                      text = "Gunakan tombol 'Populasi Data' untuk inisialisasi data Firestore.",
                      color = TextMuted.copy(alpha = 0.7f),
                      fontSize = 11.sp,
                      textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                  }
                }
              }
            } else {
              item {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = "HASIL PENCARIAN FIRESTORE LIVE (${prodResults.size})",
                    fontWeight = FontWeight.Bold,
                    color = HighlightSoftCyan,
                    fontSize = 12.sp
                  )
                  Text(
                    text = "Snapshot Active",
                    color = Color.Green,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                  )
                }
                Spacer(modifier = Modifier.height(4.dp))
              }

              items(prodResults, key = { it.id }) { item ->
                Card(
                  modifier = Modifier.fillMaxWidth(),
                  colors = CardDefaults.cardColors(containerColor = CardGrey),
                  border = BorderStroke(1.dp, AgedGold.copy(alpha = 0.15f))
                ) {
                  Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Text(
                        text = item.seriesName,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                      )
                      Box(
                        modifier = Modifier
                          .clip(RoundedCornerShape(4.dp))
                          .background(AgedGold.copy(alpha = 0.12f))
                          .border(1.dp, AgedGold.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                          .padding(horizontal = 6.dp, vertical = 2.dp)
                      ) {
                        Text(
                          text = item.code,
                          color = AgedGold,
                          fontSize = 11.sp,
                          fontWeight = FontWeight.Bold
                        )
                      }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                      ) {
                        Icon(
                          imageVector = Icons.Outlined.Palette,
                          contentDescription = "Color",
                          tint = HighlightSoftCyan,
                          modifier = Modifier.size(14.dp)
                        )
                        Text(
                          text = "Varian: ${item.color}",
                          color = TextLight,
                          fontSize = 12.sp
                        )
                      }

                      Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                      ) {
                        Icon(
                          imageVector = Icons.Outlined.Category,
                          contentDescription = "Status",
                          tint = HighlightSoftCyan,
                          modifier = Modifier.size(14.dp)
                        )
                        Text(
                          text = "Status: ${item.stockStatus}",
                          color = TextLight,
                          fontSize = 12.sp,
                          fontWeight = FontWeight.SemiBold
                        )
                      }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Text(
                        text = "Kuantitas: ${item.quantity} pcs",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                      )
                      Text(
                        text = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(item.timestamp)),
                        color = TextMuted,
                        fontSize = 10.sp
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
fun NotificationCenterDialog(
  notifications: List<AppSettings.AppNotification>,
  isOwner: Boolean,
  onDismiss: () -> Unit,
  onMarkRead: (String) -> Unit,
  onMarkAllRead: () -> Unit,
  onClearAll: () -> Unit,
  onDeleteNotification: (String) -> Unit,
  onNavigate: (AppTab) -> Unit,
  triggerPromoBroadcast: (String, String) -> Unit
) {
  var selectedCategory by remember { mutableStateOf("Semua") }
  val categories = listOf("Semua", "Pesanan", "Stock", "Pembayaran", "Sistem")
  var showPromoBroadcastDialog by remember { mutableStateOf(false) }
  var selectedSystemNotif by remember { mutableStateOf<AppSettings.AppNotification?>(null) }
  val context = androidx.compose.ui.platform.LocalContext.current

  val activeList = remember(notifications) {
    notifications.filter { !it.isArchived }
  }

  val unreadCount = remember(activeList) {
    activeList.count { !it.isRead }
  }

  val filteredList = remember(activeList, selectedCategory) {
    if (selectedCategory == "Semua") {
      activeList
    } else {
      activeList.filter { item ->
        val catUpper = item.category.uppercase()
        when (selectedCategory) {
          "Pesanan" -> catUpper in setOf("PESANAN", "INVOICE", "ORDER")
          "Stock" -> catUpper in setOf("STOCK", "STOK")
          "Pembayaran" -> catUpper in setOf("PEMBAYARAN", "PAYMENT")
          "Sistem" -> catUpper in setOf("SISTEM", "SYSTEM", "PROMOTION", "PROMO")
          else -> true
        }
      }
    }
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .fillMaxHeight(0.85f)
        .padding(16.dp),
      shape = RoundedCornerShape(20.dp),
      colors = CardDefaults.cardColors(containerColor = SecondaryShadowBlackTeal),
      border = androidx.compose.foundation.BorderStroke(1.dp, AgedGold.copy(alpha = 0.35f))
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(16.dp)
      ) {
        // Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Outlined.NotificationsActive,
              contentDescription = null,
              tint = AgedGold,
              modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "NOTIFICATION CENTER",
              fontSize = 16.sp,
              fontWeight = FontWeight.Black,
              color = Color.White,
              letterSpacing = 1.sp
            )
            if (unreadCount > 0) {
              Spacer(modifier = Modifier.width(8.dp))
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(10.dp))
                  .background(AlertRed)
                  .padding(horizontal = 6.dp, vertical = 2.dp)
              ) {
                Text(
                  text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color.White,
                  maxLines = 1,
                  softWrap = false
                )
              }
            }
          }
          IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "Tutup", tint = TextMuted)
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Actions Row
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
              onClick = onMarkAllRead,
              colors = ButtonDefaults.textButtonColors(contentColor = HighlightSoftCyan),
              contentPadding = PaddingValues(0.dp)
            ) {
              Text("Tandai Semua Dibaca", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            TextButton(
              onClick = onClearAll,
              colors = ButtonDefaults.textButtonColors(contentColor = AlertRed),
              contentPadding = PaddingValues(0.dp)
            ) {
              Text("Hapus Semua", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
          }

          if (isOwner) {
            Button(
              onClick = { showPromoBroadcastDialog = true },
              colors = ButtonDefaults.buttonColors(containerColor = AgedGold, contentColor = ShadowBlack),
              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier.height(30.dp).testTag("broadcast_promo_button")
            ) {
              Icon(Icons.Outlined.Campaign, contentDescription = null, modifier = Modifier.size(14.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Broadcast Owner", fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Categories Chips Scroll
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          categories.forEach { cat ->
            val isSelected = cat == selectedCategory
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (isSelected) AgedGold else CardGrey)
                .border(
                  1.dp,
                  if (isSelected) AgedGold else BorderGrey,
                  RoundedCornerShape(8.dp)
                )
                .clickable { selectedCategory = cat }
                .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
              Text(
                text = cat.uppercase(),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) ShadowBlack else Color.White
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Notifications List
        if (filteredList.isEmpty()) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .weight(1f),
            contentAlignment = Alignment.Center
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Icon(
                imageVector = Icons.Outlined.NotificationsNone,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(48.dp)
              )
              Spacer(modifier = Modifier.height(8.dp))
              Text(
                "Tidak Ada Notifikasi",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
              )
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                "Seluruh aktivitas ERP YANSPROJECT.ID akan tampil di sini.",
                fontSize = 10.sp,
                color = TextMuted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
              )
            }
          }
        } else {
          LazyColumn(
            modifier = Modifier
              .fillMaxWidth()
              .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            items(filteredList) { item ->
              val catUpper = item.category.uppercase()
              val icon = when {
                catUpper in setOf("PESANAN", "INVOICE", "ORDER") -> Icons.Outlined.ReceiptLong
                catUpper in setOf("STOCK", "STOK") -> Icons.Outlined.Inventory2
                catUpper in setOf("PEMBAYARAN", "PAYMENT") -> Icons.Outlined.Payments
                else -> Icons.Outlined.CloudSync
              }
              val iconColor = when {
                catUpper in setOf("PESANAN", "INVOICE", "ORDER") -> AlertOrange
                catUpper in setOf("STOCK", "STOK") -> HighlightSoftCyan
                catUpper in setOf("PEMBAYARAN", "PAYMENT") -> AlertGreen
                else -> AgedGold
              }
              val cardBg = if (item.isRead) CardGrey.copy(alpha = 0.5f) else SurfaceDarkTealSurface
              val borderCol = if (item.isRead) BorderGrey.copy(alpha = 0.4f) else AgedGold.copy(alpha = 0.3f)

              Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = androidx.compose.foundation.BorderStroke(0.8.dp, borderCol),
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                  verticalAlignment = Alignment.Top
                ) {
                  // Category Icon
                  Box(
                    modifier = Modifier
                      .size(36.dp)
                      .clip(RoundedCornerShape(8.dp))
                      .background(iconColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                  ) {
                    Icon(
                      imageVector = icon,
                      contentDescription = null,
                      tint = iconColor,
                      modifier = Modifier.size(18.dp)
                    )
                  }

                  Spacer(modifier = Modifier.width(10.dp))

                  Column(modifier = Modifier.weight(1f)) {
                    // Header Row: Title & Priority Badge
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Text(
                        text = item.title,
                        fontSize = 12.sp,
                        fontWeight = if (item.isRead) FontWeight.Bold else FontWeight.Black,
                        color = if (item.isRead) TextLight else Color.White,
                        modifier = Modifier.weight(1f)
                      )
                      Spacer(modifier = Modifier.width(6.dp))

                      // Priority Badge
                      val (priorityLabel, priorityColor) = when (item.priority.uppercase()) {
                        "HIGH", "MENDESAK" -> "Mendesak" to AlertRed
                        "MEDIUM", "PENTING" -> "Penting" to AgedGold
                        else -> "Normal" to HighlightSoftCyan
                      }
                      Box(
                        modifier = Modifier
                          .clip(RoundedCornerShape(4.dp))
                          .background(priorityColor.copy(alpha = 0.15f))
                          .border(0.5.dp, priorityColor.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                          .padding(horizontal = 6.dp, vertical = 2.dp)
                      ) {
                        Text(
                          text = priorityLabel,
                          fontSize = 8.sp,
                          fontWeight = FontWeight.Bold,
                          color = priorityColor
                        )
                      }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Message Summary
                    Text(
                      text = item.message,
                      fontSize = 11.sp,
                      color = if (item.isRead) TextMuted else TextLight
                    )

                    // Optional Info Badges (Member Name & Invoice Number)
                    val memberName = remember(item) {
                      if (item.userId != "ALL" && item.userId.isNotBlank()) item.userId else null
                    }
                    val invoiceMatch = remember(item.message, item.title) {
                      val regex = Regex("""(INV-[A-Za-z0-9-]+|#\d+)""", RegexOption.IGNORE_CASE)
                      regex.find("${item.title} ${item.message}")?.value
                    }

                    if (memberName != null || invoiceMatch != null) {
                      Spacer(modifier = Modifier.height(4.dp))
                      Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                      ) {
                        if (memberName != null) {
                          Box(
                            modifier = Modifier
                              .clip(RoundedCornerShape(4.dp))
                              .background(CardGrey)
                              .padding(horizontal = 5.dp, vertical = 1.dp)
                          ) {
                            Text(
                              text = "Member: $memberName",
                              fontSize = 8.sp,
                              color = HighlightSoftCyan,
                              fontWeight = FontWeight.Medium
                            )
                          }
                        }
                        if (invoiceMatch != null) {
                          Box(
                            modifier = Modifier
                              .clip(RoundedCornerShape(4.dp))
                              .background(CardGrey)
                              .padding(horizontal = 5.dp, vertical = 1.dp)
                          ) {
                            Text(
                              text = "Invoice: $invoiceMatch",
                              fontSize = 8.sp,
                              color = AgedGold,
                              fontWeight = FontWeight.Medium
                            )
                          }
                        }
                      }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Time & Card Actions (ONLY TWO: Lihat Detail & Hapus)
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      val sdf = remember { java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.forLanguageTag("id-ID")) }
                      val timeStr = remember(item.timestamp) { sdf.format(java.util.Date(item.timestamp)) }
                      Text(
                        text = timeStr,
                        fontSize = 9.sp,
                        color = TextMuted
                      )

                      Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                      ) {
                        // Button 1: Lihat Detail
                        TextButton(
                          onClick = {
                            onMarkRead(item.id)
                            val cat = item.category.uppercase()
                            if (cat in setOf("SISTEM", "SYSTEM")) {
                              selectedSystemNotif = item
                            } else {
                              val target = when {
                                cat in setOf("PESANAN", "INVOICE", "ORDER") -> AppTab.INVOICE
                                cat in setOf("STOCK", "STOK") -> AppTab.STOCK
                                cat in setOf("PEMBAYARAN", "PAYMENT") -> AppTab.INVOICE
                                else -> AppTab.SETTINGS
                              }
                              onNavigate(target)
                              onDismiss()
                            }
                          },
                          contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                          Text(
                            text = "Lihat Detail",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = HighlightSoftCyan
                          )
                          Spacer(modifier = Modifier.width(2.dp))
                          Icon(
                            imageVector = Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            tint = HighlightSoftCyan,
                            modifier = Modifier.size(12.dp)
                          )
                        }

                        // Button 2: Hapus
                        IconButton(
                          onClick = { onDeleteNotification(item.id) },
                          modifier = Modifier.size(24.dp).testTag("delete_notification_button")
                        ) {
                          Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "Hapus Notifikasi",
                            tint = AlertRed,
                            modifier = Modifier.size(16.dp)
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
  }

  // System Detail Info Dialog
  val sysNotif = selectedSystemNotif
  if (sysNotif != null) {
    val notif = sysNotif
    val sdf = remember { java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.forLanguageTag("id-ID")) }
    val timeStr = remember(notif.timestamp) { sdf.format(java.util.Date(notif.timestamp)) }

    Dialog(onDismissRequest = { selectedSystemNotif = null }) {
      Card(
        colors = CardDefaults.cardColors(containerColor = ShadowBlack),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AgedGold.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth(0.95f)
      ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CloudSync, contentDescription = null, tint = AgedGold, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "INFORMASI SISTEM ERP",
              fontSize = 14.sp,
              fontWeight = FontWeight.Black,
              color = AgedGold
            )
          }

          Text(
            text = notif.title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
          )

          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(AgedGold.copy(alpha = 0.2f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
              Text(text = timeStr, fontSize = 9.sp, color = AgedGold, fontWeight = FontWeight.Bold)
            }
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(HighlightSoftCyan.copy(alpha = 0.2f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
              Text(text = "Pengirim: ${notif.createdBy}", fontSize = 9.sp, color = HighlightSoftCyan, fontWeight = FontWeight.Bold)
            }
          }

          HorizontalDivider(color = BorderGrey, thickness = 0.8.dp)

          Text(
            text = notif.message,
            fontSize = 12.sp,
            color = TextLight,
            lineHeight = 18.sp
          )

          Spacer(modifier = Modifier.height(8.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
          ) {
            Button(
              onClick = { selectedSystemNotif = null },
              colors = ButtonDefaults.buttonColors(containerColor = AgedGold, contentColor = ShadowBlack),
              shape = RoundedCornerShape(8.dp)
            ) {
              Text("Tutup", fontWeight = FontWeight.Bold)
            }
          }
        }
      }
    }
  }

  // Owner Broadcast Dialog
  if (showPromoBroadcastDialog) {
    var promoTitle by remember { mutableStateOf("") }
    var promoMessage by remember { mutableStateOf("") }

    Dialog(onDismissRequest = { showPromoBroadcastDialog = false }) {
      Card(
        colors = CardDefaults.cardColors(containerColor = ShadowBlack),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AgedGold.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth(0.95f)
      ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
          Text(
            text = "Broadcast Owner",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = AgedGold
          )
          Text(
            text = "Kirim notifikasi pesan sistem/broadcast resmi langsung ke seluruh akun Member.",
            fontSize = 12.sp,
            color = TextLight
          )

          OutlinedTextField(
            value = promoTitle,
            onValueChange = { promoTitle = it },
            label = { Text("Judul Broadcast / Pengumuman", color = TextMuted) },
            modifier = Modifier.fillMaxWidth().testTag("promo_title_input"),
            colors = TextFieldDefaults.colors(
              focusedContainerColor = CardGrey,
              unfocusedContainerColor = CardGrey,
              focusedTextColor = Color.White,
              unfocusedTextColor = Color.White
            )
          )

          OutlinedTextField(
            value = promoMessage,
            onValueChange = { promoMessage = it },
            label = { Text("Pesan / Informasi Lengkap", color = TextMuted) },
            modifier = Modifier.fillMaxWidth().testTag("promo_message_input"),
            colors = TextFieldDefaults.colors(
              focusedContainerColor = CardGrey,
              unfocusedContainerColor = CardGrey,
              focusedTextColor = Color.White,
              unfocusedTextColor = Color.White
            )
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Button(
              onClick = { showPromoBroadcastDialog = false },
              modifier = Modifier.weight(1f),
              colors = ButtonDefaults.buttonColors(containerColor = CardGrey, contentColor = Color.White),
              shape = RoundedCornerShape(8.dp)
            ) {
              Text("Batal")
            }

            Button(
              onClick = {
                if (promoTitle.isNotBlank() && promoMessage.isNotBlank()) {
                  triggerPromoBroadcast(promoTitle, promoMessage)
                  Toast.makeText(context, "Broadcast berhasil terkirim!", Toast.LENGTH_LONG).show()
                  showPromoBroadcastDialog = false
                } else {
                  Toast.makeText(context, "Mohon lengkapi seluruh kolom broadcast.", Toast.LENGTH_SHORT).show()
                }
              },
              modifier = Modifier.weight(1.5f).testTag("send_broadcast_button"),
              colors = ButtonDefaults.buttonColors(containerColor = AgedGold, contentColor = ShadowBlack),
              shape = RoundedCornerShape(8.dp)
            ) {
              Text("Kirim Broadcast", fontWeight = FontWeight.Black)
            }
          }
        }
      }
    }
  }
}

@Composable
fun AppLockScreen(
    isBiometricEnabled: Boolean,
    onUnlockSuccess: () -> Unit,
    viewModel: MainViewModel
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var pinInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var usePinByChoice by remember { mutableStateOf(!isBiometricEnabled) }

    val triggerBiometric = {
        com.yansproject.app.ui.security.BiometricAuthManager.authenticateWithBiometrics(
            context = context,
            onSuccess = {
                viewModel.addAuditLog("App Lock Terbuka", "Pemilik sukses membuka App Lock menggunakan sidik jari.")
                onUnlockSuccess()
            },
            onError = { errString ->
                errorMessage = errString
                usePinByChoice = true
            }
        )
    }

    LaunchedEffect(Unit) {
        if (isBiometricEnabled) {
            triggerBiometric()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ShadowBlack)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Outlined.Security,
                contentDescription = null,
                tint = AgedGold,
                modifier = Modifier.size(72.dp)
            )

            Text(
                text = "APLIKASI TERKUNCI",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = AgedGold,
                letterSpacing = 1.5.sp
            )

            Text(
                text = "Gunakan autentikasi biometrik atau masukkan PIN keamanan Anda untuk memverifikasi identitas Owner.",
                fontSize = 13.sp,
                color = TextMuted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            if (usePinByChoice) {
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { 
                        if (it.length <= 8) {
                            pinInput = it
                            errorMessage = ""
                        }
                    },
                    placeholder = { Text("Masukkan PIN / Password", color = TextMuted) },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null, tint = AgedGold) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = AgedGold,
                        unfocusedBorderColor = BorderGrey
                    )
                )

                if (errorMessage.isNotEmpty()) {
                    Text(
                        text = errorMessage,
                        color = AlertRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isBiometricEnabled) {
                        TextButton(
                            onClick = { 
                                usePinByChoice = false
                                triggerBiometric()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Gunakan Sidik Jari", color = HighlightSoftCyan, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = {
                            val activeEmail = FirebaseSyncManager.currentUser.value?.email ?: ""
                            val savedPass = AppSettings.getLocalUserCredential(context, activeEmail)?.passwordOrPin
                            val isValid = pinInput.isNotEmpty() && savedPass != null && pinInput == savedPass
                            if (isValid) {
                                viewModel.addAuditLog("App Lock Terbuka (PIN)", "Pemilik sukses membuka App Lock menggunakan verifikasi PIN.")
                                onUnlockSuccess()
                            } else {
                                errorMessage = "PIN Keamanan atau Password salah! Periksa kredensial akun Anda."
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AgedGold, contentColor = ShadowBlack),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("VERIFIKASI", fontWeight = FontWeight.Black)
                    }
                }
            } else {
                Button(
                    onClick = { triggerBiometric() },
                    colors = ButtonDefaults.buttonColors(containerColor = AgedGold, contentColor = ShadowBlack),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp).padding(horizontal = 16.dp)
                ) {
                    Icon(Icons.Outlined.Fingerprint, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("VERIFIKASI BIOMETRIK", fontWeight = FontWeight.Black)
                }

                TextButton(onClick = { usePinByChoice = true }) {
                    Text("Gunakan PIN Keamanan", color = HighlightSoftCyan)
                }
            }

            TextButton(
                onClick = {
                    val prefs = context.getSharedPreferences("yans_security_prefs", android.content.Context.MODE_PRIVATE)
                    prefs.edit().putBoolean("app_lock_enabled", false).putBoolean("pin_lock_enabled", false).putBoolean("biometric_enabled", false).apply()
                    onUnlockSuccess()
                }
            ) {
                Text("Buka Langsung Tanpa Kunci", color = TextMuted, fontSize = 11.sp)
            }
        }
    }
}
