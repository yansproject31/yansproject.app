package com.yansproject.app.ui.settings

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.yansproject.app.data.AuditLog
import com.yansproject.app.ui.MainViewModel
import com.yansproject.app.ui.theme.*
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
    val auditLogs by viewModel.allAuditLogs.collectAsState()
    
    // Guard audit logs screen with biometric verification
    var isAuthorized by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        com.yansproject.app.ui.security.BiometricAuthManager.authenticateWithBiometrics(
            context = context,
            onSuccess = {
                isAuthorized = true
                viewModel.addAuditLog("Akses Audit Log Sistem", "Owner memverifikasi sidik jari untuk mengakses Audit Log Aktivitas Sistem.")
            },
            onError = { errString ->
                Toast.makeText(context, "Verifikasi Sidik Jari Gagal/Dibatalkan.", Toast.LENGTH_LONG).show()
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "AUDIT LOG SISTEM",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = AgedGold,
                        letterSpacing = 1.5.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali",
                            tint = AgedGold
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { 
                        viewModel.clearAuditLogs()
                        Toast.makeText(context, "Seluruh log berhasil dibersihkan dari database lokal.", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Bersihkan Log",
                            tint = AlertRed
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ShadowBlack)
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
            } else {
                val sortedLogs = remember(auditLogs) {
                    auditLogs.sortedByDescending { it.timestamp }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(sortedLogs) { log ->
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
                }
            }
        }
    }
}
