package com.tavuno.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.tavuno.tv.ui.theme.Dimens
import com.tavuno.tv.ui.theme.TavunoTheme

/** Standard 2:3 movie-poster aspect ratio used across the VOD grids. */
private const val PosterAspect = 2f / 3f

/**
 * The VOD grid tile, ported from the OwnTV-Baseline design system.
 *
 * Built on [FocusableSurface] so it inherits the shared ring/glow/scale focus behaviour. The art
 * is loaded with Coil; when [posterUrl] is null or fails, a tonal placeholder with the title is
 * drawn instead, so the grid never collapses on missing artwork.
 *
 * @param progress 0..1 resume position; draws a thin accent bar across the bottom of the art.
 */
@Composable
fun PosterCard(
    title: String,
    posterUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    progress: Float? = null,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = TavunoTheme.colors
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.width(180.dp),
        selected = selected,
        onLongClick = onLongClick,
        shape = RoundedCornerShape(Dimens.PosterCardCorner),
        focusedScale = 1.03f,
        glowElevation = 8,
        focusedContainerColor = colors.card,
        unfocusedContainerColor = colors.card,
        selectedContainerColor = colors.card,
        contentAlignment = Alignment.TopStart,
    ) { focused ->
        Column(modifier = Modifier.padding(Dimens.PosterPadding)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(PosterAspect)
                    .clip(RoundedCornerShape(Dimens.PosterArtCorner))
                    .background(colors.surfaceContainerHighest),
            ) {
                PosterArt(title = title, posterUrl = posterUrl)
                if (progress != null && progress > 0f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(Dimens.PosterProgressHeight)
                            .background(colors.outlineVariant.copy(alpha = 0.5f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress.coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .background(colors.primary),
                        )
                    }
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (focused) colors.primary else colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Dimens.GapSmall, start = 2.dp, end = 2.dp),
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 2.dp, end = 2.dp),
                )
            }
        }
    }
}

/** Poster artwork with Coil, falling back to a tonal plate carrying the title. */
@Composable
private fun PosterArt(title: String, posterUrl: String?) {
    val colors = TavunoTheme.colors
    if (posterUrl.isNullOrBlank()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(colors.surfaceContainerHigh, colors.surfaceContainerLowest),
                    ),
                )
                .padding(Dimens.GapSmall),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
        return
    }
    AsyncImage(
        model = posterUrl,
        contentDescription = title,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
    )
}