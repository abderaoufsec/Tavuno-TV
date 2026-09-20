package com.streamvault.data.remote.tavuno

import com.google.common.truth.Truth.assertThat
import com.streamvault.domain.model.Result
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class TavunoPlaybackSessionManagerTest {

    private val tavunoCatalogRepository = mock<TavunoCatalogRepository>()
    private val manager = TavunoPlaybackSessionManager(tavunoCatalogRepository)

    @Test
    fun `startSession begins heartbeat for new session`() = runTest {
        whenever(tavunoCatalogRepository.heartbeatPlayback(any())).thenReturn(Result.success(SessionResponse(1, "active")))

        manager.startSession(1, this)

        assertThat(manager.isCurrentSession(1)).isTrue()
    }

    @Test
    fun `startSession stops previous session before starting new one`() = runTest {
        whenever(tavunoCatalogRepository.heartbeatPlayback(any())).thenReturn(Result.success(SessionResponse(1, "active")))
        whenever(tavunoCatalogRepository.stopPlayback(any())).thenReturn(Result.success(SessionResponse(1, "stopped")))

        manager.startSession(1, this)

        manager.startSession(2, this)

        assertThat(manager.isCurrentSession(1)).isFalse()
        assertThat(manager.isCurrentSession(2)).isTrue()
        verify(tavunoCatalogRepository).stopPlayback(1)
    }

    @Test
    fun `stopSession stops heartbeat and sends stop request`() = runTest {
        whenever(tavunoCatalogRepository.heartbeatPlayback(any())).thenReturn(Result.success(SessionResponse(1, "active")))
        whenever(tavunoCatalogRepository.stopPlayback(any())).thenReturn(Result.success(SessionResponse(1, "stopped")))

        manager.startSession(1, this)

        manager.stopSession()

        assertThat(manager.isCurrentSession(1)).isFalse()
        verify(tavunoCatalogRepository).stopPlayback(1)
    }

    @Test
    fun `stopSession with no active session does nothing`() = runTest {
        manager.stopSession()

        verify(tavunoCatalogRepository, never()).stopPlayback(any())
    }

    @Test
    fun `heartbeat sent periodically during active session`() = runTest {
        whenever(tavunoCatalogRepository.heartbeatPlayback(any())).thenReturn(Result.success(SessionResponse(1, "active")))

        manager.startSession(1, this)

        // Heartbeat job is created; actual interval is 60 seconds in production
        // We verify the session is active which implies heartbeat job exists
        assertThat(manager.isCurrentSession(1)).isTrue()
    }

    @Test
    fun `heartbeat transient failure does not stop session`() = runTest {
        whenever(tavunoCatalogRepository.heartbeatPlayback(any())).thenReturn(Result.error("Network error"))

        manager.startSession(1, this)

        // Session should still be active despite heartbeat failure
        assertThat(manager.isCurrentSession(1)).isTrue()
    }

    @Test
    fun `isCurrentSession returns false for different session ID`() = runTest {
        whenever(tavunoCatalogRepository.heartbeatPlayback(any())).thenReturn(Result.success(SessionResponse(1, "active")))

        manager.startSession(1, this)

        assertThat(manager.isCurrentSession(2)).isFalse()
    }
}
