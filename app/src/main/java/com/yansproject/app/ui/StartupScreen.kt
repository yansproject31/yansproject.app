package com.yansproject.app.ui

import android.content.Context
import androidx.annotation.Keep
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.ErrorOutline
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

    fun startBootstrap(context: Context, db: AppDatabase, firestore: FirebaseFirestore) {
        viewModelScope.launch {
            _errorMessage.value = null
            val metadataManager = SyncMetadataManager.getInstance(context)
            _state.value = BootstrapState.DOWNLOADING
            
            try {
                EnterpriseBootstrapEngine.executeFullBootstrap(
                    context = context,
                    db = db,
                    firestore = firestore,
                    metadataManager = metadataManager,
                    onProgress = { text, value ->
                        _progressText.value = text
                        _progress.value = value
                        _state.value = metadataManager.getState()
                    }
                )
                _state.value = BootstrapState.FINISHED
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Kegagalan sinkronisasi yang tidak diketahui."
                _state.value = BootstrapState.NOT_STARTED
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

    LaunchedEffect(state) {
        if (state == BootstrapState.FINISHED) {
            val isDbEmpty = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                db.catalogDao().getCatalogsList().isEmpty() &&
                db.stockDao().getAllStockList().isEmpty() &&
                db.invoiceDao().getInvoicesList().isEmpty() &&
                db.projectDao().getAllProjectsList().isEmpty()
            }
            if (isDbEmpty) {
                SyncMetadataManager.getInstance(context).reset()
                viewModel.startBootstrap(context, db, firestore)
            } else {
                EnterpriseSyncEngine.startRealtimeSyncListeners(context)
                onFinished()
            }
        } else if (state == BootstrapState.NOT_STARTED && errorMessage == null) {
            val isDbEmpty = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                db.catalogDao().getCatalogsList().isEmpty() &&
                db.stockDao().getAllStockList().isEmpty() &&
                db.invoiceDao().getInvoicesList().isEmpty() &&
                db.projectDao().getAllProjectsList().isEmpty()
            }
            val metadataState = SyncMetadataManager.getInstance(context).getState()
            if (metadataState == BootstrapState.FINISHED && !isDbEmpty) {
                EnterpriseSyncEngine.startRealtimeSyncListeners(context)
                onFinished()
            } else {
                viewModel.startBootstrap(context, db, firestore)
            }
        }
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

            if (errorMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x22EF4444))
                        .border(1.dp, Color(0x88EF4444), RoundedCornerShape(8.dp))
                        .padding(16.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.ErrorOutline,
                            contentDescription = "Error Icon",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "BOOTSTRAP PIPELINE FAILED",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFEF4444),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorMessage ?: "",
                            color = Color.White,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { viewModel.startBootstrap(context, db, firestore) },
                    colors = ButtonDefaults.buttonColors(containerColor = AgedGold),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "COBA LAGI / RETRY PIPELINE",
                        color = ShadowBlack,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
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
