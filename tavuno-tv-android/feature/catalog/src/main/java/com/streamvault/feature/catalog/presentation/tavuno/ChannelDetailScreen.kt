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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.compose.material3.CircularProgressIndicator
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
import kotlinx.coroutines.launch

@Composable
fun ChannelDetailScreen(
    channelId: Long,
    onPlayChannel: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: ChannelDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isTelevisionDevice = rememberIsTelevisionDevice()
    val playButtonFocusRequester = FocusRequester()
    val coroutineScope = rememberCoroutineScope()
    var isAuthorizing by remember { mutableStateOf(false) }

    LaunchedEffect(channelId) {
        viewModel.loadChannelDetails(channelId)
    }

    when (uiState) {
        is ChannelDetailUiState.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AppColors.Canvas),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Loading channel details...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = AppColors.TextSecondary
                )
            }
        }
        is ChannelDetailUiState.Success -> {
            val details = (uiState as ChannelDetailUiState.Success).details
            if (details != null) {
                ChannelDetailContent(
                    details = details,
                    isTelevisionDevice = isTelevisionDevice,
                    playButtonFocusRequester = playButtonFocusRequester,
                    isAuthorizing = isAuthorizing,
                    onPlayChannel = {
                        coroutineScope.launch {
                            isAuthorizing = true
                            val result = viewModel.authorizePlayback(details.id)
                            isAuthorizing = false
                            if (result is com.streamvault.domain.model.Result.Success) {
                                onPlayChannel(result.data.playback.url)
                            } else {
                                // Handle error - could show toast or update UI state
                            }
                        }
                    },
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
                        text = "Channel not found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = AppColors.Live
                    )
                }
            }
        }
        is ChannelDetailUiState.Error -> {
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
                        text = (uiState as ChannelDetailUiState.Error).message,
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
private fun ChannelDetailContent(
    details: com.streamvault.domain.model.ChannelDetails,
    isTelevisionDevice: Boolean,
    playButtonFocusRequester: FocusRequester,
    isAuthorizing: Boolean,
    onPlayChannel: () -> Unit,
    onBack: () -> Unit
) {
    val categoryName = details.categoryName
    val description = details.description

    LaunchedEffect(details.id) {
        playButtonFocusRequester.requestFocusSafely(
            tag = "ChannelDetailScreen",
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
        val logoSize = if (compactLayout) 120.dp else 160.dp

        // Backdrop gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(heroHeight)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            AppColors.Brand.copy(alpha = 0.3f),
                            AppColors.HeroTop,
                            AppColors.HeroBottom
                        )
                    )
                )
        )

        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            // Back button
            TvButton(
                onClick = onBack,
                colors = ButtonDefaults.colors(
                    containerColor = AppColors.Surface.copy(alpha = 0.72f),
                    contentColor = AppColors.TextPrimary
                )
            ) {
                Text("Back")
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Channel info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                if (details.logo != null) {
                    AsyncImage(
                        model = rememberCrossfadeImageModel(details.logo),
                        contentDescription = details.name,
                        modifier = Modifier
                            .size(logoSize)
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Fit
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = details.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextPrimary
                    )
                    if (categoryName != null) {
                        Text(
                            text = categoryName,
                            style = MaterialTheme.typography.bodyLarge,
                            color = AppColors.TextSecondary
                        )
                    }
                    if (details.isActive) {
                        Text(
                            text = "Live",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppColors.Success,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Description
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = AppColors.TextSecondary,
                    lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
                )
                Spacer(modifier = Modifier.height(32.dp))
            }

            // Play button
            Button(
                onClick = { if (!isAuthorizing) onPlayChannel() },
                enabled = !isAuthorizing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .focusRequester(playButtonFocusRequester),
                colors = ButtonDefaults.colors(
                    containerColor = AppColors.Brand
                )
            ) {
                if (isAuthorizing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = AppColors.OnPrimary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Play Channel",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Playback status
            if (details.playbackAvailable) {
                androidx.compose.material3.Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = AppColors.Surface
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Channel Status",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.TextPrimary
                        )
                        Text(
                            text = "Live streaming available",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppColors.TextSecondary
                        )
                    }
                }
            }
        }
    }
}
