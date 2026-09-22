package com.tavuno.tv.ui.screens.live

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
import androidx.compose.runtime.collectAsState
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.tavuno.tv.data.model.Channel
import com.tavuno.tv.data.model.Category
import com.tavuno.tv.data.repository.CatalogRepository
import com.tavuno.tv.ui.theme.TavunoAccent
import com.tavuno.tv.ui.theme.TavunoSecondary

@Composable
fun LiveTvScreen(
    catalogRepository: com.tavuno.tv.data.repository.CatalogRepository,
    onNavigateBack: () -> Unit,
    onNavigateToPlayer: (Int) -> Unit
) {
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Load categories and channels
    CoroutineScope(Dispatchers.IO).launch {
        val categoriesResult = catalogRepository.getCategories(kind = "live")
        categoriesResult.fold(
            onSuccess = { loadedCategories ->
                categories = loadedCategories
                val channelsResult = catalogRepository.getChannels()
                channelsResult.fold(
                    onSuccess = { loadedChannels ->
                        channels = loadedChannels
                        isLoading = false
                    },
                    onFailure = { error ->
                        errorMessage = error.message
                        isLoading = false
                    }
                )
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
                text = "LIVE TV",
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
        
        // Category filter
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CategoryChip(
                name = "All",
                isSelected = selectedCategory == null,
                onClick = {
                    selectedCategory = null
                    CoroutineScope(Dispatchers.IO).launch {
                        val channelsResult = catalogRepository.getChannels()
                        channelsResult.fold(
                            onSuccess = { loadedChannels ->
                                channels = loadedChannels
                            },
                            onFailure = { error ->
                                errorMessage = error.message
                            }
                        )
                    }
                }
            )
            
            categories.forEach { category ->
                CategoryChip(
                    name = category.name,
                    isSelected = selectedCategory?.id == category.id,
                    onClick = {
                        selectedCategory = category
                        CoroutineScope(Dispatchers.IO).launch {
                            val channelsResult = catalogRepository.getChannels(categoryId = category.id)
                            channelsResult.fold(
                                onSuccess = { loadedChannels ->
                                    channels = loadedChannels
                                },
                                onFailure = { error ->
                                    errorMessage = error.message
                                }
                            )
                        }
                    }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        if (isLoading) {
            com.tavuno.tv.ui.components.LoadingState("Loading channels...")
        } else if (errorMessage != null) {
            com.tavuno.tv.ui.components.ErrorState(
                message = errorMessage!!,
                onRetry = {
                    isLoading = true
                    errorMessage = null
                    CoroutineScope(Dispatchers.IO).launch {
                        val categoriesResult = catalogRepository.getCategories(kind = "live")
                        categoriesResult.fold(
                            onSuccess = { loadedCategories ->
                                categories = loadedCategories
                                val channelsResult = catalogRepository.getChannels()
                                channelsResult.fold(
                                    onSuccess = { loadedChannels ->
                                        channels = loadedChannels
                                        isLoading = false
                                    },
                                    onFailure = { error ->
                                        errorMessage = error.message
                                        isLoading = false
                                    }
                                )
                            },
                            onFailure = { error ->
                                errorMessage = error.message
                                isLoading = false
                            }
                        )
                    }
                }
            )
        } else if (channels.isEmpty()) {
            com.tavuno.tv.ui.components.EmptyState("No channels available")
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(channels) { channel ->
                    ChannelCard(
                        channel = channel,
                        onClick = { onNavigateToPlayer(channel.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryChip(
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

@Composable
fun ChannelCard(
    channel: Channel,
    onClick: () -> Unit
) {
    com.tavuno.tv.ui.components.FocusableCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxSize()
            .height(80.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.titleLarge
                )
                // TODO: Add current program from EPG
            }
            Text(
                text = "▶",
                style = MaterialTheme.typography.headlineMedium,
                color = TavunoAccent
            )
        }
    }
}
