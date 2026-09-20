package com.streamvault.domain.repository

import kotlinx.coroutines.CoroutineScope

/**
 * Domain interface for Tavuno playback session lifecycle management.
 * Abstracts the heartbeat and stop functionality for playback sessions.
 */
interface TavunoPlaybackSessionRepository {
    /**
     * Start managing a Tavuno playback session with the given session ID.
     * Stops any existing session before starting the new one.
     */
    fun startSession(sessionId: Int, scope: CoroutineScope)

    /**
     * Stop the current playback session and cancel heartbeat.
     * Sends stop request to backend if there's an active session.
     */
    fun stopSession()

    /**
     * Check if a given session ID is the currently active session.
     */
    fun isCurrentSession(sessionId: Int): Boolean
}
