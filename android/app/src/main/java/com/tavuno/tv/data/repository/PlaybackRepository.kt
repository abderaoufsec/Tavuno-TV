package com.tavuno.tv.data.repository

import com.tavuno.tv.data.api.TavunoApiService
import com.tavuno.tv.data.local.SessionManager
import com.tavuno.tv.data.model.*
import com.tavuno.tv.network.NetworkModule
import kotlinx.coroutines.flow.first

class PlaybackRepository(
    private val apiService: TavunoApiService,
    private val sessionManager: SessionManager
) {
    
    suspend fun authorizeLivePlayback(channelId: Int): Result<PlaybackAuthorization> {
        return try {
            val authorization = authorizationHeader()
            val response = apiService.authorizeLivePlayback(channelId, authorization)
            
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorMessage = when (response.code()) {
                    401 -> "Authentication required"
                    402 -> "Active subscription required for playback"
                    403 -> "Access denied - check subscription or device limit"
                    404 -> "Channel not found"
                    429 -> "Concurrent stream limit reached"
                    else -> "Playback authorization failed"
                }
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun authorizeMoviePlayback(movieId: Int): Result<PlaybackAuthorization> {
        return try {
            val authorization = authorizationHeader()
            val response = apiService.authorizeMoviePlayback(movieId, authorization)
            
            if (response.isSuccessful && response.body() != null) {
                val movieAuth = response.body()!!
                // Convert movie-specific response to generic PlaybackAuthorization
                val playbackAuth = PlaybackAuthorization(
                    sessionId = movieAuth.sessionId ?: 0,
                    channelId = movieId, // Use movie_id as channel_id for consistency
                    channelName = movieAuth.title,
                    expiresAt = movieAuth.expiresAt ?: "",
                    playback = movieAuth.playback ?: PlaybackInfo(
                        protocol = "hls",
                        url = "",
                        streamName = "movie_$movieId"
                    )
                )
                Result.success(playbackAuth)
            } else {
                val errorMessage = when (response.code()) {
                    401 -> "Authentication required"
                    402 -> "Active subscription required for playback"
                    403 -> "Access denied - check subscription or device limit"
                    404 -> "Movie not found"
                    429 -> "Concurrent stream limit reached"
                    503 -> "Movie stream URL not available"
                    else -> "Movie playback authorization failed"
                }
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun authorizeEpisodePlayback(episodeId: Int): Result<PlaybackAuthorization> {
        return try {
            val authorization = authorizationHeader()
            val response = apiService.authorizeEpisodePlayback(episodeId, authorization)
            
            if (response.isSuccessful && response.body() != null) {
                val episodeAuth = response.body()!!
                // Convert episode-specific response to generic PlaybackAuthorization
                val playbackAuth = PlaybackAuthorization(
                    sessionId = episodeAuth.sessionId ?: 0,
                    channelId = episodeId, // Use episode_id as channel_id for consistency
                    channelName = episodeAuth.title,
                    expiresAt = episodeAuth.expiresAt ?: "",
                    playback = episodeAuth.playback ?: PlaybackInfo(
                        protocol = "hls",
                        url = "",
                        streamName = "episode_$episodeId"
                    )
                )
                Result.success(playbackAuth)
            } else {
                val errorMessage = when (response.code()) {
                    401 -> "Authentication required"
                    402 -> "Active subscription required for playback"
                    403 -> "Access denied - check subscription or device limit"
                    404 -> "Episode not found"
                    429 -> "Concurrent stream limit reached"
                    503 -> "Episode stream URL not available"
                    else -> "Episode playback authorization failed"
                }
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun sendHeartbeat(sessionId: Int): Result<SessionResponse> {
        return try {
            val request = SessionRequest(sessionId)
            val response = apiService.playbackHeartbeat(request)
            
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Heartbeat failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun stopPlayback(sessionId: Int): Result<SessionResponse> {
        return try {
            val request = SessionRequest(sessionId)
            val response = apiService.stopPlayback(request)
            
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Stop playback failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Build the Authorization header for playback calls.
     *
     * Free launch (AUTH_OPEN_ACCESS) runs without a login: there is no access
     * token to send. Returning null lets Retrofit omit the header so the
     * backend resolves its seeded guest identity instead of the app failing
     * with "No access token available".
     */
    private suspend fun authorizationHeader(): String? {
        val token = sessionManager.accessToken.first()
        // Keep the shared OkHttp interceptor in sync for signed-in users; a
        // guest keeps the interceptor clear so nothing invents a token.
        if (!token.isNullOrBlank()) {
            NetworkModule.updateAuthToken(token)
        }
        return bearerAuthorization(token)
    }
}
