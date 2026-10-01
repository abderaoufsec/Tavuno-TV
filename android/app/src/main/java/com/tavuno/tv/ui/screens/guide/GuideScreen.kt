package com.tavuno.tv.ui.screens.guide

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.tavuno.tv.core.LiveChannelQueue
import com.tavuno.tv.data.model.EpgProgram
import com.tavuno.tv.data.model.EpgWindow
import com.tavuno.tv.data.model.GuideChannel
import com.tavuno.tv.data.repository.CatalogRepository
import com.tavuno.tv.playback.toZapChannel
import com.tavuno.tv.ui.components.ChannelLogoPlate
import com.tavuno.tv.ui.components.EmptyState
import com.tavuno.tv.ui.components.ErrorState
import com.tavuno.tv.ui.components.FocusableSurface
import com.tavuno.tv.ui.components.LoadingState
import com.tavuno.tv.ui.components.TavunoButton
import com.tavuno.tv.ui.components.TavunoButtonStyle
import com.tavuno.tv.ui.screens.live.EpgTimeFormat
import com.tavuno.tv.ui.theme.Dimens
import com.tavuno.tv.ui.theme.TavunoTheme
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

/** Row height: two lines of programme text plus the gap that separates it from its neighbour. */
private val RowHeight = 84.dp

/** Height of the fixed time-axis strip above the grid. */
private val AxisHeight = 46.dp

/** The channel column stays put while the grid scrolls under it. */
private val ChannelColumnWidth = 190.dp

/** Breathing room between two programme cells, drawn as padding rather than a gap in the maths. */
private val CellInset = 3.dp

/** The focused programme's details, shown above the grid. */
private val DetailHeight = 92.dp

/** Programme cells are wide and short; a full card corner reads as a pill, so they get a small one. */
private val CellCorner = 8.dp

/** What the D-pad is currently sitting on: a programme cell, or just a channel's row. */
data class FocusedCell(val channel: GuideChannel, val program: EpgProgram?)
/**
 * The Guide destination (Slice C): every channel's programmes for one time window, as a grid.
 *
 * The whole window comes back in one `GET /v1/epg/window` call — one request per screen, not
 * one per row — and all of the geometry lives in [GuideGrid], which is pure and unit-tested.
 *
 * Navigation is deliberately native TV rather than touch: LEFT/RIGHT walk the time axis,
 * UP/DOWN walk channels, and OK tunes to whatever is focused. Two behaviours are worth calling
 * out because they are what makes a grid usable from a sofa:
 *  - **focus follows scroll.** Compose 1.5 does not scroll focused items into view, so the grid
 *    drives both scroll positions from the focused cell itself.
 *  - **the channel column is focusable too.** A channel with no synced EPG has no programme
 *    cell to land on, so it would be unreachable; its row doubles as a one-press tune target.
 */
@Composable
fun GuideScreen(
    catalogRepository: CatalogRepository,
    onNavigateToPlayer: (Int) -> Unit,
) {
    val zone = remember { ZoneId.systemDefault() }
    // Anchored once, at entry: re-deriving the window on every recomposition would slide the
    // grid out from under the viewer mid-browse.
    val windowStart = remember { GuideGrid.windowStart(Instant.now()) }
    val windowEnd = remember(windowStart) { GuideGrid.windowEnd(windowStart) }

    var window by remember { mutableStateOf<EpgWindow?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }
    var focused by remember { mutableStateOf<FocusedCell?>(null) }

    LaunchedEffect(windowStart, reloadKey) {
        isLoading = true
        errorMessage = null
        catalogRepository.getEpgWindow(windowStart, windowEnd).fold(
            onSuccess = { loaded ->
                window = loaded
                isLoading = false
            },
            onFailure = { error ->
                errorMessage = error.message ?: "Failed to load the program guide"
                isLoading = false
            },
        )
    }

    val channels = window?.channels.orEmpty()

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Dimens.GapSmall),
    ) {
        GuideHeader(
            windowStart = windowStart,
            windowEnd = windowEnd,
            channelCount = channels.size,
            zone = zone,
            onRefresh = { reloadKey++ },
        )

        GuideDetailStrip(focused = focused, zone = zone)

        when {
            isLoading -> LoadingState("Loading the guide...")

            errorMessage != null -> ErrorState(
                message = errorMessage!!,
                onRetry = { reloadKey++ },
            )

            channels.isEmpty() -> EmptyState(
                title = "No guide data",
                message = "Nothing is scheduled for this window. Try again in a few minutes.",
            )

            else -> GuideGridBody(
                channels = channels,
                windowStart = windowStart,
                windowEnd = windowEnd,
                onTuneToChannel = { channel ->
                    // Arm the player's zap list with the guide's own channels, exactly as Live TV
                    // does, so CH+/- works after tuning from here.
                    LiveChannelQueue.publish(
                        channels = channels.map { it.toZapChannel() },
                        currentChannelId = channel.id,
                    )
                    onNavigateToPlayer(channel.id)
                },
                onFocusChange = { focused = it },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Window range, channel count and a Refresh action for the window currently on screen. */
@Composable
private fun GuideHeader(
    windowStart: Instant,
    windowEnd: Instant,
    channelCount: Int,
    zone: ZoneId,
    onRefresh: () -> Unit,
) {
    val colors = TavunoTheme.colors
    val startLabel = EpgTimeFormat.clockLabel(windowStart.toString(), zone).orEmpty()
    val endLabel = EpgTimeFormat.clockLabel(windowEnd.toString(), zone).orEmpty()
    // A window whose end label sorts before its start label has rolled past midnight.
    val crossesMidnight = startLabel.take(2) > endLabel.take(2)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (crossesMidnight) "$startLabel - $endLabel  (tomorrow)" else "$startLabel - $endLabel",
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(Dimens.GapMedium))
        Text(
            text = "$channelCount channels",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            maxLines = 1,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "◀ ▶ time    ▲ ▼ channel    OK watch",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            maxLines = 1,
        )
        Spacer(Modifier.width(Dimens.GapMedium))
        TavunoButton(
            label = "Refresh",
            onClick = onRefresh,
            style = TavunoButtonStyle.SECONDARY,
            compact = true,
        )
    }
}

/**
 * Details of the focused cell.
 *
 * Always rendered — a panel that appeared only on focus would make the grid jump every time
 * the viewer moved off a programme — and it falls back to a hint until something is focused.
 */
@Composable
private fun GuideDetailStrip(focused: FocusedCell?, zone: ZoneId) {
    val colors = TavunoTheme.colors
    val channel = focused?.channel
    val program = focused?.program

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(DetailHeight)
            .clip(RoundedCornerShape(Dimens.CornerMedium))
            .background(colors.surfaceContainerLow)
            .padding(Dimens.GapMedium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (channel == null) {
            Text(
                text = "Pick a channel on the left, or a programme to see its details.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                maxLines = 2,
            )
            return@Row
        }

        ChannelLogoPlate(
            channelName = channel.name,
            logoUrl = channel.logo,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.width(Dimens.GapMedium))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = channel.name,
                style = MaterialTheme.typography.labelLarge,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (program == null) {
                Text(
                    text = "No guide data for this channel",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(
                    text = program.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = program.description ?: "No description available",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (program != null) {
            Spacer(Modifier.width(Dimens.GapMedium))
            Text(
                text = EpgTimeFormat.rangeLabel(program.startsAt, program.endsAt, zone).orEmpty(),
                style = MaterialTheme.typography.labelLarge,
                color = colors.primary,
                maxLines = 1,
            )
        }
    }
}

/**
 * The scrolling grid: a fixed channel column on the left, a scrolling time area on the right.
 *
 * Both scroll axes are *shared* state — the channel column rides the grid's vertical scroll and
 * the axis header rides its horizontal one — which is what makes three separate boxes read as
 * one continuous surface instead of three panes that drift apart.
 */
@Composable
private fun GuideGridBody(
    channels: List<GuideChannel>,
    windowStart: Instant,
    windowEnd: Instant,
    onTuneToChannel: (GuideChannel) -> Unit,
    onFocusChange: (FocusedCell) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TavunoTheme.colors
    val density = LocalDensity.current
    val horizontal = rememberScrollState()
    val vertical = rememberScrollState()
    val totalMinutes = GuideGrid.totalMinutes(windowStart, windowEnd)
    val contentWidth = (totalMinutes * GuideGrid.DP_PER_MINUTE).dp
    val now = remember { Instant.now() }
    val nowMinute = GuideGrid.minutesFromWindowStart(windowStart, now)
    val zone = remember { ZoneId.systemDefault() }
    val slotLabels = remember(windowStart, windowEnd) { GuideGrid.slotLabels(windowStart, windowEnd, zone = zone) }

    var focusedRow by remember { mutableStateOf(0) }
    var focusedStartMinute by remember { mutableStateOf<Long?>(null) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val viewportWidth = maxWidth
        val viewportHeight = maxHeight

        // Compose 1.5 does not bring a focused item into view by itself, so the grid does it:
        // the focused row is centred vertically and the focused programme sits about a third
        // in from the left, which keeps the upcoming programmes visible while you read.
        LaunchedEffect(focusedRow, viewportHeight) {
            val targetDp = focusedRow * RowHeight.value -
                viewportHeight.value / 2f + RowHeight.value / 2f
            val target = with(density) { targetDp.dp.toPx() }.roundToInt()
            vertical.animateScrollTo(target.coerceIn(0, vertical.maxValue))
        }
        LaunchedEffect(focusedStartMinute, viewportWidth) {
            val minute = focusedStartMinute ?: return@LaunchedEffect
            val targetDp = minute * GuideGrid.DP_PER_MINUTE - viewportWidth.value * 0.3f
            val target = with(density) { targetDp.dp.toPx() }.roundToInt()
            horizontal.animateScrollTo(target.coerceIn(0, horizontal.maxValue))
        }
        // Open on the present, not on midnight: the axis starts a couple of dp before "now".
        // The first pass waits for measurement, because maxValue is 0 until the grid is laid out.
        LaunchedEffect(windowStart, channels.size) {
            val maxValue = snapshotFlow { horizontal.maxValue }.first { it > 0 }
            val targetDp = nowMinute * GuideGrid.DP_PER_MINUTE - 24
            val target = with(density) { targetDp.dp.toPx() }.roundToInt()
            horizontal.scrollTo(target.coerceIn(0, maxValue))
        }

        Row(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .width(ChannelColumnWidth)
                    .fillMaxHeight()
                    .verticalScroll(vertical),
            ) {
                // Corner cell, sized to the axis strip so the two header rows line up.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(AxisHeight)
                        .padding(start = Dimens.GapMedium),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = "Channels",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary,
                    )
                }
                channels.forEachIndexed { index, channel ->
                    ChannelCell(
                        channel = channel,
                        onClick = { onTuneToChannel(channel) },
                        modifier = Modifier
                            .width(ChannelColumnWidth)
                            .height(RowHeight)
                            .onFocusChanged { state ->
                                if (state.isFocused) {
                                    focusedRow = index
                                    onFocusChange(FocusedCell(channel, null))
                                }
                            },
                    )
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    TimeAxis(
                        labels = slotLabels,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(AxisHeight)
                            .horizontalScroll(horizontal),
                    )
                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(vertical)
                                .horizontalScroll(horizontal),
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(contentWidth)
                                    .height(RowHeight * channels.size),
                            ) {
                                channels.forEachIndexed { index, channel ->
                                    GuideRow(
                                        channel = channel,
                                        windowStart = windowStart,
                                        windowEnd = windowEnd,
                                        now = now,
                                        zone = zone,
                                        onTune = { onTuneToChannel(channel) },
                                        onFocus = { slot ->
                                            focusedRow = index
                                            focusedStartMinute = slot.startMinute
                                            onFocusChange(FocusedCell(channel, slot.program))
                                        },
                                        modifier = Modifier.offset(y = RowHeight * index),
                                    )
                                }
                                if (nowMinute in 0..totalMinutes) {
                                    NowLine(
                                        nowMinute = nowMinute,
                                        modifier = Modifier.matchParentSize(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One channel's programmes, positioned along the time axis inside a single row. */
@Composable
private fun GuideRow(
    channel: GuideChannel,
    windowStart: Instant,
    windowEnd: Instant,
    now: Instant,
    zone: ZoneId,
    onTune: () -> Unit,
    onFocus: (GuideGrid.PlacedProgramme) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TavunoTheme.colors
    val placed = remember(channel, windowStart, windowEnd) {
        GuideGrid.placeProgrammes(channel, windowStart, windowEnd)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(RowHeight),
    ) {
        if (placed.isEmpty()) {
            // The row still exists: a channel with no synced EPG must not silently vanish.
            Text(
                text = "No guide data",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = Dimens.GapMedium),
            )
        }
        placed.forEach { slot ->
            FocusableSurface(
                onClick = onTune,
                modifier = Modifier
                    .offset(x = (slot.startMinute * GuideGrid.DP_PER_MINUTE).dp + CellInset)
                    .width((slot.durationMinutes * GuideGrid.DP_PER_MINUTE).dp - CellInset * 2)
                    .height(RowHeight - CellInset * 2)
                    .onFocusChanged { state -> if (state.isFocused) onFocus(slot) },
                shape = RoundedCornerShape(CellCorner),
                // Deliberately flat: the grid is a ruler, and a cell that grows under focus
                // would push its neighbours out of alignment with the time axis.
                focusedScale = 1f,
                glowElevation = 0,
                unfocusedContainerColor = colors.surfaceContainerLow,
                focusedContainerColor = colors.surfaceContainerHigh,
                contentAlignment = Alignment.TopStart,
            ) { focused ->
                ProgrammeCell(
                    program = slot.program,
                    isLive = slot.isLiveAt(now),
                    zone = zone,
                    focused = focused,
                )
            }
        }
    }
}

/** Title, time range and the "on now" badge for one programme cell. */
@Composable
private fun ProgrammeCell(
    program: EpgProgram,
    isLive: Boolean,
    zone: ZoneId,
    focused: Boolean,
) {
    val colors = TavunoTheme.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.GapSmall),
        contentAlignment = Alignment.TopStart,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = program.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (focused) colors.primary else colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = EpgTimeFormat.rangeLabel(program.startsAt, program.endsAt, zone).orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isLive) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(CircleShape)
                    .background(colors.primary)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "NOW",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onPrimary,
                )
            }
        }
    }
}
/**
 * A channel's fixed left-hand cell.
 *
 * Focusable in its own right: it is the only reachable target for a channel whose EPG has not
 * synced (that row has no programme cell), and it doubles as a one-press tune from anywhere in
 * the grid. It also gives LEFT a sensible destination from the first programme cell.
 */
@Composable
private fun ChannelCell(
    channel: GuideChannel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TavunoTheme.colors
    FocusableSurface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(CellCorner),
        focusedScale = 1f,
        glowElevation = 0,
        unfocusedContainerColor = colors.surfaceContainerLow,
        focusedContainerColor = colors.surfaceContainerHigh,
        contentAlignment = Alignment.CenterStart,
    ) { focused ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(Dimens.GapSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChannelLogoPlate(
                channelName = channel.name,
                logoUrl = channel.logo,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.width(Dimens.GapSmall))
            Text(
                text = channel.name,
                style = MaterialTheme.typography.titleSmall,
                color = if (focused) colors.primary else colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The time axis: one label per slot, scrolling horizontally with the grid it labels.
 *
 * Even slots are drawn in the full text colour because the window always starts on a round half
 * hour, so every other label is the top of an hour.
 */
@Composable
private fun TimeAxis(
    labels: List<String>,
    modifier: Modifier = Modifier,
) {
    val colors = TavunoTheme.colors
    Row(modifier = modifier) {
        labels.forEachIndexed { index, label ->
            Box(
                modifier = Modifier
                    .width((GuideGrid.SLOT_MINUTES * GuideGrid.DP_PER_MINUTE).dp)
                    .fillMaxHeight()
                    .padding(start = if (index == 0) 0.dp else Dimens.GapSmall),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (index % 2 == 0) colors.textPrimary else colors.textSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * The "you are here" line down the grid.
 *
 * Drawn inside the scrolling content rather than over the viewport, so it scrolls with the axis
 * it belongs to. A Canvas is not focusable and takes no pointer input, so it never steals the
 * D-pad from the cells underneath it.
 */
@Composable
private fun NowLine(
    nowMinute: Long,
    modifier: Modifier = Modifier,
) {
    val colors = TavunoTheme.colors
    Canvas(modifier = modifier) {
        val x = nowMinute * GuideGrid.DP_PER_MINUTE * density
        drawLine(
            color = colors.accent,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = 2.5.dp.toPx(),
        )
        drawCircle(
            color = colors.accent,
            radius = 4.dp.toPx(),
            center = Offset(x, 5.dp.toPx()),
        )
    }
}