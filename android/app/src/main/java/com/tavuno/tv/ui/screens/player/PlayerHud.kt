package com.tavuno.tv.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.tavuno.tv.data.model.ChannelNowNext
import com.tavuno.tv.ui.components.TavunoButton
import com.tavuno.tv.ui.components.TavunoButtonStyle
import com.tavuno.tv.ui.screens.live.EpgTimeFormat
import com.tavuno.tv.ui.theme.Dimens
import com.tavuno.tv.ui.theme.TavunoTheme

/**
 * The player's own controls, replacing Media3's `PlayerView` controller.
 *
 * Two reasons this is hand-built rather than `useController = true`:
 *  1. Media3's controller is touch-first — its timeline bar is unusable at sofa distance, and it
 *     draws M3 phone widgets, not the Tavuno design system.
 *  2. The controller consumes D-pad LEFT/RIGHT to seek, which is exactly the key this player needs
 *     for the channel list. Owning the HUD means [resolvePlayerKey] can define the whole key map.
 *
 * The HUD owns no state: every button is a callback, and the *first* control carries the
 * [firstControlFocus] requester so the screen can honour `PlayerKey.FocusHud` (the second OK press)
 * without the HUD knowing anything about key handling.
 */
@Composable
internal fun PlayerHud(
    title: String,
    position: Int?,
    total: Int?,
    nowNext: ChannelNowNext?,
    isPlaying: Boolean,
    dvrEnabled: Boolean,
    canZap: Boolean,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onStep: (Int) -> Unit,
    onRewind: (Long) -> Unit,
    onOpenChannels: () -> Unit,
    firstControlFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                // A single scrim that darkens only the strips the chrome sits on, so the picture
                // between them stays untouched.
                Brush.verticalGradient(
                    0.0f to Color.Black.copy(alpha = 0.72f),
                    0.28f to Color.Transparent,
                    0.62f to Color.Transparent,
                    1.0f to Color.Black.copy(alpha = 0.78f),
                ),
            )
            .padding(Dimens.ScreenPaddingH, Dimens.ScreenPaddingV),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        PlayerHudHeader(
            title = title,
            position = position,
            total = total,
            nowNext = nowNext,
        )
        PlayerHudControls(
            isPlaying = isPlaying,
            dvrEnabled = dvrEnabled,
            canZap = canZap,
            onBack = onBack,
            onTogglePlay = onTogglePlay,
            onStep = onStep,
            onRewind = onRewind,
            onOpenChannels = onOpenChannels,
            firstControlFocus = firstControlFocus,
        )
    }
}

/**
 * Channel identity, read from the sofa: what is on, where it sits in the zap list, and what is next.
 *
 * Body text is drawn in fixed white rather than a theme role on purpose — this chrome is always a
 * dark scrim over video, and `onSurface` is near-black in the light theme.
 */
@Composable
private fun PlayerHudHeader(
    title: String,
    position: Int?,
    total: Int?,
    nowNext: ChannelNowNext?,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.GapMedium),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (position != null && total != null) {
                Text(
                    text = "$position / $total",
                    style = MaterialTheme.typography.labelMedium,
                    color = TavunoTheme.colors.accentOnVideo,
                )
            }
        }
        val program = nowNext?.now
        if (program != null) {
            val timeRange = EpgTimeFormat.rangeLabel(program.startsAt, program.endsAt)
            Text(
                text = if (timeRange != null) "$timeRange  •  ${program.title}" else program.title,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The button strip.
 *
 * `CH−` is delta **+1** and `CH+` is delta **−1**: the resolver and the zap engine both speak
 * OwnTV's provider-order convention, and having the buttons pass the same deltas keeps the remote's
 * CH rocker and the on-screen pill stepping in the same direction.
 */
@Composable
private fun PlayerHudControls(
    isPlaying: Boolean,
    dvrEnabled: Boolean,
    canZap: Boolean,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onStep: (Int) -> Unit,
    onRewind: (Long) -> Unit,
    onOpenChannels: () -> Unit,
    firstControlFocus: FocusRequester,
) {
    val colors = TavunoTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.GapSmall),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.GapSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TavunoButton(
                label = "Back",
                onClick = onBack,
                style = TavunoButtonStyle.SECONDARY,
                modifier = Modifier.focusRequester(firstControlFocus),
                leading = { tint ->
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )

            if (canZap) {
                HudStepButton(
                    label = "CH−",
                    icon = Icons.Filled.SkipPrevious,
                    onClick = { onStep(1) },
                )
            }

            TavunoButton(
                label = if (isPlaying) "Pause" else "Play",
                onClick = onTogglePlay,
                style = TavunoButtonStyle.PRIMARY,
                leading = { tint ->
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )

            if (canZap) {
                HudStepButton(
                    label = "CH+",
                    icon = Icons.Filled.SkipNext,
                    onClick = { onStep(-1) },
                )
                HudStepButton(
                    label = "Channels",
                    icon = Icons.Filled.List,
                    onClick = onOpenChannels,
                )
            }

            if (dvrEnabled) {
                HudStepButton(
                    label = "−30s",
                    icon = Icons.Filled.Replay30,
                    onClick = { onRewind(30_000L) },
                )
                HudStepButton(
                    label = "−10s",
                    icon = Icons.Filled.Replay10,
                    onClick = { onRewind(10_000L) },
                )
            }
        }

        Text(
            text = if (canZap) {
                "▲▼ or CH± to change channel  •  ◀ channel list  •  BACK returns to the menu"
            } else {
                "OK for controls  •  BACK returns to the menu"
            },
            style = MaterialTheme.typography.labelSmall,
            color = colors.accentOnVideo.copy(alpha = 0.85f),
        )
    }
}

/** A secondary HUD pill with a leading icon — the shared shape of every non-primary control. */
@Composable
private fun HudStepButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    TavunoButton(
        label = label,
        onClick = onClick,
        style = TavunoButtonStyle.SECONDARY,
        leading = { tint ->
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp),
            )
        },
    )
}