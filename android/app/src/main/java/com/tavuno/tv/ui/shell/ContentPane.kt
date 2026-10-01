package com.tavuno.tv.ui.shell

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.tavuno.tv.ui.components.RoundedPanel
import com.tavuno.tv.ui.theme.Dimens
import com.tavuno.tv.ui.theme.TavunoTheme

/**
 * Shell layer 3 — the rounded content panel, ported from the OwnTV-Baseline design system.
 *
 * It owns the per-region content tint ([TavunoColors.contentPanelFill]) so the content area reads
 * as a distinct surface from the navigation rail and any preview pane, rather than collapsing into
 * the generic M3 elevation ladder.
 *
 * Content is inset by [Dimens.ScreenPaddingH]/[Dimens.ScreenPaddingV] by default; pass
 * [innerPadding] = PaddingValues(0.dp) when the child needs to bleed to the panel edge (e.g. a
 * full-bleed hero).
 */
@Composable
fun ContentPane(
    modifier: Modifier = Modifier,
    fillColor: Color? = null,
    innerPadding: PaddingValues = PaddingValues(
        horizontal = Dimens.ScreenPaddingH,
        vertical = Dimens.ScreenPaddingV,
    ),
    content: @Composable () -> Unit,
) {
    val colors = TavunoTheme.colors
    RoundedPanel(
        modifier = modifier,
        radius = Dimens.CornerLarge,
        fillColor = fillColor ?: colors.contentPanelFill,
        innerPadding = innerPadding,
    ) {
        content()
    }
}