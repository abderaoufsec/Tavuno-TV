package com.tavuno.tv.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ColorScheme
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme
// Compatibility bridge for the few screens still using the phone Material 3 components.
import androidx.compose.material3.MaterialTheme as PhoneMaterialTheme
import androidx.compose.material3.darkColorScheme as phoneDarkColorScheme
import androidx.compose.material3.lightColorScheme as phoneLightColorScheme

/**
 * Available Tavuno themes, ported from the OwnTV-Baseline design system. Persisted via DataStore
 * and selectable from Settings → Theme. SYSTEM follows the platform dark/light setting.
 */
enum class ThemeMode { SYSTEM, DARK, LIGHT }

val LocalThemeMode = staticCompositionLocalOf { ThemeMode.DARK }

/**
 * Width of the focus ring drawn by FocusableSurface — ported from OwnTV's "make the selection
 * prominent" work. A CompositionLocal rather than a [Dimens] constant because the user picks it in
 * Settings, and every focusable surface in the app has to follow the same choice.
 */
val LocalFocusBorderWidth = staticCompositionLocalOf { Dimens.FocusBorderWidth }

/** The offered ring widths in dp — thin, normal, thick, extra thick. 2 dp is the shipped default. */
val FocusBorderWidthChoices = listOf(1, 2, 4, 6)

/**
 * Map the resolved Tavuno tokens onto a tv-material3 M3 [ColorScheme].
 *
 * tv-material3 1.0.0-alpha10's ColorScheme has no `surfaceContainer*` ladder, so the container
 * tiers stay on [TavunoColors] and only the roles tv-material3 knows about are mapped here.
 */
private fun schemeFrom(c: TavunoColors): ColorScheme =
    if (c.isDark) {
        darkColorScheme(
            primary = c.primary,
            onPrimary = c.onPrimary,
            primaryContainer = c.primaryContainer,
            onPrimaryContainer = c.onPrimaryContainer,
            secondary = c.secondary,
            onSecondary = c.onSecondary,
            secondaryContainer = c.secondaryContainer,
            onSecondaryContainer = c.onSecondaryContainer,
            tertiary = c.tertiary,
            onTertiary = c.onTertiary,
            tertiaryContainer = c.tertiaryContainer,
            onTertiaryContainer = c.onTertiaryContainer,
            background = c.background,
            onBackground = c.onSurface,
            surface = c.surface,
            onSurface = c.onSurface,
            surfaceVariant = c.surfaceContainerHigh,
            onSurfaceVariant = c.onSurfaceVariant,
            border = c.outline,
            error = c.favorite,
        )
    } else {
        lightColorScheme(
            primary = c.primary,
            onPrimary = c.onPrimary,
            primaryContainer = c.primaryContainer,
            onPrimaryContainer = c.onPrimaryContainer,
            secondary = c.secondary,
            onSecondary = c.onSecondary,
            secondaryContainer = c.secondaryContainer,
            onSecondaryContainer = c.onSecondaryContainer,
            tertiary = c.tertiary,
            onTertiary = c.onTertiary,
            tertiaryContainer = c.tertiaryContainer,
            onTertiaryContainer = c.onTertiaryContainer,
            background = c.background,
            onBackground = c.onSurface,
            surface = c.surface,
            onSurface = c.onSurface,
            surfaceVariant = c.surfaceContainerHigh,
            onSurfaceVariant = c.onSurfaceVariant,
            border = c.outline,
            error = c.favorite,
        )
    }

/**
 * Compatibility bridge for the handful of screens that still use `androidx.compose.material3`
 * components — the login email/password fields, the player's seek `Slider`, and the loading
 * `CircularProgressIndicator`s.
 *
 * `androidx.compose.material3` keeps its OWN theme, entirely separate from tv-material3's. Because
 * nothing provided one, those components fell back to compose.material3's LIGHT baseline, so the
 * login fields rendered as white slabs on the dark shell. This maps the same resolved
 * [TavunoColors] onto the roles compose.material3 1.1.2 knows.
 *
 * This is a stop-gap, not the target state: new work must use the tv-material3 + design-system
 * components, and the remaining phone-component call sites should be migrated to them.
 */
private fun phoneSchemeFrom(c: TavunoColors) =
    if (c.isDark) {
        phoneDarkColorScheme(
            primary = c.primary,
            onPrimary = c.onPrimary,
            primaryContainer = c.primaryContainer,
            onPrimaryContainer = c.onPrimaryContainer,
            secondary = c.secondary,
            onSecondary = c.onSecondary,
            secondaryContainer = c.secondaryContainer,
            onSecondaryContainer = c.onSecondaryContainer,
            tertiary = c.tertiary,
            onTertiary = c.onTertiary,
            tertiaryContainer = c.tertiaryContainer,
            onTertiaryContainer = c.onTertiaryContainer,
            background = c.background,
            onBackground = c.onSurface,
            surface = c.surface,
            onSurface = c.onSurface,
            surfaceVariant = c.surfaceContainerHigh,
            onSurfaceVariant = c.onSurfaceVariant,
            outline = c.outline,
            outlineVariant = c.outlineVariant,
            error = c.favorite,
        )
    } else {
        phoneLightColorScheme(
            primary = c.primary,
            onPrimary = c.onPrimary,
            primaryContainer = c.primaryContainer,
            onPrimaryContainer = c.onPrimaryContainer,
            secondary = c.secondary,
            onSecondary = c.onSecondary,
            secondaryContainer = c.secondaryContainer,
            onSecondaryContainer = c.onSecondaryContainer,
            tertiary = c.tertiary,
            onTertiary = c.onTertiary,
            tertiaryContainer = c.tertiaryContainer,
            onTertiaryContainer = c.onTertiaryContainer,
            background = c.background,
            onBackground = c.onSurface,
            surface = c.surface,
            onSurface = c.onSurface,
            surfaceVariant = c.surfaceContainerHigh,
            onSurfaceVariant = c.onSurfaceVariant,
            outline = c.outline,
            outlineVariant = c.outlineVariant,
            error = c.favorite,
        )
    }

/**
 * The Tavuno TV theme. Mirrors the OwnTV-Baseline entry point: it resolves the accent + theme mode
 * into the full M3 role set, publishes it through [LocalTavunoColors] and the tv-material3
 * [MaterialTheme], and hands the shell a TV-scaled type ramp.
 */
@Composable
fun TavunoTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    accent: TavunoAccentColor = TavunoAccentColor.TEAL,
    systemInDarkTheme: Boolean = isSystemInDarkTheme(),
    customAccent: String = "",
    focusHighlight: String = "",
    focusBorderWidthDp: Int = 2,
    fontFamily: FontFamily = FontFamily.SansSerif,
    animationsEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val useDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemInDarkTheme
    }

    val colors = tavunoColors(
        isDark = useDark,
        accent = accent,
        customAccent = customAccent,
        focusHighlight = focusHighlight,
    )

    CompositionLocalProvider(
        LocalTavunoColors provides colors,
        LocalThemeMode provides themeMode,
        LocalFocusBorderWidth provides focusBorderWidthDp.dp,
        LocalAnimationsEnabled provides animationsEnabled,
    ) {
        MaterialTheme(
            colorScheme = schemeFrom(colors),
            typography = tavunoTypography(fontFamily),
        ) {
            // Bridge the phone Material 3 theme so any leftover compose.material3 component (login
            // fields, player seek slider, its CircularProgressIndicators) follows the same palette
            // instead of falling back to compose.material3's light baseline.
            PhoneMaterialTheme(
                colorScheme = phoneSchemeFrom(colors),
                content = content,
            )
        }
    }
}

/** Convenience accessor: `TavunoTheme.colors.focusBorder`. */
object TavunoTheme {
    val colors: TavunoColors
        @Composable
        @ReadOnlyComposable
        get() = LocalTavunoColors.current
}