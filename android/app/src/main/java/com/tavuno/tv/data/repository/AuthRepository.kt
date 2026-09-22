package com.tavuno.tv.data.repository

import com.tavuno.tv.data.api.TavunoApiService
import com.tavuno.tv.data.local.SessionManager
import com.tavuno.tv.data.model.*
import kotlinx.coroutines.flow.first

class AuthRepository(
    private val apiService: TavunoApiService,
    private val sessionManager: SessionManager
) {
    
    suspend fun register(username: String, email: String, password: String): Result<RegisterResponse> {
        return try {
            val request = RegisterRequest(username, email, password)
            val response = apiService.register(request)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Registration failed: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun login(email: String, password: String): Result<LoginResponse> {
        return try {
            val deviceFingerprint = sessionManager.getDeviceFingerprint()
            val request = LoginRequest(email, password, deviceFingerprint, "android-tv")
            val response = apiService.login(request)
            
            if (response.isSuccessful && response.body() != null) {
                val loginResponse = response.body()!!
                
                // Save session
                sessionManager.saveSession(
                    accessToken = loginResponse.accessToken,
                    refreshToken = loginResponse.refreshToken,
                    accessExpiresAt = loginResponse.accessExpiresAt,
                    refreshExpiresAt = loginResponse.refreshExpiresAt,
                    profileId = loginResponse.profile.id,
                    email = loginResponse.profile.email,
                    displayName = loginResponse.profile.displayName
                )
                
                Result.success(loginResponse)
            } else {
                val errorMessage = when (response.code()) {
                    401 -> "Invalid email or password"
                    403 -> "Account inactive or device limit reached"
                    else -> "Login failed: ${response.message()}"
                }
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun refreshToken(): Result<LoginResponse> {
        return try {
            val refreshToken = sessionManager.refreshToken.first()
            
            if (refreshToken == null) {
                return Result.failure(Exception("No refresh token available"))
            }
            
            val request = RefreshRequest(refreshToken)
            val response = apiService.refresh(request)
            
            if (response.isSuccessful && response.body() != null) {
                val loginResponse = response.body()!!
                
                // Update session
                sessionManager.saveSession(
                    accessToken = loginResponse.accessToken,
                    refreshToken = loginResponse.refreshToken,
                    accessExpiresAt = loginResponse.accessExpiresAt,
                    refreshExpiresAt = loginResponse.refreshExpiresAt,
                    profileId = loginResponse.profile.id,
                    email = loginResponse.profile.email,
                    displayName = loginResponse.profile.displayName
                )
                
                Result.success(loginResponse)
            } else {
                // Refresh token expired, clear session
                sessionManager.clearSession()
                Result.failure(Exception("Session expired. Please login again."))
            }
        } catch (e: Exception) {
            sessionManager.clearSession()
            Result.failure(e)
        }
    }
    
    suspend fun logout(): Result<Unit> {
        return try {
            val refreshToken = sessionManager.refreshToken.first()
            
            if (refreshToken != null) {
                val request = LogoutRequest(refreshToken)
                apiService.logout(request)
            }
            
            sessionManager.clearSession()
            Result.success(Unit)
        } catch (e: Exception) {
            sessionManager.clearSession()
            Result.success(Unit) // Always clear session locally even if API call fails
        }
    }
    
    suspend fun getSubscription(): Result<Subscription> {
        return try {
            val response = apiService.getSubscription("Bearer ${getAccessToken()}")
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to get subscription"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private suspend fun getAccessToken(): String {
        return sessionManager.accessToken.first() ?: throw Exception("No access token available")
    }
}
