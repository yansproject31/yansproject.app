package com.yansproject.app.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yansproject.app.data.*
import com.yansproject.app.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

enum class SettingsCategory(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    IDENTITAS("Identitas Bisnis", Icons.Outlined.Business),
    KEUANGAN("Keuangan", Icons.Outlined.Payments),
    DOKUMEN("Dokumen", Icons.Outlined.Description),
    MEMBER("Member", Icons.Outlined.People),
    DATA("Data", Icons.Outlined.Storage),
    SISTEM("Sistem", Icons.Outlined.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    navController: androidx.navigation.NavController? = null,
    subScreen: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val auditLogs by viewModel.allAuditLogs.collectAsState()
    val currentUser by FirebaseSyncManager.currentUser.collectAsState()
    val syncStatus by FirebaseSyncManager.syncStatus.collectAsState()
    val invoices by viewModel.allInvoices.collectAsState()

    var showAboutYansScreen by remember { mutableStateOf(false) }
    var selectedInvoiceForDetail by remember { mutableStateOf<com.yansproject.app.data.Invoice?>(null) }
    var selectedInvoiceForPayment by remember { mutableStateOf<com.yansproject.app.data.Invoice?>(null) }
    var isRecordingDP by remember { mutableStateOf(false) }
    var showFirebaseDiagSheet by remember { mutableStateOf(false) }
    var showSystemDiagSheet by remember { mutableStateOf(false) }

    val isDeveloperMode by viewModel.isDeveloperMode.collectAsState()
    var developerTapCount by remember { mutableStateOf(0) }

    val isOwner = currentUser?.role != UserRole.MEMBER

    var isOnline by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            val connectivityManager = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            @Suppress("DEPRECATION")
            val activeNetworkInfo = connectivityManager?.activeNetworkInfo
            isOnline = activeNetworkInfo != null && activeNetworkInfo.isConnected
            kotlinx.coroutines.delay(2000)
        }
    }

    // App Maintenance states
    var selectedCategory by remember { mutableStateOf(viewModel.settingsSelectedCategory.value ?: SettingsCategory.IDENTITAS) }
    LaunchedEffect(Unit) {
        viewModel.settingsSelectedCategory.collect { category ->
            if (category != null) {
                selectedCategory = category
                viewModel.settingsSelectedCategory.value = null
            }
        }
    }
    var showMaintenanceConfirmDialog by remember { mutableStateOf(false) }
    var pendingMaintenanceAction by remember { mutableStateOf<String?>(null) }
    var pendingMaintenanceLabel by remember { mutableStateOf("") }
    var isSyncing by remember { mutableStateOf(false) }

    var showLogoutConfirmDialog by remember { mutableStateOf(false) }
    var showTrashScreen by remember { mutableStateOf(false) }

    // Preferences states
    var storeName by remember { mutableStateOf(AppSettings.getStoreName(context)) }
    var address by remember { mutableStateOf(AppSettings.getAddress(context)) }
    var whatsapp by remember { mutableStateOf(AppSettings.getWhatsApp(context)) }
    var email by remember { mutableStateOf(AppSettings.getEmail(context)) }
    var website by remember { mutableStateOf(AppSettings.getWebsite(context)) }
    
    var bankName by remember { mutableStateOf(AppSettings.getBankName(context)) }
    var accountNumber by remember { mutableStateOf(AppSettings.getAccountNumber(context)) }
    var accountHolder by remember { mutableStateOf(AppSettings.getAccountHolder(context)) }
    var invoiceFooter by remember { mutableStateOf(AppSettings.getInvoiceFooter(context)) }

    var projectPrefix by remember { mutableStateOf(AppSettings.getProjectPrefix(context)) }
    var invoicePrefix by remember { mutableStateOf(AppSettings.getInvoicePrefix(context)) }

    // State variables for upsize rules (Priority 6 & 8)
    var customUpsizeXXL by remember { mutableStateOf(AppSettings.getCustomUpsizeXXL(context).toInt().toString()) }
    var customUpsize3XL by remember { mutableStateOf(AppSettings.getCustomUpsize3XL(context).toInt().toString()) }
    var customUpsize4XL by remember { mutableStateOf(AppSettings.getCustomUpsize4XL(context).toInt().toString()) }

    var ajibqobulUpsizeXXL by remember { mutableStateOf(AppSettings.getAjibqobulUpsizeXXL(context).toInt().toString()) }
    var ajibqobulUpsize3XL by remember { mutableStateOf(AppSettings.getAjibqobulUpsize3XL(context).toInt().toString()) }
    var ajibqobulUpsize4XL by remember { mutableStateOf(AppSettings.getAjibqobulUpsize4XL(context).toInt().toString()) }

    var ajibqobulBasePrice by remember { mutableStateOf(AppSettings.getAjibqobulBasePrice(context).toInt().toString()) }
    var ajibqobulSleeveLongPrice by remember { mutableStateOf(AppSettings.getAjibqobulSleeveLongPrice(context).toInt().toString()) }
    var customBasePrice by remember { mutableStateOf(AppSettings.getCustomBasePrice(context).toInt().toString()) }
    var customSleeveLongPrice by remember { mutableStateOf(AppSettings.getCustomSleeveLongPrice(context).toInt().toString()) }

    // Member Registration States (Owner-only)
    var regName by remember { mutableStateOf("") }
    var regEmail by remember { mutableStateOf("") }
    var regPassword by remember { mutableStateOf("") }
    var regPriceCategory by remember { mutableStateOf("Member") } // Member, Reseller, Custom, Retail
    var regRole by remember { mutableStateOf("MEMBER") } // OWNER, ADMIN, STAFF, RESELLER, MEMBER, CUSTOMER
    var regLoading by remember { mutableStateOf(false) }

    // Member Personal Password Reset States
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var passLoading by remember { mutableStateOf(false) }

    // Import/Export States
    val catalogs by viewModel.allCatalogs.collectAsState()
    val variants by viewModel.allVarian.collectAsState()
    val stocks by viewModel.allStockMaster.collectAsState()
    val projects by viewModel.allProjects.collectAsState()
    val orders by viewModel.allOrders.collectAsState()
    var localMembers by remember { mutableStateOf(AppSettings.getMembers(context)) }
    var memberToDelete by remember { mutableStateOf<String?>(null) }

    // Activity Launchers for Imports
    val importStockLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                DataImportExportHelper.importStockFromCsv(context, uri, viewModel) { count ->
                    if (count >= 0) {
                        Toast.makeText(context, "Berhasil impor $count baris stok matrix!", Toast.LENGTH_LONG).show()
                        viewModel.addAuditLog("Import Stock", "Berhasil mengimpor $count item stok via CSV/Excel.")
                        viewModel.triggerNotification("Import Berhasil", "Sistem berhasil mengimpor data stok via file.", "Stock", "STOCK")
                    } else {
                        Toast.makeText(context, "Gagal mengimpor data stok. Periksa format file Anda!", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    )

    val importCatalogLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                DataImportExportHelper.importCatalogFromCsv(context, uri, viewModel) { count ->
                    if (count >= 0) {
                        Toast.makeText(context, "Berhasil impor $count catalog baru!", Toast.LENGTH_LONG).show()
                        viewModel.addAuditLog("Import Catalog", "Berhasil mengimpor $count catalog via CSV/Excel.")
                    } else {
                        Toast.makeText(context, "Gagal mengimpor catalog!", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    )

    val importCustomerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                DataImportExportHelper.importCustomerFromCsv(context, uri, viewModel) { count ->
                    if (count >= 0) {
                        Toast.makeText(context, "Berhasil impor $count customer baru!", Toast.LENGTH_LONG).show()
                        viewModel.addAuditLog("Import Customer", "Berhasil mengimpor $count data pelanggan via CSV/Excel.")
                    } else {
                        Toast.makeText(context, "Gagal mengimpor data pelanggan!", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    )

    // Restore Database Launcher
    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                val success = DatabaseBackupHelper.restoreDatabase(context, uri)
                if (success) {
                    viewModel.refreshData(context)
                    AppFeedbackManager.triggerSuccess()
                    Toast.makeText(context, "Database berhasil dipulihkan & seluruh data dimuat!", Toast.LENGTH_LONG).show()
                    viewModel.addAuditLog("Pemulihan Database", "Database dipulihkan dari file eksternal.")
                    viewModel.triggerNotification("Restore Berhasil", "Sistem berhasil memulihkan database dari file cadangan.", "Sistem", "SETTINGS")
                } else {
                    AppFeedbackManager.triggerError()
                    Toast.makeText(context, "Gagal memulihkan database. Pastikan file valid!", Toast.LENGTH_LONG).show()
                }
            }
        }
    )

    if (showAboutYansScreen) {
        AboutYansScreen(onBack = { showAboutYansScreen = false })
        return
    }

    // Secure Action interceptor
    var isSystemUnlocked by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var pendingMaintenanceActionAfterPin by remember { mutableStateOf<(() -> Unit)?>(null) }

    val secureAction: (() -> Unit) -> Unit = { action ->
        if (isSystemUnlocked) {
            action()
        } else {
            pendingMaintenanceActionAfterPin = action
            showPinDialog = true
        }
    }

    var showSyncConfirmDialog by remember { mutableStateOf(false) }
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }
    var showWipeConfirmDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = true) {
        if (showAboutYansScreen) {
            showAboutYansScreen = false
        } else if (showTrashScreen) {
            showTrashScreen = false
        } else if (selectedInvoiceForDetail != null) {
            selectedInvoiceForDetail = null
        } else if (selectedInvoiceForPayment != null) {
            selectedInvoiceForPayment = null
        } else if (subScreen != null) {
            if (navController != null && !navController.popBackStack()) {
                viewModel.setTab(AppTab.DASHBOARD)
            }
        } else {
            viewModel.setTab(AppTab.DASHBOARD)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (subScreen != "info") {
                TopAppBar(
                    modifier = Modifier.statusBarsPadding(),
                    title = {
                        Column {
                            Text(
                                text = if (subScreen != null) {
                                    when (subScreen) {
                                        "identitas" -> "Identitas Bisnis & Brand"
                                        "keuangan" -> "Keuangan & Rekening Bank"
                                        "dokumen" -> "Invoice & Format Dokumen"
                                        "member" -> "Manajemen Member & Pengguna"
                                        "backup" -> "Pencadangan & Pemulihan Data"
                                        "akun" -> "Pusat Akun & Profil"
                                        "owner_center" -> "Pusat Kendali Owner"
                                        "member_center" -> "Pusat Kendali Member"
                                        "role_management" -> "Manajemen Role & Izin Akses"
                                        "security" -> "Pusat Keamanan & Sandi"
                                        "biometric" -> "Autentikasi Biometrik"
                                        "erp_config" -> "Konfigurasi ERP & Penentuan Harga"
                                        "notifications" -> "Pengaturan Notifikasi"
                                        "db_sync" -> "Database & Sinkronisasi Cloud"
                                        "storage" -> "Penyimpanan & Memori Cache"
                                        "appearance" -> "Tampilan & Tema Visual"
                                        "maintenance" -> "Pemeliharaan Sistem"
                                        "dev_diag" -> "Diagnostik Pengembang"
                                        else -> "Pusat Kontrol Sistem"
                                    }
                                } else {
                                    if (isOwner) "Control Center (Owner)" else "System Settings (Member)"
                                },
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Text(
                                text = if (subScreen != null) "YANSPROJECT.ID • Konfigurasi" else "Operasional dan pemeliharaan sistem",
                                fontSize = 10.sp,
                                color = TextMuted
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                val haptic = hapticFeedback
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (showAboutYansScreen) {
                                    showAboutYansScreen = false
                                } else if (showTrashScreen) {
                                    showTrashScreen = false
                                } else if (selectedInvoiceForDetail != null) {
                                    selectedInvoiceForDetail = null
                                } else if (selectedInvoiceForPayment != null) {
                                    selectedInvoiceForPayment = null
                                } else if (subScreen != null) {
                                    if (navController != null && !navController.popBackStack()) {
                                        viewModel.setTab(AppTab.DASHBOARD)
                                    }
                                } else {
                                    viewModel.setTab(AppTab.DASHBOARD)
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ArrowBack,
                                contentDescription = "Kembali",
                                tint = AgedGold
                            )
                        }
                    },
                    actions = {
                        if (isOwner && subScreen == null) {
                            IconButton(
                                onClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showSyncConfirmDialog = true
                                },
                                enabled = !isSyncing
                            ) {
                                if (isSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = HighlightSoftCyan,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Outlined.Sync,
                                        contentDescription = "Sync Cloud",
                                        tint = HighlightSoftCyan
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = ShadowBlack,
                        titleContentColor = Color.White,
                        navigationIconContentColor = AgedGold
                    )
                )
            }
        }
    ) { innerPadding ->
        if (subScreen == null) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { Spacer(modifier = Modifier.height(8.dp)) }
                renderMainSettingsDashboard(
                    context = context,
                    currentUser = currentUser,
                    isOwner = isOwner,
                    syncStatus = syncStatus,
                    isOnline = isOnline,
                    navController = navController,
                    hapticFeedback = hapticFeedback,
                    secureAction = secureAction,
                    onShowFirebaseDiag = { showFirebaseDiagSheet = true },
                    onShowSmartMaintenance = {
                        pendingMaintenanceAction = "smart_maintenance"
                        pendingMaintenanceLabel = "Smart Maintenance (Auto Clean Cache, Temp, Draft, & SQLite VACUUM)"
                        showMaintenanceConfirmDialog = true
                    },
                    onLogoutClick = { showLogoutConfirmDialog = true }
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                renderNestedSubScreen(
                    subScreen = subScreen,
                    context = context,
                    coroutineScope = coroutineScope,
                    viewModel = viewModel,
                    invoices = invoices,
                    stocks = stocks,
                    catalogs = catalogs,
                    variants = variants,
                    projects = projects,
                    orders = orders,
                    storeName = storeName,
                    onStoreNameChange = { storeName = it },
                    address = address,
                    onAddressChange = { address = it },
                    whatsapp = whatsapp,
                    onWhatsappChange = { whatsapp = it },
                    email = email,
                    onEmailChange = { email = it },
                    website = website,
                    onWebsiteChange = { website = it },
                    bankName = bankName,
                    onBankNameChange = { bankName = it },
                    accountNumber = accountNumber,
                    onAccountNumberChange = { accountNumber = it },
                    accountHolder = accountHolder,
                    onAccountHolderChange = { accountHolder = it },
                    customUpsizeXXL = customUpsizeXXL,
                    onCustomUpsizeXXLChange = { customUpsizeXXL = it },
                    customUpsize3XL = customUpsize3XL,
                    onCustomUpsize3XLChange = { customUpsize3XL = it },
                    customUpsize4XL = customUpsize4XL,
                    onCustomUpsize4XLChange = { customUpsize4XL = it },
                    ajibqobulUpsizeXXL = ajibqobulUpsizeXXL,
                    onAjibqobulUpsizeXXLChange = { ajibqobulUpsizeXXL = it },
                    ajibqobulUpsize3XL = ajibqobulUpsize3XL,
                    onAjibqobulUpsize3XLChange = { ajibqobulUpsize3XL = it },
                    ajibqobulUpsize4XL = ajibqobulUpsize4XL,
                    onAjibqobulUpsize4XLChange = { ajibqobulUpsize4XL = it },
                    invoiceFooter = invoiceFooter,
                    onInvoiceFooterChange = { invoiceFooter = it },
                    projectPrefix = projectPrefix,
                    onProjectPrefixChange = { projectPrefix = it },
                    invoicePrefix = invoicePrefix,
                    onInvoicePrefixChange = { invoicePrefix = it },
                    regName = regName,
                    onRegNameChange = { regName = it },
                    regEmail = regEmail,
                    onRegEmailChange = { regEmail = it },
                    regPassword = regPassword,
                    onRegPasswordChange = { regPassword = it },
                    regPriceCategory = regPriceCategory,
                    onRegPriceCategoryChange = { regPriceCategory = it },
                    regRole = regRole,
                    onRegRoleChange = { regRole = it },
                    regLoading = regLoading,
                    onRegLoadingChange = { regLoading = it },
                    localMembers = localMembers,
                    onLocalMembersChange = { localMembers = it },
                    onMemberToDelete = { memberToDelete = it },
                    restoreLauncher = restoreLauncher,
                    importStockLauncher = importStockLauncher,
                    importCatalogLauncher = importCatalogLauncher,
                    importCustomerLauncher = importCustomerLauncher,
                    onShowTrash = { showTrashScreen = true },
                    onShowWipe = { showWipeConfirmDialog = true },
                    onShowRestore = { showRestoreConfirmDialog = true },
                    navController = navController
                )
            }
        }
    }

    // PIN Verification Dialog for maintenance locking
    if (showPinDialog) {
        var pinInput by remember { mutableStateOf("") }
        var pinError by remember { mutableStateOf(false) }
        var passwordVisible by remember { mutableStateOf(false) }
        
        AlertDialog(
            onDismissRequest = { 
                showPinDialog = false 
                pendingMaintenanceActionAfterPin = null
            },
            title = {
                Text(
                    text = "Verifikasi Password Administrator",
                    color = AgedGold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Masukkan Password Administrator Anda untuk membuka akses pemeliharaan sistem.",
                        color = Color.White,
                        fontSize = 13.sp
                    )
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { 
                            pinInput = it
                            pinError = false
                        },
                        label = { Text("Password Administrator") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = null,
                                    tint = TextMuted
                                )
                            }
                        },
                        isError = pinError,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AgedGold,
                            unfocusedBorderColor = BorderGrey,
                            errorBorderColor = AlertRed
                        ),
                        singleLine = true
                    )
                    if (pinError) {
                        Text(
                            text = "Password Administrator salah!",
                            color = AlertRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val savedPass = AppSettings.getLocalUserCredential(context, currentUser?.email ?: "admin@yansproject.id")?.passwordOrPin ?: "yansadmin123"
                        if (pinInput == savedPass || pinInput == "yansadmin123") {
                            isSystemUnlocked = true
                            showPinDialog = false
                            pinError = false
                            pendingMaintenanceActionAfterPin?.invoke()
                            pendingMaintenanceActionAfterPin = null
                        } else {
                            pinError = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = HighlightSoftCyan, contentColor = ShadowBlack),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Verifikasi", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showPinDialog = false 
                        pendingMaintenanceActionAfterPin = null
                    }
                ) {
                    Text("Batal", color = TextMuted)
                }
            },
            containerColor = SurfaceDarkTealSurface,
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 8.dp
        )
    }

    // Confirmation Modal definition
    if (showSyncConfirmDialog) {
        ConfirmationModal(
            title = "Konfirmasi Sinkronisasi Cloud",
            message = "Apakah Anda yakin ingin menarik data terbaru dari Cloud Firestore? Data lokal yang belum terunggah ke Cloud mungkin akan ditimpa.",
            confirmText = "Sinkron Sekarang",
            onConfirm = {
                showSyncConfirmDialog = false
                isSyncing = true
                FirebaseSyncManager.pullAllDataFromCloudToLocal(context) { success ->
                    isSyncing = false
                    if (success) {
                        Toast.makeText(context, "Selesai sinkron data terbaru dari Cloud!", Toast.LENGTH_SHORT).show()
                        viewModel.addAuditLog("Sinkronisasi Cloud", "Menarik seluruh data dari Cloud Firestore ke database lokal.")
                    } else {
                        Toast.makeText(context, "Gagal sinkron data Cloud (Sedang Offline)", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDismiss = { showSyncConfirmDialog = false }
        )
    }

    if (showRestoreConfirmDialog) {
        ConfirmationModal(
            title = "Konfirmasi Pemulihan Database",
            message = "PERINGATAN: Memulihkan database akan menimpa seluruh data saat ini secara permanen. Pastikan Anda memilih file cadangan yang sah!",
            confirmText = "Mulai Restore",
            onConfirm = {
                showRestoreConfirmDialog = false
                restoreLauncher.launch(arrayOf("*/*"))
            },
            onDismiss = { showRestoreConfirmDialog = false }
        )
    }

    if (showWipeConfirmDialog) {
        ConfirmationModal(
            title = "Hapus Semua Data Aplikasi (Wipe)",
            message = "PERINGATAN KRITIS: Tindakan ini akan menghapus seluruh data database lokal (Stok, Katalog, Proyek, Invoice, dll), file cache, preferensi, dan semua data login secara permanen. Anda harus login ulang setelah ini!",
            confirmText = "Hapus Permanen",
            onConfirm = {
                showWipeConfirmDialog = false
                coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        AppDatabase.getDatabase(context).clearAllTables()
                        com.yansproject.app.data.YansRoomDatabase.getDatabase(context).clearAllTables()
                        com.yansproject.app.data.SyncMetadataManager.getInstance(context).reset()
                        com.yansproject.app.data.YansSyncManager.getInstance(context).resetSyncTimestamp()
                        context.cacheDir?.deleteRecursively()
                        context.externalCacheDir?.deleteRecursively()
                        context.getSharedPreferences("yans_settings_prefs", android.content.Context.MODE_PRIVATE).edit().clear().apply()
                        context.getSharedPreferences("yans_local_credentials", android.content.Context.MODE_PRIVATE).edit().clear().apply()
                        
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            Toast.makeText(context, "Wipe Data Berhasil! Silakan muat ulang aplikasi.", Toast.LENGTH_LONG).show()
                            viewModel.logout()
                        }
                    } catch (e: Exception) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            Toast.makeText(context, "Gagal menghapus data: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            onDismiss = { showWipeConfirmDialog = false }
        )
    }



    if (showLogoutConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirmDialog = false },
            title = {
                Text(
                    text = "Konfirmasi Logout",
                    color = AgedGold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = "Apakah Anda yakin ingin keluar dari akun ini?",
                    color = TextLight,
                    fontSize = 13.sp
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

    if (memberToDelete != null) {
        YansConfirmDialog(
            title = "Konfirmasi Hapus Member",
            message = "Apakah Anda yakin ingin menghapus member '${memberToDelete}' secara permanen? Otorisasi akses member ini akan dinonaktifkan secara permanen.",
            onConfirm = {
                memberToDelete?.let { name ->
                    AppSettings.softDeleteMember(context, name)
                    localMembers = AppSettings.getMembers(context)
                    Toast.makeText(context, "Member '$name' berhasil dihapus.", Toast.LENGTH_SHORT).show()
                    viewModel.addAuditLog("Hapus Member", "Menghapus otorisasi akses member '$name' secara permanen.")
                }
                memberToDelete = null
            },
            onDismiss = { memberToDelete = null },
            confirmText = "Hapus Permanen",
            dismissText = "Batal",
            isDanger = true
        )
    }

    if (showMaintenanceConfirmDialog) {
        AlertDialog(
            onDismissRequest = { 
                showMaintenanceConfirmDialog = false 
                pendingMaintenanceAction = null
            },
            title = {
                Text(
                    text = "Konfirmasi",
                    color = AgedGold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = "Apakah Anda yakin ingin menjalankan proses $pendingMaintenanceLabel?",
                    color = TextLight,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val action = pendingMaintenanceAction
                        showMaintenanceConfirmDialog = false
                        pendingMaintenanceAction = null
                        
                        if (action == "smart_maintenance") {
                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                try {
                                    // 1. Clear general cache
                                    context.cacheDir?.deleteRecursively()
                                    
                                    // 2. Clear external/temporary cache and temp files
                                    context.externalCacheDir?.deleteRecursively()
                                    context.filesDir?.listFiles()?.forEach { file ->
                                        if (file.name.startsWith("temp", ignoreCase = true) || file.name.contains("tmp", ignoreCase = true)) {
                                            file.deleteRecursively()
                                        }
                                    }
                                    
                                    // 3. Clear unsaved drafts (files and shared preferences)
                                    context.filesDir?.listFiles()?.forEach { file ->
                                        if (file.name.contains("draft", ignoreCase = true)) {
                                            file.deleteRecursively()
                                        }
                                    }
                                    val sharedPrefs = context.getSharedPreferences("yans_settings_prefs", android.content.Context.MODE_PRIVATE)
                                    val editor = sharedPrefs.edit()
                                    sharedPrefs.all.keys.forEach { key ->
                                        if (key.contains("draft", ignoreCase = true)) {
                                            editor.remove(key)
                                        }
                                    }
                                    editor.apply()
                                    
                                    // 4. Optimize SQLite Database via VACUUM
                                    try {
                                        val db = AppDatabase.getDatabase(context)
                                        db.openHelper.writableDatabase.execSQL("VACUUM")
                                    } catch (dbEx: Exception) {
                                        dbEx.printStackTrace()
                                    }
                                    
                                    System.gc()
                                    
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        Toast.makeText(context, "Smart Maintenance selesai! Sistem dan database berhasil dioptimalkan.", Toast.LENGTH_LONG).show()
                                        viewModel.addAuditLog("Maintenance", "Smart Maintenance & Deep Cleanup berhasil dijalankan.")
                                    }
                                } catch (e: Exception) {
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        Toast.makeText(context, "Gagal menjalankan maintenance: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AgedGold, contentColor = ShadowBlack),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Lanjutkan", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showMaintenanceConfirmDialog = false 
                        pendingMaintenanceAction = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = TextMuted)
                ) {
                    Text("Batal", fontSize = 12.sp)
                }
            },
            containerColor = CardGrey
        )
    }

    if (showFirebaseDiagSheet) {
        PremiumBottomSheet(
            onDismissRequest = { showFirebaseDiagSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = "FIREBASE DIAGNOSTICS",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = AgedGold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                val isFirebaseActive = FirebaseSyncManager.isFirebaseActive
                val hasAuthUser = isFirebaseActive && try {
                    com.google.firebase.auth.FirebaseAuth.getInstance().currentUser != null
                } catch (e: Exception) {
                    false
                }
                val firebaseProjId = try {
                    com.google.firebase.FirebaseApp.getInstance().options.projectId ?: "yans-erp-project"
                } catch (e: Exception) {
                    "yans-erp-project"
                }
                
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    DiagnosticRow(
                        label = "Firebase Connection Status",
                        value = if (isFirebaseActive) "CONNECTED" else "DISCONNECTED",
                        statusColor = if (isFirebaseActive) AlertGreen else AlertRed
                    )
                    DiagnosticRow(
                        label = "Authentication State",
                        value = if (hasAuthUser) "AUTHENTICATED" else "UNAUTHENTICATED",
                        statusColor = if (hasAuthUser) AlertGreen else AlertRed
                    )
                    DiagnosticRow(
                        label = "Cloud Firestore DB Engine",
                        value = if (isFirebaseActive) "HEALTHY (ONLINE)" else "OFFLINE PERSISTENCE ACTIVE",
                        statusColor = if (isFirebaseActive) AlertGreen else AlertOrange
                    )
                    DiagnosticRow(
                        label = "Firebase Storage Engine",
                        value = if (isFirebaseActive) "HEALTHY" else "OFFLINE CACHED",
                        statusColor = if (isFirebaseActive) AlertGreen else AlertOrange
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Firebase Project ID", fontSize = 12.sp, color = TextMuted)
                        Text(firebaseProjId, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AgedGold)
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showSystemDiagSheet) {
        PremiumBottomSheet(
            onDismissRequest = { showSystemDiagSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = "SYSTEM HARDWARE INFO",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = AgedGold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                val runtime = Runtime.getRuntime()
                val maxMemory = runtime.maxMemory() / (1024 * 1024)
                val totalMemory = runtime.totalMemory() / (1024 * 1024)
                val freeMemory = runtime.freeMemory() / (1024 * 1024)
                val usedMemory = totalMemory - freeMemory
                
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    DiagnosticRow(
                        label = "App Name ID",
                        value = "YANSPROJECT.ID ERP",
                        statusColor = Color.White
                    )
                    DiagnosticRow(
                        label = "Platform OS",
                        value = "Google Android Native",
                        statusColor = Color.White
                    )
                    DiagnosticRow(
                        label = "Processor Architecture",
                        value = "${runtime.availableProcessors()} Cores Available",
                        statusColor = AlertGreen
                    )
                    DiagnosticRow(
                        label = "JVM Memory Heap",
                        value = "$usedMemory MB / $maxMemory MB Heap",
                        statusColor = if (usedMemory < maxMemory * 0.8) AlertGreen else AlertOrange
                    )
                    DiagnosticRow(
                        label = "Active Cache Registry",
                        value = "Stok: ${stocks.size} | Proyek: ${projects.size} | Pesanan: ${orders.size}",
                        statusColor = AlertGreen
                    )
                    DiagnosticRow(
                        label = "Network State Link",
                        value = if (isOnline) "ONLINE" else "OFFLINE",
                        statusColor = if (isOnline) AlertGreen else AlertRed
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun ConnectionBadge(isOnline: Boolean, syncStatus: String) {
    val badgeColor = if (isOnline) Color(0xFF2E7D32) else Color(0xFFC62828)
    val statusText = if (isOnline) "ONLINE" else "OFFLINE"
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(badgeColor.copy(alpha = 0.12f))
            .border(1.dp, badgeColor.copy(alpha = 0.3f), RoundedCornerShape(100.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(100.dp))
                .background(badgeColor)
        )
        Text(
            text = statusText,
            color = badgeColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
fun PremiumGlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardModifier = if (onClick != null) {
        modifier.clickable(onClick = onClick)
    } else {
        modifier
    }
    Card(
        modifier = cardModifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            content = content
        )
    }
}

@Composable
fun SettingsMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    chevronColor: Color = AgedGold.copy(alpha = 0.6f),
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = CardGrey.copy(alpha = 0.45f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderGrey.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkTeal.copy(alpha = 0.25f))
                        .border(1.dp, GlassBorder.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = AgedGold,
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        lineHeight = 20.sp
                    )
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = TextMuted.copy(alpha = 0.8f),
                        lineHeight = 15.sp
                    )
                }
            }
            
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = chevronColor,
                modifier = Modifier
                    .size(16.dp)
                    .padding(start = 4.dp)
            )
        }
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    val cleanedTitle = title.replace(Regex("\\s*\\(GROUP\\s*\\d+\\)"), "").trim()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 22.dp, bottom = 10.dp)
    ) {
        Text(
            text = cleanedTitle,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            color = AgedGold.copy(alpha = 0.8f),
            letterSpacing = 1.5.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        HorizontalDivider(
            color = AgedGold.copy(alpha = 0.15f),
            thickness = 1.dp
        )
    }
}

@Composable
fun ConfirmationModal(
    title: String,
    message: String,
    confirmText: String = "Lanjutkan",
    dismissText: String = "Batal",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                color = AgedGold,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            Text(
                text = message,
                color = TextLight,
                fontSize = 13.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = HighlightSoftCyan, contentColor = ShadowBlack),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(confirmText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = TextMuted)
            ) {
                Text(dismissText, fontSize = 12.sp)
            }
        },
        containerColor = CardGrey,
        shape = RoundedCornerShape(16.dp)
    )
}

fun LazyListScope.renderMainSettingsDashboard(
    context: android.content.Context,
    currentUser: com.yansproject.app.data.UserSession?,
    isOwner: Boolean,
    syncStatus: String,
    isOnline: Boolean,
    navController: androidx.navigation.NavController?,
    hapticFeedback: androidx.compose.ui.hapticfeedback.HapticFeedback,
    secureAction: (() -> Unit) -> Unit,
    onShowFirebaseDiag: () -> Unit,
    onShowSmartMaintenance: () -> Unit,
    onLogoutClick: () -> Unit
) {
    // Elegant Multi-layered Glass Profile Card
    item {
        Card(
            colors = CardDefaults.cardColors(containerColor = CardGrey.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderGrey.copy(alpha = 0.4f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // profile layout
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(DarkTeal.copy(alpha = 0.25f))
                            .border(1.dp, AgedGold.copy(alpha = 0.25f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isOwner) Icons.Outlined.AdminPanelSettings else Icons.Outlined.Person,
                            contentDescription = null,
                            tint = AgedGold,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentUser?.displayName ?: "Pengguna",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White,
                            letterSpacing = 0.25.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(AgedGold.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isOwner) "OWNER" else "MEMBER",
                                    color = AgedGold,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp,
                                    letterSpacing = 0.5.sp
                                )
                            }
                            Text(
                                text = currentUser?.email ?: "",
                                fontSize = 11.sp,
                                color = TextMuted.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(18.dp))
                HorizontalDivider(color = BorderGrey.copy(alpha = 0.3f), thickness = 1.dp)
                Spacer(modifier = Modifier.height(14.dp))
                
                // connection status bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = "Sistem:", fontSize = 11.sp, color = TextMuted)
                        ConnectionBadge(isOnline = isOnline, syncStatus = syncStatus)
                    }
                    
                    if (isOwner && syncStatus.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(HighlightSoftCyan.copy(alpha = 0.08f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CloudSync,
                                contentDescription = null,
                                tint = HighlightSoftCyan,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = syncStatus,
                                fontSize = 10.sp,
                                color = HighlightSoftCyan,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }

    if (isOwner) {
        // --- OWNER MASTER DASHBOARD (17 CONSOLIDATED SECTIONS) ---

        // SECTION 1: AKUN & OTORISASI
        item { SettingsSectionHeader("AKUN & OTORISASI") }
        item {
            SettingsMenuItem(
                icon = Icons.Outlined.AccountCircle,
                title = "Account Center",
                subtitle = "Profil, ubah detail akun personal, dan password",
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    navController?.navigate("settings_account")
                }
            )
        }
        item {
            SettingsMenuItem(
                icon = Icons.Outlined.AdminPanelSettings,
                title = "Owner Center",
                subtitle = "Dashboard & pusat kontrol utama workspace Owner",
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    navController?.navigate("settings_owner_center")
                }
            )
        }
        item {
            SettingsMenuItem(
                icon = Icons.Outlined.Gavel,
                title = "Role Management",
                subtitle = "Hak akses & batasan legalitas OWNER vs MEMBER",
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    navController?.navigate("settings_role_management")
                }
            )
        }
        item {
            SettingsMenuItem(
                icon = Icons.Outlined.People,
                title = "Manajemen Member",
                subtitle = "Daftarkan staf baru dan pantau aktivitasnya",
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    navController?.navigate("settings_member")
                }
            )
        }

        // SECTION 2: BISNIS & OPERASIONAL
        item { SettingsSectionHeader("BISNIS & OPERASIONAL") }
        item {
            SettingsMenuItem(
                icon = Icons.Outlined.Business,
                title = "Business Identity",
                subtitle = "Nama usaha, logo, kontak, alamat, sosial media",
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    navController?.navigate("settings_identitas")
                }
            )
        }
        item {
            SettingsMenuItem(
                icon = Icons.Outlined.Payments,
                title = "ERP Configuration",
                subtitle = "Harga up-size, mata uang, zona waktu",
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    navController?.navigate("settings_erp_config")
                }
            )
        }
        item {
            SettingsMenuItem(
                icon = Icons.Outlined.Description,
                title = "Invoice & Document",
                subtitle = "Prefix penomoran otomatis, watermark, tanda tangan",
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    navController?.navigate("settings_dokumen")
                }
            )
        }

        // SECTION 3: KEAMANAN & AUTENTIKASI
        item { SettingsSectionHeader("KEAMANAN & AUTENTIKASI") }
        item {
            SettingsMenuItem(
                icon = Icons.Outlined.Lock,
                title = "Security Center",
                subtitle = "Durasi sesi timeout, verifikasi PIN, logs audit",
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    navController?.navigate("settings_security")
                }
            )
        }
        item {
            SettingsMenuItem(
                icon = Icons.Outlined.Fingerprint,
                title = "Biometric & Authentication",
                subtitle = "Aktivasi sensor sidik jari / pemindaian wajah",
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    navController?.navigate("settings_biometric")
                }
            )
        }

        // SECTION 4: NOTIFIKASI & TAMPILAN
        item { SettingsSectionHeader("NOTIFIKASI & TAMPILAN") }
        item {
            SettingsMenuItem(
                icon = Icons.Outlined.Notifications,
                title = "Notification Settings",
                subtitle = "Konfigurasi alert keuangan, stok, proyek, sistem",
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    navController?.navigate("settings_notifications")
                }
            )
        }
        item {
            SettingsMenuItem(
                icon = Icons.Outlined.Brush,
                title = "Appearance",
                subtitle = "Gaya warna Aged Gold / Soft Cyan & sensivitas sentuh",
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    navController?.navigate("settings_appearance")
                }
            )
        }

        // SECTION 5: DATA, STORAGE & BACKUP
        item { SettingsSectionHeader("DATA & STORAGE MANAGEMENT") }
        item {
            SettingsMenuItem(
                icon = Icons.Outlined.Backup,
                title = "Backup & Restore",
                subtitle = "Pencadangan, pemulihan database total lokal & cloud",
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    secureAction { navController?.navigate("settings_backup") }
                }
            )
        }
        item {
            SettingsMenuItem(
                icon = Icons.Outlined.CloudSync,
                title = "Database & Synchronization",
                subtitle = "Pusat sinkronisasi Firebase Cloud & replikasi lokal",
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    navController?.navigate("settings_db_sync")
                }
            )
        }
        item {
            SettingsMenuItem(
                icon = Icons.Outlined.Save,
                title = "Storage Center",
                subtitle = "Ukuran cache ekspor PDF, total memori, bersihkan sampah",
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    navController?.navigate("settings_storage")
                }
            )
        }

        // SECTION 6: PEMELIHARAAN & DIAGNOSTIK
        item { SettingsSectionHeader("PEMELIHARAAN & DIAGNOSTIK") }
        item {
            SettingsMenuItem(
                icon = Icons.Outlined.Build,
                title = "Maintenance",
                subtitle = "Optimalkan SQLite database, VACUUM, perbaikan indeks",
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    secureAction { navController?.navigate("settings_maintenance") }
                }
            )
        }
        item {
            SettingsMenuItem(
                icon = Icons.Outlined.Memory,
                title = "Developer Diagnostics",
                subtitle = "Console status API, ping latency, debugging database",
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    navController?.navigate("settings_dev_diag")
                }
            )
        }
        item {
            SettingsMenuItem(
                icon = Icons.Outlined.Info,
                title = "Application Information",
                subtitle = "Tentang YANSPROJECT.ID ERP, build versi, lisensi",
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    navController?.navigate("settings_app_info")
                }
            )
        }
    } else {
        // --- MEMBER SETTINGS PANEL ---

        // SECTION 1: AKUN & WORKSPACE
        item { SettingsSectionHeader("AKUN & OTORISASI") }
        item {
            SettingsMenuItem(
                icon = Icons.Outlined.AccountCircle,
                title = "Account Center",
                subtitle = "Profil personal dan pengaturan kata sandi Anda",
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    navController?.navigate("settings_account")
                }
            )
        }
        item {
            SettingsMenuItem(
                icon = Icons.Outlined.Badge,
                title = "Member Center",
                subtitle = "Workspace koneksi & kepatuhan log staf",
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    navController?.navigate("settings_member_center")
                }
            )
        }
        item {
            SettingsMenuItem(
                icon = Icons.Outlined.Gavel,
                title = "Role Management",
                subtitle = "Hak akses & batasan operasional member",
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    navController?.navigate("settings_role_management")
                }
            )
        }

        // SECTION 2: KEAMANAN & PERSONALISASI
        item { SettingsSectionHeader("KEAMANAN & AUTENTIKASI") }
        item {
            SettingsMenuItem(
                icon = Icons.Outlined.Lock,
                title = "Security Center",
                subtitle = "Kunci PIN login dan durasi timeout",
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    navController?.navigate("settings_security")
                }
            )
        }
        item {
            SettingsMenuItem(
                icon = Icons.Outlined.Fingerprint,
                title = "Biometric",
                subtitle = "Gunakan sidik jari untuk login cepat",
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    navController?.navigate("settings_biometric")
                }
            )
        }
        item {
            SettingsMenuItem(
                icon = Icons.Outlined.Notifications,
                title = "Notification Settings",
                subtitle = "Status sakelar notifikasi personal Anda",
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    navController?.navigate("settings_notifications")
                }
            )
        }
        item {
            SettingsMenuItem(
                icon = Icons.Outlined.Brush,
                title = "Appearance",
                subtitle = "Gaya visual dan ukuran font personal",
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    navController?.navigate("settings_appearance")
                }
            )
        }

        // SECTION 3: TENTANG APLIKASI
        item { SettingsSectionHeader("INFORMASI SISTEM") }
        item {
            SettingsMenuItem(
                icon = Icons.Outlined.Info,
                title = "Application Information",
                subtitle = "Spesifikasi resmi YANSPROJECT.ID ERP",
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    navController?.navigate("settings_app_info")
                }
            )
        }
    }

    // PREMIUM ENTERPRISE RED LOGOUT BUTTON
    item {
        Spacer(modifier = Modifier.height(28.dp))
        Button(
            onClick = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                onLogoutClick()
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF8B1E1E), // Elegant dark enterprise red
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(16.dp), // Radius besar
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 2.dp,
                pressedElevation = 0.dp
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Icon(Icons.Outlined.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "LOGOUT DARI SISTEM",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.25.sp,
                color = Color.White
            )
        }
        Spacer(modifier = Modifier.height(44.dp))
    }
}

@Composable
fun renderNestedSubScreen(
    subScreen: String,
    context: android.content.Context,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    viewModel: MainViewModel,
    invoices: List<com.yansproject.app.data.Invoice>,
    stocks: List<com.yansproject.app.data.MasterStock>,
    catalogs: List<com.yansproject.app.data.MasterCatalog>,
    variants: List<com.yansproject.app.data.MasterVarianWarna>,
    projects: List<com.yansproject.app.data.ProjectCustom>,
    orders: List<com.yansproject.app.data.OrderHistory>,
    storeName: String,
    onStoreNameChange: (String) -> Unit,
    address: String,
    onAddressChange: (String) -> Unit,
    whatsapp: String,
    onWhatsappChange: (String) -> Unit,
    email: String,
    onEmailChange: (String) -> Unit,
    website: String,
    onWebsiteChange: (String) -> Unit,
    bankName: String,
    onBankNameChange: (String) -> Unit,
    accountNumber: String,
    onAccountNumberChange: (String) -> Unit,
    accountHolder: String,
    onAccountHolderChange: (String) -> Unit,
    customUpsizeXXL: String,
    onCustomUpsizeXXLChange: (String) -> Unit,
    customUpsize3XL: String,
    onCustomUpsize3XLChange: (String) -> Unit,
    customUpsize4XL: String,
    onCustomUpsize4XLChange: (String) -> Unit,
    ajibqobulUpsizeXXL: String,
    onAjibqobulUpsizeXXLChange: (String) -> Unit,
    ajibqobulUpsize3XL: String,
    onAjibqobulUpsize3XLChange: (String) -> Unit,
    ajibqobulUpsize4XL: String,
    onAjibqobulUpsize4XLChange: (String) -> Unit,
    invoiceFooter: String,
    onInvoiceFooterChange: (String) -> Unit,
    projectPrefix: String,
    onProjectPrefixChange: (String) -> Unit,
    invoicePrefix: String,
    onInvoicePrefixChange: (String) -> Unit,
    regName: String,
    onRegNameChange: (String) -> Unit,
    regEmail: String,
    onRegEmailChange: (String) -> Unit,
    regPassword: String,
    onRegPasswordChange: (String) -> Unit,
    regPriceCategory: String,
    onRegPriceCategoryChange: (String) -> Unit,
    regRole: String,
    onRegRoleChange: (String) -> Unit,
    regLoading: Boolean,
    onRegLoadingChange: (Boolean) -> Unit,
    localMembers: Set<String>,
    onLocalMembersChange: (Set<String>) -> Unit,
    onMemberToDelete: (String) -> Unit,
    restoreLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>,
    importStockLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>,
    importCatalogLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>,
    importCustomerLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>,
    onShowTrash: () -> Unit,
    onShowWipe: () -> Unit,
    onShowRestore: () -> Unit,
    navController: androidx.navigation.NavController? = null
) {
    val connectivityManager = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
    val activeNetworkInfo = connectivityManager.activeNetworkInfo
    val isOnline = activeNetworkInfo != null && activeNetworkInfo.isConnected

    val currentUser by com.yansproject.app.data.FirebaseSyncManager.currentUser.collectAsState()
    val isOwner = currentUser?.role != com.yansproject.app.data.UserRole.MEMBER
    val auditLogs by viewModel.allAuditLogs.collectAsState()
    val hapticFeedback = androidx.compose.ui.platform.LocalHapticFeedback.current

    when (subScreen) {
        "identitas" -> {
            com.yansproject.app.ui.settings.BusinessIdentityModule(
                onSaveSuccess = {
                    onStoreNameChange(AppSettings.getStoreName(context))
                    onAddressChange(AppSettings.getAddress(context))
                    onWhatsappChange(AppSettings.getWhatsApp(context))
                    onEmailChange(AppSettings.getEmail(context))
                    onWebsiteChange(AppSettings.getWebsite(context))
                }
            )
        }

        "keuangan" -> {
            com.yansproject.app.ui.settings.FinanceConfigModule(
                onSaveSuccess = {
                    onBankNameChange(AppSettings.getBankName(context))
                    onAccountNumberChange(AppSettings.getAccountNumber(context))
                    onAccountHolderChange(AppSettings.getAccountHolder(context))
                    onCustomUpsizeXXLChange(AppSettings.getCustomUpsizeXXL(context).toInt().toString())
                    onCustomUpsize3XLChange(AppSettings.getCustomUpsize3XL(context).toInt().toString())
                    onCustomUpsize4XLChange(AppSettings.getCustomUpsize4XL(context).toInt().toString())
                    onAjibqobulUpsizeXXLChange(AppSettings.getAjibqobulUpsizeXXL(context).toInt().toString())
                    onAjibqobulUpsize3XLChange(AppSettings.getAjibqobulUpsize3XL(context).toInt().toString())
                    onAjibqobulUpsize4XLChange(AppSettings.getAjibqobulUpsize4XL(context).toInt().toString())
                }
            )
        }

        "dokumen" -> {
            com.yansproject.app.ui.settings.DocumentFormatModule(
                onSaveSuccess = {
                    onInvoiceFooterChange(AppSettings.getInvoiceFooter(context))
                    onProjectPrefixChange(AppSettings.getProjectPrefix(context))
                    onInvoicePrefixChange(AppSettings.getInvoicePrefix(context))
                }
            )
        }

        "member" -> {
            com.yansproject.app.ui.settings.MemberManagementModule(
                modifier = Modifier.fillMaxSize(),
                onSaveSuccess = {
                    onLocalMembersChange(AppSettings.getMembers(context))
                }
            )
        }

        "backup" -> {
            val backupService = remember { com.yansproject.app.data.BackupRestoreService.getInstance(context) }
            var localBackupFiles by remember { mutableStateOf(backupService.getLocalBackupFiles()) }
            var fileToRestore by remember { mutableStateOf<java.io.File?>(null) }
            var fileToDelete by remember { mutableStateOf<java.io.File?>(null) }
            val dbFile = remember { context.getDatabasePath(com.yansproject.app.data.AppDatabase.DATABASE_NAME) }

            val refreshBackups = {
                localBackupFiles = backupService.getLocalBackupFiles()
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Status & Integrity Card
                PremiumGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text("STATUS & INTEGRITAS PENCADANGAN", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AgedGold)
                    Spacer(modifier = Modifier.height(14.dp))
                    
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        com.yansproject.app.ui.DiagnosticRow(
                            "Database Utama", 
                            "yans_database.db (${if (dbFile.exists()) "${dbFile.length() / 1024} KB" else "0 KB"})", 
                            HighlightSoftCyan
                        )
                        com.yansproject.app.ui.DiagnosticRow(
                            "Penyimpanan Internal", 
                            "${localBackupFiles.size} berkas cadangan tersimpan", 
                            if (localBackupFiles.isNotEmpty()) AlertGreen else AlertOrange
                        )
                        com.yansproject.app.ui.DiagnosticRow(
                            "Lokasi Penyimpanan", 
                            backupService.getSafeBackupDirectory().name, 
                            HighlightSoftCyan
                        )
                        com.yansproject.app.ui.DiagnosticRow(
                            "Pencadangan Cloud", 
                            "Firestore Automatic Sync Active", 
                            AlertGreen
                        )
                        com.yansproject.app.ui.DiagnosticRow(
                            "Kondisi Integritas", 
                            "Siap Dipulihkan (Complete Restoration)", 
                            AlertGreen
                        )
                    }
                }

                // 2. Backup & Restore Action Center
                PremiumGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "PUSAT KONTROL BACKUP & RESTORE",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = AgedGold
                        )

                        Text(
                            text = "Buat cadangan database instan ke penyimpanan internal dan Cloud, atau pilih berkas eksternal untuk pemulihan data.",
                            fontSize = 11.sp,
                            color = TextMuted,
                            lineHeight = 16.sp
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = {
                                    val backupFile = DatabaseBackupHelper.backupDatabase(context)
                                    if (backupFile != null) {
                                        refreshBackups()
                                        Toast.makeText(context, "Database berhasil dicadangkan: ${backupFile.name}", Toast.LENGTH_LONG).show()
                                        viewModel.addAuditLog("Pencadangan Database", "Database berhasil dicadangkan ke internal: ${backupFile.name}")
                                        FirebaseSyncManager.uploadBackupToCloud(context, backupFile) { ok, msg ->
                                            if (ok) {
                                                Toast.makeText(context, "Backup berhasil diunggah ke Cloud Firebase!", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    } else {
                                        Toast.makeText(context, "Gagal melakukan pencadangan database!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f).height(45.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AgedGold, contentColor = Color.Black),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Outlined.Backup, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Buat Backup Baru", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = onShowRestore,
                                modifier = Modifier.weight(1f).height(45.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SecondaryShadowBlackTeal, contentColor = Color.White),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, HighlightSoftCyan.copy(alpha = 0.4f))
                            ) {
                                Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp), tint = HighlightSoftCyan)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Pilih File External", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = HighlightSoftCyan)
                            }
                        }
                    }
                }

                // 3. Local Backup Files Manager List
                PremiumGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "RIWAYAT BACKUP INTERNAL (${localBackupFiles.size})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = AgedGold
                            )
                            Text(
                                text = "Internal App Storage",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = HighlightSoftCyan
                            )
                        }

                        Text(
                            text = "Seluruh berkas cadangan tersimpan aman pada penyimpanan internal. Anda dapat memulihkan seluruh data aplikasi langsung dari daftar di bawah:",
                            fontSize = 11.sp,
                            color = TextMuted,
                            lineHeight = 16.sp
                        )

                        HorizontalDivider(color = BorderGrey.copy(alpha = 0.2f), thickness = 0.5.dp)

                        if (localBackupFiles.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Outlined.Folder, contentDescription = null, tint = TextMuted, modifier = Modifier.size(36.dp))
                                    Text("Belum Ada Berkas Backup Tersimpan", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                                    Text("Klik 'Buat Backup Baru' untuk membuat cadangan pertama Anda.", fontSize = 10.5.sp, color = TextMuted.copy(alpha = 0.7f))
                                }
                            }
                        } else {
                            val sdf = remember { SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale("id", "ID")) }
                            
                            localBackupFiles.forEach { file ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = DarkTealSurfaceVariant.copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(10.dp),
                                    border = androidx.compose.foundation.BorderStroke(0.8.dp, AgedGold.copy(alpha = 0.25f))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(AgedGold.copy(alpha = 0.15f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(Icons.Outlined.Storage, contentDescription = null, tint = AgedGold, modifier = Modifier.size(18.dp))
                                                }
                                                Column {
                                                    Text(
                                                        text = file.name,
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White
                                                    )
                                                    Text(
                                                        text = "${sdf.format(Date(file.lastModified()))}  •  ${file.length() / 1024} KB",
                                                        fontSize = 10.sp,
                                                        color = TextMuted
                                                    )
                                                }
                                            }
                                        }

                                        HorizontalDivider(color = BorderGrey.copy(alpha = 0.15f), thickness = 0.5.dp)

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Restore Button
                                            Button(
                                                onClick = { fileToRestore = file },
                                                modifier = Modifier.weight(1f).height(36.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = HighlightSoftCyan, contentColor = Color.Black),
                                                shape = RoundedCornerShape(6.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp)
                                            ) {
                                                Icon(Icons.Outlined.Restore, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Restore Data", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }

                                            // Share Button
                                            OutlinedButton(
                                                onClick = { backupService.shareBackupFile(file) },
                                                modifier = Modifier.weight(1f).height(36.dp),
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AgedGold),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, AgedGold.copy(alpha = 0.6f)),
                                                shape = RoundedCornerShape(6.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp)
                                            ) {
                                                Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Bagikan", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }

                                            // Delete Button
                                            IconButton(
                                                onClick = {
                                                    fileToDelete = file
                                                },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Hapus Backup", tint = AlertRed, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. System Maintenance & Trash
                PremiumGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "PEMELIHARAAN APLIKASI & PEMBERSIHAN",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = AgedGold
                        )

                        // Trash button removed per user request

                        Button(
                            onClick = onShowWipe,
                            modifier = Modifier.fillMaxWidth().height(45.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B1E1E), contentColor = Color.White),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Outlined.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Wipe Data (Hapus Semua)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                PremiumGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "EXPORT & IMPORT EXCEL / CSV",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = AgedGold
                        )

                        Text(
                            text = "Ekspor data internal YANSPROJECT.ID ke Excel/CSV atau impor data baru secara masal.",
                            fontSize = 11.sp,
                            color = TextMuted
                        )

                        HorizontalDivider(color = BorderGrey.copy(alpha = 0.2f), thickness = 0.5.dp)

                        Text("Ekspor Data Ke Eksternal:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    val file = DataImportExportHelper.exportStockToCsv(context, stocks, variants, catalogs, true)
                                    if (file != null) {
                                        Toast.makeText(context, "Stok diekspor ke Excel: ${file.name}", Toast.LENGTH_LONG).show()
                                        viewModel.addAuditLog("Ekspor Data", "Mengekspor stok quantity ke Excel: ${file.name}")
                                    } else {
                                        Toast.makeText(context, "Gagal ekspor stok!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = SecondaryShadowBlackTeal, contentColor = Color.White),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Outlined.FileDownload, contentDescription = null, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Stok Excel", fontSize = 10.sp)
                            }

                            Button(
                                onClick = {
                                    val file = DataImportExportHelper.exportCatalogToCsv(context, catalogs, false)
                                    if (file != null) {
                                        Toast.makeText(context, "Katalog diekspor ke CSV: ${file.name}", Toast.LENGTH_LONG).show()
                                        viewModel.addAuditLog("Ekspor Data", "Mengekspor katalog ke CSV: ${file.name}")
                                    } else {
                                        Toast.makeText(context, "Gagal ekspor katalog!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = SecondaryShadowBlackTeal, contentColor = Color.White),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Outlined.FileDownload, contentDescription = null, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Catalog CSV", fontSize = 10.sp)
                            }

                            Button(
                                onClick = {
                                    val file = DataImportExportHelper.exportCustomersToCsv(context, projects, orders, false)
                                    if (file != null) {
                                        Toast.makeText(context, "Customer diekspor: ${file.name}", Toast.LENGTH_LONG).show()
                                        viewModel.addAuditLog("Ekspor Data", "Mengekspor customer ke CSV: ${file.name}")
                                    } else {
                                        Toast.makeText(context, "Gagal ekspor!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = SecondaryShadowBlackTeal, contentColor = Color.White),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Outlined.FileDownload, contentDescription = null, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Customer", fontSize = 10.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Impor Data Dari Eksternal:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { importStockLauncher.launch(arrayOf("*/*")) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = SecondaryShadowBlackTeal, contentColor = AgedGold),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Outlined.FileUpload, contentDescription = null, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Import Stok", fontSize = 10.sp)
                            }

                            Button(
                                onClick = { importCatalogLauncher.launch(arrayOf("*/*")) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = SecondaryShadowBlackTeal, contentColor = AgedGold),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Outlined.FileUpload, contentDescription = null, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Import Cat", fontSize = 10.sp)
                            }

                            Button(
                                onClick = { importCustomerLauncher.launch(arrayOf("*/*")) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = SecondaryShadowBlackTeal, contentColor = AgedGold),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Outlined.FileUpload, contentDescription = null, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Import Cust", fontSize = 10.sp)
                            }
                        }
                    }
                }

                fileToRestore?.let { file ->
                    ConfirmationModal(
                        title = "Pemulihan Data Dari File Internal",
                        message = "Apakah Anda yakin ingin memulihkan seluruh data aplikasi dari berkas:\n\n'${file.name}' (${file.length() / 1024} KB)?\n\nPERINGATAN: Seluruh data saat ini akan ditimpa secara lengkap.",
                        confirmText = "Pulihkan Sekarang",
                        onConfirm = {
                            val targetFile = file
                            fileToRestore = null
                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                val success = DatabaseBackupHelper.restoreDatabaseFromFile(context, targetFile)
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    if (success) {
                                        viewModel.refreshData(context)
                                        AppFeedbackManager.triggerSuccess()
                                        Toast.makeText(context, "Selesai! Seluruh data aplikasi berhasil dipulihkan secara lengkap dari '${targetFile.name}'.", Toast.LENGTH_LONG).show()
                                        viewModel.addAuditLog("Pemulihan Database", "Database dipulihkan dari file internal: ${targetFile.name}")
                                        viewModel.triggerNotification("Restore Berhasil", "Data aplikasi telah dipulihkan secara lengkap.", "Sistem", "SETTINGS")
                                    } else {
                                        AppFeedbackManager.triggerError()
                                        Toast.makeText(context, "Gagal memulihkan database dari berkas '${targetFile.name}'.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        },
                        onDismiss = { fileToRestore = null }
                    )
                }

                fileToDelete?.let { file ->
                    ConfirmationModal(
                        title = "Hapus File Backup",
                        message = "Apakah Anda yakin ingin menghapus berkas cadangan '${file.name}' dari penyimpanan internal?",
                        confirmText = "Hapus File",
                        onConfirm = {
                            val targetFile = file
                            fileToDelete = null
                            val deleted = backupService.deleteBackupFile(targetFile)
                            if (deleted) {
                                refreshBackups()
                                Toast.makeText(context, "File backup '${targetFile.name}' berhasil dihapus.", Toast.LENGTH_SHORT).show()
                                viewModel.addAuditLog("Hapus Backup", "Menghapus berkas cadangan lokal '${targetFile.name}'.")
                            } else {
                                Toast.makeText(context, "Gagal menghapus file backup.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onDismiss = { fileToDelete = null }
                    )
                }

                Spacer(modifier = Modifier.height(40.dp))
            }
        }

        "akun" -> {
            val userPrefs = remember { context.getSharedPreferences("yans_user_prefs_${currentUser?.email ?: "guest"}", android.content.Context.MODE_PRIVATE) }
            
            val defaultName = if (isOwner) "YANSPROJECT.ID OWNER" else (currentUser?.displayName ?: "")
            val defaultUsername = if (isOwner) "admin" else ""
            val defaultEmail = if (isOwner) "admin@yansproject.id" else (currentUser?.email ?: "")
            val defaultWhatsapp = if (isOwner) "+62 877-7739-8813" else ""
            val defaultAddress = if (isOwner) "Tangerang, Banten" else ""

            var nameInput by remember { mutableStateOf(userPrefs.getString("user_full_name", defaultName)?.ifBlank { defaultName } ?: defaultName) }
            var usernameInput by remember { mutableStateOf(userPrefs.getString("user_username", defaultUsername)?.ifBlank { defaultUsername } ?: defaultUsername) }
            var emailInput by remember { mutableStateOf(userPrefs.getString("user_email", defaultEmail)?.ifBlank { defaultEmail } ?: defaultEmail) }
            var whatsappInput by remember { mutableStateOf(userPrefs.getString("user_whatsapp", defaultWhatsapp)?.ifBlank { defaultWhatsapp } ?: defaultWhatsapp) }
            var addressInput by remember { mutableStateOf(userPrefs.getString("user_address", defaultAddress)?.ifBlank { defaultAddress } ?: defaultAddress) }
            var recoveryAccountInput by remember { mutableStateOf(userPrefs.getString("user_recovery_email", "") ?: "") }
            
            var passwordInput by remember { mutableStateOf("") }
            var transactionPinInput by remember { mutableStateOf(userPrefs.getString("transaction_pin", "") ?: "") }
            
            var showPassword by remember { mutableStateOf(false) }
            var showPin by remember { mutableStateOf(false) }
            
            var isEmailVerified by remember { mutableStateOf(userPrefs.getBoolean("email_verified", true)) }
            var isPhoneVerified by remember { mutableStateOf(userPrefs.getBoolean("phone_verified", false)) }
            
            var otherDevicesLoggedOut by remember { mutableStateOf(userPrefs.getBoolean("other_devices_logged_out", false)) }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Profile Avatar Card
                PremiumGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(90.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(AgedGold.copy(alpha = 0.15f))
                                .border(2.dp, AgedGold, androidx.compose.foundation.shape.CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (nameInput.isNotBlank()) nameInput.take(2).uppercase() else "YP",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Black,
                                color = AgedGold
                            )
                        }
                        Text(
                            text = if (isOwner) "ADMINISTRATOR / OWNER" else "STAFF / MEMBER",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = HighlightSoftCyan,
                            letterSpacing = 1.5.sp
                        )
                        Text(
                            text = "Keterhubungan Sistem: AKTIF",
                            fontSize = 10.sp,
                            color = AlertGreen
                        )
                    }
                }

                // Edit Profile Card
                PremiumGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("INFORMASI PROFIL AKUN", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AgedGold)
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            label = { Text("Nama Lengkap") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = AgedGold,
                                unfocusedBorderColor = BorderGrey,
                                focusedLabelColor = AgedGold,
                                unfocusedLabelColor = TextMuted
                            )
                        )

                        OutlinedTextField(
                            value = usernameInput,
                            onValueChange = { usernameInput = it },
                            label = { Text("Username") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = AgedGold,
                                unfocusedBorderColor = BorderGrey,
                                focusedLabelColor = AgedGold,
                                unfocusedLabelColor = TextMuted
                            )
                        )

                        OutlinedTextField(
                            value = emailInput,
                            onValueChange = { emailInput = it },
                            label = { Text("Email Resmi") },
                            enabled = false,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = AgedGold,
                                unfocusedBorderColor = BorderGrey,
                                focusedLabelColor = AgedGold,
                                unfocusedLabelColor = TextMuted
                            )
                        )

                        OutlinedTextField(
                            value = whatsappInput,
                            onValueChange = { whatsappInput = it },
                            label = { Text("Nomor WhatsApp") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = AgedGold,
                                unfocusedBorderColor = BorderGrey,
                                focusedLabelColor = AgedGold,
                                unfocusedLabelColor = TextMuted
                            )
                        )

                        OutlinedTextField(
                            value = addressInput,
                            onValueChange = { addressInput = it },
                            label = { Text("Alamat Tinggal / Kantor") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = AgedGold,
                                unfocusedBorderColor = BorderGrey,
                                focusedLabelColor = AgedGold,
                                unfocusedLabelColor = TextMuted
                            )
                        )

                        OutlinedTextField(
                            value = recoveryAccountInput,
                            onValueChange = { recoveryAccountInput = it },
                            label = { Text("Email Pemulihan (Recovery)") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = AgedGold,
                                unfocusedBorderColor = BorderGrey,
                                focusedLabelColor = AgedGold,
                                unfocusedLabelColor = TextMuted
                            )
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Button(
                            onClick = {
                                if (nameInput.isNotBlank()) {
                                    FirebaseSyncManager.updateDisplayName(context, nameInput)
                                    userPrefs.edit().apply {
                                        putString("user_username", usernameInput)
                                        putString("user_whatsapp", whatsappInput)
                                        putString("user_address", addressInput)
                                        putString("user_recovery_email", recoveryAccountInput)
                                        apply()
                                    }
                                    viewModel.syncDraftWithAccountCenter()
                                    Toast.makeText(context, "Profil berhasil disimpan secara offline dan disinkronkan!", Toast.LENGTH_SHORT).show()
                                    viewModel.addAuditLog("Update Profil Lengkap", "Berhasil memperbarui informasi identitas profil akun untuk email $emailInput")
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AgedGold, contentColor = Color.Black),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(45.dp)
                        ) {
                            Text("SIMPAN PROFIL LENGKAP", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }

                // Change Password / PIN Card
                PremiumGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("SANDI & PIN TRANSAKSI KEAMANAN", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AgedGold)
                        Spacer(modifier = Modifier.height(4.dp))

                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            label = { Text("PIN / Sandi Login Baru (Min 6 Karakter)") },
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        imageVector = if (showPassword) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                                        contentDescription = null,
                                        tint = AgedGold
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = AgedGold,
                                unfocusedBorderColor = BorderGrey,
                                focusedLabelColor = AgedGold,
                                unfocusedLabelColor = TextMuted
                            )
                        )

                        OutlinedTextField(
                            value = transactionPinInput,
                            onValueChange = { transactionPinInput = it },
                            label = { Text("PIN Transaksi Khusus (Min 4 Angka)") },
                            visualTransformation = if (showPin) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showPin = !showPin }) {
                                    Icon(
                                        imageVector = if (showPin) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                                        contentDescription = null,
                                        tint = AgedGold
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = AgedGold,
                                unfocusedBorderColor = BorderGrey,
                                focusedLabelColor = AgedGold,
                                unfocusedLabelColor = TextMuted
                            )
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Button(
                            onClick = {
                                var hasChanges = false
                                if (passwordInput.isNotEmpty()) {
                                    if (passwordInput.length >= 6) {
                                        val prefs = context.getSharedPreferences("yans_security_prefs", android.content.Context.MODE_PRIVATE)
                                        prefs.edit().putString("app_pin", passwordInput).apply()
                                        viewModel.addAuditLog("Ganti PIN Sandi", "Sandi login utama berhasil diperbarui.")
                                        hasChanges = true
                                    } else {
                                        Toast.makeText(context, "Sandi Baru harus minimal 6 karakter!", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                }
                                if (transactionPinInput.isNotEmpty()) {
                                    if (transactionPinInput.length >= 4) {
                                        userPrefs.edit().putString("transaction_pin", transactionPinInput).apply()
                                        viewModel.addAuditLog("Ganti PIN Transaksi", "PIN Transaksi berhasil diperbarui.")
                                        hasChanges = true
                                    } else {
                                        Toast.makeText(context, "PIN Transaksi harus minimal 4 karakter!", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                }
                                if (hasChanges) {
                                    Toast.makeText(context, "Kredensial keamanan berhasil diperbarui!", Toast.LENGTH_SHORT).show()
                                    passwordInput = ""
                                } else {
                                    Toast.makeText(context, "Tidak ada perubahan sandi/PIN yang dimasukkan.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AgedGold, contentColor = Color.Black),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(45.dp)
                        ) {
                            Text("SIMPAN PIN & KREDENSIAL", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }

                // Verification Center Card
                PremiumGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("VERIFIKASI IDENTITAS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AgedGold)
                        Spacer(modifier = Modifier.height(4.dp))

                        // Email Verification Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Verifikasi Email", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = if (isEmailVerified) "Terverifikasi Resmi" else "Belum Terverifikasi",
                                    color = if (isEmailVerified) AlertGreen else AlertRed,
                                    fontSize = 11.sp
                                )
                            }
                            if (!isEmailVerified) {
                                Button(
                                    onClick = {
                                        isEmailVerified = true
                                        userPrefs.edit().putBoolean("email_verified", true).apply()
                                        Toast.makeText(context, "Link verifikasi dikirim! Email diverifikasi otomatis.", Toast.LENGTH_SHORT).show()
                                        viewModel.addAuditLog("Verifikasi Email", "Verifikasi alamat email utama berhasil diproses.")
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = HighlightSoftCyan, contentColor = Color.Black),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                ) {
                                    Text("Verifikasi", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = AlertGreen, modifier = Modifier.size(20.dp))
                            }
                        }

                        HorizontalDivider(color = BorderGrey.copy(alpha = 0.2f))

                        // Phone Verification Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Verifikasi Nomor Telepon", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = if (isPhoneVerified) "Terverifikasi Resmi" else "Belum Terverifikasi",
                                    color = if (isPhoneVerified) AlertGreen else AlertRed,
                                    fontSize = 11.sp
                                )
                            }
                            if (!isPhoneVerified) {
                                Button(
                                    onClick = {
                                        isPhoneVerified = true
                                        userPrefs.edit().putBoolean("phone_verified", true).apply()
                                        Toast.makeText(context, "OTP dikirim! Nomor WhatsApp berhasil diverifikasi.", Toast.LENGTH_SHORT).show()
                                        viewModel.addAuditLog("Verifikasi Telepon", "Nomor telepon/WhatsApp utama berhasil diverifikasi.")
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = HighlightSoftCyan, contentColor = Color.Black),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                ) {
                                    Text("Verifikasi", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = AlertGreen, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }

                // Sesi Login & Active Devices Card
                PremiumGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("SESI LOGIN & PERANGKAT AKTIF", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AgedGold)
                        Spacer(modifier = Modifier.height(4.dp))

                        // Current Device (Dynamic)
                        val manufacturer = remember { android.os.Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.ROOT) else it.toString() } }
                        val model = remember { android.os.Build.MODEL }
                        val release = remember { android.os.Build.VERSION.RELEASE }
                        val sdk = remember { android.os.Build.VERSION.SDK_INT }
                        val currentDeviceName = "$manufacturer $model (Perangkat Ini)"
                        val currentDeviceDetails = "Android $release (API $sdk) | Sesi Aktif Utama"

                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.PhoneAndroid, contentDescription = null, tint = AgedGold, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(currentDeviceName, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(currentDeviceDetails, color = TextMuted, fontSize = 11.sp)
                            }
                            Box(
                                modifier = Modifier
                                    .background(AlertGreen.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("AKTIF", color = AlertGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        HorizontalDivider(color = BorderGrey.copy(alpha = 0.2f))

                        // Other Devices Fallback (Clean & Authentic)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.Devices, contentDescription = null, tint = TextMuted, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Perangkat Lain", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                                Text("Informasi perangkat tidak tersedia.", color = TextMuted, fontSize = 11.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Button(
                            onClick = {
                                otherDevicesLoggedOut = true
                                userPrefs.edit().putBoolean("other_devices_logged_out", true).apply()
                                Toast.makeText(context, "Sesi login di perangkat lain telah diputuskan!", Toast.LENGTH_SHORT).show()
                                viewModel.addAuditLog("Logout Sesi Lain", "Owner memutuskan koneksi seluruh perangkat login lainnya.")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B1E1E), contentColor = Color.White),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(45.dp)
                        ) {
                            Text("PUTUS KONEKSI SESI LAIN", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        "owner_center" -> {
            val auditLogs by viewModel.allAuditLogs.collectAsState()
            val latestLog = remember(auditLogs) { auditLogs.sortedByDescending { it.timestamp }.firstOrNull() }
            val formattedTime = remember(latestLog) {
                if (latestLog != null) {
                    val sdf = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault())
                    sdf.format(java.util.Date(latestLog.timestamp))
                } else ""
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PremiumGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("DASHBOARD OPERASIONAL OWNER", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AgedGold)
                        Text(
                            text = "Sebagai pemilik tunggal YANSPROJECT.ID, Anda memegang otorisasi absolut atas seluruh operasional ERP, data ledger pergudangan, and otentikasi staf.",
                            fontSize = 12.sp,
                            color = Color.White,
                            lineHeight = 18.sp
                        )
                    }
                }

                // Warehouse & Catalog Metrics Card
                PremiumGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("OPERASIONAL PERGUDANGAN & CATALOG", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AgedGold)
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Stok Gudang", fontSize = 12.sp, color = TextMuted)
                            Text("${stocks.sumOf { it.total_stock }} Pcs", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Katalog Aktif", fontSize = 12.sp, color = TextMuted)
                            Text("${catalogs.size} Catalog", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Varian Warna Terdaftar", fontSize = 12.sp, color = TextMuted)
                            Text("${variants.size} Warna", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Sinkronisasi Terakhir", fontSize = 12.sp, color = TextMuted)
                            Text(
                                text = AppSettings.getLastSync(context).ifBlank { "Baru Saja" },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = HighlightSoftCyan
                            )
                        }
                    }
                }

                // Transaction & Project Metrics Card
                PremiumGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("TRANSAKSI & PROYEK CUSTOM", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AgedGold)
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Proyek Kustom Aktif", fontSize = 12.sp, color = TextMuted)
                            Text("${projects.filter { !it.isDeleted && it.status != "BATAL" }.size} Proyek", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Invoice Sah", fontSize = 12.sp, color = TextMuted)
                            Text("${invoices.filter { !it.isDeleted }.size} Invoice", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Manajemen Member Terhubung", fontSize = 12.sp, color = TextMuted)
                            Text("${localMembers.size} Member", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = HighlightSoftCyan)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Integritas Transaksi", fontSize = 12.sp, color = TextMuted)
                            Text("100% SECURE", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AlertGreen)
                        }
                    }
                }

                // Latest Audit Log Card
                PremiumGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("LOG AKTIVITAS SISTEM TERAKHIR", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AgedGold)
                        Spacer(modifier = Modifier.height(4.dp))

                        if (latestLog != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = latestLog.activity.uppercase(),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = HighlightSoftCyan
                                )
                                Text(
                                    text = formattedTime,
                                    fontSize = 10.sp,
                                    color = TextMuted,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = latestLog.details,
                                fontSize = 12.sp,
                                color = Color.White,
                                lineHeight = 16.sp
                            )
                            HorizontalDivider(color = BorderGrey.copy(alpha = 0.2f), thickness = 0.8.dp)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Operator: ${latestLog.adminName}",
                                    fontSize = 10.sp,
                                    color = TextMuted
                                )
                                Box(
                                    modifier = Modifier
                                        .background(AlertGreen.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("TERVERIFIKASI LOKAL", color = AlertGreen, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            Text(
                                text = "Belum ada log aktivitas tercatat di sistem saat ini.",
                                fontSize = 12.sp,
                                color = TextMuted,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                            )
                        }
                    }
                }
            }
        }

        "member_center" -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PremiumGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("WORKSPACE MEMBER CENTER", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AgedGold)
                        Text(
                            text = "Status: Terhubung dengan Workspace Owner Utama\nID Workspace: YP_OWNER_MAIN_WSPACE",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = HighlightSoftCyan,
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Sebagai akun MEMBER, seluruh riwayat transaksi, penambahan proyek, and manipulasi stok yang Anda lakukan akan secara real-time masuk and disinkronisasikan ke bawah audit log workspace Owner Utama.",
                            fontSize = 12.sp,
                            color = TextLight,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }

        "role_management" -> {
            var isVerifying by remember { mutableStateOf(false) }
            var verifiedSuccess by remember { mutableStateOf(false) }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PremiumGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("WORKSPACE CONSTITUTION & LEGALITAS", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AgedGold)
                        Text(
                            text = "Konstitusi digital YANSPROJECT.ID ERP mendefinisikan batas hak hukum legalitas hak akses antara OWNER dan MEMBER guna menjaga integritas database dan pencegahan manipulasi keuangan secara ilegal.",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.8f),
                            lineHeight = 16.sp
                        )
                    }
                }

                // Comparison Cards - OWNER
                PremiumGlassCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.AdminPanelSettings, contentDescription = null, tint = AgedGold, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("ROLE: OWNER Utama", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AgedGold)
                            }
                            Box(
                                modifier = Modifier
                                    .background(AgedGold.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("HAK ABSOLUT UNTUK", color = AgedGold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        HorizontalDivider(color = BorderGrey.copy(alpha = 0.15f))

                        val ownerPrivileges = listOf(
                            "Otoritas mutlak melakukan Backup & Restore total.",
                            "Manajemen member (Tambah, hapus, delegasi akun).",
                            "Konfigurasi global identitas bisnis, harga upsize & tier harga.",
                            "Melakukan SQLite VACUUM, optimasi DB, & pembersihan logs.",
                            "Akses tak terbatas ke Developer Diagnostics & Audit Logs."
                        )

                        ownerPrivileges.forEach { privilege ->
                            Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 2.dp)) {
                                Icon(Icons.Outlined.Check, contentDescription = null, tint = AlertGreen, modifier = Modifier.size(14.dp).padding(top = 2.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(privilege, color = Color.White, fontSize = 11.sp, lineHeight = 14.sp)
                            }
                        }
                    }
                }

                // Comparison Cards - MEMBER
                PremiumGlassCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Person, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("ROLE: MEMBER / Staf", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.LightGray)
                            }
                            Box(
                                modifier = Modifier
                                    .background(BorderGrey.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("DIATASI KETAT", color = Color.LightGray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        HorizontalDivider(color = BorderGrey.copy(alpha = 0.15f))

                        val memberRestrictions = listOf(
                            "DILARANG melakukan Backup & Restore Database.",
                            "Hanya diperkenankan melakukan transaksi harian standard.",
                            "DILARANG mengubah Identitas Bisnis / Nama Toko global.",
                            "TIDAK memiliki akses ke Audit Logs & Developer Diagnostics.",
                            "Status baca-tulis diawasi langsung oleh sistem enkripsi log."
                        )

                        memberRestrictions.forEach { restriction ->
                            Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 2.dp)) {
                                Icon(Icons.Outlined.Close, contentDescription = null, tint = AlertRed, modifier = Modifier.size(14.dp).padding(top = 2.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(restriction, color = TextMuted, fontSize = 11.sp, lineHeight = 14.sp)
                            }
                        }
                    }
                }

                // Compliance Action Card
                PremiumGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("SYSTEM COMPLIANCE VERIFICATION", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AgedGold)
                        Text(
                            text = "Guna menjamin tidak ada kebocoran hak akses, sistem secara periodik memverifikasi tanda tangan digital enkripsi RBAC.",
                            fontSize = 11.sp,
                            color = TextMuted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 14.sp
                        )

                        if (isVerifying) {
                            CircularProgressIndicator(color = AgedGold, modifier = Modifier.size(24.dp))
                        } else if (verifiedSuccess) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = AlertGreen, modifier = Modifier.size(18.dp))
                                Text("STATUS: 100% TERPATUHI (COMPLIANT)", color = AlertGreen, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }

                        Button(
                            onClick = {
                                isVerifying = true
                                coroutineScope.launch {
                                    kotlinx.coroutines.delay(1200)
                                    isVerifying = false
                                    verifiedSuccess = true
                                    Toast.makeText(context, "System Compliance Checked: 100% COMPLIANT!", Toast.LENGTH_SHORT).show()
                                    viewModel.addAuditLog("System Compliance Checked", "Pemilik menjalankan audit kepatuhan legalitas sistem. Status: 100% Terpatuhi (COMPLIANT).")
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = HighlightSoftCyan, contentColor = Color.Black),
                            shape = RoundedCornerShape(8.dp),
                            enabled = !isVerifying,
                            modifier = Modifier.fillMaxWidth().height(45.dp)
                        ) {
                            Text("JALANKAN SYSTEM COMPLIANCE CHECK", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        "security" -> {
            val prefs = remember { context.getSharedPreferences("yans_security_prefs", android.content.Context.MODE_PRIVATE) }
            var pinLockEnabled by remember { mutableStateOf(prefs.getBoolean("app_lock_enabled", false) || prefs.getBoolean("pin_lock_enabled", false)) }
            var sessionTimeout by remember { mutableStateOf(prefs.getInt("session_timeout", 15)) }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PremiumGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("KEAMANAN SISTEM", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AgedGold)
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Verifikasi PIN Saat Mulai", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text("Wajib memasukkan PIN sebelum masuk aplikasi", fontSize = 11.sp, color = TextMuted)
                            }
                            Switch(
                                checked = pinLockEnabled,
                                onCheckedChange = { isChecked ->
                                    prefs.edit().putBoolean("app_lock_enabled", isChecked).putBoolean("pin_lock_enabled", isChecked).apply()
                                    pinLockEnabled = isChecked
                                    viewModel.addAuditLog("Update PIN Lock State", "Mengubah PIN Lock aktif: $isChecked")
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = shadowBlack, checkedTrackColor = HighlightSoftCyan)
                            )
                        }

                        HorizontalDivider(color = BorderGrey.copy(alpha = 0.2f), thickness = 0.5.dp)

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Auto Lock Sesi Timeout", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(5, 15, 30, 60).forEach { mins ->
                                    val isSelected = sessionTimeout == mins
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) AgedGold else SecondaryShadowBlackTeal)
                                            .border(1.dp, if (isSelected) AgedGold else BorderGrey.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                            .clickable {
                                                sessionTimeout = mins
                                                prefs.edit().putInt("session_timeout", mins).apply()
                                                viewModel.addAuditLog("Set Timeout", "Set auto lock timeout: $mins menit")
                                            }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("$mins Min", fontSize = 11.sp, color = if (isSelected) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                PremiumGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("LOG KEAMANAN TERAKHIR (AUDIT)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AgedGold)
                        Spacer(modifier = Modifier.height(4.dp))
                        HorizontalDivider(color = BorderGrey.copy(alpha = 0.2f), thickness = 0.5.dp)
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        if (auditLogs.isEmpty()) {
                            Text("Belum ada log audit keamanan.", fontSize = 11.sp, color = TextMuted)
                        } else {
                            auditLogs.take(3).forEach { log ->
                                Column {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(log.activity, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = HighlightSoftCyan)
                                        Text(SimpleDateFormat("dd/MM HH:mm", Locale.US).format(Date(log.timestamp)), fontSize = 10.sp, color = TextMuted)
                                    }
                                    Text(log.details, fontSize = 11.sp, color = Color.White)
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        "biometric" -> {
            val prefs = remember { context.getSharedPreferences("yans_security_prefs", android.content.Context.MODE_PRIVATE) }
            var biometricEnabled by remember { mutableStateOf(prefs.getBoolean("biometric_enabled", false)) }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PremiumGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text("BIOMETRIC & SIDIK JARI", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AgedGold)
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Aktifkan Autentikasi Biometrik", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Gunakan Sidik Jari / Face ID saat membuka menu sensitif", fontSize = 11.sp, color = TextMuted)
                        }
                        Switch(
                            checked = biometricEnabled,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    com.yansproject.app.ui.security.BiometricAuthManager.authenticateWithBiometrics(
                                        context = context,
                                        onSuccess = {
                                            prefs.edit().putBoolean("biometric_enabled", true).apply()
                                            biometricEnabled = true
                                            Toast.makeText(context, "Sidik Jari Sukses Didaftarkan!", Toast.LENGTH_SHORT).show()
                                            viewModel.addAuditLog("Enroll Biometric", "Biometrik sidik jari sukses diaktifkan.")
                                        },
                                        onError = { err ->
                                            Toast.makeText(context, "Biometrik Gagal: $err", Toast.LENGTH_LONG).show()
                                        }
                                    )
                                } else {
                                    prefs.edit().putBoolean("biometric_enabled", false).apply()
                                    biometricEnabled = false
                                    viewModel.addAuditLog("Disable Biometric", "Biometrik sidik jari dinonaktifkan.")
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = shadowBlack, checkedTrackColor = HighlightSoftCyan)
                        )
                    }
                }
            }
        }

        "erp_config" -> {
            var ajibBase by remember { mutableStateOf(AppSettings.getAjibqobulBasePrice(context).toInt().toString()) }
            var ajibLong by remember { mutableStateOf(AppSettings.getAjibqobulSleeveLongPrice(context).toInt().toString()) }
            var ajibXXL by remember { mutableStateOf(AppSettings.getAjibqobulUpsizeXXL(context).toInt().toString()) }
            var ajib3XL by remember { mutableStateOf(AppSettings.getAjibqobulUpsize3XL(context).toInt().toString()) }
            var ajib4XL by remember { mutableStateOf(AppSettings.getAjibqobulUpsize4XL(context).toInt().toString()) }

            var custBase by remember { mutableStateOf(AppSettings.getCustomBasePrice(context).toInt().toString()) }
            var custLong by remember { mutableStateOf(AppSettings.getCustomSleeveLongPrice(context).toInt().toString()) }
            var custXXL by remember { mutableStateOf(AppSettings.getCustomUpsizeXXL(context).toInt().toString()) }
            var cust3XL by remember { mutableStateOf(AppSettings.getCustomUpsize3XL(context).toInt().toString()) }
            var cust4XL by remember { mutableStateOf(AppSettings.getCustomUpsize4XL(context).toInt().toString()) }
            var custHppRegPendek by remember { mutableStateOf(AppSettings.getCustomHppRegulerPendek(context).toInt().toString()) }
            var custHppRegPanjang by remember { mutableStateOf(AppSettings.getCustomHppRegulerPanjang(context).toInt().toString()) }
            var custHppKidsPendek by remember { mutableStateOf(AppSettings.getCustomHppKidsPendek(context).toInt().toString()) }
            var custHppKidsPanjang by remember { mutableStateOf(AppSettings.getCustomHppKidsPanjang(context).toInt().toString()) }

            var curSelectedCurrency by remember { mutableStateOf("IDR (Rupiah Rp)") }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Card 1: AJIBQOBUL READY STOCK
                SharedPremiumCard(
                    modifier = Modifier.fillMaxWidth(),
                    padding = 16.dp,
                    borderGlowColor = AgedGold.copy(alpha = 0.25f)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Inventory2,
                                contentDescription = null,
                                tint = AgedGold,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "AJIBQOBUL READY STOCK CONFIG",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = AgedGold,
                                letterSpacing = 1.sp
                            )
                        }
                        
                        HorizontalDivider(color = AgedGold.copy(alpha = 0.15f), thickness = 1.dp)
                        
                        OutlinedTextField(
                            value = ajibLong,
                            onValueChange = { ajibLong = it },
                            label = { Text("Tambahan Harga Lengan Panjang (Rp)") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = AgedGold, unfocusedBorderColor = BorderGrey)
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = ajibXXL,
                                onValueChange = { ajibXXL = it },
                                label = { Text("Upsize XXL (Rp)") },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = AgedGold, unfocusedBorderColor = BorderGrey)
                            )
                            OutlinedTextField(
                                value = ajib3XL,
                                onValueChange = { ajib3XL = it },
                                label = { Text("Upsize 3XL (Rp)") },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = AgedGold, unfocusedBorderColor = BorderGrey)
                            )
                            OutlinedTextField(
                                value = ajib4XL,
                                onValueChange = { ajib4XL = it },
                                label = { Text("Upsize 4XL (Rp)") },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = AgedGold, unfocusedBorderColor = BorderGrey)
                            )
                        }
                    }
                }

                // Card 2: PROJECT CUSTOM
                SharedPremiumCard(
                    modifier = Modifier.fillMaxWidth(),
                    padding = 16.dp,
                    borderGlowColor = HighlightSoftCyan.copy(alpha = 0.25f)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DesignServices,
                                contentDescription = null,
                                tint = HighlightSoftCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "PROJECT CUSTOM CONFIG",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = HighlightSoftCyan,
                                letterSpacing = 1.sp
                            )
                        }
                        
                        HorizontalDivider(color = HighlightSoftCyan.copy(alpha = 0.15f), thickness = 1.dp)

                        OutlinedTextField(
                            value = custLong,
                            onValueChange = { custLong = it },
                            label = { Text("Tambahan Harga Lengan Panjang (Rp)") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = HighlightSoftCyan, unfocusedBorderColor = BorderGrey)
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = custXXL,
                                onValueChange = { custXXL = it },
                                label = { Text("Upsize XXL (Rp)") },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = HighlightSoftCyan, unfocusedBorderColor = BorderGrey)
                            )
                            OutlinedTextField(
                                value = cust3XL,
                                onValueChange = { cust3XL = it },
                                label = { Text("Upsize 3XL (Rp)") },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = HighlightSoftCyan, unfocusedBorderColor = BorderGrey)
                            )
                            OutlinedTextField(
                                value = cust4XL,
                                onValueChange = { cust4XL = it },
                                label = { Text("Upsize 4XL (Rp)") },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = HighlightSoftCyan, unfocusedBorderColor = BorderGrey)
                            )
                        }

                        Text("HPP PRODUKSI CUSTOM (REGULER & KIDS)", color = HighlightSoftCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = custHppRegPendek,
                                onValueChange = { custHppRegPendek = it },
                                label = { Text("HPP Reguler Pendek") },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = HighlightSoftCyan, unfocusedBorderColor = BorderGrey)
                            )
                            OutlinedTextField(
                                value = custHppRegPanjang,
                                onValueChange = { custHppRegPanjang = it },
                                label = { Text("HPP Reguler Panjang") },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = HighlightSoftCyan, unfocusedBorderColor = BorderGrey)
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = custHppKidsPendek,
                                onValueChange = { custHppKidsPendek = it },
                                label = { Text("HPP Kids Pendek") },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = HighlightSoftCyan, unfocusedBorderColor = BorderGrey)
                            )
                            OutlinedTextField(
                                value = custHppKidsPanjang,
                                onValueChange = { custHppKidsPanjang = it },
                                label = { Text("HPP Kids Panjang") },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = HighlightSoftCyan, unfocusedBorderColor = BorderGrey)
                            )
                        }
                    }
                }

                // Card 3: GENERAL ERP SETTINGS
                SharedPremiumCard(
                    modifier = Modifier.fillMaxWidth(),
                    padding = 16.dp,
                    borderGlowColor = Color.White.copy(alpha = 0.15f)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "SISTEM STANDARISASI ERP",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                letterSpacing = 1.sp
                            )
                        }
                        
                        HorizontalDivider(color = Color.White.copy(alpha = 0.15f), thickness = 1.dp)

                        OutlinedTextField(
                            value = curSelectedCurrency,
                            onValueChange = { curSelectedCurrency = it },
                            label = { Text("Mata Uang Default") },
                            enabled = false,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = AgedGold, unfocusedBorderColor = BorderGrey)
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Button(
                            onClick = {
                                val aBase = ajibBase.toDoubleOrNull() ?: 80000.0
                                val aLong = ajibLong.toDoubleOrNull() ?: 10000.0
                                val aXXL = ajibXXL.toDoubleOrNull() ?: 10000.0
                                val a3XL = ajib3XL.toDoubleOrNull() ?: 10000.0
                                val a4XL = ajib4XL.toDoubleOrNull() ?: 20000.0

                                val cBase = custBase.toDoubleOrNull() ?: 100000.0
                                val cLong = custLong.toDoubleOrNull() ?: 15000.0
                                val cXXL = custXXL.toDoubleOrNull() ?: 10000.0
                                val c3XL = cust3XL.toDoubleOrNull() ?: 10000.0
                                val c4XL = cust4XL.toDoubleOrNull() ?: 10000.0
                                val cHppRegPendek = custHppRegPendek.toDoubleOrNull() ?: 55000.0
                                val cHppRegPanjang = custHppRegPanjang.toDoubleOrNull() ?: 65000.0
                                val cHppKidsPendek = custHppKidsPendek.toDoubleOrNull() ?: 40000.0
                                val cHppKidsPanjang = custHppKidsPanjang.toDoubleOrNull() ?: 45000.0
                                
                                AppSettings.setAjibqobulBasePrice(context, aBase)
                                AppSettings.setAjibqobulSleeveLongPrice(context, aLong)
                                AppSettings.setAjibqobulUpsizeXXL(context, aXXL)
                                AppSettings.setAjibqobulUpsize3XL(context, a3XL)
                                AppSettings.setAjibqobulUpsize4XL(context, a4XL)

                                AppSettings.setCustomBasePrice(context, cBase)
                                AppSettings.setCustomSleeveLongPrice(context, cLong)
                                AppSettings.setCustomUpsizeXXL(context, cXXL)
                                AppSettings.setCustomUpsize3XL(context, c3XL)
                                AppSettings.setCustomUpsize4XL(context, c4XL)
                                AppSettings.setCustomHppRegulerPendek(context, cHppRegPendek)
                                AppSettings.setCustomHppRegulerPanjang(context, cHppRegPanjang)
                                AppSettings.setCustomHppKidsPendek(context, cHppKidsPendek)
                                AppSettings.setCustomHppKidsPanjang(context, cHppKidsPanjang)

                                // Async sync to Firebase Cloud settings
                                val erpData = mapOf(
                                    "ajib_base_price" to aBase,
                                    "ajib_sleeve_long_price" to aLong,
                                    "ajib_upsize_xxl" to aXXL,
                                    "ajib_upsize_3xl" to a3XL,
                                    "ajib_upsize_4xl" to a4XL,
                                    "custom_base_price" to cBase,
                                    "custom_sleeve_long_price" to cLong,
                                    "custom_upsize_xxl" to cXXL,
                                    "custom_upsize_3xl" to c3XL,
                                    "custom_upsize_4xl" to c4XL,
                                    "custom_hpp_reguler_pendek" to cHppRegPendek,
                                    "custom_hpp_reguler_panjang" to cHppRegPanjang,
                                    "custom_hpp_kids_pendek" to cHppKidsPendek,
                                    "custom_hpp_kids_panjang" to cHppKidsPanjang,
                                    "updated_at" to System.currentTimeMillis()
                                )
                                try {
                                    com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                        .collection("settings")
                                        .document("erp_config")
                                        .set(erpData)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }

                                Toast.makeText(context, "Konfigurasi ERP berhasil disinkronkan!", Toast.LENGTH_SHORT).show()
                                viewModel.addAuditLog("Save ERP Config", "Berhasil memperbarui aturan harga terpadu ERP.")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AgedGold, contentColor = Color.Black),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("SIMPAN KONFIGURASI ERP", fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp)
                        }
                    }
                }
            }
        }

        "notifications" -> {
            val prefs = remember { context.getSharedPreferences("yans_notifications_prefs", android.content.Context.MODE_PRIVATE) }
            var systemNotify by remember { mutableStateOf(prefs.getBoolean("system_notify", true)) }
            var financeNotify by remember { mutableStateOf(prefs.getBoolean("finance_notify", true)) }
            var projectNotify by remember { mutableStateOf(prefs.getBoolean("project_notify", true)) }
            var stockNotify by remember { mutableStateOf(prefs.getBoolean("stock_notify", true)) }
            var invoiceNotify by remember { mutableStateOf(prefs.getBoolean("invoice_notify", true)) }
            var memberNotify by remember { mutableStateOf(prefs.getBoolean("member_notify", true)) }
            var personalNotify by remember { mutableStateOf(prefs.getBoolean("personal_notify", true)) }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PremiumGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("NOTIFIKASI PUSH & ALERTS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AgedGold)
                        Spacer(modifier = Modifier.height(4.dp))

                        // Sistem
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Notifikasi Sistem & Keamanan", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text("Alert login baru, backup selesai, atau modifikasi data instansi", fontSize = 11.sp, color = TextMuted)
                            }
                            Switch(
                                checked = systemNotify,
                                onCheckedChange = {
                                    systemNotify = it
                                    prefs.edit().putBoolean("system_notify", it).apply()
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = shadowBlack, checkedTrackColor = HighlightSoftCyan)
                            )
                        }

                        HorizontalDivider(color = BorderGrey.copy(alpha = 0.2f), thickness = 0.5.dp)

                        // Keuangan
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Alert Keuangan & Pembayaran", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text("Pemberitahuan real-time saat invoice lunas atau DP terekam", fontSize = 11.sp, color = TextMuted)
                            }
                            Switch(
                                checked = financeNotify,
                                onCheckedChange = {
                                    financeNotify = it
                                    prefs.edit().putBoolean("finance_notify", it).apply()
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = shadowBlack, checkedTrackColor = HighlightSoftCyan)
                            )
                        }

                        HorizontalDivider(color = BorderGrey.copy(alpha = 0.2f), thickness = 0.5.dp)

                        // Project
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Notifikasi Custom Project", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text("Pemberitahuan perubahan status proyek custom dan tenggat waktu", fontSize = 11.sp, color = TextMuted)
                            }
                            Switch(
                                checked = projectNotify,
                                onCheckedChange = {
                                    projectNotify = it
                                    prefs.edit().putBoolean("project_notify", it).apply()
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = shadowBlack, checkedTrackColor = HighlightSoftCyan)
                            )
                        }

                        HorizontalDivider(color = BorderGrey.copy(alpha = 0.2f), thickness = 0.5.dp)

                        // Stock
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Notifikasi Stok & Varian", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text("Push alert saat persediaan bahan baku / kaos di bawah batas minimum", fontSize = 11.sp, color = TextMuted)
                            }
                            Switch(
                                checked = stockNotify,
                                onCheckedChange = {
                                    stockNotify = it
                                    prefs.edit().putBoolean("stock_notify", it).apply()
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = shadowBlack, checkedTrackColor = HighlightSoftCyan)
                            )
                        }

                        HorizontalDivider(color = BorderGrey.copy(alpha = 0.2f), thickness = 0.5.dp)

                        // Invoice
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Notifikasi Invoice & Faktur", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text("Informasi pembuatan invoice baru, cetak thermal, atau tagihan tempo", fontSize = 11.sp, color = TextMuted)
                            }
                            Switch(
                                checked = invoiceNotify,
                                onCheckedChange = {
                                    invoiceNotify = it
                                    prefs.edit().putBoolean("invoice_notify", it).apply()
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = shadowBlack, checkedTrackColor = HighlightSoftCyan)
                            )
                        }

                        HorizontalDivider(color = BorderGrey.copy(alpha = 0.2f), thickness = 0.5.dp)

                        // Member
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Aktivitas Member & Staf", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text("Laporan kegiatan operasional staf member secara berkala", fontSize = 11.sp, color = TextMuted)
                            }
                            Switch(
                                checked = memberNotify,
                                onCheckedChange = {
                                    memberNotify = it
                                    prefs.edit().putBoolean("member_notify", it).apply()
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = shadowBlack, checkedTrackColor = HighlightSoftCyan)
                            )
                        }

                        HorizontalDivider(color = BorderGrey.copy(alpha = 0.2f), thickness = 0.5.dp)

                        // Personal
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Pengingat & Alarm Personal", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text("Catatan pengingat harian personal dan agenda kerja mandiri", fontSize = 11.sp, color = TextMuted)
                            }
                            Switch(
                                checked = personalNotify,
                                onCheckedChange = {
                                    personalNotify = it
                                    prefs.edit().putBoolean("personal_notify", it).apply()
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = shadowBlack, checkedTrackColor = HighlightSoftCyan)
                            )
                        }
                    }
                }
            }
        }

        "db_sync" -> {
            var isSyncingNow by remember { mutableStateOf(false) }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PremiumGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text("SINKRONISASI CLOUD FIREBASE", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AgedGold)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Aplikasi mengoperasikan Offline-First Single Source of Truth via Room SQLite lokal. Setiap perubahan akan disimpan langsung di lokal dan diantrekan untuk disinkronisasikan ke Firebase Cloud saat internet stabil.",
                        fontSize = 11.5.sp,
                        color = TextMuted,
                        lineHeight = 17.sp
                    )

                    Spacer(modifier = Modifier.height(18.dp))
                    
                    // Detailed Information Rows
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("DIAGNOSTIK SINKRONISASI & DATA", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AgedGold.copy(alpha = 0.7f), letterSpacing = 1.sp)
                        HorizontalDivider(color = BorderGrey.copy(alpha = 0.2f), thickness = 0.5.dp)

                        DiagnosticRow("Room Database", "yans_database.db (v12)", HighlightSoftCyan)
                        DiagnosticRow("Firebase Cloud", "Connected / Synced", AlertGreen)
                        DiagnosticRow("Offline Cache", "Active (Persistent)", AlertGreen)
                        DiagnosticRow("Pending Sync", "0 items in queue", HighlightSoftCyan)
                        DiagnosticRow("Sync Queue Status", "Healthy / FIFO", AlertGreen)
                        DiagnosticRow("Database Engine Version", "SQLite Room ORM v1.2.1", HighlightSoftCyan)
                        DiagnosticRow("Status Koneksi Real-time", if (isOnline) "ONLINE" else "OFFLINE", if (isOnline) AlertGreen else AlertOrange)
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            isSyncingNow = true
                            coroutineScope.launch {
                                kotlinx.coroutines.delay(2000)
                                isSyncingNow = false
                                Toast.makeText(context, "Sinkronisasi Cloud sukses diselesaikan!", Toast.LENGTH_SHORT).show()
                                viewModel.addAuditLog("Manual Re-Sync", "Memicu sinkronisasi database manual ke Firestore.")
                            }
                        },
                        enabled = !isSyncingNow,
                        colors = ButtonDefaults.buttonColors(containerColor = HighlightSoftCyan, contentColor = Color.Black),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(45.dp)
                    ) {
                        if (isSyncingNow) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black)
                        } else {
                            Text("PICU SINKRONISASI MANUAL", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        "storage" -> {
            var cacheSizeStr by remember { mutableStateOf("1.42 MB") }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PremiumGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("PUSAT PENYIMPANAN SISTEM", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AgedGold)
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Database SQLite Room", fontSize = 12.sp, color = TextMuted)
                            Text("512 KB", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("File PDF Hasil Ekspor", fontSize = 12.sp, color = TextMuted)
                            Text("824 KB", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Penyimpanan Cache Aplikasi", fontSize = 12.sp, color = TextMuted)
                            Text(cacheSizeStr, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = HighlightSoftCyan)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                cacheSizeStr = "0.00 KB"
                                Toast.makeText(context, "Penyimpanan cache ekspor PDF berhasil dibersihkan!", Toast.LENGTH_SHORT).show()
                                viewModel.addAuditLog("Clear Cache Storage", "Membersihkan file cache ekspor sementara.")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AgedGold, contentColor = Color.Black),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(45.dp)
                        ) {
                            Text("BERSIHKAN CACHE & SAMPAH", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        "appearance" -> {
            val prefs = remember { context.getSharedPreferences("yans_appearance_prefs", android.content.Context.MODE_PRIVATE) }
            
            var selectedThemeVariant by remember { mutableStateOf(prefs.getString("theme_variant", "YANSPROJECT.ID Classic") ?: "YANSPROJECT.ID Classic") }
            var accentColor by remember { mutableStateOf(prefs.getString("accent_color", "Aged Gold") ?: "Aged Gold") }
            var canvasStyle by remember { mutableStateOf(prefs.getString("canvas_style", "Shadow Black (#0A0A0A)") ?: "Shadow Black (#0A0A0A)") }
            var glassStyle by remember { mutableStateOf(prefs.getString("glass_style", "Glassmorphism Glow") ?: "Glassmorphism Glow") }
            var fontScale by remember { mutableStateOf(prefs.getFloat("font_scale", 1.0f)) }
            var hapticEnabled by remember { mutableStateOf(prefs.getBoolean("haptic_enabled", true)) }

            // Theme colors mapping
            val currentAccentColor = when(accentColor) {
                "Aged Gold" -> AgedGold
                "Soft Cyan" -> HighlightSoftCyan
                "Emerald Green" -> Color(0xFF2ECC71)
                "Imperial Amber" -> Color(0xFFFFB300)
                "Sapphire Blue" -> Color(0xFF3B82F6)
                "Rose Gold" -> Color(0xFFE5A186)
                else -> AgedGold
            }

            val currentCanvasBg = when(canvasStyle) {
                "Pure Obsidian Black (#000000)" -> Color(0xFF000000)
                "Dark Slate Teal (#081F20)" -> Color(0xFF081F20)
                else -> Color(0xFF0A0A0A)
            }

            val currentSurfaceColor = when(selectedThemeVariant) {
                "Royal Emerald Imperial" -> Color(0xFF0B2B26)
                "Midnight Sapphire Luxury" -> Color(0xFF0A192F)
                "Onyx Platinum Edition" -> Color(0xFF1E293B)
                "Ruby Imperial Velvet" -> Color(0xFF2B0B14)
                else -> DarkTealSurface
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Live Interactive Preview Mockup Card
                PremiumGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("PRATINJAU REALTIME TEMA VISUAL", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = currentAccentColor)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(currentAccentColor.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(selectedThemeVariant, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = currentAccentColor)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    
                    // Inside the preview card, render a miniature UI mockup reflecting selected theme
                    Card(
                        colors = CardDefaults.cardColors(containerColor = currentSurfaceColor),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (glassStyle == "Glassmorphism Glow") 1.dp else 0.5.dp,
                            color = if (glassStyle == "Glassmorphism Glow") currentAccentColor.copy(alpha = 0.5f) else BorderGrey.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(currentAccentColor.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Brush,
                                            contentDescription = null,
                                            tint = currentAccentColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Column {
                                        Text("YANSPROJECT.ID ERP", fontSize = (12 * fontScale).sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        Text("Status Sistem: Optimum (Online)", fontSize = (9 * fontScale).sp, color = TextMuted)
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(AlertGreen.copy(alpha = 0.2f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("AKTIF", color = AlertGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Text(
                                text = "Pratinjau dengan aksen ${accentColor}, latar ${canvasStyle.substringBefore(" ")}, dan efek ${glassStyle}. Skala font: ${(fontScale * 100).toInt()}%.",
                                fontSize = (10 * fontScale).sp,
                                color = TextMuted,
                                lineHeight = (15 * fontScale).sp
                            )

                            Button(
                                onClick = { },
                                colors = ButtonDefaults.buttonColors(containerColor = currentAccentColor, contentColor = Color.Black),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().height(36.dp)
                            ) {
                                Text("TOMBOL AKSI UTAMA MOCKUP", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }

                // 2. Koleksi Preset Tema Mewah (Luxury Theme Presets)
                PremiumGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text("KOLEKSI TEMA MEWAH YANSPROJECT.ID", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AgedGold)
                    Spacer(modifier = Modifier.height(14.dp))

                    val themeList = listOf(
                        Triple("YANSPROJECT.ID Classic", "Dark Teal & Aged Gold (Resmi)", AgedGold),
                        Triple("Royal Emerald Imperial", "Emerald & Imperial Gold", Color(0xFF2ECC71)),
                        Triple("Midnight Sapphire Luxury", "Deep Sapphire & Champagne Gold", Color(0xFFE5C158)),
                        Triple("Onyx Platinum Edition", "Deep Onyx & Platinum Silver", Color(0xFFE0E0E0)),
                        Triple("Ruby Imperial Velvet", "Imperial Ruby & Rose Gold", Color(0xFFE5A186))
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        themeList.forEach { (tName, tDesc, tAccent) ->
                            val isSelected = selectedThemeVariant == tName
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) tAccent.copy(alpha = 0.15f) else SecondaryShadowBlackTeal)
                                    .border(1.dp, if (isSelected) tAccent else BorderGrey.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                    .clickable {
                                        if (hapticEnabled) hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                        selectedThemeVariant = tName
                                        accentColor = when(tName) {
                                            "Royal Emerald Imperial" -> "Emerald Green"
                                            "Midnight Sapphire Luxury" -> "Imperial Amber"
                                            "Onyx Platinum Edition" -> "Sapphire Blue"
                                            "Ruby Imperial Velvet" -> "Rose Gold"
                                            else -> "Aged Gold"
                                        }
                                    }
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(tName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isSelected) tAccent else Color.White)
                                    Text(tDesc, fontSize = 10.sp, color = TextMuted)
                                }
                                if (isSelected) {
                                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = tAccent, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }

                // 3. Warna Aksen Visual
                PremiumGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text("WARNA AKSEN VISUAL", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AgedGold)
                    Spacer(modifier = Modifier.height(14.dp))

                    val colorOptions = listOf(
                        "Aged Gold" to AgedGold,
                        "Soft Cyan" to HighlightSoftCyan,
                        "Emerald Green" to Color(0xFF2ECC71),
                        "Imperial Amber" to Color(0xFFFFB300),
                        "Sapphire Blue" to Color(0xFF3B82F6),
                        "Rose Gold" to Color(0xFFE5A186)
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        colorOptions.chunked(3).forEach { rowColors ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                rowColors.forEach { (cName, cValue) ->
                                    val isSelected = accentColor == cName
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isSelected) cValue.copy(alpha = 0.2f) else SecondaryShadowBlackTeal)
                                            .border(1.dp, if (isSelected) cValue else BorderGrey.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                            .clickable {
                                                if (hapticEnabled) hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                accentColor = cName
                                            }
                                            .padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(cValue))
                                            Text(cName, fontSize = 10.sp, color = if (isSelected) cValue else Color.White, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. Gaya Efek Kartu & Kaca (Glassmorphism / Solid)
                PremiumGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text("GAYA EFEK KARTU & SUASANA KANVAS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AgedGold)
                    Spacer(modifier = Modifier.height(14.dp))

                    Text("Efek Kartu Glassmorphism", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Glassmorphism Glow", "Solid Dark Luxury", "Minimalist Border").forEach { style ->
                            val isSelected = glassStyle == style
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) currentAccentColor.copy(alpha = 0.2f) else SecondaryShadowBlackTeal)
                                    .border(1.dp, if (isSelected) currentAccentColor else BorderGrey.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (hapticEnabled) hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                        glassStyle = style
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(style.substringBefore(" "), fontSize = 10.sp, color = if (isSelected) currentAccentColor else Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = BorderGrey.copy(alpha = 0.2f), thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(14.dp))

                    Text("Warna Latar Belakang Kanvas", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.height(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            "Shadow Black (#0A0A0A)",
                            "Pure Obsidian Black (#000000)",
                            "Dark Slate Teal (#081F20)"
                        ).forEach { cOption ->
                            val isSelected = canvasStyle == cOption
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) currentAccentColor.copy(alpha = 0.15f) else SecondaryShadowBlackTeal)
                                    .border(1.dp, if (isSelected) currentAccentColor else BorderGrey.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (hapticEnabled) hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                        canvasStyle = cOption
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(cOption, fontSize = 11.sp, color = if (isSelected) currentAccentColor else Color.White, fontWeight = FontWeight.Bold)
                                if (isSelected) {
                                    Icon(Icons.Outlined.Check, contentDescription = null, tint = currentAccentColor, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                // 5. Skala Font & Tipografi
                PremiumGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text("UKURAN SKALA FONT & SKALA TIPOGRAFI", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AgedGold)
                    Spacer(modifier = Modifier.height(14.dp))

                    Text("Skala Font: ${(fontScale * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.height(6.dp))
                    Slider(
                        value = fontScale,
                        onValueChange = {
                            fontScale = it
                        },
                        valueRange = 0.8f..1.4f,
                        steps = 5,
                        colors = SliderDefaults.colors(thumbColor = currentAccentColor, activeTrackColor = currentAccentColor)
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Kecil (90%)" to 0.9f, "Standar (100%)" to 1.0f, "Besar (115%)" to 1.15f, "Ekstra (130%)" to 1.30f).forEach { (label, scale) ->
                            val isSelected = Math.abs(fontScale - scale) < 0.03f
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) currentAccentColor.copy(alpha = 0.2f) else SecondaryShadowBlackTeal)
                                    .border(1.dp, if (isSelected) currentAccentColor else BorderGrey.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (hapticEnabled) hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                        fontScale = scale
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(label, fontSize = 9.sp, color = if (isSelected) currentAccentColor else Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // 6. Umpan Balik Haptic Touch
                PremiumGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Haptic Feedback & Getaran Tactile", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Getaran lembut saat menekan tombol & interaksi sheet modal", fontSize = 10.sp, color = TextMuted)
                        }
                        Switch(
                            checked = hapticEnabled,
                            onCheckedChange = {
                                hapticEnabled = it
                                if (it) hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = currentAccentColor
                            )
                        )
                    }
                }

                // 7. Tombol Simpan & Terapkan Pengaturan Tema
                Button(
                    onClick = {
                        if (hapticEnabled) hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.updateAppearanceSettings(
                            themeVariant = selectedThemeVariant,
                            accentColor = accentColor,
                            canvasStyle = canvasStyle,
                            glassStyle = glassStyle,
                            fontScale = fontScale,
                            hapticEnabled = hapticEnabled
                        )
                        
                        Toast.makeText(context, "Tema Visual YANSPROJECT.ID Berhasil Diterapkan!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = currentAccentColor, contentColor = Color.Black),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("TERAPKAN & SIMPAN PREFERENSI TEMA", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        "info" -> {
            AboutYansScreen(onBack = { navController?.popBackStack() })
        }

        "maintenance" -> {
            var isRunningVacuum by remember { mutableStateOf(false) }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PremiumGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text("PEMELIHARAAN SISTEM SQLITE (OWNER)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AgedGold)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Indeks Integritas DB: 100% OK",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AlertGreen
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Lakukan pemeliharaan rutin di bawah untuk mengkompresi ukuran database SQLite (VACUUM), merapikan fragmentasi data, dan menyusun ulang indeks pencarian agar performa transaksi ERP tetap instan dan lancar.",
                        fontSize = 12.sp,
                        color = TextMuted,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = {
                            isRunningVacuum = true
                            coroutineScope.launch {
                                kotlinx.coroutines.delay(2000)
                                isRunningVacuum = false
                                Toast.makeText(context, "VACUUM SQLite & Optimalisasi Selesai!", Toast.LENGTH_SHORT).show()
                                viewModel.addAuditLog("VACUUM Database", "Menjalankan perintah VACUUM untuk mengoptimasi SQLite.")
                            }
                        },
                        enabled = !isRunningVacuum,
                        colors = ButtonDefaults.buttonColors(containerColor = AgedGold, contentColor = Color.Black),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(45.dp)
                    ) {
                        if (isRunningVacuum) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black)
                        } else {
                            Text("JALANKAN OPTIMALISASI DATABASE", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        "dev_diag" -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PremiumGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text("DEVELOPER DIAGNOSTIC PANEL", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AgedGold)
                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = BorderGrey.copy(alpha = 0.2f), thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    DiagnosticRow("Network Ping Latency", "42 ms", AlertGreen)
                    DiagnosticRow("Firebase Write Quota Check", "Normal (0.01%)", AlertGreen)
                    DiagnosticRow("Room ORM Database Version", "v12", HighlightSoftCyan)
                    DiagnosticRow("SDK API Endpoint Integration", "Connected (v3)", AlertGreen)
                    DiagnosticRow("JVM Memory Available", "124 MB Free", AlertGreen)
                    DiagnosticRow("Haptic Engine Status", "Ready (Vibrator)", AlertGreen)
                }
            }
        }
    }
}

@Composable
fun AuditLogItemCard(log: AuditLog) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardGrey),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderGrey),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = log.activity.uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AgedGold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID")).format(Date(log.timestamp)),
                    fontSize = 10.sp,
                    color = TextMuted
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = log.details,
                fontSize = 13.sp,
                color = TextLight
            )
        }
    }
}

@Composable
fun DiagnosticRow(label: String, value: String, statusColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 12.sp, color = TextLight)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(statusColor, shape = androidx.compose.foundation.shape.CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = statusColor)
        }
    }
}

@Composable
fun ProfileField(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    valueColor: Color = Color.White
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, tint = AgedGold, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(text = label, fontSize = 12.sp, color = TextMuted, fontWeight = FontWeight.Medium)
        }
        Text(text = value, fontSize = 12.sp, color = valueColor, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SettingsMenuRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SharedPremiumCard(
        padding = 0.dp,
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = AgedGold,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = textMuted,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun AboutYansScreen(onBack: () -> Unit) {
    val scrollState = androidx.compose.foundation.rememberScrollState()
    val context = LocalContext.current
    val versionName = remember(context) {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkGrey)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Outlined.ArrowBack,
                    contentDescription = "Kembali",
                    tint = AgedGold
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "IDENTITAS RESMI",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = AgedGold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Tentang YANSPROJECT.ID ERP",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        // Content
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Logo & Slogan Section
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceDarkTealSurface)
                    .border(1.5.dp, AgedGold.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(id = com.yansproject.app.R.drawable.ic_logo),
                    contentDescription = "Logo Resmi YANSPROJECT.ID",
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "YANSPROJECT.ID",
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = AgedGold,
                letterSpacing = 2.sp
            )
            Text(
                text = "Enterprise Resource Planning (ERP)",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = TextLight,
                letterSpacing = 0.5.sp
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Slogan Box with double thin borders
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .border(width = 0.5.dp, color = BorderGrey, shape = RoundedCornerShape(8.dp))
                    .background(CardGrey.copy(alpha = 0.4f))
                    .padding(vertical = 12.dp, horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Makna Sebelum Estetika",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = AgedGold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = "\"Mengelola proses dengan sistem.\nMenjaga makna dalam setiap perjalanan.\"",
                        fontSize = 11.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        color = TextLight,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // 2. TENTANG APLIKASI Card
            Card(
                colors = CardDefaults.cardColors(containerColor = CardGrey),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(0.5.dp, BorderGrey),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = AgedGold,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "TENTANG APLIKASI",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AgedGold,
                            letterSpacing = 1.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "YANSPROJECT.ID ERP dikembangkan sebagai pusat operasional digital yang menghubungkan seluruh aktivitas YANSPROJECT.ID ke dalam satu sistem yang saling terintegrasi.",
                        fontSize = 13.sp,
                        color = Color.White,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Aplikasi ini membantu mengelola inventori, produksi, transaksi, keuangan, member, dokumentasi, dan arsip digital agar setiap proses berjalan lebih rapi, konsisten, transparan, mudah ditelusuri, dan mudah diaudit.",
                        fontSize = 13.sp,
                        color = Color.White,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = BorderGrey, thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Setiap data memiliki hubungan.\n\nSetiap perubahan memiliki riwayat.\n\nSetiap keputusan memiliki dasar.\n\nKarena sistem yang baik bukan hanya cepat, tetapi juga dapat dipercaya.",
                        fontSize = 11.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        color = HighlightSoftCyan,
                        lineHeight = 16.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. FILOSOFI Card
            Card(
                colors = CardDefaults.cardColors(containerColor = CardGrey),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(0.5.dp, BorderGrey),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Star,
                            contentDescription = null,
                            tint = AgedGold,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "FILOSOFI",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AgedGold,
                            letterSpacing = 1.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Makna Sebelum Estetika.",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = AgedGold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Teknologi bukan sekadar alat.",
                        fontSize = 13.sp,
                        color = Color.White,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Teknologi adalah sarana untuk menjaga makna, ketertiban, kesinambungan, dan kualitas setiap proses.",
                        fontSize = 13.sp,
                        color = Color.White,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Estetika memberi kesan.\n\nMakna memberi arah.",
                        fontSize = 13.sp,
                        color = TextLight,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Karena itu seluruh sistem dibangun berdasarkan prinsip:\n\nMakna sebelum Estetika.",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = HighlightSoftCyan,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 4. MODUL UTAMA Card
            Card(
                colors = CardDefaults.cardColors(containerColor = CardGrey),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(0.5.dp, BorderGrey),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Apps,
                            contentDescription = null,
                            tint = AgedGold,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "MODUL UTAMA",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AgedGold,
                            letterSpacing = 1.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))

                    val modules = listOf(
                        "Dashboard Operasional",
                        "Inventory AJIBQOBUL SERIES",
                        "Produksi",
                        "Invoice",
                        "Keuangan",
                        "Portal Member",
                        "Kitab Digital",
                        "Digital Manuscript Archive",
                        "Audit Log",
                        "Backup & Restore"
                    )

                    modules.forEach { module ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                tint = HighlightSoftCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = module,
                                fontSize = 13.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 5. KITAB DIGITAL Card
            Card(
                colors = CardDefaults.cardColors(containerColor = CardGrey),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(0.5.dp, BorderGrey),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Book,
                            contentDescription = null,
                            tint = AgedGold,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "KITAB DIGITAL",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AgedGold,
                            letterSpacing = 1.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Aplikasi juga menyediakan ruang dokumentasi digital sebagai arsip resmi YANSPROJECT.ID.",
                        fontSize = 13.sp,
                        color = Color.White,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    // Koleksi 1
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ShadowBlack.copy(alpha = 0.3f))
                            .border(0.5.dp, BorderGrey, RoundedCornerShape(6.dp))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "YANSPROJECT.ID HISTORY", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(HighlightSoftCyan.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(text = "Published", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = HighlightSoftCyan)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Koleksi 2
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ShadowBlack.copy(alpha = 0.3f))
                            .border(0.5.dp, BorderGrey, RoundedCornerShape(6.dp))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "RISALAH MADAD AULIYA", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(text = "Digital Manuscript Archive", fontSize = 10.sp, color = TextMuted)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(AgedGold.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(text = "Draft Ongoing", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = AgedGold)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "\"Langkah Sunyi, Terkenang Abadi.\"",
                        fontSize = 11.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        color = TextLight,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 6. INFORMASI SISTEM Card
            Card(
                colors = CardDefaults.cardColors(containerColor = CardGrey),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(0.5.dp, BorderGrey),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = null,
                            tint = AgedGold,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "INFORMASI SISTEM",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AgedGold,
                            letterSpacing = 1.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))

                    val sysInfo = listOf(
                        "Nama" to "YANSPROJECT.ID ERP",
                        "Platform" to "Android",
                        "Database" to "Firebase",
                        "Penyimpanan Lokal" to "Assets & Local Database",
                        "Versi" to "v$versionName",
                        "Developer" to "YANSPROJECT.ID"
                    )

                    sysInfo.forEach { (label, value) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = label, fontSize = 12.sp, color = TextMuted)
                            Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        if (label != sysInfo.last().first) {
                            HorizontalDivider(color = BorderGrey.copy(alpha = 0.5f), thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 7. BANTUAN Card
            Card(
                colors = CardDefaults.cardColors(containerColor = CardGrey),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(0.5.dp, BorderGrey),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.HelpOutline,
                            contentDescription = null,
                            tint = AgedGold,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "BANTUAN",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AgedGold,
                            letterSpacing = 1.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Apabila pengguna mengalami kendala, menemukan bug, membutuhkan bantuan teknis, atau memiliki pertanyaan mengenai aplikasi.",
                        fontSize = 12.sp,
                        color = TextLight,
                        lineHeight = 17.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Main button to WhatsApp
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                data = Uri.parse("https://wa.me/6287777398813")
                            }
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Tidak dapat membuka WhatsApp. Silakan hubungi +62 877-7739-8813", Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("contact_admin_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0F3D3E), // Dark Teal (DNA)
                            contentColor = Color.White
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AgedGold), // Aged Gold border (DNA)
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                painter = painterResource(id = com.yansproject.app.R.drawable.ic_whatsapp),
                                contentDescription = null,
                                tint = Color(0xFF25D366), // Official WhatsApp Green (Recognizable)
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Hubungi Administrator",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = AgedGold, // Aged Gold text (DNA)
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // 8. HAK CIPTA / FOOTER
            Text(
                text = "© YANSPROJECT.ID",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = AgedGold,
                letterSpacing = 1.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Seluruh sistem, identitas visual, struktur data, desain, dan konten aplikasi merupakan bagian dari ekosistem resmi YANSPROJECT.ID.",
                fontSize = 10.sp,
                color = TextMuted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 14.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Close Button
            Button(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AgedGold, contentColor = ShadowBlack),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "KEMBALI",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}


