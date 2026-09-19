package com.ffboostx.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ffboostx.core.AnimationIntensity
import com.ffboostx.core.MotionBudget
import com.ffboostx.core.PerformanceProfile

/**
 * Palette. The base is a cold near-black; the single hot accent is an ember
 * orange borrowed from the game itself, with an ice blue used only for secondary
 * data. Status colours are reserved for status and never used as decoration.
 */
object BoostColors {
    val Void = Color(0xFF05060A)
    val Base = Color(0xFF0A0C13)
    val Surface = Color(0xFF11141D)
    val SurfaceRaised = Color(0xFF171B27)
    val Line = Color(0xFF232A3A)
    val LineSoft = Color(0xFF1A1F2C)

    val Ember = Color(0xFFFF6B2C)
    val EmberDim = Color(0xFF7A3216)
    val Ice = Color(0xFF38BDF8)

    val Success = Color(0xFF4ADE80)
    val Warning = Color(0xFFFBBF24)
    val Danger = Color(0xFFF87171)
    val Muted = Color(0xFF6B7690)

    val TextPrimary = Color(0xFFF2F5FA)
    val TextSecondary = Color(0xFF9AA5BC)
    val TextTertiary = Color(0xFF6B7690)

    /** Used for the primary action only. */
    val EmberGradient = Brush.horizontalGradient(listOf(Color(0xFFFF8A3D), Color(0xFFF04E23)))
}

/**
 * One family, five roles. Weight and letter spacing carry the esports tone rather
 * than a second display face, which keeps the APK free of font assets.
 */
private val BoostTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 30.sp,
        lineHeight = 34.sp,
        letterSpacing = 2.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp,
        lineHeight = 18.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        letterSpacing = 1.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.4.sp
    )
)

private val BoostShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

private val BoostColorScheme = darkColorScheme(
    primary = BoostColors.Ember,
    onPrimary = Color(0xFF1A0800),
    secondary = BoostColors.Ice,
    onSecondary = Color(0xFF041420),
    background = BoostColors.Base,
    onBackground = BoostColors.TextPrimary,
    surface = BoostColors.Surface,
    onSurface = BoostColors.TextPrimary,
    surfaceVariant = BoostColors.SurfaceRaised,
    onSurfaceVariant = BoostColors.TextSecondary,
    outline = BoostColors.Line,
    error = BoostColors.Danger
)

/** The active motion budget, read by every animated component. */
val LocalMotion: ProvidableCompositionLocal<MotionBudget> = staticCompositionLocalOf {
    MotionBudget(PerformanceProfile.BALANCED, AnimationIntensity.FULL)
}

@Composable
fun FFBoostXTheme(
    motion: MotionBudget,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalMotion provides motion) {
        MaterialTheme(
            colorScheme = BoostColorScheme,
            typography = BoostTypography,
            shapes = BoostShapes,
            content = content
        )
    }
}
