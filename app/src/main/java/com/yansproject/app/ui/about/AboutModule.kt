package com.yansproject.app.ui.about

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yansproject.app.ui.theme.*
import com.yansproject.app.ui.theme.glassCard
import com.yansproject.app.ui.theme.ambientGlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val scrollState = rememberScrollState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = BackgroundShadowBlack,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "TENTANG APLIKASI",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        color = AccentAgedGold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onBack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SecondaryShadowBlackTeal
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(BackgroundShadowBlack)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header Logo Area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(SecondaryShadowBlackTeal)
                        .border(1.dp, AccentAgedGold, RoundedCornerShape(16.dp))
                        .ambientGlow(color = AccentAgedGold, radius = 6.dp, alpha = 0.3f),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "YANSPROJECT.ID",
                        tint = AccentAgedGold,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Text(
                    text = "YANSPROJECT.ID",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = AccentAgedGold,
                    letterSpacing = 2.sp
                )

                Text(
                    text = "Eksklusif ERP & Manufaktur Manajemen Garmen",
                    fontSize = 11.sp,
                    color = TextNonActive,
                    textAlign = TextAlign.Center
                )
            }

            // Section 1: Filosofi
            Text(
                text = "FILOSOFI UTAMA",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = AccentAgedGold,
                letterSpacing = 1.sp
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard(),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Makna Sebelum Estetika",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = HighlightSoftCyan
                    )
                    
                    Text(
                        text = "Makna Sebelum Estetika. Teknologi bukan sekadar alat, tapi sarana menjaga makna, ketertiban, kesinambungan, dan kualitas setiap proses.",
                        fontSize = 13.sp,
                        color = TextIsiSoftGray,
                        textAlign = TextAlign.Justify,
                        lineHeight = 18.sp
                    )
                }
            }

            // Section 2: Kitab Digital
            Text(
                text = "KITAB DIGITAL & MANUSKRIP",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = AccentAgedGold,
                letterSpacing = 1.sp
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, DividerDarkCyanGray.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = SurfaceDarkTealSurface.copy(alpha = 0.3f))
            ) {
                Column {
                    // Item 1: YANSPROJECT.ID HISTORY
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SecondaryShadowBlackTeal),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = AccentAgedGold
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "YANSPROJECT.ID HISTORY",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Rekam jejak, sejarah, dan nilai perjuangan pendirian ekosistem garmen.",
                                fontSize = 11.sp,
                                color = TextNonActive
                            )
                        }
                    }

                    HorizontalDivider(color = DividerDarkCyanGray.copy(alpha = 0.3f))

                    // Item 2: RISALAH MADAD AULIYA
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SecondaryShadowBlackTeal),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Book,
                                contentDescription = null,
                                tint = HighlightSoftCyan
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "RISALAH MADAD AULIYA",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Panduan spiritual, etika kerja, gotong royong, dan integritas manajemen.",
                                fontSize = 11.sp,
                                color = TextNonActive
                            )
                        }
                    }
                }
            }

            // Section 3: Informasi Sistem
            Text(
                text = "INFORMASI SISTEM",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = AccentAgedGold,
                letterSpacing = 1.sp
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, DividerDarkCyanGray.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = SecondaryShadowBlackTeal.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SystemInfoRow(label = "Nama Aplikasi", value = "YANSPROJECT.ID ERP")
                    SystemInfoRow(label = "Platform", value = "Android (Native OS)")
                    SystemInfoRow(label = "Database", value = "Firebase Cloud & SQLite Room v8")
                    SystemInfoRow(label = "Versi Aplikasi", value = "1.1.0")
                    
                    HorizontalDivider(color = DividerDarkCyanGray.copy(alpha = 0.4f))
                    
                    Text(
                        text = "Copyright © 2026 YANSPROJECT.ID. All Rights Reserved.",
                        fontSize = 10.sp,
                        color = TextNonActive,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun SystemInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = TextNonActive,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            fontSize = 12.sp,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}
