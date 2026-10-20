package com.akash.agni.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.akash.agni.data.ThemeMode

private val Dark = darkColorScheme(
    primary = Color(0xFFFF8A3D),
    onPrimary = Color(0xFF1A0E05),
    primaryContainer = Color(0xFF2A1D14),
    onPrimaryContainer = Color(0xFFF3F1EC),
    secondary = Color(0xFF6CB4FF),
    onSecondary = Color(0xFF0B1A2B),
    tertiaryContainer = Color(0xFF2A1D14),
    onTertiaryContainer = Color(0xFFF3F1EC),
    background = Color(0xFF0F1012),
    onBackground = Color(0xFFF3F1EC),
    surface = Color(0xFF1A1C1F),
    onSurface = Color(0xFFF3F1EC),
    surfaceVariant = Color(0xFF25282C),
    onSurfaceVariant = Color(0xFFA3A7AD),
    surfaceContainerLowest = Color(0xFF0F1012),
    surfaceContainerLow = Color(0xFF1A1C1F),
    surfaceContainer = Color(0xFF0F1012),
    surfaceContainerHigh = Color(0xFF1A1C1F),
    surfaceContainerHighest = Color(0xFF25282C),
    outline = Color(0xFF3A3E43),
    outlineVariant = Color(0xFF2E3135),
    error = Color(0xFFFF6B6B),
)

private val Light = lightColorScheme(
    primary = Color(0xFFB84A0A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFBEADF),
    onPrimaryContainer = Color(0xFF17181A),
    secondary = Color(0xFF1F6FC5),
    onSecondary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFBEADF),
    onTertiaryContainer = Color(0xFF17181A),
    background = Color(0xFFF6F4EF),
    onBackground = Color(0xFF17181A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF17181A),
    surfaceVariant = Color(0xFFECE8E0),
    onSurfaceVariant = Color(0xFF5A5F66),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF6F4EF),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFECE8E0),
    outline = Color(0xFFCFCAC0),
    outlineVariant = Color(0xFFE2DED5),
    error = Color(0xFFB3261E),
)

private val Display = FontFamily.SansSerif

private val AppTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontFamily = Display, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineLarge = headlineLarge.copy(fontFamily = Display, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = headlineMedium.copy(fontFamily = Display, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
        headlineSmall = headlineSmall.copy(fontFamily = Display, fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Big numbers (calories, timers). */
val NumberStyle = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)

@Composable
fun AgniTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = AppTypography, content = content)
}
