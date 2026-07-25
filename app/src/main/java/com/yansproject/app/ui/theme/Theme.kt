package com.yansproject.app.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

import androidx.compose.ui.unit.sp
import androidx.compose.material3.Typography

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Forced Premium Luxury Dark Theme
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("yans_appearance_prefs", Context.MODE_PRIVATE) }

    var themeVariant by remember { mutableStateOf(prefs.getString("theme_variant", "YANSPROJECT.ID Classic") ?: "YANSPROJECT.ID Classic") }
    var accentColorName by remember { mutableStateOf(prefs.getString("accent_color", "Aged Gold") ?: "Aged Gold") }
    var canvasStyleName by remember { mutableStateOf(prefs.getString("canvas_style", "Shadow Black (#0A0A0A)") ?: "Shadow Black (#0A0A0A)") }
    var fontScale by remember { mutableStateOf(prefs.getFloat("font_scale", 1.0f)) }

    val preferenceListener = remember {
        SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            if (key == "theme_variant" || key == "accent_color" || key == "canvas_style" || key == "font_scale") {
                themeVariant = p.getString("theme_variant", "YANSPROJECT.ID Classic") ?: "YANSPROJECT.ID Classic"
                accentColorName = p.getString("accent_color", "Aged Gold") ?: "Aged Gold"
                canvasStyleName = p.getString("canvas_style", "Shadow Black (#0A0A0A)") ?: "Shadow Black (#0A0A0A)"
                fontScale = p.getFloat("font_scale", 1.0f)
            }
        }
    }

    DisposableEffect(prefs, preferenceListener) {
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        }
    }

    val primaryAccent = when(accentColorName) {
        "Aged Gold" -> StaticAgedGold
        "Soft Cyan" -> StaticHighlightSoftCyan
        "Emerald Green" -> Color(0xFF2ECC71)
        "Imperial Amber" -> Color(0xFFFFB300)
        "Sapphire Blue" -> Color(0xFF3B82F6)
        "Rose Gold" -> Color(0xFFE5A186)
        else -> StaticAgedGold
    }

    val canvasBackground = when(canvasStyleName) {
        "Pure Obsidian Black (#000000)" -> Color(0xFF000000)
        "Dark Slate Teal (#081F20)" -> Color(0xFF081F20)
        else -> Color(0xFF0A0A0A)
    }

    val surfaceBg = when(themeVariant) {
        "Royal Emerald Imperial" -> Color(0xFF0B2B26)
        "Midnight Sapphire Luxury" -> Color(0xFF0A192F)
        "Onyx Platinum Edition" -> Color(0xFF1E293B)
        "Ruby Imperial Velvet" -> Color(0xFF2B0B14)
        else -> StaticDarkTealSurface
    }

    val surfaceVariantBg = when(themeVariant) {
        "Royal Emerald Imperial" -> Color(0xFF103A34)
        "Midnight Sapphire Luxury" -> Color(0xFF112240)
        "Onyx Platinum Edition" -> Color(0xFF334155)
        "Ruby Imperial Velvet" -> Color(0xFF3D101E)
        else -> StaticDarkTealSurfaceVariant
    }

    val primaryContainerBg = when(themeVariant) {
        "Royal Emerald Imperial" -> Color(0xFF061F1B)
        "Midnight Sapphire Luxury" -> Color(0xFF060D1A)
        "Onyx Platinum Edition" -> Color(0xFF0F172A)
        "Ruby Imperial Velvet" -> Color(0xFF1A050B)
        else -> StaticPrimaryDarkTeal
    }

    val secondaryAccent = if (primaryAccent == StaticHighlightSoftCyan) StaticAgedGold else StaticHighlightSoftCyan

    // Sync dynamic theme snapshot states for real-time application-wide theme updates
    dynamicShadowBlack = canvasBackground
    dynamicDarkTealSurface = surfaceBg
    dynamicCardDarkCard = surfaceVariantBg
    dynamicPrimaryDarkTeal = primaryContainerBg
    dynamicSecondaryShadowBlackTeal = surfaceVariantBg
    dynamicAgedGold = primaryAccent
    dynamicHighlightSoftCyan = secondaryAccent
    dynamicBorderGrey = primaryAccent.copy(alpha = 0.3f)

    val dynamicColorScheme = darkColorScheme(
        primary = primaryAccent,
        onPrimary = Color.Black,
        primaryContainer = primaryContainerBg,
        onPrimaryContainer = primaryAccent,
        secondary = secondaryAccent,
        onSecondary = Color.Black,
        tertiary = secondaryAccent,
        background = canvasBackground,
        onBackground = YansTextPrimary,
        surface = surfaceBg,
        onSurface = YansTextPrimary,
        surfaceVariant = surfaceVariantBg,
        onSurfaceVariant = YansTextSecondary,
        outline = primaryAccent.copy(alpha = 0.3f),
        error = YansError,
        onError = Color.White
    )

    val scaledTypography = remember(fontScale) {
        if (fontScale == 1.0f) YansTypography
        else Typography(
            displayLarge = YansTypography.displayLarge.copy(fontSize = (32 * fontScale).sp, lineHeight = (40 * fontScale).sp),
            displayMedium = YansTypography.displayMedium.copy(fontSize = (28 * fontScale).sp, lineHeight = (36 * fontScale).sp),
            headlineMedium = YansTypography.headlineMedium.copy(fontSize = (20 * fontScale).sp, lineHeight = (28 * fontScale).sp),
            titleLarge = YansTypography.titleLarge.copy(fontSize = (18 * fontScale).sp, lineHeight = (24 * fontScale).sp),
            titleMedium = YansTypography.titleMedium.copy(fontSize = (16 * fontScale).sp, lineHeight = (22 * fontScale).sp),
            bodyLarge = YansTypography.bodyLarge.copy(fontSize = (14 * fontScale).sp, lineHeight = (20 * fontScale).sp),
            bodyMedium = YansTypography.bodyMedium.copy(fontSize = (13 * fontScale).sp, lineHeight = (18 * fontScale).sp),
            labelLarge = YansTypography.labelLarge.copy(fontSize = (13 * fontScale).sp, lineHeight = (18 * fontScale).sp),
            labelMedium = YansTypography.labelMedium.copy(fontSize = (12 * fontScale).sp, lineHeight = (16 * fontScale).sp),
            bodySmall = YansTypography.bodySmall.copy(fontSize = (11 * fontScale).sp, lineHeight = (14 * fontScale).sp)
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            var ctx = view.context
            while (ctx is ContextWrapper) {
                if (ctx is Activity) break
                ctx = ctx.baseContext
            }
            val activity = ctx as? Activity
            activity?.window?.let { window ->
                window.statusBarColor = canvasBackground.toArgb()
                window.navigationBarColor = surfaceBg.toArgb()
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = false
                insetsController.isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = dynamicColorScheme,
        typography = scaledTypography,
        content = content
    )
}

/**
 * SharedPremiumCard - Reusable Premium Non-Blurred Card Component
 */
@Composable
fun SharedPremiumCard(
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
    borderGlowColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
    content: @Composable () -> Unit
) {
    val cardShape = RoundedCornerShape(20.dp) // Card corner radius constraint: 20dp
    
    val cardModifier = if (onClick != null) {
        modifier
            .shadow(
                elevation = 6.dp,
                shape = cardShape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.5f),
                spotColor = Color.Black.copy(alpha = 0.5f)
            )
            .clip(cardShape)
            .clickable(onClick = onClick)
    } else {
        modifier
            .shadow(
                elevation = 6.dp,
                shape = cardShape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.5f),
                spotColor = Color.Black.copy(alpha = 0.5f)
            )
            .clip(cardShape)
    }

    Card(
        modifier = cardModifier,
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(
                    borderGlowColor,
                    Color.Transparent
                )
            )
        )
    ) {
        Box(
            modifier = Modifier
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                )
                .padding(padding)
        ) {
            content()
        }
    }
}
