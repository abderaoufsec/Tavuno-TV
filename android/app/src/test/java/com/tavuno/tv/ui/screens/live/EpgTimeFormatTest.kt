package com.tavuno.tv.ui.screens.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId

class EpgTimeFormatTest {

    private val utc = ZoneId.of("UTC")
    private val paris = ZoneId.of("Europe/Paris")

    @Test
    fun `parses Z suffixed timestamps`() {
        assertEquals("2026-10-01T14:10:00Z", EpgTimeFormat.parseInstant("2026-10-01T14:10:00Z").toString())
    }

    @Test
    fun `parses offset timestamps`() {
        assertEquals("2026-10-01T12:10:00Z", EpgTimeFormat.parseInstant("2026-10-01T14:10:00+02:00").toString())
    }

    @Test
    fun `parses legacy space separated timestamps`() {
        assertEquals("2026-10-01T14:10:00Z", EpgTimeFormat.parseInstant("2026-10-01 14:10:00+00:00").toString())
    }

    @Test
    fun `formats clock label in requested zone`() {
        assertEquals("14:10", EpgTimeFormat.clockLabel("2026-10-01T14:10:00Z", utc))
        assertEquals("16:10", EpgTimeFormat.clockLabel("2026-10-01T14:10:00Z", paris))
    }

    @Test
    fun `formats clock label for offset timestamps`() {
        assertEquals("12:10", EpgTimeFormat.clockLabel("2026-10-01T14:10:00+02:00", utc))
    }

    @Test
    fun `returns null for missing or invalid timestamps`() {
        assertNull(EpgTimeFormat.parseInstant(null))
        assertNull(EpgTimeFormat.parseInstant(""))
        assertNull(EpgTimeFormat.parseInstant("not-a-timestamp"))
        assertNull(EpgTimeFormat.clockLabel("not-a-timestamp", utc))
    }

    @Test
    fun `builds range label from both bounds`() {
        assertEquals(
            "14:10 - 14:15",
            EpgTimeFormat.rangeLabel("2026-10-01T14:10:00Z", "2026-10-01T14:15:00Z", utc)
        )
    }

    @Test
    fun `falls back to single bound for partial ranges`() {
        assertEquals("14:10", EpgTimeFormat.rangeLabel("2026-10-01T14:10:00Z", null, utc))
        assertEquals("14:15", EpgTimeFormat.rangeLabel(null, "2026-10-01T14:15:00Z", utc))
        assertNull(EpgTimeFormat.rangeLabel(null, null, utc))
    }
}
