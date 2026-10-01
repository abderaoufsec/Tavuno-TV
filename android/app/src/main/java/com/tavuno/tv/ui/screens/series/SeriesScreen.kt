package com.tavuno.tv.ui.screens.series

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
import com.tavuno.tv.data.model.Series
import com.tavuno.tv.data.repository.CatalogRepository
import com.tavuno.tv.ui.components.EmptyState
import com.tavuno.tv.ui.components.ErrorState
import com.tavuno.tv.ui.components.LoadingState
import com.tavuno.tv.ui.components.PosterCard
import com.tavuno.tv.ui.theme.Dimens

/**
 * The Series grid, rebuilt on the design system — the Movies grid's twin.
 *
 * The load runs in a [LaunchedEffect] rather than a body-level `Dispatchers.IO` scope: writing
 * Compose state off the main thread is a fatal violation, and the repository call is already
 * `suspend`, so the effect is correct and main-safe.
 */
@Composable
fun SeriesScreen(
    catalogRepository: CatalogRepository,
    onNavigateToSeriesDetails: (Int) -> Unit,
) {
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var seriesList by remember { mutableStateOf<List<Series>>(emptyList()) }
    // Bumped by Retry to re-run the load effect.
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(reloadKey) {
        isLoading = true
        errorMessage = null
        catalogRepository.getSeries().fold(
            onSuccess = { loaded ->
                seriesList = loaded
                isLoading = false
            },
            onFailure = { error ->
                errorMessage = error.message ?: "Failed to load series"
                isLoading = false
            }
        )
    }

    when {
        isLoading -> LoadingState("Loading series...")

        errorMessage != null -> ErrorState(
            message = errorMessage!!,
            onRetry = { reloadKey++ },
        )

        seriesList.isEmpty() -> EmptyState("No series available")

        else -> LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 200.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(Dimens.GapSmall),
            horizontalArrangement = Arrangement.spacedBy(Dimens.GapMedium),
            verticalArrangement = Arrangement.spacedBy(Dimens.GapMedium),
        ) {
            items(items = seriesList, key = { it.id }) { series ->
                PosterCard(
                    title = series.title,
                    posterUrl = series.poster,
                    onClick = { onNavigateToSeriesDetails(series.id) },
                )
            }
        }
    }
}