package com.tavuno.tv.ui.screens.player

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The player's whole remote map, asserted as a matrix.
 *
 * These behaviours cannot be eyeballed on a TV (a wrong branch looks like "the remote is flaky"),
 * so every rule in [resolvePlayerKey]'s design ladder gets a test.
 */
class PlayerRemoteTest {

    private fun context(
        hudVisible: Boolean = false,
        channelListOpen: Boolean = false,
        canZap: Boolean = true,
        rootFocused: Boolean = true,
        canSelectSubtitles: Boolean = false,
    ) = PlayerKeyContext(
        hudVisible = hudVisible,
        channelListOpen = channelListOpen,
        canZap = canZap,
        rootFocused = rootFocused,
        canSelectSubtitles = canSelectSubtitles,
    )

    private fun resolve(keyCode: Int, ctx: PlayerKeyContext = context()) =
        resolvePlayerKey(keyCode, ctx)

    // --- 1. Media keys are global ----------------------------------------------------

    @Test
    fun `media play pause keys work whatever the overlays are doing`() {
        for (ctx in listOf(context(), context(hudVisible = true), context(channelListOpen = true))) {
            assertEquals(PlayerKey.Play, resolve(KeyEvent.KEYCODE_MEDIA_PLAY, ctx))
            assertEquals(PlayerKey.Pause, resolve(KeyEvent.KEYCODE_MEDIA_PAUSE, ctx))
            assertEquals(PlayerKey.PlayPause, resolve(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, ctx))
        }
    }

    @Test
    fun `media stop leaves the player`() {
        assertEquals(PlayerKey.Back, resolve(KeyEvent.KEYCODE_MEDIA_STOP))
    }

    // --- 2. An open channel list owns the D-pad --------------------------------------

    @Test
    fun `channel list swallows navigation so zapping never fires underneath it`() {
        val open = context(channelListOpen = true)
        assertEquals(PlayerKey.Ignore, resolve(KeyEvent.KEYCODE_DPAD_DOWN, open))
        assertEquals(PlayerKey.Ignore, resolve(KeyEvent.KEYCODE_DPAD_UP, open))
        assertEquals(PlayerKey.Ignore, resolve(KeyEvent.KEYCODE_CHANNEL_UP, open))
        assertEquals(PlayerKey.Ignore, resolve(KeyEvent.KEYCODE_DPAD_CENTER, open))
    }

    @Test
    fun `channel list closes on left right and back`() {
        val open = context(channelListOpen = true)
        assertEquals(PlayerKey.CloseChannelList, resolve(KeyEvent.KEYCODE_DPAD_LEFT, open))
        assertEquals(PlayerKey.CloseChannelList, resolve(KeyEvent.KEYCODE_DPAD_RIGHT, open))
        assertEquals(PlayerKey.CloseChannelList, resolve(KeyEvent.KEYCODE_BACK, open))
        assertEquals(PlayerKey.CloseChannelList, resolve(KeyEvent.KEYCODE_ESCAPE, open))
    }

    // --- 3. The D-pad rocker is the primary surf control ------------------------------

    @Test
    fun `d pad up and down surf channels while the hud is hidden`() {
        assertEquals(PlayerKey.PrevChannel, resolve(KeyEvent.KEYCODE_DPAD_UP))
        assertEquals(PlayerKey.NextChannel, resolve(KeyEvent.KEYCODE_DPAD_DOWN))
    }

    @Test
    fun `d pad travel belongs to focus once the hud is up`() {
        val hud = context(hudVisible = true)
        assertEquals(PlayerKey.Ignore, resolve(KeyEvent.KEYCODE_DPAD_UP, hud))
        assertEquals(PlayerKey.Ignore, resolve(KeyEvent.KEYCODE_DPAD_DOWN, hud))
    }

    @Test
    fun `d pad reveals the hud when there is no list to surf`() {
        val single = context(canZap = false)
        assertEquals(PlayerKey.ToggleHud, resolve(KeyEvent.KEYCODE_DPAD_UP, single))
        assertEquals(PlayerKey.ToggleHud, resolve(KeyEvent.KEYCODE_DPAD_DOWN, single))
    }

    // __PLAYER_REMOTE_TESTS__

    // --- 1b. The SUBTITLE / CAPTIONS key ----------------------------------------------

    @Test
    fun `subtitle key toggles when the stream carries a text track`() {
        assertEquals(
            PlayerKey.ToggleSubtitles,
            resolve(KeyEvent.KEYCODE_CAPTIONS, context(canSelectSubtitles = true)),
        )
    }

    @Test
    fun `subtitle key falls through when there is nothing to select`() {
        assertEquals(
            PlayerKey.Ignore,
            resolve(KeyEvent.KEYCODE_CAPTIONS, context(canSelectSubtitles = false)),
        )
    }

    @Test
    fun `subtitle key is global like the rest of the media rocker`() {
        // Reachable mid-stream, with the controls up, with the channel list open, and off the root.
        for (ctx in listOf(
            context(canSelectSubtitles = true),
            context(canSelectSubtitles = true, hudVisible = true),
            context(canSelectSubtitles = true, channelListOpen = true),
            context(canSelectSubtitles = true, rootFocused = false),
        )) {
            assertEquals(PlayerKey.ToggleSubtitles, resolve(KeyEvent.KEYCODE_CAPTIONS, ctx))
        }
    }

    @Test
    fun `subtitle key leaves the surf and back behaviour alone`() {
        val subtitles = context(canSelectSubtitles = true)
        assertEquals(PlayerKey.PrevChannel, resolve(KeyEvent.KEYCODE_DPAD_UP, subtitles))
        assertEquals(PlayerKey.Back, resolve(KeyEvent.KEYCODE_BACK, subtitles))
    }

    // --- 4. The dedicated channel rocker ----------------------------------------------

    @Test
    fun `channel rocker zaps in both directions when a list is armed`() {
        assertEquals(PlayerKey.PrevChannel, resolve(KeyEvent.KEYCODE_CHANNEL_UP))
        assertEquals(PlayerKey.NextChannel, resolve(KeyEvent.KEYCODE_CHANNEL_DOWN))
        assertEquals(PlayerKey.PrevChannel, resolve(KeyEvent.KEYCODE_MEDIA_PREVIOUS))
        assertEquals(PlayerKey.NextChannel, resolve(KeyEvent.KEYCODE_MEDIA_NEXT))
    }

    @Test
    fun `channel rocker falls through instead of dead ending on a single channel`() {
        val single = context(canZap = false)
        assertEquals(PlayerKey.Ignore, resolve(KeyEvent.KEYCODE_CHANNEL_UP, single))
        assertEquals(PlayerKey.Ignore, resolve(KeyEvent.KEYCODE_CHANNEL_DOWN, single))
    }

    @Test
    fun `left opens the channel list only from the video root`() {
        assertEquals(PlayerKey.OpenChannelList, resolve(KeyEvent.KEYCODE_DPAD_LEFT))
        // Focus is on a HUD control: LEFT must keep meaning "move focus left".
        assertEquals(
            PlayerKey.Ignore,
            resolve(KeyEvent.KEYCODE_DPAD_LEFT, context(rootFocused = false)),
        )
        // No list to show.
        assertEquals(
            PlayerKey.Ignore,
            resolve(KeyEvent.KEYCODE_DPAD_LEFT, context(canZap = false)),
        )
    }

    // --- 5. BACK is a ladder: list → hud → exit ---------------------------------------

    @Test
    fun `back closes the hud before it leaves the player`() {
        assertEquals(PlayerKey.ToggleHud, resolve(KeyEvent.KEYCODE_BACK, context(hudVisible = true)))
        assertEquals(PlayerKey.Back, resolve(KeyEvent.KEYCODE_BACK, context(hudVisible = false)))
        assertEquals(PlayerKey.Back, resolve(KeyEvent.KEYCODE_ESCAPE))
    }

    // --- 6. OK: reveal, then step in, then let the control have it --------------------

    @Test
    fun `first ok reveals the hud second ok enters it`() {
        assertEquals(PlayerKey.ToggleHud, resolve(KeyEvent.KEYCODE_DPAD_CENTER))
        assertEquals(
            PlayerKey.FocusHud,
            resolve(KeyEvent.KEYCODE_DPAD_CENTER, context(hudVisible = true, rootFocused = true)),
        )
        assertEquals(
            PlayerKey.Ignore,
            resolve(KeyEvent.KEYCODE_DPAD_CENTER, context(hudVisible = true, rootFocused = false)),
        )
    }

    // --- Unbound keys never get eaten --------------------------------------------------

    @Test
    fun `unbound keys pass through to the focus system`() {
        assertEquals(PlayerKey.Ignore, resolve(KeyEvent.KEYCODE_A))
        assertEquals(PlayerKey.Ignore, resolve(KeyEvent.KEYCODE_MENU))
        assertEquals(PlayerKey.Ignore, resolve(KeyEvent.KEYCODE_DPAD_RIGHT))
    }
}
