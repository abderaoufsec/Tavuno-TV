package com.streamvault.data.remote.tavuno

import com.streamvault.data.preferences.TokenStore
import com.streamvault.domain.model.Result
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for fetching catalog data from Tavuno Control API.
 * This provides the Tavuno-owned catalog data (channels, categories, movies, series).
 */
@Singleton
class TavunoCatalogRepository @Inject constructor(
    private val tavunoApiService: TavunoApiService,
    private val tokenStore: TokenStore
) {

    private suspend fun getAuthToken(): String? {
        return tokenStore.getAccessToken()
    }

    suspend fun getHome(): Result<HomeResponse> {
        return try {
            val token = getAuthToken()
            if (token == null) {
                return Result.error("Not authenticated")
            }
            val response = tavunoApiService.getHome()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Result.error(errorBody)
            }
        } catch (e: Exception) {
            Result.error(e.message ?: "Failed to fetch home data")
        }
    }

    suspend fun getChannels(categoryId: Int? = null): Result<List<ChannelDto>> {
        return try {
            val token = getAuthToken()
            if (token == null) {
                return Result.error("Not authenticated")
            }
            val response = tavunoApiService.getChannels(categoryId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Result.error(errorBody)
            }
        } catch (e: Exception) {
            Result.error(e.message ?: "Failed to fetch channels")
        }
    }

    suspend fun getChannel(channelId: Int): Result<ChannelDetailDto> {
        return try {
            val token = getAuthToken()
            if (token == null) {
                return Result.error("Not authenticated")
            }
            val response = tavunoApiService.getChannel(channelId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Result.error(errorBody)
            }
        } catch (e: Exception) {
            Result.error(e.message ?: "Failed to fetch channel")
        }
    }

    suspend fun getCategories(kind: String? = null): Result<List<CategoryDto>> {
        return try {
            val token = getAuthToken()
            if (token == null) {
                return Result.error("Not authenticated")
            }
            val response = tavunoApiService.getCategories(kind)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Result.error(errorBody)
            }
        } catch (e: Exception) {
            Result.error(e.message ?: "Failed to fetch categories")
        }
    }

    suspend fun getCategory(categoryId: Int): Result<CategoryDto> {
        return try {
            val token = getAuthToken()
            if (token == null) {
                return Result.error("Not authenticated")
            }
            val response = tavunoApiService.getCategory(categoryId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Result.error(errorBody)
            }
        } catch (e: Exception) {
            Result.error(e.message ?: "Failed to fetch category")
        }
    }

    suspend fun getMovies(categoryId: Int? = null): Result<List<MovieDto>> {
        return try {
            val token = getAuthToken()
            if (token == null) {
                return Result.error("Not authenticated")
            }
            val response = tavunoApiService.getMovies(categoryId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Result.error(errorBody)
            }
        } catch (e: Exception) {
            Result.error(e.message ?: "Failed to fetch movies")
        }
    }

    suspend fun getMovie(movieId: Int): Result<MovieDto> {
        return try {
            val token = getAuthToken()
            if (token == null) {
                return Result.error("Not authenticated")
            }
            val response = tavunoApiService.getMovie(movieId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Result.error(errorBody)
            }
        } catch (e: Exception) {
            Result.error(e.message ?: "Failed to fetch movie")
        }
    }

    suspend fun getSeries(categoryId: Int? = null): Result<List<SeriesDto>> {
        return try {
            val token = getAuthToken()
            if (token == null) {
                return Result.error("Not authenticated")
            }
            val response = tavunoApiService.getSeries(categoryId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Result.error(errorBody)
            }
        } catch (e: Exception) {
            Result.error(e.message ?: "Failed to fetch series")
        }
    }

    suspend fun getSeriesDetail(seriesId: Int): Result<SeriesDto> {
        return try {
            val token = getAuthToken()
            if (token == null) {
                return Result.error("Not authenticated")
            }
            val response = tavunoApiService.getSeries(seriesId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Result.error(errorBody)
            }
        } catch (e: Exception) {
            Result.error(e.message ?: "Failed to fetch series")
        }
    }

    suspend fun getSports(): Result<List<CategoryDto>> {
        return try {
            val token = getAuthToken()
            if (token == null) {
                return Result.error("Not authenticated")
            }
            val response = tavunoApiService.getSports()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Result.error(errorBody)
            }
        } catch (e: Exception) {
            Result.error(e.message ?: "Failed to fetch sports")
        }
    }

    suspend fun getChannelDetails(channelId: Int): Result<ChannelDetailsDto> {
        return try {
            val token = getAuthToken()
            if (token == null) {
                return Result.error("Not authenticated")
            }
            val response = tavunoApiService.getChannelDetails(channelId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Result.error(errorBody)
            }
        } catch (e: Exception) {
            Result.error(e.message ?: "Failed to fetch channel details")
        }
    }

    suspend fun getMovieDetails(movieId: Int): Result<MovieDetailsDto> {
        return try {
            val token = getAuthToken()
            if (token == null) {
                return Result.error("Not authenticated")
            }
            val response = tavunoApiService.getMovieDetails(movieId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Result.error(errorBody)
            }
        } catch (e: Exception) {
            Result.error(e.message ?: "Failed to fetch movie details")
        }
    }

    suspend fun getSeriesDetails(seriesId: Int): Result<SeriesDetailsDto> {
        return try {
            val token = getAuthToken()
            if (token == null) {
                return Result.error("Not authenticated")
            }
            val response = tavunoApiService.getSeriesDetails(seriesId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Result.error(errorBody)
            }
        } catch (e: Exception) {
            Result.error(e.message ?: "Failed to fetch series details")
        }
    }

    suspend fun authorizeLivePlayback(channelId: Int): Result<PlaybackResponse> {
        return try {
            val token = getAuthToken()
            if (token == null) {
                return Result.error("Not authenticated")
            }
            val response = tavunoApiService.authorizeLivePlayback(channelId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Result.error(errorBody)
            }
        } catch (e: Exception) {
            Result.error(e.message ?: "Failed to authorize playback")
        }
    }

    suspend fun heartbeatPlayback(sessionId: Int): Result<SessionResponse> {
        return try {
            val token = getAuthToken()
            if (token == null) {
                return Result.error("Not authenticated")
            }
            val response = tavunoApiService.heartbeatPlayback(SessionRequest(sessionId))
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Result.error(errorBody)
            }
        } catch (e: Exception) {
            Result.error(e.message ?: "Failed to send heartbeat")
        }
    }

    suspend fun stopPlayback(sessionId: Int): Result<SessionResponse> {
        return try {
            val token = getAuthToken()
            if (token == null) {
                return Result.error("Not authenticated")
            }
            val response = tavunoApiService.stopPlayback(SessionRequest(sessionId))
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Result.error(errorBody)
            }
        } catch (e: Exception) {
            Result.error(e.message ?: "Failed to stop playback")
        }
    }

    // M11 Sports methods

    suspend fun getCompetitions(sport: String? = null): Result<List<CompetitionDto>> {
        return try {
            val token = getAuthToken()
            if (token == null) {
                return Result.error("Not authenticated")
            }
            val response = tavunoApiService.getCompetitions(sport)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Result.error(errorBody)
            }
        } catch (e: Exception) {
            Result.error(e.message ?: "Failed to fetch competitions")
        }
    }

    suspend fun getCompetition(competitionId: Int): Result<CompetitionDto> {
        return try {
            val token = getAuthToken()
            if (token == null) {
                return Result.error("Not authenticated")
            }
            val response = tavunoApiService.getCompetition(competitionId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Result.error(errorBody)
            }
        } catch (e: Exception) {
            Result.error(e.message ?: "Failed to fetch competition")
        }
    }

    suspend fun getCompetitionMatches(
        competitionId: Int,
        status: String? = null,
        limit: Int = 100
    ): Result<List<MatchDto>> {
        return try {
            val token = getAuthToken()
            if (token == null) {
                return Result.error("Not authenticated")
            }
            val response = tavunoApiService.getCompetitionMatches(competitionId, status, limit)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Result.error(errorBody)
            }
        } catch (e: Exception) {
            Result.error(e.message ?: "Failed to fetch competition matches")
        }
    }

    suspend fun getMatches(status: String? = null, limit: Int = 100): Result<List<MatchDto>> {
        return try {
            val token = getAuthToken()
            if (token == null) {
                return Result.error("Not authenticated")
            }
            val response = tavunoApiService.getMatches(status, limit)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Result.error(errorBody)
            }
        } catch (e: Exception) {
            Result.error(e.message ?: "Failed to fetch matches")
        }
    }

    suspend fun getMatch(matchId: Int): Result<MatchDto> {
        return try {
            val token = getAuthToken()
            if (token == null) {
                return Result.error("Not authenticated")
            }
            val response = tavunoApiService.getMatch(matchId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Result.error(errorBody)
            }
        } catch (e: Exception) {
            Result.error(e.message ?: "Failed to fetch match")
        }
    }

    suspend fun getMatchDetails(matchId: Int): Result<MatchDetailsDto> {
        return try {
            val token = getAuthToken()
            if (token == null) {
                return Result.error("Not authenticated")
            }
            val response = tavunoApiService.getMatchDetails(matchId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Result.error(errorBody)
            }
        } catch (e: Exception) {
            Result.error(e.message ?: "Failed to fetch match details")
        }
    }
}
