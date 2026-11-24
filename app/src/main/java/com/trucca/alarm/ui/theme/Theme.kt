package com.trucca.alarm.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.trucca.alarm.model.GlassThemeId
import com.trucca.alarm.model.LunarCalendarUtil

data class GlassPalette(
    val id: GlassThemeId,
    val reflectionPrimary: Color,
    val reflectionSecondary: Color,
    val accent: Color,
    val heroStart: Color,
    val heroMid: Color,
    val heroEnd: Color,
    val highlightYellow: Color, // Harmonized highlight color matching each glass tone
    val softWhite: Color,
    val mutedText: Color
)

fun paletteFor(id: GlassThemeId): GlassPalette = when (id) {
    GlassThemeId.OCEAN -> GlassPalette(
        id = id,
        reflectionPrimary = Color(0xFF38BDF8),
        reflectionSecondary = Color(0xFF3B82F6),
        accent = Color(0xFF7DD3FC),
        heroStart = Color(0xFF38BDF8),
        heroMid = Color(0xFF2563EB),
        heroEnd = Color(0xFF0EA5E9),
        highlightYellow = Color(0xFF67E8F9),
        softWhite = Color(0xFFF0F9FF),
        mutedText = Color(0xFFBAE6FD)
    )
    GlassThemeId.EMERALD -> GlassPalette(
        id = id,
        reflectionPrimary = Color(0xFF34D399),
        reflectionSecondary = Color(0xFF2DD4BF),
        accent = Color(0xFF6EE7B7),
        heroStart = Color(0xFF34D399),
        heroMid = Color(0xFF10B981),
        heroEnd = Color(0xFF06B6D4),
        highlightYellow = Color(0xFF5EEAD4),
        softWhite = Color(0xFFECFDF5),
        mutedText = Color(0xFFA7F3D0)
    )
    GlassThemeId.VIOLET -> GlassPalette(
        id = id,
        reflectionPrimary = Color(0xFFC084FC),
        reflectionSecondary = Color(0xFFF472B6),
        accent = Color(0xFFE9D5FF),
        heroStart = Color(0xFFC084FC),
        heroMid = Color(0xFFA855F7),
        heroEnd = Color(0xFFEC4899),
        highlightYellow = Color(0xFFF0ABFC),
        softWhite = Color(0xFFFAF5FF),
        mutedText = Color(0xFFDDD6FE)
    )
    GlassThemeId.AMBER -> GlassPalette(
        id = id,
        reflectionPrimary = Color(0xFFFBBF24),
        reflectionSecondary = Color(0xFFFB923C),
        accent = Color(0xFFFDE68A),
        heroStart = Color(0xFFFBBF24),
        heroMid = Color(0xFFF59E0B),
        heroEnd = Color(0xFFFB7185),
        highlightYellow = Color(0xFFFDE68A),
        softWhite = Color(0xFFFFFBEB),
        mutedText = Color(0xFFFDE68A).copy(alpha = 0.78f)
    )
    GlassThemeId.CRIMSON -> GlassPalette(
        id = id,
        reflectionPrimary = Color(0xFFF472B6),
        reflectionSecondary = Color(0xFFFB7185),
        accent = Color(0xFFFBCFE8),
        heroStart = Color(0xFFF472B6),
        heroMid = Color(0xFFEC4899),
        heroEnd = Color(0xFFF43F5E),
        highlightYellow = Color(0xFFFDA4AF),
        softWhite = Color(0xFFFFF1F2),
        mutedText = Color(0xFFFECDD3)
    )
    GlassThemeId.TITANIUM -> GlassPalette(
        id = id,
        reflectionPrimary = Color(0xFFE2E8F0),
        reflectionSecondary = Color(0xFF94A3B8),
        accent = Color(0xFFF8FAFC),
        heroStart = Color(0xFFE2E8F0),
        heroMid = Color(0xFFCBD5E1),
        heroEnd = Color(0xFF94A3B8),
        highlightYellow = Color(0xFFF1F5F9),
        softWhite = Color(0xFFFFFFFF),
        mutedText = Color(0xFFCBD5E1)
    )
}

val LocalGlassPalette = staticCompositionLocalOf { paletteFor(GlassThemeId.OCEAN) }

@Composable
fun TrucCaTheme(
    themeId: GlassThemeId = GlassThemeId.OCEAN,
    content: @Composable () -> Unit
) {
    val palette = paletteFor(themeId)
    val scheme = darkColorScheme(
        primary = palette.accent,
        secondary = palette.highlightYellow,
        background = Color(0xFF0B111E),
        surface = Color(0xFF141E30),
        onPrimary = Color(0xFF061325),
        onSecondary = Color(0xFF0F172A),
        onBackground = palette.softWhite,
        onSurface = palette.softWhite
    )
    CompositionLocalProvider(LocalGlassPalette provides palette) {
        MaterialTheme(
            colorScheme = scheme,
            content = content
        )
    }
}

@Composable
fun LiquidGlassBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val p = LocalGlassPalette.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF131D31), Color(0xFF0A101D), Color(0xFF0E1729))
                )
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(p.reflectionPrimary.copy(alpha = 0.34f), Color.Transparent),
                    center = Offset(size.width * 0.18f, size.height * 0.14f),
                    radius = size.width * 0.65f
                ),
                center = Offset(size.width * 0.18f, size.height * 0.14f),
                radius = size.width * 0.65f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(p.reflectionSecondary.copy(alpha = 0.28f), Color.Transparent),
                    center = Offset(size.width * 0.86f, size.height * 0.36f),
                    radius = size.width * 0.58f
                ),
                center = Offset(size.width * 0.86f, size.height * 0.36f),
                radius = size.width * 0.58f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(p.reflectionPrimary.copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(size.width * 0.28f, size.height * 0.84f),
                    radius = size.width * 0.62f
                ),
                center = Offset(size.width * 0.28f, size.height * 0.84f),
                radius = size.width * 0.62f
            )
        }
        content()
    }
}

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 26.dp,
    alphaBoost: Float = 0f,
    content: @Composable BoxScope.() -> Unit
) {
    val p = LocalGlassPalette.current
    val shape = RoundedCornerShape(cornerRadius)
    val clearGlassFill = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = (0.17f + alphaBoost).coerceAtMost(0.28f)),
            Color.White.copy(alpha = (0.06f + alphaBoost * 0.4f).coerceAtMost(0.14f)),
            p.reflectionPrimary.copy(alpha = 0.06f)
        ),
        start = Offset(0f, 0f),
        end = Offset(800f, 800f)
    )
    val specularRim = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.72f),
            p.reflectionPrimary.copy(alpha = 0.45f),
            Color.White.copy(alpha = 0.16f),
            p.reflectionSecondary.copy(alpha = 0.62f)
        ),
        start = Offset(0f, 0f),
        end = Offset(700f, 700f)
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(clearGlassFill)
            .border(1.1.dp, specularRim, shape),
        content = content
    )
}
