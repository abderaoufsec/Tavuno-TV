package com.tavuno.tv.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * Tavuno TV's resolved Material 3 colour roles for the current theme + accent, ported from the
 * OwnTV-Baseline design system. Read it as `TavunoTheme.colors`.
 *
 * Exposes the full M3 surface-container tiers plus the primary/secondary/tertiary roles the MD3
 * shell needs. A few legacy aliases (`panel`/`card`/`rail`/`textPrimary`/`textSecondary`/`accent`)
 * map onto M3 roles so older components keep working.
 */
@Immutable
data class TavunoColors(
    val isDark: Boolean,
    // Surfaces
    val background: Color,
    val surface: Color,
    val surfaceContainerLowest: Color,
    val surfaceContainerLow: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val outlineVariant: Color,
    // Primary
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    // Secondary
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    // Tertiary
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    // Focus / status
    val focusBorder: Color,
    val focusGlow: Color,
    val favorite: Color,
    /**
     * The accent as it must look on the player's chrome, which is always a dark scrim over video
     * no matter which theme the app is in. On the light theme [primary] is the deep tone M3 picks
     * for a light surface (teal becomes #006B5E), which all but disappears against a dark HUD.
     * This is the same accent resolved for dark surfaces, so the seek bar and active buttons stay
     * readable — and on the dark theme it is simply [primary].
     */
    val accentOnVideo: Color,
    /** Text/icon colour for content drawn ON [accentOnVideo] — e.g. the count inside a badge. */
    val onAccentOnVideo: Color,
    // Shell per-region identity (not part of the M3 ladder).
    val railPanelFill: Color,
    val contentPanelFill: Color,
    val previewPanelFill: Color,
) {
    // Legacy aliases used by components written before the restyle.
    val textPrimary: Color get() = onSurface
    val textSecondary: Color get() = onSurfaceVariant
    val panel: Color get() = surfaceContainerLow
    val card: Color get() = surfaceContainerHigh
    val rail: Color get() = surfaceContainer
    val accent: Color get() = primary
}

/** The four M3 primary roles, resolved either from a preset or generated from a custom seed. */
private data class AccentRoles(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
)

/** Wrap a preset's ARGB role table in Compose [Color]s. */
private fun TavunoAccentColor.toRoles(isDark: Boolean): AccentRoles {
    val v = roles(isDark)
    return AccentRoles(
        primary = Color(v.primary),
        onPrimary = Color(v.onPrimary),
        primaryContainer = Color(v.primaryContainer),
        onPrimaryContainer = Color(v.onPrimaryContainer),
    )
}

/**
 * Generate tonal primary roles from an arbitrary seed colour (the custom hex accent).
 * The derivation is [accentRolesFromSeed]; this only wraps its values in [Color].
 */
private fun rolesFrom(seed: Color, isDark: Boolean): AccentRoles {
    val roles = accentRolesFromSeed(
        seed = seed.toArgb().toLong() and 0xFFFFFFFFL,
        isDark = isDark,
    )
    return AccentRoles(
        primary = Color(roles.primary),
        onPrimary = Color(roles.onPrimary),
        primaryContainer = Color(roles.primaryContainer),
        onPrimaryContainer = Color(roles.onPrimaryContainer),
    )
}

/**
 * Build the resolved M3 tokens for a theme (dark/light) and accent. A valid [customAccent] hex
 * overrides the preset (its tonal roles are generated from the seed colour).
 *
 * [focusHighlight] recolours the focus ring and its glow only: the accent still owns buttons,
 * chips and containers, so a loud focus colour does not repaint the whole app. Blank = the accent.
 */
fun tavunoColors(
    isDark: Boolean,
    accent: TavunoAccentColor,
    customAccent: String = "",
    focusHighlight: String = "",
): TavunoColors {
    val roles = parseAccentHex(customAccent)?.let { rolesFrom(Color(it), isDark) }
        ?: accent.toRoles(isDark)
    val primary = roles.primary
    // Always the dark-surface tone of the same accent: the player HUD is dark chrome in every theme.
    val onVideoRoles = parseAccentHex(customAccent)?.let { rolesFrom(Color(it), true) }
        ?: accent.toRoles(true)
    val focus = parseAccentHex(focusHighlight)?.let { Color(it) } ?: primary
    return if (isDark) {
        TavunoColors(
            isDark = true,
            background = DarkBackground,
            surface = DarkSurface,
            surfaceContainerLowest = DarkSurfaceContainerLowest,
            surfaceContainerLow = DarkSurfaceContainerLow,
            surfaceContainer = DarkSurfaceContainer,
            surfaceContainerHigh = DarkSurfaceContainerHigh,
            surfaceContainerHighest = DarkSurfaceContainerHighest,
            onSurface = DarkOnSurface,
            onSurfaceVariant = DarkOnSurfaceVariant,
            outline = DarkOutline,
            outlineVariant = DarkOutlineVariant,
            primary = primary,
            onPrimary = roles.onPrimary,
            primaryContainer = roles.primaryContainer,
            onPrimaryContainer = roles.onPrimaryContainer,
            secondary = DarkSecondary,
            onSecondary = DarkOnSecondary,
            secondaryContainer = DarkSecondaryContainer,
            onSecondaryContainer = DarkOnSecondaryContainer,
            tertiary = DarkTertiary,
            onTertiary = DarkOnTertiary,
            tertiaryContainer = DarkTertiaryContainer,
            onTertiaryContainer = DarkOnTertiaryContainer,
            focusBorder = focus,
            focusGlow = focus.copy(alpha = 0.40f),
            favorite = DarkError,
            accentOnVideo = onVideoRoles.primary,
            onAccentOnVideo = onVideoRoles.onPrimary,
            railPanelFill = Color(TavunoPalette.DarkRailPanel),
            contentPanelFill = Color(TavunoPalette.DarkContentPanel),
            previewPanelFill = Color(TavunoPalette.DarkPreviewPanel),
        )
    } else {
        TavunoColors(
            isDark = false,
            background = LightBackground,
            surface = LightSurface,
            surfaceContainerLowest = LightSurfaceContainerLowest,
            surfaceContainerLow = LightSurfaceContainerLow,
            surfaceContainer = LightSurfaceContainer,
            surfaceContainerHigh = LightSurfaceContainerHigh,
            surfaceContainerHighest = LightSurfaceContainerHighest,
            onSurface = LightOnSurface,
            onSurfaceVariant = LightOnSurfaceVariant,
            outline = LightOutline,
            outlineVariant = LightOutlineVariant,
            primary = primary,
            onPrimary = roles.onPrimary,
            primaryContainer = roles.primaryContainer,
            onPrimaryContainer = roles.onPrimaryContainer,
            secondary = LightSecondary,
            onSecondary = LightOnSecondary,
            secondaryContainer = LightSecondaryContainer,
            onSecondaryContainer = LightOnSecondaryContainer,
            tertiary = LightTertiary,
            onTertiary = LightOnTertiary,
            tertiaryContainer = LightTertiaryContainer,
            onTertiaryContainer = LightOnTertiaryContainer,
            focusBorder = focus,
            focusGlow = focus.copy(alpha = 0.28f),
            favorite = LightError,
            accentOnVideo = onVideoRoles.primary,
            onAccentOnVideo = onVideoRoles.onPrimary,
            railPanelFill = Color(TavunoPalette.LightRailPanel),
            contentPanelFill = Color(TavunoPalette.LightContentPanel),
            previewPanelFill = Color(TavunoPalette.LightPreviewPanel),
        )
    }
}

val LocalTavunoColors = staticCompositionLocalOf {
    tavunoColors(isDark = true, accent = TavunoAccentColor.TEAL)
}