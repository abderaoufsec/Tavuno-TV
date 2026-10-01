package com.tavuno.tv.data.repository

import com.tavuno.tv.data.api.TavunoApiService
import com.tavuno.tv.data.local.SessionManager
import com.tavuno.tv.data.model.CreateProfileRequest
import com.tavuno.tv.data.model.DeleteProfileResult
import com.tavuno.tv.data.model.UpdateProfileRequest
import com.tavuno.tv.data.model.ViewingProfile
import com.tavuno.tv.network.NetworkModule
import kotlinx.coroutines.flow.first

/**
 * Viewing profiles (Slice D): the account plus the child profiles that split its subscription
 * across viewers.
 *
 * There is no "switch profile" call on purpose — the backend resolves the acting profile from
 * the caller's token, so switching identity is a re-authentication, not an API call. This
 * repository therefore manages viewers (create/rename/flag/remove) and never pretends to
 * select one.
 */
class ProfileRepository(
    private val apiService: TavunoApiService,
    private val sessionManager: SessionManager
) {

    private suspend fun ensureAuthToken() {
        val token = sessionManager.accessToken.first()
        if (token != null) {
            NetworkModule.updateAuthToken(token)
        }
    }

    private fun error(code: Int, action: String): String = when (code) {
        401 -> "Session expired. Please log in again."
        403 -> "Access denied"
        404 -> "Profile not found"
        // The name is the only validated field (1..60 chars), so a 400 here is almost always
        // an empty or over-long name typed on the remote.
        400 -> "That profile name cannot be used"
        else -> "Failed to $action"
    }

    suspend fun getProfiles(): Result<List<ViewingProfile>> {
        return try {
            ensureAuthToken()
            val response = apiService.getProfiles()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(error(response.code(), "load profiles")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createProfile(
        displayName: String,
        isKids: Boolean = false,
        avatar: String? = null
    ): Result<ViewingProfile> {
        return try {
            ensureAuthToken()
            val response = apiService.createProfile(
                CreateProfileRequest(displayName = displayName, avatar = avatar, isKids = isKids)
            )
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(error(response.code(), "add the profile")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Partial update: null fields are left alone by the backend. */
    suspend fun updateProfile(
        profileId: Int,
        displayName: String? = null,
        isKids: Boolean? = null
    ): Result<ViewingProfile> {
        return try {
            ensureAuthToken()
            val response = apiService.updateProfile(
                profileId,
                UpdateProfileRequest(displayName = displayName, isKids = isKids)
            )
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(error(response.code(), "update the profile")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Remove a child profile. The account row is never deletable, so 404 is a real outcome. */
    suspend fun deleteProfile(profileId: Int): Result<DeleteProfileResult> {
        return try {
            ensureAuthToken()
            val response = apiService.deleteProfile(profileId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(error(response.code(), "remove the profile")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}