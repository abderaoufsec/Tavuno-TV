package com.tavuno.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.tv.material3.ColorScheme
import androidx.tv.material3.MaterialTheme

/**
 * Backwards-compatible theme entry point. The restyle replaced the old flat dark scheme with the
 * OwnTV-Baseline [TavunoTheme], so this now simply delegates to it with Tavuno's shipped defaults
 * (dark theme, teal accent). Prefer calling [TavunoTheme] directly when a screen needs to override
 * the accent or theme mode.
 */
@Composable
fun TavunoTVTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    accent: TavunoAccentColor = TavunoAccentColor.TEAL,
    content: @Composable () -> Unit,
) {
    TavunoTheme(
        themeMode = themeMode,
        accent = accent,
        content = content,
    )
}

/** Retained for callers that read the active tv-material3 scheme directly. */
val TavunoColorScheme: ColorScheme
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme