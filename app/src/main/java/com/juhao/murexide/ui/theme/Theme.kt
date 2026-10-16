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

private fun getOledColorScheme(baseScheme: String): ColorScheme {
    val baseColors = when (baseScheme) {
        "WHITE" -> WhiteDarkColorScheme
        "PURPLE", "DYNAMIC" -> PurpleDarkColorScheme
        "BLUE" -> BlueDarkColorScheme
        "GREEN" -> GreenDarkColorScheme
        "ORANGE" -> OrangeDarkColorScheme
        "RED" -> RedDarkColorScheme
        "TEAL" -> TealDarkColorScheme
        "PINK" -> PinkDarkColorScheme
        "INDIGO" -> IndigoDarkColorScheme
        "BROWN" -> BrownDarkColorScheme
        "GRAY" -> GrayDarkColorScheme
        else -> PurpleDarkColorScheme
    }

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

    val targetColorScheme = when {
        themeMode == "oled" -> {
            getOledColorScheme(themeColor)
        }
        themeColor == "WHITE" -> {
            if (darkTheme) WhiteDarkColorScheme else WhiteLightColorScheme
        }
        themeColor == "DYNAMIC" -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (darkTheme) PurpleDarkColorScheme else PurpleLightColorScheme
            }
        }
        themeColor == "PURPLE" -> {
            if (darkTheme) PurpleDarkColorScheme else PurpleLightColorScheme
        }
        themeColor == "BLUE" -> {
            if (darkTheme) BlueDarkColorScheme else BlueLightColorScheme
        }
        themeColor == "GREEN" -> {
            if (darkTheme) GreenDarkColorScheme else GreenLightColorScheme
        }
        themeColor == "ORANGE" -> {
            if (darkTheme) OrangeDarkColorScheme else OrangeLightColorScheme
        }
        themeColor == "RED" -> {
            if (darkTheme) RedDarkColorScheme else RedLightColorScheme
        }
        themeColor == "TEAL" -> {
            if (darkTheme) TealDarkColorScheme else TealLightColorScheme
        }
        themeColor == "PINK" -> {
            if (darkTheme) PinkDarkColorScheme else PinkLightColorScheme
        }
        themeColor == "INDIGO" -> {
            if (darkTheme) IndigoDarkColorScheme else IndigoLightColorScheme
        }
        themeColor == "BROWN" -> {
            if (darkTheme) BrownDarkColorScheme else BrownLightColorScheme
        }
        themeColor == "GRAY" -> {
            if (darkTheme) GrayDarkColorScheme else GrayLightColorScheme
        }
        else -> {
            if (darkTheme) PurpleDarkColorScheme else PurpleLightColorScheme
        }
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
private fun animateColorScheme(target: ColorScheme): ColorScheme {
    val animationSpec = tween<Color>(durationMillis = 280)
    val primary by animateColorAsState(target.primary, animationSpec, "themePrimary")
    val onPrimary by animateColorAsState(target.onPrimary, animationSpec, "themeOnPrimary")
    val primaryContainer by animateColorAsState(target.primaryContainer, animationSpec, "themePrimaryContainer")
    val onPrimaryContainer by animateColorAsState(target.onPrimaryContainer, animationSpec, "themeOnPrimaryContainer")
    val secondary by animateColorAsState(target.secondary, animationSpec, "themeSecondary")
    val onSecondary by animateColorAsState(target.onSecondary, animationSpec, "themeOnSecondary")
    val secondaryContainer by animateColorAsState(target.secondaryContainer, animationSpec, "themeSecondaryContainer")
    val onSecondaryContainer by animateColorAsState(target.onSecondaryContainer, animationSpec, "themeOnSecondaryContainer")
    val tertiary by animateColorAsState(target.tertiary, animationSpec, "themeTertiary")
    val onTertiary by animateColorAsState(target.onTertiary, animationSpec, "themeOnTertiary")
    val tertiaryContainer by animateColorAsState(target.tertiaryContainer, animationSpec, "themeTertiaryContainer")
    val onTertiaryContainer by animateColorAsState(target.onTertiaryContainer, animationSpec, "themeOnTertiaryContainer")
    val background by animateColorAsState(target.background, animationSpec, "themeBackground")
    val onBackground by animateColorAsState(target.onBackground, animationSpec, "themeOnBackground")
    val surface by animateColorAsState(target.surface, animationSpec, "themeSurface")
    val onSurface by animateColorAsState(target.onSurface, animationSpec, "themeOnSurface")
    val surfaceVariant by animateColorAsState(target.surfaceVariant, animationSpec, "themeSurfaceVariant")
    val onSurfaceVariant by animateColorAsState(target.onSurfaceVariant, animationSpec, "themeOnSurfaceVariant")
    val outline by animateColorAsState(target.outline, animationSpec, "themeOutline")
    val outlineVariant by animateColorAsState(target.outlineVariant, animationSpec, "themeOutlineVariant")
    val surfaceTint by animateColorAsState(target.surfaceTint, animationSpec, "themeSurfaceTint")
    val inverseSurface by animateColorAsState(target.inverseSurface, animationSpec, "themeInverseSurface")
    val inverseOnSurface by animateColorAsState(target.inverseOnSurface, animationSpec, "themeInverseOnSurface")
    val inversePrimary by animateColorAsState(target.inversePrimary, animationSpec, "themeInversePrimary")
    val surfaceContainerHighest by animateColorAsState(
        target.surfaceContainerHighest,
        animationSpec,
        "themeSurfaceContainerHighest"
    )
    val surfaceContainerHigh by animateColorAsState(
        target.surfaceContainerHigh,
        animationSpec,
        "themeSurfaceContainerHigh"
    )
    val surfaceContainer by animateColorAsState(
        target.surfaceContainer,
        animationSpec,
        "themeSurfaceContainer"
    )
    val surfaceContainerLow by animateColorAsState(
        target.surfaceContainerLow,
        animationSpec,
        "themeSurfaceContainerLow"
    )
    val surfaceContainerLowest by animateColorAsState(
        target.surfaceContainerLowest,
        animationSpec,
        "themeSurfaceContainerLowest"
    )

    return target.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
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
        outline = outline,
        outlineVariant = outlineVariant,
        surfaceTint = surfaceTint,
        inverseSurface = inverseSurface,
        inverseOnSurface = inverseOnSurface,
        inversePrimary = inversePrimary,
        surfaceContainerHighest = surfaceContainerHighest,
        surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainer = surfaceContainer,
        surfaceContainerLow = surfaceContainerLow,
        surfaceContainerLowest = surfaceContainerLowest
    )
}