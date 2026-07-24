package com.yansproject.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

// ==========================================
// THEME TOKENS COMPATIBILITY LAYER
// ==========================================
val DeepCarbonBlack = DarkTealBase
val EmeraldSlateGreen = DarkTealSurface
val LuxuryGold = AgedGold
val WarningAmber = AmberWarning

val ShadowCarbonBg = DarkTealBase
val TextOnCarbon = YansTextPrimary
val TextMutedCarbon = YansTextSecondary

val LuxuryFintechColorScheme: ColorScheme = darkColorScheme(
    primary = AgedGold,
    onPrimary = Color.Black,
    primaryContainer = DarkTealSurfaceVariant,
    onPrimaryContainer = AgedGold,
    secondary = NeonCyan,
    onSecondary = Color.Black,
    background = DarkTealBase,
    onBackground = YansTextPrimary,
    surface = DarkTealSurface,
    onSurface = YansTextPrimary,
    surfaceVariant = DarkTealSurfaceVariant,
    onSurfaceVariant = YansTextSecondary,
    error = YansError,
    onError = Color.White
)
