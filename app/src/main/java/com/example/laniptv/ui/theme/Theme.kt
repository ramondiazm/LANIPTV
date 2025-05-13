package com.example.laniptv.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Esquema de colores para modo oscuro (usado principalmente para TV)
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF1976D2),         // Azul
    onPrimary = Color.White,
    primaryContainer = Color(0xFF0D47A1),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF00ACC1),        // Azul claro
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF006064),
    onSecondaryContainer = Color.White,
    tertiary = Color(0xFF7B1FA2),         // Púrpura
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF4A148C),
    onTertiaryContainer = Color.White,
    background = Color(0xFF121212),       // Gris muy oscuro
    onBackground = Color.White,
    surface = Color(0xFF1E1E1E),         // Gris oscuro
    onSurface = Color.White,
    surfaceVariant = Color(0xFF252525),
    onSurfaceVariant = Color.LightGray,
    error = Color(0xFFCF6679),
    onError = Color.Black
)

// Esquema de colores para modo claro (por si acaso)
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF2196F3),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBBDEFB),
    onPrimaryContainer = Color(0xFF0D47A1),
    secondary = Color(0xFF00BCD4),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB2EBF2),
    onSecondaryContainer = Color(0xFF006064),
    tertiary = Color(0xFF9C27B0),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE1BEE7),
    onTertiaryContainer = Color(0xFF4A148C),
    background = Color(0xFFF5F5F5),
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFEEEEEE),
    onSurfaceVariant = Color(0xFF666666),
    error = Color(0xFFB00020),
    onError = Color.White
)

@Composable
fun LanIPTVTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Siempre usar colores oscuros para Android TV
    // o Usar siempre el tema oscuro para aplicaciones de TV
    content: @Composable () -> Unit
) {
    // Para TV, usamos siempre el tema oscuro
    val colorScheme = DarkColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}