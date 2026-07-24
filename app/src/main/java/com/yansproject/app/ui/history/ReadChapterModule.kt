package com.yansproject.app.ui.history

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yansproject.app.ui.theme.*
import com.yansproject.app.ui.components.ManuscriptDropCap
import com.yansproject.app.ui.components.ManuscriptParagraph
import com.yansproject.app.ui.components.ManuscriptQuoteCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadChapterScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToDaftarIsi: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("kitab_prefs", Context.MODE_PRIVATE) }
    
    // Simulasikan ID bab yang sedang dibaca untuk integrasi progress baca
    val currentBabId = "bab_01_sunyi"

    // PREMIUM STATES
    var currentFontSize by remember { mutableStateOf(16.sp) }
    var isBookmarked by remember { mutableStateOf(prefs.getBoolean("${currentBabId}_bookmarked", false)) }
    var showPlaylistSheet by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DarkTealBase, // Background kanvas WAJIB warna DarkTealBase pekat (#040F0D)
        topBar = {
            TopAppBar(
                title = {
                    Column(
                        horizontalAlignment = Alignment.Start,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "JUZ I: PROLOG PERJALANAN",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AgedGoldLight,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "BAB I - SUNYI",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    // Tombol A-
                    IconButton(
                        onClick = {
                            if (currentFontSize > 12.sp) {
                                currentFontSize = (currentFontSize.value - 2f).sp
                            } else {
                                Toast.makeText(context, "Ukuran teks minimum tercapai", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text(
                            text = "A-",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    // Tombol A+
                    IconButton(
                        onClick = {
                            if (currentFontSize < 32.sp) {
                                currentFontSize = (currentFontSize.value + 2f).sp
                            } else {
                                Toast.makeText(context, "Ukuran teks maksimum tercapai", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text(
                            text = "A+",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    // Tombol Search
                    IconButton(onClick = { showSearchDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Cari Kata",
                            tint = Color.White
                        )
                    }

                    // Tombol Bookmark
                    IconButton(
                        onClick = {
                            isBookmarked = !isBookmarked
                            prefs.edit().putBoolean("${currentBabId}_bookmarked", isBookmarked).apply()
                            val msg = if (isBookmarked) "Halaman ditambahkan ke Bookmark!" else "Bookmark dihapus!"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Simpan Halaman",
                            tint = if (isBookmarked) Color(0xFFD4AF37) else Color.White
                        )
                    }

                    // Tombol TOC / Playlist
                    IconButton(onClick = { showPlaylistSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.FormatListBulleted,
                            contentDescription = "Daftar Isi",
                            tint = Color(0xFFD4AF37) // Warna emas
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkTealBase,
                    titleContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        // Bottom Sheet Playlist
        if (showPlaylistSheet) {
            ChapterPlaylistBottomSheet(
                onDismiss = { showPlaylistSheet = false },
                onChapterSelected = { selectedBabId ->
                    showPlaylistSheet = false
                    Toast.makeText(context, "Membuka: ${selectedBabId.uppercase().replace("_", " ")}", Toast.LENGTH_SHORT).show()
                },
                currentChapterId = currentBabId
            )
        }

        // Search Dialog
        if (showSearchDialog) {
            AlertDialog(
                onDismissRequest = { showSearchDialog = false },
                title = { Text("Cari Kata di Manuskrip", color = Color(0xFFD4AF37), fontWeight = FontWeight.Bold) },
                text = {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Masukkan kata kunci", color = TextSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFD4AF37),
                            unfocusedBorderColor = GlassBorder,
                            focusedLabelColor = Color(0xFFD4AF37)
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showSearchDialog = false
                            if (searchQuery.isNotEmpty()) {
                                Toast.makeText(context, "Kata '${searchQuery}' ditemukan di halaman ini!", Toast.LENGTH_LONG).show()
                            }
                        }
                    ) {
                        Text("CARI", color = Color(0xFFD4AF37), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSearchDialog = false }) {
                        Text("BATAL", color = TextSecondary)
                    }
                },
                containerColor = DarkTealSurface
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 32.dp) // PADDING LEGA LAYAKNYA BUKU CETAK PREMIUM
        ) {
            item {
                // 1. Drop Cap (Serif drop cap letter, rest is currentFontSize dynamic)
                ManuscriptDropCap(
                    letter = "A",
                    text = "da perjalanan yang tidak pernah diumumkan. Tidak ramai, tidak selalu dimengerti, dan tidak lahir dari ambisi untuk terlihat. Namun diam-diam, perjalanan itu mengubah manusia.",
                    fontSize = currentFontSize
                )
            }

            item {
                // 2. Paragraph with dynamic currentFontSize
                ManuscriptParagraph(
                    text = "AJIBQOBUL lahir bukan untuk menjadi yang paling besar, melainkan untuk menemani mereka yang pernah merasa hilang di tengah dunia yang terlalu bising. Sebelum semuanya memiliki bentuk, yang ada hanyalah malam, hujan, perjalanan, ruang sepi, dan seseorang yang terlalu lama mencoba memahami hidup lewat rasa.",
                    fontSize = currentFontSize
                )
            }

            item {
                // 3. Quote Card (Luxury stylized Quote Card container)
                ManuscriptQuoteCard(
                    quote = "Yang paling kuat tidak selalu paling terlihat."
                )
            }

            item {
                // 4. Paragraph with dynamic currentFontSize
                ManuscriptParagraph(
                    text = "Kalimat itu bukan slogan, melainkan cara melihat kehidupan. Karena tidak semua yang hidup harus berisik, dan tidak semua yang berisik benar-benar hidup. Perjalanan itu terus berjalan dalam sunyi. Di tengah malam, di sela hujan, perlahan muncul ketertarikan terhadap simbol, typography, spiritualitas, ritual, pesantren, dan dunia visual yang memiliki ruh.",
                    fontSize = currentFontSize
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = GlassBorder, thickness = 1.dp)
                Spacer(modifier = Modifier.height(24.dp))

                // NAVIGATION FOOTER (Sebelumnya & Berikutnya dengan Emas dan Icon)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { Toast.makeText(context, "Sudah berada di halaman pertama", Toast.LENGTH_SHORT).show() }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                tint = Color(0xFFD4AF37),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Sebelumnya",
                                color = Color(0xFFD4AF37),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    TextButton(
                        onClick = { Toast.makeText(context, "Membuka BAB II - HUJAN...", Toast.LENGTH_SHORT).show() }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Berikutnya",
                                color = Color(0xFFD4AF37),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = Color(0xFFD4AF37),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // TOMBOL SELESAI MEMBACA
                OutlinedButton(
                    onClick = {
                        val completed = prefs.getStringSet("completed", emptySet()) ?: emptySet()
                        val updated = completed.toMutableSet()
                        updated.add(currentBabId)
                        
                        prefs.edit()
                            .putStringSet("completed", updated)
                            .putString("last_opened_title", "BAB I - SUNYI")
                            .putInt("last_opened_juz", 0)
                            .putInt("last_opened_bab", 0)
                            .apply()
                            
                        Toast.makeText(context, "Membaca bab selesai! Progress diperbarui.", Toast.LENGTH_SHORT).show()
                        onBack()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    border = BorderStroke(1.5.dp, AgedGoldLight), // Border stroke tebal 1.5.dp warna AgedGoldLight
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = AgedGoldLight
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFFD4AF37), // Checklist circle emas
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Tandai Selesai Membaca",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFFD4AF37) // Teks emas
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
