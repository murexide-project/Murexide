package com.juhao.murexide.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val defaultSeed = Color(0xFF9BD7FF)

private val neutralBase: ColorScheme = darkColorScheme(
    primary = Color(0xFF1F1F1F),
    onPrimary = Color(0xFF1F1F1F),
    primaryContainer = Color(0xFF1F1F1F),
    onPrimaryContainer = Color(0xFF1F1F1F),
    secondary = Color(0xFF1F1F1F),
    onSecondary = Color(0xFF1F1F1F),
    secondaryContainer = Color(0xFF1F1F1F),
    onSecondaryContainer = Color(0xFF1F1F1F),
    tertiary = Color(0xFF1F1F1F),
    onTertiary = Color(0xFF1F1F1F),
    tertiaryContainer = Color(0xFF1F1F1F),
    onTertiaryContainer = Color(0xFF1F1F1F),
    error = Color(0xFFFFC4BC),
    onError = Color(0xFF741210),
    errorContainer = Color(0xFF8C1515),
    onErrorContainer = Color(0xFFFFE5E1),
    background = Color(0xFF181A20),
    onBackground = Color(0xFFE8E8EF),
    surface = Color(0xFF181A20),
    onSurface = Color(0xFFE8E8EF),
    surfaceVariant = Color(0xFF30333B),
    onSurfaceVariant = Color(0xFFD0D2DC),
    outline = Color(0xFF9A9CA5),
    outlineVariant = Color(0xFF525560),
    surfaceTint = Color(0xFF1F1F1F),
    inverseSurface = Color(0xFFE8E8EF),
    inverseOnSurface = Color(0xFF1F1F1F),
    inversePrimary = Color(0xFF1F1F1F),
    surfaceContainerHighest = Color(0xFF3A3D45),
    surfaceContainerHigh = Color(0xFF30333B),
    surfaceContainer = Color(0xFF252830),
    surfaceContainerLow = Color(0xFF20232A),
    surfaceContainerLowest = Color(0xFF0D0F14),
    primaryFixed = Color(0xFF1F1F1F),
    primaryFixedDim = Color(0xFF1F1F1F),
    onPrimaryFixed = Color(0xFF1F1F1F),
    onPrimaryFixedVariant = Color(0xFF1F1F1F),
    secondaryFixed = Color(0xFF1F1F1F),
    secondaryFixedDim = Color(0xFF1F1F1F),
    onSecondaryFixed = Color(0xFF1F1F1F),
    onSecondaryFixedVariant = Color(0xFF1F1F1F),
    tertiaryFixed = Color(0xFF1F1F1F),
    tertiaryFixedDim = Color(0xFF1F1F1F),
    onTertiaryFixed = Color(0xFF1F1F1F),
    onTertiaryFixedVariant = Color(0xFF1F1F1F),
    scrim = Color(0xFF000000)
)

private val dayNeutralBase: ColorScheme = lightColorScheme(
    primary = Color(0xFF1F1F1F),
    onPrimary = Color(0xFF1F1F1F),
    primaryContainer = Color(0xFF1F1F1F),
    onPrimaryContainer = Color(0xFF1F1F1F),
    secondary = Color(0xFF1F1F1F),
    onSecondary = Color(0xFF1F1F1F),
    secondaryContainer = Color(0xFF1F1F1F),
    onSecondaryContainer = Color(0xFF1F1F1F),
    tertiary = Color(0xFF1F1F1F),
    onTertiary = Color(0xFF1F1F1F),
    tertiaryContainer = Color(0xFF1F1F1F),
    onTertiaryContainer = Color(0xFF1F1F1F),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFF7F8FC),
    onBackground = Color(0xFF1B1B1F),
    surface = Color(0xFFF7F8FC),
    onSurface = Color(0xFF1B1B1F),
    surfaceVariant = Color(0xFFE6E7EB),
    onSurfaceVariant = Color(0xFF44464F),
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6D0),
    surfaceTint = Color(0xFF1F1F1F),
    inverseSurface = Color(0xFF1B1B1F),
    inverseOnSurface = Color(0xFFF7F8FC),
    inversePrimary = Color(0xFF1F1F1F),
    surfaceContainerHighest = Color(0xFFE0E1E5),
    surfaceContainerHigh = Color(0xFFE6E7EB),
    surfaceContainer = Color(0xFFECEDF1),
    surfaceContainerLow = Color(0xFFF2F3F7),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    primaryFixed = Color(0xFF1F1F1F),
    primaryFixedDim = Color(0xFF1F1F1F),
    onPrimaryFixed = Color(0xFF1F1F1F),
    onPrimaryFixedVariant = Color(0xFF1F1F1F),
    secondaryFixed = Color(0xFF1F1F1F),
    secondaryFixedDim = Color(0xFF1F1F1F),
    onSecondaryFixed = Color(0xFF1F1F1F),
    onSecondaryFixedVariant = Color(0xFF1F1F1F),
    tertiaryFixed = Color(0xFF1F1F1F),
    tertiaryFixedDim = Color(0xFF1F1F1F),
    onTertiaryFixed = Color(0xFF1F1F1F),
    onTertiaryFixedVariant = Color(0xFF1F1F1F),
    scrim = Color(0xFF000000)
)

private fun blend(base: Color, tint: Color, amount: Float): Color = Color(
    red = base.red * (1f - amount) + tint.red * amount,
    green = base.green * (1f - amount) + tint.green * amount,
    blue = base.blue * (1f - amount) + tint.blue * amount,
    alpha = 1f
)

private fun toHsl(color: Color): Triple<Float, Float, Float> {
    val r = color.red
    val g = color.green
    val b = color.blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val l = (max + min) / 2f
    if (max == min) return Triple(0f, 0f, l)
    val d = max - min
    val s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
    val h = when (max) {
        r -> ((g - b) / d + if (g < b) 6f else 0f) / 6f
        g -> ((b - r) / d + 2f) / 6f
        else -> ((r - g) / d + 4f) / 6f
    }
    return Triple(h, s, l)
}

private fun hueToRgb(p: Float, q: Float, t0: Float): Float {
    var t = t0
    if (t < 0f) t += 1f
    if (t > 1f) t -= 1f
    return when {
        t < 1f / 6f -> p + (q - p) * 6f * t
        t < 1f / 2f -> q
        t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
        else -> p
    }
}

private fun fromHsl(h: Float, s: Float, l: Float): Color {
    if (s <= 0.0001f) return Color(l, l, l, 1f)
    val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
    val p = 2f * l - q
    return Color(
        red = hueToRgb(p, q, h + 1f / 3f),
        green = hueToRgb(p, q, h),
        blue = hueToRgb(p, q, h - 1f / 3f),
        alpha = 1f
    )
}

private fun hueShift(h: Float, shift: Float): Float {
    val v = h + shift
    return if (v > 1f) v - 1f else if (v < 0f) v + 1f else v
}

private fun contentOn(
    background: Color,
    h: Float,
    s: Float
): Color {
    val bgL = toHsl(background).third
    return if (bgL > 0.55f) {
        fromHsl(h, (s * 0.85f).coerceIn(0.35f, 0.70f), 0.18f)
    } else {
        fromHsl(h, (s * 0.30f).coerceIn(0.10f, 0.30f), 0.92f)
    }
}

private fun softSurface(
    h: Float,
    s: Float,
    l: Float,
    minS: Float,
    isDay: Boolean
): Color = fromHsl(
    h = h,
    s = (s * if (isDay) 0.16f else 0.20f).coerceAtLeast(minS),
    l = if (isDay) l.coerceIn(0.90f, 0.99f) else l.coerceIn(0.07f, 0.22f)
)

fun buildEventColorScheme(
    base: ColorScheme,
    eventColor: Color,
    eventUrgent: Boolean = false,
    isDay: Boolean = false
): ColorScheme {
    val (h, s, l) = toHsl(eventColor)

    if (eventUrgent) {
        val (errH, errS, _) = toHsl(base.error)
        val bg = softSurface(errH, errS, if (isDay) 0.97f else 0.10f, 0.03f, isDay)
        val scl = softSurface(errH, errS, if (isDay) 0.94f else 0.13f, 0.04f, isDay)
        val sc = softSurface(errH, errS, if (isDay) 0.91f else 0.16f, 0.05f, isDay)
        val sch = softSurface(errH, errS, if (isDay) 0.88f else 0.19f, 0.06f, isDay)
        val schh = softSurface(errH, errS, if (isDay) 0.85f else 0.22f, 0.06f, isDay)
        val scll = softSurface(errH, errS, if (isDay) 0.99f else 0.07f, 0.03f, isDay)
        return base.copy(
            primary = base.error,
            onPrimary = base.onError,
            primaryContainer = base.errorContainer,
            onPrimaryContainer = base.onErrorContainer,
            secondary = base.error,
            onSecondary = base.onError,
            secondaryContainer = base.errorContainer,
            onSecondaryContainer = base.onErrorContainer,
            tertiary = base.error,
            onTertiary = base.onError,
            tertiaryContainer = base.errorContainer,
            onTertiaryContainer = base.onErrorContainer,
            background = bg,
            onBackground = contentOn(bg, errH, errS),
            surface = bg,
            onSurface = contentOn(bg, errH, errS),
            surfaceVariant = sch,
            onSurfaceVariant = contentOn(sch, errH, errS),
            outline = blend(base.outline, base.error, if (isDay) 0.25f else 0.35f),
            outlineVariant = blend(base.outlineVariant, base.error, if (isDay) 0.20f else 0.28f),
            surfaceContainerLowest = scll,
            surfaceContainerLow = scl,
            surfaceContainer = sc,
            surfaceContainerHigh = sch,
            surfaceContainerHighest = schh,
            surfaceTint = base.error,
            inversePrimary = base.error
        )
    }

    val primaryS = (s * if (isDay) 0.95f else 1.0f).coerceIn(0.36f, 0.65f)
    val primary = if (isDay) {
        fromHsl(h, primaryS, 0.40f)
    } else {
        fromHsl(h, primaryS, if (l < 0.45f) 0.78f else (l + 0.10f).coerceAtMost(0.86f))
    }
    val onPrimary = contentOn(primary, h, primaryS)

    val primaryContainer = if (isDay) {
        fromHsl(h, (s * 0.60f).coerceIn(0.22f, 0.45f), 0.90f)
    } else {
        fromHsl(h, (s * 0.60f).coerceIn(0.20f, 0.42f), 0.24f)
    }
    val onPrimaryContainer = if (isDay) {
        fromHsl(h, (s * 0.75f).coerceIn(0.30f, 0.55f), 0.22f)
    } else {
        fromHsl(h, (s * 0.25f).coerceIn(0.08f, 0.25f), 0.92f)
    }

    val secondary = if (isDay) {
        fromHsl(h, (s * 0.12f).coerceIn(0.04f, 0.10f), 0.42f)
    } else {
        fromHsl(h, (s * 0.35f).coerceIn(0.10f, 0.25f), 0.82f)
    }
    val onSecondary = contentOn(secondary, h, s * 0.12f)

    val secondaryContainer = if (isDay) {
        fromHsl(h, (s * 0.10f).coerceIn(0.04f, 0.08f), 0.92f)
    } else {
        fromHsl(h, (s * 0.30f).coerceIn(0.08f, 0.22f), 0.28f)
    }
    val onSecondaryContainer = if (isDay) {
        fromHsl(h, (s * 0.20f).coerceIn(0.08f, 0.18f), 0.20f)
    } else {
        fromHsl(h, (s * 0.18f).coerceIn(0.05f, 0.18f), 0.92f)
    }

    val tertiaryH = hueShift(h, 60f / 360f)

    val tertiary = if (isDay) {
        fromHsl(tertiaryH, (s * 0.55f).coerceIn(0.30f, 0.55f), 0.36f)
    } else {
        fromHsl(tertiaryH, (s * 0.50f).coerceIn(0.18f, 0.38f), 0.85f)
    }
    val onTertiary = contentOn(tertiary, tertiaryH, s * 0.55f)

    val tertiaryContainer = if (isDay) {
        fromHsl(tertiaryH, (s * 0.45f).coerceIn(0.25f, 0.45f), 0.88f)
    } else {
        fromHsl(tertiaryH, (s * 0.45f).coerceIn(0.15f, 0.35f), 0.30f)
    }
    val onTertiaryContainer = if (isDay) {
        fromHsl(tertiaryH, (s * 0.70f).coerceIn(0.35f, 0.60f), 0.22f)
    } else {
        fromHsl(tertiaryH, (s * 0.20f).coerceIn(0.06f, 0.20f), 0.92f)
    }

    val bg = softSurface(h, s, if (isDay) 0.97f else 0.10f, 0.03f, isDay)
    val scl = softSurface(h, s, if (isDay) 0.95f else 0.13f, 0.04f, isDay)
    val sc = softSurface(h, s, if (isDay) 0.92f else 0.16f, 0.05f, isDay)
    val sch = softSurface(h, s, if (isDay) 0.89f else 0.19f, 0.05f, isDay)
    val schh = softSurface(h, s, if (isDay) 0.86f else 0.22f, 0.05f, isDay)
    val scll = softSurface(h, s, if (isDay) 0.99f else 0.07f, 0.03f, isDay)

    val outline = blend(base.outline, eventColor, if (isDay) 0.22f else 0.30f)
    val outlineVariant = blend(base.outlineVariant, eventColor, if (isDay) 0.16f else 0.24f)

    return base.copy(
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
        background = bg,
        onBackground = contentOn(bg, h, s),
        surface = bg,
        onSurface = contentOn(bg, h, s),
        surfaceVariant = sch,
        onSurfaceVariant = contentOn(sch, h, s),
        outline = outline,
        outlineVariant = outlineVariant,
        surfaceContainerLowest = scll,
        surfaceContainerLow = scl,
        surfaceContainer = sc,
        surfaceContainerHigh = sch,
        surfaceContainerHighest = schh,
        surfaceTint = primary,
        inversePrimary = primary,
        primaryFixed = primaryContainer,
        primaryFixedDim = primary,
        onPrimaryFixed = onPrimaryContainer,
        onPrimaryFixedVariant = onPrimaryContainer,
        secondaryFixed = secondaryContainer,
        secondaryFixedDim = secondary,
        onSecondaryFixed = onSecondaryContainer,
        onSecondaryFixedVariant = onSecondaryContainer,
        tertiaryFixed = tertiaryContainer,
        tertiaryFixedDim = tertiary,
        onTertiaryFixed = onTertiaryContainer,
        onTertiaryFixedVariant = onTertiaryContainer
    )
}

fun buildCustomColorScheme(
    base: ColorScheme,
    customColor: Color,
    isDay: Boolean = false
): ColorScheme = buildEventColorScheme(
    base = base,
    eventColor = customColor,
    eventUrgent = false,
    isDay = isDay
)

enum class AppTheme(
    val displayName: String,
    val seedColor: Color
) {
    DYNAMIC("动态取色", Color(0xFFD946EF)),
    WHITE("白色", Color(0xFF6750A4)),
    PURPLE("紫色", Color(0xFF7C4DFF)),
    BLUE("蓝色", Color(0xFF008CFF)),
    GREEN("绿色", Color(0xFF00A63E)),
    ORANGE("橙色", Color(0xFFFF6D00)),
    RED("红色", Color(0xFFE53935)),
    TEAL("青色", Color(0xFF00897B)),
    PINK("粉色", Color(0xFFEC407A)),
    INDIGO("靛蓝", Color(0xFF3F51B5)),
    BROWN("棕色", Color(0xFF795548)),
    GRAY("灰色", Color(0xFF607D8B))
}

fun buildThemeColorScheme(
    base: ColorScheme,
    theme: AppTheme,
    isDay: Boolean = false
): ColorScheme = buildEventColorScheme(
    base = base,
    eventColor = theme.seedColor,
    eventUrgent = false,
    isDay = isDay
)

val DynamicLightColorScheme = buildThemeColorScheme(dayNeutralBase, AppTheme.DYNAMIC, true)
val DynamicDarkColorScheme = buildThemeColorScheme(neutralBase, AppTheme.DYNAMIC, false)

val WhiteLightColorScheme = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF4F0FF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFFF3F1F7),
    onSecondary = Color(0xFF25232A),
    secondaryContainer = Color(0xFFF8F7FA),
    onSecondaryContainer = Color(0xFF25232A),
    tertiary = Color(0xFFF0EEF4),
    onTertiary = Color(0xFF25232A),
    tertiaryContainer = Color(0xFFF6F4F8),
    onTertiaryContainer = Color(0xFF25232A),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFF1F1F3),
    onBackground = Color(0xFF242127),
    surface = Color(0xFFF1F1F3),
    onSurface = Color(0xFF242127),
    surfaceVariant = Color(0xFFF1F1F3),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFDAD6E0),
    surfaceTint = Color(0xFF6750A4),
    inverseSurface = Color(0xFF313033),
    inverseOnSurface = Color(0xFFF4EFF4),
    inversePrimary = Color(0xFFD0BCFF),
    surfaceContainerHighest = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainer = Color(0xFFF8F8F8),
    surfaceContainerLow = Color.White,
    surfaceContainerLowest = Color.White,
    primaryFixed = Color(0xFFEADDFF),
    primaryFixedDim = Color(0xFFD0BCFF),
    onPrimaryFixed = Color(0xFF21005D),
    onPrimaryFixedVariant = Color(0xFF4F378B),
    secondaryFixed = Color(0xFFE8DEF8),
    secondaryFixedDim = Color(0xFFCCC2DC),
    onSecondaryFixed = Color(0xFF1D192B),
    onSecondaryFixedVariant = Color(0xFF4A4458),
    tertiaryFixed = Color(0xFFFFD8E4),
    tertiaryFixedDim = Color(0xFFEFB8C8),
    onTertiaryFixed = Color(0xFF31111D),
    onTertiaryFixedVariant = Color(0xFF633B48),
    scrim = Color(0xFF000000)
)

val WhiteDarkColorScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCAC4D0),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF49454F),
    onSecondaryContainer = Color(0xFFE7E0EC),
    tertiary = Color(0xFFE7E0EC),
    onTertiary = Color(0xFF303034),
    tertiaryContainer = Color(0xFF46464D),
    onTertiaryContainer = Color(0xFFE7E0EC),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF121214),
    onBackground = Color(0xFFE5E1E6),
    surface = Color(0xFF121214),
    onSurface = Color(0xFFE5E1E6),
    surfaceVariant = Color(0xFF45454C),
    onSurfaceVariant = Color(0xFFC9C5CC),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF45454C),
    surfaceTint = Color(0xFFD0BCFF),
    inverseSurface = Color(0xFFE5E1E6),
    inverseOnSurface = Color(0xFF303034),
    inversePrimary = Color(0xFF6750A4),
    surfaceContainerHighest = Color(0xFF191919),
    surfaceContainerHigh = Color(0xFF2C2C2C),
    surfaceContainer = Color(0xFF111111),
    surfaceContainerLow = Color(0xFF1C1C20),
    surfaceContainerLowest = Color(0xFF0D0D0F),
    primaryFixed = Color(0xFFEADDFF),
    primaryFixedDim = Color(0xFFD0BCFF),
    onPrimaryFixed = Color(0xFF21005D),
    onPrimaryFixedVariant = Color(0xFF4F378B),
    secondaryFixed = Color(0xFFE7E0EC),
    secondaryFixedDim = Color(0xFFCAC4D0),
    onSecondaryFixed = Color(0xFF1D1B20),
    onSecondaryFixedVariant = Color(0xFF49454F),
    tertiaryFixed = Color(0xFFE7E0EC),
    tertiaryFixedDim = Color(0xFFCAC4D0),
    onTertiaryFixed = Color(0xFF1D1B20),
    onTertiaryFixedVariant = Color(0xFF46464D),
    scrim = Color(0xFF000000)
)

val PurpleLightColorScheme = buildThemeColorScheme(dayNeutralBase, AppTheme.PURPLE, true)
val PurpleDarkColorScheme = buildThemeColorScheme(neutralBase, AppTheme.PURPLE, false)

val BlueLightColorScheme = buildThemeColorScheme(dayNeutralBase, AppTheme.BLUE, true)
val BlueDarkColorScheme = buildThemeColorScheme(neutralBase, AppTheme.BLUE, false)

val GreenLightColorScheme = buildThemeColorScheme(dayNeutralBase, AppTheme.GREEN, true)
val GreenDarkColorScheme = buildThemeColorScheme(neutralBase, AppTheme.GREEN, false)

val OrangeLightColorScheme = buildThemeColorScheme(dayNeutralBase, AppTheme.ORANGE, true)
val OrangeDarkColorScheme = buildThemeColorScheme(neutralBase, AppTheme.ORANGE, false)

val RedLightColorScheme = buildThemeColorScheme(dayNeutralBase, AppTheme.RED, true)
val RedDarkColorScheme = buildThemeColorScheme(neutralBase, AppTheme.RED, false)

val TealLightColorScheme = buildThemeColorScheme(dayNeutralBase, AppTheme.TEAL, true)
val TealDarkColorScheme = buildThemeColorScheme(neutralBase, AppTheme.TEAL, false)

val PinkLightColorScheme = buildThemeColorScheme(dayNeutralBase, AppTheme.PINK, true)
val PinkDarkColorScheme = buildThemeColorScheme(neutralBase, AppTheme.PINK, false)

val IndigoLightColorScheme = buildThemeColorScheme(dayNeutralBase, AppTheme.INDIGO, true)
val IndigoDarkColorScheme = buildThemeColorScheme(neutralBase, AppTheme.INDIGO, false)

val BrownLightColorScheme = buildThemeColorScheme(dayNeutralBase, AppTheme.BROWN, true)
val BrownDarkColorScheme = buildThemeColorScheme(neutralBase, AppTheme.BROWN, false)

val GrayLightColorScheme = buildThemeColorScheme(dayNeutralBase, AppTheme.GRAY, true)
val GrayDarkColorScheme = buildThemeColorScheme(neutralBase, AppTheme.GRAY, false)