package com.tavuno.tv.ui.screens.movies

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.tavuno.tv.data.model.Movie
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.tavuno.tv.ui.theme.TavunoAccent
import com.tavuno.tv.ui.theme.TavunoSecondary

@Composable
fun MoviesScreen(
    catalogRepository: com.tavuno.tv.data.repository.CatalogRepository,
    onNavigateBack: () -> Unit,
    onNavigateToMovieDetails: (Int) -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var movies by remember { mutableStateOf<List<Movie>>(emptyList())}
    
    // Load movies from repository
    CoroutineScope(Dispatchers.IO).launch {
        val moviesResult = catalogRepository.getMovies()
        moviesResult.fold(
            onSuccess = { moviesList ->
                movies = moviesList
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
                text = "MOVIES",
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
            com.tavuno.tv.ui.components.LoadingState("Loading movies...")
        } else if (errorMessage != null) {
            com.tavuno.tv.ui.components.ErrorState(
                message = errorMessage!!,
                onRetry = {
                    isLoading = true
                    errorMessage = null
                    CoroutineScope(Dispatchers.IO).launch {
                        val moviesResult = catalogRepository.getMovies()
                        moviesResult.fold(
                            onSuccess = { moviesList ->
                                movies = moviesList
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
        } else if (movies.isEmpty()) {
            com.tavuno.tv.ui.components.EmptyState("No movies available")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(movies) { movie ->
                    com.tavuno.tv.ui.components.FocusableCard(
                        onClick = { onNavigateToMovieDetails(movie.id) },
                        modifier = Modifier.height(240.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = movie.title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 2
                            )
                            if (movie.releaseYear != null) {
                                Text(
                                    text = movie.releaseYear.toString(),
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
