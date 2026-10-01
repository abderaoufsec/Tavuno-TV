package com.tavuno.tv.ui.screens.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.tavuno.tv.data.model.ChannelNowNext
import com.tavuno.tv.playback.ZapChannel
import com.tavuno.tv.ui.components.ChannelLogoPlate
import com.tavuno.tv.ui.components.FocusableSurface
import com.tavuno.tv.ui.components.roundedPanel
import com.tavuno.tv.ui.screens.live.EpgTimeFormat
import com.tavuno.tv.ui.theme.Dimens
import com.tavuno.tv.ui.theme.TavunoTheme

/**
 * The in-player channel list — D-pad LEFT slides this in over the video.
 *
 * This is Tavuno's answer to OwnTV's zap-list overlay: same job (see the whole armed list, jump
 * anywhere, keep watching), same panel geometry ([Dimens.ChannelListWidth], the token ported for
 * exactly this), but re-targeted. OwnTV's overlay reads Room entities and hands them to libmpv;
 * this reads the [ZapChannel] list Live TV published and hands the chosen id back through
 * `onSelect`, so the player keeps owning the Media3 instance.
 *
 * Focus enters on the playing row rather than the top of the list, so the viewer lands where they
 * already are — the single most important property of a zap list on a real TV.
 */
@Composable
internal fun PlayerChannelOverlay(
    channels: List<ZapChannel>,
    playingChannelId: Int?,
    nowNext: Map<Int, ChannelNowNext>,
    onSelect: (ZapChannel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TavunoTheme.colors
    val playingIndex = channels.indexOfFirst { it.id == playingChannelId }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = playingIndex)
    val playingRowFocus = remember { FocusRequester() }

    LaunchedEffect(playingChannelId) {
        // A FocusRequester only resolves once its node is placed, and the LazyColumn places rows
        // during the first layout pass of this frame — so wait one frame before asking.
        withFrameNanos { }
        runCatching { playingRowFocus.requestFocus() }
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(Dimens.ChannelListWidth)
            .roundedPanel(fillColor = colors.previewPanelFill),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(Dimens.GapLarge)) {
            Text(
                text = "Channels",
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${channels.size} in this list",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 1,
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(
                start = Dimens.GapSmall,
                end = Dimens.GapSmall,
                bottom = Dimens.GapMedium,
            ),
            verticalArrangement = Arrangement.spacedBy(Dimens.GapTiny),
        ) {
            itemsIndexed(channels, key = { _, channel -> channel.id }) { index, channel ->
                ChannelZapRow(
                    channel = channel,
                    position = index + 1,
                    playing = channel.id == playingChannelId,
                    nowNext = nowNext[channel.id],
                    onSelect = onSelect,
                    modifier = if (index == playingIndex) {
                        Modifier.focusRequester(playingRowFocus)
                    } else {
                        Modifier
                    },
                )
            }
        }

        Text(
            text = "OK to watch  •  ◀ ▶ or BACK to close",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
            modifier = Modifier.padding(horizontal = Dimens.GapLarge, vertical = Dimens.GapMedium),
        )
    }
}

/**
 * One zap-list row: position, logo plate, name, the NOW line, and an ON AIR marker.
 *
 * Rendered through [FocusableSurface] like every other remote-navigable surface in the app, so the
 * focus ring inside the player is the same ring the rest of the shell uses.
 */
@Composable
private fun ChannelZapRow(
    channel: ZapChannel,
    position: Int,
    playing: Boolean,
    nowNext: ChannelNowNext?,
    onSelect: (ZapChannel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TavunoTheme.colors
    FocusableSurface(
        onClick = { onSelect(channel) },
        modifier = modifier.fillMaxWidth().height(72.dp),
        selected = playing,
        shape = RoundedCornerShape(Dimens.CornerMedium),
        unfocusedContainerColor = if (playing) colors.card else colors.previewPanelFill,
        selectedContainerColor = colors.card,
        focusedContainerColor = colors.card,
        contentAlignment = Alignment.CenterStart,
    ) { focused ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.GapMedium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = position.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = if (playing || focused) colors.primary else colors.textSecondary,
                modifier = Modifier.width(28.dp),
            )
            ChannelLogoPlate(
                channelName = channel.name,
                logoUrl = channel.logoUrl,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.width(Dimens.GapMedium))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (playing || focused) colors.textPrimary else colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                NowNextSummary(nowNext)
            }
            if (playing) {
                Text(
                    text = "ON AIR",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.primary,
                )
            }
        }
    }
}

/** One compact EPG line for a zap-list row; renders nothing when the channel has no guide data. */
@Composable
private fun NowNextSummary(nowNext: ChannelNowNext?) {
    val program = nowNext?.now ?: return
    val timeRange = EpgTimeFormat.rangeLabel(program.startsAt, program.endsAt)
    Text(
        text = if (timeRange != null) "$timeRange  •  ${program.title}" else program.title,
        style = MaterialTheme.typography.bodySmall,
        color = TavunoTheme.colors.textSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}