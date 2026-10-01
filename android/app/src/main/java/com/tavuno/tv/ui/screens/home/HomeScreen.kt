package com.tavuno.tv.ui.screens.home

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.tavuno.tv.ui.components.FocusableSurface
import com.tavuno.tv.ui.theme.Dimens
import com.tavuno.tv.ui.theme.TavunoTheme

/**
 * The Home destination, rebuilt on the shell + design system.
 *
 * It used to be a fixed, centred `Column` that overflowed a 1080p viewport and pushed the Settings
 * button out of D-pad reach. It is now a scrollable [LazyColumn] of rails, so every tile is
 * reachable at any resolution — and Settings is additionally always reachable from the sidebar.
 */
@Composable
fun HomeScreen(
    onNavigateToLive: () -> Unit,
    onNavigateToSports: () -> Unit,
    onNavigateToMovies: () -> Unit,
    onNavigateToSeries: () -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    val colors = TavunoTheme.colors
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = Dimens.GapSmall),
        verticalArrangement = Arrangement.spacedBy(Dimens.GapLarge),
    ) {
        item(key = "welcome") {
            Column {
                Text(
                    text = "Welcome to Tavuno",
                    style = MaterialTheme.typography.headlineLarge,
                    color = colors.textPrimary,
                )
                Spacer(Modifier.height(Dimens.GapTiny))
                Text(
                    text = "Pick a section to start watching.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textSecondary,
                )
            }
        }

        item(key = "browse") {
            HomeRail(title = "Browse") {
                HomeTile("Live TV", "Watch live channels", Icons.Filled.LiveTv, onNavigateToLive)
                HomeTile("Sports", "Live sports events", Icons.Filled.SportsSoccer, onNavigateToSports)
                HomeTile("Movies", "Watch movies", Icons.Filled.Movie, onNavigateToMovies)
                HomeTile("Series", "Watch series", Icons.Filled.Tv, onNavigateToSeries)
            }
        }

        item(key = "account") {
            HomeRail(title = "Account") {
                HomeTile(
                    title = "Settings",
                    subtitle = "Theme, focus ring and playback",
                    icon = Icons.Filled.Settings,
                    onClick = onNavigateToSettings,
                )
            }
        }
    }
}

/** A titled, horizontally scrollable rail of tiles. */
@Composable
private fun HomeRail(
    title: String,
    content: @Composable () -> Unit,
) {
    val colors = TavunoTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.GapSmall)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = colors.textPrimary,
            modifier = Modifier.padding(horizontal = Dimens.HomeRowPaddingH),
        )
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Dimens.HomeRowPaddingH),
            horizontalArrangement = Arrangement.spacedBy(Dimens.GapMedium),
        ) {
            content()
        }
    }
}

@Composable
private fun HomeTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val colors = TavunoTheme.colors
    FocusableSurface(
        onClick = onClick,
        modifier = Modifier
            .width(260.dp)
            .height(150.dp),
        shape = RoundedCornerShape(Dimens.CornerLarge),
        focusedScale = 1.03f,
        glowElevation = 8,
        unfocusedContainerColor = colors.card,
        focusedContainerColor = colors.card,
        contentAlignment = Alignment.CenterStart,
    ) { focused ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Dimens.GapLarge),
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (focused) colors.primary else colors.textSecondary,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.height(Dimens.GapSmall))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}