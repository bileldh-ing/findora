package com.findora.app.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.findora.app.R

/** Syne (wide geometric display face), bundled for offline reliability. SIL OFL 1.1. */
val SyneFamily =
    FontFamily(
        Font(R.font.syne_bold, FontWeight.Bold),
        Font(R.font.syne_extrabold, FontWeight.ExtraBold),
    )

/** Poppins UI face, bundled. SIL OFL 1.1. */
val PoppinsFamily =
    FontFamily(
        Font(R.font.poppins_regular, FontWeight.Normal),
        Font(R.font.poppins_medium, FontWeight.Medium),
        Font(R.font.poppins_semibold, FontWeight.SemiBold),
        Font(R.font.poppins_bold, FontWeight.Bold),
    )

/** Type scale from the spec: Display 34/40, Title 24/30, Section 16/22, Body 14/20, Caption 12/16. */
@Immutable
data class FindoraTypography(
    val display: TextStyle,
    val headline: TextStyle,
    val title: TextStyle,
    val section: TextStyle,
    val body: TextStyle,
    val bodyMedium: TextStyle,
    val button: TextStyle,
    val caption: TextStyle,
    val label: TextStyle,
)

internal val DefaultFindoraTypography =
    FindoraTypography(
        display =
            TextStyle(
                fontFamily = SyneFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 34.sp,
                lineHeight = 40.sp,
                letterSpacing = 0.01.em,
            ),
        headline =
            TextStyle(
                fontFamily = PoppinsFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                lineHeight = 32.sp,
                letterSpacing = (-0.01).em,
            ),
        title =
            TextStyle(
                fontFamily = PoppinsFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                lineHeight = 30.sp,
            ),
        section =
            TextStyle(
                fontFamily = PoppinsFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                lineHeight = 22.sp,
            ),
        body =
            TextStyle(
                fontFamily = PoppinsFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = PoppinsFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
        button =
            TextStyle(
                fontFamily = PoppinsFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                lineHeight = 22.sp,
            ),
        caption =
            TextStyle(
                fontFamily = PoppinsFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            ),
        label =
            TextStyle(
                fontFamily = PoppinsFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                lineHeight = 14.sp,
            ),
    )

val LocalFindoraTypography = staticCompositionLocalOf { DefaultFindoraTypography }
