package com.trucca.alarm.ui.theme

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

data class GlassPalette(
    val id: GlassThemeId,
    val bgDeep: Color,
    val glowTopLeft: Color,
    val glowBottomRight: Color,
    val heroStart: Color,
    val heroMid: Color,
    val heroEnd: Color,
    val accent: Color,
    val highlightYellow: Color = Color(0xFFFACC15),
    val softWhite: Color = Color(0xFFF4F7FB),
    val mutedText: Color = Color(0xFFA6B4CC)
)

fun paletteFor(id: GlassThemeId): GlassPalette = when (id) {
    GlassThemeId.OCEAN -> GlassPalette(
        id = id,
        bgDeep = Color(0xFF060A13),
        glowTopLeft = Color(0xFF2563EB),
        glowBottomRight = Color(0xFF0EA5E9),
        heroStart = Color(0xFF2B6EEB),
        heroMid = Color(0xFF17449B),
        heroEnd = Color(0xFF0A204A),
        accent = Color(0xFF60A5FA)
    )
    GlassThemeId.EMERALD -> GlassPalette(
        id = id,
        bgDeep = Color(0xFF050E0C),
        glowTopLeft = Color(0xFF10B981),
        glowBottomRight = Color(0xFF06B6D4),
        heroStart = Color(0xFF059669),
        heroMid = Color(0xFF047857),
        heroEnd = Color(0xFF064E3B),
        accent = Color(0xFF34D399)
    )
    GlassThemeId.VIOLET -> GlassPalette(
        id = id,
        bgDeep = Color(0xFF090614),
        glowTopLeft = Color(0xFF8B5CF6),
        glowBottomRight = Color(0xFFEC4899),
        heroStart = Color(0xFF7C3AED),
        heroMid = Color(0xFF5B21B6),
        heroEnd = Color(0xFF2E1065),
        accent = Color(0xFFC084FC)
    )
    GlassThemeId.AMBER -> GlassPalette(
        id = id,
        bgDeep = Color(0xFF100A05),
        glowTopLeft = Color(0xFFF59E0B),
        glowBottomRight = Color(0xFFEF4444),
        heroStart = Color(0xFFD97706),
        heroMid = Color(0xFFB45309),
        heroEnd = Color(0xFF451A03),
        accent = Color(0xFFFBBF24)
    )
    GlassThemeId.CRIMSON -> GlassPalette(
        id = id,
        bgDeep = Color(0xFF11060A),
        glowTopLeft = Color(0xFFF43F5E),
        glowBottomRight = Color(0xFF8B5CF6),
        heroStart = Color(0xFFE11D48),
        heroMid = Color(0xFF9F1239),
        heroEnd = Color(0xFF4C0519),
        accent = Color(0xFFFB7185)
    )
    GlassThemeId.TITANIUM -> GlassPalette(
        id = id,
        bgDeep = Color(0xFF080A0E),
        glowTopLeft = Color(0xFF94A3B8),
        glowBottomRight = Color(0xFF38BDF8),
        heroStart = Color(0xFF475569),
        heroMid = Color(0xFF1E293B),
        heroEnd = Color(0xFF0F172A),
        accent = Color(0xFFCBD5E1)
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
        background = palette.bgDeep,
        surface = Color(0xFF111827),
        onPrimary = Color(0xFF07101F),
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

/**
 * Lightweight ambient aurora backdrop for Liquid Glass depth (zero heavy GPU blur/animation).
 */
@Composable
fun LiquidGlassBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val p = LocalGlassPalette.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(p.bgDeep)
            .background(
                Brush.radialGradient(
                    colors = listOf(p.glowTopLeft.copy(alpha = 0.28f), Color.Transparent),
                    center = Offset(120f, 180f),
                    radius = 780f
                )
            )
            .background(
                Brush.radialGradient(
                    colors = listOf(p.glowBottomRight.copy(alpha = 0.20f), Color.Transparent),
                    center = Offset(920f, 1680f),
                    radius = 850f
                )
            ),
        content = content
    )
}

/**
 * Translucent iOS-inspired Liquid Glass card with specular rim highlight.
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    alphaBoost: Float = 0f,
    content: @Composable BoxScope.() -> Unit
) {
    val p = LocalGlassPalette.current
    val shape = RoundedCornerShape(cornerRadius)
    val surfaceGradient = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = (0.14f + alphaBoost).coerceAtMost(0.28f)),
            Color.White.copy(alpha = (0.05f + alphaBoost * 0.5f).coerceAtMost(0.14f))
        )
    )
    val rimGradient = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.34f),
            Color.White.copy(alpha = 0.08f),
            p.accent.copy(alpha = 0.28f)
        )
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(surfaceGradient)
            .border(1.dp, rimGradient, shape),
        content = content
    )
}
