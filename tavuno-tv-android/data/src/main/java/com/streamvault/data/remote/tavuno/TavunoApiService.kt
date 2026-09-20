package com.streamvault.data.remote.tavuno

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Tavuno Control API abstraction for authentication, device management, and catalog.
 */
interface TavunoApiService {
    @POST("v1/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("v1/auth/refresh")
    suspend fun refresh(@Body request: RefreshRequest): Response<LoginResponse>

    @POST("v1/auth/logout")
    suspend fun logout(@Body request: RefreshRequest): Response<Unit>

    @GET("v1/devices")
    suspend fun listDevices(): Response<List<DeviceDto>>

    @DELETE("v1/devices/{id}")
    suspend fun revokeDevice(@Path("id") deviceId: Int): Response<Unit>

    // Catalog endpoints
    @GET("v1/home")
    suspend fun getHome(): Response<HomeResponse>

    @GET("v1/channels")
    suspend fun getChannels(@Query("category_id") categoryId: Int?): Response<List<ChannelDto>>

    @GET("v1/channels/{id}")
    suspend fun getChannel(@Path("id") channelId: Int): Response<ChannelDetailDto>

    @GET("v1/categories")
    suspend fun getCategories(@Query("kind") kind: String?): Response<List<CategoryDto>>

    @GET("v1/categories/{id}")
    suspend fun getCategory(@Path("id") categoryId: Int): Response<CategoryDto>

    @GET("v1/movies")
    suspend fun getMovies(@Query("category_id") categoryId: Int?): Response<List<MovieDto>>

    @GET("v1/movies/{id}")
    suspend fun getMovie(@Path("id") movieId: Int): Response<MovieDto>

    @GET("v1/series")
    suspend fun getSeries(@Query("category_id") categoryId: Int?): Response<List<SeriesDto>>

    @GET("v1/series/{id}")
    suspend fun getSeries(@Path("id") seriesId: Int): Response<SeriesDto>

    @GET("v1/sports")
    suspend fun getSports(): Response<List<CategoryDto>>

    // Sports endpoints (M11)
    @GET("v1/sports/competitions")
    suspend fun getCompetitions(@Query("sport") sport: String?): Response<List<CompetitionDto>>

    @GET("v1/sports/competitions/{id}")
    suspend fun getCompetition(@Path("id") competitionId: Int): Response<CompetitionDto>

    @GET("v1/sports/competitions/{id}/matches")
    suspend fun getCompetitionMatches(
        @Path("id") competitionId: Int,
        @Query("status") status: String?,
        @Query("limit") limit: Int
    ): Response<List<MatchDto>>

    @GET("v1/sports/matches")
    suspend fun getMatches(
        @Query("status") status: String?,
        @Query("limit") limit: Int
    ): Response<List<MatchDto>>

    @GET("v1/sports/matches/{id}")
    suspend fun getMatch(@Path("id") matchId: Int): Response<MatchDto>

    @GET("v1/sports/matches/{id}/details")
    suspend fun getMatchDetails(@Path("id") matchId: Int): Response<MatchDetailsDto>

    // Content detail endpoints (M12)
    @GET("v1/channels/{id}/details")
    suspend fun getChannelDetails(@Path("id") channelId: Int): Response<ChannelDetailsDto>

    @GET("v1/movies/{id}/details")
    suspend fun getMovieDetails(@Path("id") movieId: Int): Response<MovieDetailsDto>

    @GET("v1/series/{id}/details")
    suspend fun getSeriesDetails(@Path("id") seriesId: Int): Response<SeriesDetailsDto>

    // Playback authorization endpoints (M12.5)
    @POST("v1/playback/live/{channel_id}")
    suspend fun authorizeLivePlayback(
        @Path("channel_id") channelId: Int
    ): Response<PlaybackResponse>

    @POST("v1/playback/heartbeat")
    suspend fun heartbeatPlayback(
        @Body request: SessionRequest
    ): Response<SessionResponse>

    @POST("v1/playback/stop")
    suspend fun stopPlayback(
        @Body request: SessionRequest
    ): Response<SessionResponse>

    // EPG endpoints (M13)
    @GET("v1/epg")
    suspend fun getEpg(@Query("channel_id") channelId: Int?): Response<List<EpgProgramDto>>

    @GET("v1/epg/channel/{channel_id}/now-next")
    suspend fun getEpgNowNext(@Path("channel_id") channelId: Int): Response<EpgNowNextDto>
}
