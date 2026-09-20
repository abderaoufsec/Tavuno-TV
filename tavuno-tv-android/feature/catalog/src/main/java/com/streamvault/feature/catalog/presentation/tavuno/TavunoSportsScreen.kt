package com.streamvault.feature.catalog.presentation.tavuno

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.streamvault.core.ui.theme.StreamVaultTheme
import com.streamvault.domain.model.Competition
import com.streamvault.domain.model.Match
import com.streamvault.domain.model.MatchStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TavunoSportsScreen(
    viewModel: TavunoSportsViewModel = hiltViewModel(),
    onCompetitionSelected: (Competition) -> Unit = {},
    onMatchSelected: (Match) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    StreamVaultTheme {
        when (uiState) {
            is SportsUiState.Loading -> {
                SportsLoadingScreen()
            }
            is SportsUiState.Success -> {
                val state = uiState as SportsUiState.Success
                SportsContent(
                    competitions = state.competitions,
                    liveMatches = state.liveMatches,
                    upcomingMatches = state.upcomingMatches,
                    onCompetitionSelected = onCompetitionSelected,
                    onMatchSelected = onMatchSelected
                )
            }
            is SportsUiState.Error -> {
                SportsErrorScreen(
                    message = (uiState as SportsUiState.Error).message,
                    onRetry = { viewModel.loadSports() }
                )
            }
            else -> {
                // Handle other states (CompetitionMatches, MatchDetailsLoaded)
                SportsLoadingScreen()
            }
        }
    }
}

@Composable
private fun SportsLoadingScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Loading Sports...",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SportsErrorScreen(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Error: $message",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(24.dp))
        androidx.tv.material3.Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}

@Composable
private fun SportsContent(
    competitions: List<Competition>,
    liveMatches: List<Match>,
    upcomingMatches: List<Match>,
    onCompetitionSelected: (Competition) -> Unit,
    onMatchSelected: (Match) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(48.dp)
    ) {
        item {
            Text(
                text = "Competitions",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }

        item {
            CompetitionRow(
                competitions = competitions,
                onCompetitionSelected = onCompetitionSelected
            )
        }

        item {
            Spacer(modifier = Modifier.height(48.dp))
            Text(
                text = "Live Matches",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }

        item {
            MatchColumn(
                matches = liveMatches,
                onMatchSelected = onMatchSelected
            )
        }

        item {
            Spacer(modifier = Modifier.height(48.dp))
            Text(
                text = "Upcoming Matches",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }

        item {
            MatchColumn(
                matches = upcomingMatches,
                onMatchSelected = onMatchSelected
            )
        }
    }
}

@Composable
private fun CompetitionRow(
    competitions: List<Competition>,
    onCompetitionSelected: (Competition) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(competitions) { competition ->
            CompetitionCard(
                competition = competition,
                onClick = { onCompetitionSelected(competition) }
            )
        }
    }
}

@Composable
private fun CompetitionCard(
    competition: Competition,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .width(300.dp)
            .height(120.dp),
        colors = CardDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = competition.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = competition.sport,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MatchColumn(
    matches: List<Match>,
    onMatchSelected: (Match) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(matches) { match ->
            MatchCard(
                match = match,
                onClick = { onMatchSelected(match) }
            )
        }
    }
}

@Composable
private fun MatchCard(
    match: Match,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp),
        colors = CardDefaults.colors(
            containerColor = if (match.status == MatchStatus.LIVE) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Match #${match.id}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = formatKickoff(match.kickoff),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (match.status == MatchStatus.LIVE && match.homeScore != null && match.awayScore != null) {
                    Text(
                        text = "${match.homeScore} - ${match.awayScore}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(
                text = match.status.name,
                style = MaterialTheme.typography.labelLarge,
                color = when (match.status) {
                    MatchStatus.LIVE -> MaterialTheme.colorScheme.primary
                    MatchStatus.FINISHED -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

private fun formatKickoff(kickoff: String): String {
    return try {
        val instant = Instant.parse(kickoff)
        val formatter = DateTimeFormatter.ofPattern("MMM dd, HH:mm")
            .withZone(ZoneId.systemDefault())
        formatter.format(instant)
    } catch (e: Exception) {
        kickoff
    }
}
