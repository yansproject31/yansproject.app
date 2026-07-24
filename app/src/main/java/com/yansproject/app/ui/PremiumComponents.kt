package com.yansproject.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.yansproject.app.ui.theme.*

// ==========================================
// ANTI-FLAT COMPONENT ARSENAL (PRODUCTION READY)
// ==========================================

/**
 * SharedPremiumCard - Premium "Anti-Flat" Card Component
 * Features:
 * - Corner Radius: 16dp - 20dp
 * - Background: Dark Teal linear gradient
 * - Anti-Flat Effect: 15% opacity cyanPulse inner border (glassmorphism)
 * - Elevation/Shadow: 12dp drop shadow
 */
@Composable
fun SharedPremiumCard(
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
    borderGlowColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
    content: @Composable () -> Unit
) {
    val cardModifier = if (onClick != null) {
        modifier
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(20.dp),
                clip = false,
                ambientColor = Color.Black,
                spotColor = Color.Black
            )
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    } else {
        modifier
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(20.dp),
                clip = false,
                ambientColor = Color.Black,
                spotColor = Color.Black
            )
            .clip(RoundedCornerShape(20.dp))
    }

    Card(
        modifier = cardModifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    borderGlowColor,
                    Color.Transparent,
                    borderGlowColor.copy(alpha = 0.05f)
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

/**
 * SmartTextField - Custom OutlinedTextField with filled dark background and cyan pulse glow on focus
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = true,
    maxLines: Int = 1
) {
    val primaryAccent = MaterialTheme.colorScheme.primary
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = textMuted, fontSize = 13.sp) },
        placeholder = { Text(placeholder, color = textMuted.copy(alpha = 0.6f), fontSize = 12.sp) },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        singleLine = singleLine,
        maxLines = maxLines,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = textWhite,
            unfocusedTextColor = textWhite,
            focusedContainerColor = Color(0xFF050505),
            unfocusedContainerColor = Color(0xFF0E0E0E),
            focusedBorderColor = primaryAccent,
            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
            cursorColor = primaryAccent,
            focusedLabelColor = primaryAccent,
            unfocusedLabelColor = textMuted
        )
    )
}

/**
 * PremiumFAB - Elevated Circular Floating Action Button using agedGold containing only the icon
 */
@Composable
fun PremiumFAB(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    label: @Composable () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    FloatingActionButton(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        modifier = modifier
            .shadow(
                elevation = 12.dp,
                shape = CircleShape,
                clip = false,
                ambientColor = Color.Black,
                spotColor = Color.Black
            ),
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = shadowBlack
    ) {
        icon()
    }
}

/**
 * PremiumBottomSheetLayout - Rounded top corners (24dp) and darkTeal background
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumBottomSheetLayout(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.() -> Unit
) {
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(Unit) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = textWhite,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            content()
        }
    }
}

/**
 * PremiumBottomSheet - Base layout for pop-up transparan melengkung (24dp)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.() -> Unit
) {
    PremiumBottomSheetLayout(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        content = content
    )
}

/**
 * ConnectivityStatusBadge - Reusable M3 Glassmorphism status indicator component
 * Displays Real-Time Online / Offline Connectivity state reflecting Firestore Offline Persistence mode.
 */
@Composable
fun ConnectivityStatusBadge(
    isOnline: Boolean,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val backgroundColor = if (isOnline) Color(0xFF0F3D3E).copy(alpha = 0.6f) else Color(0xFF1E160D).copy(alpha = 0.6f)
    val borderColor = if (isOnline) cyanPulse.copy(alpha = 0.4f) else agedGold.copy(alpha = 0.4f)
    val dotColor = if (isOnline) cyanPulse else agedGold
    val textState = if (isOnline) "ONLINE" else "OFFLINE"

    val clickableModifier = if (onClick != null) modifier.clickable { onClick() } else modifier

    Surface(
        modifier = clickableModifier
            .clip(RoundedCornerShape(50))
            .border(0.8.dp, borderColor, RoundedCornerShape(50)),
        color = backgroundColor,
        shape = RoundedCornerShape(50)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            if (showLabel) {
                Text(
                    text = textState,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isOnline) cyanPulse else agedGold,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

/**
 * SholawatMarqueeBanner - DNA YANSPROJECT.ID Premium Luxury Ticker
 * Continuous smooth infinite marquee sliding animation of the Lafadz Sholawat:
 * "اَللّٰهُمَّ صَلِّ وَسَلِّمْ عَلَىٰ سَيِّدِنَا مُحَمَّدٍ وَعَلَىٰ آلِ سَيِّدِنَا مُحَمَّدٍ"
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SholawatMarqueeBanner(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sholawatLafadz = "اَللّٰهُمَّ صَلِّ وَسَلِّمْ عَلَىٰ سَيِّدِنَا مُحَمَّدٍ وَعَلَىٰ آلِ سَيِّدِنَا مُحَمَّدٍ"
    val repeatedText = "$sholawatLafadz   ✦   $sholawatLafadz   ✦   $sholawatLafadz   ✦   $sholawatLafadz"

    val premiumArabicFontFamily = remember {
        try {
            val tf = android.graphics.Typeface.create("serif-arabic", android.graphics.Typeface.BOLD)
            FontFamily(tf)
        } catch (e: Exception) {
            FontFamily.Serif
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        AgedGold.copy(alpha = 0.55f),
                        HighlightSoftCyan.copy(alpha = 0.35f),
                        AgedGold.copy(alpha = 0.55f)
                    )
                ),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable {
                Toast.makeText(
                    context,
                    "اَللّٰهُمَّ صَلِّ وَسَلِّمْ عَلَىٰ سَيِّدِنَا مُحَمَّدٍ وَعَلَىٰ آلِ سَيِّدِنَا مُحَمَّدٍ\nSHOLAWAT - SHOLAWAT - SHOLAWAT\n\nDIDO'AKEUN KU ABAH",
                    Toast.LENGTH_SHORT
                ).show()
            },
        color = CardDarkCard,
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Sholawat Badge (Luxury DNA)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                PrimaryDarkTeal,
                                SecondaryShadowBlackTeal
                            )
                        )
                    )
                    .border(0.8.dp, AgedGold.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "ﷺ",
                        color = AgedGold,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = premiumArabicFontFamily
                    )
                    Text(
                        text = "SHOLAWAT",
                        color = HighlightSoftCyan,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.8.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Infinite Sliding Marquee Text (Bermula/muncul dari Kiri dengan LayOutDirection.Rtl)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clipToBounds(),
                contentAlignment = Alignment.CenterStart
            ) {
                CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) {
                    Text(
                        text = repeatedText,
                        color = AgedGold,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = premiumArabicFontFamily,
                        maxLines = 1,
                        style = androidx.compose.ui.text.TextStyle(
                            shadow = androidx.compose.ui.graphics.Shadow(
                                color = AgedGold.copy(alpha = 0.35f),
                                blurRadius = 6f
                            )
                        ),
                        modifier = Modifier
                            .padding(vertical = 2.dp)
                            .basicMarquee(
                                iterations = Int.MAX_VALUE,
                                velocity = 35.dp
                            )
                    )
                }
            }
        }
    }
}

