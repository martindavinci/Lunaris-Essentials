package io.github.martindavinci.lunaris.archives.ui.theme

import androidx.compose.ui.graphics.Color

// One accent, the azure of the Lunaris family, on neutral surfaces. Red stays in the icon and in errors; warnings are amber.
val LunarisBackground = Color(0xFF15171C)
val LunarisSurface = Color(0xFF1E2127)
val LunarisSurfaceVariant = Color(0xFF2B2F37)
val LunarisSurfaceHigh = Color(0xFF262A31)
val LunarisOnSurface = Color(0xFFECEEF1)
val LunarisOnSurfaceVariant = Color(0xFFAAB0B9)
val LunarisOutline = Color(0xFF3B414A)
val LunarisAccent = Color(0xFF2E9BE0)
val LunarisOnAccent = Color(0xFF06263E)
val LunarisAccentSoft = Color(0xFF7CC2F0)
val LunarisWarning = Color(0xFF93221B)
val LunarisOnWarning = Color(0xFFFFDAD6)
val LunarisError = Color(0xFFF2B8B5)

// The dark shade of the accent, in both themes: the button that opens a document, and what is switched on.
val LunarisDeepBlue = Color(0xFF203D62)
val LunarisOnDeepBlue = Color(0xFFF2F6FB)
val LunarisOnDeepBlueVariant = Color(0xFFB9CBE3)

// Light palette: the same accent, darkened for contrast on white.
val LunarisLightBackground = Color(0xFFF6F7F9)
val LunarisLightSurface = Color(0xFFFFFFFF)
val LunarisLightSurfaceVariant = Color(0xFFECEEF2)
val LunarisLightOnSurface = Color(0xFF171A1F)
val LunarisLightOnSurfaceVariant = Color(0xFF5A616B)
val LunarisLightOutline = Color(0xFFD2D6DD)
val LunarisLightAccent = Color(0xFF1A6DA8)
val LunarisLightOnAccent = Color(0xFFFFFFFF)
val LunarisLightWarning = Color(0xFFFBE3E0)
val LunarisLightOnWarning = Color(0xFF8C1D18)
val LunarisLightError = Color(0xFF8C1D18)

/** A warning that is not an error: amber, never red. [container] behind, [content] on top. */
data class WarningPair(val container: Color, val content: Color)

object WarningColors {
    val dark = WarningPair(container = Color(0xFF5A3D00), content = Color(0xFFFFDDB0))
    val light = WarningPair(container = Color(0xFFFFE3B3), content = Color(0xFF5A3A00))
}

/** WCAG contrast ratio, from 1 (same colour) to 21 (black on white). */
fun contrastRatio(a: Color, b: Color): Double {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    return (maxOf(la, lb) + CONTRAST_OFFSET) / (minOf(la, lb) + CONTRAST_OFFSET)
}

private fun relativeLuminance(c: Color): Double =
    LUMA_RED * linear(c.red) + LUMA_GREEN * linear(c.green) + LUMA_BLUE * linear(c.blue)

private fun linear(channel: Float): Double {
    val v = channel.toDouble()
    return if (v <= SRGB_KNEE) v / SRGB_LINEAR_DIVISOR else Math.pow((v + SRGB_OFFSET) / (1 + SRGB_OFFSET), SRGB_GAMMA)
}

private const val CONTRAST_OFFSET = 0.05
private const val LUMA_RED = 0.2126
private const val LUMA_GREEN = 0.7152
private const val LUMA_BLUE = 0.0722
private const val SRGB_KNEE = 0.04045
private const val SRGB_LINEAR_DIVISOR = 12.92
private const val SRGB_OFFSET = 0.055
private const val SRGB_GAMMA = 2.4
