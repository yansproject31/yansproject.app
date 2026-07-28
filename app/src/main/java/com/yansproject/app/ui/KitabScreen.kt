package com.yansproject.app.ui

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yansproject.app.ui.theme.*
import org.json.JSONObject

// --- DATA STRUCTURES ---
data class KitabFull(
    val id: String,
    val title: String,
    val subtitle: String,
    val quote: String,
    val muqaddimah: String,
    val penutup: String,
    val juzList: List<JuzData>,
    val folder: String
)

data class JuzData(
    val id: String,
    val title: String,
    val babList: List<BabData>
)

data class BabData(
    val id: String,
    val title: String,
    val content: List<String>
)

data class SearchResult(
    val book: KitabFull,
    val type: String, // "BAB" or "MUQADDIMAH" or "PENUTUP"
    val juzIndex: Int, // -1 for muqaddimah / penutup
    val babIndex: Int, // -1 for muqaddimah / penutup
    val title: String,
    val snippet: String
)

@Composable
fun rememberKitabData(context: Context): List<KitabFull> {
    return remember(context) {
        val list = mutableListOf<KitabFull>()
        try {
            val manifestStr = context.assets.open("library/manifest.json").bufferedReader().use { it.readText() }
            val manifestJson = JSONObject(manifestStr)
            val booksArray = manifestJson.getJSONArray("books")
            for (i in 0 until booksArray.length()) {
                val bookObj = booksArray.getJSONObject(i)
                val id = bookObj.getString("id")
                val folder = bookObj.getString("folder")
                
                val metaStr = context.assets.open("library/$folder/metadata.json").bufferedReader().use { it.readText() }
                val metaJson = JSONObject(metaStr)
                val title = metaJson.getString("title")
                val subtitle = metaJson.getString("subtitle")
                val quote = metaJson.getString("quote")
                val muqaddimah = metaJson.getString("muqaddimah")
                val penutup = metaJson.getString("penutup")
                
                val juzFiles = metaJson.getJSONArray("juzFiles")
                val juzList = mutableListOf<JuzData>()
                for (j in 0 until juzFiles.length()) {
                    val juzFile = juzFiles.getString(j)
                    val juzStr = context.assets.open("library/$folder/$juzFile").bufferedReader().use { it.readText() }
                    val juzJson = JSONObject(juzStr)
                    val juzId = juzJson.getString("id")
                    val juzTitle = juzJson.getString("title")
                    
                    val babArray = juzJson.getJSONArray("babList")
                    val babList = mutableListOf<BabData>()
                    for (k in 0 until babArray.length()) {
                        val babObj = babArray.getJSONObject(k)
                        val babId = babObj.getString("id")
                        val babTitle = babObj.getString("title")
                        val contentArray = babObj.getJSONArray("content")
                        val content = mutableListOf<String>()
                        for (l in 0 until contentArray.length()) {
                            content.add(contentArray.getString(l))
                        }
                        babList.add(BabData(babId, babTitle, content))
                    }
                    juzList.add(JuzData(juzId, juzTitle, babList))
                }
                list.add(KitabFull(id, title, subtitle, quote, muqaddimah, penutup, juzList, folder))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        list
    }
}

@Composable
fun KitabScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val books = rememberKitabData(context)
    
    if (books.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ShadowBlack),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = AgedGold)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Memuat Perpustakaan...", color = TextMuted, fontSize = 14.sp)
            }
        }
        return
    }

    // Currently viewing first book by default
    var selectedBookId by remember { mutableStateOf("kitab_01") }
    val currentBook = remember(selectedBookId, books) {
        books.find { it.id == selectedBookId } ?: books.first()
    }

    // --- SHARED PREFERENCES LOCAL STORAGE ---
    val prefs = remember(context) { context.getSharedPreferences("kitab_prefs", Context.MODE_PRIVATE) }
    
    var bookmarkedBabs by remember {
        mutableStateOf(prefs.getStringSet("bookmarks", emptySet()) ?: emptySet())
    }
    
    var completedBabs by remember {
        mutableStateOf(prefs.getStringSet("completed", emptySet()) ?: emptySet())
    }

    var lastOpenedBabTitle by remember {
        mutableStateOf(prefs.getString("last_opened_title", "Belum dibaca") ?: "Belum dibaca")
    }
    
    var lastOpenedJuzIndex by remember { mutableStateOf(prefs.getInt("last_opened_juz", -2)) } // -2 means never opened, -1 muqaddimah, 999 penutup
    var lastOpenedBabIndex by remember { mutableStateOf(prefs.getInt("last_opened_bab", -2)) }

    // Navigation State
    // -2: Cover Page, -1: Muqaddimah, 999: Penutup, 0 to N: JUZ indices
    var currentJuzView by remember { mutableStateOf(-2) }
    var currentBabView by remember { mutableStateOf(0) }
    
    // Intercept system back button/gesture to return to cover page when reading or switch back to main book
    BackHandler(enabled = currentJuzView != -2 || selectedBookId != "kitab_01") {
        if (selectedBookId != "kitab_01") {
            selectedBookId = "kitab_01"
            currentJuzView = -2
        } else {
            currentJuzView = -2
        }
    }
    
    // UI preferences
    var readerTextSize by remember { mutableStateOf(prefs.getFloat("text_size", 16f)) }

    // Search and Quick Jump
    var showSearchOverlay by remember { mutableStateOf(false) }
    var showTocOverlay by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val totalBabCount = remember(currentBook) {
        currentBook.juzList.sumOf { it.babList.size }
    }
    
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
            .putInt("last_opened_juz", juzIdx)
            .putInt("last_opened_bab", babIdx)
            .putString("last_opened_title", title)
            .apply()
    }

    fun saveTextSize(size: Float) {
        readerTextSize = size
        prefs.edit().putFloat("text_size", size).apply()
    }

    // --- NAVIGATION HELPERS ---
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
        showSearchOverlay = false
    }

    fun navigateNext() {
        if (currentJuzView == -1) {
            // From Muqaddimah to Juz 0, Bab 0
            if (currentBook.juzList.isNotEmpty() && currentBook.juzList[0].babList.isNotEmpty()) {
                openReader(0, 0)
            } else {
                openReader(999, 0)
            }
        } else if (currentJuzView == 999) {
            // Already at Penutup, do nothing or go back to Cover
            currentJuzView = -2
        } else {
            val curJuz = currentBook.juzList.getOrNull(currentJuzView)
            if (curJuz != null) {
                if (currentBabView < curJuz.babList.size - 1) {
                    openReader(currentJuzView, currentBabView + 1)
                } else {
                    // Next Juz
                    if (currentJuzView < currentBook.juzList.size - 1) {
                        openReader(currentJuzView + 1, 0)
                    } else {
                        // Go to Penutup
                        openReader(999, 0)
                    }
                }
            }
        }
    }

    fun navigatePrev() {
        if (currentJuzView == 999) {
            // From Penutup to Last Juz, Last Bab
            val lastJuzIdx = currentBook.juzList.size - 1
            if (lastJuzIdx >= 0) {
                val lastJuz = currentBook.juzList[lastJuzIdx]
                openReader(lastJuzIdx, lastJuz.babList.size - 1)
            } else {
                openReader(-1, 0)
            }
        } else if (currentJuzView == -1) {
            // Already at Muqaddimah, go to cover
            currentJuzView = -2
        } else {
            if (currentBabView > 0) {
                openReader(currentJuzView, currentBabView - 1)
            } else {
                // Prev Juz
                if (currentJuzView > 0) {
                    val prevJuzIdx = currentJuzView - 1
                    val prevJuz = currentBook.juzList[prevJuzIdx]
                    openReader(prevJuzIdx, prevJuz.babList.size - 1)
                } else {
                    // Go to Muqaddimah
                    openReader(-1, 0)
                }
            }
        }
    }

    // --- SEARCH ENGINE LOGIC ---
    val searchResults = remember(searchQuery, currentBook) {
        if (searchQuery.trim().length < 2) emptyList<SearchResult>()
        else {
            val query = searchQuery.trim().lowercase()
            val results = mutableListOf<SearchResult>()

            // 1. Check Muqaddimah
            if (currentBook.muqaddimah.lowercase().contains(query)) {
                val idx = currentBook.muqaddimah.lowercase().indexOf(query)
                val snippet = getSnippet(currentBook.muqaddimah, idx, query.length)
                results.add(SearchResult(currentBook, "MUQADDIMAH", -1, 0, "Muqaddimah", snippet))
            }

            // 2. Check Penutup
            if (currentBook.penutup.lowercase().contains(query)) {
                val idx = currentBook.penutup.lowercase().indexOf(query)
                val snippet = getSnippet(currentBook.penutup, idx, query.length)
                results.add(SearchResult(currentBook, "PENUTUP", 999, 0, "Penutup", snippet))
            }

            // 3. Check JUZ & BAB
            currentBook.juzList.forEachIndexed { jIdx, juz ->
                if (juz.title.lowercase().contains(query)) {
                    results.add(SearchResult(currentBook, "BAB", jIdx, 0, juz.title, "Daftar Juz: ${juz.title}"))
                }
                juz.babList.forEachIndexed { bIdx, bab ->
                    if (bab.title.lowercase().contains(query)) {
                        results.add(SearchResult(currentBook, "BAB", jIdx, bIdx, bab.title, "Judul Bab: ${bab.title}"))
                    }
                    bab.content.forEach { paragraph ->
                        if (paragraph.lowercase().contains(query)) {
                            val idx = paragraph.lowercase().indexOf(query)
                            val snippet = getSnippet(paragraph, idx, query.length)
                            results.add(SearchResult(currentBook, "BAB", jIdx, bIdx, bab.title, snippet))
                        }
                    }
                }
            }
            results
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ShadowBlack)
    ) {
        Crossfade(
            targetState = currentJuzView,
            animationSpec = tween(500),
            label = "ScreenTransition"
        ) { page ->
            if (selectedBookId == "kitab_02") {
                // --- EXCLUSIVE RISALAH MADAD AULIYA TRAILER PAGE ---
                RisalahMadadAuliyaTrailerPage(
                    books = books,
                    onSwitchBook = { bookId ->
                        selectedBookId = bookId
                        currentJuzView = -2
                    },
                    onBackToDashboard = { viewModel.setTab(AppTab.DASHBOARD) }
                )
            } else if (page == -2) {
                // --- COVER PAGE VIEW ---
                CoverPage(
                    book = currentBook,
                    progressPercent = progressPercent,
                    completedCount = completedCount,
                    totalBabCount = totalBabCount,
                    lastOpenedTitle = lastOpenedBabTitle,
                    hasLastOpened = lastOpenedJuzIndex != -2,
                    onStartReading = { openReader(-1, 0) },
                    onContinueReading = {
                        if (lastOpenedJuzIndex != -2) {
                            openReader(lastOpenedJuzIndex, lastOpenedBabIndex)
                        } else {
                            openReader(-1, 0)
                        }
                    },
                    onOpenToc = { showTocOverlay = true },
                    onOpenSearch = { showSearchOverlay = true },
                    onBackToDashboard = { viewModel.setTab(AppTab.DASHBOARD) },
                    books = books,
                    onSwitchBook = { bookId ->
                        selectedBookId = bookId
                        currentJuzView = -2
                    }
                )
            } else {
                // --- READER PAGE VIEW ---
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

                ReaderPage(
                    babId = currentBabId,
                    title = currentTitle,
                    content = currentContent,
                    juzTitle = when (page) {
                        -1 -> "MUQADDIMAH KITAB"
                        999 -> "PENUTUP KITAB"
                        else -> currentBook.juzList.getOrNull(page)?.title ?: ""
                    },
                    isBookmarked = bookmarkedBabs.contains(currentBabId),
                    isCompleted = completedBabs.contains(currentBabId),
                    textSize = readerTextSize,
                    onToggleBookmark = { saveBookmark(currentBabId, !bookmarkedBabs.contains(currentBabId)) },
                    onToggleCompleted = { saveCompleted(currentBabId, !completedBabs.contains(currentBabId)) },
                    onNext = { navigateNext() },
                    onPrev = { navigatePrev() },
                    onOpenToc = { showTocOverlay = true },
                    onOpenSearch = { showSearchOverlay = true },
                    onIncreaseTextSize = { if (readerTextSize < 24f) saveTextSize(readerTextSize + 1.5f) },
                    onDecreaseTextSize = { if (readerTextSize > 12f) saveTextSize(readerTextSize - 1.5f) },
                    onBackToCover = { currentJuzView = -2 }
                )
            }
        }

        // --- OVERLAY: TABLE OF CONTENTS (DAFTAR ISI) ---
        AnimatedVisibility(
            visible = showTocOverlay,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            TocBottomSheet(
                book = currentBook,
                bookmarkedBabs = bookmarkedBabs,
                completedBabs = completedBabs,
                currentJuz = currentJuzView,
                currentBab = currentBabView,
                onSelectMuqaddimah = { openReader(-1, 0) },
                onSelectPenutup = { openReader(999, 0) },
                onSelectBab = { jIdx, bIdx -> openReader(jIdx, bIdx) },
                onDismiss = { showTocOverlay = false }
            )
        }

        // --- OVERLAY: SEARCH MODAL ---
        AnimatedVisibility(
            visible = showSearchOverlay,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -100 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -100 }),
            modifier = Modifier.fillMaxSize()
        ) {
            SearchDialog(
                searchQuery = searchQuery,
                onQueryChange = { searchQuery = it },
                results = searchResults,
                onSelectResult = { res ->
                    openReader(res.juzIndex, res.babIndex)
                },
                onDismiss = { showSearchOverlay = false }
            )
        }
    }
}

// --- HELPER FUNCTION FOR SEARCH SNIPPET ---
private fun getSnippet(fullText: String, keywordIndex: Int, keywordLength: Int): String {
    val start = (keywordIndex - 30).coerceAtLeast(0)
    val end = (keywordIndex + keywordLength + 40).coerceAtMost(fullText.length)
    var snippet = fullText.substring(start, end).replace("\n", " ")
    if (start > 0) snippet = "...$snippet"
    if (end < fullText.length) snippet = "$snippet..."
    return snippet
}

// ==========================================
// COMPOSABLE: PROCEDURAL BOOK COVER
// ==========================================
@Composable
fun BookCoverProcedural(title: String, subtitle: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth(0.62f)
            .aspectRatio(0.68f)
            .shadow(16.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkGrey),
        border = BorderStroke(2.5.dp, AgedGold)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Elegant Sacred Geometric Borders on Canvas
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                
                // Outer gold bounding line
                drawRect(
                    color = AgedGold.copy(alpha = 0.35f),
                    topLeft = Offset(12f, 12f),
                    size = Size(w - 24f, h - 24f),
                    style = Stroke(width = 1.dp.toPx())
                )
                
                // Corner accents
                val offset = 22f
                val len = 35f
                // Top-Left
                drawLine(AgedGold, Offset(offset, offset), Offset(offset + len, offset), 2.5f)
                drawLine(AgedGold, Offset(offset, offset), Offset(offset, offset + len), 2.5f)
                // Top-Right
                drawLine(AgedGold, Offset(w - offset, offset), Offset(w - offset - len, offset), 2.5f)
                drawLine(AgedGold, Offset(w - offset, offset), Offset(w - offset, offset + len), 2.5f)
                // Bottom-Left
                drawLine(AgedGold, Offset(offset, h - offset), Offset(offset + len, h - offset), 2.5f)
                drawLine(AgedGold, Offset(offset, h - offset), Offset(offset, h - offset - len), 2.5f)
                // Bottom-Right
                drawLine(AgedGold, Offset(w - offset, h - offset), Offset(w - offset - len, h - offset), 2.5f)
                drawLine(AgedGold, Offset(w - offset, h - offset), Offset(w - offset, h - offset - len), 2.5f)
                
                // Central Concentric Spiritual Spheres
                drawCircle(
                    color = AgedGold.copy(alpha = 0.12f),
                    radius = w * 0.26f,
                    center = Offset(w / 2f, h * 0.46f),
                    style = Stroke(width = 1.5f)
                )
                drawCircle(
                    color = AgedGold.copy(alpha = 0.3f),
                    radius = w * 0.22f,
                    center = Offset(w / 2f, h * 0.46f),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Seal
                Text(
                    text = "MANUSKRIP RESMI",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = AgedGold,
                    letterSpacing = 1.5.sp
                )
                
                // Title Group
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 35.dp)
                ) {
                    Text(
                        text = title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        lineHeight = 24.sp,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = subtitle,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        color = AgedGold,
                        textAlign = TextAlign.Center,
                        letterSpacing = 1.sp
                    )
                }
                
                // Seal Logo Accent
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .border(1.dp, AgedGold, RoundedCornerShape(4.dp))
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.VerifiedUser,
                        contentDescription = null,
                        tint = AgedGold,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// ==========================================
// COMPOSABLE: COVER VIEW SCREEN
// ==========================================
@Composable
fun CoverPage(
    book: KitabFull,
    progressPercent: Int,
    completedCount: Int,
    totalBabCount: Int,
    lastOpenedTitle: String,
    hasLastOpened: Boolean,
    onStartReading: () -> Unit,
    onContinueReading: () -> Unit,
    onOpenToc: () -> Unit,
    onOpenSearch: () -> Unit,
    onBackToDashboard: () -> Unit,
    books: List<KitabFull>,
    onSwitchBook: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ShadowBlack)
            .statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(24.dp)
    ) {
        // Top Nav header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackToDashboard) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = AgedGold)
                }
                Text(
                    text = "KITAB DIGITAL",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = AgedGold,
                    letterSpacing = 1.5.sp
                )
                IconButton(onClick = onOpenSearch) {
                    Icon(imageVector = Icons.Outlined.Search, contentDescription = "Cari", tint = AgedGold)
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // Procedural Cover Illustration
        item {
            BookCoverProcedural(title = book.title, subtitle = book.subtitle)
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Quote section
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.FormatQuote,
                    contentDescription = null,
                    tint = AgedGold.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = book.quote,
                    fontSize = 13.sp,
                    fontStyle = FontStyle.Italic,
                    color = TextLight,
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
            Spacer(modifier = Modifier.height(28.dp))
        }

        // Stats Card (Silent Luxury styling)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkGrey),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, BorderGrey),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "INFORMASI MANUSKRIP",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AgedGold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Total Bagian", fontSize = 11.sp, color = TextMuted)
                            Text("${book.juzList.size} JUZ", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Column {
                            Text("Total Risalah", fontSize = 11.sp, color = TextMuted)
                            Text("$totalBabCount BAB", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Column {
                            Text("Waktu Baca", fontSize = 11.sp, color = TextMuted)
                            Text("~45 Menit", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = BorderGrey, thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(14.dp))

                    Text("Terakhir Dibaca", fontSize = 11.sp, color = TextMuted)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = lastOpenedTitle,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (hasLastOpened) AgedGold else Color.White
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Progress Membaca", fontSize = 11.sp, color = TextMuted)
                        Text("$progressPercent% ($completedCount dari $totalBabCount BAB)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AgedGold)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { progressPercent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = AgedGold,
                        trackColor = BorderGrey
                    )
                }
            }
            Spacer(modifier = Modifier.height(28.dp))
        }

        // Action Buttons
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onContinueReading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("continue_reading_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = AgedGold),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(imageVector = Icons.Outlined.MenuBook, contentDescription = null, tint = DarkGrey)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (hasLastOpened) "Lanjutkan Membaca" else "Mulai Membaca",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkGrey
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onOpenToc,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AgedGold),
                        border = BorderStroke(1.dp, AgedGold),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Outlined.FormatListNumbered, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Daftar Isi", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onStartReading,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = BorderStroke(1.dp, BorderGrey),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Outlined.History, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Dari Awal", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item {
            Spacer(modifier = Modifier.height(28.dp))
            DigitalManuscriptCollection(
                currentBookId = book.id,
                books = books,
                onSelectBook = onSwitchBook
            )
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

// ==========================================
// COMPOSABLE: READER VIEW
// ==========================================
@Composable
fun ReaderPage(
    babId: String,
    title: String,
    content: List<String>,
    juzTitle: String,
    isBookmarked: Boolean,
    isCompleted: Boolean,
    textSize: Float,
    onToggleBookmark: () -> Unit,
    onToggleCompleted: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onOpenToc: () -> Unit,
    onOpenSearch: () -> Unit,
    onIncreaseTextSize: () -> Unit,
    onDecreaseTextSize: () -> Unit,
    onBackToCover: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("kitab_prefs", Context.MODE_PRIVATE) }
    val scrollState = rememberScrollState()

    // Restore scroll position when babId changes
    LaunchedEffect(babId) {
        val savedScroll = prefs.getInt("scroll_pos_$babId", 0)
        scrollState.scrollTo(savedScroll)
    }

    // Save scroll position dynamically as user scrolls
    LaunchedEffect(scrollState.value) {
        prefs.edit().putInt("scroll_pos_$babId", scrollState.value).apply()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ShadowBlack)
            .statusBarsPadding()
    ) {
        // Toolbar Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkGrey)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBackToCover) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cover", tint = AgedGold)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Text(
                        text = juzTitle,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = AgedGold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(140.dp)
                    )
                }
            }

            // Controls
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDecreaseTextSize) {
                    Text("A-", fontSize = 12.sp, color = AgedGold, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onIncreaseTextSize) {
                    Text("A+", fontSize = 14.sp, color = AgedGold, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onOpenSearch) {
                    Icon(imageVector = Icons.Outlined.Search, contentDescription = "Cari", tint = AgedGold)
                }
                IconButton(onClick = onToggleBookmark) {
                    Icon(
                        imageVector = if (isBookmarked) Icons.Outlined.BookmarkAdded else Icons.Outlined.BookmarkBorder,
                        contentDescription = "Bookmark",
                        tint = if (isBookmarked) AgedGold else TextMuted
                    )
                }
                IconButton(onClick = onOpenToc) {
                    Icon(imageVector = Icons.Outlined.FormatListNumbered, contentDescription = "Indeks", tint = AgedGold)
                }
            }
        }

        // Long Scrollable Manuscript Reader
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Sacred Header Decoration
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(1.dp)
                    .background(AgedGold.copy(alpha = 0.3f))
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                fontSize = (textSize + 4).sp,
                fontWeight = FontWeight.Black,
                color = AgedGold,
                textAlign = TextAlign.Center,
                lineHeight = (textSize + 10).sp,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(1.dp)
                    .background(AgedGold.copy(alpha = 0.3f))
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Body Paragraphs
            val firstBodyParagraphIdx = content.indexOfFirst { p ->
                val trimmed = p.trim()
                !(trimmed.startsWith("\"") && trimmed.endsWith("\"") || 
                  trimmed.startsWith("“") && trimmed.endsWith("”") || 
                  trimmed == "Bismillāhirraḥmānirraḥīm" || 
                  trimmed == "YANSPROJECT.ID HISTORY" || 
                  trimmed == "PESAN KHATIMAH" || 
                  trimmed == "FILOSOFI WARNA" || 
                  trimmed == "FILOSOFI TIPOGRAFI" || 
                  trimmed == "FILOSOFI TEKSTUR" || 
                  trimmed == "FILOSOFI ORNAMEN MELINGKAR" || 
                  trimmed == "FILOSOFI PENEMPATAN SABLON" || 
                  trimmed == "FILOSOFI RUANG KOSONG" || 
                  trimmed == "KHATIMAH VISUAL" || 
                  trimmed == "SHADOW BLACK\n#0A0A0A" || 
                  trimmed == "BLOOD RED" || 
                  trimmed == "WHITE VOID" || 
                  trimmed == "AGED GOLD" || 
                  trimmed == "TYPOGRAPHY GOTHIC" || 
                  trimmed == "KUFI & GEOMETRI")
            }

            content.forEachIndexed { idx, paragraph ->
                val trimmed = paragraph.trim()
                val isQuote = (trimmed.startsWith("\"") && trimmed.endsWith("\"")) || (trimmed.startsWith("“") && trimmed.endsWith("”"))
                
                if (trimmed == "Bismillāhirraḥmānirraḥīm") {
                    Text(
                        text = paragraph,
                        fontSize = (textSize + 5).sp,
                        fontWeight = FontWeight.Bold,
                        color = AgedGold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp)
                    )
                } else if (trimmed == "YANSPROJECT.ID HISTORY") {
                    Text(
                        text = paragraph,
                        fontSize = (textSize + 2).sp,
                        fontWeight = FontWeight.Bold,
                        color = AgedGold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 28.dp, bottom = 8.dp)
                    )
                } else if (trimmed == "PESAN KHATIMAH" || 
                           trimmed == "FILOSOFI WARNA" || 
                           trimmed == "FILOSOFI TIPOGRAFI" || 
                           trimmed == "FILOSOFI TEKSTUR" || 
                           trimmed == "FILOSOFI ORNAMEN MELINGKAR" || 
                           trimmed == "FILOSOFI PENEMPATAN SABLON" || 
                           trimmed == "FILOSOFI RUANG KOSONG" || 
                           trimmed == "KHATIMAH VISUAL") {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp)
                    ) {
                        Box(modifier = Modifier.width(60.dp).height(1.dp).background(AgedGold.copy(alpha = 0.4f)))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = paragraph,
                            fontSize = (textSize + 1.5).sp,
                            fontWeight = FontWeight.Bold,
                            color = AgedGold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(modifier = Modifier.width(60.dp).height(1.dp).background(AgedGold.copy(alpha = 0.4f)))
                    }
                } else if (trimmed == "SHADOW BLACK\n#0A0A0A" || 
                           trimmed == "BLOOD RED" || 
                           trimmed == "WHITE VOID" || 
                           trimmed == "AGED GOLD" || 
                           trimmed == "TYPOGRAPHY GOTHIC" || 
                           trimmed == "KUFI & GEOMETRI") {
                    Text(
                        text = paragraph,
                        fontSize = (textSize + 1).sp,
                        fontWeight = FontWeight.Bold,
                        color = AgedGold,
                        textAlign = TextAlign.Left,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, bottom = 6.dp)
                    )
                } else if (isQuote) {
                    // Quotation Block Accent
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp)
                            .background(AgedGold.copy(alpha = 0.05f))
                            .border(BorderStroke(1.dp, AgedGold.copy(alpha = 0.2f)), RoundedCornerShape(4.dp))
                            .padding(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FormatQuote,
                            contentDescription = null,
                            tint = AgedGold,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = paragraph,
                            fontSize = (textSize + 1).sp,
                            fontWeight = FontWeight.Medium,
                            fontStyle = FontStyle.Italic,
                            color = AgedGold,
                            lineHeight = (textSize + 8).sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    val isFirstBody = idx == firstBodyParagraphIdx
                    if (isFirstBody && paragraph.isNotEmpty()) {
                        // Drop Cap Style for the First Body Paragraph
                        val firstChar = paragraph.first().toString()
                        val remainingText = paragraph.substring(1)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(end = 12.dp, top = 4.dp)
                                    .background(AgedGold.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                                    .border(BorderStroke(1.dp, AgedGold), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = firstChar,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Black,
                                    color = AgedGold,
                                    lineHeight = 28.sp
                                )
                            }
                            Text(
                                text = remainingText,
                                fontSize = textSize.sp,
                                fontWeight = FontWeight.Normal,
                                color = TextLight,
                                lineHeight = (textSize + 8).sp,
                                textAlign = TextAlign.Justify,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        // Standard Body Paragraph
                        Text(
                            text = paragraph,
                            fontSize = textSize.sp,
                            fontWeight = FontWeight.Normal,
                            color = TextLight,
                            lineHeight = (textSize + 8).sp,
                            textAlign = TextAlign.Justify,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Mark as completed Section
            Card(
                colors = CardDefaults.cardColors(containerColor = if (isCompleted) AlertGreen.copy(alpha = 0.08f) else DarkGrey),
                border = BorderStroke(1.dp, if (isCompleted) AlertGreen.copy(alpha = 0.3f) else BorderGrey),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleCompleted() }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (isCompleted) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (isCompleted) AlertGreen else AgedGold,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isCompleted) "Risalah Selesai Dibaca" else "Tandai Selesai Membaca",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isCompleted) AlertGreen else Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(60.dp))
        }

        // Bottom Footer Navigation (Prev / Next)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkGrey)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onPrev,
                colors = ButtonDefaults.textButtonColors(contentColor = AgedGold)
            ) {
                Icon(imageVector = Icons.Outlined.ChevronLeft, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Sebelumnya", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Text(
                text = "YANSPROJECT.ID",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = AgedGold.copy(alpha = 0.6f),
                letterSpacing = 1.sp
            )

            TextButton(
                onClick = onNext,
                colors = ButtonDefaults.textButtonColors(contentColor = AgedGold)
            ) {
                Text("Berikutnya", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(4.dp))
                Icon(imageVector = Icons.Outlined.ChevronRight, contentDescription = null)
            }
        }
    }
}

// ==========================================
// COMPOSABLE: INDEX / TABLE OF CONTENTS
// ==========================================
@Composable
fun TocBottomSheet(
    book: KitabFull,
    bookmarkedBabs: Set<String>,
    completedBabs: Set<String>,
    currentJuz: Int,
    currentBab: Int,
    onSelectMuqaddimah: () -> Unit,
    onSelectPenutup: () -> Unit,
    onSelectBab: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable { onDismiss() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .align(Alignment.BottomCenter)
                .background(DarkGrey, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .border(BorderStroke(1.dp, BorderGrey), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .clickable(enabled = false) { /* Prevent click through */ }
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DAFTAR ISI & INDEKS",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = AgedGold,
                    letterSpacing = 1.sp
                )
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Outlined.Close, contentDescription = "Tutup", tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. Muqaddimah
                item {
                    val isMuqActive = currentJuz == -1
                    val muqId = "${book.id}_muqaddimah"
                    val isCompleted = completedBabs.contains(muqId)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isMuqActive) AgedGold.copy(alpha = 0.12f) else Color.Transparent)
                            .border(
                                BorderStroke(
                                    1.dp,
                                    if (isMuqActive) AgedGold else BorderGrey.copy(alpha = 0.5f)
                                ), RoundedCornerShape(8.dp)
                            )
                            .clickable { onSelectMuqaddimah() }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.PlayLesson,
                                contentDescription = null,
                                tint = if (isMuqActive) AgedGold else TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "MUQADDIMAH (PENGANTAR)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isMuqActive) AgedGold else Color.White
                            )
                        }
                        if (isCompleted) {
                            Icon(imageVector = Icons.Outlined.CheckCircle, contentDescription = "Selesai", tint = AlertGreen, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // 2. Juz & Bab Hierarchy
                book.juzList.forEachIndexed { jIdx, juz ->
                    item {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = juz.title,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AgedGold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                        )
                    }

                    items(juz.babList.size) { bIdx ->
                        val bab = juz.babList[bIdx]
                        val isBabActive = currentJuz == jIdx && currentBab == bIdx
                        val isCompleted = completedBabs.contains(bab.id)
                        val isBookmarked = bookmarkedBabs.contains(bab.id)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isBabActive) AgedGold.copy(alpha = 0.08f) else CardDarkCard)
                                .clickable { onSelectBab(jIdx, bIdx) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .background(
                                            if (isBabActive) AgedGold else BorderGrey,
                                            RoundedCornerShape(4.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${bIdx + 1}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isBabActive) DarkGrey else Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = bab.title,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isBabActive) AgedGold else TextLight,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (isBookmarked) {
                                    Icon(imageVector = Icons.Outlined.Bookmark, contentDescription = "Tandai", tint = AgedGold, modifier = Modifier.size(14.dp))
                                }
                                if (isCompleted) {
                                    Icon(imageVector = Icons.Outlined.CheckCircle, contentDescription = "Selesai", tint = AlertGreen, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                // 3. Penutup
                item {
                    Spacer(modifier = Modifier.height(14.dp))
                    val isPenutupActive = currentJuz == 999
                    val penutupId = "${book.id}_penutup"
                    val isCompleted = completedBabs.contains(penutupId)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isPenutupActive) AgedGold.copy(alpha = 0.12f) else Color.Transparent)
                            .border(
                                BorderStroke(
                                    1.dp,
                                    if (isPenutupActive) AgedGold else BorderGrey.copy(alpha = 0.5f)
                                ), RoundedCornerShape(8.dp)
                            )
                            .clickable { onSelectPenutup() }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.DoneAll,
                                contentDescription = null,
                                tint = if (isPenutupActive) AgedGold else TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "PENUTUP KITAB (KHATIMAH)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isPenutupActive) AgedGold else Color.White
                            )
                        }
                        if (isCompleted) {
                            Icon(imageVector = Icons.Outlined.CheckCircle, contentDescription = "Selesai", tint = AlertGreen, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// COMPOSABLE: SEARCH DIALOG
// ==========================================
@Composable
fun SearchDialog(
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    results: List<SearchResult>,
    onSelectResult: (SearchResult) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f))
            .clickable { onDismiss() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .align(Alignment.TopCenter)
                .background(DarkGrey)
                .border(BorderStroke(1.dp, BorderGrey))
                .clickable(enabled = false) { /* Prevent click through */ }
                .padding(24.dp)
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PENCARIAN MANUSKRIP",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = AgedGold,
                    letterSpacing = 1.sp
                )
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Outlined.Close, contentDescription = "Tutup", tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = onQueryChange,
                placeholder = { Text("Ketik kata kunci (misal: sunyi, sebat, malam...)", color = TextMuted) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AgedGold,
                    unfocusedBorderColor = BorderGrey,
                    focusedLabelColor = AgedGold,
                    unfocusedLabelColor = TextMuted,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                singleLine = true,
                leadingIcon = { Icon(imageVector = Icons.Outlined.Search, contentDescription = null, tint = AgedGold) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (results.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(imageVector = Icons.Outlined.FindInPage, contentDescription = null, tint = TextMuted, modifier = Modifier.size(44.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.trim().length < 2) "Masukkan minimal 2 karakter" else "Tidak ditemukan hasil pencarian.",
                            fontSize = 12.sp,
                            color = TextMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Text(
                    text = "Ditemukan ${results.size} kecocokan naskah:",
                    fontSize = 11.sp,
                    color = TextMuted,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(results) { res ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CardDarkCard),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectResult(res)
                                    onDismiss()
                                }
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = res.title,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AgedGold
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(AgedGold.copy(alpha = 0.1f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = res.type,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AgedGold
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = res.snippet,
                                    fontSize = 11.sp,
                                    color = TextLight,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DigitalManuscriptCollection(
    currentBookId: String,
    books: List<KitabFull>,
    onSelectBook: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkGrey),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BorderGrey),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "DIGITAL MANUSCRIPT COLLECTION",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = AgedGold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(14.dp))

            books.forEach { b ->
                val isActive = b.id == currentBookId
                val statusText = when (b.id) {
                    "kitab_01" -> "Published"
                    "kitab_02" -> "Draft Ongoing"
                    else -> "Coming Soon"
                }
                val statusColor = when (b.id) {
                    "kitab_01" -> HighlightSoftCyan
                    "kitab_02" -> AgedGold
                    else -> TextMuted
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isActive) ShadowBlack.copy(alpha = 0.5f) else Color.Transparent)
                        .border(
                            width = 0.5.dp,
                            color = if (isActive) AgedGold.copy(alpha = 0.3f) else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable(enabled = !isActive) { onSelectBook(b.id) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = b.title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isActive) AgedGold else Color.White
                            )
                            if (isActive) {
                                Icon(
                                    imageVector = Icons.Outlined.CheckCircle,
                                    contentDescription = "Active",
                                    tint = AgedGold,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                        Text(b.subtitle, fontSize = 10.sp, color = TextMuted)
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(statusColor.copy(alpha = 0.12f))
                            .border(0.5.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = statusText.uppercase(),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun RisalahMadadAuliyaTrailerPage(
    books: List<KitabFull>,
    onSwitchBook: (String) -> Unit,
    onBackToDashboard: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ShadowBlack)
            .statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(24.dp)
    ) {
        // Top Nav header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackToDashboard) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = AgedGold)
                }
                Text(
                    text = "DIGITAL MANUSCRIPT ARCHIVE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AgedGold,
                    letterSpacing = 1.5.sp
                )
                Box(modifier = Modifier.size(48.dp)) // empty space to balance back button
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // Manuscript Procedural Cover Illustration
        item {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                BookCoverProcedural(title = "RISALAH MADAD AULIYA", subtitle = "Digital Manuscript Archive")
                
                // Overlay "DRAFT ONGOING" watermark badge diagonally
                Box(
                    modifier = Modifier
                        .offset(y = 80.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(AgedGold)
                        .border(1.dp, ShadowBlack, RoundedCornerShape(4.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "DRAFT ONGOING",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = ShadowBlack,
                        letterSpacing = 1.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(36.dp))
        }

        // Quote section
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.FormatQuote,
                    contentDescription = null,
                    tint = AgedGold.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Langkah Sunyi, Terkenang Abadi.",
                    fontSize = 14.sp,
                    fontStyle = FontStyle.Italic,
                    color = TextLight,
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
            Spacer(modifier = Modifier.height(28.dp))
        }

        // Status & Progress Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkGrey),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, AgedGold.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "STATUS ARSIP NASKAH",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AgedGold,
                            letterSpacing = 1.sp
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(AgedGold.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "COMING SOON",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = AgedGold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Dalam Penyusunan Naskah",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Naskah ini sedang dalam fase penulisan dan penyusunan.",
                        fontSize = 11.sp,
                        color = TextMuted,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = BorderGrey, thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Progress Penulisan", fontSize = 11.sp, color = TextMuted)
                            Text("13% Drafted", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AgedGold)
                        }
                        Column {
                            Text("Estimasi Rilis", fontSize = 11.sp, color = TextMuted)
                            Text("Insya Allah", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Column {
                            Text("Kategori", fontSize = 11.sp, color = TextMuted)
                            Text("Risalah", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { 0.13f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = AgedGold,
                        trackColor = BorderGrey
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // Author Note
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkGrey),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, BorderGrey),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = null,
                            tint = AgedGold,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "CATATAN PENULIS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AgedGold,
                            letterSpacing = 1.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Risalah ini tidak lahir dalam satu malam.\n\nIa tumbuh perlahan, mengikuti langkah yang Allah bukakan; melewati doa, penantian, pertemuan, kehilangan, serta perjalanan-perjalanan yang tidak seluruhnya dapat dituliskan.\n\nKarena itu, sebagian halaman di dalam kitab ini masih dibiarkan kosong.\nBukan karena belum ada yang ingin disampaikan.\nMelainkan karena perjalanan yang melahirkannya masih terus berjalan.\n\nSemoga Allah menjaga niat, langkah, dan setiap kalimat yang kelak menjadi saksi perjalanan ini.",
                        fontSize = 11.sp,
                        color = TextLight,
                        lineHeight = 16.sp,
                        textAlign = TextAlign.Justify
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // Teaser Preview Box
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkGrey),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, BorderGrey),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.AutoStories,
                            contentDescription = null,
                            tint = AgedGold,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "PREVIEW MANUSKRIP (TEASER)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AgedGold,
                            letterSpacing = 1.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ShadowBlack)
                            .border(0.5.dp, BorderGrey, RoundedCornerShape(8.dp))
                            .padding(16.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "BAB I: LANGKAH SUNYI",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = AgedGold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "□□□□□□□□□□□□□□□□□□□□□□□□□□□□□□\n□□□□□□□□□□□□□□□□□□□□□□□□□□□□□□\n□□□□□□□□□□□□□□□□□□□□□□□□□□□□□□",
                                fontSize = 10.sp,
                                color = AgedGold.copy(alpha = 0.25f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                                letterSpacing = 2.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "\"Tidak semua perjalanan dimulai dengan langkah yang terdengar.\nSebagiannya lahir dalam diam.\nKetika manusia mulai belajar berjalan tanpa sibuk menghitung siapa yang melihatnya.\nTanpa tergesa menunggu pengakuan. Tanpa menjadikan pujian sebagai tujuan.\nSebab ada perjalanan yang semakin sunyi, justru semakin dekat kepada maknanya.\n\nDan barangkali...\nsetiap jejak yang lahir dari ketulusan tidak pernah benar-benar hilang.\nIa hanya berpindah: dari langkah, menjadi doa.\nDari doa, menjadi cahaya.\nLalu hidup perlahan di dalam hati manusia...\"",
                                fontSize = 11.sp,
                                fontStyle = FontStyle.Italic,
                                color = TextLight,
                                lineHeight = 16.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(28.dp))
        }

        // Digital Manuscript Collection
        item {
            DigitalManuscriptCollection(
                currentBookId = "kitab_02",
                books = books,
                onSelectBook = onSwitchBook
            )
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}
