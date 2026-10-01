package com.tavuno.tv.data.repository

import com.tavuno.tv.data.api.TavunoApiService
import com.tavuno.tv.data.local.SessionManager
import com.tavuno.tv.data.model.*
import com.tavuno.tv.network.NetworkModule
import kotlinx.coroutines.flow.first

class CatalogRepository(
    private val apiService: TavunoApiService,
    private val sessionManager: SessionManager
) {

    private suspend fun ensureAuthToken() {
        val token = sessionManager.accessToken.first()
        if (token != null) {
            NetworkModule.updateAuthToken(token)
        }
    }

    private fun catalogError(code: Int, resource: String): String {
        return when (code) {
            401 -> "Session expired. Please log in again."
            403 -> "Access denied"
            else -> "Failed to load $resource"
        }
    }

    suspend fun getHome(): Result<HomeData> {
        return try {
            ensureAuthToken()
            val response = apiService.getHome()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(catalogError(response.code(), "home data")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getChannels(categoryId: Int? = null): Result<List<Channel>> {
        return try {
            ensureAuthToken()
            val response = apiService.getChannels(categoryId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(catalogError(response.code(), "channels")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getChannelDetails(channelId: Int): Result<ChannelDetails> {
        return try {
            ensureAuthToken()
            val response = apiService.getChannelDetails(channelId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(catalogError(response.code(), "channel details")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCategories(kind: String? = null): Result<List<Category>> {
        return try {
            ensureAuthToken()
            val response = apiService.getCategories(kind)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(catalogError(response.code(), "categories")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCategory(categoryId: Int): Result<Category> {
        return try {
            ensureAuthToken()
            val response = apiService.getCategory(categoryId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(catalogError(response.code(), "category")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Search channels, movies and series in one round trip (`GET /v1/search`).
     *
     * The backend trims, wildcard-escapes and live-channel-scopes the term; the app simply debounces
     * typing and renders the three groups.
     */
    suspend fun search(query: String, limit: Int? = null): Result<SearchResults> {
        return try {
            ensureAuthToken()
            val response = apiService.search(query, limit)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(catalogError(response.code(), "search results")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMovies(categoryId: Int? = null): Result<List<Movie>> {
        return try {
            ensureAuthToken()
            val response = apiService.getMovies(categoryId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(catalogError(response.code(), "movies")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMovieDetails(movieId: Int): Result<MovieDetails> {
        return try {
            ensureAuthToken()
            val response = apiService.getMovieDetails(movieId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(catalogError(response.code(), "movie details")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSeries(categoryId: Int? = null): Result<List<Series>> {
        return try {
            ensureAuthToken()
            val response = apiService.getSeries(categoryId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(catalogError(response.code(), "series")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSeriesDetails(seriesId: Int): Result<SeriesDetails> {
        return try {
            ensureAuthToken()
            val response = apiService.getSeriesDetails(seriesId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(catalogError(response.code(), "series details")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSeriesSeasons(seriesId: Int): Result<List<SeasonDetails>> {
        return try {
            ensureAuthToken()
            val response = apiService.getSeriesSeasons(seriesId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(catalogError(response.code(), "seasons")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSeasonEpisodes(seasonId: Int): Result<List<EpisodeDetails>> {
        return try {
            ensureAuthToken()
            val response = apiService.getSeasonEpisodes(seasonId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(catalogError(response.code(), "episodes")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getChannelNowNext(channelId: Int): Result<ChannelNowNext> {
        return try {
            ensureAuthToken()
            val response = apiService.getChannelNowNext(channelId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(catalogError(response.code(), "channel now/next")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
