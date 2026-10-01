package com.tavuno.tv.ui.screens.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.tavuno.tv.data.model.Channel
import com.tavuno.tv.data.model.Category
import com.tavuno.tv.data.model.ChannelNowNext
import com.tavuno.tv.data.model.EpgProgram
import com.tavuno.tv.data.repository.CatalogRepository
import com.tavuno.tv.ui.theme.TavunoAccent
import com.tavuno.tv.ui.theme.TavunoSecondary

/** How often visible rows re-read now/next; mirrors the API's 60s cache window. */
private const val EPG_REFRESH_INTERVAL_MS = 60_000L

@Composable
fun LiveTvScreen(
    catalogRepository: CatalogRepository,
    onNavigateBack: () -> Unit,
    onNavigateToPlayer: (Int) -> Unit
) {
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var channelNowNextMap by remember { mutableStateOf<Map<Int, ChannelNowNext>>(emptyMap()) }
    
    val coroutineScope = rememberCoroutineScope()
    
    // Initial load of categories and channels
    LaunchedEffect(Unit) {
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
    
    // Load channels when category changes
    LaunchedEffect(selectedCategory) {
        val channelsResult = catalogRepository.getChannels(
            categoryId = selectedCategory?.id
        )
        channelsResult.fold(
            onSuccess = { loadedChannels ->
                channels = loadedChannels
                errorMessage = null
            },
            onFailure = { error ->
                errorMessage = error.message
            }
        )
    }
    
    // Refresh now/next for the visible rows; the API caches each payload for 60s.
    var epgRefreshTick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(EPG_REFRESH_INTERVAL_MS)
            epgRefreshTick++
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
        
        // Category filter. Kept horizontally scrollable: a plain Row squeezes
        // overflowing chips to zero width, their labels wrap onto many lines
        // and the row ends up eating the whole screen, leaving no room for the
        // channel list below.
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item {
                CategoryChip(
                    name = "All",
                    isSelected = selectedCategory == null,
                    onClick = {
                        selectedCategory = null
                    }
                )
            }
            
            items(categories, key = { it.id }) { category ->
                CategoryChip(
                    name = category.name,
                    isSelected = selectedCategory?.id == category.id,
                    onClick = {
                        selectedCategory = category
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
                    coroutineScope.launch {
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
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(channels, key = { it.id }) { channel ->
                    val nowNext = channelNowNextMap[channel.id]
                    // Fetch lazily per visible row: avoids firing one request per
                    // channel at once, and refreshes when the tick advances.
                    LaunchedEffect(channel.id, epgRefreshTick) {
                        catalogRepository.getChannelNowNext(channel.id).fold(
                            onSuccess = { loaded ->
                                channelNowNextMap = channelNowNextMap + (channel.id to loaded)
                            },
                            onFailure = {
                                // Per-channel EPG failures are non-fatal: keep the list usable.
                            }
                        )
                    }
                    ChannelCard(
                        channel = channel,
                        nowNext = nowNext,
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
        Text(
            text = name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ChannelCard(
    channel: Channel,
    nowNext: ChannelNowNext?,
    onClick: () -> Unit
) {
    com.tavuno.tv.ui.components.FocusableCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                NowNextLine(prefix = "NOW", program = nowNext?.now)
                NowNextLine(prefix = "NEXT", program = nowNext?.next)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "▶",
                style = MaterialTheme.typography.headlineMedium,
                color = TavunoAccent
            )
        }
    }
}

/** Renders "NOW  14:10 - 14:15  •  Programme title" style EPG lines. */
@Composable
private fun NowNextLine(
    prefix: String,
    program: EpgProgram?
) {
    if (program == null) return

    val timeRange = EpgTimeFormat.rangeLabel(program.startsAt, program.endsAt)
    Text(
        text = if (timeRange != null) {
            "$prefix  $timeRange  •  ${program.title}"
        } else {
            "$prefix  •  ${program.title}"
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}
