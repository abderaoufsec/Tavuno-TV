package com.tavuno.tv.data.repository

import com.tavuno.tv.data.api.TavunoApiService
import com.tavuno.tv.data.model.*

class SportsRepository(private val apiService: TavunoApiService) {
    
    suspend fun getCompetitions(sport: String? = null): Result<List<Competition>> {
        return try {
            val response = apiService.getCompetitions(sport)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to load competitions"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getCompetition(competitionId: Int): Result<Competition> {
        return try {
            val response = apiService.getCompetition(competitionId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to load competition"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getCompetitionMatches(
        competitionId: Int,
        status: String? = null,
        limit: Int = 100
    ): Result<List<Match>> {
        return try {
            val response = apiService.getCompetitionMatches(competitionId, status, limit)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to load competition matches"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getMatches(status: String? = null, limit: Int = 100): Result<List<Match>> {
        return try {
            val response = apiService.getMatches(status, limit)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to load matches"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getMatchDetails(matchId: Int): Result<MatchDetails> {
        return try {
            val response = apiService.getMatchDetails(matchId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to load match details"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
