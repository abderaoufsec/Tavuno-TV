package com.tavuno.tv.ui.screens.guide

import com.tavuno.tv.data.model.EpgProgram
import com.tavuno.tv.data.model.GuideChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * Pins the guide grid's geometry — the part that is impossible to eyeball on a TV and trivial to
 * break: window alignment, programme clipping, and the ordering the D-pad walks.
 */
class GuideGridTest {

    private val windowStart: Instant = Instant.parse("2026-10-01T14:00:00Z")
    private val windowEnd: Instant = Instant.parse("2026-10-01T18:00:00Z")
    private val utc = ZoneId.of("UTC")

    private fun program(id: Int, start: String, end: String) = EpgProgram(
        id = id,
        title = "Programme $id",
        startsAt = start,
        endsAt = end,
        description = null,
        channelId = 1,
    )

    // --- window alignment ---

    @Test
    fun `window starts on the enclosing half hour`() {
        assertEquals(
            Instant.parse("2026-10-01T14:30:00Z"),
            GuideGrid.windowStart(Instant.parse("2026-10-01T14:37:12Z")),
        )
    }

    @Test
    fun `window on an exact boundary does not jump to the next slot`() {
        assertEquals(
            Instant.parse("2026-10-01T14:00:00Z"),
            GuideGrid.windowStart(Instant.parse("2026-10-01T14:00:00Z")),
        )
    }

    @Test
    fun `window starts four hours out and measures its own span`() {
        assertEquals(windowEnd, GuideGrid.windowEnd(windowStart))
        assertEquals(240L, GuideGrid.totalMinutes(windowStart, windowEnd))
    }

    @Test
    fun `minutes from window start is negative before the window`() {
        assertEquals(
            -30L,
            GuideGrid.minutesFromWindowStart(windowStart, Instant.parse("2026-10-01T13:30:00Z")),
        )
        assertEquals(
            45L,
            GuideGrid.minutesFromWindowStart(windowStart, Instant.parse("2026-10-01T14:45:00Z")),
        )
    }

    // --- programme placement ---

    @Test
    fun `programme inside the window keeps its position`() {
        val placed = GuideGrid.placeProgrammes(
            listOf(program(1, "2026-10-01T15:00:00Z", "2026-10-01T16:00:00Z")),
            windowStart,
            windowEnd,
        )
        assertEquals(1, placed.size)
        assertEquals(60L, placed[0].startMinute)
        assertEquals(120L, placed[0].endMinute)
    }

    @Test
    fun `programme running at the window start is clipped to the left edge`() {
        // The show that is on right now started before the window — it must still be drawn.
        val placed = GuideGrid.placeProgrammes(
            listOf(program(1, "2026-10-01T13:30:00Z", "2026-10-01T15:00:00Z")),
            windowStart,
            windowEnd,
        )
        assertEquals(1, placed.size)
        assertEquals(0L, placed[0].startMinute)
        assertEquals(60L, placed[0].endMinute)
    }
    @Test
    fun `programme running past the window end is clipped to the right edge`() {
        val placed = GuideGrid.placeProgrammes(
            listOf(program(1, "2026-10-01T17:30:00Z", "2026-10-01T19:00:00Z")),
            windowStart,
            windowEnd,
        )
        assertEquals(1, placed.size)
        assertEquals(210L, placed[0].startMinute)
        assertEquals(240L, placed[0].endMinute)
    }

    @Test
    fun `programme entirely outside the window is dropped`() {
        val placed = GuideGrid.placeProgrammes(
            listOf(
                program(1, "2026-10-01T10:00:00Z", "2026-10-01T11:00:00Z"),
                program(2, "2026-10-01T19:00:00Z", "2026-10-01T20:00:00Z"),
            ),
            windowStart,
            windowEnd,
        )
        assertTrue(placed.isEmpty())
    }

    @Test
    fun `programmes with unparseable bounds are dropped rather than crashing the row`() {
        val placed = GuideGrid.placeProgrammes(
            listOf(
                program(1, "not-a-timestamp", "2026-10-01T15:00:00Z"),
                program(2, "2026-10-01T15:00:00Z", ""),
                program(3, "2026-10-01T15:00:00Z", "2026-10-01T16:00:00Z"),
            ),
            windowStart,
            windowEnd,
        )
        assertEquals(1, placed.size)
        assertEquals(3, placed[0].program.id)
    }

    @Test
    fun `zero length programme is dropped so the row has no unfocusable sliver`() {
        val placed = GuideGrid.placeProgrammes(
            listOf(program(1, "2026-10-01T15:00:00Z", "2026-10-01T15:00:00Z")),
            windowStart,
            windowEnd,
        )
        assertTrue(placed.isEmpty())
    }

    @Test
    fun `programmes are ordered by start time then id`() {
        val placed = GuideGrid.placeProgrammes(
            listOf(
                program(9, "2026-10-01T16:00:00Z", "2026-10-01T17:00:00Z"),
                program(4, "2026-10-01T15:00:00Z", "2026-10-01T16:00:00Z"),
                program(2, "2026-10-01T15:00:00Z", "2026-10-01T15:30:00Z"),
            ),
            windowStart,
            windowEnd,
        )
        assertEquals(listOf(2, 4, 9), placed.map { it.program.id })
    }

    @Test
    fun `live detection follows the clipped bounds`() {
        val placed = GuideGrid.placeProgrammes(
            listOf(program(1, "2026-10-01T13:30:00Z", "2026-10-01T15:00:00Z")),
            windowStart,
            windowEnd,
        ).single()
        assertTrue(placed.isLiveAt(Instant.parse("2026-10-01T14:30:00Z")))
        assertFalse(placed.isLiveAt(Instant.parse("2026-10-01T15:00:00Z")))
    }

    @Test
    fun `a channel with no programmes yields an empty row instead of failing`() {
        val channel = GuideChannel(id = 7, name = "Channel Seven")
        assertTrue(GuideGrid.placeProgrammes(channel, windowStart, windowEnd).isEmpty())
    }

    @Test
    fun `axis labels cover every slot in the window`() {
        assertEquals(
            listOf("14:00", "14:30", "15:00", "15:30", "16:00", "16:30", "17:00", "17:30"),
            GuideGrid.slotLabels(windowStart, windowEnd, zone = utc),
        )
    }
}