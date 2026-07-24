package com.yansproject.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yansproject.app.ui.theme.*

@Composable
fun EmptyStateView(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    val boxShape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .shadow(
                elevation = 6.dp,
                shape = boxShape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.4f),
                spotColor = AgedGold.copy(alpha = 0.15f)
            )
            .clip(boxShape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        SurfaceDarkTeal.copy(alpha = 0.9f),
                        SecondaryShadowBlackTeal.copy(alpha = 0.95f)
                    )
                )
            )
            .border(
                border = BorderStroke(
                    width = 1.2.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            AgedGold.copy(alpha = 0.3f),
                            Color.Transparent
                        )
                    )
                ),
                shape = boxShape
            )
            .padding(vertical = 36.dp, horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(AgedGold.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = AgedGold,
                    modifier = Modifier.size(32.dp)
                )
            }
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Text(
                text = description,
                fontSize = 12.sp,
                color = TextSecondary.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.9f),
                lineHeight = 18.sp
            )
        }
    }
}
