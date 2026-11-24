package com.trucca.alarm.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DarkBg = Color(0xFF080D16)
val CardSurface = Color(0xFF111A2B)
val CardSurfaceAlt = Color(0xFF162238)
val BlueStart = Color(0xFF2667D6)
val BlueMid = Color(0xFF164396)
val BlueEnd = Color(0xFF0A204A)
val AccentYellow = Color(0xFFFACC15)
val AccentBlue = Color(0xFF60A5FA)
val SoftWhite = Color(0xFFF1F5F9)
val MutedText = Color(0xFF94A3B8)

private val AppDarkColors = darkColorScheme(
    primary = Color(0xFF3B82F6),
    secondary = AccentYellow,
    background = DarkBg,
    surface = CardSurface,
    onPrimary = Color.White,
    onSecondary = Color(0xFF0F172A),
    onBackground = SoftWhite,
    onSurface = SoftWhite
)

@Composable
fun TrucCaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppDarkColors,
        content = content
    )
}
