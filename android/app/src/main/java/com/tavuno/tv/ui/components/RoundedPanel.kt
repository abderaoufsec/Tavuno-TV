package com.tavuno.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tavuno.tv.ui.theme.TavunoTheme

/**
 * A rounded visual container matching the shell mockup's "panel" look, ported from the
 * OwnTV-Baseline design system: large rounded corners, a subtle surface fill, and a hairline
 * top-edge lift. Content is clipped to the shape.
 *
 * This is a VISUAL wrapper only — a plain [Box], no `clickable`/`selectable`/focus of its own.
 *
 * @param fillColor the panel surface colour, or null for the theme default
 *   ([TavunoColors.surfaceContainerLowest]).
 * @param radius corner radius (≈24px on the mockup; 22dp reads well at TV distance).
 * @param innerPadding inset between the rounded edge and the content.
 */
@Composable
fun RoundedPanel(
    modifier: Modifier = Modifier,
    radius: Dp = 22.dp,
    fillColor: Color? = null,
    innerPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable () -> Unit,
) {
    val colors = TavunoTheme.colors
    val shape = RoundedCornerShape(radius)
    val bg = fillColor ?: colors.surfaceContainerLowest
    val outline = colors.outlineVariant.copy(alpha = 0.66f)
    Box(
        modifier = modifier
            .clip(shape)
            .background(bg)
            .solidPanelMaterial(
                edgeColor = colors.outlineVariant,
                accent = colors.primary,
                isDark = colors.isDark,
            )
            .border(width = 1.dp, color = outline, shape = shape)
            .padding(innerPadding),
    ) {
        content()
    }
}

/**
 * The rounded-panel look as a [Modifier], for applying to an EXISTING container.
 * Same spec as [RoundedPanel].
 */
@Composable
fun Modifier.roundedPanel(
    radius: Dp = 22.dp,
    fillColor: Color? = null,
): Modifier {
    val colors = TavunoTheme.colors
    val shape = RoundedCornerShape(radius)
    val bg = fillColor ?: colors.surfaceContainerLowest
    val outline = colors.outlineVariant.copy(alpha = 0.66f)
    return this
        .clip(shape)
        .background(bg)
        .solidPanelMaterial(
            edgeColor = colors.outlineVariant,
            accent = colors.primary,
            isDark = colors.isDark,
        )
        .border(width = 1.dp, color = outline, shape = shape)
}

/**
 * Cached solid-material lighting: one broad accent reflection, a restrained lower depth tone, and
 * the existing top-edge lift. These are plain brush draws inside the panel clip — no blur, shadow
 * layer, animation, or per-frame brush allocation.
 */
private fun Modifier.solidPanelMaterial(
    edgeColor: Color,
    accent: Color,
    isDark: Boolean,
): Modifier = drawWithCache {
    val edgeHeight = 2.dp.toPx()
    val edge = Brush.verticalGradient(
        colors = listOf(edgeColor.copy(alpha = 0.42f), Color.Transparent),
        endY = edgeHeight,
    )
    val ambient = Brush.radialGradient(
        colors = listOf(
            accent.copy(alpha = if (isDark) 0.055f else 0.032f),
            Color.Transparent,
        ),
        center = Offset(
            x = minOf(size.width * 0.16f, 120.dp.toPx()),
            y = -minOf(size.height * 0.08f, 20.dp.toPx()),
        ),
        radius = maxOf(size.minDimension * 1.45f, 260.dp.toPx()),
    )
    val depth = Brush.verticalGradient(
        colors = listOf(
            Color.Transparent,
            Color.Black.copy(alpha = if (isDark) 0.045f else 0.018f),
        ),
        startY = size.height * 0.58f,
        endY = size.height,
    )
    onDrawWithContent {
        drawRect(brush = ambient)
        drawRect(brush = depth)
        drawContent()
        drawRect(brush = edge, size = Size(size.width, edgeHeight))
    }
}