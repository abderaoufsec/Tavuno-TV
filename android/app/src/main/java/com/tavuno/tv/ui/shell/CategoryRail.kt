package com.tavuno.tv.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.tavuno.tv.ui.components.FocusableSurface
import com.tavuno.tv.ui.components.RoundedPanel
import com.tavuno.tv.ui.theme.Dimens
import com.tavuno.tv.ui.theme.TavunoTheme

/**
 * Shell layer 2 — the category rail, ported from the OwnTV-Baseline design system.
 *
 * A fixed-width column of filter pills. Names wrap to two lines and ellipsize rather than forcing
 * the rail to resize as focus moves — a fixed width keeps the content pane from re-laying-out on
 * every D-pad step. [selected] = null means the [allLabel] entry is active.
 */
@Composable
fun CategoryRail(
    categories: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    header: String? = null,
    allLabel: String = "All",
) {
    val colors = TavunoTheme.colors
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(Dimens.RailWidthFixed),
    ) {
        RoundedPanel(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
            radius = Dimens.CornerLarge,
            fillColor = colors.railPanelFill,
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                contentPadding = PaddingValues(Dimens.GapSmall),
                verticalArrangement = Arrangement.spacedBy(Dimens.GapTiny),
            ) {
                if (header != null) {
                    item(key = "__header") {
                        Text(
                            text = header,
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(
                                horizontal = Dimens.GapSmall,
                                vertical = Dimens.GapTiny,
                            ),
                        )
                    }
                }
                item(key = "__all") {
                    CategoryPill(
                        label = allLabel,
                        selected = selected == null,
                        onClick = { onSelect(null) },
                    )
                }
                items(items = categories, key = { it }) { category ->
                    CategoryPill(
                        label = category,
                        selected = category == selected,
                        onClick = { onSelect(category) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = TavunoTheme.colors
    FocusableSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        selected = selected,
        shape = RoundedCornerShape(Dimens.CornerMedium),
        focusedScale = 1.0f,
        unfocusedContainerColor = Color.Transparent,
        focusedContainerColor = colors.card,
        selectedContainerColor = colors.primaryContainer.copy(alpha = 0.18f),
        contentAlignment = Alignment.CenterStart,
    ) { focused ->
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = if (focused || selected) colors.primary else colors.textSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.GapSmall, vertical = 10.dp),
        )
    }
}