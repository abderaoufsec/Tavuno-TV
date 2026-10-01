package com.tavuno.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.tavuno.tv.ui.theme.Dimens
import com.tavuno.tv.ui.theme.TavunoTheme

/**
 * A square channel tile for the Live TV list, ported from the OwnTV-Baseline design system.
 *
 * The logo is drawn on a light plate (channel logos are almost always dark-on-transparent artwork,
 * so they need a bright backing to read on a dark TV surface). When [logoUrl] is missing the
 * channel's initials are shown instead.
 */
@Composable
fun ChannelLogoTile(
    channelName: String,
    logoUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    channelNumber: Int? = null,
    selected: Boolean = false,
) {
    val colors = TavunoTheme.colors
    FocusableSurface(
        onClick = onClick,
        modifier = modifier,
        selected = selected,
        shape = RoundedCornerShape(Dimens.CornerMedium),
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
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(Dimens.PosterArtCorner))
                    .background(colors.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                if (logoUrl.isNullOrBlank()) {
                    Text(
                        text = initialsOf(channelName),
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    AsyncImage(
                        model = logoUrl,
                        contentDescription = channelName,
                        // Logos are usually transparent PNGs: FIT keeps the whole mark visible
                        // instead of cropping it.
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(Dimens.GapSmall),
                    )
                }
                if (channelNumber != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(Dimens.GapTiny)
                            .clip(RoundedCornerShape(Dimens.CornerSmall))
                            .background(colors.surfaceContainerLowest.copy(alpha = 0.82f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = channelNumber.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textSecondary,
                        )
                    }
                }
            }
            Text(
                text = channelName,
                style = MaterialTheme.typography.titleSmall,
                color = if (focused) colors.primary else colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start,
                modifier = Modifier.padding(top = Dimens.GapSmall, start = 2.dp, end = 2.dp),
            )
        }
    }
}

/** First letters of up to two words, for the no-logo placeholder. */
private fun initialsOf(name: String): String =
    name.trim()
        .split(' ')
        .filter { it.isNotEmpty() }
        .take(2)
        .map { it.first().uppercaseChar() }
        .joinToString("")
        .ifEmpty { "?" }