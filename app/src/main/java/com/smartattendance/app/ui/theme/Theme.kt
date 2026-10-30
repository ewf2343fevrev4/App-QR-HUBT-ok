package com.smartattendance.app.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ==========================================
// COLOR PALETTE TOKENS (IDENTICAL TONAL IDENTITY)
// ==========================================

val DeepSlateNavy = Color(0xFF173756)
val IceBlue = Color(0xFFDCE8F4)
val PineTeal = Color(0xFF007E87)
val SoftMint = Color(0xFFD5F4EF)
val WarmBronze = Color(0xFF956600)
val CoolCanvasLight = Color(0xFFF4F7FB)
val CrispWhite = Color(0xFFFDFEFF)
val SubtleCardLight = Color(0xFFE8EEF5)
val SlateGreyBorder = Color(0xFF778899)

val PrimaryDark = Color(0xFF9DCBF2)
val PrimaryContainerDark = Color(0xFF204160)
val SecondaryDark = Color(0xFF65D8CB)
val SecondaryContainerDark = Color(0xFF084E50)
val ObsidianDark = Color(0xFF101B29)
val NavySlateDark = Color(0xFF172536)
val SurfaceVariantDark = Color(0xFF243549)

// Semantic Status Colors
val StatusPresentLight = Color(0xFF087B69)
val StatusPresentDark = Color(0xFF34D399)
val StatusLateLight = Color(0xFF9B690B)
val StatusLateDark = Color(0xFFFBBF24)
val StatusExcusedLight = Color(0xFF1C74A6)
val StatusExcusedDark = Color(0xFF60A5FA)
val StatusAbsentLight = Color(0xFFB3261E)
val StatusAbsentDark = Color(0xFFF87171)

fun statusColor(status: String, isDark: Boolean = false): Color = when (status) {
    "PRESENT", "OPEN", "ACTIVE" -> if (isDark) StatusPresentDark else StatusPresentLight
    "LATE", "SCHEDULED" -> if (isDark) StatusLateDark else StatusLateLight
    "EXCUSED" -> if (isDark) StatusExcusedDark else StatusExcusedLight
    "ABSENT", "INACTIVE", "CLOSED" -> if (isDark) StatusAbsentDark else StatusAbsentLight
    else -> if (isDark) PrimaryDark else DeepSlateNavy
}

fun statusContainerColor(status: String, isDark: Boolean = false): Color =
    statusColor(status, isDark).copy(alpha = if (isDark) 0.16f else 0.12f)

val Light = lightColorScheme(
    primary = DeepSlateNavy,
    onPrimary = Color.White,
    primaryContainer = IceBlue,
    onPrimaryContainer = Color(0xFF0C243B),
    secondary = PineTeal,
    onSecondary = Color.White,
    secondaryContainer = SoftMint,
    onSecondaryContainer = Color(0xFF00373B),
    tertiary = WarmBronze,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFBE4B5),
    onTertiaryContainer = Color(0xFF2E1D00),
    background = CoolCanvasLight,
    onBackground = Color(0xFF141F2B),
    surface = CrispWhite,
    onSurface = Color(0xFF172536),
    surfaceVariant = SubtleCardLight,
    onSurfaceVariant = Color(0xFF495A6D),
    outline = SlateGreyBorder,
    outlineVariant = Color(0xFFD0DBE5),
    error = StatusAbsentLight,
    onError = Color.White
)

val Dark = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = Color(0xFF052A4A),
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = Color(0xFFCCE4FA),
    secondary = SecondaryDark,
    onSecondary = Color(0xFF00373B),
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = Color(0xFF96F2E7),
    tertiary = Color(0xFFE5BE65),
    onTertiary = Color(0xFF3F2E00),
    tertiaryContainer = Color(0xFF5B4300),
    onTertiaryContainer = Color(0xFFFFDF9B),
    background = ObsidianDark,
    onBackground = Color(0xFFE4EDF7),
    surface = NavySlateDark,
    onSurface = Color(0xFFE4EDF7),
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = Color(0xFFB3C5D7),
    outline = Color(0xFF6C7F93),
    outlineVariant = Color(0xFF2C3E52),
    error = StatusAbsentDark,
    onError = Color(0xFF5F1412)
)

// ==========================================
// SHAPES & ELEVATION TOKENS
// ==========================================

val CardShape = RoundedCornerShape(14.dp)
val ControlShape = RoundedCornerShape(8.dp)
val BadgeShape = RoundedCornerShape(6.dp)
val PillShape = RoundedCornerShape(50)

fun hairlineBorder(
    color: Color,
    width: Dp = 1.dp
): BorderStroke = BorderStroke(width, color)

// ==========================================
// TYPOGRAPHY SYSTEM
// ==========================================

const val TabularNumbers = "tnum"

val TabularStyle = TextStyle(fontFeatureSettings = TabularNumbers)

val AppTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.03).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.02).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.015).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.015).sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.01).sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.04.sp
    )
)

@Composable
fun SmartTheme(mode: String, content: @Composable () -> Unit) {
    val darkTheme = mode == "DARK" || (mode == "SYSTEM" && isSystemInDarkTheme())
    MaterialTheme(
        colorScheme = if (darkTheme) Dark else Light,
        typography = AppTypography,
        content = content
    )
}
