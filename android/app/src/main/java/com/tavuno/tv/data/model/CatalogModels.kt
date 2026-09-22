package com.tavuno.tv.data.model

import com.google.gson.annotations.SerializedName

data class Channel(
    val id: Int,
    val name: String,
    val slug: String,
    @SerializedName("category_id")
    val categoryId: Int?,
    val logo: String?,
    @SerializedName("is_active")
    val isActive: Boolean
)

data class ChannelDetails(
    val id: Int,
    val name: String,
    val slug: String,
    @SerializedName("category_id")
    val categoryId: Int?,
    val logo: String?,
    @SerializedName("is_active")
    val isActive: Boolean,
    val description: String?,
    @SerializedName("category_name")
    val categoryName: String?,
    @SerializedName("playback_available")
    val playbackAvailable: Boolean
)

data class Category(
    val id: Int,
    val name: String,
    val kind: String,
    @SerializedName("parent_id")
    val parentId: Int?,
    @SerializedName("sort_order")
    val sortOrder: Int,
    @SerializedName("is_active")
    val isActive: Boolean
)

data class Movie(
    val id: Int,
    val title: String,
    val slug: String,
    @SerializedName("category_id")
    val categoryId: Int?,
    val synopsis: String?,
    @SerializedName("release_year")
    val releaseYear: Int?,
    @SerializedName("is_active")
    val isActive: Boolean
)

data class MovieDetails(
    val id: Int,
    val title: String,
    val slug: String,
    @SerializedName("category_id")
    val categoryId: Int?,
    val synopsis: String?,
    @SerializedName("release_year")
    val releaseYear: Int?,
    @SerializedName("is_active")
    val isActive: Boolean,
    val poster: String?,
    val backdrop: String?,
    val duration: String?,
    @SerializedName("category_name")
    val categoryName: String?,
    val genres: List<String>?,
    @SerializedName("playback_available")
    val playbackAvailable: Boolean
)

data class Series(
    val id: Int,
    val title: String,
    val slug: String,
    @SerializedName("category_id")
    val categoryId: Int?,
    val synopsis: String?,
    @SerializedName("is_active")
    val isActive: Boolean
)

data class SeriesDetails(
    val id: Int,
    val title: String,
    val slug: String,
    @SerializedName("category_id")
    val categoryId: Int?,
    val synopsis: String?,
    @SerializedName("is_active")
    val isActive: Boolean,
    val poster: String?,
    val backdrop: String?,
    @SerializedName("release_year")
    val releaseYear: Int?,
    @SerializedName("category_name")
    val categoryName: String?,
    val seasons: List<SeasonDetails>?,
    @SerializedName("episode_count")
    val episodeCount: Int?,
    @SerializedName("playback_available")
    val playbackAvailable: Boolean
)

data class SeasonInfo(
    val id: Int,
    @SerializedName("season_number")
    val seasonNumber: Int,
    val name: String,
    val poster: String?,
    @SerializedName("episode_count")
    val episodeCount: Int
)

data class SeasonDetails(
    val id: Int,
    @SerializedName("season_number")
    val seasonNumber: Int,
    val name: String,
    val poster: String?,
    @SerializedName("episode_count")
    val episodeCount: Int
)

data class EpisodeDetails(
    val id: Int,
    @SerializedName("episode_number")
    val episodeNumber: Int,
    val title: String,
    val synopsis: String?,
    val duration: String?,
    val thumbnail: String?,
    @SerializedName("playback_available")
    val playbackAvailable: Boolean
)

data class HomeData(
    val channels: List<Channel>,
    val categories: List<Category>,
    val movies: List<Movie>,
    val series: List<Series>
)
