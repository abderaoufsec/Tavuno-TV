package com.streamvault.data.remote.tavuno

import com.streamvault.domain.model.Competition
import com.streamvault.domain.model.Match
import com.streamvault.domain.model.MatchDetails
import com.streamvault.domain.model.MatchStatus
import com.streamvault.domain.model.Team
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mapper for converting Tavuno sports DTOs to domain models.
 */
@Singleton
class TavunoSportsMapper @Inject constructor() {

    fun mapCompetition(dto: CompetitionDto): Competition {
        return Competition(
            id = dto.id,
            name = dto.name,
            slug = dto.slug,
            sport = dto.sport,
            categoryId = dto.categoryId,
            externalId = dto.externalId,
            isActive = dto.isActive
        )
    }

    fun mapTeam(dto: TeamDto): Team {
        return Team(
            id = dto.id,
            name = dto.name,
            slug = dto.slug,
            competitionId = dto.competitionId,
            logo = dto.logo,
            externalId = dto.externalId,
            isActive = dto.isActive
        )
    }

    fun mapMatch(dto: MatchDto): Match {
        return Match(
            id = dto.id,
            competitionId = dto.competitionId,
            homeTeamId = dto.homeTeamId,
            awayTeamId = dto.awayTeamId,
            channelId = dto.channelId,
            kickoff = dto.kickoff,
            status = mapMatchStatus(dto.status),
            homeScore = dto.homeScore,
            awayScore = dto.awayScore,
            externalId = dto.externalId,
            isActive = dto.isActive
        )
    }

    fun mapMatchDetails(dto: MatchDetailsDto): MatchDetails {
        return MatchDetails(
            id = dto.id,
            competitionId = dto.competitionId,
            competitionName = dto.competitionName,
            homeTeamId = dto.homeTeamId,
            homeTeamName = dto.homeTeamName,
            homeTeamLogo = dto.homeTeamLogo,
            awayTeamId = dto.awayTeamId,
            awayTeamName = dto.awayTeamName,
            awayTeamLogo = dto.awayTeamLogo,
            channelId = dto.channelId,
            channelName = dto.channelName,
            kickoff = dto.kickoff,
            status = mapMatchStatus(dto.status),
            homeScore = dto.homeScore,
            awayScore = dto.awayScore,
            isActive = dto.isActive
        )
    }

    private fun mapMatchStatus(status: String): MatchStatus {
        return when (status.lowercase()) {
            "upcoming" -> MatchStatus.UPCOMING
            "live" -> MatchStatus.LIVE
            "finished" -> MatchStatus.FINISHED
            "postponed" -> MatchStatus.POSTPONED
            else -> MatchStatus.UPCOMING
        }
    }
}
