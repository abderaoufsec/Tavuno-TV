package com.tavuno.tv.ui.screens.series

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.tavuno.tv.data.model.EpisodeDetails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.tavuno.tv.ui.theme.TavunoSecondary

@Composable
fun SeasonEpisodesScreen(
    seasonId: Int,
    catalogRepository: com.tavuno.tv.data.repository.CatalogRepository,
    playbackRepository: com.tavuno.tv.data.repository.PlaybackRepository,
    onNavigateBack: () -> Unit,
    onNavigateToPlayer: (String, String) -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var episodes by remember { mutableStateOf<List<EpisodeDetails>>(emptyList())}
    
    // Load episodes from repository
    CoroutineScope(Dispatchers.IO).launch {
        val episodesResult = catalogRepository.getSeasonEpisodes(seasonId)
        episodesResult.fold(
            onSuccess = { episodeList ->
                episodes = episodeList
                isLoading = false
            },
            onFailure = { error ->
                errorMessage = error.message
                isLoading = false
            }
        )
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "EPISODES",
                style = MaterialTheme.typography.displayMedium
            )
            
            Button(
                onClick = onNavigateBack,
                colors = ButtonDefaults.colors(
                    containerColor = TavunoSecondary
                )
            ) {
                Text("Back")
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        if (isLoading) {
            com.tavuno.tv.ui.components.LoadingState("Loading episodes...")
        } else if (errorMessage != null) {
            com.tavuno.tv.ui.components.ErrorState(
                message = errorMessage!!,
                onRetry = {
                    isLoading = true
                    errorMessage = null
                    CoroutineScope(Dispatchers.IO).launch {
                        val episodesResult = catalogRepository.getSeasonEpisodes(seasonId)
                        episodesResult.fold(
                            onSuccess = { episodeList ->
                                episodes = episodeList
                                isLoading = false
                            },
                            onFailure = { error ->
                                errorMessage = error.message
                                isLoading = false
                            }
                        )
                    }
                }
            )
        } else if (episodes.isEmpty()) {
            com.tavuno.tv.ui.components.EmptyState("No episodes available")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(episodes) { episode ->
                    com.tavuno.tv.ui.components.FocusableCard(
                        onClick = {
                            CoroutineScope(Dispatchers.IO).launch {
                                val authResult = playbackRepository.authorizeEpisodePlayback(episode.id)
                                authResult.fold(
                                    onSuccess = { auth ->
                                        onNavigateToPlayer("episode", episode.id.toString())
                                    },
                                    onFailure = { error ->
                                        errorMessage = error.message
                                    }
                                )
                            }
                        },
                        modifier = Modifier.height(80.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Episode ${episode.episodeNumber}: ${episode.title}",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                if (episode.duration != null) {
                                    Text(
                                        text = episode.duration,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
