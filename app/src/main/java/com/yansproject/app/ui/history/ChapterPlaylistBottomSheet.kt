package com.yansproject.app.ui.history

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yansproject.app.ui.theme.*
import com.yansproject.app.ui.theme.ambientGlow
import com.yansproject.app.ui.theme.glassCard

// Data class representing a chapter in the manuscript
data class LuxuryChapterItem(
    val id: String,
    val juzTitle: String,
    val title: String,
    val subtitle: String,
    val isActive: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterPlaylistBottomSheet(
    onDismiss: () -> Unit,
    onChapterSelected: (String) -> Unit,
    currentChapterId: String,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    
    // Query local preferences to see which chapters have been completed in real time
    val prefs = remember(context) { context.getSharedPreferences("kitab_prefs", Context.MODE_PRIVATE) }
    val completedSet = remember(prefs) { 
        prefs.getStringSet("completed", emptySet()) ?: emptySet()
    }

    // Static/Dummy list of chapters representing the manuscript layout
    val chapters = remember {
        listOf(
            LuxuryChapterItem(
                id = "bab_01_sunyi",
                juzTitle = "JUZ I: PROLOG PERJALANAN",
                title = "BAB I - SUNYI",
                subtitle = "Prolog Perjalanan Sunyi",
                isActive = true
            ),
            LuxuryChapterItem(
                id = "bab_02_hujan",
                juzTitle = "JUZ I: PROLOG PERJALANAN",
                title = "BAB II - HUJAN",
                subtitle = "Kisah Sang Pembawa Kabar",
                isActive = false
            ),
            LuxuryChapterItem(
                id = "bab_03_perjalanan",
                juzTitle = "JUZ I: PROLOG PERJALANAN",
                title = "BAB III - PERJALANAN",
                subtitle = "Titik Balik & Kebangkitan",
                isActive = false
            )
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkTealSurface, // DarkTealSurface (#0A1C18)
        scrimColor = Color.Black.copy(alpha = 0.7f),
        dragHandle = {
            // Drag handle: small thick line in Aged Gold (#D4AF37)
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFFD4AF37))
            )
        },
        modifier = modifier.glassCard() // Terapkan glassCard pada container ModalBottomSheet
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
        ) {
            // HEADER BOTTOM SHEET
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "DAFTAR ISI MANUSKRIP",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD4AF37) // Aged Gold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "YANSPROJECT.ID HISTORY - Playlist Membaca",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Tutup",
                        tint = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = GlassBorder, thickness = 1.dp)

            // ITEM DAFTAR ISI (LazyColumn & Grouping)
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // PENGELOMPOKAN SECTION (HEADER JUZ)
                item {
                    Text(
                        text = "JUZ I: PROLOG PERJALANAN",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD4AF37), // Aged Gold
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }

                // Loop through chapters
                items(chapters) { bab ->
                    val isCompleted = completedSet.contains(bab.id)
                    val isCurrentActive = currentChapterId == bab.id

                    // Setiap baris BAB dibungkus Card dengan warna DarkTealSurface transparan
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (isCurrentActive) {
                                    // Jika Bab aktif, border card menyala warna Aged Gold atau Neon Cyan
                                    Modifier
                                        .border(1.5.dp, Color(0xFF00E5FF), RoundedCornerShape(12.dp))
                                        .ambientGlow(color = Color(0xFF00E5FF), radius = 8.dp, alpha = 0.3f)
                                } else {
                                    Modifier.border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                                }
                            )
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onChapterSelected(bab.id)
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = DarkTealSurface.copy(alpha = 0.6f) // Transparan elegan
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // SISI KIRI (Icon Aktif/Pasif)
                            Icon(
                                imageVector = Icons.Default.MenuBook,
                                contentDescription = null,
                                tint = if (isCurrentActive) Color(0xFF00E5FF) else TextSecondary, // Neon Cyan (#00E5FF) or TextSecondary
                                modifier = Modifier
                                    .size(24.dp)
                                    .then(
                                        if (isCurrentActive) {
                                            Modifier.ambientGlow(color = Color(0xFF00E5FF), radius = 6.dp, alpha = 0.4f)
                                        } else Modifier
                                    )
                            )

                            Spacer(modifier = Modifier.width(16.dp))

                            // SISI TENGAH (Teks Judul)
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = bab.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = bab.subtitle,
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            // SISI KANAN (STATUS BACA / COMPLETED)
                            if (isCompleted) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Selesai Membaca",
                                    tint = Color(0xFFD4AF37), // PrimaryGold (#D4AF37)
                                    modifier = Modifier
                                        .size(22.dp)
                                        .ambientGlow(color = Color(0xFFD4AF37), radius = 6.dp, alpha = 0.4f)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.Circle,
                                    contentDescription = "Belum Selesai",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
