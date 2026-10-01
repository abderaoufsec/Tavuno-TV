package com.tavuno.tv.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Material 3 tonal palette for Tavuno TV, ported verbatim from the OwnTV-Baseline design system
 * (its `OwnTVPalette`). Values are ARGB longs so the two apps read one source of truth; each
 * Compose colour wraps a value exactly once: `Color(TavunoPalette.DarkBackground)`.
 *
 * Dark uses a near-black background (#040E0B) with a subtle green undertone so the shell's panel
 * colours pop against the deep dark surface while staying distinct from each other. NEUTRAL and
 * the secondary/tertiary roles are theme-only; the `primary` roles are seeded per
 * [TavunoAccentColor] (default TEAL).
 *
 * The three `*RailPanel` / `*ContentPanel` / `*PreviewPanel` values are NOT part of the M3 ladder:
 * they are the shell's per-region colour identity, keeping navigation, content and the preview
 * pane visually separate instead of collapsing into the greyer generic elevation steps.
 */
object TavunoPalette {

    /** Brand mark colour (the play logo) — constant, and the default teal accent. */
    const val AccentCyan = 0xFF52DBC8L

    // ---------------- DARK (M3 dark over near-black #040E0B) ----------------
    const val DarkBackground = 0xFF040E0BL
    const val DarkSurface = 0xFF0E1513L
    const val DarkSurfaceContainerLowest = 0xFF090F0EL
    const val DarkSurfaceContainerLow = 0xFF161D1BL
    const val DarkSurfaceContainer = 0xFF1B211FL
    const val DarkSurfaceContainerHigh = 0xFF252B29L
    const val DarkSurfaceContainerHighest = 0xFF303634L
    const val DarkRailPanel = 0xFF111C18L
    const val DarkContentPanel = 0xFF0A1512L
    const val DarkPreviewPanel = 0xFF15201DL
    const val DarkOnSurface = 0xFFDEE4E1L
    const val DarkOnSurfaceVariant = 0xFFBFC9C4L
    const val DarkOutline = 0xFF89938FL
    const val DarkOutlineVariant = 0xFF3F4945L
    const val DarkSecondary = 0xFFB1CCC3L
    const val DarkOnSecondary = 0xFF1C352EL
    const val DarkSecondaryContainer = 0xFF334B44L
    const val DarkOnSecondaryContainer = 0xFFCDE8DFL
    const val DarkTertiary = 0xFFA9CBE4L
    const val DarkOnTertiary = 0xFF0B3445L
    const val DarkTertiaryContainer = 0xFF294B5DL
    const val DarkOnTertiaryContainer = 0xFFC5E7FFL
    const val DarkError = 0xFFFFB4ABL

    // ---------------- LIGHT (M3 light) ----------------
    const val LightBackground = 0xFFF5FBF8L
    const val LightSurface = 0xFFF5FBF8L
    const val LightSurfaceContainerLowest = 0xFFFFFFFFL
    const val LightSurfaceContainerLow = 0xFFEFF5F2L
    const val LightSurfaceContainer = 0xFFE9EFECL
    const val LightSurfaceContainerHigh = 0xFFE3EAE6L
    const val LightSurfaceContainerHighest = 0xFFDEE4E1L
    const val LightRailPanel = 0xFFE6EEE9L
    const val LightContentPanel = 0xFFF2F7F4L
    const val LightPreviewPanel = 0xFFDEE9E3L
    const val LightOnSurface = 0xFF171D1BL
    const val LightOnSurfaceVariant = 0xFF3F4945L
    const val LightOutline = 0xFF6F7975L
    const val LightOutlineVariant = 0xFFBFC9C4L
    const val LightSecondary = 0xFF4B635CL
    const val LightOnSecondary = 0xFFFFFFFFL
    const val LightSecondaryContainer = 0xFFCDE8DFL
    const val LightOnSecondaryContainer = 0xFF07201AL
    const val LightTertiary = 0xFF416278L
    const val LightOnTertiary = 0xFFFFFFFFL
    const val LightTertiaryContainer = 0xFFC5E7FFL
    const val LightOnTertiaryContainer = 0xFF001E2FL
    const val LightError = 0xFFBA1A1AL
}

// ---------------- DARK (Compose wrappers) ----------------
val DarkBackground = Color(TavunoPalette.DarkBackground)
val DarkSurface = Color(TavunoPalette.DarkSurface)
val DarkSurfaceContainerLowest = Color(TavunoPalette.DarkSurfaceContainerLowest)
val DarkSurfaceContainerLow = Color(TavunoPalette.DarkSurfaceContainerLow)
val DarkSurfaceContainer = Color(TavunoPalette.DarkSurfaceContainer)
val DarkSurfaceContainerHigh = Color(TavunoPalette.DarkSurfaceContainerHigh)
val DarkSurfaceContainerHighest = Color(TavunoPalette.DarkSurfaceContainerHighest)
val DarkOnSurface = Color(TavunoPalette.DarkOnSurface)
val DarkOnSurfaceVariant = Color(TavunoPalette.DarkOnSurfaceVariant)
val DarkOutline = Color(TavunoPalette.DarkOutline)
val DarkOutlineVariant = Color(TavunoPalette.DarkOutlineVariant)
val DarkSecondary = Color(TavunoPalette.DarkSecondary)
val DarkOnSecondary = Color(TavunoPalette.DarkOnSecondary)
val DarkSecondaryContainer = Color(TavunoPalette.DarkSecondaryContainer)
val DarkOnSecondaryContainer = Color(TavunoPalette.DarkOnSecondaryContainer)
val DarkTertiary = Color(TavunoPalette.DarkTertiary)
val DarkOnTertiary = Color(TavunoPalette.DarkOnTertiary)
val DarkTertiaryContainer = Color(TavunoPalette.DarkTertiaryContainer)
val DarkOnTertiaryContainer = Color(TavunoPalette.DarkOnTertiaryContainer)
val DarkError = Color(TavunoPalette.DarkError)

// ---------------- LIGHT (Compose wrappers) ----------------
val LightBackground = Color(TavunoPalette.LightBackground)
val LightSurface = Color(TavunoPalette.LightSurface)
val LightSurfaceContainerLowest = Color(TavunoPalette.LightSurfaceContainerLowest)
val LightSurfaceContainerLow = Color(TavunoPalette.LightSurfaceContainerLow)
val LightSurfaceContainer = Color(TavunoPalette.LightSurfaceContainer)
val LightSurfaceContainerHigh = Color(TavunoPalette.LightSurfaceContainerHigh)
val LightSurfaceContainerHighest = Color(TavunoPalette.LightSurfaceContainerHighest)
val LightOnSurface = Color(TavunoPalette.LightOnSurface)
val LightOnSurfaceVariant = Color(TavunoPalette.LightOnSurfaceVariant)
val LightOutline = Color(TavunoPalette.LightOutline)
val LightOutlineVariant = Color(TavunoPalette.LightOutlineVariant)
val LightSecondary = Color(TavunoPalette.LightSecondary)
val LightOnSecondary = Color(TavunoPalette.LightOnSecondary)
val LightSecondaryContainer = Color(TavunoPalette.LightSecondaryContainer)
val LightOnSecondaryContainer = Color(TavunoPalette.LightOnSecondaryContainer)
val LightTertiary = Color(TavunoPalette.LightTertiary)
val LightOnTertiary = Color(TavunoPalette.LightOnTertiary)
val LightTertiaryContainer = Color(TavunoPalette.LightTertiaryContainer)
val LightOnTertiaryContainer = Color(TavunoPalette.LightOnTertiaryContainer)
val LightError = Color(TavunoPalette.LightError)

// ---------------- Legacy aliases ----------------
// Kept so screens written against the pre-restyle flat palette keep compiling while they are
// migrated onto TavunoTheme.colors / TavunoColors roles. New code must NOT use these; they are
// fixed to the dark theme and therefore wrong under the light theme.

/** The brand accent on the default (dark) theme — teal, matching the OwnTV-Baseline default. */
val TavunoAccent = Color(TavunoPalette.AccentCyan)

/** Legacy "panel" grey → the dark elevated container role. */
val TavunoSecondary = DarkSurfaceContainerHigh

/** Legacy near-black → the deepest dark container role. */
val TavunoDarker = DarkSurfaceContainerLowest

/** Legacy app background → the dark background role. */
val TavunoDark = DarkBackground

/** Legacy primary text → the dark on-surface role. */
val TavunoText = DarkOnSurface

/** Legacy secondary text → the dark on-surface-variant role. */
val TavunoTextSecondary = DarkOnSurfaceVariant