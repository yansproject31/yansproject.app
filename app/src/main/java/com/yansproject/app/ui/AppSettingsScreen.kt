package com.yansproject.app.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.yansproject.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(
    navController: NavController,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isNotificationEnabled by remember { mutableStateOf(true) }
    var isBiometricsEnabled by remember { mutableStateOf(false) }
    var selectedLanguage by remember { mutableStateOf("Bahasa Indonesia") }

    var showLanguageDialog by remember { mutableStateOf(false) }

    androidx.activity.compose.BackHandler(enabled = true) {
        if (!navController.popBackStack()) {
            viewModel.setTab(AppTab.DASHBOARD)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize().background(shadowBlack),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0.dp)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = darkTeal.copy(alpha = 0.95f),
                shadowElevation = 8.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderGrey.copy(alpha = 0.5f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    IconButton(
                        onClick = {
                            if (!navController.popBackStack()) {
                                viewModel.setTab(AppTab.DASHBOARD)
                            }
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(darkTeal.copy(alpha = 0.4f))
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Kembali ke Dashboard",
                            tint = agedGold,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "PENGATURAN UMUM & SISTEM",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = agedGold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Konfigurasi preferensi tampilan, suara & keamanan",
                            fontSize = 10.sp,
                            color = textMuted
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "PENGATURAN UMUM",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = agedGold,
                    letterSpacing = 1.5.sp
                )

            // row card 1: Notification
            SharedPremiumCard(
                modifier = Modifier.fillMaxWidth(),
                padding = 12.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Notifications,
                            contentDescription = null,
                            tint = agedGold,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "Notifikasi Sistem",
                                fontWeight = FontWeight.Bold,
                                color = textWhite,
                                fontSize = 14.sp
                            )
                            Text(
                                text = if (isNotificationEnabled) "Aktif" else "Nonaktif",
                                color = textMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                    Switch(
                        checked = isNotificationEnabled,
                        onCheckedChange = {
                            isNotificationEnabled = it
                            Toast.makeText(
                                context,
                                "Notifikasi ${if (it) "diaktifkan" else "dimatikan"}",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = shadowBlack,
                            checkedTrackColor = cyanPulse,
                            uncheckedThumbColor = textMuted,
                            uncheckedTrackColor = darkTeal.copy(alpha = 0.3f)
                        )
                    )
                }
            }

            // row card 2: Security / Biometrics
            SharedPremiumCard(
                modifier = Modifier.fillMaxWidth(),
                padding = 12.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Shield,
                            contentDescription = null,
                            tint = agedGold,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "Keamanan Biometrik / Sidik Jari",
                                fontWeight = FontWeight.Bold,
                                color = textWhite,
                                fontSize = 14.sp
                            )
                            Text(
                                text = if (isBiometricsEnabled) "Aktif" else "Nonaktif",
                                color = textMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                    Switch(
                        checked = isBiometricsEnabled,
                        onCheckedChange = {
                            isBiometricsEnabled = it
                            Toast.makeText(
                                context,
                                "Biometrik ${if (it) "diaktifkan" else "dimatikan"}",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = shadowBlack,
                            checkedTrackColor = cyanPulse,
                            uncheckedThumbColor = textMuted,
                            uncheckedTrackColor = darkTeal.copy(alpha = 0.3f)
                        )
                    )
                }
            }

            // row card 3: Language selection
            SharedPremiumCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showLanguageDialog = true },
                padding = 12.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Language,
                            contentDescription = null,
                            tint = agedGold,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "Bahasa Aplikasi",
                                fontWeight = FontWeight.Bold,
                                color = textWhite,
                                fontSize = 14.sp
                            )
                            Text(
                                text = selectedLanguage,
                                color = textMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = textMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "PENGATURAN TAMBAHAN",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = agedGold,
                letterSpacing = 1.5.sp
            )

            // custom rows that match standard lists
            AppSettingsRowItem(
                icon = Icons.Outlined.AdminPanelSettings,
                title = "Profil Administrator",
                subtitle = "Kelola data pribadi dan ganti PIN keamanan",
                onClick = { navController.navigate("admin_profile") }
            )

            AppSettingsRowItem(
                icon = Icons.Outlined.Info,
                title = "Informasi Sistem",
                subtitle = "Lihat versi aplikasi, status, dan lisensi",
                onClick = { navController.navigate("app_info") }
            )
        }
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = {
                Text(
                    "Pilih Bahasa",
                    fontWeight = FontWeight.Bold,
                    color = agedGold
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf("Bahasa Indonesia", "English", "العربية").forEach { lang ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedLanguage = lang
                                    showLanguageDialog = false
                                    Toast.makeText(context, "Bahasa diubah ke $lang", Toast.LENGTH_SHORT).show()
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = lang,
                                color = if (selectedLanguage == lang) cyanPulse else textWhite,
                                fontWeight = if (selectedLanguage == lang) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 15.sp,
                                modifier = Modifier.weight(1f)
                            )
                            if (selectedLanguage == lang) {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = null,
                                    tint = cyanPulse,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text("Tutup", color = agedGold)
                }
            },
            containerColor = darkTeal,
            shape = RoundedCornerShape(16.dp)
        )
    }
}
}

@Composable
fun AppSettingsRowItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    SharedPremiumCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        padding = 12.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = agedGold,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        color = textWhite,
                        fontSize = 14.sp
                    )
                    Text(
                        text = subtitle,
                        color = textMuted,
                        fontSize = 11.sp
                    )
                }
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = textMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
