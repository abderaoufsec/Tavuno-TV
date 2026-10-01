package com.tavuno.tv.ui.screens.player

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The subtitle toggle's decision layer, asserted without Android or a decoder.
 *
 * Three failures here are invisible on a television — a subtitle key that dead-ends on the last
 * track, a label that reads "und" to the viewer, and a highlight that disagrees with what is
 * actually rendering — so each rule in [PlayerTracks] gets a test.
 */
class PlayerTracksTest {

    private fun track(
        label: String? = null,
        language: String? = null,
        selected: Boolean = false,
    ) = SubtitleTrackInfo(
        label = label,
        language = language,
        selected = selected,
        groupIndex = 0,
        trackIndex = 0,
    )

    // --- subtitleTrackLabel -----------------------------------------------------------

    @Test
    fun `label and language combine into one name`() {
        assertEquals("English (eng)", subtitleTrackLabel("English", "eng", "Track"))
    }

    @Test
    fun `a label alone wins as the name`() {
        assertEquals("English", subtitleTrackLabel("English", null, "Track"))
    }

    @Test
    fun `a language alone names the track when there is no label`() {
        assertEquals("fra", subtitleTrackLabel(null, "fra", "Track"))
    }

    @Test
    fun `undetermined language is treated as absent rather than shown`() {
        // Media3 stamps "und" on every track whose language it could not read.
        assertEquals("Track", subtitleTrackLabel(null, "und", "Track"))
        assertEquals("Track", subtitleTrackLabel(null, "UND", "Track"))
        assertEquals("Forced", subtitleTrackLabel("Forced", "und", "Track"))
    }

    @Test
    fun `blank inputs fall back so the picker never shows an empty row`() {
        assertEquals("Track", subtitleTrackLabel("   ", "", "Track"))
        assertEquals("Track", subtitleTrackLabel(null, null, "Track"))
        assertEquals("Track", subtitleTrackLabel("  ", "  ", "Track"))
    }

    @Test
    fun `label and language are trimmed before they are joined`() {
        assertEquals("English (eng)", subtitleTrackLabel("  English  ", " eng ", "Track"))
    }

    // --- nextSubtitleIndex ------------------------------------------------------------

    @Test
    fun `toggle walks off then every track then wraps back to off`() {
        val count = 4 // Off + 3 tracks
        assertEquals(1, nextSubtitleIndex(0, count))
        assertEquals(2, nextSubtitleIndex(1, count))
        assertEquals(3, nextSubtitleIndex(2, count))
        assertEquals(0, nextSubtitleIndex(3, count)) // wraps: the last track returns to Off
    }

    @Test
    fun `with no text tracks the key stays on off instead of dead ending`() {
        assertEquals(0, nextSubtitleIndex(0, 1)) // only the Off entry
        assertEquals(0, nextSubtitleIndex(0, 0)) // defensive: a nonsense count
    }

    @Test
    fun `an out of range index is normalised to off rather than guessed`() {
        assertEquals(1, nextSubtitleIndex(99, 3))
        assertEquals(1, nextSubtitleIndex(-5, 3))
    }

    // --- selectedSubtitleIndex --------------------------------------------------------

    @Test
    fun `nothing selected reads as off`() {
        assertEquals(0, selectedSubtitleIndex(listOf(track(), track())))
    }

    @Test
    fun `the selected track maps to its choice index`() {
        assertEquals(2, selectedSubtitleIndex(listOf(track(), track(selected = true), track())))
    }

    @Test
    fun `an empty track list reads as off`() {
        assertEquals(0, selectedSubtitleIndex(emptyList()))
    }
}