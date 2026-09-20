package com.streamvault.feature.catalog.presentation.tavuno

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.streamvault.core.ui.design.AppColors
import com.streamvault.core.ui.device.rememberIsTelevisionDevice
import com.streamvault.core.ui.image.rememberCrossfadeImageModel
import com.streamvault.core.ui.interaction.TvButton
import com.streamvault.core.ui.design.requestFocusSafely

@Composable
fun TavunoSeriesDetailScreen(
    seriesId: Long,
    onPlayEpisode: (Long, Long) -> Unit,
    onBack: () -> Unit,
    viewModel: TavunoSeriesDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isTelevisionDevice = rememberIsTelevisionDevice()
    val playButtonFocusRequester = FocusRequester()

    LaunchedEffect(seriesId) {
        viewModel.loadSeriesDetails(seriesId)
    }

    when (uiState) {
        is TavunoSeriesDetailUiState.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AppColors.Canvas),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Loading series details...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = AppColors.TextSecondary
                )
            }
        }
        is TavunoSeriesDetailUiState.Success -> {
            val details = (uiState as TavunoSeriesDetailUiState.Success).details
            if (details != null) {
                SeriesDetailContent(
                    details = details,
                    isTelevisionDevice = isTelevisionDevice,
                    playButtonFocusRequester = playButtonFocusRequester,
                    onPlayEpisode = onPlayEpisode,
                    onBack = onBack
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AppColors.Canvas),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Series not found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = AppColors.Live
                    )
                }
            }
        }
        is TavunoSeriesDetailUiState.Error -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AppColors.Canvas),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = (uiState as TavunoSeriesDetailUiState.Error).message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = AppColors.Live
                    )
                    TvButton(onClick = onBack) {
                        Text("Back")
                    }
                }
            }
        }
    }
}

@Composable
private fun SeriesDetailContent(
    details: com.streamvault.domain.model.SeriesDetails,
    isTelevisionDevice: Boolean,
    playButtonFocusRequester: FocusRequester,
    onPlayEpisode: (Long, Long) -> Unit,
    onBack: () -> Unit
) {
    val categoryName = details.categoryName
    val synopsis = details.synopsis
    val seasons = details.seasons

    LaunchedEffect(details.id) {
        playButtonFocusRequester.requestFocusSafely(
            tag = "SeriesDetailScreen",
            target = "Play button"
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Canvas)
    ) {
        val compactLayout = !isTelevisionDevice && maxWidth < 900.dp
        val heroHeight = when {
            maxWidth < 700.dp -> 240.dp
            !isTelevisionDevice && maxWidth < 900.dp -> 300.dp
            else -> 440.dp
        }
        val contentPadding = if (compactLayout) {
            PaddingValues(horizontal = 16.dp, vertical = 20.dp)
        } else {
            PaddingValues(horizontal = 56.dp, vertical = 36.dp)
        }
        val posterWidth = if (compactLayout) 148.dp else 240.dp

        // Backdrop image
        if (details.backdrop != null) {
            AsyncImage(
                model = rememberCrossfadeImageModel(details.backdrop),
                contentDescription = details.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(heroHeight)
                    .align(Alignment.TopCenter),
                contentScale = ContentScale.Crop
            )
        }

        // Gradient overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(heroHeight)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, AppColors.HeroTop, AppColors.HeroBottom)
                    )
                )
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Back button
            item {
                TvButton(
                    onClick = onBack,
                    colors = ButtonDefaults.colors(
                        containerColor = AppColors.Surface.copy(alpha = 0.72f),
                        contentColor = AppColors.TextPrimary
                    )
                ) {
                    Text("Back")
                }
            }

            // Series info row
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (details.poster != null) {
                        AsyncImage(
                            model = rememberCrossfadeImageModel(details.poster),
                            contentDescription = details.title,
                            modifier = Modifier
                                .width(posterWidth)
                                .aspectRatio(2f / 3f)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = details.title,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.TextPrimary
                        )
                        if (details.releaseYear != null) {
                            Text(
                                text = details.releaseYear.toString(),
                                style = MaterialTheme.typography.bodyLarge,
                                color = AppColors.TextSecondary
                            )
                        }
                        if (details.episodeCount != null) {
                            Text(
                                text = "$details.episodeCount episodes",
                                style = MaterialTheme.typography.bodyLarge,
                                color = AppColors.TextSecondary
                            )
                        }
                        if (categoryName != null) {
                            Text(
                                text = categoryName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = AppColors.TextSecondary
                            )
                        }
                    }
                }
            }

            // Synopsis
            if (synopsis != null) {
                item {
                    Text(
                        text = synopsis,
                        style = MaterialTheme.typography.bodyLarge,
                        color = AppColors.TextSecondary,
                        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
                    )
                }
            }

            // Seasons
            if (!seasons.isNullOrEmpty()) {
                item {
                    Text(
                        text = "Seasons",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextPrimary
                    )
                }

                items(seasons) { season ->
                    SeasonItem(
                        season = season,
                        seriesId = details.id,
                        onPlayEpisode = onPlayEpisode
                    )
                }
            }
        }
    }
}

@Composable
private fun SeasonItem(
    season: com.streamvault.domain.model.SeasonInfo,
    seriesId: Long,
    onPlayEpisode: (Long, Long) -> Unit
) {
    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = AppColors.Surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = season.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary
                )
                Text(
                    text = "${season.episodeCount} episodes",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextSecondary
                )
            }

            Button(
                onClick = { onPlayEpisode(seriesId, season.seasonNumber.toLong()) },
                colors = ButtonDefaults.colors(
                    containerColor = AppColors.Brand
                )
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
