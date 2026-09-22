package com.tavuno.tv.ui.screens.sports

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
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.tavuno.tv.data.model.Match
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.tavuno.tv.ui.theme.TavunoAccent
import com.tavuno.tv.ui.theme.TavunoSecondary

@Composable
fun SportsScreen(
    sportsRepository: com.tavuno.tv.data.repository.SportsRepository,
    onNavigateBack: () -> Unit,
    onNavigateToPlayer: (Int) -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableStateOf("live") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var matches by remember { mutableStateOf<List<Match>>(emptyList())}
    
    // Load matches based on selected tab
    CoroutineScope(Dispatchers.IO).launch {
        val status = if (selectedTab == "live") "live" else "upcoming"
        val matchesResult = sportsRepository.getMatches(status = status, limit = 50)
        matchesResult.fold(
            onSuccess = { matchList ->
                matches = matchList
                isLoading = false
            },
            onFailure = { error ->
                errorMessage = error.message
                isLoading = false
            }
        )
    }
    
    // Reload when tab changes
    CoroutineScope(Dispatchers.IO).launch {
        val status = if (selectedTab == "live") "live" else "upcoming"
        val matchesResult = sportsRepository.getMatches(status = status, limit = 50)
        matchesResult.fold(
            onSuccess = { matchList ->
                matches = matchList
            },
            onFailure = { error ->
                errorMessage = error.message
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
                text = "SPORTS",
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
        
        // Tab selector
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TabChip(
                name = "LIVE NOW",
                isSelected = selectedTab == "live",
                onClick = {
                    selectedTab = "live"
                    CoroutineScope(Dispatchers.IO).launch {
                        val matchesResult = sportsRepository.getMatches(status = "live", limit = 50)
                        matchesResult.fold(
                            onSuccess = { matchList ->
                                matches = matchList
                            },
                            onFailure = { error ->
                                errorMessage = error.message
                            }
                        )
                    }
                }
            )
            TabChip(
                name = "UPCOMING",
                isSelected = selectedTab == "upcoming",
                onClick = {
                    selectedTab = "upcoming"
                    CoroutineScope(Dispatchers.IO).launch {
                        val matchesResult = sportsRepository.getMatches(status = "upcoming", limit = 50)
                        matchesResult.fold(
                            onSuccess = { matchList ->
                                matches = matchList
                            },
                            onFailure = { error ->
                                errorMessage = error.message
                            }
                        )
                    }
                }
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        if (isLoading) {
            com.tavuno.tv.ui.components.LoadingState("Loading sports events...")
        } else if (errorMessage != null) {
            com.tavuno.tv.ui.components.ErrorState(
                message = errorMessage!!,
                onRetry = {
                    isLoading = true
                    errorMessage = null
                    CoroutineScope(Dispatchers.IO).launch {
                        val status = if (selectedTab == "live") "live" else "upcoming"
                        val matchesResult = sportsRepository.getMatches(status = status, limit = 50)
                        matchesResult.fold(
                            onSuccess = { matchList ->
                                matches = matchList
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
        } else if (matches.isEmpty()) {
            com.tavuno.tv.ui.components.EmptyState("No sports events available")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(matches) { match ->
                    com.tavuno.tv.ui.components.FocusableCard(
                        onClick = {
                            match.channelId?.let { channelId ->
                                onNavigateToPlayer(channelId)
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
                                    text = "Match ${match.id}",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = match.status,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (match.channelId != null) {
                                Text(
                                    text = "▶",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = TavunoAccent
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TabChip(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = if (isSelected) {
            ButtonDefaults.colors(containerColor = TavunoAccent)
        } else {
            ButtonDefaults.colors(containerColor = TavunoSecondary)
        }
    ) {
        Text(name)
    }
}
