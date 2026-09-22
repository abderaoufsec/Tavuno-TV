package com.tavuno.tv.data.api

import com.tavuno.tv.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface TavunoApiService {
    
    // Authentication
    @POST("v1/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<RegisterResponse>
    
    @POST("v1/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>
    
    @POST("v1/auth/refresh")
    suspend fun refresh(@Body request: RefreshRequest): Response<LoginResponse>
    
    @POST("v1/auth/logout")
    suspend fun logout(@Body request: LogoutRequest): Response<Unit>
    
    @GET("v1/auth/me")
    suspend fun getMe(@Header("Authorization") authorization: String): Response<UserProfile>
    
    @GET("v1/auth/subscription")
    suspend fun getSubscription(@Header("Authorization") authorization: String): Response<Subscription>
    
    // Catalog
    @GET("v1/home")
    suspend fun getHome(): Response<HomeData>
    
    @GET("v1/channels")
    suspend fun getChannels(@Query("category_id") categoryId: Int?): Response<List<Channel>>
    
    @GET("v1/channels/{channel_id}/details")
    suspend fun getChannelDetails(@Path("channel_id") channelId: Int): Response<ChannelDetails>
    
    @GET("v1/categories")
    suspend fun getCategories(@Query("kind") kind: String?): Response<List<Category>>
    
    @GET("v1/categories/{category_id}")
    suspend fun getCategory(@Path("category_id") categoryId: Int): Response<Category>
    
    @GET("v1/movies")
    suspend fun getMovies(@Query("category_id") categoryId: Int?): Response<List<Movie>>
    
    @GET("v1/movies/{movie_id}/details")
    suspend fun getMovieDetails(@Path("movie_id") movieId: Int): Response<MovieDetails>
    
    @GET("v1/series")
    suspend fun getSeries(@Query("category_id") categoryId: Int?): Response<List<Series>>
    
    @GET("v1/series/{series_id}/details")
    suspend fun getSeriesDetails(@Path("series_id") seriesId: Int): Response<SeriesDetails>
    
    @GET("v1/series/{series_id}/seasons")
    suspend fun getSeriesSeasons(@Path("series_id") seriesId: Int): Response<List<SeasonDetails>>
    
    @GET("v1/seasons/{season_id}/episodes")
    suspend fun getSeasonEpisodes(@Path("season_id") seasonId: Int): Response<List<EpisodeDetails>>
    
    // Sports
    @GET("v1/sports/competitions")
    suspend fun getCompetitions(@Query("sport") sport: String?): Response<List<Competition>>
    
    @GET("v1/sports/competitions/{competition_id}")
    suspend fun getCompetition(@Path("competition_id") competitionId: Int): Response<Competition>
    
    @GET("v1/sports/competitions/{competition_id}/matches")
    suspend fun getCompetitionMatches(
        @Path("competition_id") competitionId: Int,
        @Query("status") status: String?,
        @Query("limit") limit: Int?
    ): Response<List<Match>>
    
    @GET("v1/sports/matches")
    suspend fun getMatches(@Query("status") status: String?, @Query("limit") limit: Int?): Response<List<Match>>
    
    @GET("v1/sports/matches/{match_id}/details")
    suspend fun getMatchDetails(@Path("match_id") matchId: Int): Response<MatchDetails>
    
    // EPG
    @GET("v1/epg")
    suspend fun getEpg(@Query("channel_id") channelId: Int?): Response<List<EpgProgram>>
    
    @GET("v1/epg/channel/{channel_id}/now-next")
    suspend fun getChannelNowNext(@Path("channel_id") channelId: Int): Response<ChannelNowNext>
    
    // Playback
    @POST("v1/playback/live/{channel_id}")
    suspend fun authorizeLivePlayback(
        @Path("channel_id") channelId: Int,
        @Header("Authorization") authorization: String
    ): Response<PlaybackAuthorization>
    
    @POST("v1/playback/movie/{movie_id}")
    suspend fun authorizeMoviePlayback(
        @Path("movie_id") movieId: Int,
        @Header("Authorization") authorization: String
    ): Response<MoviePlaybackAuthorization>
    
    @POST("v1/playback/episode/{episode_id}")
    suspend fun authorizeEpisodePlayback(
        @Path("episode_id") episodeId: Int,
        @Header("Authorization") authorization: String
    ): Response<EpisodePlaybackAuthorization>
    
    @POST("v1/playback/heartbeat")
    suspend fun playbackHeartbeat(@Body request: SessionRequest): Response<SessionResponse>
    
    @POST("v1/playback/stop")
    suspend fun stopPlayback(@Body request: SessionRequest): Response<SessionResponse>
    
    @GET("v1/media/verify")
    suspend fun verifyMediaToken(@Query("token") token: String): Response<MediaTokenVerification>
    
    // Health
    @GET("health")
    suspend fun health(): Response<Map<String, Any>>
}
