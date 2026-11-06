package com.cloudify.music.core.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun CloudifyTheme(
    themeColor: String = "violet",
    content: @Composable () -> Unit
) {
    val palette = if (themeColor == "yellow") YellowColors else VioletColors

    val colorScheme = darkColorScheme(
        primary = palette.primary,
        onPrimary = palette.onPrimary,
        primaryContainer = palette.dark,
        onPrimaryContainer = TextPrimary,
        secondary = palette.light,
        onSecondary = palette.onPrimary,
        background = DeepBlack,
        onBackground = TextPrimary,
        surface = DarkSurface,
        onSurface = TextPrimary,
        surfaceVariant = DarkSurfaceVariant,
        onSurfaceVariant = TextSecondary,
        outline = DarkBorder
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    CompositionLocalProvider(LocalAppThemeColors provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
