package com.findora.app.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/**
 * Findora's theme. Custom tokens live in CompositionLocals (read through [FindoraTheme] accessors);
 * a Material 3 colour scheme is derived from them so stock M3 components never fall back to the default purple look.
 */
@Composable
fun FindoraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkFindoraColors else LightFindoraColors
    val materialScheme =
        if (darkTheme) {
            darkColorScheme(
                primary = colors.primaryAction,
                onPrimary = colors.onPrimaryAction,
                secondary = colors.accent,
                background = colors.background,
                onBackground = colors.onBackground,
                surface = colors.background,
                onSurface = colors.onBackground,
                surfaceVariant = colors.surface,
                onSurfaceVariant = colors.onSurfaceMuted,
                outline = colors.outline,
                error = colors.error,
            )
        } else {
            lightColorScheme(
                primary = colors.primaryAction,
                onPrimary = colors.onPrimaryAction,
                secondary = colors.accent,
                background = colors.background,
                onBackground = colors.onBackground,
                surface = colors.background,
                onSurface = colors.onBackground,
                surfaceVariant = colors.surface,
                onSurfaceVariant = colors.onSurfaceMuted,
                outline = colors.outline,
                error = colors.error,
            )
        }

    CompositionLocalProvider(
        LocalFindoraColors provides colors,
        LocalFindoraTypography provides DefaultFindoraTypography,
        LocalFindoraSpacing provides FindoraSpacing(),
        LocalFindoraShapes provides FindoraShapes(),
        LocalFindoraElevation provides FindoraElevation(),
        LocalFindoraMotion provides FindoraMotion(),
    ) {
        MaterialTheme(colorScheme = materialScheme, content = content)
    }
}

/** Accessors: `FindoraTheme.colors.accent`, `FindoraTheme.spacing.md`, and so on. */
object FindoraTheme {
    val colors: FindoraColors
        @Composable @ReadOnlyComposable
        get() = LocalFindoraColors.current

    val typography: FindoraTypography
        @Composable @ReadOnlyComposable
        get() = LocalFindoraTypography.current

    val spacing: FindoraSpacing
        @Composable @ReadOnlyComposable
        get() = LocalFindoraSpacing.current

    val shapes: FindoraShapes
        @Composable @ReadOnlyComposable
        get() = LocalFindoraShapes.current

    val elevation: FindoraElevation
        @Composable @ReadOnlyComposable
        get() = LocalFindoraElevation.current

    val motion: FindoraMotion
        @Composable @ReadOnlyComposable
        get() = LocalFindoraMotion.current
}
