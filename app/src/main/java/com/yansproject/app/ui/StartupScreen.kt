package com.yansproject.app.ui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.Keep
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.yansproject.app.data.*
import com.yansproject.app.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@Keep
class StartupViewModel : ViewModel() {

    private val _state = MutableStateFlow(BootstrapState.NOT_STARTED)
    val state: StateFlow<BootstrapState> = _state

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress

    private val _progressText = MutableStateFlow("Inisialisasi sistem...")
    val progressText: StateFlow<String> = _progressText

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _isRecovering = MutableStateFlow(false)
    val isRecovering: StateFlow<Boolean> = _isRecovering

    private val _recoveryStatusMessage = MutableStateFlow<String?>(null)
    val recoveryStatusMessage: StateFlow<String?> = _recoveryStatusMessage

    fun startBootstrap(context: Context, db: AppDatabase, firestore: FirebaseFirestore) {
        viewModelScope.launch {
            _errorMessage.value = null
            _recoveryStatusMessage.value = null
            val metadataManager = SyncMetadataManager.getInstance(context)
            _state.value = BootstrapState.DOWNLOADING
            
            try {
                // 1. Execute deterministic startup recovery pipeline
                _progressText.value = "Menjalankan Startup Pipeline..."
                _progress.value = 0.1f
                val startupResult = AppStartupManager.getInstance(context).executeStartupSequence(db) { stage ->
                    _progressText.value = "Pipeline: ${stage.javaClass.simpleName}"
                }
                
                if (startupResult is StartupStage.StartupFailed) {
                    throw Exception("Startup pipeline failed: ${startupResult.reason}")
                }

                // 2. Execute Bootstrap Engine
                EnterpriseBootstrapEngine.executeFullBootstrap(
                    context = context,
                    db = db,
                    firestore = firestore,
                    metadataManager = metadataManager,
                    onProgress = { text, value ->
                        _progressText.value = text
                        _progress.value = 0.2f + (value * 0.8f)
                        _state.value = metadataManager.getState()
                    }
                )
                _state.value = BootstrapState.FINISHED
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Kegagalan sinkronisasi/startup yang tidak diketahui."
                metadataManager.setState(BootstrapState.FAILED)
                _state.value = BootstrapState.FAILED
            }
        }
    }

    fun executeSelfHealing(context: Context, db: AppDatabase, firestore: FirebaseFirestore) {
        viewModelScope.launch {
            _isRecovering.value = true
            _recoveryStatusMessage.value = "Menjalankan diagnostik & pemulihan database mandiri..."
            try {
                IntegrityManager.getInstance(context).executeRecoveryMode(db)
                val recoveryResult = RecoveryManager.getInstance(context).attemptDatabaseRecovery(db)
                IntegrityManager.getInstance(context).markStartupSuccessful()
                
                if (recoveryResult is RecoveryResult.Success) {
                    _recoveryStatusMessage.value = "Pemulihan mandiri sukses! Memulai ulang bootstrap..."
                } else if (recoveryResult is RecoveryResult.Failure) {
                    _recoveryStatusMessage.value = "Perbaikan parsial: ${recoveryResult.reason}"
                }
                
                kotlinx.coroutines.delay(1000)
                _isRecovering.value = false
                startBootstrap(context, db, firestore)
            } catch (e: Exception) {
                _recoveryStatusMessage.value = "Gagal pemulihan: ${e.localizedMessage}"
                _isRecovering.value = false
            }
        }
    }

    fun executeCloudResync(context: Context, db: AppDatabase, firestore: FirebaseFirestore) {
        viewModelScope.launch {
            _isRecovering.value = true
            _recoveryStatusMessage.value = "Mereset metadata & mempersiapkan sinkronisasi ulang cloud..."
            try {
                SyncMetadataManager.getInstance(context).reset()
                CacheManager.getInstance(context).clearAll()
                IntegrityManager.getInstance(context).markStartupSuccessful()
                _recoveryStatusMessage.value = "Memulai sinkronisasi ulang dari cloud..."
                kotlinx.coroutines.delay(800)
                _isRecovering.value = false
                startBootstrap(context, db, firestore)
            } catch (e: Exception) {
                _recoveryStatusMessage.value = "Gagal reset cloud: ${e.localizedMessage}"
                _isRecovering.value = false
            }
        }
    }

    fun executeClearCache(context: Context, db: AppDatabase, firestore: FirebaseFirestore) {
        viewModelScope.launch {
            _isRecovering.value = true
            _recoveryStatusMessage.value = "Membersihkan cache & mereset tracker kegagalan..."
            try {
                CacheManager.getInstance(context).clearAll()
                IntegrityManager.getInstance(context).markStartupSuccessful()
                _recoveryStatusMessage.value = "Cache dibersihkan. Memulai ulang pipeline..."
                kotlinx.coroutines.delay(800)
                _isRecovering.value = false
                startBootstrap(context, db, firestore)
            } catch (e: Exception) {
                _recoveryStatusMessage.value = "Gagal bersihkan cache: ${e.localizedMessage}"
                _isRecovering.value = false
            }
        }
    }

    fun executeLocalBackupRestore(context: Context, uri: android.net.Uri, db: AppDatabase, firestore: FirebaseFirestore) {
        viewModelScope.launch {
            _isRecovering.value = true
            _recoveryStatusMessage.value = "Membaca & mendekripsi file cadangan lokal..."
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    _recoveryStatusMessage.value = "Gagal membuka stream file cadangan."
                    _isRecovering.value = false
                    return@launch
                }
                
                val backupManager = LocalEncryptedBackupManager(context)
                val restored = backupManager.importBackup(inputStream)
                if (restored) {
                    IntegrityManager.getInstance(context).markStartupSuccessful()
                    _recoveryStatusMessage.value = "Database berhasil dipulihkan dari cadangan! Mengulang bootstrap..."
                    kotlinx.coroutines.delay(1000)
                    _isRecovering.value = false
                    startBootstrap(context, db, firestore)
                } else {
                    _recoveryStatusMessage.value = "Gagal mendekripsi atau memvalidasi header file cadangan SQLite."
                    _isRecovering.value = false
                }
            } catch (e: Exception) {
                _recoveryStatusMessage.value = "Restorasi gagal: ${e.localizedMessage}"
                _isRecovering.value = false
            }
        }
    }
}

@Composable
@Keep
fun StartupScreen(
    viewModel: StartupViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val firestore = remember { FirebaseFirestore.getInstance() }
    val state by viewModel.state.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val progressText by viewModel.progressText.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val isRecovering by viewModel.isRecovering.collectAsState()
    val recoveryStatusMessage by viewModel.recoveryStatusMessage.collectAsState()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            viewModel.executeLocalBackupRestore(context, uri, db, firestore)
        }
    }

    var hasFinishedTriggered by remember { mutableStateOf(false) }
    val currentMetadataState = remember(state) {
        SyncMetadataManager.getInstance(context).getState()
    }
    val isBootstrapped = currentMetadataState == BootstrapState.FINISHED || state == BootstrapState.FINISHED

    val infiniteTransition = rememberInfiniteTransition(label = "terminal_cursor")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                0.7f at 500
            },
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursor_alpha"
    )

    LaunchedEffect(state, currentMetadataState) {
        if (isBootstrapped) {
            val isDbEmpty = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                db.catalogDao().getCatalogsList().isEmpty() &&
                db.stockDao().getAllStockList().isEmpty() &&
                db.invoiceDao().getInvoicesList().isEmpty() &&
                db.projectDao().getAllProjectsList().isEmpty()
            }
            if (isDbEmpty && currentMetadataState == BootstrapState.FINISHED) {
                SyncMetadataManager.getInstance(context).reset()
                viewModel.startBootstrap(context, db, firestore)
            } else if (!hasFinishedTriggered) {
                hasFinishedTriggered = true
                EnterpriseSyncEngine.startRealtimeSyncListeners(context)
                onFinished()
            }
        } else if ((state == BootstrapState.NOT_STARTED || state == BootstrapState.FAILED) && errorMessage == null) {
            val isDbEmpty = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                db.catalogDao().getCatalogsList().isEmpty() &&
                db.stockDao().getAllStockList().isEmpty() &&
                db.invoiceDao().getInvoicesList().isEmpty() &&
                db.projectDao().getAllProjectsList().isEmpty()
            }
            if (currentMetadataState == BootstrapState.FINISHED && !isDbEmpty) {
                if (!hasFinishedTriggered) {
                    hasFinishedTriggered = true
                    EnterpriseSyncEngine.startRealtimeSyncListeners(context)
                    onFinished()
                }
            } else {
                viewModel.startBootstrap(context, db, firestore)
            }
        }
    }

    if (isBootstrapped) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ShadowBlack)
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ShadowBlack)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Terminal,
                contentDescription = "Terminal Icon",
                tint = AgedGold,
                modifier = Modifier.size(64.dp)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "YANSPROJECT.ID ERP",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = AgedGold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.5.sp
            )
            
            Text(
                text = "Enterprise Node Bootstrap Pipeline",
                fontSize = 12.sp,
                color = HighlightSoftCyan,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(40.dp))

            if (errorMessage != null || state == BootstrapState.FAILED) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x22EF4444))
                            .border(1.dp, Color(0x88EF4444), RoundedCornerShape(12.dp))
                            .padding(16.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.Shield,
                                    contentDescription = "Shield Icon",
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "MODUL PEMULIHAN SISTEM (RECOVERY MODE)",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFEF4444),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = errorMessage ?: "Terdeteksi anomali pada inisialisasi startup. Data lokal tetap aman & terisolasi.",
                                color = TextLight,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (recoveryStatusMessage != null) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Status: $recoveryStatusMessage",
                                    color = AlertGreen,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    if (isRecovering) {
                        CircularProgressIndicator(color = AgedGold, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Memproses tindakan pemulihan...",
                            color = HighlightSoftCyan,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    } else {
                        Text(
                            text = "OPSI PEMULIHAN TANPA DESTRUKSI DATA:",
                            color = AgedGold,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Button 1: Perbaiki Schema & Tabel
                        OutlinedButton(
                            onClick = { viewModel.executeSelfHealing(context, db, firestore) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AlertGreen),
                            border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(AlertGreen)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Outlined.Build, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("1. Perbaiki Skema & Tabel (Self-Healing)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text("Diagnostik SQLite & perbaikan integritas otomatis", fontSize = 10.sp, color = TextMuted)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Button 2: Sinkronisasi Ulang Cloud
                        OutlinedButton(
                            onClick = { viewModel.executeCloudResync(context, db, firestore) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = HighlightSoftCyan),
                            border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(HighlightSoftCyan)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Outlined.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("2. Reset Metadata & Resync Cloud", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text("Unduh ulang data aman dari Firestore tanpa hapus lokal", fontSize = 10.sp, color = TextMuted)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Button 3: Bersihkan Cache
                        OutlinedButton(
                            onClick = { viewModel.executeClearCache(context, db, firestore) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AgedGold),
                            border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(AgedGold)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Outlined.Storage, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("3. Bersihkan Cache & Tracker Crash", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text("Hapus berkas sementara & reset penghitung kegagalan", fontSize = 10.sp, color = TextMuted)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Button 4: Restorasi dari Cadangan Lokal
                        OutlinedButton(
                            onClick = { filePickerLauncher.launch("*/*") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF60A5FA)),
                            border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF60A5FA))),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("4. Restorasi dari Cadangan Terenkripsi", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text("Pilih berkas cadangan lokal (.db/.yansbak) secara atomik", fontSize = 10.sp, color = TextMuted)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Button 5: Coba Lagi Pipeline
                        Button(
                            onClick = { viewModel.startBootstrap(context, db, firestore) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = AgedGold),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Outlined.Refresh, contentDescription = null, tint = ShadowBlack, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("COBA LAGI PIPELINE STARTUP", color = ShadowBlack, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkTealSurface)
                        .padding(16.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.CloudDownload,
                                contentDescription = "Sync",
                                tint = HighlightSoftCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "yans_node_bootstrap_progress.log",
                                color = HighlightSoftCyan,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp),
                            color = HighlightSoftCyan,
                            trackColor = Color(0xFF163536)
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "> $progressText",
                                color = Color.White,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                            
                            Box(
                                modifier = Modifier
                                    .size(8.dp, 14.dp)
                                    .background(HighlightSoftCyan.copy(alpha = cursorAlpha))
                            )
                        }
                    }
                }
            }
        }
    }
}
