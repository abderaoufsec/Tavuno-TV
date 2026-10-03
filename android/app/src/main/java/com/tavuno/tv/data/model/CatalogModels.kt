package com.tavuno.tv.data.model

import com.google.gson.annotations.SerializedName

/**
 * Per-item viewer state the API projects onto every catalog read (backend A6).
 *
 * Null on a model built before this field existed, and Gson leaves it null when
 * the API omits it, so `item.viewer?.isFavourite == true` is the only safe read.
 */
data class ViewerState(
    @SerializedName("is_favourite")
    val isFavourite: Boolean = false,
    /** 0..1 resume position, or null when there is no bar to draw (live content). */
    val progress: Float? = null,
    @SerializedName("position_ms")
    val positionMs: Long = 0L
)

data class Channel(
    val id: Int,
    val name: String,
    val slug: String,
    @SerializedName("category_id")
    val categoryId: Int?,
    val logo: String?,
    @SerializedName("is_active")
    val isActive: Boolean,
    val viewer: ViewerState? = null
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
    val isActive: Boolean,
    // Populated by the API as a Directus /assets/<uuid> URL (backend A6); absent
    // today, in which case Gson leaves it null and PosterCard draws its tonal
    // placeholder.
    val poster: String? = null,
    val viewer: ViewerState? = null
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
    val isActive: Boolean,
    // Same forward-compatibility note as [Movie.poster].
    val poster: String? = null,
    val viewer: ViewerState? = null
)

/**
 * Grouped payload for `GET /v1/search` (Slice B): one query, three collections. Every group is
 * always present (possibly empty) so the search UI can render sections without null checks.
 */
data class SearchResults(
    val query: String,
    val channels: List<Channel>,
    val movies: List<Movie>,
    val series: List<Series>
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

/**
 * `GET /v1/home` (backend A6).
 *
 * The four rails the home screen renders. `continueWatching` and `favourites`
 * are always present — empty lists for a profile with nothing stored — so no
 * screen has to null-check them; the API guarantees the keys.
 */
data class HomeData(
    val channels: List<Channel>,
    val categories: List<Category>,
    val movies: List<Movie>,
    val series: List<Series>,
    @SerializedName("continue_watching")
    val continueWatching: List<HomeRailItem> = emptyList(),
    val favourites: List<HomeRailItem> = emptyList()
)

/**
 * One entry on a personal home rail: the catalog item plus the state that put it
 * there. `kind` is which table the id came from (`channel`/`movie`/`series`),
 * which a client needs in order to pick the item back up.
 */
data class HomeRailItem(
    val id: Int,
    val kind: String,
    val title: String? = null,
    val name: String? = null,
    val poster: String? = null,
    val logo: String? = null,
    val progress: Float? = null,
    @SerializedName("position_ms")
    val positionMs: Long? = null
)

/** `GET /v1/favourites/{kind}` — the profile's favourites for one kind. */
data class FavouriteList(
    val kind: String,
    val items: List<HomeRailItem> = emptyList()
)

/**
 * A toggle request. Leaving [isFavourite] null asks the API to flip the current
 * value, so the client never has to read the row to know what the heart does.
 */
data class FavouriteToggle(
    val kind: String,
    @SerializedName("item_id")
    val itemId: Int,
    @SerializedName("is_favourite")
    val isFavourite: Boolean? = null
)

/** The stored flag, plus the resolved item so the UI can render it immediately. */
data class FavouriteState(
    val kind: String,
    @SerializedName("item_id")
    val itemId: Int,
    @SerializedName("is_favourite")
    val isFavourite: Boolean,
    val item: HomeRailItem? = null
)

/** `GET /v1/resume` — continue-watching entries, most recent first. */
data class ProgressList(
    val items: List<HomeRailItem> = emptyList()
)

/** One item's stored position. */
data class ProgressPayload(
    val kind: String,
    @SerializedName("item_id")
    val itemId: Int,
    @SerializedName("position_ms")
    val positionMs: Long = 0L,
    @SerializedName("duration_ms")
    val durationMs: Long = 0L,
    /** Null means "no bar": live content has no duration. */
    val progress: Float? = null
)

/**
 * Report a playback position. A 204 answer means the write *cleared* the row
 * (finished, or rewound to the start), which is a success.
 */
data class ProgressUpdate(
    val kind: String,
    @SerializedName("item_id")
    val itemId: Int,
    @SerializedName("position_ms")
    val positionMs: Long,
    @SerializedName("duration_ms")
    val durationMs: Long = 0L
)
