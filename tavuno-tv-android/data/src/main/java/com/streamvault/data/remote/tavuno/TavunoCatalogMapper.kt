package com.streamvault.data.remote.tavuno

import com.streamvault.domain.model.Category
import com.streamvault.domain.model.Channel
import com.streamvault.domain.model.ChannelDetails
import com.streamvault.domain.model.ContentType
import com.streamvault.domain.model.Movie
import com.streamvault.domain.model.MovieDetails
import com.streamvault.domain.model.Series
import com.streamvault.domain.model.SeriesDetails
import com.streamvault.domain.model.SeasonInfo
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Maps Tavuno API DTOs to domain models.
 * Uses sensible defaults for provider-specific fields not applicable to Tavuno.
 */
@Singleton
class TavunoCatalogMapper @Inject constructor() {

    fun toCategory(dto: CategoryDto): Category {
        return Category(
            id = dto.id.toLong(),
            roomId = dto.id.toLong(),
            name = dto.name,
            parentId = dto.parentId?.toLong(),
            type = ContentType.LIVE, // Default to LIVE for Tavuno
            isVirtual = false,
            count = 0, // Tavuno doesn't provide count
            providerOrder = dto.sortOrder,
            isAdult = false,
            isUserProtected = false
        )
    }

    fun toChannel(dto: ChannelDto): Channel {
        return Channel(
            id = dto.id.toLong(),
            name = dto.name,
            canonicalName = dto.name,
            logoUrl = dto.logo,
            groupTitle = null, // Tavuno uses categories instead
            categoryId = dto.categoryId?.toLong(),
            categoryName = null,
            streamUrl = "", // Don't expose stream URL, use playback API
            epgChannelId = null,
            number = 0, // Tavuno doesn't have channel numbers
            isFavorite = false,
            catchUpSupported = false,
            catchUpDays = 0,
            catchUpSource = null,
            providerId = -1L, // Tavuno has no providerId
            currentProgram = null,
            nextProgram = null,
            isAdult = false,
            isUserProtected = false,
            logicalGroupId = "",
            selectedVariantId = dto.id.toLong(),
            errorCount = 0,
            qualityOptions = emptyList(),
            alternativeStreams = emptyList(),
            variants = emptyList(),
            streamId = 0L
        )
    }

    fun toMovie(dto: MovieDto): Movie {
        return Movie(
            id = dto.id.toLong(),
            name = dto.title,
            posterUrl = null, // Tavuno doesn't provide poster in basic DTO
            backdropUrl = null,
            categoryId = dto.categoryId?.toLong(),
            categoryName = null,
            streamUrl = "", // Don't expose stream URL
            containerExtension = null,
            plot = dto.synopsis,
            cast = null,
            director = null,
            genre = null,
            releaseDate = dto.releaseYear?.toString(),
            duration = null,
            durationSeconds = 0,
            rating = 0f,
            year = dto.releaseYear?.toString(),
            tmdbId = null,
            youtubeTrailer = null,
            isFavorite = false,
            providerId = -1L,
            watchProgress = 0L,
            lastWatchedAt = 0L,
            isAdult = false,
            isUserProtected = false,
            streamId = 0L,
            addedAt = 0L,
            logicalGroupId = null,
            selectedVariantId = null,
            variants = emptyList(),
            duplicateConfidence = com.streamvault.domain.model.VodDuplicateConfidence.NONE,
            variantLabel = null
        )
    }

    fun toSeries(dto: SeriesDto): Series {
        return Series(
            id = dto.id.toLong(),
            name = dto.title,
            posterUrl = null,
            backdropUrl = null,
            categoryId = dto.categoryId?.toLong(),
            categoryName = null,
            plot = dto.synopsis,
            cast = null,
            director = null,
            genre = null,
            releaseDate = null,
            rating = 0f,
            tmdbId = null,
            youtubeTrailer = null,
            isFavorite = false,
            providerId = -1L,
            seasons = emptyList(),
            episodeRunTime = null,
            lastModified = 0L,
            isAdult = false,
            isUserProtected = false,
            seriesId = 0L,
            providerSeriesId = null,
            logicalGroupId = "",
            selectedVariantId = null,
            variants = emptyList(),
            duplicateConfidence = com.streamvault.domain.model.VodDuplicateConfidence.NONE,
            variantLabel = null,
            catalogOrigin = com.streamvault.domain.model.SeriesCatalogOrigin.NATIVE,
            episodePlaybackTemplateUrl = null
        )
    }

    fun toChannelDetails(dto: ChannelDetailsDto): ChannelDetails {
        return ChannelDetails(
            id = dto.id.toLong(),
            name = dto.name,
            slug = dto.slug,
            categoryId = dto.categoryId?.toLong(),
            logo = dto.logo,
            isActive = dto.isActive,
            description = dto.description,
            categoryName = dto.categoryName,
            playbackAvailable = dto.playbackAvailable
        )
    }

    fun toMovieDetails(dto: MovieDetailsDto): MovieDetails {
        return MovieDetails(
            id = dto.id.toLong(),
            title = dto.title,
            slug = dto.slug,
            categoryId = dto.categoryId?.toLong(),
            synopsis = dto.synopsis,
            releaseYear = dto.releaseYear,
            isActive = dto.isActive,
            poster = dto.poster,
            backdrop = dto.backdrop,
            duration = dto.duration,
            categoryName = dto.categoryName,
            genres = dto.genres,
            playbackAvailable = dto.playbackAvailable
        )
    }

    fun toSeriesDetails(dto: SeriesDetailsDto): SeriesDetails {
        return SeriesDetails(
            id = dto.id.toLong(),
            title = dto.title,
            slug = dto.slug,
            categoryId = dto.categoryId?.toLong(),
            synopsis = dto.synopsis,
            isActive = dto.isActive,
            poster = dto.poster,
            backdrop = dto.backdrop,
            releaseYear = dto.releaseYear,
            categoryName = dto.categoryName,
            seasons = dto.seasons?.map { seasonDto ->
                SeasonInfo(
                    seasonNumber = seasonDto.seasonNumber,
                    name = seasonDto.name,
                    episodeCount = seasonDto.episodeCount
                )
            },
            episodeCount = dto.episodeCount,
            playbackAvailable = dto.playbackAvailable
        )
    }
}
