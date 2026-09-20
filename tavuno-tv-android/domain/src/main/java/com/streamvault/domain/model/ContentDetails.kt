package com.streamvault.domain.model

/**
 * Content detail models for M12 content detail screens.
 * These models provide extended metadata for channel, movie, and series detail views.
 */

data class ChannelDetails(
    val id: Long,
    val name: String,
    val slug: String,
    val categoryId: Long? = null,
    val logo: String? = null,
    val isActive: Boolean = true,
    val description: String? = null,
    val categoryName: String? = null,
    val playbackAvailable: Boolean = true
)

data class MovieDetails(
    val id: Long,
    val title: String,
    val slug: String,
    val categoryId: Long? = null,
    val synopsis: String? = null,
    val releaseYear: Int? = null,
    val isActive: Boolean = true,
    val poster: String? = null,
    val backdrop: String? = null,
    val duration: String? = null,
    val categoryName: String? = null,
    val genres: List<String>? = null,
    val playbackAvailable: Boolean = true
)

data class SeriesDetails(
    val id: Long,
    val title: String,
    val slug: String,
    val categoryId: Long? = null,
    val synopsis: String? = null,
    val isActive: Boolean = true,
    val poster: String? = null,
    val backdrop: String? = null,
    val releaseYear: Int? = null,
    val categoryName: String? = null,
    val seasons: List<SeasonInfo>? = null,
    val episodeCount: Int? = null,
    val playbackAvailable: Boolean = true
)

data class SeasonInfo(
    val seasonNumber: Int,
    val name: String,
    val episodeCount: Int
)

data class EpisodeDetails(
    val id: Long,
    val title: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val synopsis: String? = null,
    val duration: String? = null,
    val thumbnail: String? = null,
    val playbackAvailable: Boolean = true
)
