package com.tavuno.tv.ui.screens.movies

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tavuno.tv.data.model.Movie
import com.tavuno.tv.data.repository.CatalogRepository
import com.tavuno.tv.ui.components.EmptyState
import com.tavuno.tv.ui.components.ErrorState
import com.tavuno.tv.ui.components.LoadingState
import com.tavuno.tv.ui.components.PosterCard
import com.tavuno.tv.ui.theme.Dimens

/**
 * The Movies grid, rebuilt on the design system.
 *
 * The load now runs in a [LaunchedEffect] instead of a body-level
 * `CoroutineScope(Dispatchers.IO).launch`: writing Compose state off the main thread is a fatal
 * violation, and the repository's calls are already `suspend` (Retrofit's suspend adapter), so the
 * effect is both correct and main-safe.
 *
 * There is no Back affordance in the body — the shell's sidebar and the system BACK button (which
 * [com.tavuno.tv.ui.navigation.TavunoMainScreen] routes back to Home) own that now.
 */
@Composable
fun MoviesScreen(
    catalogRepository: CatalogRepository,
    onNavigateToMovieDetails: (Int) -> Unit,
) {
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var movies by remember { mutableStateOf<List<Movie>>(emptyList()) }
    // Bumped by Retry to re-run the load effect.
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(reloadKey) {
        isLoading = true
        errorMessage = null
        catalogRepository.getMovies().fold(
            onSuccess = { loaded ->
                movies = loaded
                isLoading = false
            },
            onFailure = { error ->
                errorMessage = error.message ?: "Failed to load movies"
                isLoading = false
            }
        )
    }

    when {
        isLoading -> LoadingState("Loading movies...")

        errorMessage != null -> ErrorState(
            message = errorMessage!!,
            onRetry = { reloadKey++ },
        )

        movies.isEmpty() -> EmptyState("No movies available")

        else -> LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 200.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(Dimens.GapSmall),
            horizontalArrangement = Arrangement.spacedBy(Dimens.GapMedium),
            verticalArrangement = Arrangement.spacedBy(Dimens.GapMedium),
        ) {
            items(items = movies, key = { it.id }) { movie ->
                PosterCard(
                    title = movie.title,
                    posterUrl = movie.poster,
                    subtitle = movie.releaseYear?.toString(),
                    onClick = { onNavigateToMovieDetails(movie.id) },
                )
            }
        }
    }
}