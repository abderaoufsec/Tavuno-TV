package com.tavuno.tv.ui.shell

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.tavuno.tv.ui.components.FocusableSurface
import com.tavuno.tv.ui.components.RoundedPanel
import com.tavuno.tv.ui.theme.Dimens
import com.tavuno.tv.ui.theme.TavunoTheme

/**
 * Shell layer 1 — the MD3 navigation panel, ported from the OwnTV-Baseline design system.
 *
 * It expands to a full labelled drawer ([Dimens.SidebarWidthExpanded]) whenever focus is anywhere
 * inside it, and collapses to an icon rail ([Dimens.SidebarWidthCollapsed]) the moment focus moves
 * into the content, so browsing wastes no horizontal space. Focusing a destination expands it back.
 *
 * The selected tab may optionally take initial D-pad focus ([requestInitialFocus]). The app leaves
 * that off by default: TV apps conventionally start focus on the first content item, with LEFT
 * reaching the rail (which then expands), so the user can start watching without an extra step.
 */
@Composable
fun TavunoSidebar(
    tabs: List<TavunoTab>,
    current: TavunoTab,
    onSelect: (TavunoTab) -> Unit,
    modifier: Modifier = Modifier,
    requestInitialFocus: Boolean = true,
) {
    val colors = TavunoTheme.colors
    // `hasFocus` is true for this node OR any descendant, so the rail tracks "focus is inside me".
    var railFocused by remember { mutableStateOf(false) }
    val width by animateDpAsState(
        targetValue = if (railFocused) Dimens.SidebarWidthExpanded else Dimens.SidebarWidthCollapsed,
        animationSpec = tween(180),
        label = "sidebarWidth",
    )
    val initialFocus = remember { FocusRequester() }
    LaunchedEffect(requestInitialFocus) {
        if (requestInitialFocus) {
            // A FocusRequester can only resolve once its node has been *placed*, and this effect can
            // run before the first layout pass of the frame that created it. Waiting one frame makes
            // the request deterministic instead of silently depending on dispatch ordering.
            withFrameNanos { }
            runCatching { initialFocus.requestFocus() }
        }
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(width)
            .onFocusChanged { railFocused = it.hasFocus }
            .padding(Dimens.GapSmall),
    ) {
        RoundedPanel(
            modifier = Modifier.fillMaxSize(),
            radius = Dimens.CornerLarge,
            fillColor = colors.railPanelFill,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Dimens.GapSmall),
                verticalArrangement = Arrangement.spacedBy(Dimens.GapTiny),
            ) {
                BrandMark(expanded = railFocused)
                Spacer(Modifier.height(Dimens.GapSmall))
                tabs.forEach { tab ->
                    SidebarItem(
                        tab = tab,
                        selected = tab == current,
                        expanded = railFocused,
                        onClick = { onSelect(tab) },
                        modifier = if (tab == current) {
                            Modifier.focusRequester(initialFocus)
                        } else {
                            Modifier
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun BrandMark(expanded: Boolean) {
    val colors = TavunoTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = if (expanded) Dimens.GapMedium else 0.dp, vertical = Dimens.GapSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (expanded) Arrangement.spacedBy(Dimens.GapSmall) else Arrangement.Center,
    ) {
        Text(
            text = "T",
            style = MaterialTheme.typography.headlineSmall,
            color = colors.primary,
        )
        if (expanded) {
            Text(
                text = "TAVUNO",
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SidebarItem(
    tab: TavunoTab,
    selected: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TavunoTheme.colors
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        selected = selected,
        shape = RoundedCornerShape(Dimens.CornerMedium),
        // A nav rail row must not scale while the rail animates its width — that reads as a wobble.
        focusedScale = 1.0f,
        unfocusedContainerColor = Color.Transparent,
        focusedContainerColor = colors.card,
        selectedContainerColor = colors.primaryContainer.copy(alpha = 0.18f),
        contentAlignment = Alignment.Center,
    ) { focused ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (expanded) Dimens.GapMedium else 0.dp,
                    vertical = 12.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (expanded) {
                Arrangement.spacedBy(Dimens.GapMedium)
            } else {
                Arrangement.Center
            },
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = tab.label,
                tint = when {
                    focused || selected -> colors.primary
                    else -> colors.textSecondary
                },
                modifier = Modifier.size(24.dp),
            )
            if (expanded) {
                Text(
                    text = tab.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (focused || selected) colors.textPrimary else colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}