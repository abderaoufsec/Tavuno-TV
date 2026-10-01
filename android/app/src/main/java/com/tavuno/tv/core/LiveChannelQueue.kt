package com.tavuno.tv.core

import com.tavuno.tv.playback.ZapChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The channel list the player zaps within, published by whichever browse screen launched it.
 *
 * OwnTV-Baseline keeps this inside `LiveViewModel`, because its player is hosted in the same
 * navigation graph as the browse UI and the two share one ViewModel. Tavuno pushes the player as a
 * separate full-screen route, so the hand-off needs somewhere to live between the two compositions.
 * This is that place — deliberately a tiny held singleton rather than a fourth architectural layer.
 *
 * Contract:
 *  - **Live TV publishes on tap**, immediately before navigating, so the list is already armed when
 *    the player composes (no flicker, no empty first frame).
 *  - **The player never writes the list**, only [markPlaying]. Zapping within the armed list is
 *    `LiveZapNavigator`'s job; this only records which channel is on screen so a return visit to
 *    Live TV can highlight the right row.
 *  - **Reversible**: [clear] drops everything, which is exactly what happens if a viewer opens the
 *    player from Sports (no browse list) — zapping then degrades to a single channel instead of
 *    silently stepping through a stale list.
 */
object LiveChannelQueue {

    private val _channels = MutableStateFlow<List<ZapChannel>>(emptyList())

    /** The armed zap list, in provider order. Empty until a browse screen publishes one. */
    val channels: StateFlow<List<ZapChannel>> = _channels.asStateFlow()

    private val _playingId = MutableStateFlow<Int?>(null)

    /** The live channel currently on screen, or null when nothing is playing. */
    val playingId: StateFlow<Int?> = _playingId.asStateFlow()

    /**
     * Arm a new list and note which channel is about to play.
     *
     * [currentChannelId] is passed through even when it is not in [channels] (a Sports match tuned
     * by id), so the player can tell "not in this list" from "nothing published".
     */
    fun publish(channels: List<ZapChannel>, currentChannelId: Int? = null) {
        _channels.value = channels
        _playingId.value = currentChannelId
    }

    /** Record which channel is on screen. Called by the player on every tune. */
    fun markPlaying(channelId: Int?) {
        _playingId.value = channelId
    }

    /** Drop the armed list — used when live playback context no longer applies. */
    fun clear() {
        _channels.value = emptyList()
        _playingId.value = null
    }
}