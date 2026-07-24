package com.yansproject.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yansproject.app.ui.theme.*
import com.yansproject.app.ui.components.LuxuryBookCover
import com.yansproject.app.ui.components.ManuscriptQuoteBlock
import com.yansproject.app.ui.components.YansPremiumButton
import com.yansproject.app.ui.theme.ambientGlow
import com.yansproject.app.ui.theme.glassCard

@Composable
fun HistoryDashboardScreen(
    onNavigateToKitab: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ShadowBlack)
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // 1. COVER UTAMA (LUXURY BOOK COVER)
        LuxuryBookCover(
            title = "YANSPROJECT.ID HISTORY",
            subtitle = "Manuskrip Digital Sejarah & Risalah Perjalanan"
        )

        // 2. INFORMASI MANUSKRIP (Jilid, Risalah, Status)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .glassCard()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Item Jilid
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "TOTAL JILID",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "4 JUZ",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00E5FF)
                    )
                }

                // Divider vertical
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .width(1.dp)
                        .background(GlassBorder)
                )

                // Item Risalah
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "TOTAL RISALAH",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "17 BAB",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00E5FF)
                    )
                }

                // Divider vertical
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .width(1.dp)
                        .background(GlassBorder)
                )

                // Item Status
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "STATUS BACA",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "100% Selesai",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00E5FF)
                    )
                }
            }
        }

        // 3. QUOTE PENGANTAR
        ManuscriptQuoteBlock(
            quote = "Tak semua perjalanan meminta untuk dikenang. Sebagian hanya berharap agar tak pernah hilang."
        )

        // 4. SECTION DRAFT MANUSKRIP (RISALAH MADAD AULIYA)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .glassCard()
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header of Draft with Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "RISALAH MADAD AULIYA",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Coming Soon",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }

                    // Badge "DRAFT ONGOING" at top right
                    Box(
                        modifier = Modifier
                            .border(
                                width = 1.dp,
                                color = Color(0xFF00E5FF),
                                shape = RoundedCornerShape(50)
                            )
                            .background(
                                color = Color(0xFF00E5FF).copy(alpha = 0.1f),
                                shape = RoundedCornerShape(50)
                            )
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "DRAFT ONGOING",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00E5FF)
                        )
                    }
                }

                HorizontalDivider(color = GlassBorder, thickness = 1.dp)

                // Glowing Custom Progress Bar
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Drafting Progress",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondary
                        )
                        Text(
                            text = "45%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00E5FF)
                        )
                    }

                    // Outer track
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, GlassBorder, RoundedCornerShape(50)),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        // Inner track (Progress)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.45f)
                                .fillMaxHeight()
                                .background(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(Color(0xFF00E5FF), AgedGoldLight)
                                    )
                                )
                        )
                    }
                }

                // Catatan Penulis
                Text(
                    text = "Catatan Penulis: Penggalian naskah kuno serta penyelarasan sanad riwayat dalam bentuk manuskrip digital premium YANSPROJECT.ID.",
                    fontSize = 12.sp,
                    fontStyle = FontStyle.Italic,
                    color = TextSecondary,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = GlassBorder.copy(alpha = 0.5f), thickness = 1.dp)
                Spacer(modifier = Modifier.height(8.dp))

                // KOTAK PREVIEW MANUSKRIP (TEASER)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkTealSurface.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFFD4AF37).copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "PREVIEW MANUSKRIP (TEASER)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD4AF37),
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Text(
                        text = "\"Di dalam gulungan waktu yang sunyi, ketika malam membentangkan tabir hitamnya, kami menuliskan lembaran ini. Bukan dengan tinta biasa, melainkan dengan air mata keheningan dan kerinduan spiritual yang mendalam. Cahaya para auliya membimbing setiap jengkal aksara yang terukir di atas kanvas digital ini...\"",
                        fontSize = 13.sp,
                        fontStyle = FontStyle.Italic,
                        fontFamily = FontFamily.Serif,
                        color = TextSecondary.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )
                }
            }
        }

        // 5. TOMBOL MULAI MEMBACA (YansPremiumButton)
        YansPremiumButton(
            text = "BUKA KITAB DIGITAL UTAMA",
            onClick = onNavigateToKitab,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}
