package com.yansproject.app.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yansproject.app.data.FirebaseSyncManager
import com.yansproject.app.data.diagnostics.DiagnosticSeverity
import com.yansproject.app.data.diagnostics.LibraryDiagnosticLog
import com.yansproject.app.data.diagnostics.LibraryDiagnosticReport
import com.yansproject.app.data.diagnostics.LibraryDiagnosticService
import com.yansproject.app.ui.components.*
import com.yansproject.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.net.HttpURLConnection
import java.net.URL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemHealthScreen(
    navController: NavController,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // Real-time Status state: strictly initialized as UNKNOWN / N/A
    var isChecking by remember { mutableStateOf(false) }
    var firebaseStatus by remember { mutableStateOf("UNKNOWN") }
    var n8nStatus by remember { mutableStateOf("UNKNOWN") }
    var paperIdStatus by remember { mutableStateOf("UNKNOWN") }

    // Latency metrics: strictly initialized as N/A
    var firebaseLatency by remember { mutableStateOf("N/A") }
    var n8nLatency by remember { mutableStateOf("N/A") }
    var paperIdLatency by remember { mutableStateOf("N/A") }

    // Fetch actual states
    val actualFirebaseActive = FirebaseSyncManager.isFirebaseActive

    // Library Assets Diagnostics State
    val libraryReport by LibraryDiagnosticService.lastReport.collectAsState()
    val libraryLogs by LibraryDiagnosticService.diagnosticLogs.collectAsState()
    var showLibraryLogsDialog by remember { mutableStateOf(false) }

    // Shared Preferences for API Health settings with URL validation
    val prefs = remember(context) { context.getSharedPreferences("api_health_prefs", Context.MODE_PRIVATE) }
    var n8nWebhookUrl by remember { mutableStateOf(prefs.getString("n8n_url", "https://primary-production.shared.n8n.cloud") ?: "") }
    var paperIdApiKey by remember { mutableStateOf(prefs.getString("paper_api_key", "") ?: "") }

    // Slow blinking transition for warnings
    val infiniteTransition = rememberInfiniteTransition(label = "BlinkingWarning")
    val blinkingAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BlinkingAlpha"
    )

    fun performDiagnosticCheck() {
        isChecking = true
        firebaseStatus = "CHECKING"
        n8nStatus = "CHECKING"
        paperIdStatus = "CHECKING"
        firebaseLatency = "Measuring..."
        n8nLatency = "Measuring..."
        paperIdLatency = "Measuring..."

        viewModel.addAuditLog("System Health Check", "Diagnostik sistem real-time dipicu.")
        coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // Monitor assets/library JSON files integrity
            LibraryDiagnosticService.runDiagnosticAudit(context)

            // Check Firebase Active Status and measure real local check time
            val fbStartTime = System.currentTimeMillis()
            val fbActive = FirebaseSyncManager.isFirebaseActive
            val fbElapsed = maxOf(1L, System.currentTimeMillis() - fbStartTime)
            
            // Actual Web Diagnostics with real HTTP response timing
            val n8nStartTime = System.currentTimeMillis()
            val n8nResult = runCatching {
                val urlToTest = if (n8nWebhookUrl.isNotBlank() && (n8nWebhookUrl.startsWith("http://") || n8nWebhookUrl.startsWith("https://"))) {
                    n8nWebhookUrl
                } else {
                    "https://n8n.io"
                }
                val connection = URL(urlToTest).openConnection() as HttpURLConnection
                connection.connectTimeout = 3000
                connection.readTimeout = 3000
                connection.requestMethod = "GET"
                val code = connection.responseCode
                code
            }
            val n8nElapsed = System.currentTimeMillis() - n8nStartTime

            val paperStartTime = System.currentTimeMillis()
            val paperResult = runCatching {
                val connection = URL("https://api.paper.id").openConnection() as HttpURLConnection
                connection.connectTimeout = 3000
                connection.readTimeout = 3000
                connection.requestMethod = "GET"
                val code = connection.responseCode
                code
            }
            val paperElapsed = System.currentTimeMillis() - paperStartTime

            withContext(kotlinx.coroutines.Dispatchers.Main) {
                firebaseStatus = if (fbActive) "ONLINE" else "OFFLINE"
                firebaseLatency = if (fbActive) "$fbElapsed ms" else "N/A"

                val n8nCode = n8nResult.getOrNull()
                n8nStatus = when {
                    n8nCode in 200..399 -> "API_HEALTHY"
                    n8nCode == 401 || n8nCode == 403 -> "AUTH_REQUIRED"
                    n8nCode != null -> "NETWORK_REACHABLE"
                    else -> "API_UNAVAILABLE"
                }
                n8nLatency = if (n8nCode != null) "$n8nElapsed ms" else "N/A"

                val paperCode = paperResult.getOrNull()
                paperIdStatus = when {
                    paperIdApiKey.isNotBlank() && paperCode in 200..399 -> "API_HEALTHY"
                    paperCode in 200..499 -> if (paperIdApiKey.isBlank()) "AUTH_REQUIRED" else "API_HEALTHY"
                    paperCode != null -> "NETWORK_REACHABLE"
                    else -> "API_UNAVAILABLE"
                }
                paperIdLatency = if (paperCode != null) "$paperElapsed ms" else "N/A"

                isChecking = false
                Toast.makeText(context, "Diagnostik sistem selesai.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Trigger on screen launch
    LaunchedEffect(Unit) {
        performDiagnosticCheck()
    }

    androidx.activity.compose.BackHandler(enabled = true) {
        if (!navController.popBackStack()) {
            viewModel.setTab(AppTab.DASHBOARD)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            YansTopAppBar(
                title = "SYSTEM & API HEALTH",
                subtitle = "Status Server, Konektivitas Cloud & Firebase",
                navigationIcon = {
                    YansBackButton(onClick = {
                        if (!navController.popBackStack()) {
                            viewModel.setTab(AppTab.DASHBOARD)
                        }
                    })
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(ShadowBlack)
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Live Diagnostics Header Card
            SharedPremiumCard(
                modifier = Modifier.fillMaxWidth(),
                borderGlowColor = CyanPulse.copy(alpha = 0.2f),
                padding = 20.dp
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.clickable {
                                val currentUser = com.yansproject.app.data.FirebaseSyncManager.currentUser.value
                                val isAuthorized = currentUser?.role == com.yansproject.app.data.UserRole.OWNER || currentUser?.role == com.yansproject.app.data.UserRole.ADMIN
                                if (isAuthorized) {
                                    navController.navigate("telemetry")
                                    Toast.makeText(context, "Membuka Panel Telemetri Diagnostik Lanjutan.", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Akses Terbatas: Hanya Administrator Berwenang yang dapat membuka Telemetri Lanjutan.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Text(
                                text = "REAL-TIME DIAGNOSTIC CENTRE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = AgedGold,
                                letterSpacing = 1.5.sp
                            )
                            Text(
                                text = "Engine Core Monitoring",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextWhite
                            )
                        }

                        if (isChecking) {
                            CircularProgressIndicator(
                                color = CyanPulse,
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            IconButton(onClick = { performDiagnosticCheck() }) {
                                Icon(
                                    imageVector = Icons.Outlined.Cached,
                                    contentDescription = "Re-check APIs",
                                    tint = AgedGold,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = "Monitoring integrasi eksternal secara aktif untuk menjaga reliabilitas operasional ERP YANSPROJECT.ID. Jika salah satu layanan terputus, silakan lakukan pemeriksaan kredensial di bawah.",
                        fontSize = 11.sp,
                        color = TextMuted,
                        lineHeight = 16.sp,
                        textAlign = TextAlign.Justify
                    )
                }
            }

            // --- SERVICES STATUS LIST ---
            Text(
                text = "INTEGRATION SUITES STATUS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = AgedGold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 4.dp)
            )

            // 1. Firebase Sync Status Card
            val isFirebaseOnline = firebaseStatus == "ONLINE"
            ServiceHealthCard(
                serviceName = "Firebase Cloud Integration",
                status = firebaseStatus,
                latency = firebaseLatency,
                isHealthy = isFirebaseOnline,
                description = "Sinkronisasi Cloud Firestore & Real-time Active Listener.",
                icon = Icons.Outlined.CloudQueue,
                blinkingAlpha = if (!isFirebaseOnline) blinkingAlpha else 1f
            )

            // 2. n8n Automation Status Card
            val isN8nActive = n8nStatus == "ACTIVE"
            ServiceHealthCard(
                serviceName = "n8n Automation Engine",
                status = n8nStatus,
                latency = n8nLatency,
                isHealthy = isN8nActive,
                description = "Webhooks trigger otomatis & Alur Kerja Bisnis ERP.",
                icon = Icons.Outlined.Hub,
                blinkingAlpha = if (!isN8nActive) blinkingAlpha else 1f
            )

            // 3. Paper.id API Integration Card
            val isPaperConnected = paperIdStatus.startsWith("CONNECTED")
            ServiceHealthCard(
                serviceName = "Paper.id Invoicing Gateway",
                status = paperIdStatus,
                latency = paperIdLatency,
                isHealthy = isPaperConnected,
                description = "Gerbang otentikasi eksternal & pengiriman invoice komersial resmi.",
                icon = Icons.Outlined.ReceiptLong,
                blinkingAlpha = if (!isPaperConnected) blinkingAlpha else 1f
            )

            // 4. Library & Manuscripts Assets JSON Diagnostics Card
            val isLibraryHealthy = libraryReport?.isHealthy ?: true
            LibraryAssetsDiagnosticCard(
                report = libraryReport,
                isHealthy = isLibraryHealthy,
                blinkingAlpha = if (!isLibraryHealthy) blinkingAlpha else 1f,
                onAuditNow = {
                    coroutineScope.launch(Dispatchers.IO) {
                        LibraryDiagnosticService.runDiagnosticAudit(context)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Audit integritas berkas assets/library/ selesai.", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onViewLogs = {
                    showLibraryLogsDialog = true
                }
            )

            // --- API CREDENTIALS FORM ---
            Text(
                text = "CREDENTIAL DIAGNOSTIC CORNER",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = AgedGold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 8.dp)
            )

            SharedPremiumCard(
                modifier = Modifier.fillMaxWidth(),
                borderGlowColor = AgedGold.copy(alpha = 0.15f),
                padding = 20.dp
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = "KONFIGURASI INTEGRASI API",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AgedGold
                    )

                    SmartTextField(
                        value = n8nWebhookUrl,
                        onValueChange = { n8nWebhookUrl = it },
                        label = "n8n Automation Root Endpoint",
                        placeholder = "https://your-n8n.instance/webhook"
                    )

                    SmartTextField(
                        value = paperIdApiKey,
                        onValueChange = { paperIdApiKey = it },
                        label = "Paper.id Live API Key",
                        placeholder = "Masukkan Paper.id API Key untuk pengetesan..."
                    )

                    Button(
                        onClick = {
                            prefs.edit()
                                .putString("n8n_url", n8nWebhookUrl)
                                .putString("paper_api_key", paperIdApiKey)
                                .apply()
                            Toast.makeText(context, "Konfigurasi integrasi berhasil disimpan!", Toast.LENGTH_SHORT).show()
                            performDiagnosticCheck()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AgedGold,
                            contentColor = ShadowBlack
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.Outlined.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("SIMPAN & TES DIAGNOSTIK", fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                    }
                }
            }
        }
    }

    // Modal dialog to inspect real-time library diagnostic logs
    if (showLibraryLogsDialog) {
        LibraryDiagnosticLogsDialog(
            logs = libraryLogs,
            report = libraryReport,
            onDismiss = { showLibraryLogsDialog = false },
            onClear = {
                LibraryDiagnosticService.clearLogs()
                Toast.makeText(context, "Log diagnostik dibersihkan.", Toast.LENGTH_SHORT).show()
            },
            onExport = {
                val text = LibraryDiagnosticService.exportLogsAsText()
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Library Diagnostics", text)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Log diagnostik disalin ke clipboard.", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
fun ServiceHealthCard(
    serviceName: String,
    status: String,
    latency: String,
    isHealthy: Boolean,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    blinkingAlpha: Float
) {
    SharedPremiumCard(
        modifier = Modifier.fillMaxWidth(),
        borderGlowColor = if (isHealthy) CyanPulse.copy(alpha = 0.1f) else AmberWarning.copy(alpha = 0.3f),
        padding = 16.dp
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = AgedGold,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = serviceName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                }

                // Health Badge with blinking warning logic
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(40.dp))
                        .background(
                            if (isHealthy) AlertGreen.copy(alpha = 0.12f) else AmberWarning.copy(
                                alpha = 0.12f * blinkingAlpha
                            )
                        )
                        .border(
                            1.dp,
                            if (isHealthy) AlertGreen else AmberWarning.copy(alpha = blinkingAlpha),
                            RoundedCornerShape(40.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = status,
                        color = if (isHealthy) AlertGreen else AmberWarning,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Text(
                text = description,
                fontSize = 11.sp,
                color = TextMuted,
                lineHeight = 15.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Respons Ping / Latensi",
                    fontSize = 10.sp,
                    color = TextMuted
                )
                Text(
                    text = latency,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isHealthy) CyanPulse else AlertOrange
                )
            }
        }
    }
}

@Composable
fun LibraryAssetsDiagnosticCard(
    report: LibraryDiagnosticReport?,
    isHealthy: Boolean,
    blinkingAlpha: Float,
    onAuditNow: () -> Unit,
    onViewLogs: () -> Unit
) {
    SharedPremiumCard(
        modifier = Modifier.fillMaxWidth(),
        borderGlowColor = if (isHealthy) CyanPulse.copy(alpha = 0.15f) else AmberWarning.copy(alpha = 0.35f),
        padding = 16.dp
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.MenuBook,
                        contentDescription = null,
                        tint = AgedGold,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "Library Assets JSON Monitor",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite
                        )
                        Text(
                            text = "assets/library/ Manifest & Metadata",
                            fontSize = 10.sp,
                            color = TextMuted
                        )
                    }
                }

                val statusLabel = when (report?.status) {
                    "HEALTHY" -> "HEALTHY"
                    "WARNINGS_FOUND" -> "WARNING"
                    "ERRORS_HANDLED" -> "RECOVERED"
                    else -> "MONITORED"
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(40.dp))
                        .background(
                            if (isHealthy) AlertGreen.copy(alpha = 0.12f)
                            else AmberWarning.copy(alpha = 0.12f * blinkingAlpha)
                        )
                        .border(
                            1.dp,
                            if (isHealthy) AlertGreen else AmberWarning.copy(alpha = blinkingAlpha),
                            RoundedCornerShape(40.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = statusLabel,
                        color = if (isHealthy) AlertGreen else AmberWarning,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Text(
                text = "Memantau berkas metadata.json dan juz_*.json secara aktif untuk mencegah crash akibat JSON rusak, missing keys, atau berkas hilang.",
                fontSize = 11.sp,
                color = TextMuted,
                lineHeight = 15.sp
            )

            // Diagnostic stats row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF091718), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Total Terverifikasi", fontSize = 9.sp, color = TextMuted)
                    Text(
                        text = "${report?.totalAssetsChecked ?: 0} Aset (${report?.booksDiscovered ?: 0} Kitab)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = HighlightSoftCyan
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Integritas Berkas", fontSize = 9.sp, color = TextMuted)
                    Text(
                        text = if ((report?.errorCount ?: 0) == 0) "100% Bebas Crash" else "${report?.errorCount} Isu Ditangani",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if ((report?.errorCount ?: 0) == 0) AlertGreen else AlertOrange
                    )
                }
            }

            // Quick actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onViewLogs,
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AgedGold),
                    border = BorderStroke(1.dp, AgedGold.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Outlined.Analytics, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("LIHAT LOGS", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onAuditNow,
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkTeal, contentColor = TextWhite),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Outlined.Cached, contentDescription = null, modifier = Modifier.size(16.dp), tint = CyanPulse)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("AUDIT ASET", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun LibraryDiagnosticLogsDialog(
    logs: List<LibraryDiagnosticLog>,
    report: LibraryDiagnosticReport?,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
    onExport: () -> Unit
) {
    var filterSeverity by remember { mutableStateOf<DiagnosticSeverity?>(null) }
    val filteredLogs = remember(logs, filterSeverity) {
        if (filterSeverity == null) logs else logs.filter { it.severity == filterSeverity }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.88f),
                shape = RoundedCornerShape(16.dp),
                color = DeepCanvas,
                border = BorderStroke(1.dp, AgedGold.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "LIBRARY ASSETS DIAGNOSTIC LOGS",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                color = AgedGold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Monitoring assets/library/ JSON runtime events",
                                fontSize = 10.sp,
                                color = TextMuted
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Tutup",
                                tint = TextLight
                            )
                        }
                    }

                    // Severity filter tabs
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val filters = listOf(
                            null to "SEMUA (${logs.size})",
                            DiagnosticSeverity.SUCCESS to "OK (${logs.count { it.severity == DiagnosticSeverity.SUCCESS }})",
                            DiagnosticSeverity.WARNING to "WARN (${logs.count { it.severity == DiagnosticSeverity.WARNING }})",
                            DiagnosticSeverity.ERROR to "ERR (${logs.count { it.severity == DiagnosticSeverity.ERROR }})"
                        )

                        for ((sev, label) in filters) {
                            val isSelected = filterSeverity == sev
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) AgedGold else DarkTeal.copy(alpha = 0.5f))
                                    .clickable { filterSeverity = sev }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 9.sp,
                                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                                    color = if (isSelected) ShadowBlack else TextLight
                                )
                            }
                        }
                    }

                    // Log list
                    if (filteredLogs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Belum ada catatan log diagnostik untuk filter ini.",
                                color = TextMuted,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredLogs, key = { it.id }) { item ->
                                val sevColor = when (item.severity) {
                                    DiagnosticSeverity.SUCCESS -> AlertGreen
                                    DiagnosticSeverity.WARNING -> AmberWarning
                                    DiagnosticSeverity.ERROR -> AlertRed
                                    DiagnosticSeverity.INFO -> HighlightSoftCyan
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF071213), RoundedCornerShape(8.dp))
                                        .border(0.8.dp, sevColor.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(sevColor.copy(alpha = 0.15f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = item.severity.name,
                                                    color = sevColor,
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Black
                                                )
                                            }
                                            Text(
                                                text = item.operation,
                                                color = HighlightSoftCyan,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Text(
                                            text = item.formattedTime,
                                            color = TextMuted,
                                            fontSize = 9.sp
                                        )
                                    }

                                    Text(
                                        text = item.targetPath,
                                        color = TextWhite,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )

                                    Text(
                                        text = item.message,
                                        color = TextLight,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp
                                    )

                                    if (item.details != null) {
                                        Text(
                                            text = item.details,
                                            color = AlertOrange,
                                            fontSize = 9.sp,
                                            lineHeight = 13.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Action buttons at bottom
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onClear,
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AlertRed),
                            border = BorderStroke(1.dp, AlertRed.copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("BERSIHKAN", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onExport,
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AgedGold, contentColor = ShadowBlack)
                        ) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("SALIN SEMUA", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
