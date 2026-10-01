package com.tavuno.tv.playback

import com.tavuno.tv.data.model.Channel
import com.tavuno.tv.data.model.GuideChannel

/**
 * The channel list the player zaps within — CH+/CH−, the D-pad surf keys and the in-player
 * channel-list overlay all step through this.
 *
 * Ported from OwnTV-Baseline's `features/live/LiveZapList`, but re-targeted: OwnTV's zap list owns a
 * Room query and rebuilds windows around a numeric direct tune, because its catalog is large and
 * local. Tavuno already receives the exact channel list the user was browsing (Live TV publishes it
 * to `LiveChannelQueue`), so this is the *decision* half only — pure, ordered, in memory, and
 * therefore unit-testable without Android.
 *
 * Two invariants are pinned by [LiveZapNavigatorTest] because both fail invisibly on a real TV:
 *  - CH± must never dead-end (the ends wrap, like every set-top box made since 1998);
 *  - the list must never silently desync from the channel that is actually playing.
 */
data class ZapChannel(
    val id: Int,
    val name: String,
    val logoUrl: String?,
)

/** Map a catalog channel onto its zap-list entry. */
fun Channel.toZapChannel(): ZapChannel = ZapChannel(id = id, name = name, logoUrl = logo)

/**
 * Same mapping for a guide row (Slice C).
 *
 * The guide never loads the catalog list, so tuning from it has to map its own channel
 * shape; without this the player would get an empty zap list and CH+/- would dead-end.
 */
fun GuideChannel.toZapChannel(): ZapChannel = ZapChannel(id = id, name = name, logoUrl = logo)

/**
 * Compute the next index for a CH+/CH− delta within a bounded zap list.
 *
 * OwnTV's convention is kept deliberately: **CH+ is −1 and CH− is +1**, matching how the remote's
 * channel rocker reads against a provider's ascending channel numbering. Negative deltas are
 * therefore the normal case, not an edge case. Both ends wrap, so the first channel's CH+ lands on
 * the last (and vice versa) instead of doing nothing.
 *
 * Returns null when there is no valid navigation target:
 *  - [listSize] < 2: a single-element list has no neighbour;
 *  - [currentIndex] out of range: the caller passed an inconsistent snapshot;
 *  - the wrapped index lands on [currentIndex] itself (defensive; only reachable for a one-element
 *    list, already excluded above).
 */
internal fun wrappedZapIndex(currentIndex: Int, delta: Int, listSize: Int): Int? {
    if (listSize < 2) return null
    if (currentIndex !in 0 until listSize) return null
    val raw = ((currentIndex + delta) % listSize + listSize) % listSize
    return if (raw == currentIndex) null else raw
}

/**
 * Ordered, mutable selection over a fixed channel list.
 *
 * Deliberately not thread-safe and not a `StateFlow`: the player owns exactly one of these on the
 * composition's main dispatcher, and publishing the *list* is `LiveChannelQueue`'s job. Keeping the
 * two apart is what lets the sequencing be tested directly.
 */
class LiveZapNavigator {

    private var channels: List<ZapChannel> = emptyList()
    private var currentId: Int? = null

    /** The list currently armed for zapping, in provider order. */
    val list: List<ZapChannel> get() = channels

    /** How many channels can be zapped; 0 or 1 means zapping is unavailable. */
    val size: Int get() = channels.size

    /** True when there is at least one neighbour to step to. */
    val canZap: Boolean get() = channels.size >= 2

    /** The channel on screen, or null when it is not part of the armed list. */
    val playing: ZapChannel? get() = channels.firstOrNull { it.id == currentId }

    /** 1-based position of the playing channel for the HUD, or null when it is not in the list. */
    val playingPosition: Int?
        get() {
            val id = currentId ?: return null
            return indexOf(id).takeIf { it >= 0 }?.plus(1)
        }

    /** Index of [id] in the armed list, or −1 when absent. */
    fun indexOf(id: Int): Int = channels.indexOfFirst { it.id == id }

    /** True when [id] is part of the armed list. */
    fun contains(id: Int): Boolean = indexOf(id) >= 0

    fun channelAt(index: Int): ZapChannel? = channels.getOrNull(index)

    /**
     * Install the list published by the browse screen.
     *
     * [preferredCurrentId] wins when it is present in the new list; otherwise the previously playing
     * channel is kept if it survived, so a list refresh (a category re-load, a retry) never moves the
     * user's cursor off the channel they are watching.
     *
     * @return true when the armed list actually changed, so callers can skip redundant work.
     */
    fun replace(channels: List<ZapChannel>, preferredCurrentId: Int? = null): Boolean {
        val changed = channels != this.channels
        this.channels = channels
        currentId = when {
            preferredCurrentId != null && contains(preferredCurrentId) -> preferredCurrentId
            currentId != null && contains(currentId!!) -> currentId
            else -> null
        }
        return changed
    }

    /** Mark [id] as playing. Returns the entry, or null when it is not in the armed list. */
    fun select(id: Int): ZapChannel? {
        val channel = channels.firstOrNull { it.id == id } ?: return null
        currentId = id
        return channel
    }

    /**
     * Step [delta] places from the playing channel, wrapping at both ends.
     *
     * Returns null when zapping is unavailable (fewer than two channels) or the playing channel is
     * not in the armed list — the caller must then fall back to a full re-tune rather than guess.
     * Stepping also *selects* the result, which is what makes a repeated CH+ walk the whole list.
     */
    fun step(delta: Int): ZapChannel? {
        val from = currentId ?: return null
        val fromIndex = indexOf(from)
        val nextIndex = wrappedZapIndex(fromIndex, delta, channels.size) ?: return null
        val channel = channels[nextIndex]
        currentId = channel.id
        return channel
    }

    /** Drop the armed list (leaving live playback entirely). */
    fun clear() {
        channels = emptyList()
        currentId = null
    }
}