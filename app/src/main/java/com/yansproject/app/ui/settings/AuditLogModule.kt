package com.yansproject.app.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AutoDelete
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.yansproject.app.data.AuditLog
import com.yansproject.app.data.AuditLogRepository
import com.yansproject.app.data.AuditScreenState
import com.yansproject.app.data.FirebaseSyncManager
import com.yansproject.app.data.RoleAccessManager
import com.yansproject.app.data.UserRole
import com.yansproject.app.ui.MainViewModel
import com.yansproject.app.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditLogModuleScreen(
    navController: NavController,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val auditRepo = remember { AuditLogRepository.getInstance(context) }
    val auditUiState by auditRepo.uiState.collectAsState()
    
    // Guard audit logs screen with biometric verification
    var isAuthorized by remember { mutableStateOf(false) }
    var showRetentionDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        com.yansproject.app.ui.security.BiometricAuthManager.authenticateWithBiometrics(
            context = context,
            onSuccess = {
                isAuthorized = true
                viewModel.addAuditLog("Akses Audit Log Sistem", "Super Admin memverifikasi biometrik untuk mengakses Audit Log Aktivitas Sistem.")
                auditRepo.loadPagedLogs(page = 0, pageSize = 50)
            },
            onError = { errString ->
                val msg = if (errString.isNotBlank()) "Verifikasi Biometrik Gagal: $errString" else "Verifikasi Biometrik Dibatalkan."
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                navController.popBackStack()
            }
        )
    }

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

    if (showRetentionDialog) {
        AlertDialog(
            onDismissRequest = { showRetentionDialog = false },
            title = {
                Text(
                    text = "Eksekusi Kebijakan Retensi?",
                    color = AgedGold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = "Sistem akan mengarsipkan catatan lebih dari 30 hari dan membersihkan arsip kedaluwarsa lebih dari 90 hari sesuai regulasi ISO/SOC2. Aktivitas ini akan tercatat dalam log audit.",
                    color = Color.White,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val secCtx = RoleAccessManager.SecurityContext(
                                role = UserRole.OWNER,
                                email = FirebaseSyncManager.currentUser.value?.email ?: "admin@yansproject.id"
                            )
                            val result = auditRepo.executeRetentionLifecycle(secCtx)
                            if (result.isSuccess) {
                                Toast.makeText(context, "Kebijakan retensi audit berhasil dieksekusi.", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Gagal mengeksekusi retensi: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                            }
                            showRetentionDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberEmerald)
                ) {
                    Text("Jalankan Retensi", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showRetentionDialog = false }
                ) {
                    Text("Batal", color = TextMuted)
                }
            },
            containerColor = CardGrey
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp)
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(ShadowBlack)
                .padding(paddingValues)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Kembali",
                        tint = AgedGold
                    )
                }
                Text(
                    text = "AUDIT LOG SISTEM",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = AgedGold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { 
                    showRetentionDialog = true
                }) {
                    Icon(
                        imageVector = Icons.Outlined.AutoDelete,
                        contentDescription = "Kebijakan Retensi",
                        tint = AgedGold
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (auditUiState.state) {
                    AuditScreenState.LOADING -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = AgedGold)
                        }
                    }
                    AuditScreenState.ERROR -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.WarningAmber,
                                contentDescription = null,
                                tint = AlertRed,
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = auditUiState.errorMessage ?: "Terjadi kesalahan saat memuat catatan audit.",
                                color = TextMuted,
                                fontSize = 13.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { auditRepo.loadPagedLogs(0, 50) },
                                colors = ButtonDefaults.buttonColors(containerColor = CyberEmerald)
                            ) {
                                Text("Coba Lagi", color = Color.White)
                            }
                        }
                    }
                    AuditScreenState.SUCCESS_EMPTY -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Security,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Tidak Ada Aktivitas Tercatat",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Setiap perubahan data dan transaksi akan otomatis tersimpan di sini.",
                                color = TextMuted,
                                fontSize = 12.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(top = 4.dp).padding(horizontal = 24.dp)
                            )
                        }
                    }
                    AuditScreenState.SUCCESS_DATA -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(auditUiState.logs) { log ->
                                val isDanger = log.activity.contains("Gagal", ignoreCase = true) || 
                                               log.activity.contains("Tidak Sah", ignoreCase = true) || 
                                               log.activity.contains("Danger", ignoreCase = true) ||
                                               log.activity.contains("Hapus", ignoreCase = true)

                                val timeStr = remember(log.timestamp) {
                                    val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
                                    sdf.format(Date(log.timestamp))
                                }

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = CardGrey),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp, 
                                        if (isDanger) AlertRed.copy(alpha = 0.3f) else BorderGrey.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
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
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = if (isDanger) AlertRed else AgedGold,
                                                    letterSpacing = 0.5.sp
                                                )
                                            }
                                            
                                            Text(
                                                text = timeStr,
                                                fontSize = 10.sp,
                                                color = TextMuted,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Text(
                                            text = log.details,
                                            fontSize = 12.sp,
                                            color = TextWhite,
                                            lineHeight = 16.sp
                                        )

                                        HorizontalDivider(
                                            color = BorderGrey.copy(alpha = 0.3f),
                                            thickness = 0.8.dp
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "User Pelaksana: ${log.adminName}",
                                                fontSize = 10.sp,
                                                color = TextMuted,
                                                fontWeight = FontWeight.Medium
                                            )
                                            
                                            Box(
                                                modifier = Modifier
                                                    .background(
                                                        if (isDanger) AlertRed.copy(alpha = 0.1f) else AlertGreen.copy(alpha = 0.1f),
                                                        RoundedCornerShape(4.dp)
                                                    )
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = if (isDanger) "ATTENTION" else "LOCAL SECURE",
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isDanger) AlertRed else AlertGreen
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (auditUiState.hasMore) {
                                item {
                                    Button(
                                        onClick = {
                                            auditRepo.loadPagedLogs(
                                                page = auditUiState.currentPage + 1,
                                                pageSize = auditUiState.pageSize
                                            )
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceDarkTeal)
                                    ) {
                                        Text("Muat Catatan Lebih Banyak (${auditUiState.logs.size}/${auditUiState.totalCount})", color = AgedGold)
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

