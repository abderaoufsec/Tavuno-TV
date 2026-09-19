package com.streamvault.data.preferences

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypted token store for Tavuno authentication.
 * Stores access tokens, refresh tokens, and device fingerprint securely.
 */
@Singleton
class TokenStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "tavuno_auth_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _isLoggedIn = MutableStateFlow(getAccessToken() != null)
    val isLoggedIn: Flow<Boolean> = _isLoggedIn

    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_ACCESS_EXPIRES_AT = "access_expires_at"
        private const val KEY_REFRESH_EXPIRES_AT = "refresh_expires_at"
        private const val KEY_DEVICE_FINGERPRINT = "device_fingerprint"
        private const val KEY_PROFILE_ID = "profile_id"
        private const val KEY_PROFILE_EMAIL = "profile_email"
        private const val KEY_PROFILE_DISPLAY_NAME = "profile_display_name"
    }

    fun saveTokens(
        accessToken: String,
        refreshToken: String,
        accessExpiresAt: Double,
        refreshExpiresAt: Double,
        profileId: Int,
        profileEmail: String,
        profileDisplayName: String
    ) {
        sharedPreferences.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putLong(KEY_ACCESS_EXPIRES_AT, accessExpiresAt.toLong())
            .putLong(KEY_REFRESH_EXPIRES_AT, refreshExpiresAt.toLong())
            .putInt(KEY_PROFILE_ID, profileId)
            .putString(KEY_PROFILE_EMAIL, profileEmail)
            .putString(KEY_PROFILE_DISPLAY_NAME, profileDisplayName)
            .apply()
        _isLoggedIn.value = true
    }

    fun getAccessToken(): String? {
        return sharedPreferences.getString(KEY_ACCESS_TOKEN, null)
    }

    fun getRefreshToken(): String? {
        return sharedPreferences.getString(KEY_REFRESH_TOKEN, null)
    }

    fun getAccessExpiresAt(): Long? {
        val value = sharedPreferences.getLong(KEY_ACCESS_EXPIRES_AT, -1L)
        return if (value != -1L) value else null
    }

    fun getRefreshExpiresAt(): Long? {
        val value = sharedPreferences.getLong(KEY_REFRESH_EXPIRES_AT, -1L)
        return if (value != -1L) value else null
    }

    fun getProfileId(): Int? {
        val value = sharedPreferences.getInt(KEY_PROFILE_ID, -1)
        return if (value != -1) value else null
    }

    fun getProfileEmail(): String? {
        return sharedPreferences.getString(KEY_PROFILE_EMAIL, null)
    }

    fun getProfileDisplayName(): String? {
        return sharedPreferences.getString(KEY_PROFILE_DISPLAY_NAME, null)
    }

    fun saveDeviceFingerprint(fingerprint: String) {
        sharedPreferences.edit()
            .putString(KEY_DEVICE_FINGERPRINT, fingerprint)
            .apply()
    }

    fun getDeviceFingerprint(): String? {
        return sharedPreferences.getString(KEY_DEVICE_FINGERPRINT, null)
    }

    fun clear() {
        sharedPreferences.edit()
            .clear()
            .apply()
        _isLoggedIn.value = false
    }
}
