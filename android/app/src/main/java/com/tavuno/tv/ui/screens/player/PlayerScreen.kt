package com.tavuno.tv.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.material3.Slider
import com.tavuno.tv.data.repository.PlaybackRepository
import com.tavuno.tv.playback.PlaybackUrls
import com.tavuno.tv.ui.theme.TavunoAccent
import com.tavuno.tv.ui.theme.TavunoSecondary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PlayerScreen(
    contentType: String,
    contentId: String,
    playbackRepository: com.tavuno.tv.data.repository.PlaybackRepository,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var playbackUrl by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var sessionId by remember { mutableStateOf<Int?>(null) }
    var dvrEnabled by remember { mutableStateOf(false) }
    var maxRewindSeconds by remember { mutableStateOf(0) }
    var playerError by remember { mutableStateOf<String?>(null) }
    
    // ExoPlayer setup
    val exoPlayer = remember {
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("TavunoTV/1.0 (Linux; Android TV)")
            .setAllowCrossProtocolRedirects(true)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .apply {
                setHandleAudioBecomingNoisy(true)
            }
    }
    
    val lifecycleOwner = LocalLifecycleOwner.current
    
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                playerError = error.message ?: "Playback failed"
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }
    
    // Heartbeat job
    val heartbeatJob = remember { mutableStateOf<Job?>(null) }
    
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    exoPlayer.pause()
                    heartbeatJob.value?.cancel()
                }
                Lifecycle.Event.ON_RESUME -> {
                    exoPlayer.play()
                    startHeartbeat(sessionId, playbackRepository, heartbeatJob)
                }
                Lifecycle.Event.ON_DESTROY -> {
                    exoPlayer.release()
                    heartbeatJob.value?.cancel()
                    sessionId?.let {
                        CoroutineScope(Dispatchers.IO).launch {
                            playbackRepository.stopPlayback(it)
                        }
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
            heartbeatJob.value?.cancel()
            sessionId?.let {
                CoroutineScope(Dispatchers.IO).launch {
                    playbackRepository.stopPlayback(it)
                }
            }
        }
    }
    
    // Load playback authorization based on content type
    DisposableEffect(contentType, contentId) {
        isLoading = true
        errorMessage = null
        
        CoroutineScope(Dispatchers.IO).launch {
            val result = when (contentType) {
                "live" -> playbackRepository.authorizeLivePlayback(contentId.toIntOrNull() ?: 0)
                "movie" -> playbackRepository.authorizeMoviePlayback(contentId.toIntOrNull() ?: 0)
                "episode" -> playbackRepository.authorizeEpisodePlayback(contentId.toIntOrNull() ?: 0)
                else -> Result.failure(Exception("Unknown content type"))
            }
            
            result.fold(
                onSuccess = { auth ->
                    val playableUrl = PlaybackUrls.rewriteLoopbackForEmulator(auth.playback.url)
                    playbackUrl = playableUrl
                    sessionId = auth.sessionId
                    dvrEnabled = auth.playback.dvrEnabled
                    maxRewindSeconds = auth.playback.maxRewindSeconds
                    isLoading = false
                    playerError = null
                    
                    // Start playback
                    if (playableUrl.isNotBlank()) {
                        val mediaItem = MediaItem.fromUri(playableUrl)
                        exoPlayer.setMediaItem(mediaItem)
                        exoPlayer.prepare()
                        exoPlayer.play()
                        
                        // Start heartbeat for live content
                        if (contentType == "live") {
                            startHeartbeat(sessionId, playbackRepository, heartbeatJob)
                        }
                    }
                },
                onFailure = { error ->
                    errorMessage = error.message
                    isLoading = false
                }
            )
        }
        
        onDispose {
            heartbeatJob.value?.cancel()
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black)
    ) {
        // Player view
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    player = exoPlayer
                    useController = true
                    controllerAutoShow = false
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        )
        
        // DVR seek bar (only for live content with DVR enabled)
        if (contentType == "live" && dvrEnabled && !isLoading && errorMessage == null) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        // Rewind 30 seconds
                        val currentPosition = exoPlayer.currentPosition
                        exoPlayer.seekTo((currentPosition - 30000).coerceAtLeast(0))
                    },
                    colors = ButtonDefaults.colors(
                        containerColor = TavunoSecondary
                    ),
                    modifier = Modifier.width(100.dp)
                ) {
                    Text("-30s")
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Button(
                    onClick = {
                        // Rewind 10 seconds
                        val currentPosition = exoPlayer.currentPosition
                        exoPlayer.seekTo((currentPosition - 10000).coerceAtLeast(0))
                    },
                    colors = ButtonDefaults.colors(
                        containerColor = TavunoSecondary
                    ),
                    modifier = Modifier.width(100.dp)
                ) {
                    Text("-10s")
                }
            }
        }
        
        // Controls overlay
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onNavigateBack,
                colors = ButtonDefaults.colors(
                    containerColor = TavunoSecondary
                )
            ) {
                Text("Back")
            }
            
            if (isLoading) {
                CircularProgressIndicator(color = TavunoAccent)
            } else if (errorMessage != null || playerError != null) {
                Text(
                    text = errorMessage ?: playerError ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

private fun startHeartbeat(
    sessionId: Int?,
    playbackRepository: com.tavuno.tv.data.repository.PlaybackRepository,
    heartbeatJob: androidx.compose.runtime.MutableState<Job?>
) {
    sessionId?.let { id ->
        heartbeatJob.value = CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                delay(60000) // Send heartbeat every 60 seconds
                playbackRepository.sendHeartbeat(id)
            }
        }
    }
}
