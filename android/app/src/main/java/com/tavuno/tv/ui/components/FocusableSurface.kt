package com.tavuno.tv.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp
import com.tavuno.tv.ui.theme.Dimens
import com.tavuno.tv.ui.theme.LocalFocusBorderWidth
import com.tavuno.tv.ui.theme.TavunoTheme

/**
 * The single focus abstraction for the whole app, ported from the OwnTV-Baseline design system.
 *
 * Every remote-navigable surface (cards, list rows, nav pills, buttons, tiles) renders through
 * this, so the focus ring width, the glow, the tonal lift and the scale "pop" stay identical
 * everywhere and follow the user's Settings → Focus ring choice.
 *
 * The focus signal is a three-part ladder on the solid material (OwnTV's glass layer is not
 * ported):
 *  1. a tonal container lift — `primaryContainer` composited over the surface the control normally
 *     sits on, so transparent list rows lift to the same card tone as filled ones;
 *  2. a ring in [TavunoColors.focusBorder], drawn at [LocalFocusBorderWidth];
 *  3. a soft depth shadow + scale, reserved for large cards (rows stay flat while scrolling).
 *
 * [focusedContainerColor] is only used for explicit-fill callers (e.g. a brand-anchored PRIMARY
 * button); everything else gets the tonal ladder automatically.
 */
@Composable
fun FocusableSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(Dimens.CardCorner),
    focusedContainerColor: Color = TavunoTheme.colors.card,
    unfocusedContainerColor: Color = Color.Transparent,
    selectedContainerColor: Color = TavunoTheme.colors.card,
    focusedScale: Float = 1.012f,
    glowElevation: Int = 6,
    // When false, this surface never draws the built-in focus/selected outline, so the caller can
    // manage its own border (e.g. a cursor that outlines only focused-unselected items).
    showFocusBorder: Boolean = true,
    // Keep selected semantics/click behaviour but let custom content own the complete selected
    // appearance. Prevents a second material plate behind controls that paint their own.
    renderSelectionContainer: Boolean = true,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    val colors = TavunoTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val visuallySelected = selected && renderSelectionContainer

    // Row-sized controls use only the restrained focus lift; larger poster cards keep their
    // deliberate depth motion.
    val compactFocusableRow = focusedScale <= 1.012f
    // Primary action pills deliberately remain solid brand anchors; every other control shares the
    // same M3 tonal focus ladder instead of inheriting unrelated fills.
    val solidBrandAnchor = unfocusedContainerColor == colors.primary &&
        focusedContainerColor == colors.primary
    val useSolidTonalLadder = showFocusBorder && !solidBrandAnchor
    val solidTonalBase = if (unfocusedContainerColor.alpha > 0f) {
        unfocusedContainerColor
    } else {
        // Transparent list rows still need the tonal focus to sit on the same elevated card tone.
        colors.surfaceContainerHigh
    }
    val solidFocusedContainer = colors.primaryContainer.copy(alpha = 0.22f)
        .compositeOver(solidTonalBase)
    val solidSelectedContainer = colors.primaryContainer.copy(alpha = 0.14f)
        .compositeOver(solidTonalBase)

    val container by animateColorAsState(
        targetValue = when {
            focused && useSolidTonalLadder -> solidFocusedContainer
            focused -> focusedContainerColor
            visuallySelected && useSolidTonalLadder -> solidSelectedContainer
            visuallySelected -> selectedContainerColor
            else -> unfocusedContainerColor
        },
        animationSpec = tween(if (compactFocusableRow) 0 else 160),
        label = "focusContainer",
    )
    val scale by animateFloatAsState(
        targetValue = if (focused) focusedScale else 1f,
        animationSpec = tween(if (compactFocusableRow) 0 else 160),
        label = "focusScale",
    )

    val showBorder = showFocusBorder && (focused || visuallySelected)
    // The user's chosen ring width. A wider ring opens the glow with it, so "extra thick" reads as
    // a halo from sofa distance instead of just a fatter line.
    val focusBorderWidth = LocalFocusBorderWidth.current
    val glowScale = focusBorderWidth.value / Dimens.FocusBorderWidth.value
    val borderColor = if (focused) colors.focusBorder else colors.focusBorder.copy(alpha = 0.28f)

    Box(
        modifier = modifier
            .scale(scale)
            .then(
                // The depth shadow is reserved for larger cards: a separately elevated row layer
                // can trail the parent's bringIntoView scroll for a frame, which reads as a moving
                // bar in light mode.
                if (focused && !compactFocusableRow) Modifier.shadow(
                    elevation = (glowElevation * glowScale).dp,
                    shape = shape,
                    clip = false,
                    ambientColor = colors.focusGlow,
                    spotColor = colors.focusGlow,
                ) else Modifier,
            )
            .clip(shape)
            .background(container)
            .then(
                when {
                    showBorder && focused ->
                        Modifier.border(focusBorderWidth, borderColor, shape)
                    // The idle "selected" hairline stays a muted 1.dp stroke: it marks position
                    // without competing with the live remote cursor.
                    showBorder && visuallySelected ->
                        Modifier.border(1.dp, colors.focusBorder.copy(alpha = 0.28f), shape)
                    else -> Modifier
                },
            )
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        interactionSource = interaction,
                        indication = null,
                        enabled = enabled,
                        onLongClick = onLongClick,
                        onClick = onClick,
                    )
                } else {
                    Modifier.selectable(
                        selected = selected,
                        enabled = enabled,
                        interactionSource = interaction,
                        indication = null,
                        onClick = onClick,
                    )
                },
            ),
        contentAlignment = contentAlignment,
    ) {
        content(focused)
    }
}