package com.tavuno.tv.ui.screens.player

import android.view.KeyEvent

/**
 * What the player should do for a remote key press.
 *
 * Every decision the player makes about the D-pad lives here rather than inline in the composable,
 * because "which key does what, in which state" is the part of channel navigation that is
 * impossible to eyeball on a TV and trivial to unit-test ([PlayerRemoteTest]).
 */
internal enum class PlayerKey {
    /** CH+ — the previous channel in provider order (OwnTV's −1 convention). */
    PrevChannel,

    /** CH− — the next channel in provider order. */
    NextChannel,

    /** Reveal the in-player channel list (D-pad LEFT). */
    OpenChannelList,

    /** Dismiss the channel list (D-pad LEFT/RIGHT or BACK). */
    CloseChannelList,

    /** Reveal the HUD (first OK press). */
    ToggleHud,

    /** The HUD is already up but nothing in it is focused — move focus onto it. */
    FocusHud,

    Play,
    Pause,

    /** Media play/pause rocker: toggle. */
    PlayPause,

    /** Leave the player (BACK with no overlay open). */
    Back,

    /** Let the event through: focus travel inside the HUD, or an unbound key. */
    Ignore,
}

/**
 * The player state a key press is interpreted against.
 *
 * @param hudVisible the control strip is on screen.
 * @param channelListOpen the in-player channel list is on screen.
 * @param canZap the armed zap list has at least one neighbour to step to.
 * @param rootFocused D-pad focus is on the video surface itself, not on a HUD control. Only then may
 *   a key be swallowed; otherwise an `Ignore` must let Compose's focus system deliver it to the
 *   focused button, or OK would stop activating anything.
 */
internal data class PlayerKeyContext(
    val hudVisible: Boolean,
    val channelListOpen: Boolean,
    val canZap: Boolean,
    val rootFocused: Boolean,
)

/**
 * The player's key map, in one place.
 *
 * Design rules, in priority order:
 *  1. **Media keys are global.** A remote's play/pause/stop rocker must work whether or not the HUD
 *     happens to be up, so they resolve before any state check.
 *  2. **An open channel list owns the D-pad.** Its rows are focusable; UP/DOWN must move the cursor
 *     inside it, never zap underneath it.
 *  3. **One OK reveals the controls, a second OK enters them.** This is the behaviour every TV
 *     player has, and it is why OK resolves to `ToggleHud` → `FocusHud` → `Ignore` as the HUD goes
 *     hidden → visible-unfocused → visible-focused.
 *  4. **Zapping never dead-ends.** CH± is honoured in every state except when the list is open or
 *     there is no neighbour; when there is none the key falls through instead of being eaten.
 *  5. **BACK is a ladder, not an exit.** It closes the channel list, then the HUD, and only then
 *     leaves the player — so a viewer can never be thrown out of a stream by a stray press.
 */
internal fun resolvePlayerKey(keyCode: Int, context: PlayerKeyContext): PlayerKey {
    // 1. Media keys — global, whatever the HUD is doing.
    when (keyCode) {
        KeyEvent.KEYCODE_MEDIA_PLAY -> return PlayerKey.Play
        KeyEvent.KEYCODE_MEDIA_PAUSE -> return PlayerKey.Pause
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> return PlayerKey.PlayPause
        KeyEvent.KEYCODE_MEDIA_STOP -> return PlayerKey.Back
    }

    // 2. The channel list, when open, consumes the D-pad.
    if (context.channelListOpen) {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_ESCAPE -> PlayerKey.CloseChannelList

            else -> PlayerKey.Ignore
        }
    }

    // 3. The D-pad rocker is the primary surf control, so it is resolved before the dedicated
    //    channel keys. Once the HUD is up it belongs to focus travel instead — otherwise a viewer
    //    could never reach a button.
    when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP -> return when {
            context.hudVisible -> PlayerKey.Ignore
            context.canZap -> PlayerKey.PrevChannel
            // No list armed (e.g. tuned straight from Sports): reveal the controls instead of
            // eating the press, so UP is never a dead key.
            else -> PlayerKey.ToggleHud
        }

        KeyEvent.KEYCODE_DPAD_DOWN -> return when {
            context.hudVisible -> PlayerKey.Ignore
            context.canZap -> PlayerKey.NextChannel
            else -> PlayerKey.ToggleHud
        }
    }

    // 4. The dedicated channel rocker and the media skip keys, for remotes that have them.
    val zap = when (keyCode) {
        KeyEvent.KEYCODE_CHANNEL_UP,
        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> PlayerKey.PrevChannel

        KeyEvent.KEYCODE_CHANNEL_DOWN,
        KeyEvent.KEYCODE_MEDIA_NEXT -> PlayerKey.NextChannel

        else -> null
    }
    if (zap != null) return if (context.canZap) zap else PlayerKey.Ignore

    return when (keyCode) {
        // 4. LEFT opens the list (it is the panel that slides in from the left) — but only from the
        //    video root; while a HUD control is focused it must keep meaning "move focus left".
        KeyEvent.KEYCODE_DPAD_LEFT ->
            if (context.canZap && context.rootFocused) PlayerKey.OpenChannelList else PlayerKey.Ignore

        // 5. BACK closes the HUD before it leaves the player.
        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE ->
            if (context.hudVisible) PlayerKey.ToggleHud else PlayerKey.Back

        // 6. OK: reveal the HUD, then step into it, then let its buttons have the press.
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER,
        KeyEvent.KEYCODE_SPACE ->
            when {
                !context.hudVisible -> PlayerKey.ToggleHud
                context.rootFocused -> PlayerKey.FocusHud
                else -> PlayerKey.Ignore
            }

        else -> PlayerKey.Ignore
    }
}