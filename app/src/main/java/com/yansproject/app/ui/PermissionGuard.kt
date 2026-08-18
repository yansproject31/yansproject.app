package com.yansproject.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.yansproject.app.ui.theme.AgedGold
import com.yansproject.app.ui.theme.CardGrey
import com.yansproject.app.ui.theme.DarkGrey
import com.yansproject.app.ui.theme.TextMuted

enum class FeaturePermissionState {
    REQUIRED,
    OPTIONAL,
    DENIED,
    FEATURE_DISABLED
}

object FeaturePermissionGuard {

    fun checkBluetoothPermissionState(context: Context): FeaturePermissionState {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return FeaturePermissionState.OPTIONAL
        }
        val connectGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        val scanGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        return if (connectGranted && scanGranted) {
            FeaturePermissionState.OPTIONAL
        } else {
            FeaturePermissionState.REQUIRED
        }
    }

    fun checkStoragePermissionState(context: Context): FeaturePermissionState {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) {
            // Android 11+ uses MediaStore / SAF without needing legacy storage permission
            return FeaturePermissionState.OPTIONAL
        }
        val readGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        return if (readGranted) FeaturePermissionState.OPTIONAL else FeaturePermissionState.REQUIRED
    }
}

@Composable
fun PermissionGuard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    // Only POST_NOTIFICATIONS is requested on general app startup (if Android 13+)
    // Bluetooth and Storage permissions are feature-gated and requested only when entering those features!
    val startupPermissions = remember {
        val list = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        list.toTypedArray()
    }

    fun checkGranted(ctx: Context, permissions: Array<String>): Boolean {
        return permissions.all { perm ->
            ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    var isStartupPermissionsGranted by remember {
        mutableStateOf(checkGranted(context, startupPermissions))
    }

    var userBypassedPrompt by remember { mutableStateOf(false) }

    val startupPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        isStartupPermissionsGranted = checkGranted(context, startupPermissions)
        if (!isStartupPermissionsGranted) {
            userBypassedPrompt = true
        }
    }

    LaunchedEffect(Unit) {
        if (!isStartupPermissionsGranted && startupPermissions.isNotEmpty()) {
            startupPermissionLauncher.launch(startupPermissions)
        }
    }

    if (isStartupPermissionsGranted || userBypassedPrompt || startupPermissions.isEmpty()) {
        content()
    } else {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(DarkGrey)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .testTag("permission_rationale_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardGrey),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(AgedGold.copy(alpha = 0.12f), RoundedCornerShape(50))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.NotificationsActive,
                            contentDescription = "Notifikasi Aktif",
                            tint = AgedGold,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Izin Notifikasi Realtime",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Aplikasi YANSPROJECT.ID ERP membutuhkan izin notifikasi untuk mengirimkan siaran broadcast Owner dan pembaruan real-time status pesanan.",
                        fontSize = 13.sp,
                        color = TextMuted,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            if (startupPermissions.isNotEmpty()) {
                                startupPermissionLauncher.launch(startupPermissions)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AgedGold,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("request_permission_button")
                    ) {
                        Text(
                            text = "Aktifkan Notifikasi",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    TextButton(
                        onClick = { userBypassedPrompt = true },
                        modifier = Modifier.testTag("skip_permission_button")
                    ) {
                        Text(
                            text = "Lanjutkan Tanpa Notifikasi",
                            fontSize = 12.sp,
                            color = AgedGold,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
