package com.tavuno.tv.ui.screens.player

import androidx.media3.common.C
import androidx.media3.common.Tracks

/**
 * Subtitle (text-track) selection for the player, split the same way the remote map is: the
 * *decisions* are pure functions here and unit-tested ([PlayerTracksTest]), while the only Media3
 * dependency is the small [subtitleTrackInfos] adapter that flattens [Tracks] into that pure shape.
 *
 * Why this exists at all: Media3's `DefaultTrackSelector` happily auto-selects a text track, so a
 * viewer who never asked for subtitles gets them, and there is no built-in "Off" state to cycle back
 * to. The player therefore **starts with text disabled** and offers one
 * [PlayerKey.ToggleSubtitles] action that walks `Off → track 1 → … → track n → Off`, exactly like the
 * SUBTITLE key on a set-top box.
 */

/// Choice index 0 is always "Off" — the state a viewer returns to after the last real track.
internal const val SUBTITLE_OFF_INDEX = 0

/** One selectable text track, in the flat order the toggle walks. */
internal data class SubtitleTrackInfo(
    /** `Format.label` — the provider's own name for the track, if it gave one. */
    val label: String?,
    /** `Format.language` — an ISO code such as `eng`, or `und`/null when undetermined. */
    val language: String?,
    /** Whether the decoder is rendering this track right now. */
    val selected: Boolean,
    /** Index into [Tracks.groups] — kept so the caller can rebuild a `TrackSelectionOverride`. */
    val groupIndex: Int,
    /** Index within that group. */
    val trackIndex: Int,
)

/**
 * The human name for a text track, in preference order:
 * `label (language)` → `label` → `language` → [fallback].
 *
 * `und` ("undetermined") is treated as absent, because Media3 stamps it on every track whose
 * language it could not read and showing a viewer the word "und" is worse than showing nothing.
 */
internal fun subtitleTrackLabel(label: String?, language: String?, fallback: String): String {
    val name = label?.trim()?.takeIf { it.isNotEmpty() }
    val lang = language?.trim()?.takeIf { it.isNotEmpty() && !it.equals("und", ignoreCase = true) }
    return when {
        name != null && lang != null -> "$name ($lang)"
        name != null -> name
        lang != null -> lang
        else -> fallback
    }
}

/**
 * The choice index a SUBTITLE press moves to.
 *
 * Walks forward and wraps, so the last track returns to Off rather than dead-ending: the same key
 * that turned subtitles on always gets the viewer back to "none". With only the Off entry there is
 * nothing to select, so Off is the answer.
 */
internal fun nextSubtitleIndex(currentIndex: Int, choiceCount: Int): Int {
    if (choiceCount <= 1) return SUBTITLE_OFF_INDEX
    val normalized = if (currentIndex in 0 until choiceCount) currentIndex else SUBTITLE_OFF_INDEX
    return (normalized + 1) % choiceCount
}

/**
 * The choice index of the track that is actually rendering, or [SUBTITLE_OFF_INDEX].
 *
 * Derived from the tracks rather than remembered locally, so the HUD's "on/off" can never disagree
 * with what the decoder is doing — including when the stream swaps its own tracks mid-playback.
 */
internal fun selectedSubtitleIndex(tracks: List<SubtitleTrackInfo>): Int {
    val selected = tracks.indexOfFirst { it.selected }
    return if (selected >= 0) selected + 1 else SUBTITLE_OFF_INDEX
}

/**
 * Flattens Media3's track tree into [SubtitleTrackInfo], skipping unsupported tracks so the toggle
 * never lands on an entry that cannot play.
 *
 * The `groupIndex`/`trackIndex` it records address the same [Tracks] instance the caller holds,
 * which is what lets a chosen entry be turned back into a `TrackSelectionOverride` without a second
 * lookup table.
 */
internal fun subtitleTrackInfos(tracks: Tracks): List<SubtitleTrackInfo> {
    val result = mutableListOf<SubtitleTrackInfo>()
    tracks.groups.forEachIndexed { groupIndex, group ->
        if (group.type != C.TRACK_TYPE_TEXT) return@forEachIndexed
        for (trackIndex in 0 until group.length) {
            if (!group.isTrackSupported(trackIndex)) continue
            val format = group.getTrackFormat(trackIndex)
            result.add(
                SubtitleTrackInfo(
                    label = format.label,
                    language = format.language,
                    selected = group.isTrackSelected(trackIndex),
                    groupIndex = groupIndex,
                    trackIndex = trackIndex,
                ),
            )
        }
    }
    return result
}