package com.streamvault.data.remote.tavuno

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    val email: String,
    val password: String,
    @SerializedName("device_fingerprint")
    val deviceFingerprint: String,
    val platform: String
)

data class LoginResponse(
    @SerializedName("access_token")
    val accessToken: String,
    @SerializedName("refresh_token")
    val refreshToken: String,
    @SerializedName("access_expires_at")
    val accessExpiresAt: Double,
    @SerializedName("refresh_expires_at")
    val refreshExpiresAt: Double,
    val profile: Profile
)

data class Profile(
    val id: Int,
    val email: String,
    @SerializedName("display_name")
    val displayName: String
)

data class RefreshRequest(
    @SerializedName("refresh_token")
    val refreshToken: String
)

data class DeviceDto(
    val id: Int,
    @SerializedName("display_name")
    val displayName: String,
    val platform: String,
    @SerializedName("last_seen_at")
    val lastSeenAt: String? = null,
    @SerializedName("is_current")
    val isCurrent: Boolean = false,
    @SerializedName("is_revoked")
    val isRevoked: Boolean = false
)

// Catalog DTOs
data class HomeResponse(
    val categories: List<CategoryDto>,
    @SerializedName("featured_channels")
    val featuredChannels: List<ChannelDto>
)

data class ChannelDto(
    val id: Int,
    val name: String,
    val slug: String,
    @SerializedName("category_id")
    val categoryId: Int?,
    val logo: String?,
    @SerializedName("is_active")
    val isActive: Boolean
)

data class ChannelDetailDto(
    val id: Int,
    val name: String,
    val slug: String,
    @SerializedName("category_id")
    val categoryId: Int?,
    val logo: String?,
    @SerializedName("is_active")
    val isActive: Boolean,
    val sources: List<ChannelSourceDto>
)

data class ChannelSourceDto(
    val provider: String,
    @SerializedName("external_id")
    val externalId: String,
    val priority: Int,
    @SerializedName("is_active")
    val isActive: Boolean
)

data class CategoryDto(
    val id: Int,
    val name: String,
    val kind: String,
    @SerializedName("parent_id")
    val parentId: Int?,
    @SerializedName("sort_order")
    val sortOrder: Int,
    @SerializedName("is_active")
    val isActive: Boolean
)

data class MovieDto(
    val id: Int,
    val title: String,
    val slug: String,
    @SerializedName("category_id")
    val categoryId: Int?,
    val synopsis: String?,
    @SerializedName("release_year")
    val releaseYear: Int?,
    @SerializedName("is_active")
    val isActive: Boolean
)

data class SeriesDto(
    val id: Int,
    val title: String,
    val slug: String,
    @SerializedName("category_id")
    val categoryId: Int?,
    val synopsis: String?,
    @SerializedName("is_active")
    val isActive: Boolean
)

// Content detail DTOs (M12)
data class ChannelDetailsDto(
    val id: Int,
    val name: String,
    val slug: String,
    @SerializedName("category_id")
    val categoryId: Int?,
    val logo: String?,
    @SerializedName("is_active")
    val isActive: Boolean,
    val description: String?,
    @SerializedName("category_name")
    val categoryName: String?,
    @SerializedName("playback_available")
    val playbackAvailable: Boolean
)

data class MovieDetailsDto(
    val id: Int,
    val title: String,
    val slug: String,
    @SerializedName("category_id")
    val categoryId: Int?,
    val synopsis: String?,
    @SerializedName("release_year")
    val releaseYear: Int?,
    @SerializedName("is_active")
    val isActive: Boolean,
    val poster: String?,
    val backdrop: String?,
    val duration: String?,
    @SerializedName("category_name")
    val categoryName: String?,
    val genres: List<String>?,
    @SerializedName("playback_available")
    val playbackAvailable: Boolean
)

data class SeriesDetailsDto(
    val id: Int,
    val title: String,
    val slug: String,
    @SerializedName("category_id")
    val categoryId: Int?,
    val synopsis: String?,
    @SerializedName("is_active")
    val isActive: Boolean,
    val poster: String?,
    val backdrop: String?,
    @SerializedName("release_year")
    val releaseYear: Int?,
    @SerializedName("category_name")
    val categoryName: String?,
    val seasons: List<SeasonDto>?,
    @SerializedName("episode_count")
    val episodeCount: Int?,
    @SerializedName("playback_available")
    val playbackAvailable: Boolean
)

data class SeasonDto(
    @SerializedName("season_number")
    val seasonNumber: Int,
    val name: String,
    @SerializedName("episode_count")
    val episodeCount: Int
)

// Playback DTOs (M12.5)
data class PlaybackRequest(
    @SerializedName("device_key")
    val deviceKey: String? = null
)

data class SessionRequest(
    @SerializedName("session_id")
    val sessionId: Int
)

data class SessionResponse(
    @SerializedName("session_id")
    val sessionId: Int,
    val status: String
)

data class PlaybackResponse(
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

// EPG DTOs (M13)
data class EpgProgramDto(
    val id: Int,
    val title: String,
    @SerializedName("starts_at")
    val startsAt: String,
    @SerializedName("ends_at")
    val endsAt: String,
    val description: String? = null,
    @SerializedName("channel_id")
    val channelId: Int? = null
)

data class EpgNowNextDto(
    @SerializedName("channel_id")
    val channelId: Int,
    val now: EpgProgramDto? = null,
    val next: EpgProgramDto? = null,
    val later: EpgProgramDto? = null
)

// M11 Sports DTOs
data class CompetitionDto(
    val id: Int,
    val name: String,
    val slug: String,
    val sport: String,
    @SerializedName("category_id")
    val categoryId: Int? = null,
    @SerializedName("external_id")
    val externalId: String? = null,
    @SerializedName("is_active")
    val isActive: Boolean = true
)

data class TeamDto(
    val id: Int,
    val name: String,
    val slug: String,
    @SerializedName("competition_id")
    val competitionId: Int? = null,
    val logo: String? = null,
    @SerializedName("external_id")
    val externalId: String? = null,
    @SerializedName("is_active")
    val isActive: Boolean = true
)

data class MatchDto(
    val id: Int,
    @SerializedName("competition_id")
    val competitionId: Int,
    @SerializedName("home_team_id")
    val homeTeamId: Int,
    @SerializedName("away_team_id")
    val awayTeamId: Int,
    @SerializedName("channel_id")
    val channelId: Int? = null,
    val kickoff: String,
    val status: String,
    @SerializedName("home_score")
    val homeScore: Int? = null,
    @SerializedName("away_score")
    val awayScore: Int? = null,
    @SerializedName("external_id")
    val externalId: String? = null,
    @SerializedName("is_active")
    val isActive: Boolean = true
)

data class MatchDetailsDto(
    val id: Int,
    @SerializedName("competition_id")
    val competitionId: Int,
    @SerializedName("competition_name")
    val competitionName: String? = null,
    @SerializedName("home_team_id")
    val homeTeamId: Int,
    @SerializedName("home_team_name")
    val homeTeamName: String,
    @SerializedName("home_team_logo")
    val homeTeamLogo: String? = null,
    @SerializedName("away_team_id")
    val awayTeamId: Int,
    @SerializedName("away_team_name")
    val awayTeamName: String,
    @SerializedName("away_team_logo")
    val awayTeamLogo: String? = null,
    @SerializedName("channel_id")
    val channelId: Int? = null,
    @SerializedName("channel_name")
    val channelName: String? = null,
    val kickoff: String,
    val status: String,
    @SerializedName("home_score")
    val homeScore: Int? = null,
    @SerializedName("away_score")
    val awayScore: Int? = null,
    @SerializedName("is_active")
    val isActive: Boolean = true
)
