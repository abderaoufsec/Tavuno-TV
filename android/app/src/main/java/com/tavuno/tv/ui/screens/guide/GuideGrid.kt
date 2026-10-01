package com.tavuno.tv.ui.screens.guide

import com.tavuno.tv.data.model.EpgProgram
import com.tavuno.tv.data.model.GuideChannel
import com.tavuno.tv.ui.screens.live.EpgTimeFormat
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * The guide grid's geometry, kept as pure functions with no Compose types.
 *
 * Everything the grid needs to decide — where the window starts, how wide each programme
 * is, which programmes survive the window clip — is arithmetic on timestamps, so it lives
 * here rather than inside the composable: [GuideGridTest] pins it without an emulator, and
 * the screen is left with nothing but drawing and focus.
 */
object GuideGrid {

    /** Time-axis granularity. 30 minutes is what every TV guide labels on its axis. */
    const val SLOT_MINUTES = 30

    /** Horizontal density: one dp per minute, so a 30-minute slot is 150 dp wide. */
    const val DP_PER_MINUTE = 5f

    /** How far ahead the grid looks from the start of the current slot. */
    const val WINDOW_HOURS = 4L

    /**
     * Floor [now] to a slot boundary so the axis always starts on a round time.
     *
     * Flooring (never ceiling) keeps "now" inside the window: a guide that opened at 14:20
     * must be able to draw the programme that is on right now, clipped to the left edge.
     */
    fun windowStart(now: Instant, slotMinutes: Long = SLOT_MINUTES.toLong()): Instant {
        require(slotMinutes > 0) { "slotMinutes must be positive" }
        // floorDiv, not `/`: a plain integer division truncates toward zero and would floor
        // backwards for any pre-1970 instant.
        val epochMinute = Math.floorDiv(now.toEpochMilli(), 60_000L)
        val flooredMinute = epochMinute - Math.floorMod(epochMinute, slotMinutes)
        return Instant.ofEpochMilli(flooredMinute * 60_000L)
    }

    /** End of the window: [hours] after [start]. */
    fun windowEnd(start: Instant, hours: Long = WINDOW_HOURS): Instant =
        start.plus(Duration.ofHours(hours))

    /** Width of the window in minutes — the content width of the scrolling grid. */
    fun totalMinutes(start: Instant, end: Instant): Long =
        Duration.between(start, end).toMinutes()

    /**
     * Minutes from [windowStart] to [instant], negative before the window.
     *
     * The now-line uses the unclipped value so it can be tested against the window bounds;
     * programme placement clamps instead.
     */
    fun minutesFromWindowStart(windowStart: Instant, instant: Instant): Long =
        Duration.between(windowStart, instant).toMinutes()

    /**
     * One programme placed on the time axis.
     *
     * [startMinute] is always >= 0 and [endMinute] > [startMinute]: the window has already
     * clipped the programme, so the screen can draw it without re-checking bounds.
     */
    data class PlacedProgramme(
        val program: EpgProgram,
        val startMinute: Long,
        val endMinute: Long,
        /** Clipped start of the programme; kept so the row can mark "on now" without re-parsing. */
        val start: Instant,
        /** Clipped end of the programme. */
        val end: Instant,
    ) {
        val durationMinutes: Long get() = endMinute - startMinute

        /** True when [now] falls inside the programme — the cell the now-line is crossing. */
        fun isLiveAt(now: Instant): Boolean = !now.isBefore(start) && now.isBefore(end)
    }

    /**
     * Clip one channel's programmes to `[windowStart, windowEnd)` and lay them out in time order.
     *
     * Dropped rather than fudged:
     *  - a programme with an unparseable or missing bound, which cannot be positioned at all;
     *  - a programme entirely outside the window;
     *  - a zero/negative-length programme, which would render as an unfocusable sliver.
     *
     * A programme still running at [windowStart] is kept and clipped to minute 0, so the
     * channel's leftmost cell is the show that is actually on.
     */
    fun placeProgrammes(
        programmes: List<EpgProgram>,
        windowStart: Instant,
        windowEnd: Instant,
    ): List<PlacedProgramme> =
        programmes.mapNotNull { program ->
            val rawStart = EpgTimeFormat.parseInstant(program.startsAt) ?: return@mapNotNull null
            val rawEnd = EpgTimeFormat.parseInstant(program.endsAt) ?: return@mapNotNull null
            val startMinute = maxOf(0L, minutesFromWindowStart(windowStart, rawStart))
            val endMinute = minOf(totalMinutes(windowStart, windowEnd), minutesFromWindowStart(windowStart, rawEnd))
            if (endMinute <= startMinute) {
                null
            } else {
                PlacedProgramme(
                    program = program,
                    startMinute = startMinute,
                    endMinute = endMinute,
                    start = maxOf(windowStart, rawStart),
                    end = minOf(windowEnd, rawEnd),
                )
            }
        }.sortedWith(compareBy({ it.startMinute }, { it.program.id }))

    /** Convenience wrapper for one channel row. */
    fun placeProgrammes(
        channel: GuideChannel,
        windowStart: Instant,
        windowEnd: Instant,
    ): List<PlacedProgramme> = placeProgrammes(channel.programmes, windowStart, windowEnd)

    /** Clock labels for the axis, one per slot, in the viewer's zone. */
    fun slotLabels(
        windowStart: Instant,
        windowEnd: Instant,
        slotMinutes: Long = SLOT_MINUTES.toLong(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<String> {
        val span = totalMinutes(windowStart, windowEnd)
        val labels = ArrayList<String>(((span + slotMinutes - 1) / slotMinutes).toInt())
        var minute = 0L
        while (minute < span) {
            val instant = windowStart.plus(Duration.ofMinutes(minute))
            labels += EpgTimeFormat.clockLabel(instant.toString(), zone).orEmpty()
            minute += slotMinutes
        }
        return labels
    }
}