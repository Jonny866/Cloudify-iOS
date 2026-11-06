package com.cloudify.music.core.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Dark Palette (Black + Warm Dark Gray)
val DeepBlack = Color(0xFF090810)
val DarkSurface = Color(0xFF161320)
val DarkSurfaceVariant = Color(0xFF221C30)
val DarkBorder = Color(0xFF2E2642)

data class AppThemeColors(
    val primary: Color,
    val dark: Color,
    val light: Color,
    val glow: Color,
    val onPrimary: Color = DeepBlack
)

val VioletColors = AppThemeColors(
    primary = Color(0xFF8B5CF6),
    dark = Color(0xFF7C3AED),
    light = Color(0xFFA78BFA),
    glow = Color(0xFFC4B5FD),
    onPrimary = DeepBlack
)

val YellowColors = AppThemeColors(
    primary = Color(0xFFFACC15),
    dark = Color(0xFFEAB308),
    light = Color(0xFFFDE047),
    glow = Color(0xFFFEF08A),
    onPrimary = DeepBlack
)

val LocalAppThemeColors = staticCompositionLocalOf { VioletColors }

// Dynamic Accessors that evaluate in Composable contexts
val VioletPrimary: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalAppThemeColors.current.primary

val VioletDark: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalAppThemeColors.current.dark

val VioletLight: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalAppThemeColors.current.light

val VioletGlow: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalAppThemeColors.current.glow

val isYellowTheme: Boolean
    @Composable
    @ReadOnlyComposable
    get() = LocalAppThemeColors.current.primary == YellowColors.primary

val appLogoResource: Int
    @Composable
    @ReadOnlyComposable
    get() = if (isYellowTheme) com.cloudify.music.R.drawable.ic_cloudify_logo_yellow else com.cloudify.music.R.drawable.ic_cloudify_logo_violet

val AccentPink = Color(0xFFEC4899)
val AccentCyan = Color(0xFF06B6D4)
val AccentGreen = Color(0xFF10B981)

val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFF9CA3AF)
val TextMuted = Color(0xFF6B7280)
