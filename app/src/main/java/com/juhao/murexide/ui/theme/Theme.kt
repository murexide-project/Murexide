package com.juhao.murexide.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.juhao.murexide.datastore.SettingsStorage
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

private fun whiteOledColorScheme(): ColorScheme {
    val baseColors = WhiteDarkColorScheme
    return darkColorScheme(
        primary = baseColors.primary,
        onPrimary = baseColors.onPrimary,
        primaryContainer = baseColors.primaryContainer,
        onPrimaryContainer = baseColors.onPrimaryContainer,
        secondary = baseColors.secondary,
        onSecondary = baseColors.onSecondary,
        secondaryContainer = baseColors.secondaryContainer,
        onSecondaryContainer = baseColors.onSecondaryContainer,
        tertiary = baseColors.tertiary,
        onTertiary = baseColors.onTertiary,
        tertiaryContainer = baseColors.tertiaryContainer,
        onTertiaryContainer = baseColors.onTertiaryContainer,
        error = baseColors.error,
        onError = baseColors.onError,
        errorContainer = baseColors.errorContainer,
        onErrorContainer = baseColors.onErrorContainer,
        background = Color(0xFF000000),
        onBackground = baseColors.onBackground,
        surface = Color(0xFF000000),
        onSurface = baseColors.onSurface,
        surfaceVariant = Color(0xFF1A1A1A),
        onSurfaceVariant = baseColors.onSurfaceVariant,
        outline = baseColors.outline,
        outlineVariant = Color(0xFF2A2A2A),
        surfaceTint = baseColors.surfaceTint,
        inverseSurface = baseColors.inverseSurface,
        inverseOnSurface = baseColors.inverseOnSurface,
        inversePrimary = baseColors.inversePrimary,
        surfaceContainerHighest = baseColors.surfaceContainerHighest,
        surfaceContainerHigh = baseColors.surfaceContainerHigh,
        surfaceContainer = baseColors.surfaceContainer,
        surfaceContainerLow = baseColors.surfaceContainerLow,
        surfaceContainerLowest = baseColors.surfaceContainerLowest,
        primaryFixed = baseColors.primaryFixed,
        primaryFixedDim = baseColors.primaryFixedDim,
        onPrimaryFixed = baseColors.onPrimaryFixed,
        onPrimaryFixedVariant = baseColors.onPrimaryFixedVariant,
        secondaryFixed = baseColors.secondaryFixed,
        secondaryFixedDim = baseColors.secondaryFixedDim,
        onSecondaryFixed = baseColors.onSecondaryFixed,
        onSecondaryFixedVariant = baseColors.onSecondaryFixedVariant,
        tertiaryFixed = baseColors.tertiaryFixed,
        tertiaryFixedDim = baseColors.tertiaryFixedDim,
        onTertiaryFixed = baseColors.onTertiaryFixed,
        onTertiaryFixedVariant = baseColors.onTertiaryFixedVariant,
        scrim = baseColors.scrim
    )
}

private fun presetScheme(themeColor: String, dark: Boolean): ColorScheme = when (themeColor) {
    "BLUE" -> if (dark) BlueDarkColorScheme else BlueLightColorScheme
    "GREEN" -> if (dark) GreenDarkColorScheme else GreenLightColorScheme
    "ORANGE" -> if (dark) OrangeDarkColorScheme else OrangeLightColorScheme
    "RED" -> if (dark) RedDarkColorScheme else RedLightColorScheme
    "TEAL" -> if (dark) TealDarkColorScheme else TealLightColorScheme
    "PINK" -> if (dark) PinkDarkColorScheme else PinkLightColorScheme
    "INDIGO" -> if (dark) IndigoDarkColorScheme else IndigoLightColorScheme
    "BROWN" -> if (dark) BrownDarkColorScheme else BrownLightColorScheme
    "GRAY" -> if (dark) GrayDarkColorScheme else GrayLightColorScheme
    else -> if (dark) PurpleDarkColorScheme else PurpleLightColorScheme
}

internal fun usesDarkTheme(themeMode: String, systemInDarkTheme: Boolean): Boolean = when (themeMode) {
    "system" -> systemInDarkTheme
    "light" -> false
    "dark", "oled" -> true
    else -> false
}

@Composable
fun MurexideTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val settingsStorage = remember { SettingsStorage(context) }

    LaunchedEffect(Unit) {
        UiState.themeMode.value = settingsStorage.getThemeMode()
        UiState.themeColor.value = settingsStorage.getThemeColor()
    }

    val themeMode by UiState.themeMode
    val themeColor by UiState.themeColor
    val liquidGlassEnabled by settingsStorage.liquidGlassEnabledFlow.collectAsState(initial = false)
    val liquidGlassBlur by settingsStorage.liquidGlassBlurFlow.collectAsState(initial = 1f)

    val darkTheme = usesDarkTheme(themeMode, isSystemInDarkTheme())

    val scheme = when {
        themeColor == "WHITE" -> if (darkTheme) WhiteDarkColorScheme else WhiteLightColorScheme
        themeColor == "DYNAMIC" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        themeColor == "DYNAMIC" -> if (darkTheme) PurpleDarkColorScheme else PurpleLightColorScheme
        else -> presetScheme(themeColor, darkTheme)
    }
    val targetColorScheme = when {
        themeMode == "oled" && themeColor == "WHITE" -> whiteOledColorScheme()
        themeMode == "oled" -> toOledColorScheme(scheme)
        else -> scheme
    }
    val colorScheme = animateColorScheme(targetColorScheme)

    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as Activity).window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = {
            val backdrop = if (liquidGlassEnabled) rememberLayerBackdrop() else null
            CompositionLocalProvider(
                LocalLiquidGlassEnabled provides liquidGlassEnabled,
                LocalLiquidGlassBlur provides liquidGlassBlur,
                LocalLiquidGlassBackdrop provides backdrop
            ) {
                if (backdrop != null) {
                    Box(Modifier.fillMaxSize()) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .layerBackdrop(backdrop)
                                .background(colorScheme.background)
                        )
                        Box(
                            Modifier
                                .fillMaxSize()
                        ) {
                            content()
                        }
                    }
                } else {
                    content()
                }
            }
        }
    )
}

@Composable
private fun anim(target: Color, label: String): Color {
    val value by animateColorAsState(target, tween(durationMillis = 280), label)
    return value
}

@Composable
private fun animateColorScheme(target: ColorScheme): ColorScheme {
    val primary = anim(target.primary, "themePrimary")
    val onPrimary = anim(target.onPrimary, "themeOnPrimary")
    val primaryContainer = anim(target.primaryContainer, "themePrimaryContainer")
    val onPrimaryContainer = anim(target.onPrimaryContainer, "themeOnPrimaryContainer")
    val inversePrimary = anim(target.inversePrimary, "themeInversePrimary")
    val secondary = anim(target.secondary, "themeSecondary")
    val onSecondary = anim(target.onSecondary, "themeOnSecondary")
    val secondaryContainer = anim(target.secondaryContainer, "themeSecondaryContainer")
    val onSecondaryContainer = anim(target.onSecondaryContainer, "themeOnSecondaryContainer")
    val tertiary = anim(target.tertiary, "themeTertiary")
    val onTertiary = anim(target.onTertiary, "themeOnTertiary")
    val tertiaryContainer = anim(target.tertiaryContainer, "themeTertiaryContainer")
    val onTertiaryContainer = anim(target.onTertiaryContainer, "themeOnTertiaryContainer")
    val background = anim(target.background, "themeBackground")
    val onBackground = anim(target.onBackground, "themeOnBackground")
    val surface = anim(target.surface, "themeSurface")
    val onSurface = anim(target.onSurface, "themeOnSurface")
    val surfaceVariant = anim(target.surfaceVariant, "themeSurfaceVariant")
    val onSurfaceVariant = anim(target.onSurfaceVariant, "themeOnSurfaceVariant")
    val surfaceTint = anim(target.surfaceTint, "themeSurfaceTint")
    val inverseSurface = anim(target.inverseSurface, "themeInverseSurface")
    val inverseOnSurface = anim(target.inverseOnSurface, "themeInverseOnSurface")
    val error = anim(target.error, "themeError")
    val onError = anim(target.onError, "themeOnError")
    val errorContainer = anim(target.errorContainer, "themeErrorContainer")
    val onErrorContainer = anim(target.onErrorContainer, "themeOnErrorContainer")
    val outline = anim(target.outline, "themeOutline")
    val outlineVariant = anim(target.outlineVariant, "themeOutlineVariant")
    val scrim = anim(target.scrim, "themeScrim")
    val surfaceBright = anim(target.surfaceBright, "themeSurfaceBright")
    val surfaceDim = anim(target.surfaceDim, "themeSurfaceDim")
    val surfaceContainer = anim(target.surfaceContainer, "themeSurfaceContainer")
    val surfaceContainerHigh = anim(target.surfaceContainerHigh, "themeSurfaceContainerHigh")
    val surfaceContainerHighest = anim(target.surfaceContainerHighest, "themeSurfaceContainerHighest")
    val surfaceContainerLow = anim(target.surfaceContainerLow, "themeSurfaceContainerLow")
    val surfaceContainerLowest = anim(target.surfaceContainerLowest, "themeSurfaceContainerLowest")
    val primaryFixed = anim(target.primaryFixed, "themePrimaryFixed")
    val primaryFixedDim = anim(target.primaryFixedDim, "themePrimaryFixedDim")
    val onPrimaryFixed = anim(target.onPrimaryFixed, "themeOnPrimaryFixed")
    val onPrimaryFixedVariant = anim(target.onPrimaryFixedVariant, "themeOnPrimaryFixedVariant")
    val secondaryFixed = anim(target.secondaryFixed, "themeSecondaryFixed")
    val secondaryFixedDim = anim(target.secondaryFixedDim, "themeSecondaryFixedDim")
    val onSecondaryFixed = anim(target.onSecondaryFixed, "themeOnSecondaryFixed")
    val onSecondaryFixedVariant = anim(target.onSecondaryFixedVariant, "themeOnSecondaryFixedVariant")
    val tertiaryFixed = anim(target.tertiaryFixed, "themeTertiaryFixed")
    val tertiaryFixedDim = anim(target.tertiaryFixedDim, "themeTertiaryFixedDim")
    val onTertiaryFixed = anim(target.onTertiaryFixed, "themeOnTertiaryFixed")
    val onTertiaryFixedVariant = anim(target.onTertiaryFixedVariant, "themeOnTertiaryFixedVariant")

    return target.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        inversePrimary = inversePrimary,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        onTertiary = onTertiary,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        surfaceTint = surfaceTint,
        inverseSurface = inverseSurface,
        inverseOnSurface = inverseOnSurface,
        error = error,
        onError = onError,
        errorContainer = errorContainer,
        onErrorContainer = onErrorContainer,
        outline = outline,
        outlineVariant = outlineVariant,
        scrim = scrim,
        surfaceBright = surfaceBright,
        surfaceDim = surfaceDim,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerHighest = surfaceContainerHighest,
        surfaceContainerLow = surfaceContainerLow,
        surfaceContainerLowest = surfaceContainerLowest,
        primaryFixed = primaryFixed,
        primaryFixedDim = primaryFixedDim,
        onPrimaryFixed = onPrimaryFixed,
        onPrimaryFixedVariant = onPrimaryFixedVariant,
        secondaryFixed = secondaryFixed,
        secondaryFixedDim = secondaryFixedDim,
        onSecondaryFixed = onSecondaryFixed,
        onSecondaryFixedVariant = onSecondaryFixedVariant,
        tertiaryFixed = tertiaryFixed,
        tertiaryFixedDim = tertiaryFixedDim,
        onTertiaryFixed = onTertiaryFixed,
        onTertiaryFixedVariant = onTertiaryFixedVariant
    )
}