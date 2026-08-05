package com.yansproject.app.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.yansproject.app.data.FirebaseSyncManager
import com.yansproject.app.ui.components.*
import com.yansproject.app.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminProfileScreen(
    navController: NavController,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val currentUser by FirebaseSyncManager.currentUser.collectAsState()
    var isAuthorized by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        com.yansproject.app.ui.security.BiometricAuthManager.authenticateWithBiometrics(
            context = context,
            onSuccess = {
                isAuthorized = true
                viewModel.addAuditLog("Akses Profil Admin", "Administrator sukses verifikasi sidik jari untuk mengedit Profil Administrator.")
            },
            onError = { errString ->
                Toast.makeText(context, "Verifikasi Sidik Jari Gagal/Dibatalkan.", Toast.LENGTH_LONG).show()
                navController.popBackStack()
            }
        )
    }

    var name by remember { mutableStateOf(currentUser?.displayName ?: "") }
    var email by remember { mutableStateOf(currentUser?.email ?: "") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    if (!isAuthorized) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(shadowBlack),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = agedGold)
        }
        return
    }

    LaunchedEffect(currentUser) {
        currentUser?.let {
            name = it.displayName
            email = it.email
        }
    }

    androidx.activity.compose.BackHandler(enabled = true) {
        if (!navController.popBackStack()) {
            viewModel.setTab(AppTab.DASHBOARD)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize().background(shadowBlack),
        containerColor = Color.Transparent,
        topBar = {
            YansTopAppBar(
                title = "PROFIL ADMINISTRATOR",
                subtitle = "Otoritas Sistem & Pengaturan Akun",
                navigationIcon = {
                    YansBackButton(onClick = {
                        if (!navController.popBackStack()) {
                            viewModel.setTab(AppTab.DASHBOARD)
                        }
                    })
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // circular avatar colored agedGold
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(CircleShape)
                    .background(agedGold.copy(alpha = 0.15f))
                    .border(3.dp, agedGold, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = name.take(2).uppercase(),
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Black,
                    color = agedGold
                )
            }

            // large Badge "ADMINISTRATOR" with cyanPulse accent
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(30.dp))
                    .background(cyanPulse.copy(alpha = 0.12f))
                    .border(1.5.dp, cyanPulse, RoundedCornerShape(30.dp))
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AdminPanelSettings,
                        contentDescription = null,
                        tint = cyanPulse,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "ADMINISTRATOR",
                        color = cyanPulse,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // SharedPremiumCard containing profile form
            SharedPremiumCard(
                modifier = Modifier.fillMaxWidth(),
                padding = 20.dp
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "INFORMASI PRIBADI",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = agedGold,
                        letterSpacing = 1.sp
                    )

                    SmartTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = "Nama Lengkap",
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Person,
                                contentDescription = null,
                                tint = agedGold
                            )
                        }
                    )

                    SmartTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = "Email / Username",
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Email,
                                contentDescription = null,
                                tint = agedGold
                            )
                        }
                    )
                }
            }

            // SharedPremiumCard containing security / pin change
            SharedPremiumCard(
                modifier = Modifier.fillMaxWidth(),
                padding = 20.dp
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "KEAMANAN & PIN / PASSWORD",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = agedGold,
                        letterSpacing = 1.sp
                    )

                    SmartTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = "Password Baru",
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = null,
                                tint = agedGold
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = null,
                                    tint = textMuted
                                )
                            }
                        }
                    )

                    SmartTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = "Konfirmasi Password",
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.LockReset,
                                contentDescription = null,
                                tint = agedGold
                            )
                        }
                    )
                }
            }

            Button(
                onClick = {
                    if (name.isBlank()) {
                        Toast.makeText(context, "Nama tidak boleh kosong!", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (password.isNotEmpty() && password != confirmPassword) {
                        Toast.makeText(context, "Konfirmasi password tidak cocok!", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isLoading = true
                    coroutineScope.launch {
                        var success = true
                        if (password.isNotEmpty()) {
                            success = FirebaseSyncManager.changePasswordOnCloud(password)
                        }
                        if (success) {
                            val userCred = AppSettings.getLocalUserCredential(context, email)
                            val currentRole = userCred?.role ?: "ADMIN"
                            val currentPriceType = userCred?.priceCategory ?: "Retail"
                            val existingPass = userCred?.passwordOrPin ?: ""
                            val finalPass = if (password.isNotEmpty()) password else existingPass

                            if (userCred != null || password.isNotEmpty()) {
                                AppSettings.saveLocalUserCredential(
                                    context,
                                    email,
                                    finalPass,
                                    name,
                                    currentRole,
                                    currentPriceType
                                )
                            }
                            viewModel.addAuditLog(
                                "Update Admin Profile",
                                "Administrator memperbarui profil miliknya ($name)."
                            )
                            Toast.makeText(context, "Profil berhasil diperbarui!", Toast.LENGTH_LONG).show()
                            password = ""
                            confirmPassword = ""
                        } else {
                            Toast.makeText(context, "Gagal memperbarui password di Cloud. Periksa koneksi!", Toast.LENGTH_LONG).show()
                        }
                        isLoading = false
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = agedGold,
                    contentColor = shadowBlack
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = shadowBlack,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Text(
                        "SIMPAN PERUBAHAN",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}
