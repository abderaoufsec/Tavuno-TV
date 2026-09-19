package com.streamvault.domain.repository

import com.streamvault.domain.model.Result

/**
 * Repository for Tavuno authentication operations.
 */
interface AuthRepository {
    suspend fun login(email: String, password: String): Result<Unit>
    suspend fun logout(): Result<Unit>
    fun isLoggedIn(): Boolean
    fun currentProfileId(): Int?
    fun currentProfileEmail(): String?
    fun currentProfileDisplayName(): String?
}
