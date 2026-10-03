package dev.vitngan.harness.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme()
private val DarkColors = darkColorScheme()

/**
 * Minimal Material3 theme.
 *
 * Deliberately plain: M0-005 is a control and observation surface, not a
 * brand. Colour choices here are the stock Material 3 baseline so nothing
 * about the theme can be mistaken for product design.
 */
@Composable
fun HarnessTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}