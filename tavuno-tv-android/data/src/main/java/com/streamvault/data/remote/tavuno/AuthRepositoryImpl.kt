package com.streamvault.data.remote.tavuno

import com.streamvault.data.preferences.TokenStore
import com.streamvault.data.util.DeviceFingerprintGenerator
import com.streamvault.domain.model.Result
import com.streamvault.domain.repository.AuthRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of AuthRepository using Tavuno Control API.
 */
@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val tavunoApiService: TavunoApiService,
    private val tokenStore: TokenStore,
    private val deviceFingerprintGenerator: DeviceFingerprintGenerator
) : AuthRepository {

    override suspend fun login(email: String, password: String): Result<Unit> {
        return try {
            val deviceFingerprint = deviceFingerprintGenerator.getFingerprint()
            tokenStore.saveDeviceFingerprint(deviceFingerprint)

            val response = tavunoApiService.login(
                LoginRequest(
                    email = email,
                    password = password,
                    deviceFingerprint = deviceFingerprint,
                    platform = "android"
                )
            )

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                tokenStore.saveTokens(
                    accessToken = body.accessToken,
                    refreshToken = body.refreshToken,
                    accessExpiresAt = body.accessExpiresAt,
                    refreshExpiresAt = body.refreshExpiresAt,
                    profileId = body.profile.id,
                    profileEmail = body.profile.email,
                    profileDisplayName = body.profile.displayName
                )
                Result.success(Unit)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Result.error(errorBody)
            }
        } catch (e: Exception) {
            Result.error(e.message ?: "Login failed")
        }
    }

    override suspend fun logout(): Result<Unit> {
        return try {
            val refreshToken = tokenStore.getRefreshToken()
            if (refreshToken != null) {
                tavunoApiService.logout(RefreshRequest(refreshToken))
            }
            tokenStore.clear()
            Result.success(Unit)
        } catch (e: Exception) {
            // Clear tokens even if API call fails
            tokenStore.clear()
            Result.success(Unit)
        }
    }

    override fun isLoggedIn(): Boolean {
        return tokenStore.getAccessToken() != null
    }

    override fun currentProfileId(): Int? {
        return tokenStore.getProfileId()
    }

    override fun currentProfileEmail(): String? {
        return tokenStore.getProfileEmail()
    }

    override fun currentProfileDisplayName(): String? {
        return tokenStore.getProfileDisplayName()
    }
}
