package com.yansproject.app.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yansproject.app.R
import com.yansproject.app.ui.theme.*
import com.yansproject.app.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: MainViewModel,
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var lastClickTime by remember { mutableLongStateOf(0L) }
    var logoClickCount by remember { mutableIntStateOf(0) }
    
    val loginError by viewModel.loginError.collectAsState()
    val isLoginLoading by viewModel.isLoginLoading.collectAsState()
    val isDeveloperMode by viewModel.isDeveloperMode.collectAsState()

    // Preferences for Biometric Configuration
    val secPrefs = remember { context.getSharedPreferences("yans_security_prefs", Context.MODE_PRIVATE) }
    val biometricEnabled = remember { secPrefs.getBoolean("biometric_enabled", false) }
    val authPrefs = remember { context.getSharedPreferences("yans_auth_prefs", Context.MODE_PRIVATE) }
    val savedEmail = remember { authPrefs.getString("saved_email", null) }
    
    // Check if biometric is supported on this device
    val isBiometricSupported = remember {
        val biometricManager = androidx.biometric.BiometricManager.from(context)
        biometricManager.canAuthenticate(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
    }

    var showBiometricEnrollDialog by remember { mutableStateOf(false) }
    var pendingSuccessAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Automatic trigger for biometric authentication on launch if switch is ON
    LaunchedEffect(Unit) {
        if (biometricEnabled && isBiometricSupported && savedEmail != null) {
            val cred = AppSettings.getLocalUserCredential(context, savedEmail)
            if (cred != null) {
                com.yansproject.app.ui.security.BiometricAuthManager.authenticateWithBiometrics(
                    context = context,
                    onSuccess = {
                        viewModel.login(savedEmail, cred.passwordOrPin) {
                            onLoginSuccess()
                        }
                    },
                    onError = { err ->
                        // Gracefully fail and show traditional password form
                        Toast.makeText(context, "Masuk menggunakan Username & Password Anda.", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    // Biometric Offer Dialog after successful login
    if (showBiometricEnrollDialog) {
        AlertDialog(
            onDismissRequest = {
                showBiometricEnrollDialog = false
                pendingSuccessAction?.invoke()
            },
            title = {
                Text(
                    text = "AKTIFKAN BIOMETRIK",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFC6A15B), // Aged Gold
                    fontSize = 16.sp,
                    letterSpacing = 1.sp
                )
            },
            text = {
                Text(
                    text = "Apakah Anda ingin mengaktifkan autentikasi sidik jari untuk masuk lebih cepat ke portal operasional YANSPROJECT.ID berikutnya?",
                    color = Color.White,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBiometricEnrollDialog = false
                        com.yansproject.app.ui.security.BiometricAuthManager.authenticateWithBiometrics(
                            context = context,
                            onSuccess = {
                                secPrefs.edit().putBoolean("biometric_enabled", true).apply()
                                Toast.makeText(context, "Sidik jari sukses diaktifkan!", Toast.LENGTH_SHORT).show()
                                viewModel.addAuditLog("Enroll Biometric", "Pemilik mendaftarkan sidik jari melalui login pertama.")
                                pendingSuccessAction?.invoke()
                            },
                            onError = { err ->
                                Toast.makeText(context, "Gagal mendaftarkan sidik jari: $err", Toast.LENGTH_SHORT).show()
                                pendingSuccessAction?.invoke()
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFC6A15B),
                        contentColor = Color(0xFF0A0A0A)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("AKTIFKAN", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showBiometricEnrollDialog = false
                        pendingSuccessAction?.invoke()
                    }
                ) {
                    Text("NANTI SAJA", color = Color(0xFFA7B8B3))
                }
            },
            containerColor = Color(0xFF163536), // Dark Card
            shape = RoundedCornerShape(20.dp)
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A)) // Deep Shadow Black foundation
    ) {
        // Subtle deep Teal ambient backdrop
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF0F3D3E).copy(alpha = 0.35f), // Dark Teal center
                            Color(0xFF0A0A0A) // Fade to total shadow black
                        ),
                        radius = 1600f
                    )
                )
        )

        // Cinematic Natural soft light in center background
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-100).dp)
                .size(450.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF4FD1C5).copy(alpha = 0.04f), // Ultra thin cyan ambient glow
                            Color.Transparent
                        )
                    )
                )
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Spacer(modifier = Modifier.height(30.dp))

                // Official Logo with Subtle Aged Gold Aura
                Box(
                    modifier = Modifier
                        .size(86.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastClickTime > 1000) {
                                logoClickCount = 1
                            } else {
                                logoClickCount++
                            }
                            lastClickTime = currentTime

                            if (logoClickCount >= 13) {
                                viewModel.setDeveloperMode(true)
                                Toast.makeText(
                                    context,
                                    "Mode Developer diaktifkan! Akses Portal di Pengaturan.",
                                    Toast.LENGTH_LONG
                                ).show()
                                logoClickCount = 0
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Soft natural glow aura behind logo
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .shadow(
                                elevation = 12.dp,
                                shape = RoundedCornerShape(35.dp),
                                clip = false,
                                spotColor = Color(0xFFC6A15B).copy(alpha = 0.15f),
                                ambientColor = Color(0xFFC6A15B).copy(alpha = 0.15f)
                            )
                    )
                    Icon(
                        painter = painterResource(id = R.drawable.ic_logo),
                        contentDescription = "YANSPROJECT.ID Official Logo",
                        tint = Color(0xFFC6A15B), // Aged Gold Light
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Brand headliner with natural gold lighting
                Text(
                    text = "YANSPROJECT.ID",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.5.sp,
                    color = Color(0xFFC6A15B),
                    style = TextStyle(
                        shadow = Shadow(
                            color = Color(0xFFC6A15B).copy(alpha = 0.35f),
                            blurRadius = 10f
                        )
                    )
                )

                Text(
                    text = "INTERNAL OPERATIONS PORTAL",
                    fontSize = 11.sp,
                    color = Color(0xFFE2E8F0).copy(alpha = 0.55f), // Warm White with low opacity
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.8.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )

                Spacer(modifier = Modifier.height(44.dp))

                // Luxury Glass Username field
                LuxuryGlassTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = "Username atau Email",
                    placeholder = "Masukkan username Anda",
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Person,
                            contentDescription = "User Icon",
                            tint = Color(0xFFC6A15B), // Aged Gold
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    testTag = "username_input"
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Luxury Glass Password field
                LuxuryGlassTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Password",
                    placeholder = "Masukkan password Anda",
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = "Password Icon",
                            tint = Color(0xFFC6A15B), // Aged Gold
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        val image = if (passwordVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff
                        IconButton(
                            onClick = { passwordVisible = !passwordVisible },
                            enabled = !isLoginLoading
                        ) {
                            Icon(
                                imageVector = image,
                                contentDescription = "Toggle password visibility",
                                tint = Color(0xFFE2E8F0).copy(alpha = 0.5f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    testTag = "password_input"
                )

                // Elegant Inline Error Banner (Deep red, subtle hierarchy)
                if (loginError != null) {
                    Text(
                        text = loginError ?: "",
                        color = Color(0xFFD32F2F), // Deep Red
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 16.dp),
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(36.dp))

                // Premium Aged Gold Submit Button
                LuxuryPremiumButton(
                    text = "LOG IN",
                    onClick = {
                        if (username.isBlank() || password.isBlank()) {
                            Toast.makeText(context, "Username dan password wajib diisi!", Toast.LENGTH_SHORT).show()
                            return@LuxuryPremiumButton
                        }
                        viewModel.login(username, password) {
                            if (!biometricEnabled && isBiometricSupported) {
                                pendingSuccessAction = { onLoginSuccess() }
                                showBiometricEnrollDialog = true
                            } else {
                                onLoginSuccess()
                            }
                        }
                    },
                    enabled = !isLoginLoading,
                    isLoading = isLoginLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("login_button")
                )

                // Quick Biometric Action Shortcut (Visible if enabled)
                if (biometricEnabled && isBiometricSupported && savedEmail != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(
                        onClick = {
                            val cred = AppSettings.getLocalUserCredential(context, savedEmail)
                            if (cred != null) {
                                com.yansproject.app.ui.security.BiometricAuthManager.authenticateWithBiometrics(
                                    context = context,
                                    onSuccess = {
                                        viewModel.login(savedEmail, cred.passwordOrPin) {
                                            onLoginSuccess()
                                        }
                                    },
                                    onError = { err ->
                                        Toast.makeText(context, "Autentikasi Biometrik gagal: $err", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        },
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Fingerprint,
                            contentDescription = "Quick biometric login",
                            tint = Color(0xFF4FD1C5), // Soft Cyan
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "MASUK DENGAN SIDIK JARI",
                            color = Color(0xFF4FD1C5),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }

                // Elegant Developer Mode Information Card
                if (isDeveloperMode) {
                    Spacer(modifier = Modifier.height(28.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0F3D3E).copy(alpha = 0.15f))
                            .border(1.dp, Color(0xFFC6A15B).copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Shield,
                            contentDescription = "Developer Mode Active",
                            tint = Color(0xFFC6A15B).copy(alpha = 0.5f),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "MODE DEVELOPER AKTIF",
                            color = Color(0xFFC6A15B).copy(alpha = 0.5f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LuxuryGlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = true,
    testTag: String = ""
) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    
    val focusColor = if (isError) Color(0xFFD32F2F) else Color(0xFFC6A15B) // Deep Red vs Aged Gold
    val unFocusColor = if (isError) Color(0xFFD32F2F).copy(alpha = 0.5f) else Color(0xFFC6A15B).copy(alpha = 0.2f)
    
    val containerColor = if (isFocused) {
        Color(0xFF0F3D3E).copy(alpha = 0.18f) // Dark Teal transparent
    } else {
        Color(0xFF0F3D3E).copy(alpha = 0.08f)
    }

    // Outer ambient cyan glow when focused
    val focusGlowModifier = if (isFocused && !isError) {
        Modifier.shadow(
            elevation = 6.dp,
            shape = shape,
            clip = false,
            spotColor = Color(0xFF4FD1C5).copy(alpha = 0.15f), // Faint Cyan glow
            ambientColor = Color(0xFF4FD1C5).copy(alpha = 0.15f)
        )
    } else {
        Modifier
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { 
                Text(
                    text = label, 
                    color = if (isFocused) focusColor else Color(0xFFE2E8F0).copy(alpha = 0.5f),
                    fontWeight = FontWeight.Medium
                ) 
            },
            placeholder = { 
                Text(
                    text = placeholder, 
                    color = Color(0xFFE2E8F0).copy(alpha = 0.35f), // Warm White opacity
                    fontSize = 14.sp
                ) 
            },
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            isError = isError,
            singleLine = singleLine,
            keyboardOptions = keyboardOptions,
            visualTransformation = visualTransformation,
            modifier = Modifier
                .fillMaxWidth()
                .then(focusGlowModifier)
                .onFocusChanged { isFocused = it.isFocused }
                .background(containerColor, shape)
                .testTag(testTag),
            shape = shape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = focusColor,
                unfocusedBorderColor = unFocusColor,
                focusedLabelColor = focusColor,
                unfocusedLabelColor = Color(0xFFE2E8F0).copy(alpha = 0.5f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Color(0xFF4FD1C5), // Soft Cyan cursor
                errorBorderColor = Color(0xFFD32F2F),
                errorCursorColor = Color(0xFFD32F2F),
                errorLabelColor = Color(0xFFD32F2F)
            )
        )
    }
}

@Composable
fun LuxuryPremiumButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false
) {
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(16.dp)
    
    // Luxury vertical gradient of Aged Gold
    val brush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFD4AF37), // Aged Gold Light
            Color(0xFF9E7E43)  // Aged Gold Dark
        )
    )

    Box(
        modifier = modifier
            .height(54.dp)
            .shadow(
                elevation = if (enabled && !isLoading) 6.dp else 0.dp,
                shape = shape,
                spotColor = Color(0xFFC6A15B).copy(alpha = 0.3f),
                ambientColor = Color(0xFFC6A15B).copy(alpha = 0.3f)
            )
            .clip(shape)
            .background(
                if (enabled && !isLoading) brush else Brush.verticalGradient(
                    colors = listOf(Color(0xFF334E50).copy(alpha = 0.5f), Color(0xFF1E3233).copy(alpha = 0.5f))
                )
            )
            .clickable(enabled = enabled && !isLoading) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = Color(0xFF0A0A0A), // High contrast over gold background
                strokeWidth = 2.5.dp
            )
        } else {
            Text(
                text = text.uppercase(),
                color = Color(0xFF0A0A0A), // Contrast on Gold background
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp,
                letterSpacing = 1.5.sp
            )
        }
    }
}
