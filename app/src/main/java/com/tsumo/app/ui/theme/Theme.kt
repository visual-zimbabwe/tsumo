package com.tsumo.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.tsumo.app.R

object TsumoColors {
    val Surface = Color(0xFFFAF9F5)
    val SurfaceDim = Color(0xFFDAD9D6)
    val SurfaceContainerLowest = Color(0xFFFFFFFF)
    val SurfaceContainerLow = Color(0xFFF4F4F0)
    val SurfaceContainer = Color(0xFFEFEEEA)
    val SurfaceContainerHigh = Color(0xFFE9E8E4)
    val SurfaceContainerHighest = Color(0xFFE3E2DF)

    val OnSurface = Color(0xFF1B1C1A)
    val OnSurfaceVariant = Color(0xFF50453D)
    val Outline = Color(0xFF82756B)
    val OutlineVariant = Color(0xFFD4C3B9)

    val Primary = Color(0xFF291200)
    val OnPrimary = Color(0xFFFFFFFF)
    val PrimaryContainer = Color(0xFF42260C)
    val OnPrimaryContainer = Color(0xFFB68B6A)

    val Secondary = Color(0xFF7E562E)
    val SecondaryMuted = Color(0xFF8C6239)
    val Error = Color(0xFFBA1A1A)
    val ErrorContainer = Color(0xFFFFDAD6)
    val OnErrorContainer = Color(0xFF93000A)
}

val AlexBrushFamily = FontFamily(
    Font(R.font.alex_brush, FontWeight.Normal)
)

val EBGaramondFamily = FontFamily(
    Font(R.font.eb_garamond_regular, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.eb_garamond_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.eb_garamond_medium, FontWeight.Medium, FontStyle.Normal),
    Font(R.font.eb_garamond_semibold, FontWeight.SemiBold, FontStyle.Normal),
)

val ManropeFamily = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
)

val TsumoTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = EBGaramondFamily,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = (-0.3).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = EBGaramondFamily,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.Normal,
    ),
    headlineSmall = TextStyle(
        fontFamily = EBGaramondFamily,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.Normal,
    ),
    titleMedium = TextStyle(
        fontFamily = EBGaramondFamily,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Medium,
    ),
    bodyLarge = TextStyle(
        fontFamily = ManropeFamily,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodyMedium = TextStyle(
        fontFamily = ManropeFamily,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodySmall = TextStyle(
        fontFamily = ManropeFamily,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
    ),
    labelMedium = TextStyle(
        fontFamily = ManropeFamily,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = ManropeFamily,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.0.sp,
    ),
)

private val TsumoLightScheme = lightColorScheme(
    primary = TsumoColors.Primary,
    onPrimary = TsumoColors.OnPrimary,
    primaryContainer = TsumoColors.PrimaryContainer,
    onPrimaryContainer = TsumoColors.OnPrimaryContainer,
    secondary = TsumoColors.Secondary,
    onSecondary = Color.White,
    background = TsumoColors.Surface,
    onBackground = TsumoColors.OnSurface,
    surface = TsumoColors.Surface,
    onSurface = TsumoColors.OnSurface,
    surfaceVariant = TsumoColors.SurfaceContainerHighest,
    onSurfaceVariant = TsumoColors.OnSurfaceVariant,
    outline = TsumoColors.Outline,
    outlineVariant = TsumoColors.OutlineVariant,
    error = TsumoColors.Error,
    errorContainer = TsumoColors.ErrorContainer,
    onErrorContainer = TsumoColors.OnErrorContainer,
)

@Composable
fun TsumoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TsumoLightScheme,
        typography = TsumoTypography,
        content = content,
    )
}
