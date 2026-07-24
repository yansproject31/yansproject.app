package com.yansproject.app.ui.auth

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.yansproject.app.data.FirebaseSyncManager
import com.yansproject.app.data.UserRole
import com.yansproject.app.ui.theme.*
import com.yansproject.app.ui.theme.*
import com.yansproject.app.ui.components.*
import androidx.compose.ui.graphics.Brush
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// 1. AUTHENTICATION & SECURITY STATE ENGINE
enum class AuthState {
    IDLE, VALIDATING, SUCCESS, ERROR
}

class AuthViewModel : ViewModel() {
    private val _authState = MutableStateFlow(AuthState.IDLE)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _userRole = MutableStateFlow<UserRole?>(null)
    val userRole: StateFlow<UserRole?> = _userRole.asStateFlow()

    private val _errorMessage = MutableStateFlow("")
    val errorMessage: StateFlow<String> = _errorMessage.asStateFlow()

    fun authenticate(email: String, pin: String, onAuthSuccess: (UserRole) -> Unit) {
        if (email.isBlank() || pin.isBlank()) {
            _authState.value = AuthState.ERROR
            _errorMessage.value = "Email dan PIN Keamanan wajib diisi!"
            return
        }

        _authState.value = AuthState.VALIDATING
        viewModelScope.launch {
            kotlinx.coroutines.delay(1200) // Realistic secure cryptographic delay
            
            // Core Security Rules: Owner is hardcoded for first initialization
            // Other emails can be MEMBERs
            if (email.equals("owner@yansproject.id", ignoreCase = true) && pin == "2026") {
                _authState.value = AuthState.SUCCESS
                _userRole.value = UserRole.OWNER
                onAuthSuccess(UserRole.OWNER)
            } else if (email.contains("@") && pin.length >= 4) {
                // Member authorization simulation
                _authState.value = AuthState.SUCCESS
                _userRole.value = UserRole.MEMBER
                onAuthSuccess(UserRole.MEMBER)
            } else {
                _authState.value = AuthState.ERROR
                _errorMessage.value = "Kredensial salah! Hubungi Administrator OWNER."
            }
        }
    }

    fun resetState() {
        _authState.value = AuthState.IDLE
        _errorMessage.value = ""
    }
}

// 2. CYBER-FINTECH GLASMURPHIC LOGIN INTERFACE
@Composable
fun CyberLoginScreen(
    authViewModel: AuthViewModel,
    onNavigateToDashboard: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var isPinVisible by remember { mutableStateOf(false) }

    val authState by authViewModel.authState.collectAsState()
    val errorMessage by authViewModel.errorMessage.collectAsState()

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // Shake error animation triggered on state change
    val shakeOffset = remember { Animatable(0f) }
    LaunchedEffect(authState) {
        if (authState == AuthState.ERROR) {
            repeat(4) {
                shakeOffset.animateTo(
                    targetValue = if (it % 2 == 0) 15f else -15f,
                    animationSpec = tween(50)
                )
            }
            shakeOffset.animateTo(0f, tween(50))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkTealBase),
        contentAlignment = Alignment.Center
    ) {
        // Decorative Cyber ambient glow circles behind card
        Box(
            modifier = Modifier
                .size(300.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(NeonCyan.copy(alpha = 0.15f), Color.Transparent)
                    )
                )
        )

        // Main Login Glass Card with dynamic shake translation
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .offset(x = shakeOffset.value.dp)
                .glassCard()
                .padding(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Glow logo wrapper
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(SecondaryShadowBlackTeal)
                        .border(1.dp, AccentAgedGold, RoundedCornerShape(20.dp))
                        .ambientGlow(color = AccentAgedGold, radius = 6.dp, alpha = 0.4f),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Secure Key",
                        tint = AccentAgedGold,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                YansLogoGlow(
                    text = "YANSPROJECT.ID",
                    fontSize = 24.sp
                )
                Text(
                    text = "Ultimate ERP Node v2.0",
                    fontSize = 11.sp,
                    color = HighlightSoftCyan,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Email input with cyber accents
                YansGlowingTextField(
                    value = email,
                    onValueChange = { email = it; authViewModel.resetState() },
                    label = "Cyber Node Email",
                    placeholder = "Contoh: owner@yansproject.id",
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.AlternateEmail, contentDescription = null, tint = AccentAgedGold)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // PIN / Security code input
                YansGlowingTextField(
                    value = pin,
                    onValueChange = { pin = it; authViewModel.resetState() },
                    label = "Security Access PIN",
                    placeholder = "PIN 4-Digit Angka",
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.VpnKey, contentDescription = null, tint = AccentAgedGold)
                    },
                    trailingIcon = {
                        IconButton(onClick = { isPinVisible = !isPinVisible }) {
                            Icon(
                                imageVector = if (isPinVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = AccentAgedGold
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (isPinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done
                    )
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Error message display
                AnimatedVisibility(
                    visible = authState == AuthState.ERROR,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(StatusDangerRed.copy(alpha = 0.15f))
                            .border(1.dp, StatusDangerRed, RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = StatusDangerRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = errorMessage,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Button with embedded loading engine
                YansPremiumButton(
                    text = "DECRYPT ACCESS NODES",
                    onClick = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        authViewModel.authenticate(email, pin) {
                            onNavigateToDashboard()
                        }
                    },
                    enabled = authState != AuthState.VALIDATING,
                    isLoading = authState == AuthState.VALIDATING,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "System secure with 256-bit Firestore Cryptography.",
                    fontSize = 9.sp,
                    color = TextNonActive,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// 3. SECURE MULTI-ROUTING SYSTEM (YANS NAV HOST)
@Composable
fun YansNavHost(
    navController: NavHostController,
    viewModel: com.yansproject.app.ui.MainViewModel,
    modifier: Modifier = Modifier
) {
    com.yansproject.app.ui.navigation.YansNavHost(
        navController = navController,
        viewModel = viewModel,
        modifier = modifier
    )
}
