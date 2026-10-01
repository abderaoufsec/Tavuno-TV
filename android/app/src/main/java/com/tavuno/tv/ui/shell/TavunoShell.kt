package com.tavuno.tv.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tavuno.tv.ui.theme.Dimens
import com.tavuno.tv.ui.theme.TavunoTheme

/**
 * The Tavuno TV app shell — the four-layer frame from the OwnTV-Baseline design system:
 *
 *  1. [TavunoSidebar] — the MD3 navigation panel (expands/collapses with focus).
 *  2. [CategoryRail] / [PreviewPane] — optional per-screen rails drawn by the caller inside
 *     [content], since which of them applies is a property of the screen, not the shell.
 *  3. [ContentPane] — the rounded content panel that owns [TavunoColors.contentPanelFill].
 *  4. [TavunoTopBar] — the title strip above it.
 *
 * Only the shell's six top-level destinations render inside this; detail and player routes are
 * pushed full-screen so the video and detail art get the whole display.
 *
 * @param requestSidebarFocus request initial D-pad focus for the selected rail item. Pass true only
 *   for the first composition of a session — a re-request on every recomposition would yank focus
 *   out of the content the user is browsing.
 */
@Composable
fun TavunoShell(
    current: TavunoTab,
    onSelectTab: (TavunoTab) -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    topBarTrailing: @Composable (() -> Unit)? = null,
    requestSidebarFocus: Boolean = false,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .background(TavunoTheme.colors.background),
    ) {
        TavunoSidebar(
            tabs = TavunoTab.entries,
            current = current,
            onSelect = onSelectTab,
            requestInitialFocus = requestSidebarFocus,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .padding(
                    start = Dimens.GapSmall,
                    end = Dimens.GapSmall,
                    bottom = Dimens.GapSmall,
                ),
        ) {
            TavunoTopBar(
                title = title,
                subtitle = subtitle,
                onBack = onBack,
                trailing = topBarTrailing,
            )
            ContentPane(modifier = Modifier.fillMaxWidth().weight(1f)) {
                content()
            }
        }
    }
}