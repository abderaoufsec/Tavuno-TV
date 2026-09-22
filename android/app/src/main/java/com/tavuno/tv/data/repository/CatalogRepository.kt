package com.tavuno.tv.data.repository

import com.tavuno.tv.data.api.TavunoApiService
import com.tavuno.tv.data.model.*

class CatalogRepository(private val apiService: TavunoApiService) {
    
    suspend fun getHome(): Result<HomeData> {
        return try {
            val response = apiService.getHome()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to load home data"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getChannels(categoryId: Int? = null): Result<List<Channel>> {
        return try {
            val response = apiService.getChannels(categoryId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to load channels"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getChannelDetails(channelId: Int): Result<ChannelDetails> {
        return try {
            val response = apiService.getChannelDetails(channelId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to load channel details"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getCategories(kind: String? = null): Result<List<Category>> {
        return try {
            val response = apiService.getCategories(kind)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to load categories"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getCategory(categoryId: Int): Result<Category> {
        return try {
            val response = apiService.getCategory(categoryId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to load category"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getMovies(categoryId: Int? = null): Result<List<Movie>> {
        return try {
            val response = apiService.getMovies(categoryId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to load movies"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getMovieDetails(movieId: Int): Result<MovieDetails> {
        return try {
            val response = apiService.getMovieDetails(movieId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to load movie details"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getSeries(categoryId: Int? = null): Result<List<Series>> {
        return try {
            val response = apiService.getSeries(categoryId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to load series"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getSeriesDetails(seriesId: Int): Result<SeriesDetails> {
        return try {
            val response = apiService.getSeriesDetails(seriesId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to load series details"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getSeriesSeasons(seriesId: Int): Result<List<SeasonDetails>> {
        return try {
            val response = apiService.getSeriesSeasons(seriesId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to load seasons"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getSeasonEpisodes(seasonId: Int): Result<List<EpisodeDetails>> {
        return try {
            val response = apiService.getSeasonEpisodes(seasonId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to load episodes"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
