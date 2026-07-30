package com.yansproject.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
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
 * Unlimited smooth infinite marquee sliding animation of the Lafadz Sholawat
 * with Premium Arabic Font Calligraphy and Zero-Gaps Continuous Loop.
 */
@Composable
fun SholawatMarqueeBanner(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sholawatLafadzUnit = "۞     اَللّٰهُمَّ صَلِّ عَلَىٰ سَيِّدِنَا مُحَمَّدٍ وَعَلَىٰ آلِ سَيِّدِنَا مُحَمَّدٍ     "

    val premiumArabicFontFamily = remember(context) {
        FontUtils.getPremiumArabicFontFamily(context)
    }

    var showDetailDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        AgedGold.copy(alpha = 0.7f),
                        HighlightSoftCyan.copy(alpha = 0.4f),
                        AgedGold,
                        HighlightSoftCyan.copy(alpha = 0.4f),
                        AgedGold.copy(alpha = 0.7f)
                    )
                ),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable {
                showDetailDialog = true
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
                    .border(0.8.dp, AgedGold.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
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
                        fontSize = 16.sp,
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

            // Unlimited Infinite Continuous Marquee Engine
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clipToBounds(),
                contentAlignment = Alignment.CenterStart
            ) {
                CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) {
                    SeamlessUnlimitedSholawatMarquee(
                        text = sholawatLafadzUnit,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = AgedGold,
                            fontSize = 17.sp,
                            fontFamily = premiumArabicFontFamily,
                            shadow = androidx.compose.ui.graphics.Shadow(
                                color = AgedGold.copy(alpha = 0.6f),
                                blurRadius = 10f
                            )
                        ),
                        speedDpPerSecond = 35.dp
                    )
                }
            }
        }
    }

    if (showDetailDialog) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showDetailDialog = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = CardDarkCard,
                border = BorderStroke(1.dp, AgedGold.copy(alpha = 0.6f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(PrimaryDarkTeal, SecondaryShadowBlackTeal)
                                )
                            )
                            .border(1.dp, AgedGold, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "ﷺ",
                            color = AgedGold,
                            fontSize = 32.sp,
                            fontFamily = premiumArabicFontFamily
                        )
                    }

                    Text(
                        text = "RAHASIA BERSHOLAWAT",
                        color = HighlightSoftCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp
                    )

                    Text(
                        text = "اَللّٰهُمَّ صَلِّ عَلَىٰ سَيِّدِنَا مُحَمَّدٍ وَعَلَىٰ آلِ سَيِّدِنَا مُحَمَّدٍ",
                        color = AgedGold,
                        fontSize = 19.sp,
                        fontFamily = premiumArabicFontFamily,
                        textAlign = TextAlign.Center,
                        lineHeight = 44.sp,
                        style = androidx.compose.ui.text.TextStyle(
                            shadow = androidx.compose.ui.graphics.Shadow(
                                color = AgedGold.copy(alpha = 0.6f),
                                blurRadius = 12f
                            )
                        )
                    )

                    Text(
                        text = "\"Siapapun yang suka memuji, menyanjung, dan membesarkan Rosulullah, setiap detik Maqomnya naik. Kedudukan dan Martabatnya di sisi Allah itu bertambah mulia setiap detiknya.\"\n\n- Abah Guru Sekumpul -",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        textAlign = TextAlign.Center
                    )

                    HorizontalDivider(color = CardGrey, thickness = 1.dp)

                    Text(
                        text = "Semoga Keberkahan, dan Keselamatan Senantiasa Menyertai YANSPROJECT.ID & Ashab AJIBQOBUL.",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )

                    Button(
                        onClick = { showDetailDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryDarkTeal),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, AgedGold.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    ) {
                        Text(
                            text = "DIDO'AKEUN KU ABAH",
                            color = AgedGold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * SeamlessUnlimitedSholawatMarquee - Mathematical zero-gap infinite marquee.
 * Renders multiple identical layout copies side-by-side so when animation resets,
 * there is zero glitch, zero delay, and completely unlimited continuous scrolling.
 */
@Composable
fun SeamlessUnlimitedSholawatMarquee(
    text: String,
    textStyle: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
    speedDpPerSecond: Dp = 40.dp
) {
    val density = LocalDensity.current
    var unitWidthPx by remember { mutableStateOf(0) }

    val durationMs = if (unitWidthPx > 0) {
        val speedPxPerSec = with(density) { speedDpPerSecond.toPx() }
        ((unitWidthPx / speedPxPerSec) * 1000).toInt().coerceAtLeast(1000)
    } else {
        10000
    }

    val infiniteTransition = rememberInfiniteTransition(label = "SholawatInfiniteLoop")
    val currentOffsetX by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (unitWidthPx > 0) -unitWidthPx.toFloat() else -1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = durationMs,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "MarqueeOffset"
    )

    Box(
        modifier = modifier.clipToBounds(),
        contentAlignment = Alignment.CenterStart
    ) {
        androidx.compose.ui.layout.Layout(
            content = {
                // Render 4 copies side by side to guarantee seamless coverage across mobile & tablet screens
                repeat(4) {
                    Text(
                        text = text,
                        style = textStyle,
                        softWrap = false,
                        onTextLayout = { textLayoutResult ->
                            val w = textLayoutResult.size.width
                            if (w > 0 && w != unitWidthPx) {
                                unitWidthPx = w
                            }
                        }
                    )
                }
            }
        ) { measurables, constraints ->
            val placeables = measurables.map { it.measure(constraints.copy(maxWidth = Constraints.Infinity)) }
            val width = placeables.firstOrNull()?.width ?: 0
            val height = placeables.maxOfOrNull { it.height } ?: 0

            layout(constraints.maxWidth, height) {
                var x = currentOffsetX.toInt()
                placeables.forEach { placeable ->
                    placeable.placeRelative(x = x, y = 0)
                    x += width
                }
            }
        }
    }
}

