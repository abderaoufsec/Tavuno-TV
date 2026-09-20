package com.streamvault.data.remote.tavuno

import com.google.common.truth.Truth.assertThat
import com.streamvault.domain.model.ContentType
import com.streamvault.domain.model.Result
import com.streamvault.domain.provider.PlaybackRequest
import com.streamvault.domain.provider.ProviderContentReference
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class TavunoPlaybackResolverTest {

    private val tavunoCatalogRepository = mock<TavunoCatalogRepository>()
    private val resolver = TavunoPlaybackResolver(tavunoCatalogRepository)

    @Test
    fun `resolve uses provided source URL when available`() = runTest {
        val sourceUrl = "http://example.com/stream.m3u8"

        val request = PlaybackRequest(
            sourceUrl = sourceUrl,
            content = ProviderContentReference(providerId = 1L),
            contentType = ContentType.LIVE
        )

        val result = resolver.resolve(request)

        assertThat(result.isSuccess).isTrue()
        val resolved = result.getOrNull()
        assertThat(resolved).isNotNull()
        assertThat(resolved!!.url).isEqualTo(sourceUrl)
        assertThat(resolved.tavunoSessionId).isNull()
    }

    @Test
    fun `resolve returns error for non-live content`() = runTest {
        val request = PlaybackRequest(
            sourceUrl = "",
            content = ProviderContentReference(providerId = 1L, localId = 123L),
            contentType = ContentType.MOVIE
        )

        val result = resolver.resolve(request)

        assertThat(result.isError).isTrue()
        assertThat(result.errorMessageOrNull()).contains("only supports live")
    }

    @Test
    fun `resolve returns error when authorization fails`() = runTest {
        whenever(tavunoCatalogRepository.authorizeLivePlayback(any())).thenReturn(
            Result.error("Auth failed")
        )

        val request = PlaybackRequest(
            sourceUrl = "",
            content = ProviderContentReference(providerId = 1L, streamId = 123L),
            contentType = ContentType.LIVE
        )

        val result = resolver.resolve(request)

        when (result) {
            is Result.Error -> {
                // The resolver appends "Tavuno authorization failed" prefix
                assertThat(result.message).contains("Auth failed")
            }
            is Result.Success -> {
                throw AssertionError("Expected error but got success with data: ${result.data}")
            }
            Result.Loading -> {
                throw AssertionError("Expected error but got loading")
            }
        }
    }

    @Test
    fun `resolve calls authorization and returns session_id`() = runTest {
        val expectedSessionId = 42
        val expectedUrl = "http://localhost:8080/media/tavuno/test/llhls.m3u8?token=test"
        val playbackResponse = PlaybackResponse(
            sessionId = expectedSessionId,
            channelId = 123,
            channelName = "Test Channel",
            expiresAt = "2026-09-20T18:00:00Z",
            playback = PlaybackInfo(protocol = "hls", url = expectedUrl, streamName = "test")
        )

        whenever(tavunoCatalogRepository.authorizeLivePlayback(any())).thenReturn(
            Result.success(playbackResponse)
        )

        val request = PlaybackRequest(
            sourceUrl = "",
            content = ProviderContentReference(providerId = 1L, streamId = 123L),
            contentType = ContentType.LIVE
        )

        val result = resolver.resolve(request)

        when (result) {
            is Result.Success -> {
                val resolved = result.data
                assertThat(resolved.url).isEqualTo(expectedUrl)
                assertThat(resolved.tavunoSessionId).isEqualTo(expectedSessionId)
            }
            is Result.Error -> {
                throw AssertionError("Expected success but got error: ${result.message}")
            }
            Result.Loading -> {
                throw AssertionError("Expected success but got loading")
            }
        }
    }
}
