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
            val response = tavunoApiService.authorizeLivePlayback(
                channelId,
                PlaybackRequest(deviceKey = null) // Device key handled by auth interceptor
            )
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
}
