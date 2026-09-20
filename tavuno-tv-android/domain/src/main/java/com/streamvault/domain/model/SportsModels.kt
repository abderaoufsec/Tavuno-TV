package com.streamvault.domain.model

/**
 * Sports domain models (M11).
 * 
 * These models represent sports competitions, teams, and matches
 * for the Tavuno sports experience.
 */

/**
 * Sports competition (e.g., Premier League, La Liga).
 */
data class Competition(
    val id: Int,
    val name: String,
    val slug: String,
    val sport: String, // e.g., "football", "basketball", "tennis"
    val categoryId: Int? = null,
    val externalId: String? = null,
    val isActive: Boolean = true
)

/**
 * Sports team (e.g., Manchester United, Chelsea).
 */
data class Team(
    val id: Int,
    val name: String,
    val slug: String,
    val competitionId: Int? = null,
    val logo: String? = null, // UUID as string
    val externalId: String? = null,
    val isActive: Boolean = true
)

/**
 * Sports match between two teams.
 */
data class Match(
    val id: Int,
    val competitionId: Int,
    val homeTeamId: Int,
    val awayTeamId: Int,
    val channelId: Int? = null,
    val kickoff: String, // ISO 8601 timestamp
    val status: MatchStatus = MatchStatus.UPCOMING,
    val homeScore: Int? = null,
    val awayScore: Int? = null,
    val externalId: String? = null,
    val isActive: Boolean = true
)

/**
 * Match status.
 */
enum class MatchStatus {
    UPCOMING,
    LIVE,
    FINISHED,
    POSTPONED
}

/**
 * Extended match details with team names and channel information.
 */
data class MatchDetails(
    val id: Int,
    val competitionId: Int,
    val competitionName: String? = null,
    val homeTeamId: Int,
    val homeTeamName: String,
    val homeTeamLogo: String? = null,
    val awayTeamId: Int,
    val awayTeamName: String,
    val awayTeamLogo: String? = null,
    val channelId: Int? = null,
    val channelName: String? = null,
    val kickoff: String,
    val status: MatchStatus,
    val homeScore: Int? = null,
    val awayScore: Int? = null,
    val isActive: Boolean = true
) {
    /**
     * Check if the match is currently live.
     */
    val isLive: Boolean
        get() = status == MatchStatus.LIVE

    /**
     * Check if the match has finished.
     */
    val isFinished: Boolean
        get() = status == MatchStatus.FINISHED

    /**
     * Get the score display string (e.g., "2 - 1").
     */
    val scoreDisplay: String?
        get() = if (homeScore != null && awayScore != null) {
            "$homeScore - $awayScore"
        } else {
            null
        }
}
