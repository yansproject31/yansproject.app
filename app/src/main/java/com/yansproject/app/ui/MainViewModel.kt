package com.yansproject.app.ui

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yansproject.app.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class AppTab {
    DASHBOARD, PROJECT, STOCK, INVOICE, RIWAYAT, SETTINGS, KITAB
}

data class FinancialSummary(
    val totalRevenue: Double = 0.0,
    val totalPemasukan: Double = 0.0,
    val totalReceivables: Double = 0.0,
    val totalProjectValue: Double = 0.0,
    val activeProjectsCount: Int = 0,
    val lowStockCount: Int = 0,
    val totalOrdersCount: Int = 0
)

data class SnackbarEvent(
    val message: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val repository = BusinessRepository(db)
    private val searchRepository = SearchRepository(AppModule.provideFirestore(application))
    val draftSalesOrderManager = DraftSalesOrderManager(db, application, viewModelScope)
    private var notificationListenerReg: com.google.firebase.firestore.ListenerRegistration? = null

    private val networkMonitor = NetworkMonitor(application)
    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline

    override fun onCleared() {
        super.onCleared()
        networkMonitor.unregisterCallback()
    }

    // --- Global Snackbar Events ---
    private val _snackbarEvent = MutableSharedFlow<SnackbarEvent>()
    val snackbarEvent = _snackbarEvent.asSharedFlow()

    fun showGlobalSnackbar(message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
        viewModelScope.launch {
            _snackbarEvent.emit(SnackbarEvent(message, actionLabel, onAction))
        }
    }

    // --- Authentication State ---
    val isLoggedIn: StateFlow<Boolean> = FirebaseSyncManager.currentUser
        .map { it != null }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            application.getSharedPreferences("yans_auth_prefs", android.content.Context.MODE_PRIVATE).getBoolean("remember_login", false)
        )

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    private val _isLoginLoading = MutableStateFlow<Boolean>(false)
    val isLoginLoading: StateFlow<Boolean> = _isLoginLoading.asStateFlow()

    // --- Firebase Syncing State ---
    private val _isSyncing = MutableStateFlow<Boolean>(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    fun refreshData(context: android.content.Context, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        if (_isSyncing.value) return
        _isSyncing.value = true
        FirebaseSyncManager.pullAllDataFromCloudToLocal(context) { success ->
            _isSyncing.value = false
            if (success) {
                val sdf = java.text.SimpleDateFormat("dd MMMM yyyy\nHH:mm 'WIB'", java.util.Locale("id", "ID"))
                val currentSyncTime = sdf.format(java.util.Date())
                AppSettings.setLastSync(context, currentSyncTime)
                onResult(true, null)
            } else {
                onResult(false, "Koneksi Firebase gagal atau offline.")
            }
        }
    }

    // --- Active Tab ---
    private val _currentTab = MutableStateFlow(AppTab.DASHBOARD)
    val currentTab: StateFlow<AppTab> = _currentTab.asStateFlow()

    // --- Developer Mode State ---
    val isDeveloperMode = MutableStateFlow<Boolean>(AppSettings.getDeveloperMode(application))

    fun setDeveloperMode(enabled: Boolean) {
        AppSettings.setDeveloperMode(getApplication<Application>(), enabled)
        isDeveloperMode.value = enabled
        addAuditLog(
            "Developer Mode",
            "Mode Developer telah ${if (enabled) "diaktifkan" else "dinonaktifkan"}."
        )
    }

    // --- Appearance & Centralized Theme State ---
    private val appearancePrefs = getApplication<Application>().getSharedPreferences("yans_appearance_prefs", Context.MODE_PRIVATE)

    private val _themeVariant = MutableStateFlow(appearancePrefs.getString("theme_variant", "YANSPROJECT.ID Classic") ?: "YANSPROJECT.ID Classic")
    val themeVariant: StateFlow<String> = _themeVariant.asStateFlow()

    private val _accentColor = MutableStateFlow(appearancePrefs.getString("accent_color", "Aged Gold") ?: "Aged Gold")
    val accentColor: StateFlow<String> = _accentColor.asStateFlow()

    private val _canvasStyle = MutableStateFlow(appearancePrefs.getString("canvas_style", "Shadow Black (#0A0A0A)") ?: "Shadow Black (#0A0A0A)")
    val canvasStyle: StateFlow<String> = _canvasStyle.asStateFlow()

    private val _glassStyle = MutableStateFlow(appearancePrefs.getString("glass_style", "Glassmorphism Glow") ?: "Glassmorphism Glow")
    val glassStyle: StateFlow<String> = _glassStyle.asStateFlow()

    private val _fontScale = MutableStateFlow(appearancePrefs.getFloat("font_scale", 1.0f))
    val fontScale: StateFlow<Float> = _fontScale.asStateFlow()

    private val _hapticEnabled = MutableStateFlow(appearancePrefs.getBoolean("haptic_enabled", true))
    val hapticEnabled: StateFlow<Boolean> = _hapticEnabled.asStateFlow()

    private val _layoutDensity = MutableStateFlow(appearancePrefs.getString("layout_density", "Comfortable") ?: "Comfortable")
    val layoutDensity: StateFlow<String> = _layoutDensity.asStateFlow()

    fun updateAppearanceSettings(
        themeVariant: String = _themeVariant.value,
        accentColor: String = _accentColor.value,
        canvasStyle: String = _canvasStyle.value,
        glassStyle: String = _glassStyle.value,
        fontScale: Float = _fontScale.value,
        hapticEnabled: Boolean = _hapticEnabled.value,
        layoutDensity: String = _layoutDensity.value
    ) {
        _themeVariant.value = themeVariant
        _accentColor.value = accentColor
        _canvasStyle.value = canvasStyle
        _glassStyle.value = glassStyle
        _fontScale.value = fontScale
        _hapticEnabled.value = hapticEnabled
        _layoutDensity.value = layoutDensity

        appearancePrefs.edit()
            .putString("theme_variant", themeVariant)
            .putString("accent_color", accentColor)
            .putString("canvas_style", canvasStyle)
            .putString("glass_style", glassStyle)
            .putFloat("font_scale", fontScale)
            .putBoolean("haptic_enabled", hapticEnabled)
            .putString("layout_density", layoutDensity)
            .apply()

        addAuditLog("Pengaturan Tampilan", "Memperbarui tema ke $themeVariant ($accentColor), font scale ${fontScale}x, kepadatan $layoutDensity.")
    }

    // --- Flows from Database ---
    val allStock: StateFlow<List<StockItem>> = repository.allStock
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trashedStock: StateFlow<List<StockItem>> = repository.trashedStock
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allProjects: StateFlow<List<ProjectCustom>> = repository.allProjects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trashedProjects: StateFlow<List<ProjectCustom>> = repository.trashedProjects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allOrders: StateFlow<List<OrderHistory>> = repository.allOrders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allInvoices: StateFlow<List<Invoice>> = repository.allInvoices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allInvoicePayments: StateFlow<List<InvoicePayment>> = repository.allInvoicePayments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedInvoiceTab = MutableStateFlow("Semua")

    val filteredInvoices: StateFlow<List<Invoice>> = combine(
        allInvoices,
        selectedInvoiceTab
    ) { invoices, tab ->
        when {
            tab.equals("Persetujuan", ignoreCase = true) -> {
                invoices.filter { it.status.equals("MENUNGGU PERSETUJUAN", ignoreCase = true) }
            }
            tab.equals("Belum Dibayar", ignoreCase = true) -> {
                invoices.filter { 
                    it.status.equals("BELUM LUNAS", ignoreCase = true) || 
                    it.status.equals("DP", ignoreCase = true) ||
                    it.status.equals("DICICIL", ignoreCase = true) ||
                    it.status.equals("DP AWAL", ignoreCase = true)
                }
            }
            tab.equals("Lunas", ignoreCase = true) -> {
                invoices.filter { it.status.equals("LUNAS", ignoreCase = true) }
            }
            else -> invoices
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trashedInvoices: StateFlow<List<Invoice>> = repository.trashedInvoices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allExpenses: StateFlow<List<Expense>> = repository.allExpenses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allInflows: StateFlow<List<Inflow>> = repository.allInflows
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trashedInflows: StateFlow<List<Inflow>> = repository.trashedInflows
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trashedExpenses: StateFlow<List<Expense>> = repository.trashedExpenses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allStockHistory: StateFlow<List<StockHistory>> = repository.allStockHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allAuditLogs: StateFlow<List<AuditLog>> = db.auditLogDao().getAllLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Financial Summary & Warnings (Single Source of Truth) ---
    val financialSummary: StateFlow<FinancialSummary> = combine(
        allInvoices,
        allProjects,
        allStock,
        allOrders,
        allInflows,
        allInvoicePayments
    ) { flowArray ->
        @Suppress("UNCHECKED_CAST")
        val invoices = flowArray[0] as List<Invoice>
        @Suppress("UNCHECKED_CAST")
        val projects = flowArray[1] as List<ProjectCustom>
        @Suppress("UNCHECKED_CAST")
        val stock = flowArray[2] as List<StockItem>
        @Suppress("UNCHECKED_CAST")
        val orders = flowArray[3] as List<OrderHistory>
        @Suppress("UNCHECKED_CAST")
        val inflows = flowArray[4] as List<Inflow>
        @Suppress("UNCHECKED_CAST")
        val payments = flowArray[5] as List<InvoicePayment>
        val invRevenue = invoices.filter { inv ->
            if (inv.isDeleted) return@filter false
            val st = (inv.status ?: "").trim().uppercase()
            st !in listOf("CANCELLED", "VOID", "DIBATALKAN", "BATAL", "DRAFT")
        }.sumOf { inv ->
            val st = (inv.status ?: "").trim().uppercase()
            if (st in listOf("LUNAS", "PAID", "SELESAI", "LUNAS (PAID)")) {
                if (inv.totalAmount > 0.0) inv.totalAmount else 0.0
            } else {
                val paymentsForInv = payments.filter { p ->
                    (p.invoiceId == inv.invoiceNumber && inv.invoiceNumber.isNotBlank()) || 
                    p.invoiceId == inv.id.toString()
                }
                val paymentRecordsSum = paymentsForInv
                    .distinctBy { p -> p.id.ifEmpty { "${p.date}_${p.amount}_${p.paymentMethod}" } }
                    .sumOf { p -> p.amount }
                val effectivePaid = if (inv.paidAmount > 0.0) inv.paidAmount else if (inv.dpAmount > 0.0) inv.dpAmount else 0.0
                maxOf(effectivePaid, paymentRecordsSum)
            }
        }
        val standaloneOrdRevenue = orders.filter { ord -> !ord.isDeleted && invoices.none { inv -> inv.orderId == ord.id } }.sumOf { ord ->
            val paid = ord.paidAmount
            if (paid > 0.0) paid
            else {
                val st = (ord.status ?: "").trim().uppercase()
                if (st == "COMPLETED" || st == "SELESAI" || st == "LUNAS" || st == "PAID" || st == "DISETUJUI" || st == "TERBAYAR") ord.totalAmount
                else 0.0
            }
        }
        val nonInvoiceInflows = inflows.filter { 
            !it.isDeleted && 
            !(it.category ?: "").contains("Modal", ignoreCase = true) &&
            !(it.category ?: "").contains("Pembayaran Customer", ignoreCase = true) &&
            !(it.notes ?: "").contains("[PAY_") &&
            !(it.notes ?: "").contains("Pembayaran Invoice")
        }.sumOf { it.amount }
        val allInflowsAmount = inflows.filter { 
            !it.isDeleted &&
            !(it.category ?: "").contains("Pembayaran Customer", ignoreCase = true) &&
            !(it.notes ?: "").contains("[PAY_") &&
            !(it.notes ?: "").contains("Pembayaran Invoice")
        }.sumOf { it.amount }

        val revenue = invRevenue + standaloneOrdRevenue + nonInvoiceInflows
        val totalPemasukanAll = invRevenue + standaloneOrdRevenue + allInflowsAmount
        val receivables = invoices.filter { inv ->
            if (inv.isDeleted) return@filter false
            val st = (inv.status ?: "").trim().uppercase()
            st !in listOf("LUNAS", "PAID", "SELESAI", "LUNAS (PAID)", "CANCELLED", "VOID", "DIBATALKAN", "BATAL", "DRAFT")
        }.sumOf { inv ->
            val paymentsForInv = payments.filter { p ->
                (p.invoiceId == inv.invoiceNumber && inv.invoiceNumber.isNotBlank()) || 
                p.invoiceId == inv.id.toString()
            }
            val paymentRecordsSum = paymentsForInv
                .distinctBy { p -> p.id.ifEmpty { "${p.date}_${p.amount}_${p.paymentMethod}" } }
                .sumOf { p -> p.amount }
            val effectivePaid = if (inv.paidAmount > 0.0) inv.paidAmount else if (inv.dpAmount > 0.0) inv.dpAmount else 0.0
            val actualPaid = maxOf(effectivePaid, paymentRecordsSum)
            maxOf(0.0, inv.totalAmount - actualPaid)
        }
        val projVal = projects.filter { !it.isDeleted }.sumOf { it.totalCost }
        val activeProj = projects.count { !it.isDeleted && ((it.status ?: "").equals("In Progress", ignoreCase = true) || (it.status ?: "").equals("Planning", ignoreCase = true) || (it.status ?: "").equals("Sedang Berjalan", ignoreCase = true) || (it.status ?: "").equals("Proses", ignoreCase = true)) }
        val lowStock = stock.count { !it.isDeleted && it.stockCount <= 5 }
        val totalOrd = orders.count { !it.isDeleted && ((it.status ?: "").equals("Completed", ignoreCase = true) || (it.status ?: "").equals("Selesai", ignoreCase = true) || (it.status ?: "").equals("Lunas", ignoreCase = true)) }

        FinancialSummary(
            totalRevenue = revenue,
            totalPemasukan = totalPemasukanAll,
            totalReceivables = receivables,
            totalProjectValue = projVal,
            activeProjectsCount = activeProj,
            lowStockCount = lowStock,
            totalOrdersCount = totalOrd
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FinancialSummary())

    // --- UI State Preservation across Tab Switches & Rotations (Production Stabilization) ---
    val projectSearchQuery = MutableStateFlow("")
    val projectStatusFilter = MutableStateFlow("All")
    val projectDateFilter = MutableStateFlow("Semua Waktu")
    var projectScrollIndex = 0
    var projectScrollOffset = 0

    val stockSearchQuery = MutableStateFlow("")
    val stockSelectedCatalogId = MutableStateFlow<Int?>(null)
    val stockSelectedVarianId = MutableStateFlow<Int?>(null)
    val stockSelectedFilter = MutableStateFlow("Semua")
    var stockScrollIndex = 0
    var stockScrollOffset = 0

    val invoiceSearchQuery = MutableStateFlow("")
    val invoiceStatusFilter = MutableStateFlow("Semua")
    val invoiceSelectedDetail = MutableStateFlow<Invoice?>(null)
    val invoiceSelectedPayment = MutableStateFlow<Invoice?>(null)
    val invoiceShowAddSale = MutableStateFlow(false)
    var invoiceScrollIndex = 0
    var invoiceScrollOffset = 0

    val riwayatSearchQuery = MutableStateFlow("")
    val riwayatFilter = MutableStateFlow("Semua")
    val riwayatSelectedDetail = MutableStateFlow<Invoice?>(null)
    val settingsSelectedCategory = MutableStateFlow<SettingsCategory?>(null)
    var riwayatScrollIndex = 0
    var riwayatScrollOffset = 0

    // App Notifications StateFlow
    private val _notifications = MutableStateFlow<List<AppSettings.AppNotification>>(emptyList())
    val notifications: StateFlow<List<AppSettings.AppNotification>> = _notifications.asStateFlow()

    fun triggerNotification(
        title: String,
        message: String,
        category: String,
        targetTab: String? = null,
        roleTarget: String = "ALL",
        userId: String = "ALL",
        priority: String = "MEDIUM",
        createdBy: String = "SYSTEM"
    ) {
        val currentList = _notifications.value
        val isDuplicate = currentList.any { 
            it.title == title && it.message == message && Math.abs(System.currentTimeMillis() - it.timestamp) < 5000 
        }
        if (isDuplicate) return

        FirebaseSyncManager.sendPushNotification(title, message)
        
        val notificationId = java.util.UUID.randomUUID().toString()
        val appNotification = AppSettings.AppNotification(
            id = notificationId,
            title = title,
            message = message,
            timestamp = System.currentTimeMillis(),
            category = category,
            targetTab = targetTab,
            isRead = false,
            roleTarget = roleTarget,
            userId = userId,
            priority = priority,
            isArchived = false,
            createdBy = createdBy
        )
        
        // Save locally first
        AppSettings.addNotification(
            getApplication(),
            title = title,
            message = message,
            category = category,
            targetTab = targetTab,
            roleTarget = roleTarget,
            userId = userId,
            priority = priority,
            createdBy = createdBy
        )
        
        // Write to Firestore in cloud
        FirebaseSyncManager.writeNotificationToCloud(appNotification)
        
        _notifications.value = AppSettings.getNotifications(getApplication())
    }

    fun markNotificationAsRead(id: String) {
        val current = _notifications.value.toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index != -1) {
            current[index] = current[index].copy(isRead = true)
            AppSettings.saveNotifications(getApplication(), current)
            _notifications.value = current
            FirebaseSyncManager.updateNotificationInCloud(id, mapOf("isRead" to true))
        }
    }

    fun markAllNotificationsAsRead() {
        val updated = _notifications.value.map { it.copy(isRead = true) }
        AppSettings.saveNotifications(getApplication(), updated)
        _notifications.value = updated
        updated.forEach { item ->
            FirebaseSyncManager.updateNotificationInCloud(item.id, mapOf("isRead" to true))
        }
    }

    fun clearAllNotifications() {
        val current = _notifications.value
        AppSettings.saveNotifications(getApplication(), emptyList())
        _notifications.value = emptyList()
        current.forEach { item ->
            FirebaseSyncManager.deleteItemFromCloud("notifications", item.id)
            FirebaseSyncManager.deleteNotificationFromCloudPermanently(item.id)
        }
    }

    fun deleteNotification(id: String) {
        val current = _notifications.value.toMutableList()
        if (current.removeAll { it.id == id }) {
            AppSettings.saveNotifications(getApplication(), current)
            _notifications.value = current
            FirebaseSyncManager.deleteItemFromCloud("notifications", id)
            FirebaseSyncManager.deleteNotificationFromCloudPermanently(id)
        }
    }

    fun archiveNotification(id: String) {
        val current = _notifications.value.toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index != -1) {
            current[index] = current[index].copy(isArchived = true)
            AppSettings.saveNotifications(getApplication(), current)
            _notifications.value = current
            FirebaseSyncManager.updateNotificationInCloud(id, mapOf("isArchived" to true))
        }
    }

    init {
        // Run invoice deduplication to clean any existing local duplicates
        viewModelScope.launch {
            try {
                repository.deduplicateInvoicesInLocalDb()
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error deduplicating invoices: ${e.message}")
            }
        }

        // Load initial notifications
        _notifications.value = AppSettings.getNotifications(application)

        // Load member cart from SharedPreferences
        viewModelScope.launch {
            loadMemberCartFromPrefs()
        }

        // Initialize Firebase SDK with Offline Persistence
        FirebaseSyncManager.initialize(application)

        // Observe current user changes to dynamically register notification listeners and trigger sync
        viewModelScope.launch {
            FirebaseSyncManager.currentUser.collect { user ->
                notificationListenerReg?.remove()
                notificationListenerReg = null

                if (user != null) {
                    // Trigger silent automatic background synchronization of all collections
                    refreshData(application)

                    // Auto-populate cart and checkout details from user's Account Center profile
                    draftSalesOrderManager.autoPopulateFromAccountCenter(user.email)

                    android.util.Log.d("MainViewModel", "Registering notification listener for email: ${user.email}, role: ${user.role.name}")
                    notificationListenerReg = FirebaseSyncManager.startNotificationListener(
                        getApplication(),
                        userEmail = user.email,
                        userRole = user.role.name,
                        onUpdate = { list ->
                            viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                _notifications.value = list
                                AppSettings.saveNotifications(getApplication(), list)
                            }
                        }
                    )
                } else {
                    _notifications.value = AppSettings.getNotifications(getApplication())
                }
            }
        }

        // Automatically monitor database changes for alerting and local notification triggers (no cloud sync here to prevent infinite loop write storms)
        val syncedStock = mutableMapOf<Int, StockItem>()
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            repository.allStock.collect { items ->
                items.forEach { item ->
                    val last = syncedStock[item.id]
                    if (last == null || last != item) {
                        syncedStock[item.id] = item
                        if (last != null && last.stockCount != item.stockCount) {
                            if (item.stockCount == 0) {
                                triggerNotification("Stok Habis!", "Stok pakaian '${item.name}' telah habis.", "Stock", "STOCK")
                            } else if (item.stockCount <= 5) {
                                triggerNotification("Stok Menipis!", "Sisa stok pakaian '${item.name}' tinggal ${item.stockCount} pcs.", "Stock", "STOCK")
                            }
                        }
                    }
                }
            }
        }
        val syncedProjects = mutableMapOf<Int, ProjectCustom>()
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            repository.allProjects.collect { items ->
                items.forEach { proj ->
                    val last = syncedProjects[proj.id]
                    if (last == null || last != proj) {
                        syncedProjects[proj.id] = proj
                        if (last != null && last.status != proj.status) {
                            val daysRemaining = (proj.endDate - System.currentTimeMillis()) / (86400000)
                            if (daysRemaining in 0..3 && proj.status != "Completed" && proj.status != "Cancelled") {
                                triggerNotification("Deadline Project Dekat!", "Project '${proj.projectName}' mendekati batas waktu dalam $daysRemaining hari.", "Invoice", "PROJECT")
                            }
                        }
                    }
                }
            }
        }
        val syncedInvoices = mutableMapOf<Int, Invoice>()
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            repository.allInvoices.collect { items ->
                items.forEach { inv ->
                    val last = syncedInvoices[inv.id]
                    if (last == null || last != inv) {
                        syncedInvoices[inv.id] = inv
                        if (last != null && last.status != inv.status && inv.status == "BELUM LUNAS") {
                            triggerNotification("Invoice Belum Lunas", "Tagihan ${inv.invoiceNumber} untuk klien ${inv.clientName} belum dibayar.", "Invoice", "INVOICE")
                        }
                    }
                }
            }
        }
    }

    // --- Audit Log actions ---
    fun addAuditLog(activity: String, details: String) {
        viewModelScope.launch {
            val user = FirebaseSyncManager.currentUser.value
            val userName = user?.displayName ?: if (user?.role == UserRole.OWNER) "admin" else "MEMBER"
            val log = AuditLog(
                activity = activity,
                details = details,
                timestamp = System.currentTimeMillis(),
                adminName = userName
            )
            val logId = db.auditLogDao().insertLog(log).toInt()
            FirebaseSyncManager.syncItemToCloud("audit_logs", logId.toString(), log.copy(id = logId))
        }
    }

    fun clearAuditLogs() {
        viewModelScope.launch {
            db.auditLogDao().clearAllLogs()
            addAuditLog("Audit Log", "Riwayat aktivitas telah dibersihkan oleh Admin.")
        }
    }

    // --- Auth actions ---
    fun login(username: String, pssword: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _loginError.value = null
            _isLoginLoading.value = true
            val success = FirebaseSyncManager.loginUser(getApplication(), username, pssword)
            _isLoginLoading.value = false
            if (success) {
                AppFeedbackManager.triggerSuccess()
                val role = FirebaseSyncManager.currentUser.value?.role?.name ?: "OWNER"
                addAuditLog("Login Berhasil", "$role '$username' berhasil masuk ke portal.")
                onSuccess()
            } else {
                AppFeedbackManager.triggerError()
                _loginError.value = "Username/Email atau Password salah."
                addAuditLog("Login Gagal", "Percobaan login gagal oleh user: '$username'.")
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            AppFeedbackManager.triggerSuccess()
            val user = FirebaseSyncManager.currentUser.value
            val userName = user?.displayName ?: "User"
            addAuditLog("Logout", "$userName berhasil keluar dari aplikasi.")
            FirebaseSyncManager.clearSession(getApplication())
            _currentTab.value = AppTab.DASHBOARD
        }
    }

    fun setTab(tab: AppTab) {
        _currentTab.value = tab
    }

    private fun isCurrentUserOwner(): Boolean {
        return FirebaseSyncManager.currentUser.value?.role == UserRole.OWNER
    }

    // --- Stock Actions ---
    fun addStockItem(name: String, sku: String, count: Int, price: Double, costPrice: Double, description: String) {
        viewModelScope.launch {
            repository.insertStock(
                StockItem(
                    name = name,
                    sku = sku,
                    stockCount = count,
                    price = price,
                    costPrice = costPrice,
                    description = description
                )
            )
            AppFeedbackManager.triggerSuccess()
        }
    }

    fun updateStockItem(item: StockItem) {
        viewModelScope.launch {
            repository.updateStock(item)
            AppFeedbackManager.triggerSuccess()
        }
    }

    fun deleteStockItem(item: StockItem) {
        viewModelScope.launch {
            repository.deleteStock(item)
            addAuditLog("Hapus Stock", "Stock '${item.name}' berhasil dihapus.")
            AppFeedbackManager.triggerWarning()
        }
    }

    fun updateSeriesPrices(seriesName: String, hppPendek: Double, hppPanjang: Double, member: Double, retail: Double, reseller: Double, custom: Double) {
        viewModelScope.launch {
            repository.updateSeriesPrices(seriesName, hppPendek, hppPanjang, member, retail, reseller, custom)
        }
    }

    fun saveStockMatrix(seriesName: String, matrixValues: List<Triple<String, String, Int>>) {
        viewModelScope.launch {
            repository.saveStockMatrix(seriesName, matrixValues)
        }
    }

    fun addStockManual(seriesName: String, sleeve: String, size: String, quantity: Int, notes: String) {
        viewModelScope.launch {
            repository.addStockManual(seriesName, sleeve, size, quantity, notes)
            AppFeedbackManager.triggerSuccess()
        }
    }

    fun reduceStockManual(seriesName: String, sleeve: String, size: String, quantity: Int, notes: String) {
        viewModelScope.launch {
            repository.reduceStockManual(seriesName, sleeve, size, quantity, notes)
            AppFeedbackManager.triggerSuccess()
        }
    }

    fun resetSeriesStockToZero(seriesName: String) {
        viewModelScope.launch {
            repository.resetSeriesStockToZero(seriesName)
        }
    }

    // --- REFACTOR STOCK SYSTEM FLOWS ---
    val allCatalogs: StateFlow<List<MasterCatalog>> = repository.allCatalogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trashedCatalogs: StateFlow<List<MasterCatalog>> = repository.trashedCatalogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allVarian: StateFlow<List<MasterVarianWarna>> = repository.allVarian
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trashedVarian: StateFlow<List<MasterVarianWarna>> = repository.trashedVarian
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allStockMaster: StateFlow<List<MasterStock>> = repository.allStockMaster
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allInventoryLedger: StateFlow<List<InventoryLedger>> = repository.allInventoryLedger
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allProductionBatch: StateFlow<List<ProductionBatch>> = repository.allProductionBatch
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allInventorySummary: StateFlow<List<InventorySummary>> = repository.allInventorySummary
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addCatalog(name: String, desc: String, cover: String = "") {
        viewModelScope.launch {
            if (!isCurrentUserOwner()) return@launch
            repository.insertCatalog(MasterCatalog(nama_catalog = name, deskripsi = desc, cover = cover))
            addAuditLog("Tambah Catalog", "Catalog baru '$name' berhasil ditambahkan.")
            AppFeedbackManager.triggerSuccess()
        }
    }

    fun updateCatalog(catalog: MasterCatalog) {
        viewModelScope.launch {
            if (!isCurrentUserOwner()) return@launch
            repository.updateCatalog(catalog)
            addAuditLog("Update Catalog", "Catalog '${catalog.nama_catalog}' berhasil diperbarui.")
            AppFeedbackManager.triggerSuccess()
        }
    }

    fun deleteCatalog(catalog: MasterCatalog) {
        viewModelScope.launch {
            if (!isCurrentUserOwner()) return@launch
            repository.deleteCatalog(catalog)
            addAuditLog("Hapus Catalog", "Catalog '${catalog.nama_catalog}' berhasil dihapus.")
            AppFeedbackManager.triggerWarning()
        }
    }

    fun deleteCatalogsBatch(catalogs: List<MasterCatalog>) {
        viewModelScope.launch {
            if (!isCurrentUserOwner()) return@launch
            catalogs.forEach { catalog ->
                repository.deleteCatalog(catalog)
            }
            addAuditLog("Hapus Batch Catalog", "Menghapus ${catalogs.size} katalog series sekaligus.")
            AppFeedbackManager.triggerWarning()
        }
    }

    fun addVarianWarna(idCatalog: Int, nameWarna: String, kodeWarna: String, cover: String = "") {
        viewModelScope.launch {
            if (!isCurrentUserOwner()) return@launch
            repository.insertVarian(
                MasterVarianWarna(
                    id_catalog = idCatalog,
                    nama_warna = nameWarna,
                    kode_warna = kodeWarna,
                    cover = cover
                )
            )
            addAuditLog("Tambah Varian Warna", "Varian warna baru '$nameWarna' berhasil ditambahkan.")
            AppFeedbackManager.triggerSuccess()
        }
    }

    fun updateVarianWarna(varian: MasterVarianWarna) {
        viewModelScope.launch {
            if (!isCurrentUserOwner()) return@launch
            repository.updateVarian(varian)
            addAuditLog("Update Varian Warna", "Varian warna '${varian.nama_warna}' berhasil diperbarui.")
            AppFeedbackManager.triggerSuccess()
        }
    }

    fun deleteVarianWarna(varian: MasterVarianWarna) {
        viewModelScope.launch {
            if (!isCurrentUserOwner()) return@launch
            repository.deleteVarian(varian)
            addAuditLog("Hapus Varian Warna", "Varian warna '${varian.nama_warna}' berhasil dihapus.")
            AppFeedbackManager.triggerWarning()
        }
    }

    fun deleteVarianWarnaBatch(variants: List<MasterVarianWarna>) {
        viewModelScope.launch {
            if (!isCurrentUserOwner()) return@launch
            variants.forEach { varian ->
                repository.deleteVarian(varian)
            }
            addAuditLog("Hapus Batch Varian", "Menghapus ${variants.size} varian warna sekaligus.")
            AppFeedbackManager.triggerWarning()
        }
    }

    fun batchUpdateVarianStockAndPrices(
        variantIds: List<Int>,
        stockDelta: Int?,
        priceRetail: Double?,
        priceMember: Double?,
        priceReseller: Double?,
        notes: String = ""
    ) {
        viewModelScope.launch {
            if (!isCurrentUserOwner()) return@launch
            val currentStocks = allStockMaster.value
            variantIds.forEach { varianId ->
                val existing = currentStocks.find { it.id_varian == varianId } ?: MasterStock(id_varian = varianId)
                var updated = existing
                if (priceRetail != null && priceRetail > 0) updated = updated.copy(harga_retail = priceRetail)
                if (priceMember != null && priceMember > 0) updated = updated.copy(harga_member = priceMember)
                if (priceReseller != null && priceReseller > 0) updated = updated.copy(harga_reseller = priceReseller)

                if (stockDelta != null && stockDelta != 0) {
                    updated = updated.copy(
                        m_pendek = (updated.m_pendek + stockDelta).coerceAtLeast(0),
                        l_pendek = (updated.l_pendek + stockDelta).coerceAtLeast(0),
                        xl_pendek = (updated.xl_pendek + stockDelta).coerceAtLeast(0)
                    )
                }
                repository.saveVarianStockMatrix(varianId, updated, "Batch Update", notes)
            }
            addAuditLog("Update Batch Stock", "Pembaruan batch stok & harga untuk ${variantIds.size} varian warna.")
            AppFeedbackManager.triggerSuccess()
        }
    }

    fun saveVarianStockMatrix(idVarian: Int, stock: MasterStock, changeType: String = "Update Manual", notes: String = "") {
        viewModelScope.launch {
            if (!isCurrentUserOwner()) return@launch
            repository.saveVarianStockMatrix(idVarian, stock, changeType, notes)
            addAuditLog("Simpan Quantity Stock", "Matrix stock varian ID $idVarian berhasil disimpan via $changeType.")
            AppFeedbackManager.triggerSuccess()
            
            // Log Analytics Event
            val params = android.os.Bundle().apply {
                putInt("id_varian", idVarian)
                putInt("total_stock", stock.total_stock)
                putString("change_type", changeType)
            }
            FirebaseSyncManager.logEvent("tambah_stock", params)

            // FCM Alert check
            if (stock.total_stock == 0) {
                triggerNotification("Stock Habis", "Stok untuk varian warna ID $idVarian telah habis.", "Stock", "STOCK")
            } else if (stock.total_stock <= 5) {
                triggerNotification("Stock Hampir Habis", "Peringatan: Stok varian warna ID $idVarian tersisa tinggal ${stock.total_stock} pcs.", "Stock", "STOCK")
            }
        }
    }

    // --- Project Actions ---
    fun addProject(
        projectName: String,
        clientName: String,
        clientPhone: String,
        description: String,
        totalCost: Double,
        paidAmount: Double,
        status: String,
        startDate: Long,
        endDate: Long,
        productType: String = "Kaos",
        sleeveType: String = "Pendek",
        qtyXS: Int = 0,
        qtyS: Int = 0,
        qtyM: Int = 0,
        qtyL: Int = 0,
        qtyXL: Int = 0,
        qtyXXL: Int = 0,
        qty3XL: Int = 0,
        qty4XL: Int = 0,
        clientInstitution: String = "",
        clientAddress: String = "",
        clientNotes: String = "",
        pic: String = "Owner"
    ) {
        viewModelScope.launch {
            val prefix = AppSettings.getInvoicePrefix(getApplication())
            repository.createProject(
                ProjectCustom(
                    projectName = projectName,
                    clientName = clientName,
                    clientPhone = clientPhone,
                    description = description,
                    totalCost = totalCost,
                    paidAmount = paidAmount,
                    status = status,
                    startDate = startDate,
                    endDate = endDate,
                    productType = productType,
                    sleeveType = sleeveType,
                    qtyXS = qtyXS,
                    qtyS = qtyS,
                    qtyM = qtyM,
                    qtyL = qtyL,
                    qtyXL = qtyXL,
                    qtyXXL = qtyXXL,
                    qty3XL = qty3XL,
                    qty4XL = qty4XL,
                    clientInstitution = clientInstitution,
                    clientAddress = clientAddress,
                    clientNotes = clientNotes,
                    pic = pic
                ),
                prefix
            )
            addAuditLog(
                "Tambah Project",
                "Project '$projectName' senilai ${FormatUtils.formatRupiah(totalCost)} untuk klien $clientName berhasil dibuat."
            )
            AppFeedbackManager.triggerSuccess()
        }
    }

    fun updateProject(project: ProjectCustom) {
        viewModelScope.launch {
            repository.updateProject(project)
            addAuditLog(
                "Update Project",
                "Project '${project.projectName}' diperbarui. Status: ${project.status}."
            )
            AppFeedbackManager.triggerSuccess()
        }
    }

    fun deleteProject(project: ProjectCustom) {
        viewModelScope.launch {
            repository.deleteProject(project)
            addAuditLog(
                "Hapus Project",
                "Project '${project.projectName}' untuk klien ${project.clientName} berhasil dihapus."
            )
            AppFeedbackManager.triggerWarning()
        }
    }

    // --- Order Actions ---
    fun addOrder(
        clientName: String,
        clientPhone: String,
        selectedItems: List<Pair<StockItem, Int>>,
        paidAmount: Double,
        status: String,
        priceType: String = "Retail"
    ) {
        viewModelScope.launch {
            val orderItems = selectedItems.map { (item, qty) ->
                val appliedPrice = when (priceType) {
                    "Member" -> item.priceMember
                    "Reseller" -> item.priceReseller
                    "Custom" -> item.priceCustom
                    else -> item.price
                }
                OrderItemDetail(
                    stockItemId = item.id,
                    name = item.name,
                    quantity = qty,
                    price = appliedPrice
                )
            }
            val total = orderItems.sumOf { it.price * it.quantity }
            val converters = AppTypeConverters()
            
            val order = OrderHistory(
                clientName = clientName,
                clientPhone = clientPhone,
                itemsJson = converters.fromOrderItemList(orderItems),
                totalAmount = total,
                paidAmount = paidAmount,
                isPaid = paidAmount >= total,
                status = status
            )
            val prefix = AppSettings.getInvoicePrefix(getApplication())
            val invoiceNum = repository.generateInvoiceNumber(prefix, order.orderDate)
            repository.createOrder(order, orderItems, prefix)

            // 1. ORDER_NEW Notification for Owner
            triggerNotification(
                title = "Pesanan Baru",
                message = "Pesanan baru senilai ${FormatUtils.formatRupiah(total)} dari customer $clientName berhasil dibuat.",
                category = "Order",
                targetTab = "RIWAYAT",
                roleTarget = "OWNER",
                userId = "ALL"
            )

            // 2. INVOICE_CREATED Notification for Member yang bersangkutan
            triggerNotification(
                title = "Tagihan Baru",
                message = "Invoice $invoiceNum senilai ${FormatUtils.formatRupiah(total)} telah diterbitkan untuk Anda.",
                category = "Invoice",
                targetTab = "RIWAYAT",
                roleTarget = "MEMBER",
                userId = clientName
            )

            // 3. PAYMENT_RECEIVED Notification if initial payment exists
            if (paidAmount > 0.0) {
                triggerNotification(
                    title = "Pembayaran Diterima",
                    message = "Pembayaran cicilan sebesar ${FormatUtils.formatRupiah(paidAmount)} diterima dari $clientName untuk Invoice $invoiceNum.",
                    category = "Invoice",
                    targetTab = "INVOICE",
                    roleTarget = "OWNER",
                    userId = "ALL"
                )
                triggerNotification(
                    title = "Pembayaran Diterima",
                    message = "Pembayaran cicilan sebesar ${FormatUtils.formatRupiah(paidAmount)} untuk Invoice $invoiceNum telah diterima.",
                    category = "Invoice",
                    targetTab = "RIWAYAT",
                    roleTarget = "MEMBER",
                    userId = clientName
                )
            }

            // 4. INVOICE_PAID Notification if fully paid initially
            if (paidAmount >= total) {
                triggerNotification(
                    title = "Invoice Lunas",
                    message = "Invoice $invoiceNum senilai ${FormatUtils.formatRupiah(total)} dari $clientName telah Lunas.",
                    category = "Invoice",
                    targetTab = "INVOICE",
                    roleTarget = "OWNER",
                    userId = "ALL"
                )
                triggerNotification(
                    title = "Tagihan Lunas",
                    message = "Invoice $invoiceNum senilai ${FormatUtils.formatRupiah(total)} telah dinyatakan Lunas. Terima kasih atas kerja sama Anda.",
                    category = "Invoice",
                    targetTab = "RIWAYAT",
                    roleTarget = "MEMBER",
                    userId = clientName
                )
            }

            // Log Generate Invoice
            val invoiceParams = android.os.Bundle().apply {
                putString("client", clientName)
                putDouble("total_amount", total)
            }
            FirebaseSyncManager.logEvent("generate_invoice", invoiceParams)

            if (priceType == "Member") {
                AppSettings.addMember(getApplication(), clientName)
                
                // Log Tambah Member
                val memberParams = android.os.Bundle().apply {
                    putString("member_name", clientName)
                }
                FirebaseSyncManager.logEvent("tambah_member", memberParams)
                
                // FCM Notification for Member Baru Dibuat
                triggerNotification("Member Baru", "Member baru berhasil terdaftar atas nama $clientName.", "Sistem", "SETTINGS")
            }

            addAuditLog(
                "Catat Penjualan AJIBQOBUL",
                "Transaksi Penjualan AJIBQOBUL untuk $clientName berhasil dicatat. Tipe Harga: $priceType, Total: ${FormatUtils.formatRupiah(total)}."
            )
            AppFeedbackManager.triggerSuccess()
        }
    }

    fun deleteOrder(order: OrderHistory) {
        viewModelScope.launch {
            // Find linked invoice first before database deletion
            val invoices = allInvoices.value
            val linkedInvoice = invoices.find { it.orderId == order.id }
            
            repository.deleteOrder(order)
            
            FirebaseSyncManager.deleteItemFromCloud("orders", order.id.toString())
            if (linkedInvoice != null) {
                FirebaseSyncManager.deleteItemFromCloud("invoices", linkedInvoice.id.toString())
            } else {
                FirebaseSyncManager.deleteItemFromCloud("invoices", "associated_with_order_${order.id}")
            }
            addAuditLog(
                "Hapus Transaksi",
                "Transaksi penjualan untuk customer ${order.clientName} senilai ${FormatUtils.formatRupiah(order.totalAmount)} dihapus."
            )
            AppFeedbackManager.triggerWarning()
        }
    }

    // --- Invoice Actions ---
    fun duplicateInvoice(invoice: Invoice) {
        viewModelScope.launch {
            val prefix = AppSettings.getInvoicePrefix(getApplication())
            val dateMillis = System.currentTimeMillis()
            val newInvoiceNumber = repository.generateInvoiceNumber(prefix, dateMillis)
            val duplicated = invoice.copy(
                id = 0,
                invoiceNumber = newInvoiceNumber,
                issueDate = dateMillis,
                dueDate = dateMillis + (invoice.dueDate - invoice.issueDate).coerceAtLeast(0L),
                paidAmount = 0.0,
                dpAmount = 0.0,
                status = "BELUM LUNAS",
                isDeleted = false
            )
            repository.createDirectInvoice(duplicated)
            addAuditLog(
                "Duplikasi Invoice",
                "Invoice '${invoice.invoiceNumber}' berhasil diduplikasi menjadi '$newInvoiceNumber'."
            )
            AppFeedbackManager.triggerSuccess()
        }
    }

    fun updateInvoicePayment(invoiceId: Int, paidAmount: Double, dpAmount: Double = 0.0, dpType: String? = null) {
        viewModelScope.launch {
            repository.updateInvoicePayment(invoiceId, paidAmount, dpAmount, dpType)
            addAuditLog(
                "Pembayaran Invoice",
                "Invoice ID: $invoiceId memperbarui pembayaran sebesar ${FormatUtils.formatRupiah(paidAmount)} (Tipe DP: ${dpType ?: "Otomatis"})."
            )
            AppFeedbackManager.triggerSuccess()
        }
    }

    fun getPaymentsForInvoice(invoiceId: String, invoiceNumber: String = ""): Flow<List<InvoicePayment>> {
        return repository.getPaymentsForInvoice(invoiceId, invoiceNumber)
    }

    fun addInvoicePayment(
        invoiceId: Int,
        amount: Double,
        method: String,
        methodDetail: String,
        notes: String,
        customDate: Long? = null,
        onComplete: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            val adminName = FirebaseSyncManager.currentUser.value?.displayName ?: "Admin"
            val adminUid = FirebaseSyncManager.currentUser.value?.email ?: "ADMIN_EMAIL"
            val success = repository.addInvoicePayment(
                invoiceId, amount, method, methodDetail, notes, adminName, adminUid, customDate
            )
            if (success) {
                val invoice = allInvoices.value.find { it.id == invoiceId }
                if (invoice != null) {
                    val finalPaidAmount = invoice.paidAmount + amount
                    val clientName = invoice.clientName
                    val invoiceNum = invoice.invoiceNumber
                    if (finalPaidAmount >= invoice.totalAmount) {
                        // Notify MEMBER
                        triggerNotification(
                            title = "Tagihan Lunas",
                            message = "Invoice $invoiceNum senilai Rp ${String.format("%,.0f", invoice.totalAmount)} telah Lunas. Terima kasih atas kerja sama Anda.",
                            category = "Invoice",
                            targetTab = "RIWAYAT",
                            roleTarget = "MEMBER",
                            userId = clientName
                        )
                        // Notify OWNER
                        triggerNotification(
                            title = "Invoice Lunas",
                            message = "Invoice $invoiceNum senilai Rp ${String.format("%,.0f", invoice.totalAmount)} dari $clientName telah Lunas.",
                            category = "Invoice",
                            targetTab = "INVOICE",
                            roleTarget = "OWNER",
                            userId = "ALL"
                        )
                    } else {
                        // Notify MEMBER
                        triggerNotification(
                            title = "Pembayaran Diterima",
                            message = "Pembayaran sebesar Rp ${String.format("%,.0f", amount)} untuk Invoice $invoiceNum telah diterima.",
                            category = "Invoice",
                            targetTab = "RIWAYAT",
                            roleTarget = "MEMBER",
                            userId = clientName
                        )
                        // Notify OWNER
                        triggerNotification(
                            title = "Pembayaran Diterima",
                            message = "Pembayaran sebesar Rp ${String.format("%,.0f", amount)} diterima dari $clientName untuk Invoice $invoiceNum.",
                            category = "Invoice",
                            targetTab = "INVOICE",
                            roleTarget = "OWNER",
                            userId = "ALL"
                        )
                    }
                }
                addAuditLog(
                    "Tambah Pembayaran",
                    "Menambahkan pembayaran $method sebesar ${FormatUtils.formatRupiah(amount)} untuk Invoice ID: $invoiceId."
                )
                AppFeedbackManager.triggerSuccess()
            }
            onComplete(success)
        }
    }

    fun editInvoicePayment(
        paymentId: String,
        invoiceId: Int,
        newAmount: Double,
        method: String,
        methodDetail: String,
        notes: String,
        customDate: Long? = null,
        onComplete: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            val adminName = FirebaseSyncManager.currentUser.value?.displayName ?: "Admin"
            val adminUid = FirebaseSyncManager.currentUser.value?.email ?: "ADMIN_EMAIL"
            val success = repository.editInvoicePayment(
                paymentId, invoiceId, newAmount, method, methodDetail, notes, adminName, adminUid, customDate
            )
            if (success) {
                addAuditLog(
                    "Edit Pembayaran",
                    "Mengubah pembayaran ID: $paymentId menjadi sebesar ${FormatUtils.formatRupiah(newAmount)} untuk Invoice ID: $invoiceId."
                )
                AppFeedbackManager.triggerSuccess()
            }
            onComplete(success)
        }
    }

    fun deleteInvoicePayment(
        paymentId: String,
        invoiceId: Int,
        onComplete: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            val success = repository.deleteInvoicePayment(paymentId, invoiceId)
            if (success) {
                addAuditLog(
                    "Hapus Pembayaran",
                    "Menghapus pembayaran ID: $paymentId dari Invoice ID: $invoiceId."
                )
                AppFeedbackManager.triggerSuccess()
            }
            onComplete(success)
        }
    }

    fun deleteInvoice(invoice: Invoice) {
        viewModelScope.launch {
            repository.deleteInvoice(invoice)
            addAuditLog(
                "Hapus Invoice",
                "Invoice '${invoice.invoiceNumber}' untuk customer ${invoice.clientName} berhasil dihapus."
            )
            AppFeedbackManager.triggerWarning()
        }
    }

    fun cancelInvoice(invoiceId: Int) {
        viewModelScope.launch {
            repository.cancelInvoice(invoiceId)
            addAuditLog(
                "Batal Invoice",
                "Invoice ID: $invoiceId telah dibatalkan."
            )
            AppFeedbackManager.triggerWarning()
        }
    }

    fun updateInvoiceMetadata(invoiceId: Int, name: String, phone: String, address: String, notes: String) {
        viewModelScope.launch {
            repository.updateInvoiceMetadata(invoiceId, name, phone, address, notes)
            addAuditLog(
                "Update Metadata Invoice",
                "Metadata Invoice ID: $invoiceId berhasil diperbarui."
            )
            AppFeedbackManager.triggerSuccess()
        }
    }

    fun updateInvoiceFully(invoice: Invoice) {
        viewModelScope.launch {
            repository.updateInvoiceFully(invoice)
            addAuditLog(
                "Update Invoice Lengkap",
                "Invoice ID: ${invoice.id} (${invoice.invoiceNumber}) berhasil diperbarui penuh oleh Owner."
            )
            AppFeedbackManager.triggerSuccess()
        }
    }

    fun approveSalesOrder(invoiceId: Int, context: android.content.Context, callback: (String?) -> Unit) {
        viewModelScope.launch {
            val user = FirebaseSyncManager.currentUser.value
            val userName = user?.displayName ?: "Owner"
            val error = repository.approveSalesOrder(invoiceId, userName, context)
            callback(error)
            if (error == null) {
                AppFeedbackManager.triggerSuccess()
            } else {
                AppFeedbackManager.triggerError()
            }
        }
    }

    fun rejectSalesOrder(invoiceId: Int, context: android.content.Context, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            val user = FirebaseSyncManager.currentUser.value
            val userName = user?.displayName ?: "Owner"
            val success = repository.rejectSalesOrder(invoiceId, userName)
            callback(success)
            if (success) {
                AppFeedbackManager.triggerSuccess()
            } else {
                AppFeedbackManager.triggerError()
            }
        }
    }

    // --- Expense Actions ---
    fun addExpense(category: String, amount: Double, date: Long, notes: String, paymentMethod: String = "Cash") {
        viewModelScope.launch {
            val user = FirebaseSyncManager.currentUser.value
            val userName = user?.displayName ?: "Owner"
            val expense = Expense(
                category = category,
                amount = amount,
                date = date,
                notes = notes,
                paymentMethod = paymentMethod,
                createdBy = userName,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            val id = repository.insertExpense(expense).toInt()
            val insertedExpense = expense.copy(
                id = id,
                transactionNumber = repository.generateExpenseTransactionNumber(date)
            )
            repository.updateExpense(insertedExpense)
            FirebaseSyncManager.syncItemToCloud("expenses", id.toString(), insertedExpense)
            addAuditLog(
                "Tambah Pengeluaran",
                "Mencatat pengeluaran '${insertedExpense.transactionNumber}' ($category) sebesar ${FormatUtils.formatRupiah(amount)}."
            )
            AppFeedbackManager.triggerSuccess()
        }
    }

    fun updateExpense(expense: Expense) {
        viewModelScope.launch {
            val updated = expense.copy(
                updatedAt = System.currentTimeMillis()
            )
            repository.updateExpense(updated)
            FirebaseSyncManager.syncItemToCloud("expenses", expense.id.toString(), updated)
            addAuditLog(
                "Update Pengeluaran",
                "Mengubah pengeluaran '${expense.transactionNumber}' (${expense.category}) menjadi ${FormatUtils.formatRupiah(expense.amount)}."
            )
            AppFeedbackManager.triggerSuccess()
        }
    }

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch {
            repository.deleteExpense(expense)
            addAuditLog(
                "Hapus Pengeluaran",
                "Pengeluaran '${expense.transactionNumber}' (${expense.category}) senilai ${FormatUtils.formatRupiah(expense.amount)} berhasil dihapus."
            )
            AppFeedbackManager.triggerWarning()
        }
    }

    // --- Inflow Actions ---
    fun addInflow(category: String, amount: Double, date: Long, notes: String, photoUrl: String = "", paymentMethod: String = "Cash") {
        viewModelScope.launch {
            val user = FirebaseSyncManager.currentUser.value
            val userName = user?.displayName ?: "Owner"
            val inflow = Inflow(
                category = category,
                amount = amount,
                date = date,
                notes = notes,
                photoUrl = photoUrl,
                paymentMethod = paymentMethod,
                createdBy = userName,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            val id = repository.insertInflow(inflow).toInt()
            val insertedInflow = inflow.copy(
                id = id,
                transactionNumber = repository.generateInflowTransactionNumber(date)
            )
            repository.updateInflow(insertedInflow)
            FirebaseSyncManager.syncItemToCloud("inflows", id.toString(), insertedInflow)
            addAuditLog(
                "Tambah Pemasukan",
                "Mencatat pemasukan '${insertedInflow.transactionNumber}' ($category) sebesar ${FormatUtils.formatRupiah(amount)}."
            )
            AppFeedbackManager.triggerSuccess()
        }
    }

    fun updateInflow(inflow: Inflow) {
        viewModelScope.launch {
            val updated = inflow.copy(
                updatedAt = System.currentTimeMillis()
            )
            repository.updateInflow(updated)
            FirebaseSyncManager.syncItemToCloud("inflows", inflow.id.toString(), updated)
            addAuditLog(
                "Update Pemasukan",
                "Mengubah pemasukan '${inflow.transactionNumber}' (${inflow.category}) menjadi ${FormatUtils.formatRupiah(inflow.amount)}."
            )
            AppFeedbackManager.triggerSuccess()
        }
    }

    fun deleteInflow(inflow: Inflow) {
        viewModelScope.launch {
            repository.deleteInflow(inflow)
            addAuditLog(
                "Hapus Pemasukan",
                "Pemasukan '${inflow.transactionNumber}' (${inflow.category}) senilai ${FormatUtils.formatRupiah(inflow.amount)} berhasil dihapus."
            )
            AppFeedbackManager.triggerWarning()
        }
    }

    // --- Member Shopping Cart State & Actions ---
    val draftSalesOrder: StateFlow<DraftSalesOrder> = draftSalesOrderManager.draftSalesOrderFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = DraftSalesOrder()
        )

    val memberCart: StateFlow<List<MemberCartItem>> = draftSalesOrder
        .map { draftSalesOrderManager.deserializeCartItems(it.itemsJson) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    fun loadMemberCartFromPrefs() {
        // No-op since we are now reactively using Room as the single source of truth
    }

    fun updateDraftClientName(name: String) {
        draftSalesOrderManager.updateClientName(name)
    }

    fun updateDraftClientPhone(phone: String) {
        draftSalesOrderManager.updateClientPhone(phone)
    }

    fun updateDraftClientAddress(address: String) {
        draftSalesOrderManager.updateClientAddress(address)
    }

    fun syncDraftWithAccountCenter() {
        FirebaseSyncManager.currentUser.value?.let { user ->
            draftSalesOrderManager.autoPopulateFromAccountCenter(user.email, forceOverwrite = true)
        }
    }

    fun updateDraftNotes(notes: String) {
        draftSalesOrderManager.updateNotes(notes)
    }

    fun updateVarianCartItems(catalogId: Int, varianId: Int, newItems: List<MemberCartItem>) {
        val currentList = memberCart.value.filter {
            !(it.catalogId == catalogId && it.varianId == varianId)
        }.toMutableList()
        currentList.addAll(newItems)
        draftSalesOrderManager.updateCartItems(currentList)
        AppFeedbackManager.triggerSuccess()
    }

    fun addToMemberCart(item: MemberCartItem) {
        val currentList = memberCart.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == item.id }
        if (index != -1) {
            val existing = currentList[index]
            currentList[index] = existing.copy(qty = existing.qty + item.qty)
        } else {
            currentList.add(item)
        }
        draftSalesOrderManager.updateCartItems(currentList)
        AppFeedbackManager.triggerSuccess()
    }

    fun updateMemberCartQty(itemId: String, newQty: Int) {
        if (newQty <= 0) {
            removeFromMemberCart(itemId)
            return
        }
        val currentList = memberCart.value.map {
            if (it.id == itemId) it.copy(qty = newQty) else it
        }
        draftSalesOrderManager.updateCartItems(currentList)
    }

    fun removeFromMemberCart(itemId: String) {
        val currentList = memberCart.value.filter { it.id != itemId }
        draftSalesOrderManager.updateCartItems(currentList)
    }

    fun clearMemberCart() {
        draftSalesOrderManager.clearDraft()
    }

    fun checkoutMemberCart(
        clientName: String,
        clientPhone: String,
        address: String,
        notes: String,
        cartItems: List<MemberCartItem>,
        onComplete: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val currentUserEmail = FirebaseSyncManager.currentUser.value?.email ?: ""
                val orderDate = System.currentTimeMillis()
                val prefix = AppSettings.getInvoicePrefix(getApplication())
                
                // Generate unique invoice number
                val invoiceNum = repository.generateInvoiceNumber(prefix, orderDate)
                val total = cartItems.sumOf { it.price * it.qty }

                // Call AtomicCheckoutEngine to perform transaction on Firestore
                com.yansproject.app.data.AtomicCheckoutEngine.executeAtomicCheckout(
                    context = getApplication(),
                    clientName = clientName,
                    clientPhone = clientPhone,
                    clientAddress = address,
                    notes = notes,
                    cartItems = cartItems,
                    invoiceNum = invoiceNum,
                    totalAmount = total,
                    currentUserEmail = currentUserEmail,
                    onComplete = { success, result ->
                        if (success) {
                            // On successful atomic checkout, mirror the exact same invoice to local Room database immediately!
                            viewModelScope.launch {
                                try {
                                    val invoiceItems = cartItems.map { item ->
                                        InvoiceItemDetail(
                                            description = "AJIBQOBUL: ${item.catalogName} - ${item.varianName} - ${item.size} - ${item.sleeve}",
                                            quantity = item.qty,
                                            price = item.price
                                        )
                                    }.toMutableList()

                                    // Add Address and Notes metadata markers
                                    if (address.isNotBlank()) {
                                        invoiceItems.add(InvoiceItemDetail("__ADDRESS__:${address.trim()}", 0, 0.0))
                                    }
                                    if (notes.isNotBlank()) {
                                        invoiceItems.add(InvoiceItemDetail("__NOTE__:${notes.trim()}", 0, 0.0))
                                    }
                                    if (currentUserEmail.isNotBlank()) {
                                        invoiceItems.add(InvoiceItemDetail("__EMAIL__:${currentUserEmail.trim().lowercase()}", 0, 0.0))
                                    }

                                    val converters = AppTypeConverters()
                                    val invoice = Invoice(
                                        id = 0, // Autogenerated by Room
                                        invoiceNumber = invoiceNum,
                                        clientName = clientName,
                                        clientPhone = clientPhone,
                                        issueDate = orderDate,
                                        dueDate = orderDate + (86400000 * 3), // Due in 3 days
                                        totalAmount = total,
                                        paidAmount = 0.0,
                                        status = "MENUNGGU PERSETUJUAN",
                                        projectId = null,
                                        orderId = 1, // Indicate that it's a Stock order (AJIBQOBUL)
                                        itemsJson = converters.fromInvoiceItemList(invoiceItems),
                                        discount = 0.0,
                                        dpAmount = 0.0,
                                        isDeleted = false
                                    )
                                    
                                    // Save locally (triggers inventory summary update and reserves stock)
                                    repository.createDirectInvoice(invoice)
                                    repository.deduplicateInvoicesInLocalDb()

                                    // Clear the cart locally
                                    clearMemberCart()

                                    // Local notifications & analytics logging
                                    val invoiceParams = android.os.Bundle().apply {
                                        putString("client", clientName)
                                        putDouble("total_amount", total)
                                    }
                                    FirebaseSyncManager.logEvent("member_checkout_invoice", invoiceParams)
                                    
                                    onComplete(true, result)
                                } catch (e: Exception) {
                                    Log.e("MainViewModel", "Error saving invoice or updating stock locally: ${e.message}")
                                    onComplete(true, result) // still consider success because Firestore transaction completed successfully
                                }
                            }
                        } else {
                            onComplete(false, result)
                        }
                    }
                )
            } catch (e: Exception) {
                onComplete(false, e.localizedMessage ?: "Gagal memproses checkout atomik.")
            }
        }
    }

    // --- RESTORE & PERMANENT DELETE OPERATIONS ---
    fun restoreCatalog(catalog: MasterCatalog) {
        viewModelScope.launch {
            if (!isCurrentUserOwner()) return@launch
            repository.restoreCatalog(catalog)
            FirebaseSyncManager.syncItemToCloud("master_catalog", catalog.id_catalog.toString(), catalog.copy(isDeleted = false))
            addAuditLog("Pulihkan Catalog", "Catalog '${catalog.nama_catalog}' dipulihkan dari Trash.")
            AppFeedbackManager.triggerSuccess()
        }
    }
    fun deleteCatalogPermanently(catalog: MasterCatalog) {
        viewModelScope.launch {
            if (!isCurrentUserOwner()) return@launch
            val varians = db.varianWarnaDao().getVarianListByCatalog(catalog.id_catalog)
            val stocksToDelete = mutableListOf<MasterStock>()
            for (v in varians) {
                val stock = db.masterStockDao().getStockByVarian(v.id_varian)
                if (stock != null) {
                    stocksToDelete.add(stock)
                }
            }
            
            repository.deleteCatalogPermanently(catalog)
            
            FirebaseSyncManager.deleteItemFromCloud("master_catalog", catalog.id_catalog.toString())
            for (v in varians) {
                FirebaseSyncManager.deleteItemFromCloud("master_varian_warna", v.id_varian.toString())
            }
            for (s in stocksToDelete) {
                FirebaseSyncManager.deleteItemFromCloud("master_stock", s.id_stock.toString())
            }
            addAuditLog("Hapus Permanen Catalog", "Catalog '${catalog.nama_catalog}' dihapus secara permanen.")
            AppFeedbackManager.triggerWarning()
        }
    }

    fun restoreVarianWarna(varian: MasterVarianWarna) {
        viewModelScope.launch {
            if (!isCurrentUserOwner()) return@launch
            val existingStock = db.masterStockDao().getStockByVarian(varian.id_varian)
            
            repository.restoreVarian(varian)
            
            FirebaseSyncManager.syncItemToCloud("master_varian_warna", varian.id_varian.toString(), varian.copy(isDeleted = false))
            if (existingStock != null) {
                FirebaseSyncManager.syncItemToCloud("master_stock", existingStock.id_stock.toString(), existingStock.copy(isDeleted = false))
            }
            addAuditLog("Pulihkan Varian Warna", "Varian warna '${varian.nama_warna}' dipulihkan dari Trash.")
            AppFeedbackManager.triggerSuccess()
        }
    }
    fun deleteVarianPermanently(varian: MasterVarianWarna) {
        viewModelScope.launch {
            if (!isCurrentUserOwner()) return@launch
            val existingStock = db.masterStockDao().getStockByVarian(varian.id_varian)
            
            repository.deleteVarianPermanently(varian)
            
            FirebaseSyncManager.deleteItemFromCloud("master_varian_warna", varian.id_varian.toString())
            if (existingStock != null) {
                FirebaseSyncManager.deleteItemFromCloud("master_stock", existingStock.id_stock.toString())
            }
            addAuditLog("Hapus Permanen Varian Warna", "Varian warna '${varian.nama_warna}' dihapus secara permanen.")
            AppFeedbackManager.triggerWarning()
        }
    }

    fun restoreProject(project: ProjectCustom) {
        viewModelScope.launch {
            if (!isCurrentUserOwner()) return@launch
            val invoices = trashedInvoices.value
            val linkedInvoice = invoices.find { it.projectId == project.id }
            
            repository.restoreProject(project)
            
            FirebaseSyncManager.syncItemToCloud("projects", project.id.toString(), project.copy(isDeleted = false))
            addAuditLog("Pulihkan Project", "Project '${project.projectName}' dipulihkan dari Trash.")
            AppFeedbackManager.triggerSuccess()
        }
    }
    fun deleteProjectPermanently(project: ProjectCustom) {
        viewModelScope.launch {
            if (!isCurrentUserOwner()) return@launch
            val invoices = trashedInvoices.value
            val linkedInvoice = invoices.find { it.projectId == project.id }
            
            repository.deleteProjectPermanently(project)
            
            FirebaseSyncManager.deleteItemFromCloud("projects", project.id.toString())
            if (linkedInvoice != null) {
                FirebaseSyncManager.deleteItemFromCloud("invoices", linkedInvoice.id.toString())
            }
            addAuditLog("Hapus Permanen Project", "Project '${project.projectName}' dihapus secara permanen.")
            AppFeedbackManager.triggerWarning()
        }
    }

    fun restoreInvoice(invoice: Invoice) {
        viewModelScope.launch {
            if (!isCurrentUserOwner()) return@launch
            repository.restoreInvoice(invoice)
            addAuditLog("Pulihkan Invoice", "Invoice '${invoice.invoiceNumber}' dipulihkan dari Trash.")
            AppFeedbackManager.triggerSuccess()
        }
    }
    fun deleteInvoicePermanently(invoice: Invoice) {
        viewModelScope.launch {
            if (!isCurrentUserOwner()) return@launch
            repository.deleteInvoicePermanently(invoice)
            FirebaseSyncManager.deleteItemFromCloud("invoices", invoice.id.toString())
            addAuditLog("Hapus Permanen Invoice", "Invoice '${invoice.invoiceNumber}' dihapus secara permanen.")
            AppFeedbackManager.triggerWarning()
        }
    }

    fun restoreStockItem(item: StockItem) {
        viewModelScope.launch {
            if (!isCurrentUserOwner()) return@launch
            repository.restoreStockItem(item)
            FirebaseSyncManager.syncItemToCloud("stock_items", item.id.toString(), item.copy(isDeleted = false))
            addAuditLog("Pulihkan Stock", "Stock '${item.name}' dipulihkan dari Trash.")
            AppFeedbackManager.triggerSuccess()
        }
    }
    fun deleteStockItemPermanently(item: StockItem) {
        viewModelScope.launch {
            if (!isCurrentUserOwner()) return@launch
            repository.deleteStockItemPermanently(item)
            FirebaseSyncManager.deleteItemFromCloud("stock_items", item.id.toString())
            addAuditLog("Hapus Permanen Stock", "Stock '${item.name}' dihapus secara permanen.")
            AppFeedbackManager.triggerWarning()
        }
    }

    fun restoreInflow(inflow: Inflow) {
        viewModelScope.launch {
            if (!isCurrentUserOwner()) return@launch
            repository.restoreInflow(inflow)
            FirebaseSyncManager.syncItemToCloud("inflows", inflow.id.toString(), inflow.copy(isDeleted = false))
            addAuditLog("Pulihkan Pemasukan", "Pemasukan '${inflow.transactionNumber}' dipulihkan dari Trash.")
            AppFeedbackManager.triggerSuccess()
        }
    }
    fun deleteInflowPermanently(inflow: Inflow) {
        viewModelScope.launch {
            if (!isCurrentUserOwner()) return@launch
            repository.deleteInflowPermanently(inflow)
            FirebaseSyncManager.deleteItemFromCloud("inflows", inflow.id.toString())
            addAuditLog("Hapus Permanen Pemasukan", "Pemasukan '${inflow.transactionNumber}' dihapus secara permanen.")
            AppFeedbackManager.triggerWarning()
        }
    }

    fun restoreExpense(expense: Expense) {
        viewModelScope.launch {
            if (!isCurrentUserOwner()) return@launch
            repository.restoreExpense(expense)
            FirebaseSyncManager.syncItemToCloud("expenses", expense.id.toString(), expense.copy(isDeleted = false))
            addAuditLog("Pulihkan Pengeluaran", "Pengeluaran '${expense.transactionNumber}' dipulihkan dari Trash.")
            AppFeedbackManager.triggerSuccess()
        }
    }
    fun deleteExpensePermanently(expense: Expense) {
        viewModelScope.launch {
            if (!isCurrentUserOwner()) return@launch
            repository.deleteExpensePermanently(expense)
            FirebaseSyncManager.deleteItemFromCloud("expenses", expense.id.toString())
            addAuditLog("Hapus Permanen Pengeluaran", "Pengeluaran '${expense.transactionNumber}' dihapus secara permanen.")
            AppFeedbackManager.triggerWarning()
        }
    }

    // --- Realtime Production Search & Compound Filtering (Firestore Snapshot) ---
    val productionFilterSeries = MutableStateFlow("")
    val productionFilterCode = MutableStateFlow("")
    val productionFilterColor = MutableStateFlow("")
    val productionFilterStatus = MutableStateFlow("")

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val productionSearchResults: StateFlow<List<DomainProduction>> = combine(
        productionFilterSeries,
        productionFilterCode,
        productionFilterColor,
        productionFilterStatus
    ) { series, code, color, status ->
        SearchQueryContainer(series, code, color, status)
    }.flatMapLatest { query ->
        searchRepository.searchProduction(
            seriesName = query.series.ifEmpty { null },
            code = query.code.ifEmpty { null },
            color = query.color.ifEmpty { null },
            stockStatus = query.status.ifEmpty { null }
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    fun clearProductionFilters() {
        productionFilterSeries.value = ""
        productionFilterCode.value = ""
        productionFilterColor.value = ""
        productionFilterStatus.value = ""
    }

    fun addSampleProductionItem(series: String, code: String, color: String, status: String, qty: Int) {
        viewModelScope.launch {
            try {
                val dbRef = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val id = dbRef.collection("production").document().id
                val sampleItem = DomainProduction(
                    id = id,
                    seriesName = series,
                    code = code,
                    color = color,
                    stockStatus = status,
                    quantity = qty,
                    timestamp = System.currentTimeMillis()
                )
                dbRef.collection("production").document(id).set(sampleItem)
                showGlobalSnackbar("Sukses menambahkan item produksi ke Firestore!")
            } catch (e: Exception) {
                Log.e("MainViewModel", "Gagal menambahkan item produksi", e)
                showGlobalSnackbar("Error: ${e.localizedMessage}")
            }
        }
    }

    fun populateSampleProductionData() {
        val samples = listOf(
            DomainProduction("", "AJIBQOBUL RAHASIA REALITA", "AQ-RR-01", "Dark Teal", "Dalam Produksi", 150),
            DomainProduction("", "AJIBQOBUL HINA MULIA", "AQ-HM-02", "Shadow Black", "Selesai QC", 220),
            DomainProduction("", "AJIBQOBUL AKAD SAH", "AQ-AS-03", "Aged Gold", "Menunggu Produksi", 80),
            DomainProduction("", "AJIBQOBUL RAHASIA REALITA", "AQ-RR-04", "Soft Cyan", "Dalam Produksi", 110),
            DomainProduction("", "AJIBQOBUL KHIDMAH", "AQ-KD-05", "Dark Teal Surface", "Packing", 300),
            DomainProduction("", "AJIBQOBUL HINA MULIA", "AQ-HM-06", "Aged Gold", "Selesai QC", 180)
        )
        viewModelScope.launch {
            try {
                val dbRef = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                for (item in samples) {
                    val id = dbRef.collection("production").document().id
                    val itemWithId = item.copy(id = id, timestamp = System.currentTimeMillis() - (samples.indexOf(item) * 60000))
                    dbRef.collection("production").document(id).set(itemWithId)
                }
                showGlobalSnackbar("Berhasil mempopulasi 6 sample data produksi ke Firestore!")
            } catch (e: Exception) {
                Log.e("MainViewModel", "Gagal mempopulasi sample data", e)
                showGlobalSnackbar("Gagal: ${e.localizedMessage}")
            }
        }
    }
}

data class SearchQueryContainer(
    val series: String,
    val code: String,
    val color: String,
    val status: String
)

data class MemberCartItem(
    val id: String, // unique id, e.g. "catalogId_varianId_size_sleeve"
    val catalogId: Int,
    val catalogName: String,
    val varianId: Int,
    val varianName: String,
    val size: String,
    val sleeve: String, // "Pendek" or "Panjang"
    val qty: Int,
    val price: Double
)
