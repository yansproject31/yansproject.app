package com.yansproject.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yansproject.app.ui.theme.DarkTealSurface
import com.yansproject.app.ui.theme.KitabBodyText
import com.yansproject.app.ui.theme.ambientGlow
import com.yansproject.app.ui.theme.glassCard

@Composable
fun LuxuryBookCover(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp)
            .glassCard()
            .ambientGlow(color = Color(0xFFD4AF37), radius = 12.dp, alpha = 0.25f)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        // Border kotak tipis warna Aged Gold dengan siku/sudut tambahan (corner accents)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val goldColor = Color(0xFFD4AF37)
            val strokeWidth = 1.5.dp.toPx()
            val accentLength = 20.dp.toPx()
            
            // Main outer border
            drawRect(
                color = goldColor,
                style = Stroke(width = strokeWidth)
            )
            
            // Top Left corner accents
            drawLine(goldColor, start = androidx.compose.ui.geometry.Offset(-strokeWidth, 8.dp.toPx()), end = androidx.compose.ui.geometry.Offset(accentLength, 8.dp.toPx()), strokeWidth = strokeWidth * 2)
            drawLine(goldColor, start = androidx.compose.ui.geometry.Offset(8.dp.toPx(), -strokeWidth), end = androidx.compose.ui.geometry.Offset(8.dp.toPx(), accentLength), strokeWidth = strokeWidth * 2)
            
            // Top Right corner accents
            drawLine(goldColor, start = androidx.compose.ui.geometry.Offset(size.width + strokeWidth, 8.dp.toPx()), end = androidx.compose.ui.geometry.Offset(size.width - accentLength, 8.dp.toPx()), strokeWidth = strokeWidth * 2)
            drawLine(goldColor, start = androidx.compose.ui.geometry.Offset(size.width - 8.dp.toPx(), -strokeWidth), end = androidx.compose.ui.geometry.Offset(size.width - 8.dp.toPx(), accentLength), strokeWidth = strokeWidth * 2)

            // Bottom Left corner accents
            drawLine(goldColor, start = androidx.compose.ui.geometry.Offset(-strokeWidth, size.height - 8.dp.toPx()), end = androidx.compose.ui.geometry.Offset(accentLength, size.height - 8.dp.toPx()), strokeWidth = strokeWidth * 2)
            drawLine(goldColor, start = androidx.compose.ui.geometry.Offset(8.dp.toPx(), size.height + strokeWidth), end = androidx.compose.ui.geometry.Offset(8.dp.toPx(), size.height - accentLength), strokeWidth = strokeWidth * 2)

            // Bottom Right corner accents
            drawLine(goldColor, start = androidx.compose.ui.geometry.Offset(size.width + strokeWidth, size.height - 8.dp.toPx()), end = androidx.compose.ui.geometry.Offset(size.width - accentLength, size.height - 8.dp.toPx()), strokeWidth = strokeWidth * 2)
            drawLine(goldColor, start = androidx.compose.ui.geometry.Offset(size.width - 8.dp.toPx(), size.height + strokeWidth), end = androidx.compose.ui.geometry.Offset(size.width - 8.dp.toPx(), size.height - accentLength), strokeWidth = strokeWidth * 2)

            // Dua lingkaran konsentris tipis warna Emas di tengah
            drawCircle(
                color = goldColor.copy(alpha = 0.15f),
                radius = 85.dp.toPx(),
                style = Stroke(width = 1.dp.toPx())
            )
            drawCircle(
                color = goldColor.copy(alpha = 0.3f),
                radius = 75.dp.toPx(),
                style = Stroke(width = 1.dp.toPx())
            )
        }

        // Teks judul di tengah dengan efek shadow pendaran cahaya
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = title,
                style = TextStyle(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    color = Color.White,
                    letterSpacing = 2.sp,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.ambientGlow(color = Color(0xFFD4AF37), radius = 8.dp, alpha = 0.45f)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = subtitle,
                style = TextStyle(
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                    fontSize = 14.sp,
                    color = Color(0xFFD4AF37),
                    textAlign = TextAlign.Center
                )
            )
        }
    }
}

@Composable
fun ManuscriptParagraph(
    text: String,
    fontSize: TextUnit = 16.sp,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = KitabBodyText.copy(
            fontSize = fontSize,
            lineHeight = fontSize * 1.65
        ),
        modifier = modifier.padding(bottom = 20.dp)
    )
}

@Composable
fun ManuscriptDropCap(
    letter: String,
    text: String,
    fontSize: TextUnit = 16.sp,
    textColor: Color = Color.Unspecified,
    accentColor: Color = Color(0xFFD4AF37),
    fontFamily: FontFamily = FontFamily.Serif,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .border(1.5.dp, accentColor, RoundedCornerShape(4.dp))
                .ambientGlow(color = accentColor, radius = 4.dp, alpha = 0.3f),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = letter,
                style = TextStyle(
                    fontFamily = fontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 34.sp,
                    color = accentColor
                )
            )
        }
        Text(
            text = text,
            style = TextStyle(
                fontFamily = fontFamily,
                fontSize = fontSize,
                lineHeight = fontSize * 1.65,
                color = if (textColor != Color.Unspecified) textColor else Color(0xFFE0E0E0),
                textAlign = TextAlign.Justify
            ),
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp)
        )
    }
}

@Composable
fun ManuscriptQuoteCard(
    quote: String,
    cardBgColor: Color = DarkTealSurface,
    textColor: Color = Color(0xFFD4AF37),
    accentColor: Color = Color(0xFFD4AF37),
    fontFamily: FontFamily = FontFamily.Serif,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
            .ambientGlow(color = accentColor, radius = 8.dp, alpha = 0.15f),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardBgColor
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.3f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            // Sisi kiri atas berikan Icon/Teks "" ukuran raksasa warna Emas/Accent
            Text(
                text = "“",
                style = TextStyle(
                    fontFamily = fontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 72.sp,
                    color = accentColor.copy(alpha = 0.25f),
                    lineHeight = 1.sp
                ),
                modifier = Modifier.align(Alignment.TopStart)
            )

            // Teks quote di tengah dengan huruf Italic Serif warna emas
            Text(
                text = quote,
                style = TextStyle(
                    fontFamily = fontFamily,
                    fontStyle = FontStyle.Italic,
                    fontSize = 18.sp,
                    lineHeight = 30.sp,
                    color = textColor,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 20.dp)
            )
        }
    }
}

@Composable
fun ManuscriptQuoteBlock(
    quote: String,
    cardBgColor: Color = DarkTealSurface,
    textColor: Color = Color(0xFFD4AF37),
    accentColor: Color = Color(0xFFD4AF37),
    fontFamily: FontFamily = FontFamily.Serif,
    modifier: Modifier = Modifier
) {
    ManuscriptQuoteCard(
        quote = quote,
        cardBgColor = cardBgColor,
        textColor = textColor,
        accentColor = accentColor,
        fontFamily = fontFamily,
        modifier = modifier
    )
}
