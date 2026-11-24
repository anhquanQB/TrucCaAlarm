package com.trucca.alarm.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trucca.alarm.ui.theme.LocalGlassPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class WeatherKind(val label: String) {
    SUN_STRONG("Nắng gắt"),
    SUN_MILD("Nắng nhẹ"),
    CLOUDY("Có mây"),
    OVERCAST("Âm u"),
    RAIN_LIGHT("Mưa nhỏ"),
    RAIN_HEAVY("Mưa to")
}

data class WeatherInfo(
    val tempC: Int,
    val windKmh: Int,
    val kind: WeatherKind,
    val isDay: Boolean,
    val locationName: String = "Quảng Trạch"
)

suspend fun fetchQuangTrachWeather(): WeatherInfo? = withContext(Dispatchers.IO) {
    try {
        val url = URL(
            "https://api.open-meteo.com/v1/forecast?latitude=17.70&longitude=106.45" +
                "&current=temperature_2m,weather_code,is_day,wind_speed_10m&timezone=Asia%2FHo_Chi_Minh"
        )
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000
            readTimeout = 5000
            requestMethod = "GET"
        }
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        val current = JSONObject(body).getJSONObject("current")
        val temp = kotlin.math.round(current.getDouble("temperature_2m")).toInt()
        val code = current.getInt("weather_code")
        val isDay = current.optInt("is_day", 1) == 1
        val wind = kotlin.math.round(current.optDouble("wind_speed_10m", 0.0)).toInt()
        val kind = classifyWeather(code, temp, isDay)
        WeatherInfo(tempC = temp, windKmh = wind, kind = kind, isDay = isDay)
    } catch (_: Exception) {
        null
    }
}

private fun classifyWeather(code: Int, tempC: Int, isDay: Boolean): WeatherKind = when {
    code in listOf(65, 67, 81, 82, 95, 96, 99) -> WeatherKind.RAIN_HEAVY
    code in listOf(51, 53, 55, 56, 57, 61, 63, 80) -> WeatherKind.RAIN_LIGHT
    code in listOf(45, 48, 3) -> WeatherKind.OVERCAST
    code in listOf(1, 2) -> WeatherKind.CLOUDY
    code == 0 && isDay && tempC >= 32 -> WeatherKind.SUN_STRONG
    else -> if (isDay) WeatherKind.SUN_MILD else WeatherKind.CLOUDY
}

/**
 * Subtle translucent background weather animation (non-intrusive, never obscures text).
 */
@Composable
fun WeatherAtmosphereOverlay(
    weather: WeatherInfo?,
    modifier: Modifier = Modifier
) {
    if (weather == null) return
    val p = LocalGlassPalette.current
    val infinite = rememberInfiniteTransition(label = "weather_bg")
    val t by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "weather_t"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        when (weather.kind) {
            WeatherKind.SUN_STRONG, WeatherKind.SUN_MILD -> {
                val intensity = if (weather.kind == WeatherKind.SUN_STRONG) 0.24f else 0.14f
                val sunCenter = Offset(w * 0.88f, h * 0.08f)
                val sunRadius = w * (if (weather.kind == WeatherKind.SUN_STRONG) 0.65f else 0.48f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFDE68A).copy(alpha = intensity),
                            p.accent.copy(alpha = intensity * 0.35f),
                            Color.Transparent
                        ),
                        center = sunCenter,
                        radius = sunRadius
                    ),
                    center = sunCenter,
                    radius = sunRadius
                )
                val rayCount = if (weather.kind == WeatherKind.SUN_STRONG) 12 else 7
                for (i in 0 until rayCount) {
                    val angle = 1.65f + i * 0.14f
                    val wave = 0.55f + 0.45f * sin(t * 2f * PI.toFloat() * (1f + (i % 3)) + i)
                    val len = sunRadius * (0.7f + 0.3f * wave)
                    drawLine(
                        brush = Brush.linearGradient(
                            colors = listOf(Color.White.copy(alpha = intensity * wave), Color.Transparent),
                            start = sunCenter,
                            end = Offset(sunCenter.x + cos(angle) * len, sunCenter.y + sin(angle) * len)
                        ),
                        start = sunCenter,
                        end = Offset(sunCenter.x + cos(angle) * len, sunCenter.y + sin(angle) * len),
                        strokeWidth = if (weather.kind == WeatherKind.SUN_STRONG) 3.0f else 1.8f,
                        cap = StrokeCap.Round
                    )
                }
            }

            WeatherKind.CLOUDY, WeatherKind.OVERCAST -> {
                val cloudAlpha = if (weather.kind == WeatherKind.OVERCAST) 0.14f else 0.09f
                val cloudCount = if (weather.kind == WeatherKind.OVERCAST) 5 else 3
                for (i in 0 until cloudCount) {
                    val speed = 0.25f + (i % 2) * 0.15f
                    val cx = ((t * speed * w * 1.4f + i * w * 0.38f) % (w * 1.4f)) - w * 0.2f
                    val cy = h * (0.09f + i * 0.07f)
                    val r = w * (0.24f + (i % 3) * 0.06f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.White.copy(alpha = cloudAlpha), Color.Transparent),
                            center = Offset(cx, cy),
                            radius = r
                        ),
                        center = Offset(cx, cy),
                        radius = r
                    )
                }
            }

            WeatherKind.RAIN_LIGHT, WeatherKind.RAIN_HEAVY -> {
                val isHeavy = weather.kind == WeatherKind.RAIN_HEAVY
                val dropCount = if (isHeavy) 42 else 20
                val dropAlpha = if (isHeavy) 0.24f else 0.14f
                val dropLen = if (isHeavy) 34f else 20f
                val speedMult = if (isHeavy) 9f else 5f
                for (i in 0 until dropCount) {
                    val seedX = ((i * 73) % 100) / 100f
                    val seedY = ((i * 37) % 100) / 100f
                    val progress = (seedY + t * speedMult) % 1f
                    val x = seedX * w - progress * 45f
                    val y = progress * h
                    drawLine(
                        color = p.accent.copy(alpha = dropAlpha),
                        start = Offset(x, y),
                        end = Offset(x - (if (isHeavy) 7f else 3f), y + dropLen),
                        strokeWidth = if (isHeavy) 2.2f else 1.3f,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

/**
 * Compact glass pill showing temperature + mini animated weather icon.
 */
@Composable
fun WeatherPillBadge(
    weather: WeatherInfo?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = LocalGlassPalette.current
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.12f))
            .border(1.dp, Color.White.copy(alpha = 0.26f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        MiniWeatherGlyph(kind = weather?.kind ?: WeatherKind.CLOUDY)
        Text(
            text = if (weather != null) "${weather.tempC}°C • ${weather.kind.label}" else "Đang cập nhật...",
            color = p.softWhite,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun MiniWeatherGlyph(kind: WeatherKind) {
    val p = LocalGlassPalette.current
    Canvas(modifier = Modifier.size(16.dp)) {
        val w = size.width
        val h = size.height
        when (kind) {
            WeatherKind.SUN_STRONG, WeatherKind.SUN_MILD -> {
                drawCircle(color = Color(0xFFFDE047), radius = w * 0.28f, center = Offset(w * 0.5f, h * 0.5f))
                for (i in 0 until 6) {
                    val a = i * (PI.toFloat() / 3f)
                    drawLine(
                        color = Color(0xFFFDE047),
                        start = Offset(w * 0.5f + cos(a) * w * 0.34f, h * 0.5f + sin(a) * w * 0.34f),
                        end = Offset(w * 0.5f + cos(a) * w * 0.46f, h * 0.5f + sin(a) * w * 0.46f),
                        strokeWidth = 1.6f,
                        cap = StrokeCap.Round
                    )
                }
            }
            WeatherKind.CLOUDY, WeatherKind.OVERCAST -> {
                drawCircle(color = p.softWhite.copy(alpha = 0.85f), radius = w * 0.24f, center = Offset(w * 0.40f, h * 0.56f))
                drawCircle(color = p.softWhite.copy(alpha = 0.92f), radius = w * 0.28f, center = Offset(w * 0.58f, h * 0.48f))
            }
            WeatherKind.RAIN_LIGHT, WeatherKind.RAIN_HEAVY -> {
                drawCircle(color = p.softWhite.copy(alpha = 0.85f), radius = w * 0.22f, center = Offset(w * 0.40f, h * 0.42f))
                drawCircle(color = p.softWhite.copy(alpha = 0.92f), radius = w * 0.26f, center = Offset(w * 0.58f, h * 0.38f))
                drawLine(color = p.accent, start = Offset(w * 0.36f, h * 0.72f), end = Offset(w * 0.30f, h * 0.92f), strokeWidth = 1.8f, cap = StrokeCap.Round)
                drawLine(color = p.accent, start = Offset(w * 0.62f, h * 0.72f), end = Offset(w * 0.56f, h * 0.92f), strokeWidth = 1.8f, cap = StrokeCap.Round)
            }
        }
    }
}
