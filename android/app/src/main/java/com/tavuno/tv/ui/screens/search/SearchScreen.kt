package com.tavuno.tv.ui.screens.search

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.tavuno.tv.core.LiveChannelQueue
import com.tavuno.tv.data.model.Channel
import com.tavuno.tv.data.model.SearchResults
import com.tavuno.tv.data.repository.CatalogRepository
import com.tavuno.tv.playback.toZapChannel
import com.tavuno.tv.ui.components.ChannelLogoTile
import com.tavuno.tv.ui.components.EmptyState
import com.tavuno.tv.ui.components.ErrorState
import com.tavuno.tv.ui.components.LoadingState
import com.tavuno.tv.ui.components.PosterCard
import com.tavuno.tv.ui.components.roundedPanel
import com.tavuno.tv.ui.theme.Dimens
import com.tavuno.tv.ui.theme.TavunoTheme
import kotlinx.coroutines.delay

/** Quiet typing required before the network runs — cancelled by the next keystroke. */
private const val SEARCH_DEBOUNCE_MS = 350L

/** Per-group cap sent to `GET /v1/search`; a TV result row never needs more. */
private const val SEARCH_LIMIT = 24

/**
 * The Search destination (Slice B of the OwnTV parity port).
 *
 * Deliberately button-free: the field is focused on entry, the system IME does the input, and a
 * debounced [CatalogRepository.search] re-runs as the viewer types. Results render as three
 * labelled rows — channels play immediately (arming [LiveChannelQueue] exactly like Live TV, so
 * zapping works straight out of a search hit), while movies and series push their detail routes.
 */
@Composable
fun SearchScreen(
    catalogRepository: CatalogRepository,
    onNavigateToPlayer: (String, Int) -> Unit,
    onNavigateToMovieDetails: (Int) -> Unit,
    onNavigateToSeriesDetails: (Int) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<SearchResults?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    // Bumped by Retry so a failed search can re-run without editing the query.
    var reloadKey by remember { mutableStateOf(0) }
    val fieldFocus = remember { FocusRequester() }

    // Debounced auto-search. The effect restarts on every keystroke, so the delay *is* the
    // debounce: only `SEARCH_DEBOUNCE_MS` of quiet typing reaches the network, and an in-flight
    // call is cancelled when the term changes again.
    LaunchedEffect(query, reloadKey) {
        val term = query.trim()
        if (term.isEmpty()) {
            results = null
            errorMessage = null
            isLoading = false
            return@LaunchedEffect
        }
        delay(SEARCH_DEBOUNCE_MS)
        isLoading = true
        val outcome = catalogRepository.search(term, limit = SEARCH_LIMIT)
        isLoading = false
        outcome.fold(
            onSuccess = { loaded ->
                results = loaded
                errorMessage = null
            },
            onFailure = { error ->
                errorMessage = error.message ?: "Search failed"
            },
        )
    }

    // Open on the field: on a TV, "go to Search and type" is the whole interaction.
    LaunchedEffect(Unit) { fieldFocus.requestFocus() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.GapSmall),
        verticalArrangement = Arrangement.spacedBy(Dimens.GapMedium),
    ) {
        SearchField(
            query = query,
            onQueryChange = { query = it },
            focusRequester = fieldFocus,
        )

        when {
            isLoading -> LoadingState(
                message = "Searching…",
                modifier = Modifier.fillMaxWidth().padding(top = Dimens.GapLarge),
            )

            errorMessage != null -> ErrorState(
                message = errorMessage!!,
                onRetry = { reloadKey++ },
            )

            results == null -> EmptyState("Type to search channels, movies and series")

            else -> {
                val found = results!!
                SearchSections(
                    results = found,
                    onOpenChannel = { channel ->
                        // Arm the zap list exactly as Live TV does, so the player can surf from here.
                        LiveChannelQueue.publish(
                            channels = found.channels.map { it.toZapChannel() },
                            currentChannelId = channel.id,
                        )
                        onNavigateToPlayer("live", channel.id)
                    },
                    onOpenMovie = onNavigateToMovieDetails,
                    onOpenSeries = onNavigateToSeriesDetails,
                )
            }
        }
    }
}

/** The three labelled result rows; collapses to an empty state when nothing matched. */
@Composable
private fun SearchSections(
    results: SearchResults,
    onOpenChannel: (Channel) -> Unit,
    onOpenMovie: (Int) -> Unit,
    onOpenSeries: (Int) -> Unit,
) {
    if (results.channels.isEmpty() && results.movies.isEmpty() && results.series.isEmpty()) {
        EmptyState("No results for \u201C${results.query}\u201D")
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(Dimens.GapLarge)) {
        if (results.channels.isNotEmpty()) {
            ResultSection(title = "Channels", count = results.channels.size) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.GapMedium),
                    contentPadding = PaddingValues(horizontal = Dimens.GapTiny),
                ) {
                    items(results.channels, key = { "channel-${it.id}" }) { channel ->
                        ChannelLogoTile(
                            channelName = channel.name,
                            logoUrl = channel.logo,
                            modifier = Modifier.width(150.dp),
                            onClick = { onOpenChannel(channel) },
                        )
                    }
                }
            }
        }
        if (results.movies.isNotEmpty()) {
            ResultSection(title = "Movies", count = results.movies.size) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.GapMedium),
                    contentPadding = PaddingValues(horizontal = Dimens.GapTiny),
                ) {
                    items(results.movies, key = { "movie-${it.id}" }) { movie ->
                        PosterCard(
                            title = movie.title,
                            posterUrl = movie.poster,
                            subtitle = movie.releaseYear?.toString(),
                            onClick = { onOpenMovie(movie.id) },
                        )
                    }
                }
            }
        }
        if (results.series.isNotEmpty()) {
            ResultSection(title = "Series", count = results.series.size) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.GapMedium),
                    contentPadding = PaddingValues(horizontal = Dimens.GapTiny),
                ) {
                    items(results.series, key = { "series-${it.id}" }) { series ->
                        PosterCard(
                            title = series.title,
                            posterUrl = series.poster,
                            onClick = { onOpenSeries(series.id) },
                        )
                    }
                }
            }
        }
    }
}

/** Section heading for one result group: "Channels  12" above its row. */
@Composable
private fun ResultSection(
    title: String,
    count: Int,
    content: @Composable () -> Unit,
) {
    val colors = TavunoTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.GapSmall)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.GapSmall)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = colors.textSecondary,
            )
        }
        content()
    }
}

/**
 * The query box: a real [BasicTextField] so the system IME drives input, dressed as a rounded
 * panel with the search glyph. Focus draws the shared focus-border tone so it reads as the
 * active control from sofa distance.
 */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    focusRequester: FocusRequester,
) {
    val colors = TavunoTheme.colors
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { state -> focused = state.isFocused },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary),
        cursorBrush = SolidColor(colors.primary),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .roundedPanel(radius = Dimens.CornerMedium)
                    .then(
                        if (focused) {
                            Modifier.border(
                                width = Dimens.FocusBorderWidth,
                                color = colors.focusBorder,
                                shape = RoundedCornerShape(Dimens.CornerMedium),
                            )
                        } else {
                            Modifier
                        },
                    )
                    .padding(horizontal = Dimens.GapMedium),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.GapSmall),
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = if (focused) colors.primary else colors.textSecondary,
                    modifier = Modifier.size(24.dp),
                )
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = "Search channels, movies and series",
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.textSecondary,
                        )
                    }
                    innerTextField()
                }
            }
        },
    )
}
