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
