package com.yansproject.app.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.jvm.JvmName

// ==========================================
// OFFICIAL YANSPROJECT.ID COLOR SYSTEM (VERSI 1.2.0)
// ==========================================
val DarkTealBase = Color(0xFF0A0A0A)          // Shadow Black (#0A0A0A) as Background
val DarkTealSurface = Color(0xFF112B2C)       // Dark Teal Surface (#112B2C)
val DarkTealSurfaceVariant = Color(0xFF163536) // Dark Card (#163536)

val AgedGoldLight = Color(0xFFC6A15B)         // Aged Gold (#C6A15B)
val AgedGoldDark = Color(0xFF9E7E43)          // Darker Aged Gold

val NeonCyan = Color(0xFF4FD1C5)              // Soft Cyan (#4FD1C5)
val YansDivider = Color(0xFF0F3D3E)           // Dark Teal (#0F3D3E) for divider/accents
val YansTextPrimary = Color(0xFFFFFFFF)       // Text Primary (White)
val YansTextSecondary = Color(0xFFA7B8B3)     // Text Secondary (Soft Slate Gray)

val YansError = Color(0xFFFF5A5A)             // Error Red
val YansSuccess = Color(0xFF30D158)           // Success Green

val GlassWhite = Color.White.copy(alpha = 0.05f)  // Isi form/card kaca
val GlassBorder = Color(0x33FFFFFF)               // Thin border for glass Card

// ==========================================
// CENTRAL COMPATIBILITY ALIASES (PUBLIC VARIABLES)
// ==========================================
@get:JvmName("shadowBlack_lower")
val shadowBlack = Color(0xFF0A0A0A)

@get:JvmName("darkTeal_lower")
val darkTeal = Color(0xFF0F3D3E)

@get:JvmName("agedGold_lower")
val agedGold = Color(0xFFC6A15B)

@get:JvmName("cyanPulse_lower")
val cyanPulse = Color(0xFF4FD1C5)

@get:JvmName("amberWarning_lower")
val amberWarning = Color(0xFFFFB300)

@get:JvmName("textWhite_lower")
val textWhite = YansTextPrimary

@get:JvmName("textMuted_lower")
val textMuted = YansTextSecondary

val BackgroundDarkTeal = Color(0xFF0A0A0A)
val SurfaceDarkTeal = Color(0xFF112B2C)
val PrimaryGold = Color(0xFFC6A15B)
val CyanAccent = Color(0xFF4FD1C5)
val TextPrimary = YansTextPrimary
val TextSecondary = YansTextSecondary
val ErrorRed = YansError

val PrimaryDarkTeal = Color(0xFF0F3D3E)
val SecondaryShadowBlackTeal = Color(0xFF081F20)
val AccentAgedGold = Color(0xFFC6A15B)
val HighlightSoftCyan = Color(0xFF4FD1C5)
val BackgroundShadowBlack = Color(0xFF0A0A0A)
val SurfaceDarkTealSurface = Color(0xFF112B2C)
val CardDarkCard = Color(0xFF163536)

// Text Colors
val TextJudulAgedGold = Color(0xFFC6A15B)
val TextIsiSoftGray = TextSecondary
val TextNonActive = TextSecondary

// Divider
val DividerDarkCyanGray = Color(0xFF0F3D3E)

// Status Colors
val StatusSuccessTeal = Color(0xFF112B2C)
val StatusSuccessCyan = Color(0xFF4FD1C5)
val StatusWarningGold = Color(0xFFC6A15B)
val StatusDangerRed = YansError
val StatusInfoCyan = Color(0xFF4FD1C5)

// Compatibility Aliases
@get:JvmName("DarkTeal_upper")
val DarkTeal = Color(0xFF0F3D3E)

val DarkTealEnd = Color(0xFF081F20)

@get:JvmName("AgedGold_upper")
val AgedGold = Color(0xFFC6A15B)

@get:JvmName("ShadowBlack_upper")
val ShadowBlack = Color(0xFF0A0A0A)

val DarkGrey = Color(0xFF081F20)
val CardGrey = Color(0xFF163536)
val BorderGrey = Color(0xFF0F3D3E)
val AccentGoldLight = Color(0xFFC6A15B)

// Semantic colors
@get:JvmName("TextWhite_upper")
val TextWhite = Color(0xFFFFFFFF)

@get:JvmName("CyanPulse_upper")
val CyanPulse = Color(0xFF4FD1C5)

@get:JvmName("AmberWarning_upper")
val AmberWarning = Color(0xFFFFB300)

@get:JvmName("TextLight_upper")
val TextLight = TextSecondary

@get:JvmName("TextMuted_upper")
val TextMuted = TextSecondary

val AlertGreen = YansSuccess
val AlertRed = YansError
val AlertOrange = Color(0xFFC6A15B)
val AlertBlue = Color(0xFF4FD1C5)
