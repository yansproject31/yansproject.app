package com.yansproject.app.ui

import android.content.Context
import android.os.SystemClock
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Cached
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Speed
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
import com.yansproject.app.data.AppDatabase
import com.yansproject.app.data.YansRoomDatabase
import com.yansproject.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class TelemetryMetricItem(
    val id: String,
    val title: String,
    val value: String,
    val category: String,
    val isAlert: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerformanceTelemetryScreen(
    onBack: () -> Unit,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Realtime telemetry metrics state
    var maxMemoryMb by remember { mutableStateOf(0L) }
    var totalAllocatedMb by remember { mutableStateOf(0L) }
    var freeMemoryMb by remember { mutableStateOf(0L) }
    var usedMemoryMb by remember { mutableStateOf(0L) }
    var dbQuerySpeedMs by remember { mutableStateOf("NOT TESTED") }
    var offlineQueueCount by remember { mutableStateOf(0) }
    var localInvoicesCount by remember { mutableStateOf(0) }
    var isTestingQuerySpeed by remember { mutableStateOf(false) }

    // Live update loop for memory tracking
    LaunchedEffect(Unit) {
        while (true) {
            val runtime = Runtime.getRuntime()
            maxMemoryMb = runtime.maxMemory() / (1024 * 1024)
            totalAllocatedMb = runtime.totalMemory() / (1024 * 1024)
            freeMemoryMb = runtime.freeMemory() / (1024 * 1024)
            usedMemoryMb = totalAllocatedMb - freeMemoryMb

            // Load offline action and local invoice count
            withContext(Dispatchers.IO) {
                try {
                    val offlineDao = YansRoomDatabase.getDatabase(context).offlineActionDao()
                    offlineQueueCount = offlineDao.getAllActions().size

                    val invoiceDao = AppDatabase.getDatabase(context).invoiceDao()
                    localInvoicesCount = invoiceDao.getInvoicesList().size
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            delay(2000) // update stats every 2 seconds
        }
    }

    fun runDbBenchmark() {
        if (isTestingQuerySpeed) return
        isTestingQuerySpeed = true
        coroutineScope.launch {
            val startTime = SystemClock.elapsedRealtimeNanos()
            withContext(Dispatchers.IO) {
                try {
                    // Force a database fetch from room to benchmark query execution
                    AppDatabase.getDatabase(context).invoiceDao().getInvoicesList()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            val endTime = SystemClock.elapsedRealtimeNanos()
            val latencyMs = (endTime - startTime) / 1_000_000.0
            dbQuerySpeedMs = String.format("%.2f ms", latencyMs)
            isTestingQuerySpeed = false
            
            viewModel.addAuditLog("Benchmark DB", "Uji performa database lokal dilakukan: $dbQuerySpeedMs")
        }
    }

    val systemStatusList = remember(usedMemoryMb, dbQuerySpeedMs, offlineQueueCount, localInvoicesCount) {
        listOf(
            TelemetryMetricItem("jvm_used", "Memory Terpakai (JVM)", "$usedMemoryMb MB", "MEMORY", isAlert = (usedMemoryMb > maxMemoryMb * 0.8)),
            TelemetryMetricItem("jvm_alloc", "Total Alokasi Heap", "$totalAllocatedMb MB / $maxMemoryMb MB", "MEMORY"),
            TelemetryMetricItem("db_latency", "Latensi Query SQLite", dbQuerySpeedMs, "DATABASE", isAlert = (dbQuerySpeedMs != "NOT TESTED" && !dbQuerySpeedMs.contains("ms"))),
            TelemetryMetricItem("offline_cnt", "Sisa Antrean Sinkronisasi", "$offlineQueueCount item pending", "NETWORK_QUEUE", isAlert = (offlineQueueCount > 0)),
            TelemetryMetricItem("invoice_cnt", "Data Invoice Terindeks", "$localInvoicesCount invoice", "STORAGE")
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "PERFORMANCE TELEMETRY",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        color = AgedGold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = AgedGold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SecondaryShadowBlackTeal
                )
            )
        },
        containerColor = BackgroundShadowBlack
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // Summary Dashboard Hero
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CardDarkCard)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Analytics,
                                contentDescription = "Hero Icon",
                                tint = AgedGold,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "CORE TELEMETRY MONITOR",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = AgedGold,
                                letterSpacing = 1.sp
                            )
                        }

                        Text(
                            text = "Orkestrasi diagnostik hardware, latensi database mikro, dan integritas background worker untuk performa rilis ERP YANSPROJECT.ID.",
                            fontSize = 12.sp,
                            color = TextIsiSoftGray,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Button(
                            onClick = { runDbBenchmark() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryDarkTeal),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (isTestingQuerySpeed) {
                                CircularProgressIndicator(
                                    color = HighlightSoftCyan,
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Melakukan Uji...", color = HighlightSoftCyan, fontSize = 12.sp)
                            } else {
                                Icon(Icons.Outlined.Speed, contentDescription = "Run Benchmark", tint = HighlightSoftCyan)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("UJI KECEPATAN QUERY DATABASE", color = TextWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Realtime metrics list
            items(systemStatusList, key = { it.id }) { metric ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(CardDarkCard)
                        .border(1.dp, if (metric.isAlert) StatusDangerRed.copy(alpha = 0.5f) else DividerDarkCyanGray, RoundedCornerShape(8.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        val icon = when (metric.category) {
                            "MEMORY" -> Icons.Outlined.Memory
                            "DATABASE" -> Icons.Outlined.Dns
                            "NETWORK_QUEUE" -> Icons.Outlined.Cached
                            else -> Icons.Outlined.BugReport
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = metric.category,
                            tint = if (metric.isAlert) StatusDangerRed else HighlightSoftCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = metric.title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )
                            Text(
                                text = metric.category,
                                fontSize = 10.sp,
                                color = TextNonActive,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (metric.isAlert) StatusDangerRed.copy(alpha = 0.15f) else HighlightSoftCyan.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = metric.value,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (metric.isAlert) StatusDangerRed else HighlightSoftCyan
                        )
                    }
                }
            }

            // Diagnostic Footnotes
            item {
                Text(
                    text = "Layar telemetry diperbarui secara otomatis secara real-time demi efisiensi operasional. Seluruh kalkulasi performa berbasis benchmarking lokal perangkat keras.",
                    fontSize = 10.sp,
                    color = TextNonActive,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                )
            }
        }
    }
}
