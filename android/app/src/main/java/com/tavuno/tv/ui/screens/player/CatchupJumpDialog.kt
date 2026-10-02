package com.tavuno.tv.ui.screens.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.tavuno.tv.ui.components.FocusableSurface
import com.tavuno.tv.ui.components.TavunoButton
import com.tavuno.tv.ui.components.TavunoButtonStyle
import com.tavuno.tv.ui.shell.tavunoDialogPanel
import com.tavuno.tv.ui.shell.tavunoModalScrim
import com.tavuno.tv.ui.theme.Dimens
import com.tavuno.tv.ui.theme.TavunoTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import android.text.format.DateFormat as AndroidDateFormat

/**
 * "Go back to…" — pick a point in a live channel's catch-up archive and start there.
 *
 * Rows are wall-clock times rather than "3 hours ago" on purpose: the viewer is looking for the news
 * that aired at 19:00, so a clock spares them the arithmetic, and it is what the guide-less catch-up
 * pickers on other TV players show. A row that lands on an earlier day carries its weekday, because
 * "19:00" alone cannot distinguish today from yesterday.
 *
 * Ported from the OwnTV-Baseline reference (`CatchupJumpDialog` / `CatchupManualTimeDialog`), rebuilt
 * on the Tavuno design system. The archive window is the one the backend already handed the player
 * (`PlaybackInfo.maxRewindSeconds`), so this picker can only offer offsets the OME DVR hold actually
 * covers — see [CatchupJumps.optionsFor].
 *
 * Rendered as an in-player overlay rather than a separate dialog window: the player's key map owns the
 * whole remote, so the dialog must live inside that composition (see `PlayerKeyContext.catchupOpen`)
 * or the preview handler on the player root would swallow every press before the list saw it.
 */
@Composable
internal fun CatchupJumpOverlay(
    offsetsSec: List<Int>,
    windowSec: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TavunoTheme.colors
    // The proven entry pattern in this codebase (PlayerScreen root, Sidebar rail): a focusable root
    // carries the requester and focus enters it, then natural D-pad traversal walks into the list.
    val rootFocus = remember { FocusRequester() }
    var manual by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // Let the panel enter composition before asking; a requester with no node no-ops silently.
        delay(60)
        runCatching { rootFocus.requestFocus() }
    }
    BackHandler(enabled = !manual) { onDismiss() }

    if (manual) {
        CatchupManualTimeOverlay(
            windowSec = windowSec,
            onPick = { manual = false; onPick(it) },
            onDismiss = { manual = false },
        )
        return
    }

    val listHeight = (LocalConfiguration.current.screenHeightDp.dp - 220.dp).coerceIn(140.dp, 320.dp)
    Box(
        modifier = modifier
            .fillMaxSize()
            .tavunoModalScrim()
            .focusRequester(rootFocus)
            .focusable()
            .focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.tavunoDialogPanel(width = 460.dp, corner = 16.dp, padding = 18.dp, scroll = false),
        ) {
            Text(
                text = "Go back to…",
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Start this channel from an earlier point in its archive.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(12.dp))
            CatchupJumpRows(
                offsetsSec = offsetsSec,
                onPick = onPick,
                modifier = Modifier.fillMaxWidth().height(listHeight),
                onChooseExact = { manual = true },
            )
            Spacer(Modifier.height(14.dp))
            TavunoButton(
                label = "Close",
                onClick = onDismiss,
                style = TavunoButtonStyle.SECONDARY,
            )
        }
    }
}


/**
 * The scrollable offset list. Rows are wall-clock labels; the last row opens exact-time entry.
 *
 * Focus enters at the overlay root, not on a row: giving a LazyColumn row its own requester is the
 * timing race this codebase already moved away from — the row may not be composed when the request
 * lands, and `requestFocus()` then no-ops with nothing to report.
 */
@Composable
internal fun CatchupJumpRows(
    offsetsSec: List<Int>,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onChooseExact: (() -> Unit)? = null,
) {
    val colors = TavunoTheme.colors
    // One "now" for the whole list: recomputing per row would let the clock tick between rows and
    // print two different times for the same offset.
    val nowMs = remember { System.currentTimeMillis() }
    val zone = remember { TimeZone.getDefault() }
    val timeOnly = rememberWallClockFormatter(withDay = false)
    val withDay = rememberWallClockFormatter(withDay = true)

    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(offsetsSec, key = { it }) { offset ->
            val at = CatchupJumps.instantFor(offset, nowMs)
            val label = if (CatchupJumps.crossesDay(offset, nowMs, zone)) {
                withDay.format(Date(at))
            } else {
                timeOnly.format(Date(at))
            }
            FocusableSurface(
                onClick = { onPick(offset) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                contentAlignment = Alignment.CenterStart,
            ) { _ ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }
        if (onChooseExact != null) {
            item(key = "exact") {
                FocusableSurface(
                    onClick = onChooseExact,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) { _ ->
                    Text(
                        text = "Exact time…",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.primary,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}


/**
 * Exact-time entry for a catch-up channel: a day wheel plus a two-part HH:MM, so any point the
 * archive still holds is reachable — the suggestion list can only offer round offsets, so
 * "yesterday at 10:31" had no route until this.
 *
 * Three wheels rather than a text field or a clock face: a TV remote has no comfortable way to type,
 * and an analogue clock is worse still with a D-pad. Left/Right moves between the wheels, OK steps
 * *into* one, Up/Down then change it (holding a key auto-repeats), and OK or Back steps back out —
 * see [CatchupWheel] for why editing is a mode rather than always-on.
 *
 * Every change is pushed through [CatchupJumps.clampToArchive], so the wheels stop dead at the live
 * edge and at the far end of the archive. Nothing needs to explain why: the number simply will not go
 * further than the recording does.
 */
@Composable
internal fun CatchupManualTimeOverlay(
    windowSec: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = TavunoTheme.colors
    // One "now" for the overlay's lifetime. A ticking clock would shift every wheel under the user's
    // fingers and make the clamp boundaries move while they aim at them.
    val nowMs = remember { System.currentTimeMillis() }
    val zone = remember { TimeZone.getDefault() }
    val dayFormat = rememberWallClockFormatter(withDay = true)

    // Start an hour back: inside the archive on any channel, and a sensible neighbourhood to nudge from.
    var point by remember {
        mutableStateOf(
            CatchupJumps.clampToArchive(
                CatchupJumps.pointAt(CatchupJumps.instantFor(3600, nowMs), nowMs, zone),
                nowMs, zone, windowSec,
            ),
        )
    }
    val maxDaysAgo = remember(windowSec) { CatchupJumps.selectableDays(windowSec) - 1 }

    fun nudge(deltaDays: Int = 0, deltaHours: Int = 0, deltaMinutes: Int = 0) {
        val p = point
        val next = CatchupJumps.Point(
            daysAgo = (p.daysAgo + deltaDays).coerceIn(0, maxDaysAgo),
            // Hours and minutes wrap: rolling 23 → 00 is how a clock behaves, and the archive clamp
            // below catches anything the wrap puts out of reach.
            hour = ((p.hour + deltaHours) + 24) % 24,
            minute = ((p.minute + deltaMinutes) + 60) % 60,
        )
        point = CatchupJumps.clampToArchive(next, nowMs, zone, windowSec)
    }

    val rootFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(60)
        runCatching { rootFocus.requestFocus() }
    }
    BackHandler { onDismiss() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .tavunoModalScrim()
            .focusRequester(rootFocus)
            .focusable()
            .focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.tavunoDialogPanel(width = 440.dp, corner = 16.dp, padding = 18.dp, scroll = false),
        ) {
            Text(
                text = "Exact time",
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Pick a day and a time inside the archive.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth().focusGroup(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.GapSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CatchupWheel(
                    value = dayFormat.format(Date(CatchupJumps.instantOf(point, nowMs, zone))),
                    onUp = { nudge(deltaDays = -1) },   // toward today
                    onDown = { nudge(deltaDays = +1) }, // further back
                    modifier = Modifier.width(150.dp),
                )
                CatchupWheel(
                    value = two(point.hour),
                    onUp = { nudge(deltaHours = +1) },
                    onDown = { nudge(deltaHours = -1) },
                    modifier = Modifier.width(72.dp),
                )
                Text(":", style = MaterialTheme.typography.titleLarge, color = colors.textSecondary)
                CatchupWheel(
                    value = two(point.minute),
                    onUp = { nudge(deltaMinutes = +1) },
                    onDown = { nudge(deltaMinutes = -1) },
                    modifier = Modifier.width(72.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.GapSmall)) {
                TavunoButton(
                    label = "Play",
                    onClick = { onPick(CatchupJumps.offsetSecOf(point, nowMs, zone)) },
                )
                TavunoButton(
                    label = "Cancel",
                    onClick = onDismiss,
                    style = TavunoButtonStyle.SECONDARY,
                )
            }
        }
    }
}


/**
 * One value column, with an explicit edit mode.
 *
 * OK steps *into* the wheel, Up/Down then change the value, and OK or Back steps back *out*. The
 * obvious design — Up/Down always editing the focused wheel — is a trap on a TV: the wheels sit above
 * the Play button, so a wheel that swallows Down leaves no way to reach it, and the dialog becomes a
 * one-way street. Only while editing are Up/Down consumed; the rest of the time they fall through to
 * ordinary spatial navigation.
 */
@Composable
private fun CatchupWheel(
    value: String,
    onUp: () -> Unit,
    onDown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TavunoTheme.colors
    var editing by remember { mutableStateOf(false) }
    FocusableSurface(
        onClick = { editing = !editing },
        selected = editing,
        shape = RoundedCornerShape(12.dp),
        contentAlignment = Alignment.Center,
        modifier = modifier
            // Losing focus (Left/Right to a neighbour) must not leave this wheel armed behind the
            // user's back.
            .onFocusChanged { if (!it.isFocused) editing = false }
            // Preview, so Back is taken before the overlay's own BackHandler can dismiss everything.
            .onPreviewKeyEvent { e ->
                if (!editing || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionUp -> { onUp(); true }
                    Key.DirectionDown -> { onDown(); true }
                    Key.Back -> { editing = false; true }
                    else -> false // Left/Right still move to the neighbouring wheel
                }
            },
    ) { _ ->
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Arrows appear only while editing — they are a statement that Up/Down now do something
            // here, which is exactly the thing the user cannot otherwise tell.
            Text(
                text = "▲",
                style = MaterialTheme.typography.labelSmall,
                // Transparent rather than absent, so showing/hiding the arrows never reflows the row.
                color = if (editing) colors.primary else Color.Transparent,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = if (editing) colors.primary else colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "▼",
                style = MaterialTheme.typography.labelSmall,
                color = if (editing) colors.primary else Color.Transparent,
            )
        }
    }
}

private fun two(n: Int): String = n.toString().padStart(2, '0')

/**
 * A wall-clock formatter honouring the device's 12/24-hour setting. [withDay] prepends the weekday,
 * which a row needs once its offset lands on an earlier calendar day.
 */
@Composable
private fun rememberWallClockFormatter(withDay: Boolean): SimpleDateFormat {
    val context = LocalContext.current
    val is24h = AndroidDateFormat.is24HourFormat(context)
    val pattern = when {
        withDay && is24h -> "EEE H:mm"
        withDay -> "EEE h:mm a"
        is24h -> "H:mm"
        else -> "h:mm a"
    }
    return remember(pattern) { SimpleDateFormat(pattern, Locale.getDefault()) }
}

