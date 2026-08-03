package com.yansproject.app.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import kotlin.jvm.JvmName

// ==========================================
// OFFICIAL YANSPROJECT.ID COLOR SYSTEM (VERSI 1.3.0)
// ==========================================
// Static base defaults for non-composable initializations
val StaticDarkTealBase = Color(0xFF071516)
val StaticDarkTealSurface = Color(0xFF0F2E2F)
val StaticDarkTealSurfaceVariant = Color(0xFF143B3C)
val StaticAgedGold = Color(0xFFC6A15B)
val StaticHighlightSoftCyan = Color(0xFF4FD1C5)
val StaticPrimaryDarkTeal = Color(0xFF0D3738)
val StaticSecondaryShadowBlackTeal = Color(0xFF082021)

// Dynamic Compose Snapshot State holders for Theme Colors
var dynamicShadowBlack by mutableStateOf(Color(0xFF071516))
var dynamicDarkTealSurface by mutableStateOf(Color(0xFF0F2E2F))
var dynamicCardDarkCard by mutableStateOf(Color(0xFF143B3C))
var dynamicPrimaryDarkTeal by mutableStateOf(Color(0xFF0D3738))
var dynamicSecondaryShadowBlackTeal by mutableStateOf(Color(0xFF082021))
var dynamicAgedGold by mutableStateOf(Color(0xFFC6A15B))
var dynamicHighlightSoftCyan by mutableStateOf(Color(0xFF4FD1C5))
var dynamicBorderGrey by mutableStateOf(Color(0xFF0D3738))

// Public Brush for YANSPROJECT Luxury Gradient Canvas
val YansCanvasGradient: androidx.compose.ui.graphics.Brush
    get() = androidx.compose.ui.graphics.Brush.verticalGradient(
        colors = listOf(
            dynamicShadowBlack,
            Color(0xFF051112),
            Color(0xFF030A0B)
        )
    )

// Public aliases forwarding to dynamic snapshot states
val DarkTealBase: Color get() = dynamicShadowBlack
val DarkTealSurface: Color get() = dynamicDarkTealSurface
val DarkTealSurfaceVariant: Color get() = dynamicCardDarkCard

val AgedGoldLight: Color get() = dynamicAgedGold
val AgedGoldDark: Color get() = dynamicAgedGold.copy(alpha = 0.8f)

val NeonCyan: Color get() = dynamicHighlightSoftCyan
val YansDivider: Color get() = dynamicBorderGrey
val YansTextPrimary = Color(0xFFFFFFFF)
val YansTextSecondary = Color(0xFFA7B8B3)

val YansError = Color(0xFFFF5A5A)
val YansSuccess = Color(0xFF30D158)

val GlassWhite = Color.White.copy(alpha = 0.05f)
val GlassBorder = Color(0x33FFFFFF)

// ==========================================
// CENTRAL COMPATIBILITY ALIASES (PUBLIC VARIABLES)
// ==========================================
@get:JvmName("shadowBlack_lower")
val shadowBlack: Color get() = dynamicShadowBlack

@get:JvmName("darkTeal_lower")
val darkTeal: Color get() = dynamicPrimaryDarkTeal

@get:JvmName("agedGold_lower")
val agedGold: Color get() = dynamicAgedGold

@get:JvmName("cyanPulse_lower")
val cyanPulse: Color get() = dynamicHighlightSoftCyan

@get:JvmName("amberWarning_lower")
val amberWarning = Color(0xFFFFB300)

@get:JvmName("textWhite_lower")
val textWhite = YansTextPrimary

@get:JvmName("textMuted_lower")
val textMuted = YansTextSecondary

val BackgroundDarkTeal: Color get() = dynamicShadowBlack
val SurfaceDarkTeal: Color get() = dynamicDarkTealSurface
val PrimaryGold: Color get() = dynamicAgedGold
val CyanAccent: Color get() = dynamicHighlightSoftCyan
val TextPrimary = YansTextPrimary
val TextSecondary = YansTextSecondary
val ErrorRed = YansError

val PrimaryDarkTeal: Color get() = dynamicPrimaryDarkTeal
val SecondaryShadowBlackTeal: Color get() = dynamicSecondaryShadowBlackTeal
val AccentAgedGold: Color get() = dynamicAgedGold
val HighlightSoftCyan: Color get() = dynamicHighlightSoftCyan
val BackgroundShadowBlack: Color get() = dynamicShadowBlack
val SurfaceDarkTealSurface: Color get() = dynamicDarkTealSurface
val CardDarkCard: Color get() = dynamicCardDarkCard

// Text Colors
val TextJudulAgedGold: Color get() = dynamicAgedGold
val TextIsiSoftGray = TextSecondary
val TextNonActive = TextSecondary

// Divider
val DividerDarkCyanGray: Color get() = dynamicBorderGrey

// Status Colors
val StatusSuccessTeal: Color get() = dynamicDarkTealSurface
val StatusSuccessCyan: Color get() = dynamicHighlightSoftCyan
val StatusWarningGold: Color get() = dynamicAgedGold
val StatusDangerRed = YansError
val StatusInfoCyan: Color get() = dynamicHighlightSoftCyan

// Compatibility Aliases
@get:JvmName("DarkTeal_upper")
val DarkTeal: Color get() = dynamicPrimaryDarkTeal

val DarkTealEnd: Color get() = dynamicShadowBlack

@get:JvmName("AgedGold_upper")
val AgedGold: Color get() = dynamicAgedGold

@get:JvmName("ShadowBlack_upper")
val ShadowBlack: Color get() = dynamicShadowBlack

val DarkGrey: Color get() = dynamicPrimaryDarkTeal
val CardGrey: Color get() = dynamicCardDarkCard
val BorderGrey: Color get() = dynamicBorderGrey
val AccentGoldLight: Color get() = dynamicAgedGold

// Semantic colors
@get:JvmName("TextWhite_upper")
val TextWhite = Color(0xFFFFFFFF)

@get:JvmName("CyanPulse_upper")
val CyanPulse: Color get() = dynamicHighlightSoftCyan

@get:JvmName("AmberWarning_upper")
val AmberWarning = Color(0xFFFFB300)

@get:JvmName("TextLight_upper")
val TextLight = TextSecondary

@get:JvmName("TextMuted_upper")
val TextMuted = TextSecondary

val AlertGreen = YansSuccess
val AlertRed = YansError
val AlertOrange: Color get() = dynamicAgedGold
val AlertBlue: Color get() = dynamicHighlightSoftCyan


