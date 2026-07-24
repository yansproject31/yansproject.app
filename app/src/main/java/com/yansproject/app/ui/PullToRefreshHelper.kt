package com.yansproject.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yansproject.app.ui.theme.AgedGold
import com.yansproject.app.ui.theme.DarkTealSurface
import com.yansproject.app.ui.theme.HighlightSoftCyan
import com.yansproject.app.ui.theme.SecondaryShadowBlackTeal

@Composable
fun PullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val triggerThreshold = with(density) { 80.dp.toPx() }
    
    var pullOffset by remember { mutableStateOf(0f) }
    var hasHapticked by remember { mutableStateOf(false) }
    
    val animatedOffset by animateFloatAsState(
        targetValue = if (isRefreshing) triggerThreshold else pullOffset,
        animationSpec = spring(stiffness = Spring.StiffnessLow)
    )
    
    val pullProgress = (animatedOffset / triggerThreshold).coerceIn(0f, 1.5f)

    // Infinite rotation for active sync state
    val infiniteTransition = rememberInfiniteTransition(label = "pull_sync_rotation")
    val spinningRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spin"
    )

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                return if (available.y < 0 && pullOffset > 0) {
                    val prevOffset = pullOffset
                    pullOffset = (pullOffset + available.y).coerceAtLeast(0f)
                    if (pullOffset < triggerThreshold) hasHapticked = false
                    Offset(0f, pullOffset - prevOffset)
                } else {
                    Offset.Zero
                }
            }
            
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                return if (available.y > 0) {
                    val prevOffset = pullOffset
                    val resistance = (1f - (pullOffset / (triggerThreshold * 2.5f))).coerceIn(0.15f, 1f)
                    pullOffset = (pullOffset + available.y * resistance).coerceAtMost(triggerThreshold * 1.5f)
                    if (pullOffset >= triggerThreshold && !hasHapticked) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        hasHapticked = true
                    }
                    Offset(0f, pullOffset - prevOffset)
                } else {
                    Offset.Zero
                }
            }
            
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (pullOffset >= triggerThreshold && !isRefreshing) {
                    onRefresh()
                }
                pullOffset = 0f
                hasHapticked = false
                return super.onPostFling(consumed, available)
            }
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
    ) {
        // Main content offsetted down
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset(y = with(density) { animatedOffset.toDp() })
        ) {
            content()
        }
        
        // Luxury Custom Pull To Refresh Indicator
        if (animatedOffset > 4f) {
            val scaleFraction = (pullProgress).coerceIn(0.6f, 1.0f)
            val iconRotation = if (isRefreshing) spinningRotation else pullProgress * 360f
            val statusText = when {
                isRefreshing -> "Menyinkronkan Cloud Firestore..."
                pullProgress >= 1.0f -> "Lepaskan untuk Sinkronisasi"
                else -> "Tarik untuk Sinkronisasi"
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = with(density) { (animatedOffset * 0.45f).toDp() - 10.dp })
                    .scale(scaleFraction)
                    .shadow(16.dp, RoundedCornerShape(24.dp), ambientColor = AgedGold, spotColor = HighlightSoftCyan)
                    .clip(RoundedCornerShape(24.dp))
                    .background(DarkTealSurface.copy(alpha = 0.95f))
                    .border(
                        width = 1.dp,
                        color = if (pullProgress >= 1.0f || isRefreshing) AgedGold else HighlightSoftCyan.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = AgedGold,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Sync,
                            contentDescription = "Sync",
                            tint = if (pullProgress >= 1.0f) AgedGold else HighlightSoftCyan,
                            modifier = Modifier
                                .size(18.dp)
                                .rotate(iconRotation)
                        )
                    }

                    Text(
                        text = statusText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (pullProgress >= 1.0f || isRefreshing) AgedGold else Color.White,
                        letterSpacing = 0.3.sp
                    )
                }
            }
        }
    }
}

