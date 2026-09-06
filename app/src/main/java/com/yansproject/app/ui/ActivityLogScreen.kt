package com.yansproject.app.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.yansproject.app.data.AuditLog
import com.yansproject.app.ui.components.*
import com.yansproject.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityLogScreen(
    navController: NavController,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var isAuthorized by remember { mutableStateOf(false) }

    var showClearConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        com.yansproject.app.ui.security.BiometricAuthManager.authenticateWithBiometrics(
            context = context,
            onSuccess = {
                isAuthorized = true
                viewModel.addAuditLog("Akses Audit Log", "Administrator sukses verifikasi sidik jari untuk mengakses Audit Log Aktivitas Sistem.")
            },
            onError = { errString ->
                Toast.makeText(context, "Verifikasi Sidik Jari Gagal/Dibatalkan.", Toast.LENGTH_LONG).show()
                navController.popBackStack()
            }
        )
    }

    val auditLogs by viewModel.allAuditLogs.collectAsState()

    if (!isAuthorized) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ShadowBlack),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = AgedGold)
        }
        return
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = {
                Text(
                    text = "HAPUS SELURUH AUDIT LOG?",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = AgedGold
                )
            },
            text = {
                Text(
                    text = "Tindakan ini akan menghapus semua riwayat catatan keamanan dari database lokal secara permanen. Apakah Anda yakin?",
                    fontSize = 13.sp,
                    color = TextWhite
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmDialog = false
                        viewModel.clearAuditLogs()
                        Toast.makeText(context, "Seluruh Audit Log telah dibersihkan", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("HAPUS PERMANEN", color = AlertRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("BATALAN", color = TextMuted)
                }
            },
            containerColor = SurfaceDarkTeal,
            shape = RoundedCornerShape(16.dp)
        )
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
                title = "SECURITY AUDIT LOG",
                subtitle = "Catatan Keamanan & Aktivitas Sistem",
                navigationIcon = {
                    YansBackButton(onClick = {
                        if (!navController.popBackStack()) {
                            viewModel.setTab(AppTab.DASHBOARD)
                        }
                    })
                },
                actions = {
                    Surface(
                        onClick = { showClearConfirmDialog = true },
                        modifier = Modifier.size(40.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceDarkTeal.copy(alpha = 0.85f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AlertRed.copy(alpha = 0.5f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = "Clear Logs",
                                tint = AlertRed,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(ShadowBlack)
                .padding(paddingValues)
        ) {
            if (auditLogs.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = AgedGold)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Mengompilasi Catatan Keamanan...",
                        color = TextMuted,
                        fontSize = 13.sp
                    )
                }
            } else {
                val sortedLogs = remember(auditLogs) {
                    auditLogs.sortedByDescending { it.timestamp }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        SharedPremiumCard(
                            modifier = Modifier.fillMaxWidth(),
                            borderGlowColor = CyanPulse.copy(alpha = 0.2f),
                            padding = 16.dp
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Security,
                                    contentDescription = null,
                                    tint = AgedGold,
                                    modifier = Modifier.size(40.dp)
                                )
                                Column {
                                    Text(
                                        text = "AUDIT TRANSPARANSI OPERASIONAL",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        color = AgedGold,
                                        letterSpacing = 1.sp
                                    )
                                    Text(
                                        text = "Integritas Sistem Terjamin",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = TextWhite
                                    )
                                    Text(
                                        text = "Mencatat setiap sesi sensitif, mutasi data, dan upaya akses dalam database terenkripsi.",
                                        fontSize = 11.sp,
                                        color = TextMuted,
                                        lineHeight = 15.sp,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            text = "RIWAYAT SINKRONISASI AKTIVITAS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AgedGold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }

                    // Display all logs, latest first, beautifully wrapped in SharedPremiumCard
                    items(sortedLogs) { log ->
                        val isDanger = log.activity.contains("Gagal", ignoreCase = true) || 
                                       log.activity.contains("Tidak Sah", ignoreCase = true) || 
                                       log.activity.contains("Danger", ignoreCase = true)

                        val timeStr = remember(log.timestamp) {
                            val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault())
                            sdf.format(Date(log.timestamp))
                        }

                        SharedPremiumCard(
                            modifier = Modifier.fillMaxWidth(),
                            borderGlowColor = if (isDanger) AlertRed.copy(alpha = 0.25f) else CyanPulse.copy(alpha = 0.1f),
                            padding = 14.dp
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isDanger) Icons.Outlined.WarningAmber else Icons.Outlined.VerifiedUser,
                                            contentDescription = null,
                                            tint = if (isDanger) AlertRed else AlertGreen,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = log.activity.uppercase(),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Black,
                                            color = if (isDanger) AlertRed else AgedGold,
                                            letterSpacing = 0.5.sp
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .background(
                                                if (isDanger) AlertRed.copy(alpha = 0.1f) else AlertGreen.copy(alpha = 0.1f),
                                                RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (isDanger) "SECURE FLAG" else "SUCCESS",
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isDanger) AlertRed else AlertGreen
                                        )
                                    }
                                }

                                Text(
                                    text = log.details,
                                    fontSize = 12.sp,
                                    color = TextWhite,
                                    lineHeight = 16.sp
                                )

                                Divider(color = BorderGrey.copy(alpha = 0.3f), thickness = 1.dp)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Operator: ${log.adminName}",
                                        fontSize = 10.sp,
                                        color = TextMuted,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = timeStr,
                                        fontSize = 10.sp,
                                        color = TextMuted,
                                        fontWeight = FontWeight.Bold
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
