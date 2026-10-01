package com.tavuno.tv.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.tavuno.tv.ui.components.RoundedPanel
import com.tavuno.tv.ui.theme.Dimens
import com.tavuno.tv.ui.theme.TavunoTheme

/**
 * Shell layer 4 — the preview / detail pane, ported from the OwnTV-Baseline design system.
 *
 * Sits to the right of a list and describes whatever item currently holds focus, so the user can
 * read a synopsis without leaving the row. It owns the per-region preview tint
 * ([TavunoColors.previewPanelFill]) which keeps it distinct from the content panel behind it.
 */
@Composable
fun PreviewPane(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    body: String? = null,
    imageUrl: String? = null,
    actions: @Composable (() -> Unit)? = null,
) {
    val colors = TavunoTheme.colors
    RoundedPanel(
        modifier = modifier.widthIn(min = Dimens.PreviewPaneMinWidth),
        radius = Dimens.CornerLarge,
        fillColor = colors.previewPanelFill,
        innerPadding = androidx.compose.foundation.layout.PaddingValues(Dimens.GapLarge),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Dimens.GapMedium),
        ) {
            if (imageUrl != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(Dimens.CornerMedium))
                        .background(colors.surfaceContainerHighest),
                ) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (body != null) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Start,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (actions != null) {
                Spacer(Modifier.height(Dimens.GapTiny))
                actions()
            }
        }
    }
}

/** Accent-coloured helper text for a preview pane's placeholder state. */
@Composable
fun PreviewPaneHint(text: String, modifier: Modifier = Modifier) {
    RoundedPanel(
        modifier = modifier.widthIn(min = Dimens.PreviewPaneMinWidth),
        radius = Dimens.CornerLarge,
        fillColor = TavunoTheme.colors.previewPanelFill,
        innerPadding = androidx.compose.foundation.layout.PaddingValues(Dimens.GapLarge),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = TavunoTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}