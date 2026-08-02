package com.yansproject.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieComposition
import com.yansproject.app.ui.AppTab
import kotlinx.coroutines.delay

/**
 * LottieSectionAnimation: Renders subtle, fluid Lottie animations when switching
 * between main dashboard sections or tabs in YANSPROJECT.ID ERP.
 */
object LottieAnimations {

    // 1. Subtle Wave Pulse Lottie JSON (Aura pulse effect)
    const val FLUID_TAB_WAVE_JSON = """
    {
      "v": "5.7.8",
      "fr": 60,
      "ip": 0,
      "op": 60,
      "w": 100,
      "h": 100,
      "nm": "fluid_wave",
      "ddd": 0,
      "assets": [],
      "layers": [
        {
          "ddd": 0,
          "ind": 1,
          "ty": 4,
          "nm": "Outer Ring",
          "sr": 1,
          "ks": {
            "o": { "a": 1, "k": [{ "t": 0, "s": [80] }, { "t": 30, "s": [100] }, { "t": 60, "s": [0] }] },
            "r": { "a": 0, "k": 0 },
            "p": { "a": 0, "k": [50, 50, 0] },
            "a": { "a": 0, "k": [0, 0, 0] },
            "s": { "a": 1, "k": [{ "t": 0, "s": [40, 40, 100] }, { "t": 60, "s": [110, 110, 100] }] }
          },
          "shapes": [
            {
              "ty": "el",
              "p": { "a": 0, "k": [0, 0] },
              "s": { "a": 0, "k": [80, 80] }
            },
            {
              "ty": "st",
              "c": { "a": 0, "k": [0.31, 0.82, 0.77, 1] },
              "w": 3,
              "lc": 2,
              "lj": 2
            }
          ]
        },
        {
          "ddd": 0,
          "ind": 2,
          "ty": 4,
          "nm": "Inner Gold Glow",
          "sr": 1,
          "ks": {
            "o": { "a": 1, "k": [{ "t": 0, "s": [100] }, { "t": 45, "s": [60] }, { "t": 60, "s": [0] }] },
            "r": { "a": 0, "k": 0 },
            "p": { "a": 0, "k": [50, 50, 0] },
            "a": { "a": 0, "k": [0, 0, 0] },
            "s": { "a": 1, "k": [{ "t": 0, "s": [20, 20, 100] }, { "t": 60, "s": [80, 80, 100] }] }
          },
          "shapes": [
            {
              "ty": "el",
              "p": { "a": 0, "k": [0, 0] },
              "s": { "a": 0, "k": [60, 60] }
            },
            {
              "ty": "fl",
              "c": { "a": 0, "k": [0.78, 0.63, 0.36, 1] }
            }
          ]
        }
      ]
    }
    """

    // 2. Section Transition Sparkle Lottie JSON
    const val SECTION_SPARKLE_JSON = """
    {
      "v": "5.7.8",
      "fr": 60,
      "ip": 0,
      "op": 45,
      "w": 120,
      "h": 120,
      "nm": "section_sparkle",
      "ddd": 0,
      "assets": [],
      "layers": [
        {
          "ddd": 0,
          "ind": 1,
          "ty": 4,
          "nm": "Sparkle 1",
          "sr": 1,
          "ks": {
            "o": { "a": 1, "k": [{ "t": 0, "s": [0] }, { "t": 15, "s": [100] }, { "t": 45, "s": [0] }] },
            "r": { "a": 1, "k": [{ "t": 0, "s": [0] }, { "t": 45, "s": [90] }] },
            "p": { "a": 0, "k": [60, 60, 0] },
            "a": { "a": 0, "k": [0, 0, 0] },
            "s": { "a": 1, "k": [{ "t": 0, "s": [0, 0, 100] }, { "t": 20, "s": [100, 100, 100] }, { "t": 45, "s": [40, 40, 100] }] }
          },
          "shapes": [
            {
              "ty": "sr",
              "sy": 2,
              "pt": { "a": 0, "k": 4 },
              "p": { "a": 0, "k": [0, 0] },
              "r": { "a": 0, "k": 0 },
              "ir": { "a": 0, "k": 6 },
              "is": { "a": 0, "k": 0 },
              "or": { "a": 0, "k": 20 },
              "os": { "a": 0, "k": 0 }
            },
            {
              "ty": "fl",
              "c": { "a": 0, "k": [0.31, 0.82, 0.77, 1] }
            }
          ]
        }
      ]
    }
    """
}

@Composable
fun SubtleLottieTabTransition(
    currentTab: AppTab,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp
) {
    var triggerKey by remember { mutableStateOf(currentTab) }
    var isVisible by remember { mutableStateOf(false) }

    val composition by rememberLottieComposition(
        spec = LottieCompositionSpec.JsonString(LottieAnimations.FLUID_TAB_WAVE_JSON)
    )

    LaunchedEffect(currentTab) {
        triggerKey = currentTab
        isVisible = true
        delay(700)
        isVisible = false
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.size(size),
            contentAlignment = Alignment.Center
        ) {
            LottieAnimation(
                composition = composition,
                iterations = 1,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun LottieSectionBadge(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp
) {
    val composition by rememberLottieComposition(
        spec = LottieCompositionSpec.JsonString(LottieAnimations.SECTION_SPARKLE_JSON)
    )

    LottieAnimation(
        composition = composition,
        iterations = com.airbnb.lottie.compose.LottieConstants.IterateForever,
        modifier = modifier.size(size)
    )
}

@Composable
fun LottieTabPulse(
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp
) {
    if (!isSelected) return

    val composition by rememberLottieComposition(
        spec = LottieCompositionSpec.JsonString(LottieAnimations.SECTION_SPARKLE_JSON)
    )

    LottieAnimation(
        composition = composition,
        iterations = com.airbnb.lottie.compose.LottieConstants.IterateForever,
        modifier = modifier.size(size)
    )
}

@Composable
fun LottieFilterChipPulse(
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 16.dp
) {
    if (!isSelected) return

    val composition by rememberLottieComposition(
        spec = LottieCompositionSpec.JsonString(LottieAnimations.SECTION_SPARKLE_JSON)
    )

    LottieAnimation(
        composition = composition,
        iterations = com.airbnb.lottie.compose.LottieConstants.IterateForever,
        modifier = modifier.size(size)
    )
}

