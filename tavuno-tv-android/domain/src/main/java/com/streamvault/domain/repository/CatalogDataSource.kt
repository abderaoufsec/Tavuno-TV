package com.streamvault.domain.repository

import com.streamvault.domain.model.Category
import com.streamvault.domain.model.Channel
import com.streamvault.domain.model.ChannelDetails
import com.streamvault.domain.model.Movie
import com.streamvault.domain.model.MovieDetails
import com.streamvault.domain.model.Series
import com.streamvault.domain.model.SeriesDetails
import com.streamvault.domain.model.Result
import kotlinx.coroutines.flow.Flow

/**
 * Marker interface for catalog data source types.
 * Used to identify whether catalog data comes from Tavuno Control or external providers.
 */
sealed interface CatalogDataSource {
    val sourceType: CatalogSourceType
    val id: String // Unique identifier for this data source

    /**
     * Tavuno Control - centralized server catalog
     */
    data class Tavuno(
        override val id: String = "tavuno",
        override val sourceType: CatalogSourceType = CatalogSourceType.TAVUNO
    ) : CatalogDataSource

    /**
     * External provider - user-configured IPTV provider
     */
    data class Provider(
        override val id: String, // providerId as string
        override val sourceType: CatalogSourceType = CatalogSourceType.PROVIDER
    ) : CatalogDataSource
}

enum class CatalogSourceType {
    TAVUNO,
    PROVIDER
}

/**
 * Repository for unified catalog access across different data sources.
 */
interface UnifiedCatalogRepository {
    /**
     * Get the active catalog data source.
     * Returns Tavuno if authenticated, otherwise the active provider.
     */
    fun getActiveDataSource(): Flow<CatalogDataSource>

    /**
     * Set the active catalog data source.
     */
    suspend fun setActiveDataSource(source: CatalogDataSource): Result<Unit>

    /**
     * Get categories from the active data source.
     */
    suspend fun getCategories(): Result<List<Category>>

    /**
     * Get channels from the active data source.
     */
    suspend fun getChannels(categoryId: Long? = null): Result<List<Channel>>

    /**
     * Get a single channel from the active data source.
     */
    suspend fun getChannel(channelId: Long): Result<Channel?>

    /**
     * Get movies from the active data source.
     */
    suspend fun getMovies(categoryId: Long? = null): Result<List<Movie>>

    /**
     * Get a single movie from the active data source.
     */
    suspend fun getMovie(movieId: Long): Result<Movie?>

    /**
     * Get series from the active data source.
     */
    suspend fun getSeries(categoryId: Long? = null): Result<List<Series>>

    /**
     * Get a single series from the active data source.
     */
    suspend fun getSeries(seriesId: Long): Result<Series?>

    /**
     * Get detailed channel information for content detail screens (M12).
     */
    suspend fun getChannelDetails(channelId: Long): Result<ChannelDetails?>

    /**
     * Get detailed movie information for content detail screens (M12).
     */
    suspend fun getMovieDetails(movieId: Long): Result<MovieDetails?>

    /**
     * Get detailed series information for content detail screens (M12).
     */
    suspend fun getSeriesDetails(seriesId: Long): Result<SeriesDetails?>
}
