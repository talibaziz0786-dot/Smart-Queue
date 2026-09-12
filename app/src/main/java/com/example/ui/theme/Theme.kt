package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFA6C8FF),
    onPrimary = Color(0xFF003060),
    primaryContainer = BentoPrimaryDark,
    onPrimaryContainer = BentoPrimaryContainer,
    secondary = Color(0xFFB9C8DA),
    onSecondary = Color(0xFF233240),
    secondaryContainer = BentoLavenderContainer.copy(alpha = 0.25f),
    onSecondaryContainer = BentoLavenderBorder,
    tertiary = BentoLavenderContainer,
    onTertiary = BentoOnLavenderContainer,
    tertiaryContainer = Color(0xFF4A3477),
    onTertiaryContainer = BentoLavenderContainer,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    background = BentoDarkBackground,
    onBackground = BentoDarkTextPrimary,
    surface = BentoDarkSurface,
    onSurface = BentoDarkTextPrimary,
    surfaceVariant = BentoDarkSurfaceCard,
    onSurfaceVariant = BentoDarkTextSecondary,
    outline = BentoDarkBorder,
    outlineVariant = Color(0xFF43474E)
)

private val LightColorScheme = lightColorScheme(
    primary = BentoPrimary,
    onPrimary = Color.White,
    primaryContainer = BentoPrimaryContainer,
    onPrimaryContainer = BentoOnPrimaryContainer,
    secondary = Color(0xFF00639C),
    onSecondary = Color.White,
    secondaryContainer = BentoLavenderContainer,
    onSecondaryContainer = BentoOnLavenderContainer,
    tertiary = Color(0xFF65558F),
    onTertiary = Color.White,
    tertiaryContainer = BentoLavenderContainer,
    onTertiaryContainer = BentoOnLavenderContainer,
    error = AccentRose,
    onError = Color.White,
    background = BentoLightBackground,
    onBackground = BentoLightTextPrimary,
    surface = BentoLightSurface,
    onSurface = BentoLightTextPrimary,
    surfaceVariant = BentoLightSurfaceCard,
    onSurfaceVariant = BentoLightTextSecondary,
    outline = BentoLightBorder,
    outlineVariant = BentoSkyBorder
)

@Composable
fun SmartQueueTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

