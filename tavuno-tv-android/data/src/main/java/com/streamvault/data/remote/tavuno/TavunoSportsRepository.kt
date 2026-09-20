package com.streamvault.data.remote.tavuno

import com.streamvault.domain.model.Competition
import com.streamvault.domain.model.Match
import com.streamvault.domain.model.MatchDetails
import com.streamvault.domain.model.Result
import com.streamvault.domain.model.Team
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for sports data from Tavuno Control API (M11).
 * This provides competitions, teams, and matches for the sports experience.
 */
@Singleton
class TavunoSportsRepository @Inject constructor(
    private val tavunoCatalogRepository: TavunoCatalogRepository,
    private val sportsMapper: TavunoSportsMapper
) {

    suspend fun getCompetitions(sport: String? = null): Result<List<Competition>> {
        return when (val result = tavunoCatalogRepository.getCompetitions(sport)) {
            is com.streamvault.domain.model.Result.Success -> {
                val competitions = result.data.map { sportsMapper.mapCompetition(it) }
                com.streamvault.domain.model.Result.success(competitions)
            }
            is com.streamvault.domain.model.Result.Error -> {
                result
            }
            is com.streamvault.domain.model.Result.Loading -> {
                com.streamvault.domain.model.Result.Loading
            }
        }
    }

    suspend fun getCompetition(competitionId: Int): Result<Competition> {
        return when (val result = tavunoCatalogRepository.getCompetition(competitionId)) {
            is com.streamvault.domain.model.Result.Success -> {
                com.streamvault.domain.model.Result.success(sportsMapper.mapCompetition(result.data))
            }
            is com.streamvault.domain.model.Result.Error -> {
                result
            }
            is com.streamvault.domain.model.Result.Loading -> {
                com.streamvault.domain.model.Result.Loading
            }
        }
    }

    suspend fun getCompetitionMatches(
        competitionId: Int,
        status: String? = null,
        limit: Int = 100
    ): Result<List<Match>> {
        return when (val result = tavunoCatalogRepository.getCompetitionMatches(competitionId, status, limit)) {
            is com.streamvault.domain.model.Result.Success -> {
                val matches = result.data.map { sportsMapper.mapMatch(it) }
                com.streamvault.domain.model.Result.success(matches)
            }
            is com.streamvault.domain.model.Result.Error -> {
                result
            }
            is com.streamvault.domain.model.Result.Loading -> {
                com.streamvault.domain.model.Result.Loading
            }
        }
    }

    suspend fun getMatches(status: String? = null, limit: Int = 100): Result<List<Match>> {
        return when (val result = tavunoCatalogRepository.getMatches(status, limit)) {
            is com.streamvault.domain.model.Result.Success -> {
                val matches = result.data.map { sportsMapper.mapMatch(it) }
                com.streamvault.domain.model.Result.success(matches)
            }
            is com.streamvault.domain.model.Result.Error -> {
                result
            }
            is com.streamvault.domain.model.Result.Loading -> {
                com.streamvault.domain.model.Result.Loading
            }
        }
    }

    suspend fun getMatch(matchId: Int): Result<Match> {
        return when (val result = tavunoCatalogRepository.getMatch(matchId)) {
            is com.streamvault.domain.model.Result.Success -> {
                com.streamvault.domain.model.Result.success(sportsMapper.mapMatch(result.data))
            }
            is com.streamvault.domain.model.Result.Error -> {
                result
            }
            is com.streamvault.domain.model.Result.Loading -> {
                com.streamvault.domain.model.Result.Loading
            }
        }
    }

    suspend fun getMatchDetails(matchId: Int): Result<MatchDetails> {
        return when (val result = tavunoCatalogRepository.getMatchDetails(matchId)) {
            is com.streamvault.domain.model.Result.Success -> {
                com.streamvault.domain.model.Result.success(sportsMapper.mapMatchDetails(result.data))
            }
            is com.streamvault.domain.model.Result.Error -> {
                result
            }
            is com.streamvault.domain.model.Result.Loading -> {
                com.streamvault.domain.model.Result.Loading
            }
        }
    }
}
