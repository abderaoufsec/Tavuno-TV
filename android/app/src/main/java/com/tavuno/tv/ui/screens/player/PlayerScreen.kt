package com.tavuno.tv.ui.screens.player

import androidx.compose.foundation.focusable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.tavuno.tv.core.AppModule
import com.tavuno.tv.core.LiveChannelQueue
import com.tavuno.tv.data.model.ChannelNowNext
import com.tavuno.tv.data.repository.PlaybackRepository
import com.tavuno.tv.playback.LiveZapNavigator
import com.tavuno.tv.playback.PlaybackUrls
import com.tavuno.tv.playback.ZapChannel
import com.tavuno.tv.ui.components.TavunoButton
import com.tavuno.tv.ui.components.TavunoButtonStyle
import com.tavuno.tv.ui.theme.Dimens
import com.tavuno.tv.ui.theme.TavunoAccent
import com.tavuno.tv.ui.theme.TavunoTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The control strip retires itself so a stream never sits under a button bar forever. */
private const val HUD_AUTO_HIDE_MS = 8_000L

/** How often the on-screen channel's now/next guide data is re-read (matches the API cache). */
private const val EPG_REFRESH_INTERVAL_MS = 60_000L

/**
 * Full-screen playback for live channels, movies and episodes.
 *
 * Slice A of the OwnTV parity port turned this into a real TV player rather than a bare surface:
 *
 *  - **Zapping.** The browse screen arms a list via [LiveChannelQueue]; a [LiveZapNavigator] steps
 *    through it (CH±, D-pad UP/DOWN, HOLD-free auto-repeat on both), and every hop re-runs the same
 *    authorize effect the first load uses — including closing the session it replaces.
 *  - **Remote map.** All "which key does what, in which state" decisions live in [resolvePlayerKey]
 *    ([PlayerRemote.kt]) so they are unit-testable; this file only applies the result.
 *  - **Layers.** A HUD control strip (auto-hide, OK-focusable) and an in-player channel list
 *    ([PlayerChannelOverlay]), on the BACK ladder: list → controls → exit.
 *  - **Subtitles.** Text tracks start *disabled* (Media3 would otherwise auto-select one nobody asked
 *    for); the SUBTITLE key or the HUD pill walks `Off → track 1 → … → Off`. The decisions live in
 *    [PlayerTracks], so the walk is unit-testable without a decoder.
 *
 * Guide data ([ChannelNowNext]) is decoration: playback never waits on it.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PlayerScreen(
    contentType: String,
    contentId: String,
    playbackRepository: PlaybackRepository,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val isLive = contentType == "live"

    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var sessionId by remember { mutableStateOf<Int?>(null) }
    var dvrEnabled by remember { mutableStateOf(false) }
    var maxRewindSeconds by remember { mutableStateOf(0) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }

    // The id being tuned. A zap is just a change of this value: the authorize effect below owns the
    // network call, so first entry and every channel hop take the identical path.
    var currentId by remember { mutableStateOf(contentId) }

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
                // Subtitles start OFF. DefaultTrackSelector otherwise auto-selects a text track, so
                // a viewer who never asked for captions gets them; the SUBTITLE key turns them on.
                trackSelectionParameters = TrackSelectionParameters.Builder(context)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
            }
    }

    // The decoder's track tree, mirrored into state so the subtitle UI recomposes whenever a stream
    // changes its tracks — every zap re-reads it from scratch.
    var currentTracks by remember { mutableStateOf(exoPlayer.currentTracks) }

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                playerError = error.message ?: "Playback failed"
            }

            override fun onIsPlayingChanged(playingState: Boolean) {
                isPlaying = playingState
            }

            override fun onTracksChanged(tracks: Tracks) {
                currentTracks = tracks
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    // Heartbeat job. A composition-scoped (main-dispatched) scope keeps the job tied to the screen.
    val scope = rememberCoroutineScope()
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
                    startHeartbeat(sessionId, playbackRepository, heartbeatJob, scope)
                }
                Lifecycle.Event.ON_DESTROY -> {
                    exoPlayer.release()
                    heartbeatJob.value?.cancel()
                    sessionId?.let { id -> scope.launch { playbackRepository.stopPlayback(id) } }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
            heartbeatJob.value?.cancel()
            sessionId?.let { id -> scope.launch { playbackRepository.stopPlayback(id) } }
        }
    }

    // The zap list published by the browse screen. The navigator is recreated (not mutated) when
    // that published state changes, so the player's selection is always a pure function of it and
    // reads during composition are never one frame stale.
    val publishedChannels by LiveChannelQueue.channels.collectAsState()
    val publishedPlayingId by LiveChannelQueue.playingId.collectAsState()
    val navigator = remember(publishedChannels, publishedPlayingId) {
        LiveZapNavigator().apply { replace(publishedChannels, publishedPlayingId) }
    }
    val canZap = isLive && navigator.canZap
    val playingChannelId = if (isLive) currentId.toIntOrNull() else null

    // Subtitle state is derived from the decoder's own track tree, never remembered locally, so the
    // HUD can never claim a track is on that isn't — including when a stream swaps tracks mid-play.
    val subtitleTracks = remember(currentTracks) { subtitleTrackInfos(currentTracks) }
    val canSelectSubtitles = subtitleTracks.isNotEmpty()
    val subtitleChoiceCount = subtitleTracks.size + 1 // Off, then one entry per text track
    val subtitleActiveIndex = remember(subtitleTracks) { selectedSubtitleIndex(subtitleTracks) }
    val activeSubtitleLabel = remember(subtitleTracks, subtitleActiveIndex) {
        subtitleTracks.getOrNull(subtitleActiveIndex - 1)
            ?.let { subtitleTrackLabel(it.label, it.language, "Track") }
    }

    // Overlay state, declared before the effects that key off it.
    // hudVisible: the control strip is up. channelListOpen: the in-player list owns the D-pad.
    // rootFocused: focus sits on the video surface itself (see PlayerKeyContext.rootFocused).
    var hudVisible by remember { mutableStateOf(false) }
    var channelListOpen by remember { mutableStateOf(false) }
    var rootFocused by remember { mutableStateOf(true) }
    val rootFocus = remember { FocusRequester() }
    val hudFocus = remember { FocusRequester() }
    val errorFocus = remember { FocusRequester() }

    // Guide data for the HUD/overlay: the tuned channel always, plus whatever the channel list can
    // show when it opens. Failures are silently tolerated — playback never waits on this.
    var nowNextById by remember { mutableStateOf<Map<Int, ChannelNowNext>>(emptyMap()) }
    var epgTick by remember { mutableStateOf(0) }
    LaunchedEffect(playingChannelId, epgTick) {
        val id = playingChannelId ?: return@LaunchedEffect
        AppModule.catalogRepository.getChannelNowNext(id).fold(
            onSuccess = { loaded -> nowNextById = nowNextById + (id to loaded) },
            onFailure = {},
        )
    }
    LaunchedEffect(channelListOpen, publishedChannels) {
        if (!channelListOpen) return@LaunchedEffect
        for (channel in navigator.list) {
            if (channel.id in nowNextById) continue
            AppModule.catalogRepository.getChannelNowNext(channel.id).fold(
                onSuccess = { loaded -> nowNextById = nowNextById + (channel.id to loaded) },
                onFailure = {},
            )
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(EPG_REFRESH_INTERVAL_MS)
            epgTick++
        }
    }

    // Load playback authorization based on content type. Re-runs on every zap (contentId change):
    // ExoPlayer must only be touched from the thread that created it, so the network hop stays
    // inside the repository and every player call stays on main.
    LaunchedEffect(contentType, currentId) {
        isLoading = true
        errorMessage = null
        playerError = null
        heartbeatJob.value?.cancel()

        // A zap opens a fresh session; close the one it replaces instead of leaving it to expire
        // server-side. Fire-and-continue: a dead connection must not block the new tune.
        sessionId?.let { previous -> runCatching { playbackRepository.stopPlayback(previous) } }
        sessionId = null

        val result = when (contentType) {
            "live" -> playbackRepository.authorizeLivePlayback(currentId.toIntOrNull() ?: 0)
            "movie" -> playbackRepository.authorizeMoviePlayback(currentId.toIntOrNull() ?: 0)
            "episode" -> playbackRepository.authorizeEpisodePlayback(currentId.toIntOrNull() ?: 0)
            else -> Result.failure(Exception("Unknown content type"))
        }

        result.fold(
            onSuccess = { auth ->
                val playableUrl = PlaybackUrls.rewriteLoopbackForEmulator(auth.playback.url)
                sessionId = auth.sessionId
                dvrEnabled = auth.playback.dvrEnabled
                maxRewindSeconds = auth.playback.maxRewindSeconds
                isLoading = false
                playerError = null

                if (playableUrl.isNotBlank()) {
                    exoPlayer.setMediaItem(MediaItem.fromUri(playableUrl))
                    exoPlayer.prepare()
                    exoPlayer.play()
                    if (contentType == "live") {
                        startHeartbeat(sessionId, playbackRepository, heartbeatJob, scope)
                    }
                }
            },
            onFailure = { error ->
                errorMessage = error.message
                isLoading = false
            },
        )
    }

    DisposableEffect(Unit) {
        onDispose { heartbeatJob.value?.cancel() }
    }

    // --- Remote handling ---------------------------------------------------------------

    LaunchedEffect(Unit) { rootFocus.requestFocus() }

    // The control strip retires itself while the viewer is just watching (the root still holds
    // focus); moving into the controls leaves the root and cancels the countdown.
    LaunchedEffect(hudVisible, rootFocused) {
        if (hudVisible && rootFocused) {
            delay(HUD_AUTO_HIDE_MS)
            hudVisible = false
        }
    }

    // A failed authorize lands focus on the error panel's Back button, so OK alone can recover.
    LaunchedEffect(errorMessage) {
        if (errorMessage == null) return@LaunchedEffect
        withFrameNanos { } // let the button enter composition before requesting it
        runCatching { errorFocus.requestFocus() }
    }

    /** One hop of the zap list: select, record it on the queue, re-authorize via [currentId]. */
    fun tuneTo(channel: ZapChannel) {
        navigator.select(channel.id)
        LiveChannelQueue.markPlaying(channel.id)
        val nextId = channel.id.toString()
        if (currentId != nextId) currentId = nextId
    }

    /**
     * Step [delta] places (CH+ is −1, CH− is +1 — see [LiveZapNavigator.step]). When the playing
     * channel sits outside the armed list (a Sports tune), the first hop lands on the list head
     * instead of dead-ending.
     */
    fun zapBy(delta: Int) {
        val target = navigator.step(delta)
            ?: navigator.takeIf { canZap && it.playing == null }?.channelAt(0)
        if (target != null) tuneTo(target)
    }

    fun setHudVisible(visible: Boolean) {
        hudVisible = visible
        if (!visible) runCatching { rootFocus.requestFocus() }
    }

    fun closeChannelList() {
        channelListOpen = false
        runCatching { rootFocus.requestFocus() }
    }

    /**
     * Turn a choice index into decoder state. [SUBTITLE_OFF_INDEX] disables text outright; any other
     * index pins that exact track with an override, which is what makes the walk deterministic on a
     * stream that carries several subtitle languages.
     */
    fun selectSubtitle(index: Int) {
        val builder = exoPlayer.trackSelectionParameters.buildUpon()
        if (index <= SUBTITLE_OFF_INDEX) {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            builder.clearOverridesOfType(C.TRACK_TYPE_TEXT)
        } else {
            val info = subtitleTracks.getOrNull(index - 1) ?: return
            val group = currentTracks.groups.getOrNull(info.groupIndex) ?: return
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            builder.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, info.trackIndex))
        }
        exoPlayer.trackSelectionParameters = builder.build()
    }

    /** One SUBTITLE press: Off → track 1 → … → track n → Off (see [nextSubtitleIndex]). */
    fun toggleSubtitles() {
        if (canSelectSubtitles) {
            selectSubtitle(nextSubtitleIndex(subtitleActiveIndex, subtitleChoiceCount))
        }
    }

    /** Apply a resolved key. Returns true when the press was consumed by the player. */
    fun applyPlayerKey(key: PlayerKey): Boolean = when (key) {
        PlayerKey.PrevChannel -> { zapBy(-1); true }
        PlayerKey.NextChannel -> { zapBy(+1); true }
        PlayerKey.OpenChannelList -> { channelListOpen = true; true }
        PlayerKey.CloseChannelList -> { closeChannelList(); true }
        PlayerKey.ToggleHud -> { setHudVisible(!hudVisible); true }
        PlayerKey.FocusHud -> { runCatching { hudFocus.requestFocus() }; true }
        PlayerKey.Play -> { exoPlayer.play(); true }
        PlayerKey.Pause -> { exoPlayer.pause(); true }
        PlayerKey.PlayPause -> {
            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
            true
        }
        PlayerKey.ToggleSubtitles -> { toggleSubtitles(); true }
        PlayerKey.Back -> { onNavigateBack(); true }
        PlayerKey.Ignore -> false
    }

    // __PLAYER_UI__
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusable()
            .onFocusChanged { rootFocused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val native = event.nativeKeyEvent
                val key = resolvePlayerKey(
                    keyCode = native.keyCode,
                    context = PlayerKeyContext(
                        hudVisible = hudVisible,
                        channelListOpen = channelListOpen,
                        canZap = canZap,
                        rootFocused = rootFocused,
                        canSelectSubtitles = canSelectSubtitles,
                    ),
                )
                // A held channel key surfs the list; every other action fires once per press.
                val isZapKey = key == PlayerKey.PrevChannel || key == PlayerKey.NextChannel
                if (native.repeatCount > 0 && !isZapKey) return@onPreviewKeyEvent false
                applyPlayerKey(key)
            },
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false // the HUD owns the controls now
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        when {
            isLoading -> Column(
                modifier = Modifier.align(Alignment.Center).padding(Dimens.GapLarge),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dimens.GapMedium),
            ) {
                CircularProgressIndicator(color = TavunoAccent)
                Text(
                    text = "Loading…",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TavunoTheme.colors.textSecondary,
                )
            }

            errorMessage != null -> Column(
                modifier = Modifier.align(Alignment.Center).padding(Dimens.GapLarge),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dimens.GapMedium),
            ) {
                Text(
                    text = errorMessage ?: "Playback failed",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
                TavunoButton(
                    label = "Back",
                    onClick = onNavigateBack,
                    style = TavunoButtonStyle.SECONDARY,
                    modifier = Modifier.focusRequester(errorFocus),
                )
            }

            else -> {
                playerError?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(Dimens.GapLarge),
                    )
                }
                if (hudVisible) {
                    PlayerHud(
                        title = navigator.playing?.name
                            ?: (if (isLive) "Live channel" else "Now playing"),
                        position = navigator.playingPosition,
                        total = navigator.size.takeIf { it > 0 },
                        nowNext = playingChannelId?.let { nowNextById[it] },
                        isPlaying = isPlaying,
                        dvrEnabled = isLive && dvrEnabled && maxRewindSeconds > 0,
                        canZap = canZap,
                        canSelectSubtitles = canSelectSubtitles,
                        subtitlesOn = subtitleActiveIndex > SUBTITLE_OFF_INDEX,
                        activeSubtitleLabel = activeSubtitleLabel,
                        onBack = onNavigateBack,
                        onTogglePlay = {
                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                        },
                        onStep = { delta -> zapBy(delta) },
                        onRewind = { millis ->
                            exoPlayer.seekTo((exoPlayer.currentPosition - millis).coerceAtLeast(0L))
                        },
                        onOpenChannels = { channelListOpen = true },
                        onToggleSubtitles = { toggleSubtitles() },
                        firstControlFocus = hudFocus,
                    )
                }
                if (channelListOpen && navigator.size > 0) {
                    PlayerChannelOverlay(
                        channels = navigator.list,
                        playingChannelId = navigator.playing?.id ?: playingChannelId,
                        nowNext = nowNextById,
                        onSelect = { channel ->
                            tuneTo(channel)
                            closeChannelList()
                        },
                        modifier = Modifier.align(Alignment.CenterStart),
                    )
                }
            }
        }
    }
}

/** Repeating live-session heartbeat; [heartbeatJob] replaces any previous one. */
private fun startHeartbeat(
    sessionId: Int?,
    playbackRepository: PlaybackRepository,
    heartbeatJob: MutableState<Job?>,
    scope: CoroutineScope,
) {
    sessionId ?: return
    heartbeatJob.value?.cancel()
    heartbeatJob.value = scope.launch {
        while (true) {
            delay(30_000)
            playbackRepository.sendHeartbeat(sessionId)
        }
    }
}
