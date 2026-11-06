package ru.samokontrol.diary.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Teal700 = Color(0xFF0F766E)
val Teal600 = Color(0xFF0D9488)
val Teal100 = Color(0xFFCCFBF1)
val Ink = Color(0xFF0F1F1D)
val Paper = Color(0xFFF8FAF9)
val Danger = Color(0xFFB3261E)
val Amber = Color(0xFFB45309)

private val LightColors = lightColorScheme(
    primary = Teal700,
    onPrimary = Color.White,
    primaryContainer = Teal100,
    onPrimaryContainer = Color(0xFF042F2C),
    secondary = Teal600,
    onSecondary = Color.White,
    background = Paper,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE6EFEC),
    onSurfaceVariant = Color(0xFF43514E),
    error = Danger,
    outline = Color(0xFF9AA8A5),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5EEAD4),
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF115E56),
    onPrimaryContainer = Color(0xFFCCFBF1),
    secondary = Color(0xFF5EEAD4),
    background = Color(0xFF0B1513),
    onBackground = Color(0xFFE6EFEC),
    surface = Color(0xFF10201D),
    onSurface = Color(0xFFE6EFEC),
    surfaceVariant = Color(0xFF1C2F2B),
    onSurfaceVariant = Color(0xFFB7C9C5),
    error = Color(0xFFFFB4AB),
)

@Composable
fun SamokontrolTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}