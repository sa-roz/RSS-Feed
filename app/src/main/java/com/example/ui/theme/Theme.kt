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

@Composable
fun MyApplicationTheme(
    settings: AppSettings = AppSettings(),
    content: @Composable () -> Unit,
) {
    val isDark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val context = LocalContext.current
    val colorScheme = when {
        settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val base = if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            if (isDark) {
                base.copy(
                    background = Color(0xFF090D16),
                    surface = base.surfaceContainerHigh,
                    surfaceContainer = base.surfaceContainerHigh,
                    surfaceVariant = base.surfaceContainerHighest,
                    outlineVariant = base.outlineVariant.copy(alpha = 0.5f)
                )
            } else {
                base.copy(
                    background = Color(0xFFF0F4F8),
                    surface = Color.White,
                    surfaceContainer = Color.White,
                    surfaceVariant = Color(0xFFE2E8F0),
                    outlineVariant = Color(0xFFCBD5E1)
                )
            }
        }
        isDark -> {
            darkColorScheme(
                primary = settings.accentPalette.primaryDark,
                onPrimary = Color.Black,
                primaryContainer = settings.accentPalette.primaryDark.copy(alpha = 0.25f),
                onPrimaryContainer = settings.accentPalette.primaryDark,
                secondary = SecondarySlateDark,
                onSecondary = Color(0xFF0F172A),
                secondaryContainer = SecondaryContainerDark,
                onSecondaryContainer = Color(0xFFE2E8F0),
                tertiary = AccentTealDark,
                background = BackgroundDark,
                onBackground = OnSurfaceDark,
                surface = SurfaceDark,
                onSurface = OnSurfaceDark,
                surfaceVariant = SurfaceVariantDark,
                onSurfaceVariant = OnSurfaceVariantDark,
                outline = OutlineDark,
                outlineVariant = OutlineVariantDark,
                surfaceContainer = SurfaceDark,
                surfaceContainerHigh = SurfaceVariantDark
            )
        }
        else -> {
            lightColorScheme(
                primary = settings.accentPalette.primaryLight,
                onPrimary = Color.White,
                primaryContainer = settings.accentPalette.primaryLight.copy(alpha = 0.15f),
                onPrimaryContainer = settings.accentPalette.primaryLight,
                secondary = SecondarySlate,
                onSecondary = Color.White,
                secondaryContainer = SecondaryContainerLight,
                onSecondaryContainer = Color(0xFF0F172A),
                tertiary = AccentTeal,
                background = BackgroundLight,
                onBackground = OnSurfaceLight,
                surface = SurfaceLight,
                onSurface = OnSurfaceLight,
                surfaceVariant = SurfaceVariantLight,
                onSurfaceVariant = OnSurfaceVariantLight,
                outline = OutlineLight,
                outlineVariant = OutlineVariantLight,
                surfaceContainer = SurfaceLight,
                surfaceContainerHigh = Color.White
            )
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
