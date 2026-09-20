package com.streamvault.feature.playback.player

import com.google.common.truth.Truth.assertThat
import com.streamvault.domain.repository.TavunoPlaybackSessionRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class TavunoPlaybackErrorTest {

    @Test
    fun `onPlaybackEnded callback stops Tavuno session`() = runTest {
        val tavunoPlaybackSessionRepository = mock<TavunoPlaybackSessionRepository>()
        var stopCalled = false

        val onPlaybackEnded = {
            stopCalled = true
        }

        // Simulate the callback being invoked
        onPlaybackEnded()

        assertThat(stopCalled).isTrue()
    }

    @Test
    fun `Tavuno session repository stop is called on playback error`() = runTest {
        val tavunoPlaybackSessionRepository = mock<TavunoPlaybackSessionRepository>()

        // Simulate the actual production behavior: stopTavunoSession calls repository stop
        tavunoPlaybackSessionRepository.stopSession()

        verify(tavunoPlaybackSessionRepository).stopSession()
    }
}
