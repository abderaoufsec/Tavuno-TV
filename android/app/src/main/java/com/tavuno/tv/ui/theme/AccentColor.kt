package com.tavuno.tv.ui.theme

import androidx.core.graphics.ColorUtils

/**
 * Material You-style accent presets, ported from the OwnTV-Baseline design system.
 *
 * Android TV cannot rely on true wallpaper-based dynamic colour (a phone feature), so instead the
 * user picks an accent and the M3 colour scheme is seeded from it. This enum is only the user's
 * *choice*; the tonal roles each preset seeds live in the tables below.
 */
enum class TavunoAccentColor { TEAL, BLUE, VIOLET, GREEN, AMBER }

/**
 * The four M3 primary roles for one accent on one theme, as ARGB longs.
 * They come either from a preset's table ([roles]) or are generated from a custom hex seed
 * ([accentRolesFromSeed]).
 */
data class AccentRoleValues(
    val primary: Long,
    val onPrimary: Long,
    val primaryContainer: Long,
    val onPrimaryContainer: Long,
)

/** The tonal palette each [TavunoAccentColor] seeds the M3 colour scheme with, for both themes. */
private class AccentPalette(val dark: AccentRoleValues, val light: AccentRoleValues)

private val TealPalette = AccentPalette(
    dark = AccentRoleValues(0xFF52DBC8L, 0xFF003730L, 0xFF004F46L, 0xFF6FF8E4L),
    light = AccentRoleValues(0xFF006B5EL, 0xFFFFFFFFL, 0xFF6FF8E4L, 0xFF00201BL),
)

private val BluePalette = AccentPalette(
    dark = AccentRoleValues(0xFF6FB0FFL, 0xFF00315CL, 0xFF134A7CL, 0xFFD3E4FFL),
    light = AccentRoleValues(0xFF1565C0L, 0xFFFFFFFFL, 0xFFD6E3FFL, 0xFF001C3AL),
)

private val VioletPalette = AccentPalette(
    dark = AccentRoleValues(0xFFCBBEFFL, 0xFF312170L, 0xFF483A88L, 0xFFE7DEFFL),
    light = AccentRoleValues(0xFF5B45C9L, 0xFFFFFFFFL, 0xFFE5DEFFL, 0xFF190066L),
)

private val GreenPalette = AccentPalette(
    dark = AccentRoleValues(0xFF6FDB94L, 0xFF00391CL, 0xFF1F5135L, 0xFF8BF8AFL),
    light = AccentRoleValues(0xFF1B6B3FL, 0xFFFFFFFFL, 0xFFA6F2C0L, 0xFF00210FL),
)

private val AmberPalette = AccentPalette(
    dark = AccentRoleValues(0xFFFFB95CL, 0xFF452B00L, 0xFF624000L, 0xFFFFDDB3L),
    light = AccentRoleValues(0xFF8A5100L, 0xFFFFFFFFL, 0xFFFFDDB3L, 0xFF2C1600L),
)

/** The preset's four primary-role values for the given theme. */
fun TavunoAccentColor.roles(isDark: Boolean): AccentRoleValues {
    val palette = when (this) {
        TavunoAccentColor.TEAL -> TealPalette
        TavunoAccentColor.BLUE -> BluePalette
        TavunoAccentColor.VIOLET -> VioletPalette
        TavunoAccentColor.GREEN -> GreenPalette
        TavunoAccentColor.AMBER -> AmberPalette
    }
    return if (isDark) palette.dark else palette.light
}

/** Parses "#RRGGBB" / "RRGGBB" (also 8-digit AARRGGBB) into an ARGB long; null when invalid. */
fun parseAccentHex(hex: String): Long? {
    val s = hex.trim().removePrefix("#")
    return runCatching {
        when (s.length) {
            6 -> 0xFF000000L or s.toLong(16)
            8 -> s.toLong(16)
            else -> null
        }
    }.getOrNull()
}

/**
 * Generate tonal primary roles from an arbitrary seed colour (the custom hex accent).
 * The seed is used EXACTLY as `primary` so the user's hex renders true; only the supporting
 * contrast roles (onPrimary / containers) are derived by nudging the seed's lightness.
 */
fun accentRolesFromSeed(seed: Long, isDark: Boolean): AccentRoleValues {
    val argb = seed.toInt()
    // Choose a readable foreground for text/icons drawn on top of the exact seed colour.
    val onPrimary = if (ColorUtils.calculateLuminance(argb) > 0.5) {
        0xFF000000L
    } else {
        0xFFFFFFFFL
    }
    return if (isDark) {
        AccentRoleValues(seed, onPrimary, argb.withLightness(0.26f), argb.withLightness(0.90f))
    } else {
        AccentRoleValues(seed, onPrimary, argb.withLightness(0.88f), argb.withLightness(0.10f))
    }
}

/** Keep the seed's hue/saturation but pin the HSL lightness — a cheap stand-in for M3 tones. */
private fun Int.withLightness(l: Float): Long {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(this, hsl)
    hsl[2] = l
    return ColorUtils.HSLToColor(hsl).toLong() and 0xFFFFFFFFL
}