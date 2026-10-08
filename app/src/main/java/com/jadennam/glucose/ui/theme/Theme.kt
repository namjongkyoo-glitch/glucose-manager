package com.jadennam.glucose.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jadennam.glucose.domain.range.GlucoseLevel

// Soft card + gradient pill look (inspired by uiverse.io styles, implemented natively in Compose).
val Indigo = Color(0xFF4F46E5)
val Violet = Color(0xFF7C3AED)
val Teal = Color(0xFF14B8A6)

val AccentGradient = Brush.horizontalGradient(listOf(Indigo, Violet))

private val Light = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    secondary = Teal,
    onSecondary = Color.White,
    background = Color(0xFFF4F6FB),
    surface = Color.White,
    surfaceVariant = Color(0xFFEDEFF7),
    onSurfaceVariant = Color(0xFF5B6275),
    outline = Color(0xFFCBD0DD),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8B85FF),
    onPrimary = Color(0xFF14123A),
    secondary = Color(0xFF5EEAD4),
    background = Color(0xFF12131A),
    surface = Color(0xFF1C1E27),
    surfaceVariant = Color(0xFF272A36),
    onSurfaceVariant = Color(0xFFB4BACB),
    outline = Color(0xFF454A5C),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
)

object LevelColors {
    val Low = Color(0xFFE53935)
    val InTarget = Color(0xFF43A047)
    val High = Color(0xFFFB8C00)
    val VeryHigh = Color(0xFF8E24AA)

    fun of(level: GlucoseLevel): Color = when (level) {
        GlucoseLevel.LOW -> Low
        GlucoseLevel.IN_TARGET -> InTarget
        GlucoseLevel.HIGH -> High
        GlucoseLevel.VERY_HIGH -> VeryHigh
    }
}

@Composable
fun GlucoseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        shapes = AppShapes,
        content = content,
    )
}
