package com.juhao.murexide.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln1p
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

val defaultSeed = Color(0xFF9BD7FF)

private const val LAB_KAPPA = 24389.0 / 27.0
private const val LAB_EPSILON = 216.0 / 24389.0

private val WHITE_POINT_D65 = doubleArrayOf(95.047, 100.0, 108.883)

private val XYZ_TO_SRGB = arrayOf(
    doubleArrayOf(3.2413774792388685, -1.5376652402851851, -0.49885366846268053),
    doubleArrayOf(-0.9691452513005321, 1.8758853451067872, 0.04156585616912061),
    doubleArrayOf(0.05562093689691305, -0.20395524564742123, 1.0571799111220335)
)

private val XYZ_TO_CAM16RGB = arrayOf(
    doubleArrayOf(0.401288, 0.650173, -0.051461),
    doubleArrayOf(-0.250268, 1.204414, 0.045854),
    doubleArrayOf(-0.002079, 0.048952, 0.953127)
)

private val CAM16RGB_TO_XYZ = arrayOf(
    doubleArrayOf(1.8620678, -1.0112547, 0.14918678),
    doubleArrayOf(0.38752654, 0.62144744, -0.00897398),
    doubleArrayOf(-0.0158415, -0.03412294, 1.0499644)
)

private class Cam16(
    val hue: Double,
    val chroma: Double,
    val j: Double,
    val jstar: Double,
    val astar: Double,
    val bstar: Double
)

private class ViewingConditions(
    val n: Double,
    val aw: Double,
    val nbb: Double,
    val ncb: Double,
    val c: Double,
    val nc: Double,
    val rgbD: DoubleArray,
    val fl: Double,
    val flRoot: Double,
    val z: Double
)

private fun labF(t: Double): Double {
    return if (t > LAB_EPSILON) cbrt(t) else (LAB_KAPPA * t + 16.0) / 116.0
}

private fun labInvf(ft: Double): Double {
    val ft3 = ft * ft * ft
    return if (ft3 > LAB_EPSILON) ft3 else (116.0 * ft - 16.0) / LAB_KAPPA
}

private fun yFromLstar(lstar: Double): Double = 100.0 * labInvf((lstar + 16.0) / 116.0)

private fun lstarFromY(y: Double): Double = labF(y / 100.0) * 116.0 - 16.0

private fun linearized(rgbComponent: Int): Double {
    val normalized = rgbComponent / 255.0
    return if (normalized <= 0.040449936) {
        normalized / 12.92 * 100.0
    } else {
        ((normalized + 0.055) / 1.055).pow(2.4) * 100.0
    }
}

private fun delinearized(rgbComponent: Double): Int {
    val normalized = rgbComponent / 100.0
    val delinearized = if (normalized <= 0.0031308) {
        normalized * 12.92
    } else {
        1.055 * normalized.pow(1.0 / 2.4) - 0.055
    }
    return (delinearized * 255.0).roundToInt().coerceIn(0, 255)
}

private fun sanitizeDegrees(degrees: Double): Double {
    val wrapped = degrees % 360.0
    return if (wrapped < 0.0) wrapped + 360.0 else wrapped
}

private fun lerp(start: Double, stop: Double, amount: Double): Double {
    return start + (stop - start) * amount
}

private fun argbFromRgb(red: Int, green: Int, blue: Int): Int {
    return (255 shl 24) or ((red and 255) shl 16) or ((green and 255) shl 8) or (blue and 255)
}

private fun argbFromXyz(x: Double, y: Double, z: Double): Int {
    val r = delinearized(XYZ_TO_SRGB[0][0] * x + XYZ_TO_SRGB[0][1] * y + XYZ_TO_SRGB[0][2] * z)
    val g = delinearized(XYZ_TO_SRGB[1][0] * x + XYZ_TO_SRGB[1][1] * y + XYZ_TO_SRGB[1][2] * z)
    val b = delinearized(XYZ_TO_SRGB[2][0] * x + XYZ_TO_SRGB[2][1] * y + XYZ_TO_SRGB[2][2] * z)
    return argbFromRgb(r, g, b)
}

private fun argbFromLstar(lstar: Double): Int {
    val component = delinearized(yFromLstar(lstar))
    return argbFromRgb(component, component, component)
}

private fun lstarFromArgb(argb: Int): Double {
    val r = (argb shr 16) and 255
    val g = (argb shr 8) and 255
    val b = argb and 255
    val y = 0.2126 * linearized(r) + 0.7152 * linearized(g) + 0.0722 * linearized(b)
    return lstarFromY(y)
}

private fun colorFromArgb(argb: Int): Color {
    return Color(
        red = ((argb shr 16) and 255) / 255f,
        green = ((argb shr 8) and 255) / 255f,
        blue = (argb and 255) / 255f,
        alpha = 1f
    )
}

private fun Color.toArgb(): Int {
    val r = (red * 255f).roundToInt().coerceIn(0, 255)
    val g = (green * 255f).roundToInt().coerceIn(0, 255)
    val b = (blue * 255f).roundToInt().coerceIn(0, 255)
    return argbFromRgb(r, g, b)
}

private val viewingConditions: ViewingConditions = run {
    val whitePoint = WHITE_POINT_D65
    val adaptingLuminance = 200.0 / PI * yFromLstar(50.0) / 100.0
    val backgroundLstar = 50.0
    val surround = 2.0
    val rW = whitePoint[0] * XYZ_TO_CAM16RGB[0][0] + whitePoint[1] * XYZ_TO_CAM16RGB[0][1] + whitePoint[2] * XYZ_TO_CAM16RGB[0][2]
    val gW = whitePoint[0] * XYZ_TO_CAM16RGB[1][0] + whitePoint[1] * XYZ_TO_CAM16RGB[1][1] + whitePoint[2] * XYZ_TO_CAM16RGB[1][2]
    val bW = whitePoint[0] * XYZ_TO_CAM16RGB[2][0] + whitePoint[1] * XYZ_TO_CAM16RGB[2][1] + whitePoint[2] * XYZ_TO_CAM16RGB[2][2]
    val f = 0.8 + surround / 10.0
    val c = lerp(0.59, 0.69, (f - 0.9) * 10.0)
    val d = (f * (1.0 - 1.0 / 3.6 * exp((-adaptingLuminance - 42.0) / 92.0))).coerceIn(0.0, 1.0)
    val rgbD = doubleArrayOf(
        d * (100.0 / rW) + 1.0 - d,
        d * (100.0 / gW) + 1.0 - d,
        d * (100.0 / bW) + 1.0 - d
    )
    val k = 1.0 / (5.0 * adaptingLuminance + 1.0)
    val k4 = k * k * k * k
    val k4f = 1.0 - k4
    val fl = k4 * adaptingLuminance + 0.1 * k4f * k4f * cbrt(5.0 * adaptingLuminance)
    val n = yFromLstar(backgroundLstar) / whitePoint[1]
    val z = 1.48 + sqrt(n)
    val nbb = 0.725 / n.pow(0.2)
    val rgbAFactors = doubleArrayOf(
        (fl * rgbD[0] * rW / 100.0).pow(0.42),
        (fl * rgbD[1] * gW / 100.0).pow(0.42),
        (fl * rgbD[2] * bW / 100.0).pow(0.42)
    )
    val rgbA = doubleArrayOf(
        400.0 * rgbAFactors[0] / (rgbAFactors[0] + 27.13),
        400.0 * rgbAFactors[1] / (rgbAFactors[1] + 27.13),
        400.0 * rgbAFactors[2] / (rgbAFactors[2] + 27.13)
    )
    val aw = (2.0 * rgbA[0] + rgbA[1] + 0.05 * rgbA[2]) * nbb
    ViewingConditions(n, aw, nbb, nbb, c, f, rgbD, fl, fl.pow(0.25), z)
}

private fun camFromXyz(x: Double, y: Double, z: Double): Cam16 {
    val vc = viewingConditions
    val rT = x * XYZ_TO_CAM16RGB[0][0] + y * XYZ_TO_CAM16RGB[0][1] + z * XYZ_TO_CAM16RGB[0][2]
    val gT = x * XYZ_TO_CAM16RGB[1][0] + y * XYZ_TO_CAM16RGB[1][1] + z * XYZ_TO_CAM16RGB[1][2]
    val bT = x * XYZ_TO_CAM16RGB[2][0] + y * XYZ_TO_CAM16RGB[2][1] + z * XYZ_TO_CAM16RGB[2][2]
    val rD = vc.rgbD[0] * rT
    val gD = vc.rgbD[1] * gT
    val bD = vc.rgbD[2] * bT
    fun adapt(value: Double): Double {
        val af = (vc.fl * abs(value) / 100.0).pow(0.42)
        return sign(value) * 400.0 * af / (af + 27.13)
    }
    val rA = adapt(rD)
    val gA = adapt(gD)
    val bA = adapt(bD)
    val a = (11.0 * rA + -12.0 * gA + bA) / 11.0
    val b = (rA + gA - 2.0 * bA) / 9.0
    val u = (20.0 * rA + 20.0 * gA + 21.0 * bA) / 20.0
    val p2 = (40.0 * rA + 20.0 * gA + bA) / 20.0
    val hue = sanitizeDegrees(Math.toDegrees(atan2(b, a)))
    val hueRadians = Math.toRadians(hue)
    val ac = p2 * vc.nbb
    val j = 100.0 * (ac / vc.aw).pow(vc.c * vc.z)
    val huePrime = if (hue < 20.14) hue + 360.0 else hue
    val eHue = 0.25 * (cos(Math.toRadians(huePrime) + 2.0) + 3.8)
    val p1 = 50000.0 / 13.0 * eHue * vc.nc * vc.ncb
    val t = p1 * hypot(a, b) / (u + 0.305)
    val alpha = (1.64 - 0.29.pow(vc.n)).pow(0.73) * t.pow(0.9)
    val chroma = alpha * sqrt(j / 100.0)
    val m = chroma * vc.flRoot
    val jstar = (1.0 + 100.0 * 0.007) * j / (1.0 + 0.007 * j)
    val mstar = ln1p(0.0228 * m) / 0.0228
    return Cam16(hue, chroma, j, jstar, mstar * cos(hueRadians), mstar * sin(hueRadians))
}

private fun camFromArgb(argb: Int): Cam16 {
    val red = (argb shr 16) and 255
    val green = (argb shr 8) and 255
    val blue = argb and 255
    val redL = linearized(red)
    val greenL = linearized(green)
    val blueL = linearized(blue)
    val x = 0.41233895 * redL + 0.35762064 * greenL + 0.18051042 * blueL
    val y = 0.2126 * redL + 0.7152 * greenL + 0.0722 * blueL
    val z = 0.01932141 * redL + 0.11916382 * greenL + 0.95034478 * blueL
    return camFromXyz(x, y, z)
}

private fun fromJch(j: Double, c: Double, h: Double): Cam16 {
    val m = c * viewingConditions.flRoot
    val hueRadians = Math.toRadians(h)
    val jstar = (1.0 + 100.0 * 0.007) * j / (1.0 + 0.007 * j)
    val mstar = ln1p(0.0228 * m) / 0.0228
    return Cam16(h, c, j, jstar, mstar * cos(hueRadians), mstar * sin(hueRadians))
}

private fun viewed(cam: Cam16): Int {
    val vc = viewingConditions
    val alpha = if (cam.chroma == 0.0 || cam.j == 0.0) 0.0 else cam.chroma / sqrt(cam.j / 100.0)
    val t = (alpha / (1.64 - 0.29.pow(vc.n)).pow(0.73)).pow(1.0 / 0.9)
    val hRad = Math.toRadians(cam.hue)
    val eHue = 0.25 * (cos(hRad + 2.0) + 3.8)
    val ac = vc.aw * (cam.j / 100.0).pow(1.0 / vc.c / vc.z)
    val p1 = eHue * (50000.0 / 13.0) * vc.nc * vc.ncb
    val p2 = ac / vc.nbb
    val hSin = sin(hRad)
    val hCos = cos(hRad)
    val gamma = 23.0 * (p2 + 0.305) * t / (23.0 * p1 + 11.0 * t * hCos + 108.0 * t * hSin)
    val a = gamma * hCos
    val b = gamma * hSin
    val rA = (460.0 * p2 + 451.0 * a + 288.0 * b) / 1403.0
    val gA = (460.0 * p2 - 891.0 * a - 261.0 * b) / 1403.0
    val bA = (460.0 * p2 - 220.0 * a - 6300.0 * b) / 1403.0
    fun cone(value: Double, discount: Double): Double {
        val base = max(0.0, 27.13 * abs(value) / (400.0 - abs(value)))
        return sign(value) * (100.0 / vc.fl) * base.pow(1.0 / 0.42) / discount
    }
    val rF = cone(rA, vc.rgbD[0])
    val gF = cone(gA, vc.rgbD[1])
    val bF = cone(bA, vc.rgbD[2])
    val x = rF * CAM16RGB_TO_XYZ[0][0] + gF * CAM16RGB_TO_XYZ[0][1] + bF * CAM16RGB_TO_XYZ[0][2]
    val y = rF * CAM16RGB_TO_XYZ[1][0] + gF * CAM16RGB_TO_XYZ[1][1] + bF * CAM16RGB_TO_XYZ[1][2]
    val z = rF * CAM16RGB_TO_XYZ[2][0] + gF * CAM16RGB_TO_XYZ[2][1] + bF * CAM16RGB_TO_XYZ[2][2]
    return argbFromXyz(x, y, z)
}

private fun distance(first: Cam16, second: Cam16): Double {
    val dJ = first.jstar - second.jstar
    val dA = first.astar - second.astar
    val dB = first.bstar - second.bstar
    return 1.41 * sqrt(dJ * dJ + dA * dA + dB * dB).pow(0.63)
}

private fun findCamByJ(hue: Double, chroma: Double, tone: Double): Cam16? {
    var low = 0.0
    var high = 100.0
    var bestDL = 1000.0
    var bestDE = 1000.0
    var best: Cam16? = null
    while (abs(low - high) > 0.01) {
        val mid = low + (high - low) / 2.0
        val clipped = viewed(fromJch(mid, chroma, hue))
        val clippedLstar = lstarFromArgb(clipped)
        val dL = abs(tone - clippedLstar)
        if (dL < 0.2) {
            val camClipped = camFromArgb(clipped)
            val dE = distance(camClipped, fromJch(camClipped.j, camClipped.chroma, hue))
            if (dE <= 1.0 && dE <= bestDE) {
                bestDL = dL
                bestDE = dE
                best = camClipped
            }
        }
        if (bestDL == 0.0 && bestDE == 0.0) break
        if (clippedLstar < tone) low = mid else high = mid
    }
    return best
}

private fun solveToInt(hue: Double, chroma: Double, tone: Double): Int {
    if (chroma < 1.0 || tone.roundToInt() <= 0 || tone.roundToInt() >= 100) {
        return argbFromLstar(tone)
    }
    val sanitizedHue = sanitizeDegrees(hue)
    var high = chroma
    var low = 0.0
    var mid = chroma
    var first = true
    var answer: Cam16? = null
    while (abs(low - high) >= 0.4) {
        val possible = findCamByJ(sanitizedHue, mid, tone)
        if (first) {
            if (possible != null) return viewed(possible)
            first = false
            mid = low + (high - low) / 2.0
            continue
        }
        if (possible == null) {
            high = mid
        } else {
            answer = possible
            low = mid
        }
        mid = low + (high - low) / 2.0
    }
    return if (answer == null) argbFromLstar(tone) else viewed(answer)
}

private class TonalPalette(private val hue: Double, private val chroma: Double) {
    private val cache = arrayOfNulls<Int>(101)

    fun tone(tone: Int): Color {
        val key = tone.coerceIn(0, 100)
        val cached = cache[key]
        if (cached != null) return colorFromArgb(cached)
        val argb = solveToInt(hue, chroma, key.toDouble())
        cache[key] = argb
        return colorFromArgb(argb)
    }
}

private fun schemeFromPalettes(
    primary: TonalPalette,
    secondary: TonalPalette,
    tertiary: TonalPalette,
    neutral: TonalPalette,
    neutralVariant: TonalPalette,
    error: TonalPalette,
    scrim: Color,
    isDay: Boolean
): ColorScheme {
    val primaryFixed = primary.tone(90)
    val primaryFixedDim = primary.tone(80)
    val onPrimaryFixed = primary.tone(10)
    val onPrimaryFixedVariant = primary.tone(30)
    val secondaryFixed = secondary.tone(90)
    val secondaryFixedDim = secondary.tone(80)
    val onSecondaryFixed = secondary.tone(10)
    val onSecondaryFixedVariant = secondary.tone(30)
    val tertiaryFixed = tertiary.tone(90)
    val tertiaryFixedDim = tertiary.tone(80)
    val onTertiaryFixed = tertiary.tone(10)
    val onTertiaryFixedVariant = tertiary.tone(30)
    return if (isDay) {
        val primaryColor = primary.tone(40)
        lightColorScheme(
            primary = primaryColor,
            onPrimary = primary.tone(100),
            primaryContainer = primary.tone(90),
            onPrimaryContainer = primary.tone(10),
            inversePrimary = primary.tone(80),
            secondary = secondary.tone(40),
            onSecondary = secondary.tone(100),
            secondaryContainer = secondary.tone(90),
            onSecondaryContainer = secondary.tone(10),
            tertiary = tertiary.tone(40),
            onTertiary = tertiary.tone(100),
            tertiaryContainer = tertiary.tone(90),
            onTertiaryContainer = tertiary.tone(10),
            background = neutral.tone(98),
            onBackground = neutral.tone(10),
            surface = neutral.tone(98),
            onSurface = neutral.tone(10),
            surfaceVariant = neutralVariant.tone(90),
            onSurfaceVariant = neutralVariant.tone(30),
            surfaceTint = primaryColor,
            inverseSurface = neutral.tone(20),
            inverseOnSurface = neutral.tone(95),
            error = error.tone(40),
            onError = error.tone(100),
            errorContainer = error.tone(90),
            onErrorContainer = error.tone(10),
            outline = neutralVariant.tone(50),
            outlineVariant = neutralVariant.tone(80),
            scrim = scrim,
            surfaceBright = neutral.tone(98),
            surfaceDim = neutral.tone(87),
            surfaceContainerLowest = neutral.tone(100),
            surfaceContainerLow = neutral.tone(96),
            surfaceContainer = neutral.tone(94),
            surfaceContainerHigh = neutral.tone(92),
            surfaceContainerHighest = neutral.tone(90),
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
    } else {
        val primaryColor = primary.tone(80)
        darkColorScheme(
            primary = primaryColor,
            onPrimary = primary.tone(20),
            primaryContainer = primary.tone(30),
            onPrimaryContainer = primary.tone(90),
            inversePrimary = primary.tone(40),
            secondary = secondary.tone(80),
            onSecondary = secondary.tone(20),
            secondaryContainer = secondary.tone(30),
            onSecondaryContainer = secondary.tone(90),
            tertiary = tertiary.tone(80),
            onTertiary = tertiary.tone(20),
            tertiaryContainer = tertiary.tone(30),
            onTertiaryContainer = tertiary.tone(90),
            background = neutral.tone(6),
            onBackground = neutral.tone(90),
            surface = neutral.tone(6),
            onSurface = neutral.tone(90),
            surfaceVariant = neutralVariant.tone(30),
            onSurfaceVariant = neutralVariant.tone(80),
            surfaceTint = primaryColor,
            inverseSurface = neutral.tone(90),
            inverseOnSurface = neutral.tone(20),
            error = error.tone(80),
            onError = error.tone(20),
            errorContainer = error.tone(30),
            onErrorContainer = error.tone(90),
            outline = neutralVariant.tone(60),
            outlineVariant = neutralVariant.tone(30),
            scrim = scrim,
            surfaceBright = neutral.tone(24),
            surfaceDim = neutral.tone(6),
            surfaceContainerLowest = neutral.tone(4),
            surfaceContainerLow = neutral.tone(10),
            surfaceContainer = neutral.tone(12),
            surfaceContainerHigh = neutral.tone(17),
            surfaceContainerHighest = neutral.tone(22),
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
}

private val neutralBase: ColorScheme = darkColorScheme(
    error = Color(0xFFFFC4BC),
    onError = Color(0xFF741210),
    errorContainer = Color(0xFF8C1515),
    onErrorContainer = Color(0xFFFFE5E1)
)

private val dayNeutralBase: ColorScheme = lightColorScheme(
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B)
)

fun buildEventColorScheme(
    base: ColorScheme,
    eventColor: Color,
    eventUrgent: Boolean = false,
    isDay: Boolean = false
): ColorScheme {
    val seed = camFromArgb((if (eventUrgent) base.error else eventColor).toArgb())
    val primaryChroma = if (eventUrgent) 84.0 else 36.0
    val primary = TonalPalette(seed.hue, primaryChroma)
    val secondary = TonalPalette(seed.hue, 16.0)
    val tertiary = TonalPalette(sanitizeDegrees(seed.hue + 60.0), 24.0)
    val neutral = TonalPalette(seed.hue, 6.0)
    val neutralVariant = TonalPalette(seed.hue, 8.0)
    val error = TonalPalette(if (eventUrgent) seed.hue else 25.0, 84.0)
    return schemeFromPalettes(primary, secondary, tertiary, neutral, neutralVariant, error, base.scrim, isDay)
}

fun buildCustomColorScheme(
    base: ColorScheme,
    customColor: Color,
    isDay: Boolean = false
): ColorScheme = buildEventColorScheme(base, customColor, false, isDay)

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
): ColorScheme = buildEventColorScheme(base, theme.seedColor, false, isDay)

fun toOledColorScheme(scheme: ColorScheme): ColorScheme = scheme.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black
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