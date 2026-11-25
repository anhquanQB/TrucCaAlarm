package com.trucca.alarm.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Chân mờ trên và dưới mỏng nhất, bằng nhau tuyệt đối (mặc định 16.dp)
 * và áp dụng đồng bộ cho mọi khung cuộn trong app.
 */
fun Modifier.uniformThinVerticalFade(
    fadeHeight: Dp = 16.dp
): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        val fadePx = fadeHeight.toPx().coerceAtMost(size.height / 2f)
        if (fadePx > 0f && size.height > 0f && size.width > 0f) {
            val topRatio = (fadePx / size.height).coerceIn(0f, 0.49f)
            val bottomRatio = (1f - (fadePx / size.height)).coerceIn(0.51f, 1f)

            drawRect(
                brush = Brush.verticalGradient(
                    0.0f to Color.Transparent,
                    topRatio to Color.Black,
                    bottomRatio to Color.Black,
                    1.0f to Color.Transparent
                ),
                blendMode = BlendMode.DstIn
            )
        }
    }
