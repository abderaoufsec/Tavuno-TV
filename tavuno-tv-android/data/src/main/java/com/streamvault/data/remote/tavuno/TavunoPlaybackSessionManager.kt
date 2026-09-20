package com.streamvault.data.remote.tavuno

import com.streamvault.domain.model.Result
import com.streamvault.domain.repository.TavunoPlaybackSessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data layer implementation of Tavuno playback session lifecycle management.
 * Manages heartbeat and stop for Tavuno playback sessions.
 */
@Singleton
class TavunoPlaybackSessionManager @Inject constructor(
    private val tavunoCatalogRepository: TavunoCatalogRepository
) : TavunoPlaybackSessionRepository {
    companion object {
        // Heartbeat interval: 60 seconds (half of 120s TTL for safety margin)
        const val HEARTBEAT_INTERVAL_MS = 60_000L
    }

    @Volatile
    private var activeSessionId: Int? = null

    @Volatile
    private var heartbeatJob: Job? = null

    @Volatile
    private var sessionScope: CoroutineScope? = null

    override fun startSession(sessionId: Int, scope: CoroutineScope) {
        // Stop existing session if any
        stopSession()

        activeSessionId = sessionId
        sessionScope = scope

        // Start heartbeat job
        heartbeatJob = scope.launch {
            while (isActive && activeSessionId == sessionId) {
                delay(HEARTBEAT_INTERVAL_MS)
                if (activeSessionId == sessionId) {
                    sendHeartbeat(sessionId)
                }
            }
        }
    }

    override fun stopSession() {
        val sessionId = activeSessionId
        heartbeatJob?.cancel()
        heartbeatJob = null
        activeSessionId = null
        sessionScope = null

        if (sessionId != null) {
            // Send stop request without blocking
            sessionScope?.launch {
                sendStop(sessionId)
            }
        }
    }

    override fun isCurrentSession(sessionId: Int): Boolean {
        return activeSessionId == sessionId
    }

    private suspend fun sendHeartbeat(sessionId: Int) {
        try {
            val result = tavunoCatalogRepository.heartbeatPlayback(sessionId)
            if (result is Result.Error) {
                val errorMsg = result.message ?: ""
                // Definitive session failure - stop the session
                // Backend returns 404 for not found/expired, 410 for stopped
                if (errorMsg.contains("404") || errorMsg.contains("410") ||
                    errorMsg.contains("not found", ignoreCase = true) ||
                    errorMsg.contains("expired", ignoreCase = true) ||
                    errorMsg.contains("stopped", ignoreCase = true)) {
                    stopSession()
                }
                // Transient network failure - log but continue
            }
        } catch (e: Exception) {
            // Transient network failure - log but continue
        }
    }

    private suspend fun sendStop(sessionId: Int) {
        try {
            tavunoCatalogRepository.stopPlayback(sessionId)
        } catch (e: Exception) {
            // Stop is best-effort - failure is acceptable
        }
    }
}
