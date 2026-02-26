package com.motebaya.vaulten.presentation.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

/**
 * Skeuomorphic theme tokens for depth and shadows.
 */
data class SkeuomorphicColors(
    val surfaceBase: Color,
    val surfaceElevated: Color,
    val surfaceCard: Color,
    val surfaceCardPressed: Color,
    val shadowDark: Color,
    val shadowLight: Color,
    val innerHighlight: Color,
    val innerShadow: Color,
    val warningContainer: Color,
    val onWarningContainer: Color
)

/**
 * Shadow configuration for skeuomorphic effects.
 */
data class SkeuomorphicShadows(
    val elevationSmall: Dp = 4.dp,
    val elevationMedium: Dp = 8.dp,
    val elevationLarge: Dp = 16.dp,
    val blurRadius: Dp = 12.dp,
    val offsetX: Dp = 4.dp,
    val offsetY: Dp = 4.dp
)

val LocalSkeuomorphicColors = staticCompositionLocalOf {
    SkeuomorphicColors(
        surfaceBase = SurfaceLight,
        surfaceElevated = SurfaceHighlight,
        surfaceCard = SurfaceCard,
        surfaceCardPressed = SurfaceCardPressed,
        shadowDark = SurfaceShadow,
        shadowLight = SurfaceInnerLight,
        innerHighlight = SurfaceInnerLight,
        innerShadow = SurfaceInnerShadow,
        warningContainer = WarningContainer,
        onWarningContainer = TextOnWarning
    )
}

val LocalSkeuomorphicShadows = staticCompositionLocalOf { SkeuomorphicShadows() }

private val LightSkeuomorphicColors = SkeuomorphicColors(
    surfaceBase = SurfaceLight,
    surfaceElevated = SurfaceHighlight,
    surfaceCard = SurfaceCard,
    surfaceCardPressed = SurfaceCardPressed,
    shadowDark = SurfaceShadow,
    shadowLight = SurfaceInnerLight,
    innerHighlight = SurfaceInnerLight,
    innerShadow = SurfaceInnerShadow,
    warningContainer = WarningContainer,
    onWarningContainer = TextOnWarning
)

private val DarkSkeuomorphicColors = SkeuomorphicColors(
    surfaceBase = SurfaceDark,
    surfaceElevated = SurfaceDarkHighlight,
    surfaceCard = SurfaceDarkCard,
    surfaceCardPressed = SurfaceDarkCardPressed,
    shadowDark = SurfaceDarkShadow,
    shadowLight = SurfaceDarkInnerLight,
    innerHighlight = SurfaceDarkInnerLight,
    innerShadow = SurfaceDarkInnerShadow,
    warningContainer = WarningContainerDark,
    onWarningContainer = TextOnWarningDark
)

/**
 * Light color scheme for the Vault app.
 */
private val LightColorScheme = lightColorScheme(
    primary = VaultBlue,
    onPrimary = TextOnPrimary,
    primaryContainer = VaultBlueLight,
    onPrimaryContainer = TextOnPrimary,
    
    secondary = VaultGold,
    onSecondary = TextPrimary,
    secondaryContainer = VaultGoldLight,
    onSecondaryContainer = TextPrimary,
    
    tertiary = Info,
    onTertiary = TextOnPrimary,
    
    background = SurfaceLight,
    onBackground = TextPrimary,
    
    surface = SurfaceCard,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceHighlight,
    onSurfaceVariant = TextSecondary,
    
    error = Error,
    onError = TextOnPrimary,
    errorContainer = Error.copy(alpha = 0.1f),
    onErrorContainer = Error,
    
    outline = TextFieldBorder,
    outlineVariant = Divider
)

/**
 * Dark color scheme for the Vault app.
 */
private val DarkColorScheme = darkColorScheme(
    primary = VaultBlueVibrant,
    onPrimary = TextOnPrimary,
    primaryContainer = VaultBlue,
    onPrimaryContainer = TextOnPrimary,
    
    secondary = VaultGold,
    onSecondary = TextPrimary,
    secondaryContainer = VaultGoldDark,
    onSecondaryContainer = TextOnPrimary,
    
    tertiary = InfoLight,
    onTertiary = TextOnPrimary,
    
    background = SurfaceDark,
    onBackground = TextPrimaryDark,
    
    surface = SurfaceDarkCard,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceDarkHighlight,
    onSurfaceVariant = TextSecondaryDark,
    
    error = ErrorLight,
    onError = TextOnPrimary,
    errorContainer = Error.copy(alpha = 0.2f),
    onErrorContainer = ErrorLight,
    
    outline = TextFieldBorderDark,
    outlineVariant = DividerDark
)

/**
 * Access skeuomorphic colors from MaterialTheme.
 */
object VaultThemeExtras {
    val skeuomorphicColors: SkeuomorphicColors
        @Composable
        get() = LocalSkeuomorphicColors.current
    
    val shadows: SkeuomorphicShadows
        @Composable
        get() = LocalSkeuomorphicShadows.current
}

/**
 * Main theme composable for the Vault app.
 * Automatically follows system dark mode.
 */
@Composable
fun VaultTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val skeuomorphicColors = if (darkTheme) DarkSkeuomorphicColors else LightSkeuomorphicColors
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalSkeuomorphicColors provides skeuomorphicColors,
        LocalSkeuomorphicShadows provides SkeuomorphicShadows()
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = VaultTypography,
            shapes = VaultShapes,
            content = content
        )
    }
}
