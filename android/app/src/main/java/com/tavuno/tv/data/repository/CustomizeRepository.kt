package com.tavuno.tv.data.repository

import com.tavuno.tv.data.api.TavunoApiService
import com.tavuno.tv.data.local.SessionManager
import com.tavuno.tv.data.model.CustomizationPayload
import com.tavuno.tv.data.model.CustomizationReset
import com.tavuno.tv.data.model.CustomizationSet
import com.tavuno.tv.network.NetworkModule
import kotlinx.coroutines.flow.first

/**
 * Per-profile catalog customization (Slice D).
 *
 * One kind per call (`live_channel`, `live_category`, `movie_category`,
 * `series_category`) so a failed write can never leave half a rail half-updated: the backend
 * replaces a kind's rows in one transaction, and the client sends the whole list it rendered.
 */
class CustomizeRepository(
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
        // 401/403 both mean "no profile behind this request" from the viewer's side; the
        // endpoint answers 401 through require_profile_id, and 403 covers a token that is
        // valid but not allowed to touch these rows.
        403 -> "Access denied"
        400 -> "This profile cannot be customized"
        else -> "Failed to $action"
    }

    /** The stored overrides for one kind; an untouched profile answers with an empty set. */
    suspend fun getOverrides(kind: String): Result<CustomizationSet> {
        return try {
            ensureAuthToken()
            val response = apiService.getCustomizations(kind)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(error(response.code(), "load your preferences")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Replace this profile's overrides for one kind with [payload]. */
    suspend fun saveOverrides(kind: String, payload: CustomizationPayload): Result<CustomizationSet> {
        return try {
            ensureAuthToken()
            val response = apiService.putCustomizations(kind, payload)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(error(response.code(), "save your preferences")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Drop every override for one kind, restoring the catalog's natural order. */
    suspend fun resetOverrides(kind: String): Result<CustomizationReset> {
        return try {
            ensureAuthToken()
            val response = apiService.deleteCustomizations(kind)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(error(response.code(), "reset your preferences")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}