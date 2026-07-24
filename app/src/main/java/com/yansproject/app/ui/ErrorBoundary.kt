package com.yansproject.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yansproject.app.ui.theme.*

// CompositionLocal to trigger error boundary manually or from viewmodel
val LocalErrorReporter = staticCompositionLocalOf<((Throwable) -> Unit)?> { null }

@Composable
fun ErrorBoundaryWrapper(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var errorState by remember { mutableStateOf<Throwable?>(null) }

    CompositionLocalProvider(
        LocalErrorReporter provides { errorState = it }
    ) {
        if (errorState != null) {
            RecoveryModeScreen(
                error = errorState!!,
                onRetry = {
                    errorState = null
                }
            )
        } else {
            content()
        }
    }
}

@Composable
fun RecoveryModeScreen(
    error: Throwable,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ShadowBlack)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        SharedPremiumCard(
            modifier = Modifier.fillMaxWidth(),
            borderGlowColor = AgedGold.copy(alpha = 0.3f),
            padding = 24.dp
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = "Stability Warning",
                    tint = AgedGold,
                    modifier = Modifier.size(64.dp)
                )

                Text(
                    text = "RECOVERY MODE",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = AgedGold,
                    letterSpacing = 2.sp
                )

                Text(
                    text = "Aplikasi mendeteksi ketidakstabilan sistem yang kritis. Crash telemetry telah dikirim ke Firebase Cloud untuk dianalisis oleh Owner.",
                    fontSize = 12.sp,
                    color = TextMuted,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                HorizontalDivider(color = BorderGrey, thickness = 1.dp)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF051213), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "DETIL EXCEPTION:",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = AgedGold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = error.localizedMessage ?: error.message ?: "Unknown crash thread exception.",
                        fontSize = 11.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = TextWhite,
                        maxLines = 4,
                        lineHeight = 14.sp
                    )
                }

                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AgedGold,
                        contentColor = ShadowBlack
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "RETRY CONNECTION",
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}
