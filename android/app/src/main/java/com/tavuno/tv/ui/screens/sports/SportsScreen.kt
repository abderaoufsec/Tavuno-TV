package com.tavuno.tv.ui.screens.sports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.tavuno.tv.data.model.Match
import com.tavuno.tv.data.repository.SportsRepository
import com.tavuno.tv.ui.components.EmptyState
import com.tavuno.tv.ui.components.ErrorState
import com.tavuno.tv.ui.components.FocusableSurface
import com.tavuno.tv.ui.components.LoadingState
import com.tavuno.tv.ui.components.TavunoButton
import com.tavuno.tv.ui.components.TavunoButtonStyle
import com.tavuno.tv.ui.screens.live.EpgTimeFormat
import com.tavuno.tv.ui.theme.Dimens
import com.tavuno.tv.ui.theme.TavunoTheme

/**
 * The Sports destination, rebuilt on the design system.
 *
 * The shell owns the chrome (TopBar title, sidebar, system BACK via
 * [com.tavuno.tv.ui.navigation.TavunoMainScreen]), so the body starts at the status selector —
 * no Back affordance, mirroring [com.tavuno.tv.ui.screens.movies.MoviesScreen].
 *
 * Match rows render through [FocusableSurface]; rows stay focusable without a channel so the
 * D-pad never lands on nothing, but their trailing slot shows TBA instead of the play glyph.
 */
@Composable
fun SportsScreen(
    sportsRepository: SportsRepository,
    onNavigateToPlayer: (Int) -> Unit,
) {
    var isLoading by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableStateOf("live") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var matches by remember { mutableStateOf<List<Match>>(emptyList()) }
    // Bumped by Retry to re-run the load effect.
    var reloadKey by remember { mutableStateOf(0) }

    // Load matches for the selected tab, and reload whenever tab or retry changes.
    // Driven from LaunchedEffect (main dispatcher): reading/writing Compose state from a
    // bare Dispatchers.IO scope crashes with "Reading a state that was created after the
    // snapshot was taken".
    LaunchedEffect(reloadKey, selectedTab) {
        isLoading = true
        errorMessage = null
        val status = if (selectedTab == "live") "live" else "upcoming"
        sportsRepository.getMatches(status = status, limit = 50).fold(
            onSuccess = { matchList ->
                matches = matchList
                isLoading = false
            },
            onFailure = { error ->
                errorMessage = error.message ?: "Failed to load sports events"
                isLoading = false
            },
        )
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Dimens.GapMedium),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.GapSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TavunoButton(
                label = "LIVE NOW",
                onClick = { selectedTab = "live" },
                selected = selectedTab == "live",
                style = TavunoButtonStyle.SECONDARY,
                compact = true,
            )
            TavunoButton(
                label = "UPCOMING",
                onClick = { selectedTab = "upcoming" },
                selected = selectedTab == "upcoming",
                style = TavunoButtonStyle.SECONDARY,
                compact = true,
            )
        }

        when {
            isLoading -> LoadingState("Loading sports events...")

            errorMessage != null -> ErrorState(
                message = errorMessage!!,
                onRetry = { reloadKey++ },
            )

            matches.isEmpty() -> EmptyState("No sports events available")

            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Dimens.GapSmall),
            ) {
                items(matches, key = { it.id }) { match ->
                    MatchRow(
                        match = match,
                        onClick = { match.channelId?.let(onNavigateToPlayer) },
                    )
                }
            }
        }
    }
}

/**
 * A match row: id, status + kickoff time, score when known, and a trailing slot that shows the
 * play glyph when a channel is mapped (TBA otherwise — the row has nothing to play yet).
 */
@Composable
private fun MatchRow(
    match: Match,
    onClick: () -> Unit,
) {
    val colors = TavunoTheme.colors
    val hasChannel = match.channelId != null
    val score = if (match.homeScore != null || match.awayScore != null) {
        "${match.homeScore ?: "-"}  :  ${match.awayScore ?: "-"}"
    } else {
        null
    }
    val kickoff = EpgTimeFormat.clockLabel(match.kickoff)
    val meta = listOf(match.status, kickoff).filterNotNull().joinToString("  •  ")

    FocusableSurface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp),
        unfocusedContainerColor = colors.card,
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Dimens.GapMedium, vertical = Dimens.GapSmall),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Match ${match.id}",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (score != null) {
                Text(
                    text = score,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    modifier = Modifier.padding(horizontal = Dimens.GapMedium),
                )
            }
            if (hasChannel) {
                Text(
                    text = "▶",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.primary.copy(alpha = 0.6f),
                )
            } else {
                Text(
                    text = "TBA",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary,
                )
            }
        }
    }
}