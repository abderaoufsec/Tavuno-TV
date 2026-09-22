package com.tavuno.tv.data.model

import com.google.gson.annotations.SerializedName

data class Competition(
    val id: Int,
    val name: String,
    val slug: String,
    val sport: String,
    @SerializedName("category_id")
    val categoryId: Int?,
    @SerializedName("external_id")
    val externalId: String?,
    @SerializedName("is_active")
    val isActive: Boolean
)

data class Match(
    val id: Int,
    @SerializedName("competition_id")
    val competitionId: Int,
    @SerializedName("home_team_id")
    val homeTeamId: Int,
    @SerializedName("away_team_id")
    val awayTeamId: Int,
    @SerializedName("channel_id")
    val channelId: Int?,
    val kickoff: String,
    val status: String,
    @SerializedName("home_score")
    val homeScore: Int?,
    @SerializedName("away_score")
    val awayScore: Int?,
    @SerializedName("external_id")
    val externalId: String?,
    @SerializedName("is_active")
    val isActive: Boolean
)

data class MatchDetails(
    val id: Int,
    @SerializedName("competition_id")
    val competitionId: Int,
    @SerializedName("competition_name")
    val competitionName: String?,
    @SerializedName("home_team_id")
    val homeTeamId: Int,
    @SerializedName("home_team_name")
    val homeTeamName: String,
    @SerializedName("home_team_logo")
    val homeTeamLogo: String?,
    @SerializedName("away_team_id")
    val awayTeamId: Int,
    @SerializedName("away_team_name")
    val awayTeamName: String,
    @SerializedName("away_team_logo")
    val awayTeamLogo: String?,
    @SerializedName("channel_id")
    val channelId: Int?,
    @SerializedName("channel_name")
    val channelName: String?,
    val kickoff: String,
    val status: String,
    @SerializedName("home_score")
    val homeScore: Int?,
    @SerializedName("away_score")
    val awayScore: Int?,
    @SerializedName("is_active")
    val isActive: Boolean
)
