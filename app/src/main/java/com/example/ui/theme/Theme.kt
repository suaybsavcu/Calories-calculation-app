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
    primary = ForestGreen80,
    secondary = MintSecondary80,
    tertiary = WarmTangerine80,
    background = Color(0xFF101512),
    surface = Color(0xFF161E19),
    surfaceVariant = Color(0xFF222C25),
    onPrimary = Color(0xFF00391A),
    onSecondary = Color(0xFF003827),
    onTertiary = Color(0xFF4A2800),
    onBackground = Color(0xFFE1E4DF),
    onSurface = Color(0xFFE1E4DF)
)

private val LightColorScheme = lightColorScheme(
    primary = ForestGreen40,
    secondary = MintSecondary40,
    tertiary = WarmTangerine40,
    background = Color(0xFFF7FAF7),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEAF1EC),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF161D18),
    onSurface = Color(0xFF161D18)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
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
