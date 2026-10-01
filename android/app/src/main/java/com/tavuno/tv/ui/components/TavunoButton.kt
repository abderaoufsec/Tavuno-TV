package com.tavuno.tv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.tavuno.tv.ui.theme.TavunoTheme

/** Visual emphasis for [TavunoButton]. */
enum class TavunoButtonStyle { PRIMARY, SECONDARY }

/**
 * Remote-friendly TV button built on [FocusableSurface], ported from the OwnTV-Baseline design
 * system. PRIMARY fills with the brand accent; SECONDARY is a tonal surface that lifts to the
 * primary container on focus.
 *
 * [leading] renders an optional icon (or any small composable) before the label, tinted with the
 * resolved content colour.
 */
@Composable
fun TavunoButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: TavunoButtonStyle = TavunoButtonStyle.PRIMARY,
    enabled: Boolean = true,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    // Denser pill (tighter padding + smaller icon) for space-constrained popups.
    compact: Boolean = false,
    leading: (@Composable (color: androidx.compose.ui.graphics.Color) -> Unit)? = null,
) {
    val colors = TavunoTheme.colors
    val shape = RoundedCornerShape(50) // M3 full/pill button
    val primary = style == TavunoButtonStyle.PRIMARY

    FocusableSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        enabled = enabled,
        selected = selected,
        shape = shape,
        focusedScale = 1.012f,
        unfocusedContainerColor = if (primary) colors.primary else colors.card,
        focusedContainerColor = if (primary) colors.primary else colors.primaryContainer,
        selectedContainerColor = if (primary || selected) colors.primary else colors.card,
    ) { focused ->
        val contentColor = when {
            primary || (selected && !focused) -> colors.onPrimary
            focused -> colors.onPrimaryContainer
            else -> colors.textPrimary
        }

        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 13.dp else 22.dp,
                vertical = if (compact) 6.dp else 12.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp),
        ) {
            if (selected) {
                Box(Modifier.size(if (compact) 6.dp else 7.dp).background(contentColor, CircleShape))
            }
            if (leading != null) {
                leading(contentColor)
            }
            // Long labels must ellipsize, not hard-clip. `weight(1f, fill = false)` lets a short
            // label stay content-sized while a long one is constrained to the remaining row width.
            Text(
                text = label,
                style = if (compact) {
                    MaterialTheme.typography.labelMedium
                } else {
                    MaterialTheme.typography.labelLarge
                },
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}