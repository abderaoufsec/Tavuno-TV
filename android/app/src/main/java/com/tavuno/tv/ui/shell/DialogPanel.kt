package com.tavuno.tv.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tavuno.tv.ui.theme.Dimens
import com.tavuno.tv.ui.theme.TavunoTheme

/**
 * Shared panel chrome for centered popup dialogs, ported from the OwnTV-Baseline design system:
 * fixed width, rounded clip, surface fill — and by default a [verticalScroll], so a dialog taller
 * than the screen (low-resolution TVs, large text) scrolls instead of clipping its lower controls
 * out of reach. D-pad focus automatically brings off-screen children into view.
 *
 * Pass [scroll] = false when the dialog's column already contains a LazyColumn or `weight()`
 * children — nesting two same-direction scrollers is illegal in Compose, so the dialog must manage
 * its own scrolling (typically by capping the inner LazyColumn's height).
 */
@Composable
fun Modifier.tavunoDialogPanel(
    width: Dp = 440.dp,
    corner: Dp = Dimens.CardCorner,
    padding: Dp = 24.dp,
    fill: Color? = null,
    scroll: Boolean = true,
    panelHeight: Dp? = null,
): Modifier {
    val shape = RoundedCornerShape(corner)
    val outline = TavunoTheme.colors.outlineVariant.copy(alpha = 0.72f)
    val base = this
        .width(width)
        .then(panelHeight?.let { Modifier.height(it) } ?: Modifier)
        .shadow(elevation = 24.dp, shape = shape, clip = false)
        .clip(shape)
        .background(fill ?: TavunoTheme.colors.surfaceContainerHigh)
        .border(1.dp, outline, shape)
    // verticalScroll + a nested LazyColumn is an illegal same-direction nest.
    return if (scroll) {
        base.verticalScroll(rememberScrollState()).padding(padding)
    } else {
        base.padding(padding)
    }
}

/**
 * Shared modal wash behind a dialog. Solid mode stays strong enough to separate the panel from
 * the content underneath without becoming an opaque black slab.
 */
@Composable
fun Modifier.tavunoModalScrim(strength: Float = 1f): Modifier {
    val alpha = (0.58f * strength.coerceIn(0f, 1.35f)).coerceAtMost(0.72f)
    return background(Color.Black.copy(alpha = alpha))
}