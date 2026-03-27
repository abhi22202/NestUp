package com.example.nestup.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SpotifyBlack = Color(0xFF050505)
private val SpotifySurface = Color(0xFF111312)
private val SpotifySurfaceRaised = Color(0xFF1A1F1D)
private val SpotifyGreen = Color(0xFF1ED760)
private val SpotifyGreenMuted = Color(0xFF159F47)
private val SpotifyText = Color(0xFFF3F7F3)
private val SpotifyTextMuted = Color(0xFFA6B4A9)
private val SpotifyOutline = Color(0xFF2C3D34)
private val SpotifyError = Color(0xFFFF7B7B)

private val nestUpColorScheme: ColorScheme = darkColorScheme(
    primary = SpotifyGreen,
    onPrimary = SpotifyBlack,
    primaryContainer = SpotifyGreenMuted,
    onPrimaryContainer = SpotifyText,
    secondary = Color(0xFF34C97B),
    onSecondary = SpotifyBlack,
    secondaryContainer = Color(0xFF143222),
    onSecondaryContainer = SpotifyText,
    tertiary = Color(0xFF6EE7A8),
    onTertiary = SpotifyBlack,
    background = SpotifyBlack,
    onBackground = SpotifyText,
    surface = SpotifySurface,
    onSurface = SpotifyText,
    surfaceVariant = SpotifySurfaceRaised,
    onSurfaceVariant = SpotifyTextMuted,
    outline = SpotifyOutline,
    error = SpotifyError,
    onError = SpotifyBlack,
    errorContainer = Color(0xFF4D1F1F),
    onErrorContainer = SpotifyText,
)

@Composable
fun NestUpTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = nestUpColorScheme,
        typography = Typography(),
        content = content,
    )
}
