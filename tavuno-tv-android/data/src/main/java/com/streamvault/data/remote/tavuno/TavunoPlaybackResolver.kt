package com.streamvault.data.remote.tavuno

import com.streamvault.domain.model.ContentType
import com.streamvault.domain.model.Result
import com.streamvault.domain.provider.PlaybackRequest
import com.streamvault.domain.provider.PlaybackResolver
import com.streamvault.domain.provider.ProviderContentReference
import com.streamvault.domain.provider.ResolvedPlayback
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tavuno-specific playback resolver that authorizes live channels.
 * Calls POST /v1/playback/live/{channel_id} and returns authorized URL with session_id.
 */
@Singleton
class TavunoPlaybackResolver @Inject constructor(
    private val tavunoCatalogRepository: TavunoCatalogRepository
) : PlaybackResolver {

    override suspend fun buildStreamUrl(streamId: Long, containerExtension: String?): String {
        // For Tavuno, we need to authorize first - this method is not used directly
        // The resolve() method handles the full authorization flow
        return ""
    }

    override suspend fun resolve(request: PlaybackRequest): Result<ResolvedPlayback> {
        // If a source URL is already provided, use it (fallback case)
        if (request.sourceUrl.isNotBlank()) {
            return Result.success(
                ResolvedPlayback(
                    url = request.sourceUrl,
                    containerExtension = request.containerExtension
                )
            )
        }

        // Tavuno requires authorization for live channels
        if (request.contentType != ContentType.LIVE) {
            return Result.error("Tavuno currently only supports live playback")
        }

        // Extract channel ID from the content reference
        val channelId = request.content.numericRemoteId()
            ?: return Result.error("Tavuno requires a numeric channel ID for playback")

        // Authorize playback with Tavuno backend
        val authResult = tavunoCatalogRepository.authorizeLivePlayback(channelId.toInt())
        if (authResult is Result.Error) {
            return Result.error(authResult.message ?: "Tavuno authorization failed")
        }

        val playbackResponse = (authResult as Result.Success).data
        return Result.success(
            ResolvedPlayback(
                url = playbackResponse.playback.url,
                expirationTime = parseExpirationTime(playbackResponse.expiresAt),
                tavunoSessionId = playbackResponse.sessionId
            )
        )
    }

    private fun parseExpirationTime(expiresAt: String): Long? {
        return try {
            // Parse ISO 8601 timestamp
            java.time.Instant.parse(expiresAt).toEpochMilli()
        } catch (e: Exception) {
            null
        }
    }
}
