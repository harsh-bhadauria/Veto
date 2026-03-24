package com.raven.veto.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Veto color system that adapts to light and dark themes
 * - Primary: Mint green (Veto's signature color)
 * - Secondary: Teal (accent)
 * - Background/Surface: Black (in dark theme), Light gray (in light theme)
 * - Anki stats: Keep standard blue, red, green for consistency
 */
data class VetoColors(
    // Hero card colors
    val heroBackground: Color,
    val heroText: Color,

    // Button colors
    val buttonBackground: Color,
    val buttonText: Color,

    // Stats colors (Anki standard)
    val ankiNewColor: Color = AnkiNewBlue,
    val ankiLearnColor: Color = AnkiLearnRed,
    val ankiReviewColor: Color = AnkiReviewGreen,
)

@Composable
fun vetoColors(): VetoColors {
    val isDark = isSystemInDarkTheme()

    return if (isDark) {
        VetoColors(
            heroBackground = VetoMint40,
            heroText = Color.White,
            buttonBackground = VetoMint40,
            buttonText = Color.White,
        )
    } else {
        VetoColors(
            heroBackground = VetoMint60,
            heroText = Color.White,
            buttonBackground = VetoMint60,
            buttonText = Color.White,
        )
    }
}


