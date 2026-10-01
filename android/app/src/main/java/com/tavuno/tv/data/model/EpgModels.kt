package com.tavuno.tv.data.model

import com.google.gson.annotations.SerializedName

data class EpgProgram(
    val id: Int,
    val title: String,
    @SerializedName("starts_at")
    val startsAt: String,
    @SerializedName("ends_at")
    val endsAt: String,
    val description: String?,
    @SerializedName("channel_id")
    val channelId: Int
)

data class ChannelNowNext(
    @SerializedName("channel_id")
    val channelId: Int,
    val now: EpgProgram?,
    val next: EpgProgram?,
    val later: EpgProgram?
)

/**
 * One channel row inside a guide window (Slice C).
 *
 * Mirrors the backend `GuideChannel`: the guide lists a channel even when its EPG has
 * not synced yet, so [programmes] is empty rather than the row disappearing — the grid
 * renders that as "no guide data" instead of losing the channel entirely.
 */
data class GuideChannel(
    val id: Int,
    val name: String,
    val slug: String = "",
    @SerializedName("category_id")
    val categoryId: Int? = null,
    val logo: String? = null,
    val programmes: List<EpgProgram> = emptyList()
)

/** The whole guide payload for one request: the window plus its channel rows. */
data class EpgWindow(
    @SerializedName("window_start")
    val windowStart: String,
    @SerializedName("window_end")
    val windowEnd: String,
    val channels: List<GuideChannel> = emptyList()
)
