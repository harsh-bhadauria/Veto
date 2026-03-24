package com.raven.veto.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = VetoMint80,
    secondary = VetoMint40,
    tertiary = VetoTeal,
    background = VetoBlack,
    surface = VetoGray,
    onPrimary = VetoBlack,
    onSecondary = VetoBlack,
    onBackground = VetoMint80,
    onSurface = VetoMint80
)

private val LightColorScheme = lightColorScheme(
    primary = VetoMint40,
    secondary = VetoMint20,
    tertiary = VetoTeal,
    background = VetoLightBackground,
    surface = VetoLightSurface,
    onBackground = VetoBlack,
    onSurface = VetoBlack

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun VetoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}