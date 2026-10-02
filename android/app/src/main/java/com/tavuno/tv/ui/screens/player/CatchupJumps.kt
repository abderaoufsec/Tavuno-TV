package com.tavuno.tv.ui.screens.player

/**
 * The "Go back to…" offsets for a catch-up channel — how far back one pick jumps.
 *
 * Pure functions, no Android and no I/O, so the windowing rules are unit-testable.
 * Ported from the OwnTV-Baseline reference (`CatchupJumps`).
 */
object CatchupJumps {

    /** Candidate offsets, nearest first. Bounded per channel by [optionsFor]. */
    private val OFFSETS_SEC = listOf(
        15 * 60,
        30 * 60,
        60 * 60,
        2 * 60 * 60,
        3 * 60 * 60,
        6 * 60 * 60,
        12 * 60 * 60,
        24 * 60 * 60,
        2 * 24 * 60 * 60,
        3 * 24 * 60 * 60,
        7 * 24 * 60 * 60,
    )

    /**
     * The offsets worth offering for an archive [windowSec] seconds deep, nearest first.
     * Strictly inside the window, never equal to it: the very edge of an archive is the
     * part most likely to have already rolled off by the time the request lands.
     */
    fun optionsFor(windowSec: Int): List<Int> = OFFSETS_SEC.filter { it < windowSec }

    /** Wall-clock instant an [offsetSec] jump lands on, relative to [nowMs]. */
    fun instantFor(offsetSec: Int, nowMs: Long): Long = nowMs - offsetSec * 1000L

    /**
     * Seconds behind live a programme that started at [startMs] is at [nowMs] — the offset an
     * EPG "watch from start" hands to the player. Null when the programme has not started yet,
     * so the caller can tell "not airing" apart from "started this second".
     */
    fun offsetForStart(startMs: Long, nowMs: Long): Int? =
        if (nowMs <= startMs) null else ((nowMs - startMs) / 1000L).toInt()

    /**
     * Clamp a requested catch-up offset to the archive window the backend actually granted.
     * Null means there is nothing worth seeking to (no DVR window, or a non-positive request).
     * Strictly inside the window, like [optionsFor]: the far edge is the part most likely to
     * have rolled off by the time the stream seeks.
     */
    fun clampOffset(requestSec: Int, windowSec: Int): Int? {
        if (requestSec <= 0 || windowSec <= 1) return null
        return requestSec.coerceAtMost(windowSec - 1).takeIf { it > 0 }
    }

    /**
     * True when [offsetSec] back from [nowMs] falls on an earlier calendar day, so the row
     * needs a weekday to be unambiguous ("19:00" alone can't tell today from yesterday).
     */
    fun crossesDay(offsetSec: Int, nowMs: Long, zone: java.util.TimeZone): Boolean {
        val cal = java.util.Calendar.getInstance(zone)
        cal.timeInMillis = nowMs
        val today = cal.get(java.util.Calendar.DAY_OF_YEAR) to cal.get(java.util.Calendar.YEAR)
        cal.timeInMillis = instantFor(offsetSec, nowMs)
        return (cal.get(java.util.Calendar.DAY_OF_YEAR) to cal.get(java.util.Calendar.YEAR)) != today
    }

    /** A point the user typed: [daysAgo] whole calendar days back (0 = today) at [hour]:[minute]. */
    data class Point(val daysAgo: Int, val hour: Int, val minute: Int)

    /**
     * How many calendar days the picker's day wheel may offer for a [windowSec]-deep archive.
     * Always at least 1 (today), and one more than the whole days covered.
     */
    fun selectableDays(windowSec: Int): Int = (windowSec / (24 * 3600)) + 1

    /** Wall-clock millis for [p], resolved in [zone] relative to [nowMs]. */
    fun instantOf(p: Point, nowMs: Long, zone: java.util.TimeZone): Long {
        val cal = java.util.Calendar.getInstance(zone)
        cal.timeInMillis = nowMs
        cal.add(java.util.Calendar.DAY_OF_YEAR, -p.daysAgo)
        cal.set(java.util.Calendar.HOUR_OF_DAY, p.hour)
        cal.set(java.util.Calendar.MINUTE, p.minute)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /** Seconds behind live for [p]. Negative when [p] is in the future. */
    fun offsetSecOf(p: Point, nowMs: Long, zone: java.util.TimeZone): Int =
        ((nowMs - instantOf(p, nowMs, zone)) / 1000L).toInt()

    /**
     * Pull [p] back inside what the provider actually holds, so the wheels simply refuse to
     * go past either end rather than letting the user pick a time that can only fail.
     */
    fun clampToArchive(p: Point, nowMs: Long, zone: java.util.TimeZone, windowSec: Int): Point {
        val offset = offsetSecOf(p, nowMs, zone)
        // MIN_OFFSET_SEC, not 0: asking for the exact live edge as an archive request is a race
        // the provider loses about as often as it wins.
        val maxOffset = (windowSec - 1).coerceAtLeast(MIN_OFFSET_SEC)
        if (offset in MIN_OFFSET_SEC..maxOffset) return p
        val bounded = offset.coerceIn(MIN_OFFSET_SEC, maxOffset)
        var target = instantFor(bounded, nowMs)
        // A Point only carries hours and minutes, so [pointAt] necessarily rounds *down* to the
        // minute — which moves the instant further from live and would push a far-end clamp
        // straight back out of the window it was just pulled into. Round up at that end.
        if (bounded == maxOffset) {
            val intoMinute = Math.floorMod(target, 60_000L)
            if (intoMinute != 0L) target += 60_000L - intoMinute
        }
        return pointAt(target, nowMs, zone)
    }

    /** The [Point] describing [atMs], expressed relative to [nowMs]'s calendar day. */
    fun pointAt(atMs: Long, nowMs: Long, zone: java.util.TimeZone): Point {
        val cal = java.util.Calendar.getInstance(zone)
        cal.timeInMillis = atMs
        val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val minute = cal.get(java.util.Calendar.MINUTE)
        val target = startOfDay(cal)
        cal.timeInMillis = nowMs
        val today = startOfDay(cal)
        val daysAgo = ((today - target) / (24L * 3600 * 1000)).toInt().coerceAtLeast(0)
        return Point(daysAgo, hour, minute)
    }

    private fun startOfDay(cal: java.util.Calendar): Long {
        val c = cal.clone() as java.util.Calendar
        c.set(java.util.Calendar.HOUR_OF_DAY, 0)
        c.set(java.util.Calendar.MINUTE, 0)
        c.set(java.util.Calendar.SECOND, 0)
        c.set(java.util.Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    /** Closest to live an exact-time pick is allowed to land — see [clampToArchive]. */
    const val MIN_OFFSET_SEC = 60
}
