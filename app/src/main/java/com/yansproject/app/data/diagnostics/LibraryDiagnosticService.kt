package com.yansproject.app.data.diagnostics

import android.content.Context
import android.util.Log
import com.yansproject.app.ui.BabData
import com.yansproject.app.ui.JuzData
import com.yansproject.app.ui.KitabFull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.FileNotFoundException
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Diagnostic Severity classification for Library JSON asset operations.
 */
enum class DiagnosticSeverity {
    INFO,
    SUCCESS,
    WARNING,
    ERROR
}

/**
 * Structured Diagnostic Log Entry recording granular loading events of assets/library/ JSON files.
 */
data class LibraryDiagnosticLog(
    val id: String = UUID.randomUUID().toString().take(8),
    val timestamp: Long = System.currentTimeMillis(),
    val targetPath: String,
    val operation: String,
    val severity: DiagnosticSeverity,
    val message: String,
    val details: String? = null,
    val fileSize: Long = -1L
) {
    val formattedTime: String
        get() {
            val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
}

/**
 * Comprehensive summary report produced after inspecting assets/library/.
 */
data class LibraryDiagnosticReport(
    val timestamp: Long = System.currentTimeMillis(),
    val totalAssetsChecked: Int = 0,
    val successCount: Int = 0,
    val warningCount: Int = 0,
    val errorCount: Int = 0,
    val booksDiscovered: Int = 0,
    val juzDiscovered: Int = 0,
    val babCount: Int = 0,
    val status: String = "HEALTHY", // "HEALTHY", "WARNINGS_FOUND", "ERRORS_HANDLED"
    val errorSummaryList: List<String> = emptyList()
) {
    val isHealthy: Boolean
        get() = errorCount == 0 && totalAssetsChecked > 0
}

/**
 * Central Diagnostic Logs Service to monitor and safeguard the loading of JSON files in 'assets/library/'.
 * Prevents crashes caused by malformed metadata, missing keys, or absent files, while providing
 * full visibility through observable diagnostic telemetry and audit logging.
 */
object LibraryDiagnosticService {

    private const val TAG = "LibraryDiagnostics"
    private const val MAX_LOG_BUFFER = 250
    private const val LIBRARY_ROOT = "library"
    private const val MANIFEST_PATH = "library/manifest.json"

    private val logBuffer = ArrayDeque<LibraryDiagnosticLog>(MAX_LOG_BUFFER)
    private val _diagnosticLogs = MutableStateFlow<List<LibraryDiagnosticLog>>(emptyList())
    val diagnosticLogs: StateFlow<List<LibraryDiagnosticLog>> = _diagnosticLogs.asStateFlow()

    private val _lastReport = MutableStateFlow<LibraryDiagnosticReport?>(null)
    val lastReport: StateFlow<LibraryDiagnosticReport?> = _lastReport.asStateFlow()

    @Synchronized
    fun log(
        targetPath: String,
        operation: String,
        severity: DiagnosticSeverity,
        message: String,
        details: String? = null,
        fileSize: Long = -1L
    ) {
        val entry = LibraryDiagnosticLog(
            targetPath = targetPath,
            operation = operation,
            severity = severity,
            message = message,
            details = details,
            fileSize = fileSize
        )

        if (logBuffer.size >= MAX_LOG_BUFFER) {
            logBuffer.pollFirst()
        }
        logBuffer.addLast(entry)
        _diagnosticLogs.value = logBuffer.toList().reversed()

        val logMsg = "[$operation] $targetPath: $message" + if (details != null) " | Details: $details" else ""
        when (severity) {
            DiagnosticSeverity.INFO -> Log.i(TAG, logMsg)
            DiagnosticSeverity.SUCCESS -> Log.d(TAG, logMsg)
            DiagnosticSeverity.WARNING -> Log.w(TAG, logMsg)
            DiagnosticSeverity.ERROR -> Log.e(TAG, logMsg)
        }
    }

    @Synchronized
    fun clearLogs() {
        logBuffer.clear()
        _diagnosticLogs.value = emptyList()
        Log.i(TAG, "Diagnostic log buffer cleared.")
    }

    /**
     * Safely reads an asset file into String with strict error capture and telemetry logging.
     * Never throws exceptions up the call stack; returns null on failure.
     */
    fun safeReadAsset(context: Context, relativePath: String, operationName: String = "READ"): String? {
        return try {
            val assetManager = context.assets
            val inputStream = assetManager.open(relativePath)
            val bytes = inputStream.readBytes()
            inputStream.close()

            val size = bytes.size.toLong()
            if (size == 0L) {
                log(
                    targetPath = relativePath,
                    operation = operationName,
                    severity = DiagnosticSeverity.WARNING,
                    message = "Berkas aset kosong (0 bytes).",
                    fileSize = 0L
                )
                return ""
            }

            val content = String(bytes, Charsets.UTF_8)
            log(
                targetPath = relativePath,
                operation = operationName,
                severity = DiagnosticSeverity.SUCCESS,
                message = "Berhasil membaca aset ($size bytes).",
                fileSize = size
            )
            content
        } catch (e: FileNotFoundException) {
            log(
                targetPath = relativePath,
                operation = operationName,
                severity = DiagnosticSeverity.ERROR,
                message = "Berkas aset tidak ditemukan (FileNotFound).",
                details = e.localizedMessage ?: "File missing from APK assets"
            )
            null
        } catch (e: IOException) {
            log(
                targetPath = relativePath,
                operation = operationName,
                severity = DiagnosticSeverity.ERROR,
                message = "Kegagalan I/O saat membaca berkas aset.",
                details = e.localizedMessage ?: e.javaClass.simpleName
            )
            null
        } catch (e: Exception) {
            log(
                targetPath = relativePath,
                operation = operationName,
                severity = DiagnosticSeverity.ERROR,
                message = "Kesalahan tak terduga saat membaca aset.",
                details = "${e.javaClass.simpleName}: ${e.message}"
            )
            null
        }
    }

    /**
     * Safely parses JSON string with full diagnostic capture.
     * Prevents JSONException from escalating to fatal runtime crash.
     */
    fun safeParseJson(jsonString: String, targetPath: String, operationName: String = "JSON_PARSE"): JSONObject? {
        if (jsonString.isBlank()) {
            log(
                targetPath = targetPath,
                operation = operationName,
                severity = DiagnosticSeverity.WARNING,
                message = "String JSON kosong atau hanya berisi spasi."
            )
            return null
        }
        return try {
            val json = JSONObject(jsonString)
            log(
                targetPath = targetPath,
                operation = operationName,
                severity = DiagnosticSeverity.SUCCESS,
                message = "Sintaks JSON valid (${json.length()} keys root)."
            )
            json
        } catch (e: JSONException) {
            log(
                targetPath = targetPath,
                operation = operationName,
                severity = DiagnosticSeverity.ERROR,
                message = "Sintaks JSON rusak (Malformed JSON).",
                details = e.localizedMessage ?: e.message
            )
            null
        } catch (e: Exception) {
            log(
                targetPath = targetPath,
                operation = operationName,
                severity = DiagnosticSeverity.ERROR,
                message = "Kesalahan fatal parsing JSON.",
                details = "${e.javaClass.simpleName}: ${e.message}"
            )
            null
        }
    }

    /**
     * Primary robust, crash-proof loader for Kitab Library assets.
     * Guarantees graceful fallback at every level:
     * - Manifest failure: falls back to asset directory scanning or emergency built-in catalog
     * - Book metadata failure: creates fallback metadata using folder name
     * - Juz file missing/malformed: skips or creates placeholder without aborting remaining juz/books
     */
    fun loadLibraryDataSafely(context: Context): List<KitabFull> {
        val booksList = mutableListOf<KitabFull>()
        var successFiles = 0
        var warningCount = 0
        var errorCount = 0
        var totalJuz = 0
        var totalBab = 0
        val issues = mutableListOf<String>()

        log(
            targetPath = MANIFEST_PATH,
            operation = "LIBRARY_INIT",
            severity = DiagnosticSeverity.INFO,
            message = "Memulai inisialisasi dan pemuatan koleksi berkas JSON 'assets/library/'."
        )

        val manifestRaw = safeReadAsset(context, MANIFEST_PATH, "MANIFEST_READ")
        val manifestJson = manifestRaw?.let { safeParseJson(it, MANIFEST_PATH, "MANIFEST_PARSE") }

        val bookFoldersToLoad = mutableListOf<Triple<String, String, String>>() // id, title, folder

        if (manifestJson != null) {
            val booksArray = manifestJson.optJSONArray("books")
            if (booksArray != null && booksArray.length() > 0) {
                successFiles++
                log(
                    targetPath = MANIFEST_PATH,
                    operation = "MANIFEST_VALIDATION",
                    severity = DiagnosticSeverity.SUCCESS,
                    message = "Manifest valid. Ditemukan ${booksArray.length()} entri kitab."
                )
                for (i in 0 until booksArray.length()) {
                    val bObj = booksArray.optJSONObject(i)
                    if (bObj != null) {
                        val id = bObj.optString("id", "kitab_${i + 1}")
                        val folder = bObj.optString("folder", id)
                        val title = bObj.optString("title", folder.replace("_", " ").uppercase())
                        bookFoldersToLoad.add(Triple(id, title, folder))
                    }
                }
            } else {
                warningCount++
                val issue = "Manifest 'books' array kosong atau null. Mencoba dynamic asset directory scan."
                issues.add(issue)
                log(
                    targetPath = MANIFEST_PATH,
                    operation = "MANIFEST_VALIDATION",
                    severity = DiagnosticSeverity.WARNING,
                    message = issue
                )
            }
        } else {
            errorCount++
            val issue = "Manifest 'library/manifest.json' gagal dimuat atau korup. Mengaktifkan fallback directory scanner."
            issues.add(issue)
            log(
                targetPath = MANIFEST_PATH,
                operation = "MANIFEST_FALLBACK",
                severity = DiagnosticSeverity.WARNING,
                message = issue
            )
        }

        // Fallback discovery if manifest was missing or empty
        if (bookFoldersToLoad.isEmpty()) {
            try {
                val filesInLibrary = context.assets.list(LIBRARY_ROOT) ?: emptyArray()
                for (name in filesInLibrary) {
                    if (name.startsWith("kitab_") || !name.contains(".")) {
                        bookFoldersToLoad.add(Triple(name, name.replace("_", " ").uppercase(), name))
                    }
                }
                log(
                    targetPath = LIBRARY_ROOT,
                    operation = "DIR_SCAN",
                    severity = DiagnosticSeverity.INFO,
                    message = "Dynamic discovery menemukan ${bookFoldersToLoad.size} folder calon kitab."
                )
            } catch (e: Exception) {
                errorCount++
                val issue = "Gagal memindai sub-direktori 'library/': ${e.message}"
                issues.add(issue)
                log(
                    targetPath = LIBRARY_ROOT,
                    operation = "DIR_SCAN_ERROR",
                    severity = DiagnosticSeverity.ERROR,
                    message = issue
                )
            }
        }

        // Process each book folder
        for ((bookId, bookTitleFallback, folder) in bookFoldersToLoad) {
            val metaPath = "$LIBRARY_ROOT/$folder/metadata.json"
            val metaRaw = safeReadAsset(context, metaPath, "METADATA_READ")
            val metaJson = metaRaw?.let { safeParseJson(it, metaPath, "METADATA_PARSE") }

            val title: String
            val subtitle: String
            val quote: String
            val muqaddimah: String
            val penutup: String
            val juzFilesArray: JSONArray?

            if (metaJson != null) {
                successFiles++
                title = metaJson.optString("title").ifBlank { bookTitleFallback }
                subtitle = metaJson.optString("subtitle", "Digital Manuscript Archive")
                quote = metaJson.optString("quote", "")
                muqaddimah = metaJson.optString("muqaddimah", "")
                penutup = metaJson.optString("penutup", "")
                juzFilesArray = metaJson.optJSONArray("juzFiles")

                log(
                    targetPath = metaPath,
                    operation = "METADATA_VALIDATION",
                    severity = DiagnosticSeverity.SUCCESS,
                    message = "Metadata kitab '$title' ($folder) sukses diverifikasi. Juz terdaftar: ${juzFilesArray?.length() ?: 0}."
                )
            } else {
                warningCount++
                val issue = "Metadata '$metaPath' tidak tersedia atau rusak. Menggunakan profil fallback aman."
                issues.add(issue)
                log(
                    targetPath = metaPath,
                    operation = "METADATA_FALLBACK",
                    severity = DiagnosticSeverity.WARNING,
                    message = issue
                )
                title = bookTitleFallback
                subtitle = "Digital Manuscript Archive (Fallback)"
                quote = "Manuskrip arsip digital YANSPROJECT.ID."
                muqaddimah = "Arsip manuskrip digital."
                penutup = "Dokumentasi arsip."
                juzFilesArray = null
            }

            // Process Juz files
            val juzList = mutableListOf<JuzData>()
            val declaredFiles = mutableListOf<String>()

            if (juzFilesArray != null) {
                for (j in 0 until juzFilesArray.length()) {
                    val fn = juzFilesArray.optString(j)
                    if (fn.isNotBlank()) declaredFiles.add(fn)
                }
            } else {
                // If juzFiles array was missing, attempt to check juz_01.json .. juz_10.json
                try {
                    val folderContents = context.assets.list("$LIBRARY_ROOT/$folder") ?: emptyArray()
                    for (f in folderContents) {
                        if (f.startsWith("juz_") && f.endsWith(".json")) {
                            declaredFiles.add(f)
                        }
                    }
                    declaredFiles.sort()
                } catch (e: Exception) {
                    Log.w(TAG, "Tidak dapat list direktori $folder: ${e.message}")
                }
            }

            for (juzFileName in declaredFiles) {
                val juzPath = "$LIBRARY_ROOT/$folder/$juzFileName"
                val juzRaw = safeReadAsset(context, juzPath, "JUZ_READ")
                val juzJson = juzRaw?.let { safeParseJson(it, juzPath, "JUZ_PARSE") }

                if (juzJson != null) {
                    successFiles++
                    val juzId = juzJson.optString("id", juzFileName.removeSuffix(".json"))
                    val juzTitle = juzJson.optString("title", "JUZ ${juzList.size + 1}")
                    val babArray = juzJson.optJSONArray("babList")
                    val babList = mutableListOf<BabData>()

                    if (babArray != null) {
                        for (k in 0 until babArray.length()) {
                            val babObj = babArray.optJSONObject(k)
                            if (babObj != null) {
                                val babId = babObj.optString("id", "${juzId}_bab_${k + 1}")
                                val babTitle = babObj.optString("title", "BAB ${k + 1}")
                                val contentArray = babObj.optJSONArray("content")
                                val contentList = mutableListOf<String>()

                                if (contentArray != null) {
                                    for (l in 0 until contentArray.length()) {
                                        val line = contentArray.optString(l)
                                        if (line.isNotBlank()) {
                                            contentList.add(line)
                                        }
                                    }
                                }
                                babList.add(BabData(babId, babTitle, contentList))
                                totalBab++
                            }
                        }
                    }

                    juzList.add(JuzData(juzId, juzTitle, babList))
                    totalJuz++
                    log(
                        targetPath = juzPath,
                        operation = "JUZ_VALIDATION",
                        severity = DiagnosticSeverity.SUCCESS,
                        message = "Juz '$juzTitle' ($juzFileName) berhasil dimuat (${babList.size} bab)."
                    )
                } else {
                    errorCount++
                    val issue = "Berkas Juz '$juzPath' gagal dibaca atau korup. Menambahkan placeholder untuk proteksi UI."
                    issues.add(issue)
                    log(
                        targetPath = juzPath,
                        operation = "JUZ_FALLBACK",
                        severity = DiagnosticSeverity.ERROR,
                        message = issue
                    )
                    // Add safe placeholder so UI navigation doesn't crash on invalid index
                    val placeholderId = juzFileName.removeSuffix(".json")
                    juzList.add(
                        JuzData(
                            id = placeholderId,
                            title = "JUZ ($juzFileName - Berkas Tidak Tersedia)",
                            babList = listOf(
                                BabData(
                                    id = "${placeholderId}_err",
                                    title = "Pemberitahuan Diagnostik",
                                    content = listOf(
                                        "Berkas manuskrip '$juzPath' saat ini tidak dapat dimuat atau sedang dalam perbaikan format JSON.",
                                        "Silakan periksa log diagnostik pada menu System Health untuk rincian pelacakan berkas."
                                    )
                                )
                            )
                        )
                    )
                }
            }

            booksList.add(
                KitabFull(
                    id = bookId,
                    title = title,
                    subtitle = subtitle,
                    quote = quote,
                    muqaddimah = muqaddimah,
                    penutup = penutup,
                    juzList = juzList,
                    folder = folder
                )
            )
        }

        // If list is still completely empty (e.g. fresh empty assets), provide emergency book to guarantee zero crash
        if (booksList.isEmpty()) {
            warningCount++
            val issue = "Tidak ada kitab yang berhasil dimuat dari assets. Menyediakan arsip darurat standar."
            issues.add(issue)
            log(
                targetPath = LIBRARY_ROOT,
                operation = "EMERGENCY_CATALOG",
                severity = DiagnosticSeverity.WARNING,
                message = issue
            )
            booksList.add(
                KitabFull(
                    id = "kitab_default",
                    title = "YANSPROJECT.ID ARCHIVE",
                    subtitle = "Digital Manuscript Archive",
                    quote = "Tak semua perjalanan meminta untuk dikenang. Sebagian hanya berharap agar tak pernah hilang.",
                    muqaddimah = "Manuskrip arsip digital YANSPROJECT.ID.",
                    penutup = "Dokumentasi arsip resmi.",
                    juzList = emptyList(),
                    folder = "kitab_01"
                )
            )
        }

        val totalAssets = successFiles + warningCount + errorCount
        val overallStatus = when {
            errorCount > 0 -> "ERRORS_HANDLED"
            warningCount > 0 -> "WARNINGS_FOUND"
            else -> "HEALTHY"
        }

        val report = LibraryDiagnosticReport(
            timestamp = System.currentTimeMillis(),
            totalAssetsChecked = totalAssets,
            successCount = successFiles,
            warningCount = warningCount,
            errorCount = errorCount,
            booksDiscovered = booksList.size,
            juzDiscovered = totalJuz,
            babCount = totalBab,
            status = overallStatus,
            errorSummaryList = issues
        )
        _lastReport.value = report

        log(
            targetPath = LIBRARY_ROOT,
            operation = "LIBRARY_SUMMARY",
            severity = if (report.isHealthy) DiagnosticSeverity.SUCCESS else DiagnosticSeverity.WARNING,
            message = "Pemuatan perpustakaan selesai: Status=${report.status}, Kitab=${booksList.size}, Juz=$totalJuz, Bab=$totalBab, Aset Sukses=$successFiles, Eror Ditangani=$errorCount."
        )

        return booksList
    }

    /**
     * Executes an explicit, asynchronous audit of all JSON files in assets/library/
     * without changing application state. Useful for diagnostic screens.
     */
    suspend fun runDiagnosticAudit(context: Context): LibraryDiagnosticReport {
        return withContext(Dispatchers.IO) {
            log(
                targetPath = LIBRARY_ROOT,
                operation = "MANUAL_AUDIT_START",
                severity = DiagnosticSeverity.INFO,
                message = "Pemeriksaan diagnostik manual assets/library/ dipicu oleh pengguna."
            )
            // Re-run loading cycle safely which updates telemetry and returns fresh report
            loadLibraryDataSafely(context)
            _lastReport.value ?: LibraryDiagnosticReport(status = "HEALTHY")
        }
    }

    /**
     * Generates a readable plain-text export of all recent diagnostic logs.
     */
    fun exportLogsAsText(): String {
        val sb = StringBuilder()
        sb.append("=== YANSPROJECT.ID LIBRARY ASSETS DIAGNOSTIC LOGS ===\n")
        sb.append("Waktu Ekspor: ${SimpleDateFormat("dd MMMM yyyy HH:mm:ss", Locale("id", "ID")).format(Date())}\n")
        val report = _lastReport.value
        if (report != null) {
            sb.append("Status Integritas: ${report.status}\n")
            sb.append("Total Aset: ${report.totalAssetsChecked} | Sukses: ${report.successCount} | Warning: ${report.warningCount} | Error: ${report.errorCount}\n")
            sb.append("Kitab: ${report.booksDiscovered} | Juz: ${report.juzDiscovered} | Bab: ${report.babCount}\n")
        }
        sb.append("----------------------------------------------------\n")
        for (item in _diagnosticLogs.value) {
            sb.append("[${item.formattedTime}] [${item.severity}] [${item.operation}]\n")
            sb.append("Target: ${item.targetPath}\n")
            sb.append("Pesan: ${item.message}\n")
            if (item.details != null) {
                sb.append("Detail: ${item.details}\n")
            }
            sb.append("\n")
        }
        return sb.toString()
    }
}
