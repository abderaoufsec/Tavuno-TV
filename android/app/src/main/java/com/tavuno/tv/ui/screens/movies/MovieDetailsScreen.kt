package com.tavuno.tv.ui.screens.movies

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.tavuno.tv.data.model.MovieDetails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.tavuno.tv.ui.theme.TavunoAccent
import com.tavuno.tv.ui.theme.TavunoSecondary

@Composable
fun MovieDetailsScreen(
    movieId: Int,
    catalogRepository: com.tavuno.tv.data.repository.CatalogRepository,
    playbackRepository: com.tavuno.tv.data.repository.PlaybackRepository,
    onNavigateBack: () -> Unit,
    onNavigateToPlayer: (String, String) -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var movieDetails by remember { mutableStateOf<MovieDetails?>(null) }
    
    // Load movie details from repository
    CoroutineScope(Dispatchers.IO).launch {
        val movieResult = catalogRepository.getMovieDetails(movieId)
        movieResult.fold(
            onSuccess = { details ->
                movieDetails = details
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
                text = "MOVIE DETAILS",
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
            com.tavuno.tv.ui.components.LoadingState("Loading movie details...")
        } else if (errorMessage != null) {
            com.tavuno.tv.ui.components.ErrorState(
                message = errorMessage!!,
                onRetry = {
                    isLoading = true
                    errorMessage = null
                    CoroutineScope(Dispatchers.IO).launch {
                        val movieResult = catalogRepository.getMovieDetails(movieId)
                        movieResult.fold(
                            onSuccess = { details ->
                                movieDetails = details
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
        } else {
            val details = movieDetails
            if (details != null) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = details.title,
                        style = MaterialTheme.typography.displaySmall
                    )
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (details.releaseYear != null) {
                            Text(
                                text = details.releaseYear.toString(),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (details.categoryName != null) {
                            Text(
                                text = details.categoryName,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    if (details.synopsis != null) {
                        Text(
                            text = details.synopsis,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = {
                            CoroutineScope(Dispatchers.IO).launch {
                                val authResult = playbackRepository.authorizeMoviePlayback(movieId)
                                authResult.fold(
                                    onSuccess = { auth ->
                                        onNavigateToPlayer("movie", movieId.toString())
                                    },
                                    onFailure = { error ->
                                        errorMessage = error.message
                                    }
                                )
                            }
                        },
                        enabled = details.playbackAvailable,
                        modifier = Modifier.width(200.dp),
                        colors = ButtonDefaults.colors(
                            containerColor = TavunoAccent
                        )
                    ) {
                        Text("Play")
                    }
                }
            }
        }
    }
}
