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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.streamvault.core.ui.theme.StreamVaultTheme
import com.streamvault.domain.model.Category
import com.streamvault.domain.model.Channel
import com.streamvault.domain.model.Movie
import com.streamvault.domain.model.Series

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TavunoHomeScreen(
    viewModel: TavunoHomeViewModel = hiltViewModel(),
    onChannelSelected: (Channel) -> Unit = {},
    onCategorySelected: (Long) -> Unit = {},
    onMovieSelected: (Movie) -> Unit = {},
    onSeriesSelected: (Series) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    StreamVaultTheme {
        when (uiState) {
            is TavunoHomeUiState.Loading -> {
                TavunoLoadingScreen()
            }
            is TavunoHomeUiState.Success -> {
                val state = uiState as TavunoHomeUiState.Success
                TavunoCatalogContent(
                    categories = state.categories,
                    featuredChannels = state.featuredChannels,
                    movies = state.movies,
                    series = state.series,
                    onChannelSelected = onChannelSelected,
                    onCategorySelected = onCategorySelected,
                    onMovieSelected = onMovieSelected,
                    onSeriesSelected = onSeriesSelected
                )
            }
            is TavunoHomeUiState.Error -> {
                TavunoErrorScreen(
                    message = (uiState as TavunoHomeUiState.Error).message,
                    onRetry = { viewModel.loadCatalog() }
                )
            }
        }
    }
}

@Composable
private fun TavunoLoadingScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Loading Tavuno Catalog...",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun TavunoErrorScreen(
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
            text = message,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(24.dp))
        androidx.tv.material3.Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TavunoCatalogContent(
    categories: List<Category>,
    featuredChannels: List<Channel>,
    movies: List<Movie>,
    series: List<Series>,
    onChannelSelected: (Channel) -> Unit,
    onCategorySelected: (Long) -> Unit,
    onMovieSelected: (Movie) -> Unit,
    onSeriesSelected: (Series) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 32.dp)
    ) {
        // Header
        Text(
            text = "Tavuno TV",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Categories
        if (categories.isNotEmpty()) {
            SectionHeader("Categories")
            Spacer(modifier = Modifier.height(16.dp))
            CategoryRow(
                categories = categories,
                onCategorySelected = onCategorySelected
            )
            Spacer(modifier = Modifier.height(32.dp))
        }

        // Featured Channels
        if (featuredChannels.isNotEmpty()) {
            SectionHeader("Featured Channels")
            Spacer(modifier = Modifier.height(16.dp))
            ChannelRow(
                channels = featuredChannels,
                onChannelSelected = onChannelSelected
            )
            Spacer(modifier = Modifier.height(32.dp))
        }

        // Movies
        if (movies.isNotEmpty()) {
            SectionHeader("Movies")
            Spacer(modifier = Modifier.height(16.dp))
            MovieRow(
                movies = movies,
                onMovieSelected = onMovieSelected
            )
            Spacer(modifier = Modifier.height(32.dp))
        }

        // Series
        if (series.isNotEmpty()) {
            SectionHeader("Series")
            Spacer(modifier = Modifier.height(16.dp))
            SeriesRow(
                series = series,
                onSeriesSelected = onSeriesSelected
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CategoryRow(
    categories: List<Category>,
    onCategorySelected: (Long) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(categories) { category ->
            CategoryCard(
                category = category,
                onClick = { onCategorySelected(category.id) }
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CategoryCard(
    category: Category,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(200.dp).height(100.dp),
        colors = CardDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = category.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ChannelRow(
    channels: List<Channel>,
    onChannelSelected: (Channel) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(channels) { channel ->
            ChannelCard(
                channel = channel,
                onClick = { onChannelSelected(channel) }
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ChannelCard(
    channel: Channel,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(200.dp).height(120.dp),
        colors = CardDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = channel.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MovieRow(
    movies: List<Movie>,
    onMovieSelected: (Movie) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(movies) { movie ->
            MovieCard(
                movie = movie,
                onClick = { onMovieSelected(movie) }
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MovieCard(
    movie: Movie,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(160.dp).height(240.dp),
        colors = CardDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = movie.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            movie.year?.let { year ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = year.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SeriesRow(
    series: List<Series>,
    onSeriesSelected: (Series) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(series) { seriesItem ->
            SeriesCard(
                series = seriesItem,
                onClick = { onSeriesSelected(seriesItem) }
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SeriesCard(
    series: Series,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(160.dp).height(240.dp),
        colors = CardDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = series.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
