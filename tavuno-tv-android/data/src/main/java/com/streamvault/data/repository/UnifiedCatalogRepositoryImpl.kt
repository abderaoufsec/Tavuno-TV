package com.streamvault.data.repository

import com.streamvault.data.preferences.TavunoDataSourceStore
import com.streamvault.data.remote.tavuno.TavunoCatalogMapper
import com.streamvault.data.remote.tavuno.TavunoCatalogRepository
import com.streamvault.domain.model.Category
import com.streamvault.domain.model.Channel
import com.streamvault.domain.model.ChannelDetails
import com.streamvault.domain.model.Movie
import com.streamvault.domain.model.MovieDetails
import com.streamvault.domain.model.Result
import com.streamvault.domain.model.Series
import com.streamvault.domain.model.SeriesDetails
import com.streamvault.domain.repository.AuthRepository
import com.streamvault.domain.repository.CatalogDataSource
import com.streamvault.domain.repository.ChannelRepository
import com.streamvault.domain.repository.MovieRepository
import com.streamvault.domain.repository.ProviderRepository
import com.streamvault.domain.repository.SeriesRepository
import com.streamvault.domain.repository.UnifiedCatalogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UnifiedCatalogRepositoryImpl @Inject constructor(
    private val tavunoCatalogRepository: TavunoCatalogRepository,
    private val tavunoCatalogMapper: TavunoCatalogMapper,
    private val authRepository: AuthRepository,
    private val providerRepository: ProviderRepository,
    private val channelRepository: ChannelRepository,
    private val movieRepository: MovieRepository,
    private val seriesRepository: SeriesRepository,
    private val tavunoDataSourceStore: TavunoDataSourceStore
) : UnifiedCatalogRepository {

    override fun getActiveDataSource(): Flow<CatalogDataSource> {
        return tavunoDataSourceStore.getDataSource().map { sourceType ->
            when (sourceType) {
                TavunoDataSourceStore.DataSourceType.TAVUNO -> CatalogDataSource.Tavuno()
                TavunoDataSourceStore.DataSourceType.PROVIDER -> {
                    val activeProvider = providerRepository.getActiveProvider().first()
                    if (activeProvider != null) {
                        CatalogDataSource.Provider(activeProvider.id.toString())
                    } else {
                        CatalogDataSource.Tavuno() // Fallback to Tavuno if no provider
                    }
                }
            }
        }
    }

    override suspend fun setActiveDataSource(source: CatalogDataSource): Result<Unit> {
        return try {
            val sourceType = when (source) {
                is CatalogDataSource.Tavuno -> TavunoDataSourceStore.DataSourceType.TAVUNO
                is CatalogDataSource.Provider -> TavunoDataSourceStore.DataSourceType.PROVIDER
            }
            tavunoDataSourceStore.setDataSource(sourceType)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.error(e.message ?: "Failed to set data source")
        }
    }

    override suspend fun getCategories(): Result<List<Category>> {
        val source = getActiveDataSource().first()
        return when (source) {
            is CatalogDataSource.Tavuno -> getCategoriesFromTavuno()
            is CatalogDataSource.Provider -> getCategoriesFromProvider(source.id.toLong())
        }
    }

    override suspend fun getChannels(categoryId: Long?): Result<List<Channel>> {
        val source = getActiveDataSource().first()
        return when (source) {
            is CatalogDataSource.Tavuno -> getChannelsFromTavuno(categoryId)
            is CatalogDataSource.Provider -> getChannelsFromProvider(source.id.toLong(), categoryId)
        }
    }

    override suspend fun getChannel(channelId: Long): Result<Channel?> {
        val source = getActiveDataSource().first()
        return when (source) {
            is CatalogDataSource.Tavuno -> getChannelFromTavuno(channelId)
            is CatalogDataSource.Provider -> getChannelFromProvider(channelId)
        }
    }

    override suspend fun getMovies(categoryId: Long?): Result<List<Movie>> {
        val source = getActiveDataSource().first()
        return when (source) {
            is CatalogDataSource.Tavuno -> getMoviesFromTavuno(categoryId)
            is CatalogDataSource.Provider -> getMoviesFromProvider(source.id.toLong(), categoryId)
        }
    }

    override suspend fun getMovie(movieId: Long): Result<Movie?> {
        val source = getActiveDataSource().first()
        return when (source) {
            is CatalogDataSource.Tavuno -> getMovieFromTavuno(movieId)
            is CatalogDataSource.Provider -> getMovieFromProvider(movieId)
        }
    }

    override suspend fun getSeries(categoryId: Long?): Result<List<Series>> {
        val source = getActiveDataSource().first()
        return when (source) {
            is CatalogDataSource.Tavuno -> getSeriesFromTavuno(categoryId)
            is CatalogDataSource.Provider -> getSeriesFromProvider(source.id.toLong(), categoryId)
        }
    }

    override suspend fun getSeries(seriesId: Long): Result<Series?> {
        val source = getActiveDataSource().first()
        return when (source) {
            is CatalogDataSource.Tavuno -> getSeriesFromTavuno(seriesId)
            is CatalogDataSource.Provider -> getSeriesFromProvider(seriesId)
        }
    }

    override suspend fun getChannelDetails(channelId: Long): Result<ChannelDetails?> {
        val source = getActiveDataSource().first()
        return when (source) {
            is CatalogDataSource.Tavuno -> getChannelDetailsFromTavuno(channelId)
            is CatalogDataSource.Provider -> getChannelDetailsFromProvider(channelId)
        }
    }

    override suspend fun getMovieDetails(movieId: Long): Result<MovieDetails?> {
        val source = getActiveDataSource().first()
        return when (source) {
            is CatalogDataSource.Tavuno -> getMovieDetailsFromTavuno(movieId)
            is CatalogDataSource.Provider -> getMovieDetailsFromProvider(movieId)
        }
    }

    override suspend fun getSeriesDetails(seriesId: Long): Result<SeriesDetails?> {
        val source = getActiveDataSource().first()
        return when (source) {
            is CatalogDataSource.Tavuno -> getSeriesDetailsFromTavuno(seriesId)
            is CatalogDataSource.Provider -> getSeriesDetailsFromProvider(seriesId)
        }
    }

    // Tavuno methods
    private suspend fun getCategoriesFromTavuno(): Result<List<Category>> {
        return tavunoCatalogRepository.getCategories().map { dtoList ->
            dtoList.map { tavunoCatalogMapper.toCategory(it) }
        }
    }

    private suspend fun getChannelsFromTavuno(categoryId: Long?): Result<List<Channel>> {
        return tavunoCatalogRepository.getChannels(categoryId?.toInt()).map { dtoList ->
            dtoList.map { tavunoCatalogMapper.toChannel(it) }
        }
    }

    private suspend fun getChannelFromTavuno(channelId: Long): Result<Channel?> {
        return tavunoCatalogRepository.getChannels().map { dtoList ->
            dtoList.firstOrNull { it.id == channelId.toInt() }?.let { tavunoCatalogMapper.toChannel(it) }
        }
    }

    private suspend fun getMoviesFromTavuno(categoryId: Long?): Result<List<Movie>> {
        return tavunoCatalogRepository.getMovies(categoryId?.toInt()).map { dtoList ->
            dtoList.map { tavunoCatalogMapper.toMovie(it) }
        }
    }

    private suspend fun getMovieFromTavuno(movieId: Long): Result<Movie?> {
        return tavunoCatalogRepository.getMovie(movieId.toInt()).map { dto ->
            dto?.let { tavunoCatalogMapper.toMovie(it) }
        }
    }

    private suspend fun getSeriesFromTavuno(categoryId: Long?): Result<List<Series>> {
        return tavunoCatalogRepository.getSeries(categoryId?.toInt()).map { dtoList ->
            dtoList.map { tavunoCatalogMapper.toSeries(it) }
        }
    }

    private suspend fun getSeriesFromTavuno(seriesId: Long): Result<Series?> {
        return tavunoCatalogRepository.getSeriesDetail(seriesId.toInt()).map { dto ->
            dto?.let { tavunoCatalogMapper.toSeries(it) }
        }
    }

    private suspend fun getChannelDetailsFromTavuno(channelId: Long): Result<ChannelDetails?> {
        return tavunoCatalogRepository.getChannelDetails(channelId.toInt()).map { dto ->
            dto?.let { tavunoCatalogMapper.toChannelDetails(it) }
        }
    }

    private suspend fun getMovieDetailsFromTavuno(movieId: Long): Result<MovieDetails?> {
        return tavunoCatalogRepository.getMovieDetails(movieId.toInt()).map { dto ->
            dto?.let { tavunoCatalogMapper.toMovieDetails(it) }
        }
    }

    private suspend fun getSeriesDetailsFromTavuno(seriesId: Long): Result<SeriesDetails?> {
        return tavunoCatalogRepository.getSeriesDetails(seriesId.toInt()).map { dto ->
            dto?.let { tavunoCatalogMapper.toSeriesDetails(it) }
        }
    }

    // Provider methods (delegating to existing repositories)
    private suspend fun getCategoriesFromProvider(providerId: Long): Result<List<Category>> {
        return try {
            val categories = mutableListOf<Category>()
            channelRepository.getCategories(providerId).collect { catList ->
                categories.addAll(catList)
            }
            Result.success(categories)
        } catch (e: Exception) {
            Result.error(e.message ?: "Failed to get provider categories")
        }
    }

    private suspend fun getChannelsFromProvider(providerId: Long, categoryId: Long?): Result<List<Channel>> {
        return try {
            val categoryIdFinal = categoryId ?: ChannelRepository.ALL_CHANNELS_ID
            val channels = channelRepository.getChannelsByCategoryPageOffset(
                providerId = providerId,
                categoryId = categoryIdFinal,
                limit = 1000,
                offset = 0
            )
            Result.success(channels)
        } catch (e: Exception) {
            Result.error(e.message ?: "Failed to get provider channels")
        }
    }

    private suspend fun getChannelFromProvider(channelId: Long): Result<Channel?> {
        return try {
            val channel = channelRepository.getChannel(channelId)
            Result.success(channel)
        } catch (e: Exception) {
            Result.error(e.message ?: "Failed to get provider channel")
        }
    }

    private suspend fun getMoviesFromProvider(providerId: Long, categoryId: Long?): Result<List<Movie>> {
        return try {
            // Movies repository would need similar methods
            Result.error("Provider movies not yet implemented in unified catalog")
        } catch (e: Exception) {
            Result.error(e.message ?: "Failed to get provider movies")
        }
    }

    private suspend fun getMovieFromProvider(movieId: Long): Result<Movie?> {
        return try {
            // Movies repository would need similar methods
            Result.error("Provider movies not yet implemented in unified catalog")
        } catch (e: Exception) {
            Result.error(e.message ?: "Failed to get provider movie")
        }
    }

    private suspend fun getSeriesFromProvider(providerId: Long, categoryId: Long?): Result<List<Series>> {
        return try {
            // Series repository would need similar methods
            Result.error("Provider series not yet implemented in unified catalog")
        } catch (e: Exception) {
            Result.error(e.message ?: "Failed to get provider series")
        }
    }

    private suspend fun getSeriesFromProvider(seriesId: Long): Result<Series?> {
        return try {
            // Series repository would need similar methods
            Result.error("Provider series not yet implemented in unified catalog")
        } catch (e: Exception) {
            Result.error(e.message ?: "Failed to get provider series")
        }
    }

    private suspend fun getChannelDetailsFromProvider(channelId: Long): Result<ChannelDetails?> {
        return try {
            // For providers, we can use the existing channel model and map to details
            val channel = channelRepository.getChannel(channelId)
            val details = channel?.let {
                ChannelDetails(
                    id = it.id,
                    name = it.name,
                    slug = it.name.lowercase().replace(" ", "-"),
                    categoryId = it.categoryId,
                    logo = it.logoUrl,
                    isActive = true,
                    description = null,
                    categoryName = it.categoryName,
                    playbackAvailable = true
                )
            }
            Result.success(details)
        } catch (e: Exception) {
            Result.error(e.message ?: "Failed to get provider channel details")
        }
    }

    private suspend fun getMovieDetailsFromProvider(movieId: Long): Result<MovieDetails?> {
        return try {
            // Movies repository would need similar methods
            Result.error("Provider movie details not yet implemented in unified catalog")
        } catch (e: Exception) {
            Result.error(e.message ?: "Failed to get provider movie details")
        }
    }

    private suspend fun getSeriesDetailsFromProvider(seriesId: Long): Result<SeriesDetails?> {
        return try {
            // Series repository would need similar methods
            Result.error("Provider series details not yet implemented in unified catalog")
        } catch (e: Exception) {
            Result.error(e.message ?: "Failed to get provider series details")
        }
    }
}
