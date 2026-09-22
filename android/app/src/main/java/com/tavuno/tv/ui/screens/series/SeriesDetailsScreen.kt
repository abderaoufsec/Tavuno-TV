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
import com.tavuno.tv.data.model.SeriesDetails
import com.tavuno.tv.data.model.SeasonDetails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.tavuno.tv.ui.theme.TavunoSecondary

@Composable
fun SeriesDetailsScreen(
    seriesId: Int,
    catalogRepository: com.tavuno.tv.data.repository.CatalogRepository,
    onNavigateBack: () -> Unit,
    onNavigateToSeason: (Int) -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var seriesDetails by remember { mutableStateOf<SeriesDetails?>(null) }
    
    // Load series details from repository
    CoroutineScope(Dispatchers.IO).launch {
        val seriesResult = catalogRepository.getSeriesDetails(seriesId)
        seriesResult.fold(
            onSuccess = { details ->
                seriesDetails = details
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
                text = "SERIES DETAILS",
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
            com.tavuno.tv.ui.components.LoadingState("Loading series details...")
        } else if (errorMessage != null) {
            com.tavuno.tv.ui.components.ErrorState(
                message = errorMessage!!,
                onRetry = {
                    isLoading = true
                    errorMessage = null
                    CoroutineScope(Dispatchers.IO).launch {
                        val seriesResult = catalogRepository.getSeriesDetails(seriesId)
                        seriesResult.fold(
                            onSuccess = { details ->
                                seriesDetails = details
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
            val details = seriesDetails
            if (details != null) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = details.title,
                        style = MaterialTheme.typography.displaySmall
                    )
                    
                    if (details.synopsis != null) {
                        Text(
                            text = details.synopsis,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    
                    if (details.categoryName != null) {
                        Text(
                            text = details.categoryName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    if (details.episodeCount != null) {
                        Text(
                            text = "${details.episodeCount} episodes",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Seasons",
                        style = MaterialTheme.typography.titleLarge
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (details.seasons != null && details.seasons.isNotEmpty()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(details.seasons) { season ->
                                com.tavuno.tv.ui.components.FocusableCard(
                                    onClick = { onNavigateToSeason(season.id) },
                                    modifier = Modifier.height(80.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = season.name,
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                            Text(
                                                text = "${season.episodeCount} episodes",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        com.tavuno.tv.ui.components.EmptyState("No seasons available")
                    }
                }
            }
        }
    }
}
