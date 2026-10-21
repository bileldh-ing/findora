package com.findora.app.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Raw palette from the Findora design spec. Screens never use these directly; they read [FindoraColors]. */
internal object FindoraPalette {
    val White = Color(0xFFFFFFFF)
    val SurfaceGray = Color(0xFFF2F2F4)
    val ChipGrayText = Color(0xFF8A8A8E)
    val InkBlack = Color(0xFF161616)
    val AccentBlue = Color(0xFF1F6BFF)
    val StarYellow = Color(0xFFFFC531)
    val Error = Color(0xFFE5484D)
    val Success = Color(0xFF22C55E)
    val OutlineLight = Color(0xFFE3E3E6)

    val NightBackground = Color(0xFF0E0E10)
    val NightSurface = Color(0xFF1A1A1D)
    val NightOutline = Color(0xFF2C2C30)
    val NightChipText = Color(0xFFA1A1A6)

    /** Bottom photo scrim end colour: rgba(0,0,0,0.78). */
    val ScrimEnd = Color(0xC7000000)

    /** Glass chip fill rgba(255,255,255,0.22) and 1dp border rgba(255,255,255,0.35). */
    val GlassFill = Color(0x38FFFFFF)
    val GlassBorder = Color(0x59FFFFFF)
}

/** Semantic colour roles. Light and dark instances are swapped by [FindoraTheme]. */
@Immutable
data class FindoraColors(
    val background: Color,
    val surface: Color,
    val onBackground: Color,
    val onSurfaceMuted: Color,
    val primaryAction: Color,
    val onPrimaryAction: Color,
    val secondaryAction: Color,
    val onSecondaryAction: Color,
    val outline: Color,
    val accent: Color,
    val star: Color,
    val error: Color,
    val success: Color,
    val scrimEnd: Color,
    val glassFill: Color,
    val glassBorder: Color,
    val onPhoto: Color,
    val isDark: Boolean,
)

internal val LightFindoraColors =
    FindoraColors(
        background = FindoraPalette.White,
        surface = FindoraPalette.SurfaceGray,
        onBackground = FindoraPalette.InkBlack,
        onSurfaceMuted = FindoraPalette.ChipGrayText,
        primaryAction = FindoraPalette.InkBlack,
        onPrimaryAction = FindoraPalette.White,
        secondaryAction = FindoraPalette.White,
        onSecondaryAction = FindoraPalette.InkBlack,
        outline = FindoraPalette.OutlineLight,
        accent = FindoraPalette.AccentBlue,
        star = FindoraPalette.StarYellow,
        error = FindoraPalette.Error,
        success = FindoraPalette.Success,
        scrimEnd = FindoraPalette.ScrimEnd,
        glassFill = FindoraPalette.GlassFill,
        glassBorder = FindoraPalette.GlassBorder,
        onPhoto = FindoraPalette.White,
        isDark = false,
    )

internal val DarkFindoraColors =
    FindoraColors(
        background = FindoraPalette.NightBackground,
        surface = FindoraPalette.NightSurface,
        onBackground = FindoraPalette.White,
        onSurfaceMuted = FindoraPalette.NightChipText,
        primaryAction = FindoraPalette.White,
        onPrimaryAction = FindoraPalette.InkBlack,
        secondaryAction = FindoraPalette.NightSurface,
        onSecondaryAction = FindoraPalette.White,
        outline = FindoraPalette.NightOutline,
        accent = FindoraPalette.AccentBlue,
        star = FindoraPalette.StarYellow,
        error = FindoraPalette.Error,
        success = FindoraPalette.Success,
        scrimEnd = FindoraPalette.ScrimEnd,
        glassFill = FindoraPalette.GlassFill,
        glassBorder = FindoraPalette.GlassBorder,
        onPhoto = FindoraPalette.White,
        isDark = true,
    )

val LocalFindoraColors = staticCompositionLocalOf { LightFindoraColors }
