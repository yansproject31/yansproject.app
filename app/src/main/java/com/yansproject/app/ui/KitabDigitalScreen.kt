package com.yansproject.app.ui

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yansproject.app.ui.theme.*
import com.yansproject.app.ui.components.ManuscriptDropCap
import com.yansproject.app.ui.components.ManuscriptQuoteBlock
import org.json.JSONObject

private val shadowBlack = ShadowBlack
private val darkTeal = DarkTeal
private val agedGold = AgedGold
private val cyanPulse = CyanPulse
private val textWhite = TextWhite
private val textMuted = TextMuted

// ==========================================
// LUXURY READING THEME STRUCTURE
// ==========================================
data class ReadingTheme(
    val id: String,
    val name: String,
    val background: Color,
    val text: Color,
    val textSecondary: Color,
    val accent: Color,
    val cardBackground: Color,
    val serif: Boolean = true
)

val readingThemes = listOf(
    ReadingTheme("museum_dark", "Museum Dark", Color(0xFF0A0A0A), Color(0xFFE2E8F0), Color(0xFFA0AEC0), Color(0xFFC6A15B), Color(0xFF112B2C)),
    ReadingTheme("ivory_paper", "Ivory Paper", Color(0xFFFDFBF7), Color(0xFF1C1917), Color(0xFF78716C), Color(0xFFB45309), Color(0xFFF2ECE1), serif = true),
    ReadingTheme("warm_sepia", "Warm Sepia", Color(0xFFF4ECD8), Color(0xFF2E2214), Color(0xFF7C2D12), Color(0xFF9A3412), Color(0xFFE3D8C1), serif = true),
    ReadingTheme("classic_book", "Classic Book", Color(0xFFFFFFFF), Color(0xFF0A0F0D), Color(0xFF4B5563), Color(0xFF0F3D3E), Color(0xFFF3F4F6), serif = true),
    ReadingTheme("midnight_library", "Midnight Library", Color(0xFF040F10), Color(0xFFE2F9F5), Color(0xFF7FB8B2), Color(0xFF4FD1C5), Color(0xFF0B2123)),
    ReadingTheme("zen_reading", "Zen Reading", Color(0xFF0A0F0D), Color(0xFFC9D1D9), Color(0xFF8B949E), Color(0xFF319795), Color(0xFF121A16), serif = false),
    ReadingTheme("archive_mode", "Archive Mode", Color(0xFF111C18), Color(0xFFD1EADF), Color(0xFF8BAA9C), Color(0xFFC6A15B), Color(0xFF182923))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KitabDigitalScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val books = rememberKitabData(context)

    if (books.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(shadowBlack),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = agedGold)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Memuat Kitab Digital...", color = textMuted, fontSize = 14.sp)
            }
        }
        return
    }

    var selectedBookId by remember { mutableStateOf("kitab_01") }
    val currentBook = remember(selectedBookId, books) {
        books.find { it.id == selectedBookId } ?: books.first()
    }

    val prefs = remember(context) { context.getSharedPreferences("kitab_prefs", Context.MODE_PRIVATE) }
    var bookmarkedBabs by remember { mutableStateOf(prefs.getStringSet("bookmarks", emptySet()) ?: emptySet()) }
    var completedBabs by remember { mutableStateOf(prefs.getStringSet("completed", emptySet()) ?: emptySet()) }
    var lastOpenedBabTitle by remember { mutableStateOf(prefs.getString("last_opened_title_new", "Belum dibaca") ?: "Belum dibaca") }
    var lastOpenedJuzIndex by remember { mutableStateOf(prefs.getInt("last_opened_juz_new", -2)) }
    var lastOpenedBabIndex by remember { mutableStateOf(prefs.getInt("last_opened_bab_new", -2)) }

    // Reading Themes & Settings states
    var activeThemeId by remember { mutableStateOf(prefs.getString("active_theme_id", "museum_dark") ?: "museum_dark") }
    val activeTheme = remember(activeThemeId) { readingThemes.find { it.id == activeThemeId } ?: readingThemes.first() }
    var readerTextSize by remember { mutableStateOf(prefs.getFloat("text_size_new", 16f)) }
    var readerFontStyle by remember { mutableStateOf(prefs.getString("font_style_key", "serif") ?: "serif") }
    var isFullScreenMode by remember { mutableStateOf(false) }

    val activeFontFamily = remember(readerFontStyle, activeTheme) {
        when (readerFontStyle) {
            "sans" -> FontFamily.SansSerif
            "monospace" -> FontFamily.Monospace
            "serif" -> FontFamily.Serif
            else -> if (activeTheme.serif) FontFamily.Serif else FontFamily.SansSerif
        }
    }

    // --- ANNOTATIONS ENGINE STATES ---
    var paragraphNotes by remember {
        mutableStateOf(
            try {
                val jsonStr = prefs.getString("paragraph_notes_db", "{}") ?: "{}"
                val jsonObj = JSONObject(jsonStr)
                val map = mutableMapOf<String, String>()
                jsonObj.keys().forEach { key ->
                    map[key] = jsonObj.getString(key)
                }
                map
            } catch (e: Exception) {
                emptyMap<String, String>()
            }
        )
    }

    var paragraphHighlights by remember {
        mutableStateOf(
            prefs.getStringSet("paragraph_highlights_set", emptySet()) ?: emptySet()
        )
    }

    var paragraphFavorites by remember {
        mutableStateOf(
            prefs.getStringSet("paragraph_favorites_set", emptySet()) ?: emptySet()
        )
    }

    // Interactive Paragraph Selection State
    var selectedParagraphKeyForAction by remember { mutableStateOf<String?>(null) }
    var selectedParagraphTextForAction by remember { mutableStateOf<String>("") }
    var selectedParagraphChapterName by remember { mutableStateOf<String>("") }
    var selectedParagraphIndexForAction by remember { mutableStateOf(-1) }

    // Secondary UI Overlays state
    var showTocOverlay by remember { mutableStateOf(false) }
    var showSearchOverlay by remember { mutableStateOf(false) }
    var showThemeChooser by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedQuoteToShare by remember { mutableStateOf<String?>(null) }
    var selectedQuoteSource by remember { mutableStateOf<String>("") }

    // Detail page history tab state: 0 = Info, 1 = Bookmarks, 2 = Catatan, 3 = Favorit
    var activeDetailTab by remember { mutableStateOf(0) }

    // --- SAVE FUNCTIONS ---
    fun saveTheme(themeId: String) {
        activeThemeId = themeId
        prefs.edit().putString("active_theme_id", themeId).apply()
    }

    fun saveTextSize(size: Float) {
        readerTextSize = size
        prefs.edit().putFloat("text_size_new", size).apply()
    }

    fun saveFontStyle(style: String) {
        readerFontStyle = style
        prefs.edit().putString("font_style_key", style).apply()
    }

    fun saveNote(key: String, noteText: String) {
        val updated = paragraphNotes.toMutableMap()
        if (noteText.isBlank()) {
            updated.remove(key)
        } else {
            updated[key] = noteText
        }
        paragraphNotes = updated
        try {
            val jsonObj = JSONObject()
            updated.forEach { (k, v) ->
                jsonObj.put(k, v)
            }
            prefs.edit().putString("paragraph_notes_db", jsonObj.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun saveHighlight(key: String, isHighlighted: Boolean) {
        val updated = paragraphHighlights.toMutableSet()
        if (isHighlighted) updated.add(key) else updated.remove(key)
        paragraphHighlights = updated
        prefs.edit().putStringSet("paragraph_highlights_set", updated).apply()
    }

    fun saveFavorite(key: String, isFavorite: Boolean) {
        val updated = paragraphFavorites.toMutableSet()
        if (isFavorite) updated.add(key) else updated.remove(key)
        paragraphFavorites = updated
        prefs.edit().putStringSet("paragraph_favorites_set", updated).apply()
    }

    // --- MANUSCRIPT PERSISTENCE AND SINKRONISASI CORES ---
    // -2: Cover, -1: Muqaddimah, 999: Penutup, 0 to N: JUZ indices
    var currentJuzView by remember { mutableStateOf(-2) }
    var currentBabView by remember { mutableStateOf(0) }

    val searchResults = remember(searchQuery, books) {
        val query = searchQuery.trim()
        if (query.length < 2) emptyList<KitabSearchResult>()
        else {
            val results = mutableListOf<KitabSearchResult>()
            books.forEach { book ->
                if (book.muqaddimah.contains(query, ignoreCase = true)) {
                    val snippet = book.muqaddimah.split("\n")
                        .firstOrNull { it.contains(query, ignoreCase = true) }?.trim() ?: ""
                    results.add(
                        KitabSearchResult(
                            bookId = book.id,
                            bookTitle = book.title,
                            juzIndex = -1,
                            babIndex = 0,
                            sectionTitle = "Muqaddimah",
                            matchText = snippet
                        )
                    )
                }
                
                if (book.penutup.contains(query, ignoreCase = true)) {
                    val snippet = book.penutup.split("\n")
                        .firstOrNull { it.contains(query, ignoreCase = true) }?.trim() ?: ""
                    results.add(
                        KitabSearchResult(
                            bookId = book.id,
                            bookTitle = book.title,
                            juzIndex = 999,
                            babIndex = 0,
                            sectionTitle = "Penutup",
                            matchText = snippet
                        )
                    )
                }
                
                book.juzList.forEachIndexed { juzIdx, juz ->
                    juz.babList.forEachIndexed { babIdx, bab ->
                        if (bab.title.contains(query, ignoreCase = true)) {
                            results.add(
                                KitabSearchResult(
                                    bookId = book.id,
                                    bookTitle = book.title,
                                    juzIndex = juzIdx,
                                    babIndex = babIdx,
                                    sectionTitle = bab.title,
                                    matchText = bab.content.firstOrNull() ?: ""
                                )
                            )
                        } else {
                            bab.content.forEach { paragraph ->
                                if (paragraph.contains(query, ignoreCase = true)) {
                                    results.add(
                                        KitabSearchResult(
                                            bookId = book.id,
                                            bookTitle = book.title,
                                            juzIndex = juzIdx,
                                            babIndex = babIdx,
                                            sectionTitle = bab.title,
                                            matchText = paragraph.trim()
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
            results
        }
    }

    val totalBabCount = remember(currentBook) { currentBook.juzList.sumOf { it.babList.size } }
    val completedCount = remember(completedBabs, currentBook) {
        var count = 0
        currentBook.juzList.forEach { juz ->
            juz.babList.forEach { bab ->
                if (completedBabs.contains(bab.id)) count++
            }
        }
        count
    }

    val progressPercent = remember(completedCount, totalBabCount) {
        if (totalBabCount > 0) (completedCount.toFloat() / totalBabCount.toFloat() * 100).toInt() else 0
    }

    fun saveBookmark(babId: String, isBookmarked: Boolean) {
        val updated = bookmarkedBabs.toMutableSet()
        if (isBookmarked) updated.add(babId) else updated.remove(babId)
        bookmarkedBabs = updated
        prefs.edit().putStringSet("bookmarks", updated).apply()
    }

    fun saveCompleted(babId: String, isCompleted: Boolean) {
        val updated = completedBabs.toMutableSet()
        if (isCompleted) updated.add(babId) else updated.remove(babId)
        completedBabs = updated
        prefs.edit().putStringSet("completed", updated).apply()
    }

    fun saveLastOpened(juzIdx: Int, babIdx: Int, title: String) {
        lastOpenedJuzIndex = juzIdx
        lastOpenedBabIndex = babIdx
        lastOpenedBabTitle = title
        prefs.edit()
            .putInt("last_opened_juz_new", juzIdx)
            .putInt("last_opened_bab_new", babIdx)
            .putString("last_opened_title_new", title)
            .apply()
    }

    fun openReader(juzIdx: Int, babIdx: Int) {
        currentJuzView = juzIdx
        currentBabView = babIdx
        val title = when (juzIdx) {
            -1 -> "Muqaddimah"
            999 -> "Penutup"
            else -> currentBook.juzList.getOrNull(juzIdx)?.babList?.getOrNull(babIdx)?.title ?: "Kitab Reader"
        }
        saveLastOpened(juzIdx, babIdx, title)
        showTocOverlay = false
    }

    fun navigateNext() {
        if (currentJuzView == -1) {
            if (currentBook.juzList.isNotEmpty() && currentBook.juzList[0].babList.isNotEmpty()) {
                openReader(0, 0)
            } else {
                openReader(999, 0)
            }
        } else if (currentJuzView == 999) {
            currentJuzView = -2
        } else {
            val curJuz = currentBook.juzList.getOrNull(currentJuzView)
            if (curJuz != null) {
                if (currentBabView < curJuz.babList.size - 1) {
                    openReader(currentJuzView, currentBabView + 1)
                } else {
                    if (currentJuzView < currentBook.juzList.size - 1) {
                        openReader(currentJuzView + 1, 0)
                    } else {
                        openReader(999, 0)
                    }
                }
            }
        }
    }

    fun navigatePrev() {
        if (currentJuzView == 999) {
            val lastJuzIdx = currentBook.juzList.size - 1
            if (lastJuzIdx >= 0) {
                val lastJuz = currentBook.juzList[lastJuzIdx]
                openReader(lastJuzIdx, lastJuz.babList.size - 1)
            } else {
                openReader(-1, 0)
            }
        } else if (currentJuzView == -1) {
            currentJuzView = -2
        } else {
            if (currentBabView > 0) {
                openReader(currentJuzView, currentBabView - 1)
            } else {
                if (currentJuzView > 0) {
                    val prevJuz = currentBook.juzList.getOrNull(currentJuzView - 1)
                    if (prevJuz != null) {
                        openReader(currentJuzView - 1, prevJuz.babList.size - 1)
                    }
                } else {
                    openReader(-1, 0)
                }
            }
        }
    }

    BackHandler(enabled = true) {
        when {
            selectedQuoteToShare != null -> {
                selectedQuoteToShare = null
            }
            selectedParagraphKeyForAction != null -> {
                selectedParagraphKeyForAction = null
            }
            showSearchOverlay -> {
                showSearchOverlay = false
            }
            showTocOverlay -> {
                showTocOverlay = false
            }
            showThemeChooser -> {
                showThemeChooser = false
            }
            currentJuzView != -2 && currentJuzView != -3 -> {
                currentJuzView = -3
            }
            currentJuzView == -3 -> {
                currentJuzView = -2
            }
            currentJuzView == -2 -> {
                onBack()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(shadowBlack)
    ) {
        Crossfade(
            targetState = currentJuzView,
            animationSpec = tween(500),
            label = "ReaderCrossfade"
        ) { page ->
            if (page == -2) {
                // ==========================================
                // MUSEUM ARCHIVE: SELECTION SHELF (MAIN VIEW)
                // ==========================================
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.testTag("back_button_kitab_library")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = agedGold)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "YANSPROJECT.ID",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = agedGold,
                                    letterSpacing = 1.5.sp
                                )
                                Text(
                                    text = "DIGITAL MANUSCRIPT ARCHIVE",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    letterSpacing = 1.sp,
                                    fontFamily = FontFamily.Serif
                                )
                            }
                            IconButton(onClick = { showSearchOverlay = true }) {
                                Icon(Icons.Outlined.Search, contentDescription = "Cari", tint = agedGold)
                            }
                        }
                    }

                    item {
                        Text(
                            text = "Arsip resmi manuskrip digital sejarah perjalanan YANSPROJECT.ID.",
                            fontSize = 11.sp,
                            color = textMuted,
                            lineHeight = 16.sp,
                            fontFamily = FontFamily.Serif,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                        )
                    }

                    // Procedural Luxury Hardcovers shelf (Side by side horizontal or stacked vertical)
                    val book1 = books.find { it.id == "kitab_01" }
                    if (book1 != null) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "YANSPROJECT.ID HISTORY — PUBLISHED",
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Black,
                                    color = agedGold,
                                    letterSpacing = 1.sp
                                )
                                SacredHardcoverBook(
                                    title = book1.title,
                                    subtitle = book1.subtitle,
                                    collectionCode = "DMA-YP01",
                                    classification = "ARCHIVE • VOL I",
                                    isPublished = true,
                                    progress = remember(completedBabs) {
                                        val total = book1.juzList.sumOf { it.babList.size }
                                        var comp = 0
                                        book1.juzList.forEach { j -> j.babList.forEach { b -> if (completedBabs.contains(b.id)) comp++ } }
                                        if (total > 0) (comp.toFloat() / total.toFloat() * 100).toInt() else 0
                                    }
                                ) {
                                    selectedBookId = "kitab_01"
                                    currentJuzView = -3
                                }
                            }
                        }
                    }

                    val book2 = books.find { it.id == "kitab_02" }
                    if (book2 != null) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "RISALAH MADAD AULIYA — DRAFT",
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Black,
                                    color = cyanPulse,
                                    letterSpacing = 1.sp
                                )
                                SacredHardcoverBook(
                                    title = book2.title,
                                    subtitle = book2.subtitle,
                                    collectionCode = "DMA-YP02",
                                    classification = "ARCHIVE • VOL II",
                                    isPublished = false,
                                    progress = remember(completedBabs) {
                                        val total = book2.juzList.sumOf { it.babList.size }
                                        var comp = 0
                                        book2.juzList.forEach { j -> j.babList.forEach { b -> if (completedBabs.contains(b.id)) comp++ } }
                                        if (total > 0) (comp.toFloat() / total.toFloat() * 100).toInt() else 0
                                    }
                                ) {
                                    selectedBookId = "kitab_02"
                                    currentJuzView = -3
                                }
                            }
                        }
                    }

                    // Unified Classification Register Ledger (Museum Overview Stats)
                    item {
                        SharedPremiumCard(
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            padding = 20.dp,
                            borderGlowColor = agedGold.copy(alpha = 0.25f)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Outlined.AccountBalance, contentDescription = null, tint = agedGold, modifier = Modifier.size(18.dp))
                                    Text(
                                        text = "REGISTRY ARCHIVE",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = agedGold,
                                        letterSpacing = 1.5.sp
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("MANUSCRIPT COUNT", fontSize = 8.sp, color = textMuted, letterSpacing = 0.5.sp)
                                        Text("2 Volumes", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textWhite)
                                    }
                                    Column {
                                        Text("SAVED ANNOTATIONS", fontSize = 8.sp, color = textMuted, letterSpacing = 0.5.sp)
                                        Text("${paragraphNotes.size} Reflections", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textWhite)
                                    }
                                    Column {
                                        Text("STABILIZED HIGHLIGHTS", fontSize = 8.sp, color = textMuted, letterSpacing = 0.5.sp)
                                        Text("${paragraphHighlights.size} Snippets", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = cyanPulse)
                                    }
                                }

                                Divider(color = darkTeal.copy(alpha = 0.4f), thickness = 0.5.dp)

                                Text(
                                    text = "System Sync Status: SECURE OFFLINE PERSISTENCE ACTIVE. Semesta data Anda terenkripsi secara aman dan siap disinkronkan ke cloud begitu koneksi terjalin.",
                                    fontSize = 9.sp,
                                    color = textMuted,
                                    lineHeight = 12.sp,
                                    fontStyle = FontStyle.Italic
                                )
                            }
                        }
                    }
                }
            } else if (page == -3) {
                // ==========================================
                // SACRED TITLE ARCHIVE / BOOK DETAIL PAGE
                // ==========================================
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { currentJuzView = -2 }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = agedGold)
                            }
                            Text(
                                text = "OPENING MANUSCRIPT",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = agedGold,
                                letterSpacing = 1.5.sp,
                                fontFamily = FontFamily.Serif
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { showSearchOverlay = true }) {
                                    Icon(Icons.Outlined.Search, contentDescription = "Cari", tint = agedGold)
                                }
                                IconButton(onClick = { showTocOverlay = true }) {
                                    Icon(Icons.Outlined.FormatListNumbered, contentDescription = "Daftar Isi", tint = agedGold)
                                }
                            }
                        }
                    }

                    // Book cover inside details page
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            SacredHardcoverBook(
                                title = currentBook.title,
                                subtitle = currentBook.subtitle,
                                collectionCode = if (selectedBookId == "kitab_01") "DMA-YP01" else "DMA-YP02",
                                classification = if (selectedBookId == "kitab_01") "ARCHIVE - VOL I" else "ARCHIVE - VOL II",
                                isPublished = selectedBookId == "kitab_01",
                                progress = progressPercent
                            ) {
                                // Do nothing, already on details page
                            }
                        }
                    }

                    // Tab Selector: Info vs Bookmarks vs Notes vs Favorites
                    item {
                        ScrollableTabRow(
                            selectedTabIndex = activeDetailTab,
                            containerColor = Color.Transparent,
                            contentColor = agedGold,
                            edgePadding = 0.dp,
                            indicator = { tabPositions ->
                                if (tabPositions.isNotEmpty()) {
                                    TabRowDefaults.SecondaryIndicator(
                                        modifier = Modifier.tabIndicatorOffset(tabPositions[activeDetailTab]),
                                        color = agedGold
                                    )
                                }
                            },
                            divider = {}
                        ) {
                            Tab(
                                selected = activeDetailTab == 0,
                                onClick = { activeDetailTab = 0 },
                                text = { Text("ARSIP", fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp) }
                            )
                            Tab(
                                selected = activeDetailTab == 1,
                                onClick = { activeDetailTab = 1 },
                                text = { Text("MARKA (${bookmarkedBabs.filter { it.startsWith(selectedBookId) }.size})", fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp) }
                            )
                            Tab(
                                selected = activeDetailTab == 2,
                                onClick = { activeDetailTab = 2 },
                                text = { Text("CATATAN (${paragraphNotes.filter { it.key.startsWith(selectedBookId) }.size})", fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp) }
                            )
                            Tab(
                                selected = activeDetailTab == 3,
                                onClick = { activeDetailTab = 3 },
                                text = { Text("FAVORIT (${paragraphFavorites.filter { it.startsWith(selectedBookId) }.size})", fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp) }
                            )
                        }
                    }

                    when (activeDetailTab) {
                        0 -> {
                            // --- SUB-VIEW: META-INFORMASI ---
                            item {
                                SharedPremiumCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    padding = 20.dp,
                                    borderGlowColor = cyanPulse.copy(alpha = 0.2f)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                        Text(
                                            text = "METADATA MANUSKRIP",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = agedGold,
                                            letterSpacing = 1.sp
                                        )

                                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("Katalog ID", fontSize = 11.sp, color = textMuted)
                                                Text(if (selectedBookId == "kitab_01") "DMA-YP-01" else "DMA-YP-02", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textWhite)
                                            }
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("Jumlah Jilid", fontSize = 11.sp, color = textMuted)
                                                Text("${currentBook.juzList.size} JUZ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textWhite)
                                            }
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("Jumlah BAB", fontSize = 11.sp, color = textMuted)
                                                Text("$totalBabCount BAB", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textWhite)
                                            }
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("Status Publikasi", fontSize = 11.sp, color = textMuted)
                                                Text(if (selectedBookId == "kitab_01") "PUBLISHED" else "DRAFT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (selectedBookId == "kitab_01") cyanPulse else agedGold)
                                            }
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("Terakhir Dibaca", fontSize = 11.sp, color = textMuted)
                                                Text(lastOpenedBabTitle, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = agedGold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }

                                        Divider(color = darkTeal.copy(alpha = 0.3f), thickness = 0.5.dp)

                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("Progress Membaca", fontSize = 11.sp, color = textMuted)
                                                Text("$progressPercent% Selesai", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = cyanPulse)
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(4.dp)
                                                    .background(darkTeal.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth(fraction = (progressPercent / 100f).coerceIn(0f, 1f))
                                                        .fillMaxHeight()
                                                        .background(cyanPulse, RoundedCornerShape(2.dp))
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        1 -> {
                            // --- SUB-VIEW: BOOKMARKS LIST ---
                            val bookmarks = bookmarkedBabs.filter { it.startsWith(selectedBookId) }
                            if (bookmarks.isEmpty()) {
                                item {
                                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                        Text("Tidak ada marka baca tersimpan.", color = textMuted, fontSize = 12.sp, fontStyle = FontStyle.Italic)
                                    }
                                }
                            } else {
                                items(bookmarks.toList()) { bId ->
                                    // Search chapter detail
                                    var chapterTitle = "Bab"
                                    var targetJuz = -1
                                    var targetBab = -1
                                    if (bId.endsWith("muqaddimah")) {
                                        chapterTitle = "Muqaddimah"
                                        targetJuz = -1
                                        targetBab = 0
                                    } else if (bId.endsWith("penutup")) {
                                        chapterTitle = "Penutup"
                                        targetJuz = 999
                                        targetBab = 0
                                    } else {
                                        currentBook.juzList.forEachIndexed { jIdx, j ->
                                            j.babList.forEachIndexed { bIdx, b ->
                                                if (b.id == bId) {
                                                    chapterTitle = b.title
                                                    targetJuz = jIdx
                                                    targetBab = bIdx
                                                }
                                            }
                                        }
                                    }
                                    Card(
                                        onClick = { if (targetJuz != -100) openReader(targetJuz, targetBab) },
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF163536).copy(alpha = 0.4f)),
                                        border = BorderStroke(0.5.dp, agedGold.copy(alpha = 0.2f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Outlined.Bookmark, contentDescription = null, tint = agedGold, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(chapterTitle, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textWhite, modifier = Modifier.weight(1f))
                                            IconButton(onClick = { saveBookmark(bId, false) }) {
                                                Icon(Icons.Outlined.Delete, contentDescription = "Hapus", tint = textMuted, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        2 -> {
                            // --- SUB-VIEW: PERSONAL NOTES LIST ---
                            val notes = paragraphNotes.filter { it.key.startsWith(selectedBookId) }
                            if (notes.isEmpty()) {
                                item {
                                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                        Text("Belum ada catatan kontemplasi ditulis.", color = textMuted, fontSize = 12.sp, fontStyle = FontStyle.Italic)
                                    }
                                }
                            } else {
                                items(notes.toList()) { (noteKey, noteVal) ->
                                    // Key format: bookId_juzView_babView_paragraphIdx
                                    val parts = noteKey.split("_")
                                    val juzIdx = parts.getOrNull(2)?.toIntOrNull() ?: -1
                                    val babIdx = parts.getOrNull(3)?.toIntOrNull() ?: 0
                                    val sectionName = when (juzIdx) {
                                        -1 -> "Muqaddimah"
                                        999 -> "Penutup"
                                        else -> currentBook.juzList.getOrNull(juzIdx)?.babList?.getOrNull(babIdx)?.title ?: "Manuskrip"
                                    }
                                    Card(
                                        onClick = { openReader(juzIdx, babIdx) },
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF163536).copy(alpha = 0.4f)),
                                        border = BorderStroke(0.5.dp, agedGold.copy(alpha = 0.2f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Outlined.ModeComment, contentDescription = null, tint = agedGold, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(sectionName, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = agedGold)
                                                Spacer(modifier = Modifier.weight(1f))
                                                IconButton(onClick = { saveNote(noteKey, "") }, modifier = Modifier.size(24.dp)) {
                                                    Icon(Icons.Outlined.Delete, contentDescription = "Hapus", tint = textMuted, modifier = Modifier.size(14.dp))
                                                }
                                            }
                                            Text(
                                                text = noteVal,
                                                fontSize = 12.sp,
                                                color = textWhite,
                                                fontStyle = FontStyle.Italic
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        3 -> {
                            // --- SUB-VIEW: FAVORITES LIST ---
                            val favs = paragraphFavorites.filter { it.startsWith(selectedBookId) }
                            if (favs.isEmpty()) {
                                item {
                                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                        Text("Belum ada kutipan favorit ditandai.", color = textMuted, fontSize = 12.sp, fontStyle = FontStyle.Italic)
                                    }
                                }
                            } else {
                                items(favs.toList()) { favKey ->
                                    val parts = favKey.split("_")
                                    val juzIdx = parts.getOrNull(1)?.toIntOrNull() ?: -1
                                    val babIdx = parts.getOrNull(2)?.toIntOrNull() ?: 0
                                    val sectionName = when (juzIdx) {
                                        -1 -> "Muqaddimah"
                                        999 -> "Penutup"
                                        else -> currentBook.juzList.getOrNull(juzIdx)?.babList?.getOrNull(babIdx)?.title ?: "Manuskrip"
                                    }
                                    Card(
                                        onClick = { openReader(juzIdx, babIdx) },
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF163536).copy(alpha = 0.4f)),
                                        border = BorderStroke(0.5.dp, agedGold.copy(alpha = 0.2f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Outlined.Star, contentDescription = null, tint = cyanPulse, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(sectionName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textWhite, modifier = Modifier.weight(1f))
                                            IconButton(onClick = { saveFavorite(favKey, false) }) {
                                                Icon(Icons.Outlined.Delete, contentDescription = "Hapus", tint = textMuted, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Luxury Action Buttons
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 10.dp)) {
                            Button(
                                onClick = {
                                    if (lastOpenedJuzIndex != -2 && lastOpenedJuzIndex != -3) {
                                        openReader(lastOpenedJuzIndex, lastOpenedBabIndex)
                                    } else {
                                        openReader(-1, 0)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = agedGold, contentColor = shadowBlack),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                            ) {
                                Icon(Icons.Outlined.MenuBook, contentDescription = null, tint = shadowBlack)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("LANJUTKAN MEMBACA", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, letterSpacing = 1.sp)
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(
                                    onClick = { openReader(-1, 0) },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = agedGold),
                                    border = BorderStroke(1.dp, agedGold),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                ) {
                                    Icon(Icons.Outlined.History, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("DARI AWAL", fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.5.sp)
                                }

                                OutlinedButton(
                                    onClick = { showTocOverlay = true },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = agedGold),
                                    border = BorderStroke(1.dp, agedGold),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                ) {
                                    Icon(Icons.Outlined.FormatListNumbered, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("DAFTAR ISI", fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.5.sp)
                                }
                            }
                        }
                    }

                    // Published history notes
                    if (selectedBookId == "kitab_01") {
                        item {
                            SharedPremiumCard(
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                padding = 20.dp,
                                borderGlowColor = agedGold.copy(alpha = 0.15f)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("CATATAN PENULIS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = agedGold, letterSpacing = 1.sp)
                                    Text(
                                        text = "Arsip digital ini mendokumentasikan sejarah perjalanan dan nilai filosofis yang melahirkan ekosistem YANSPROJECT.ID.",
                                        fontSize = 11.sp,
                                        color = textMuted,
                                        lineHeight = 16.sp,
                                        fontFamily = FontFamily.Serif,
                                        textAlign = TextAlign.Justify
                                    )
                                }
                            }
                        }
                    } else {
                        item {
                            RisalahTrailerCard(modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                        }
                    }
                }
            } else {
                // ==========================================
                // SACRED MANUSCRIPT DIGITAL READER (READER VIEW)
                // ==========================================
                val currentBabId = when (page) {
                    -1 -> "${currentBook.id}_muqaddimah"
                    999 -> "${currentBook.id}_penutup"
                    else -> currentBook.juzList.getOrNull(page)?.babList?.getOrNull(currentBabView)?.id ?: ""
                }

                val currentTitle = when (page) {
                    -1 -> "MUQADDIMAH"
                    999 -> "PENUTUP"
                    else -> currentBook.juzList.getOrNull(page)?.babList?.getOrNull(currentBabView)?.title ?: "BACA"
                }

                val currentContent = when (page) {
                    -1 -> currentBook.muqaddimah.split("\n").filter { it.isNotBlank() }
                    999 -> currentBook.penutup.split("\n").filter { it.isNotBlank() }
                    else -> currentBook.juzList.getOrNull(page)?.babList?.getOrNull(currentBabView)?.content ?: emptyList()
                }

                val scrollState = rememberScrollState()

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(activeTheme.background)
                        .statusBarsPadding()
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // --- BREADCRUMB HEADER (SACRED SLATE DESIGN) ---
                        AnimatedVisibility(
                            visible = !isFullScreenMode,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(activeTheme.cardBackground)
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                IconButton(onClick = { currentJuzView = -3 }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = activeTheme.accent)
                                }
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = when (page) {
                                            -1 -> "MUQADDIMAH KITAB"
                                            999 -> "PENUTUP KITAB"
                                            else -> currentBook.juzList.getOrNull(page)?.title?.uppercase() ?: ""
                                        },
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = activeTheme.accent,
                                        letterSpacing = 1.sp,
                                        fontFamily = activeFontFamily
                                    )
                                    Text(
                                        text = currentTitle,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = activeTheme.text,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontFamily = activeFontFamily
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { showThemeChooser = !showThemeChooser }) {
                                        Icon(Icons.Outlined.Palette, contentDescription = "Pengaturan Tampilan", tint = activeTheme.accent)
                                    }
                                    IconButton(onClick = { isFullScreenMode = true }) {
                                        Icon(Icons.Outlined.Fullscreen, contentDescription = "Layar Penuh", tint = activeTheme.accent)
                                    }
                                    IconButton(onClick = { showSearchOverlay = true }) {
                                        Icon(Icons.Outlined.Search, contentDescription = "Cari", tint = activeTheme.accent)
                                    }
                                    IconButton(onClick = { showTocOverlay = true }) {
                                        Icon(Icons.Outlined.FormatListNumbered, contentDescription = "Daftar Isi", tint = activeTheme.accent)
                                    }
                                }
                            }
                        }

                        // --- ACTIVE THEME / FONT STYLE & SIZE CHOOSER DROPDOWN PANEL ---
                        AnimatedVisibility(
                            visible = showThemeChooser && !isFullScreenMode,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(activeTheme.cardBackground)
                                    .border(BorderStroke(0.5.dp, activeTheme.accent.copy(alpha = 0.2f)))
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // 1. Mode Membaca & Warna Tema
                                Text(
                                    text = "PILIH MODE MEMBACA & WARNA TEMA",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = activeTheme.accent,
                                    letterSpacing = 1.sp
                                )

                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(readingThemes) { th ->
                                        val isSel = th.id == activeThemeId
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(th.background)
                                                .border(
                                                    width = if (isSel) 2.dp else 0.5.dp,
                                                    color = if (isSel) th.accent else th.textSecondary.copy(alpha = 0.3f),
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .clickable { saveTheme(th.id) }
                                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = th.name,
                                                color = th.text,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }

                                HorizontalDivider(color = activeTheme.accent.copy(alpha = 0.15f), thickness = 0.5.dp)

                                // 2. Gaya Typography / Font Selector
                                Text(
                                    text = "GAYA TYPOGRAPHY / FONT",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = activeTheme.accent,
                                    letterSpacing = 1.sp
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val fontOptions = listOf(
                                        "serif" to "Serif (Klasik)",
                                        "sans" to "Sans (Modern)",
                                        "monospace" to "Monospace (Naskah)"
                                    )
                                    fontOptions.forEach { (key, label) ->
                                        val isSel = readerFontStyle == key
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isSel) activeTheme.accent.copy(alpha = 0.2f) else activeTheme.background)
                                                .border(
                                                    width = if (isSel) 1.5.dp else 0.5.dp,
                                                    color = if (isSel) activeTheme.accent else activeTheme.textSecondary.copy(alpha = 0.2f),
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .clickable { saveFontStyle(key) }
                                                .padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = label,
                                                fontSize = 10.5.sp,
                                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSel) activeTheme.accent else activeTheme.textSecondary,
                                                fontFamily = when (key) {
                                                    "sans" -> FontFamily.SansSerif
                                                    "monospace" -> FontFamily.Monospace
                                                    else -> FontFamily.Serif
                                                }
                                            )
                                        }
                                    }
                                }

                                HorizontalDivider(color = activeTheme.accent.copy(alpha = 0.15f), thickness = 0.5.dp)

                                // 3. Ukuran Teks & Preset Quick Select
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Ukuran Teks", fontSize = 11.5.sp, color = activeTheme.textSecondary)
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            IconButton(
                                                onClick = { if (readerTextSize > 12f) saveTextSize(readerTextSize - 2f) },
                                                enabled = readerTextSize > 12f
                                            ) {
                                                Icon(Icons.Outlined.Remove, contentDescription = "Kurang", tint = activeTheme.accent)
                                            }
                                            Text("${readerTextSize.toInt()} sp", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = activeTheme.text)
                                            IconButton(
                                                onClick = { if (readerTextSize < 28f) saveTextSize(readerTextSize + 2f) },
                                                enabled = readerTextSize < 28f
                                            ) {
                                                Icon(Icons.Outlined.Add, contentDescription = "Tambah", tint = activeTheme.accent)
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf(14f, 16f, 18f, 20f, 24f).forEach { presetSize ->
                                            val isSel = readerTextSize == presetSize
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (isSel) activeTheme.accent else activeTheme.background)
                                                    .clickable { saveTextSize(presetSize) }
                                                    .padding(vertical = 6.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "${presetSize.toInt()}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSel) activeTheme.cardBackground else activeTheme.text
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // --- EDITORIAL READING AREA ---
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(scrollState)
                                .padding(horizontal = 28.dp, vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            var firstNarrativeIndex = -1
                            currentContent.forEachIndexed { idx, p ->
                                val isQuote = p.trim().startsWith("\"") || p.trim().startsWith("“")
                                if (!isQuote && firstNarrativeIndex == -1) {
                                    firstNarrativeIndex = idx
                                }
                            }

                            currentContent.forEachIndexed { index, paragraph ->
                                val trimmed = paragraph.trim()
                                val isQuote = trimmed.startsWith("\"") || trimmed.startsWith("“")
                                val isArabic = trimmed.any { it in '\u0600'..'\u06FF' }

                                val paragraphKey = "${currentBook.id}_${page}_${currentBabView}_$index"
                                val hasNote = paragraphNotes.containsKey(paragraphKey)
                                val hasHighlight = paragraphHighlights.contains(paragraphKey)
                                val hasFavorite = paragraphFavorites.contains(paragraphKey)

                                val pBg = if (hasHighlight) activeTheme.accent.copy(alpha = 0.12f) else Color.Transparent

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(pBg, RoundedCornerShape(4.dp))
                                        .clickable {
                                            selectedParagraphKeyForAction = paragraphKey
                                            selectedParagraphTextForAction = paragraph
                                            selectedParagraphChapterName = currentTitle
                                            selectedParagraphIndexForAction = index
                                        }
                                        .padding(vertical = 12.dp, horizontal = 4.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        if (hasNote || hasFavorite) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(bottom = 4.dp)
                                            ) {
                                                if (hasFavorite) {
                                                    Icon(Icons.Outlined.Star, contentDescription = null, tint = activeTheme.accent, modifier = Modifier.size(12.dp))
                                                }
                                                if (hasNote) {
                                                    Icon(Icons.Outlined.Create, contentDescription = null, tint = activeTheme.accent, modifier = Modifier.size(12.dp))
                                                    Text("Catatan Kontemplasi", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = activeTheme.accent)
                                                }
                                            }
                                        }

                                        if (isQuote) {
                                            val quoteText = trimmed.removeSurrounding("\"", "\"").removeSurrounding("“", "”").removeSurrounding("“", "”")
                                            ManuscriptQuoteBlock(
                                                quote = quoteText,
                                                cardBgColor = activeTheme.cardBackground,
                                                textColor = activeTheme.text,
                                                accentColor = activeTheme.accent,
                                                fontFamily = activeFontFamily,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        } else if (index == firstNarrativeIndex) {
                                            val firstChar = trimmed.first().toString()
                                            val remainingText = trimmed.substring(1)
                                            ManuscriptDropCap(
                                                letter = firstChar,
                                                text = remainingText,
                                                fontSize = readerTextSize.sp,
                                                textColor = activeTheme.text,
                                                accentColor = activeTheme.accent,
                                                fontFamily = activeFontFamily
                                            )
                                        } else {
                                            Text(
                                                text = paragraph,
                                                fontSize = readerTextSize.sp,
                                                fontWeight = FontWeight.Normal,
                                                color = activeTheme.text,
                                                lineHeight = (readerTextSize * 1.65f).sp,
                                                fontFamily = if (isArabic) FontUtils.getPremiumArabicFontFamily(androidx.compose.ui.platform.LocalContext.current) else activeFontFamily,
                                                textAlign = if (isArabic) TextAlign.Right else TextAlign.Justify,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(32.dp))

                            // Completion Toggle Button
                            val isCompleted = completedBabs.contains(currentBabId)
                            OutlinedButton(
                                onClick = { saveCompleted(currentBabId, !isCompleted) },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = if (isCompleted) cyanPulse else activeTheme.accent
                                ),
                                border = BorderStroke(1.dp, if (isCompleted) cyanPulse else activeTheme.accent),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                            ) {
                                Icon(
                                    imageVector = if (isCompleted) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (isCompleted) cyanPulse else activeTheme.accent
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isCompleted) "Telah Selesai Dibaca" else "Tandai Selesai Membaca",
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCompleted) cyanPulse else activeTheme.text,
                                    fontSize = 12.sp,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }

                        // --- PRECISE EDITORIAL FOOTER NAVIGATION ---
                        AnimatedVisibility(
                            visible = !isFullScreenMode,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(activeTheme.cardBackground)
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = { navigatePrev() },
                                        colors = ButtonDefaults.textButtonColors(contentColor = activeTheme.accent)
                                    ) {
                                        Icon(Icons.Outlined.ChevronLeft, contentDescription = null)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Sebelumnya", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }

                                    // Progress Page Indicator Center
                                    Text(
                                        text = when (page) {
                                            -1 -> "Muqaddimah"
                                            999 -> "Penutup"
                                            else -> "Juz ${page + 1} • Bab ${currentBabView + 1}"
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = activeTheme.textSecondary,
                                        fontFamily = activeFontFamily
                                    )

                                    TextButton(
                                        onClick = { navigateNext() },
                                        colors = ButtonDefaults.textButtonColors(contentColor = activeTheme.accent)
                                    ) {
                                        Text("Berikutnya", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(Icons.Outlined.ChevronRight, contentDescription = null)
                                    }
                                }

                                // Bottom thin progress indicator bar
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(3.dp)
                                        .background(activeTheme.accent.copy(alpha = 0.1f))
                                ) {
                                    val progress = if (scrollState.maxValue > 0) {
                                        scrollState.value.toFloat() / scrollState.maxValue.toFloat()
                                    } else {
                                        0f
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(fraction = progress)
                                            .fillMaxHeight()
                                            .background(activeTheme.accent)
                                    )
                                }
                            }
                        }
                    }

                    // Floating Exit Fullscreen Button when in Fullscreen mode
                    if (isFullScreenMode) {
                        IconButton(
                            onClick = { isFullScreenMode = false },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp)
                                .clip(CircleShape)
                                .background(activeTheme.cardBackground.copy(alpha = 0.9f))
                                .border(0.5.dp, activeTheme.accent.copy(alpha = 0.4f), CircleShape)
                        ) {
                            Icon(
                                Icons.Outlined.FullscreenExit,
                                contentDescription = "Keluar Layar Penuh",
                                tint = activeTheme.accent
                            )
                        }
                    }
                }
            }
        }

        // ==========================================
        // EDITORIAL TABLE OF CONTENTS OVERLAY BS
        // ==========================================
        if (showTocOverlay) {
            PremiumBottomSheet(
                onDismissRequest = { showTocOverlay = false }
            ) {
                Column(modifier = Modifier.padding(bottom = 12.dp)) {
                    Text(
                        text = "DAFTAR ISI MANUSKRIP",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = agedGold,
                        letterSpacing = 1.sp,
                        fontFamily = FontFamily.Serif
                    )
                    Text(
                        text = "${currentBook.title} - Museum Index",
                        fontSize = 11.sp,
                        color = textMuted,
                        fontFamily = FontFamily.Serif
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        val isActive = currentJuzView == -1
                        val cardBg = if (isActive) agedGold.copy(alpha = 0.15f) else Color(0xFF112B2C)
                        val cardBorder = if (isActive) agedGold else Color(0xFF0F3D3E)
                        Card(
                            onClick = { openReader(-1, 0) },
                            colors = CardDefaults.cardColors(containerColor = cardBg),
                            border = BorderStroke(1.dp, cardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isActive) Icons.Outlined.PlayArrow else Icons.Outlined.MenuBook,
                                    contentDescription = null,
                                    tint = if (isActive) cyanPulse else agedGold,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Muqaddimah Kitab",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (isActive) agedGold else textWhite,
                                        fontFamily = FontFamily.Serif
                                    )
                                    Text(text = "Pendahuluan", fontSize = 10.sp, color = textMuted, fontFamily = FontFamily.Serif)
                                }
                                if (isActive) {
                                    Icon(
                                        imageVector = Icons.Outlined.VolumeUp,
                                        contentDescription = "Aktif",
                                        tint = cyanPulse,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    currentBook.juzList.forEachIndexed { juzIdx, juz ->
                        item {
                            Text(
                                text = juz.title.uppercase(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = agedGold,
                                modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                                letterSpacing = 1.sp,
                                fontFamily = FontFamily.Serif
                            )
                        }
                        items(juz.babList) { bab ->
                            val babIdx = juz.babList.indexOf(bab)
                            val isActive = currentJuzView == juzIdx && currentBabView == babIdx
                            val cardBg = if (isActive) agedGold.copy(alpha = 0.15f) else Color(0xFF112B2C)
                            val cardBorder = if (isActive) agedGold else Color(0xFF0F3D3E)
                            val isCompleted = completedBabs.contains(bab.id)

                            Card(
                                onClick = { openReader(juzIdx, babIdx) },
                                colors = CardDefaults.cardColors(containerColor = cardBg),
                                border = BorderStroke(1.dp, cardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isActive) Icons.Outlined.PlayArrow else Icons.Outlined.MenuBook,
                                        contentDescription = null,
                                        tint = if (isActive) cyanPulse else (if (isCompleted) cyanPulse else textMuted),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = bab.title,
                                            fontSize = 13.sp,
                                            color = if (isActive) agedGold else textWhite,
                                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                            fontFamily = FontFamily.Serif
                                        )
                                        Text(
                                            text = "Bagian ${babIdx + 1}",
                                            fontSize = 10.sp,
                                            color = textMuted,
                                            fontFamily = FontFamily.Serif
                                        )
                                    }
                                    Icon(
                                        imageVector = if (isCompleted) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = if (isCompleted) cyanPulse else textMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    item {
                        val isActive = currentJuzView == 999
                        val cardBg = if (isActive) agedGold.copy(alpha = 0.15f) else Color(0xFF112B2C)
                        val cardBorder = if (isActive) agedGold else Color(0xFF0F3D3E)
                        Card(
                            onClick = { openReader(999, 0) },
                            colors = CardDefaults.cardColors(containerColor = cardBg),
                            border = BorderStroke(1.dp, cardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isActive) Icons.Outlined.PlayArrow else Icons.Outlined.Lock,
                                    contentDescription = null,
                                    tint = if (isActive) cyanPulse else agedGold,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Penutup Kitab",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (isActive) agedGold else textWhite,
                                        fontFamily = FontFamily.Serif
                                    )
                                    Text(text = "Doa & Penutup Risalah", fontSize = 10.sp, color = textMuted, fontFamily = FontFamily.Serif)
                                }
                                if (isActive) {
                                    Icon(
                                        imageVector = Icons.Outlined.VolumeUp,
                                        contentDescription = "Aktif",
                                        tint = cyanPulse,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // PREMIUM SEARCH OVERLAY PANEL
        // ==========================================
        if (showSearchOverlay) {
            AlertDialog(
                onDismissRequest = { showSearchOverlay = false },
                containerColor = shadowBlack,
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
                modifier = Modifier
                    .fillMaxSize()
                    .background(shadowBlack),
                title = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { showSearchOverlay = false }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = agedGold)
                        }
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Cari kata atau bab...", color = textMuted, fontSize = 13.sp) },
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF0F3D3E).copy(alpha = 0.3f),
                                unfocusedContainerColor = Color(0xFF0F3D3E).copy(alpha = 0.1f),
                                focusedBorderColor = agedGold,
                                unfocusedBorderColor = Color(0xFF0F3D3E),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            leadingIcon = {
                                Icon(Icons.Outlined.Search, contentDescription = null, tint = agedGold)
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Outlined.Close, contentDescription = "Bersihkan", tint = textMuted)
                                    }
                                }
                            }
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 8.dp)
                    ) {
                        if (searchQuery.trim().length < 2) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Outlined.Search, contentDescription = null, tint = textMuted.copy(alpha = 0.4f), modifier = Modifier.size(48.dp))
                                    Text(
                                        text = "Ketik minimal 2 karakter untuk memulai pencarian di seluruh manuskrip.",
                                        color = textMuted,
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 32.dp)
                                    )
                                }
                            }
                        } else if (searchResults.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Outlined.SearchOff, contentDescription = null, tint = textMuted.copy(alpha = 0.4f), modifier = Modifier.size(48.dp))
                                    Text(
                                        text = "Tidak ditemukan hasil untuk \"$searchQuery\"",
                                        color = textMuted,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = "Coba gunakan kata kunci lain yang lebih umum.",
                                        color = textMuted,
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "DITEMUKAN ${searchResults.size} HASIL UNTUK \"${searchQuery.uppercase()}\"",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = agedGold,
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                letterSpacing = 1.sp
                            )
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                items(searchResults) { result ->
                                    Card(
                                        onClick = {
                                            selectedBookId = result.bookId
                                            openReader(result.juzIndex, result.babIndex)
                                            showSearchOverlay = false
                                        },
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF163536)),
                                        border = BorderStroke(1.dp, Color(0xFF0F3D3E)),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = result.sectionTitle.uppercase(),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = agedGold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f),
                                                    fontFamily = FontFamily.Serif
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(Color(0xFF0F3D3E))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = result.bookTitle,
                                                        fontSize = 8.sp,
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                            Text(
                                                text = result.matchText,
                                                fontSize = 12.sp,
                                                color = textWhite,
                                                maxLines = 3,
                                                overflow = TextOverflow.Ellipsis,
                                                lineHeight = 16.sp,
                                                fontFamily = FontFamily.Serif
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {}
            )
        }

        // ==========================================
        // INTERACTIVE PARAGRAPH EDITORIAL DIALOG
        // ==========================================
        if (selectedParagraphKeyForAction != null) {
            val key = selectedParagraphKeyForAction!!
            var noteVal by remember(key) { mutableStateOf(paragraphNotes[key] ?: "") }
            val isFav = paragraphFavorites.contains(key)
            val isHil = paragraphHighlights.contains(key)

            AlertDialog(
                onDismissRequest = { selectedParagraphKeyForAction = null },
                containerColor = Color(0xFF112B2C),
                modifier = Modifier
                    .padding(16.dp)
                    .border(1.dp, agedGold.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "KONTEMPLASI & ALAT BACA",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = agedGold,
                            letterSpacing = 1.sp
                        )
                        IconButton(onClick = { selectedParagraphKeyForAction = null }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Tutup", tint = agedGold)
                        }
                    }
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Text Preview Card
                        Card(
                            colors = CardDefaults.cardColors(containerColor = shadowBlack),
                            border = BorderStroke(0.5.dp, agedGold.copy(alpha = 0.2f)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = selectedParagraphTextForAction,
                                fontSize = 12.sp,
                                color = textWhite,
                                fontStyle = FontStyle.Italic,
                                lineHeight = 18.sp,
                                fontFamily = FontFamily.Serif,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(12.dp)
                            )
                        }

                        // Switches Actions Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.clickable { saveHighlight(key, !isHil) }
                            ) {
                                Icon(
                                    imageVector = if (isHil) Icons.Outlined.AutoAwesome else Icons.Outlined.AutoAwesome,
                                    contentDescription = null,
                                    tint = if (isHil) cyanPulse else textMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text("Stabilo Glow", fontSize = 11.sp, color = if (isHil) cyanPulse else textWhite)
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.clickable { saveFavorite(key, !isFav) }
                            ) {
                                Icon(
                                    imageVector = if (isFav) Icons.Outlined.Star else Icons.Outlined.StarOutline,
                                    contentDescription = null,
                                    tint = if (isFav) agedGold else textMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text("Favorit", fontSize = 11.sp, color = if (isFav) agedGold else textWhite)
                            }
                        }

                        Divider(color = darkTeal.copy(alpha = 0.3f), thickness = 0.5.dp)

                        // Note Input
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("TULIS CATATAN KONTEMPLASI", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = agedGold)
                            OutlinedTextField(
                                value = noteVal,
                                onValueChange = { noteVal = it },
                                placeholder = { Text("Tulis refleksi pribadi mengenai bagian manuskrip ini...", color = textMuted, fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth().height(90.dp),
                                textStyle = TextStyle(fontSize = 12.sp, color = textWhite),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = agedGold,
                                    unfocusedBorderColor = Color(0xFF0F3D3E),
                                    focusedContainerColor = shadowBlack,
                                    unfocusedContainerColor = shadowBlack
                                )
                            )
                        }
                    }
                },
                confirmButton = {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("YansQuote", "\"${selectedParagraphTextForAction}\"\n\n— $selectedParagraphChapterName • ${currentBook.title}")
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Kutipan berhasil disalin!", Toast.LENGTH_SHORT).show()
                                selectedParagraphKeyForAction = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF163536), contentColor = Color.White),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1.0f)
                        ) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Salin", fontSize = 11.sp)
                        }

                        Button(
                            onClick = {
                                saveNote(key, noteVal)
                                Toast.makeText(context, "Catatan berhasil disimpan!", Toast.LENGTH_SHORT).show()
                                selectedParagraphKeyForAction = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = agedGold, contentColor = shadowBlack),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1.0f)
                        ) {
                            Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(14.dp), tint = shadowBlack)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Simpan", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = shadowBlack)
                        }
                    }
                }
            )
        }

        // --- QUOTE SHARE DIALOG ---
        if (selectedQuoteToShare != null) {
            AlertDialog(
                onDismissRequest = { selectedQuoteToShare = null },
                containerColor = Color(0xFF0F3D3E).copy(alpha = 0.98f),
                tonalElevation = 8.dp,
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
                modifier = Modifier
                    .padding(28.dp)
                    .border(1.dp, agedGold.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "KUTIP & BAGIKAN",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = agedGold,
                            letterSpacing = 1.sp
                        )
                        IconButton(onClick = { selectedQuoteToShare = null }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Tutup", tint = agedGold)
                        }
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = ShadowBlack),
                            border = BorderStroke(1.dp, agedGold.copy(alpha = 0.25f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.FormatQuote,
                                    contentDescription = null,
                                    tint = agedGold.copy(alpha = 0.6f),
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = selectedQuoteToShare ?: "",
                                    fontSize = 14.sp,
                                    color = textWhite,
                                    fontStyle = FontStyle.Italic,
                                    textAlign = TextAlign.Center,
                                    fontFamily = FontFamily.Serif,
                                    lineHeight = 20.sp
                                )
                                Divider(color = agedGold.copy(alpha = 0.15f), thickness = 0.5.dp)
                                Text(
                                    text = "— $selectedQuoteSource",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = agedGold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("YansQuote", "\"${selectedQuoteToShare}\"\n\n— $selectedQuoteSource")
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Kutipan berhasil disalin!", Toast.LENGTH_SHORT).show()
                                selectedQuoteToShare = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = agedGold, contentColor = shadowBlack),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1.0f)
                        ) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Salin", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_SUBJECT, "Kutipan Manuskrip YANSPROJECT.ID")
                                    putExtra(android.content.Intent.EXTRA_TEXT, "\"${selectedQuoteToShare}\"\n\n— $selectedQuoteSource\n\nAbadi di dalam YANSPROJECT.ID")
                                }
                                context.startActivity(android.content.Intent.createChooser(shareIntent, "Bagikan Kutipan"))
                                selectedQuoteToShare = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF163536), contentColor = Color.White),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1.0f)
                        ) {
                            Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Bagikan", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            )
        }
    }
}

data class KitabSearchResult(
    val bookId: String,
    val bookTitle: String,
    val juzIndex: Int,
    val babIndex: Int,
    val sectionTitle: String,
    val matchText: String
)

@Composable
fun PremiumQuoteBlock(
    text: String,
    modifier: Modifier = Modifier
) {
    ManuscriptQuoteBlock(quote = text, modifier = modifier)
}

@Composable
fun DropCapParagraph(
    text: String,
    textSize: Float,
    modifier: Modifier = Modifier
) {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return

    val firstChar = trimmed.first().toString()
    val remainingText = trimmed.substring(1)

    ManuscriptDropCap(letter = firstChar, text = remainingText, modifier = modifier)
}

@Composable
fun RisalahTrailerCard(
    modifier: Modifier = Modifier
) {
    SharedPremiumCard(
        modifier = modifier,
        padding = 20.dp,
        borderGlowColor = cyanPulse.copy(alpha = 0.2f)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "STATUS MANUSKRIP",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = agedGold,
                    letterSpacing = 1.5.sp
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(30.dp))
                        .background(cyanPulse.copy(alpha = 0.12f))
                        .border(1.dp, cyanPulse, RoundedCornerShape(30.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "DRAFT",
                        color = cyanPulse,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "RISALAH MADAD AULIYA",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = textWhite,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "Digital Manuscript Archive",
                    fontSize = 11.sp,
                    color = agedGold,
                    fontStyle = FontStyle.Italic
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Progres Penulisan",
                        fontSize = 11.sp,
                        color = textMuted
                    )
                    Text(
                        text = "13% Completed",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = cyanPulse
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(darkTeal.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.13f)
                            .fillMaxHeight()
                            .background(cyanPulse, RoundedCornerShape(2.dp))
                    )
                }
            }

            Divider(color = darkTeal.copy(alpha = 0.4f), thickness = 1.dp)

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Catatan Penulis",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = agedGold
                )
                Text(
                    text = "Risalah ini tidak lahir dalam satu malam. Ia tumbuh perlahan, mengikuti langkah yang Allah bukakan; melewati doa, penantian, pertemuan, kehilangan, serta perjalanan-perjalanan yang tidak seluruhnya dapat dituliskan.",
                    color = textMuted,
                    fontSize = 11.sp,
                    fontStyle = FontStyle.Italic,
                    lineHeight = 16.sp,
                    textAlign = TextAlign.Justify
                )
            }
        }
    }
}

// ==========================================
// PROCEDURAL GEOMETRICAL 3D BOOK COVER
// ==========================================
@Composable
fun SacredHardcoverBook(
    title: String,
    subtitle: String,
    collectionCode: String,
    classification: String,
    isPublished: Boolean,
    progress: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val gold = Color(0xFFC6A15B)
    val leatherColor = if (isPublished) Color(0xFF0F3D3E) else Color(0xFF112B2C)
    val spineColor = if (isPublished) Color(0xFF081F20) else Color(0xFF0A1C1D)
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(320.dp)
            .shadow(24.dp, RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 14.dp, bottomEnd = 14.dp), ambientColor = Color.Black, spotColor = Color.Black)
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(spineColor, leatherColor, leatherColor),
                    startX = 0f,
                    endX = 140f
                ),
                shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 14.dp, bottomEnd = 14.dp)
            )
            .border(
                border = BorderStroke(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(gold.copy(alpha = 0.6f), Color.Transparent, gold.copy(alpha = 0.4f))
                    )
                ),
                shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 14.dp, bottomEnd = 14.dp)
            )
            .clickable { onClick() }
    ) {
        // Book thickness pages simulation on the right edge
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // Pages Edge simulation
            drawRect(
                color = Color(0xFFE5DEC4).copy(alpha = 0.85f),
                topLeft = androidx.compose.ui.geometry.Offset(w - 7.dp.toPx(), 4.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(5.dp.toPx(), h - 8.dp.toPx())
            )
            // Spine indenture shadow
            drawLine(
                color = Color.Black.copy(alpha = 0.45f),
                start = androidx.compose.ui.geometry.Offset(34.dp.toPx(), 0f),
                end = androidx.compose.ui.geometry.Offset(36.dp.toPx(), h),
                strokeWidth = 4f
            )
            drawLine(
                color = gold.copy(alpha = 0.35f),
                start = androidx.compose.ui.geometry.Offset(32.dp.toPx(), 0f),
                end = androidx.compose.ui.geometry.Offset(32.dp.toPx(), h),
                strokeWidth = 1f
            )
            
            // Concentric Gold Foiled Ornamental Frame
            val frameMargin = 16.dp.toPx()
            val spineOffset = 40.dp.toPx()
            drawRect(
                color = gold.copy(alpha = 0.55f),
                topLeft = androidx.compose.ui.geometry.Offset(spineOffset + 12.dp.toPx(), frameMargin),
                size = androidx.compose.ui.geometry.Size(w - spineOffset - 32.dp.toPx(), h - frameMargin * 2),
                style = Stroke(width = 1.25.dp.toPx())
            )
            
            // Sacred Central Emblem
            val centerCol = (w + spineOffset) / 2f
            val centerRow = h * 0.45f
            drawCircle(
                color = gold.copy(alpha = 0.2f),
                radius = 54.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(centerCol, centerRow),
                style = Stroke(width = 1.dp.toPx())
            )
            drawCircle(
                color = gold.copy(alpha = 0.15f),
                radius = 46.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(centerCol, centerRow),
                style = Stroke(width = 1.5.dp.toPx())
            )
        }
        
        // Gold foiled editorial typography & Layout
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 54.dp, end = 26.dp, top = 26.dp, bottom = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header catalog
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "YANSPROJECT.ID",
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = gold.copy(alpha = 0.85f),
                    letterSpacing = 1.sp,
                    fontFamily = FontFamily.Serif
                )
                Text(
                    text = collectionCode,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = gold.copy(alpha = 0.85f),
                    letterSpacing = 0.5.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            
            // Central Book Metadata Title
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 10.dp)
            ) {
                Icon(
                    imageVector = if (isPublished) Icons.Outlined.AutoAwesome else Icons.Outlined.HistoryEdu,
                    contentDescription = null,
                    tint = gold,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = title.uppercase(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    letterSpacing = 1.sp,
                    lineHeight = 18.sp,
                    fontFamily = FontFamily.Serif,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = subtitle,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    fontStyle = FontStyle.Italic,
                    color = gold,
                    textAlign = TextAlign.Center,
                    letterSpacing = 0.5.sp,
                    fontFamily = FontFamily.Serif
                )
            }
            
            // Bottom Metadata Block
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = classification,
                    fontSize = 7.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = gold.copy(alpha = 0.65f),
                    letterSpacing = 0.5.sp,
                    fontFamily = FontFamily.Serif
                )
                
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (isPublished) Color(0xFF0F3D3E) else Color(0x33C6A15B))
                        .border(0.5.dp, gold, RoundedCornerShape(3.dp))
                        .padding(horizontal = 10.dp, vertical = 2.5.dp)
                ) {
                    Text(
                        text = if (isPublished) "PUBLISHED ARCHIVE" else "LIVING MANUSCRIPT",
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isPublished) Color(0xFF4FD1C5) else gold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
        
        // Silk Bookmark ribbon hanging down from top edge
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 26.dp)
                .width(14.dp)
                .height(48.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(gold, gold.copy(alpha = 0.6f))
                    ),
                    shape = RoundedCornerShape(bottomStart = 2.dp, bottomEnd = 2.dp)
                )
                .border(0.5.dp, Color.Black.copy(alpha = 0.25f), RoundedCornerShape(bottomStart = 2.dp, bottomEnd = 2.dp))
        ) {
            Text(
                text = "$progress%",
                fontSize = 7.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.Black,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp),
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
