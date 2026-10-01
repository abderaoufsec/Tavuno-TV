package com.tavuno.tv.playback

import com.tavuno.tv.data.model.Channel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the two zap invariants that fail invisibly on a real TV:
 *  - CH+/CH− wrap at both ends (never dead-end),
 *  - the list never desyncs from the channel that is actually playing.
 */
class LiveZapNavigatorTest {

    private fun channels(vararg ids: Int): List<ZapChannel> =
        ids.map { ZapChannel(id = it, name = "Channel $it", logoUrl = null) }

    private fun armedNavigator(vararg ids: Int, currentId: Int? = ids.firstOrNull()): LiveZapNavigator =
        LiveZapNavigator().apply {
            replace(channels(*ids), preferredCurrentId = currentId)
        }

    // --- wrappedZapIndex -------------------------------------------------------------

    @Test
    fun `ch plus delta -1 walks backwards and wraps the head to the tail`() {
        assertEquals(1, wrappedZapIndex(2, -1, 3))
        assertEquals(0, wrappedZapIndex(1, -1, 3))
        assertEquals(2, wrappedZapIndex(0, -1, 3)) // wrap
    }

    @Test
    fun `ch minus delta +1 walks forwards and wraps the tail to the head`() {
        assertEquals(1, wrappedZapIndex(0, +1, 3))
        assertEquals(0, wrappedZapIndex(2, +1, 3)) // wrap
    }

    @Test
    fun `single channel list has no neighbour`() {
        assertNull(wrappedZapIndex(0, -1, 1))
        assertNull(wrappedZapIndex(0, +1, 1))
    }

    @Test
    fun `out of range current index is rejected rather than guessed`() {
        assertNull(wrappedZapIndex(-1, -1, 3))
        assertNull(wrappedZapIndex(3, +1, 3))
    }

    // --- LiveZapNavigator ------------------------------------------------------------

    @Test
    fun `replace arms the published list and the preferred current channel`() {
        val navigator = armedNavigator(1, 2, 3, currentId = 2)
        assertEquals(3, navigator.size)
        assertTrue(navigator.canZap)
        assertEquals(2, navigator.playing?.id)
        assertEquals(2, navigator.playingPosition)
    }

    @Test
    fun `replace keeps the playing channel when the preferred id is not in the new list`() {
        val navigator = armedNavigator(1, 2, 3, currentId = 2)
        navigator.replace(channels(1, 2, 3, 4), preferredCurrentId = 99)
        assertEquals(2, navigator.playing?.id)
    }

    @Test
    fun `replace drops the playing channel when it vanished from the list`() {
        val navigator = armedNavigator(1, 2, 3, currentId = 3)
        navigator.replace(channels(1, 2))
        assertNull(navigator.playing)
        assertEquals(-1, navigator.playingPosition ?: -1)
    }

    @Test
    fun `step walks the whole list and wraps both ends`() {
        val navigator = armedNavigator(10, 20, 30, currentId = 10)
        assertEquals(30, navigator.step(-1)?.id) // CH+ from head lands on tail
        assertEquals(20, navigator.step(-1)?.id)
        assertEquals(10, navigator.step(-1)?.id)
        assertEquals(20, navigator.step(+1)?.id) // CH− walks the other way
        assertEquals(30, navigator.step(+1)?.id)
        assertEquals(10, navigator.step(+1)?.id) // CH− wraps tail → head
    }

    @Test
    fun `step refuses when nothing is playing so the caller re-tunes instead`() {
        val navigator = LiveZapNavigator().apply { replace(channels(1, 2, 3)) }
        // canZap is about the list; without a playing entry there is no anchor to step from.
        assertTrue(navigator.canZap)
        assertNull(navigator.playing)
        assertNull(navigator.step(-1))
    }

    @Test
    fun `single channel list cannot zap`() {
        val navigator = armedNavigator(1, currentId = 1)
        assertFalse(navigator.canZap)
        assertNull(navigator.step(-1))
    }

    @Test
    fun `select marks the channel on screen and rejects unknown ids`() {
        val navigator = armedNavigator(1, 2, 3, currentId = 1)
        assertEquals(ZapChannel(2, "Channel 2", null), navigator.select(2))
        assertEquals(2, navigator.playing?.id)
        assertNull(navigator.select(99))
        assertEquals(2, navigator.playing?.id) // unknown id leaves the selection alone
    }

    @Test
    fun `clear drops everything so zapping degrades to no list`() {
        val navigator = armedNavigator(1, 2, 3, currentId = 1)
        navigator.clear()
        assertTrue(navigator.list.isEmpty())
        assertNull(navigator.playing)
        assertFalse(navigator.canZap)
    }

    @Test
    fun `catalog channel maps onto its zap entry`() {
        val channel = Channel(
            id = 7,
            name = "Tavuno One",
            slug = "tavuno-one",
            categoryId = 1,
            logo = "https://cdn.example/one.png",
            isActive = true,
        )
        assertEquals(ZapChannel(7, "Tavuno One", "https://cdn.example/one.png"), channel.toZapChannel())
    }
}
