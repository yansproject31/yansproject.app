package com.yansproject.app.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yansproject.app.ui.theme.*

// ==========================================
// CYBER DESIGN SYSTEM CONFIGURATION
// ==========================================
@Stable
class CyberDesignSystem(
    val neonGlowColor: Color = NeonCyan
)

val LocalCyberDesign = staticCompositionLocalOf { CyberDesignSystem() }

// Custom ambient glow modifier
fun Modifier.ambientGlow(
    color: Color = AgedGold,
    radius: Dp = 12.dp,
    alpha: Float = 0.35f
): Modifier = this.drawBehind {
    val spread = radius.toPx()
    drawCircle(
        color = color.copy(alpha = alpha),
        radius = size.minDimension / 2 + spread,
        center = this.center,
        style = Stroke(width = spread)
    )
}

// Custom Glassmorphic container modifier (No blur, background alpha + thin border + clip)
fun Modifier.glassCard(
    cornerRadius: Dp = 16.dp,
    borderColor: Color = GlassBorder,
    backgroundColor: Color = GlassWhite
): Modifier = this
    .background(Color(0x1AFFFFFF), RoundedCornerShape(16.dp))
    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp))
    .clip(RoundedCornerShape(16.dp))

// High-fidelity tactile interaction effect
@Composable
fun rememberTactileFeedback(): () -> Unit {
    val haptic = LocalHapticFeedback.current
    return {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
}

// Cyber shimmer effect for luxury skeleton loads
fun Modifier.cyberShimmer(
    durationMillis: Int = 2000
): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "Shimmer")
    val translateAnim = transition.animateFloat(
        initialValue = -500f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ShimmerTranslate"
    )

    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            Color.Transparent,
            NeonCyan.copy(alpha = 0.18f),
            Color.Transparent
        ),
        start = Offset(translateAnim.value, 0f),
        end = Offset(translateAnim.value + 300f, 300f)
    )

    this.background(brush = shimmerBrush)
}

// Elegant unified typography system
val OmniverseTypography = YansTypography

private val DarkCyberColorScheme = darkColorScheme(
    primary = AgedGold,
    secondary = NeonCyan,
    tertiary = AgedGoldLight,
    background = DarkTealBase,
    surface = DarkTealSurface,
    onPrimary = Color.Black,
    onSecondary = YansTextPrimary,
    onBackground = YansTextPrimary,
    onSurface = YansTextPrimary,
    outline = YansDivider
)

@Composable
fun OmniverseTheme(
    darkTheme: Boolean = true, // Force Cyber-Fintech dark theme rules
    content: @Composable () -> Unit
) {
    val cyberDesign = remember { CyberDesignSystem() }

    CompositionLocalProvider(
        LocalCyberDesign provides cyberDesign
    ) {
        MaterialTheme(
            colorScheme = DarkCyberColorScheme,
            typography = OmniverseTypography,
            content = content
        )
    }
}
