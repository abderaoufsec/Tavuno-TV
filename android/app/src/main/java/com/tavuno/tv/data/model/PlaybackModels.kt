package com.tavuno.tv.data.model

import com.google.gson.annotations.SerializedName

data class PlaybackAuthorization(
    @SerializedName("session_id")
    val sessionId: Int,
    @SerializedName("channel_id")
    val channelId: Int,
    @SerializedName("channel_name")
    val channelName: String,
    @SerializedName("expires_at")
    val expiresAt: String,
    val playback: PlaybackInfo
)

data class PlaybackInfo(
    val protocol: String,
    val url: String,
    @SerializedName("stream_name")
    val streamName: String
)

data class MoviePlaybackAuthorization(
    val authorized: Boolean,
    @SerializedName("movie_id")
    val movieId: Int,
    val title: String,
    val provider: String?,
    @SerializedName("external_id")
    val externalId: String?,
    val message: String?,
    @SerializedName("session_id")
    val sessionId: Int?,
    @SerializedName("expires_at")
    val expiresAt: String?,
    val playback: PlaybackInfo?
)

data class EpisodePlaybackAuthorization(
    val authorized: Boolean,
    @SerializedName("episode_id")
    val episodeId: Int,
    val title: String,
    @SerializedName("series_id")
    val seriesId: Int?,
    @SerializedName("series_title")
    val seriesTitle: String?,
    @SerializedName("season_id")
    val seasonId: Int?,
    val message: String?,
    @SerializedName("session_id")
    val sessionId: Int?,
    @SerializedName("expires_at")
    val expiresAt: String?,
    val playback: PlaybackInfo?
)

data class SessionRequest(
    @SerializedName("session_id")
    val sessionId: Int
)

data class SessionResponse(
    @SerializedName("session_id")
    val sessionId: Int,
    val status: String,
    @SerializedName("expires_at")
    val expiresAt: String
)

data class MediaTokenVerification(
    val valid: Boolean,
    @SerializedName("session_id")
    val sessionId: Int,
    @SerializedName("profile_id")
    val profileId: Int,
    @SerializedName("device_id")
    val deviceId: Int
)
