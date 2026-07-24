package com.yansproject.app.ui.auth

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yansproject.app.R
import com.yansproject.app.ui.theme.*
import com.yansproject.app.ui.components.YansGlowingTextField
import com.yansproject.app.ui.components.YansLogoGlow
import com.yansproject.app.ui.components.YansPremiumButton

@Composable
fun LoginScreen(
    onLoginSuccess: (String, String) -> Unit,
    isLoginLoading: Boolean = false,
    loginError: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkTealBase)
    ) {
        // BACKGROUND GLOW (Cyan radial glow at exactly 10% opacity)
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(400.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            NeonCyan.copy(alpha = 0.10f),
                            Color.Transparent
                        ),
                        radius = 500f
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
                Spacer(modifier = Modifier.height(40.dp))

                // Brand Logo Typography (Aged Gold, Bold text)
                YansLogoGlow(
                    text = "YANSPROJECT.ID",
                    fontSize = 30.sp
                )

                Text(
                    text = "Internal Operations Portal",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(44.dp))

                // Username field using custom YansGlowingTextField (clear white text, no blur)
                YansGlowingTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = "Username",
                    placeholder = "Masukkan username Anda",
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Person,
                            contentDescription = "User Icon",
                            tint = AgedGoldLight,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("username_input")
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Password field using custom YansGlowingTextField
                YansGlowingTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Password",
                    placeholder = "Masukkan password Anda",
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = "Password Icon",
                            tint = AgedGoldLight,
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
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("password_input")
                )

                // Inline Error Banner
                if (loginError != null) {
                    Text(
                        text = loginError,
                        color = Color(0xFFFF5555),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 16.dp),
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(36.dp))

                // Premium Glowing Submit Button (no blur/shadow kotor)
                YansPremiumButton(
                    text = "LOG IN",
                    onClick = {
                        if (username.isBlank() || password.isBlank()) {
                            Toast.makeText(context, "Username dan password wajib diisi!", Toast.LENGTH_SHORT).show()
                            return@YansPremiumButton
                        }
                        onLoginSuccess(username, password)
                    },
                    enabled = !isLoginLoading,
                    isLoading = isLoginLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("login_button")
                )

                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}
