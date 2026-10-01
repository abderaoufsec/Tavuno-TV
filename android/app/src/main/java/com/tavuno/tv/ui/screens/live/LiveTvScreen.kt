package com.tavuno.tv.ui.screens.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.tavuno.tv.core.LiveChannelQueue
import com.tavuno.tv.data.model.Category
import com.tavuno.tv.data.model.Channel
import com.tavuno.tv.data.model.ChannelNowNext
import com.tavuno.tv.data.model.EpgProgram
import com.tavuno.tv.data.repository.CatalogRepository
import com.tavuno.tv.playback.toZapChannel
import com.tavuno.tv.ui.components.ChannelLogoPlate
import com.tavuno.tv.ui.components.EmptyState
import com.tavuno.tv.ui.components.ErrorState
import com.tavuno.tv.ui.components.FocusableSurface
import com.tavuno.tv.ui.components.LoadingState
import com.tavuno.tv.ui.components.TavunoButton
import com.tavuno.tv.ui.components.TavunoButtonStyle
import com.tavuno.tv.ui.theme.Dimens
import com.tavuno.tv.ui.theme.TavunoTheme
import kotlinx.coroutines.delay

/** How often visible rows re-read now/next; mirrors the API's 60s cache window. */
private const val EPG_REFRESH_INTERVAL_MS = 60_000L

/**
 * The Live TV destination, rebuilt on the design system.
 *
 * The shell owns the chrome (TopBar title, sidebar, system BACK via
 * [com.tavuno.tv.ui.navigation.TavunoMainScreen]), so this body starts at the category filter —
 * there is no Back affordance here, mirroring [com.tavuno.tv.ui.screens.movies.MoviesScreen].
 *
 * Channel rows render through [FocusableSurface] so the focus ring/glow matches every other
 * screen; NOW/NEXT EPG is fetched lazily per visible row and re-polled on the API's 60s cache
 * window.
 */
@Composable
fun LiveTvScreen(
    catalogRepository: CatalogRepository,
    onNavigateToPlayer: (Int) -> Unit,
) {
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var channelNowNextMap by remember { mutableStateOf<Map<Int, ChannelNowNext>>(emptyMap()) }
    // Bumped by Retry to re-run both load effects.
    var reloadKey by remember { mutableStateOf(0) }

    // Categories feed the chip row. Failure is non-fatal: the row degrades to "All" and the
    // channel list (loaded below) still works.
    LaunchedEffect(reloadKey) {
        catalogRepository.getCategories(kind = "live").fold(
            onSuccess = { loadedCategories -> categories = loadedCategories },
            onFailure = { /* non-fatal: filters degrade to "All" */ },
        )
    }

    // Channels follow the selected category and the initial/retry load. Driven from
    // LaunchedEffect (main dispatcher) — writing Compose state off the main thread is fatal.
    LaunchedEffect(reloadKey, selectedCategory) {
        isLoading = true
        errorMessage = null
        catalogRepository.getChannels(categoryId = selectedCategory?.id).fold(
            onSuccess = { loadedChannels ->
                channels = loadedChannels
                isLoading = false
            },
            onFailure = { error ->
                errorMessage = error.message ?: "Failed to load channels"
                isLoading = false
            },
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
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Dimens.GapMedium),
    ) {
        // Category filter. Kept horizontally scrollable: a plain Row squeezes overflowing
        // chips to zero width, their labels wrap onto many lines and the row ends up eating
        // the whole screen, leaving no room for the channel list below.
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.GapSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            item(key = "all") {
                TavunoButton(
                    label = "All",
                    onClick = { selectedCategory = null },
                    selected = selectedCategory == null,
                    style = TavunoButtonStyle.SECONDARY,
                    compact = true,
                )
            }
            items(categories, key = { it.id }) { category ->
                TavunoButton(
                    label = category.name,
                    onClick = { selectedCategory = category },
                    selected = selectedCategory?.id == category.id,
                    style = TavunoButtonStyle.SECONDARY,
                    compact = true,
                )
            }
        }

        when {
            isLoading -> LoadingState("Loading channels...")

            errorMessage != null -> ErrorState(
                message = errorMessage!!,
                onRetry = { reloadKey++ },
            )

            channels.isEmpty() -> EmptyState("No channels available")

            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Dimens.GapSmall),
            ) {
                items(channels, key = { it.id }) { channel ->
                    // Fetch lazily per visible row: avoids firing one request per
                    // channel at once, and refreshes when the tick advances.
                    LaunchedEffect(channel.id, epgRefreshTick) {
                        catalogRepository.getChannelNowNext(channel.id).fold(
                            onSuccess = { loaded ->
                                channelNowNextMap = channelNowNextMap + (channel.id to loaded)
                            },
                            onFailure = {
                                // Per-channel EPG failures are non-fatal: keep the list usable.
                            },
                        )
                    }
                    ChannelRow(
                        channel = channel,
                        nowNext = channelNowNextMap[channel.id],
                        onClick = {
                            // Arm the player's zap list with what the viewer is browsing, then tune.
                            LiveChannelQueue.publish(
                                channels = channels.map { it.toZapChannel() },
                                currentChannelId = channel.id,
                            )
                            onNavigateToPlayer(channel.id)
                        },
                    )
                }
            }
        }
    }
}

/**
 * A live channel row: logo plate, name and the NOW/NEXT EPG lines, with a play affordance.
 * Rebuilt on [FocusableSurface] so the focus ring matches the rest of the app.
 */
@Composable
private fun ChannelRow(
    channel: Channel,
    nowNext: ChannelNowNext?,
    onClick: () -> Unit,
) {
    val colors = TavunoTheme.colors
    FocusableSurface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(104.dp),
        unfocusedContainerColor = colors.card,
        contentAlignment = Alignment.CenterStart,
    ) { focused ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Dimens.GapMedium, vertical = Dimens.GapSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChannelLogoPlate(
                channelName = channel.name,
                logoUrl = channel.logo,
                modifier = Modifier.size(76.dp),
            )
            Spacer(Modifier.width(Dimens.GapMedium))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                NowNextLine(prefix = "NOW", program = nowNext?.now)
                NowNextLine(prefix = "NEXT", program = nowNext?.next)
            }
            Text(
                text = "▶",
                style = MaterialTheme.typography.headlineSmall,
                color = if (focused) colors.primary else colors.primary.copy(alpha = 0.6f),
            )
        }
    }
}

/** Renders "NOW  14:10 - 14:15  •  Programme title" style EPG lines. */
@Composable
private fun NowNextLine(
    prefix: String,
    program: EpgProgram?,
) {
    if (program == null) return

    val timeRange = EpgTimeFormat.rangeLabel(program.startsAt, program.endsAt)
    Text(
        text = if (timeRange != null) {
            "$prefix  $timeRange  •  ${program.title}"
        } else {
            "$prefix  •  ${program.title}"
        },
        style = MaterialTheme.typography.bodySmall,
        color = TavunoTheme.colors.textSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}