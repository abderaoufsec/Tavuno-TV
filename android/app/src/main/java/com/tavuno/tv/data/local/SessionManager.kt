package com.tavuno.tv.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class SessionManager(private val context: Context) {
    
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "session")
    
    companion object {
        private val ACCESS_TOKEN_KEY = stringPreferencesKey("access_token")
        private val REFRESH_TOKEN_KEY = stringPreferencesKey("refresh_token")
        private val ACCESS_EXPIRES_AT_KEY = stringPreferencesKey("access_expires_at")
        private val REFRESH_EXPIRES_AT_KEY = stringPreferencesKey("refresh_expires_at")
        private val PROFILE_ID_KEY = stringPreferencesKey("profile_id")
        private val EMAIL_KEY = stringPreferencesKey("email")
        private val DISPLAY_NAME_KEY = stringPreferencesKey("display_name")
        private val DEVICE_FINGERPRINT_KEY = stringPreferencesKey("device_fingerprint")
    }
    
    val accessToken: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[ACCESS_TOKEN_KEY]
    }
    
    val refreshToken: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[REFRESH_TOKEN_KEY]
    }
    
    val isAuthenticated: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[ACCESS_TOKEN_KEY] != null
    }
    
    suspend fun saveSession(
        accessToken: String,
        refreshToken: String,
        accessExpiresAt: Double,
        refreshExpiresAt: Double,
        profileId: Int,
        email: String,
        displayName: String
    ) {
        context.dataStore.edit { preferences ->
            preferences[ACCESS_TOKEN_KEY] = accessToken
            preferences[REFRESH_TOKEN_KEY] = refreshToken
            preferences[ACCESS_EXPIRES_AT_KEY] = accessExpiresAt.toString()
            preferences[REFRESH_EXPIRES_AT_KEY] = refreshExpiresAt.toString()
            preferences[PROFILE_ID_KEY] = profileId.toString()
            preferences[EMAIL_KEY] = email
            preferences[DISPLAY_NAME_KEY] = displayName
        }
        
        // Update network interceptor
        com.tavuno.tv.network.NetworkModule.updateAuthToken(accessToken)
    }
    
    suspend fun clearSession() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
        
        // Clear network interceptor
        com.tavuno.tv.network.NetworkModule.updateAuthToken(null)
    }
    
    suspend fun getDeviceFingerprint(): String {
        val preferences = context.dataStore.data.first()
        val fingerprint = preferences[DEVICE_FINGERPRINT_KEY]
        
        if (fingerprint == null) {
            val newFingerprint = generateDeviceFingerprint()
            context.dataStore.edit { prefs ->
                prefs[DEVICE_FINGERPRINT_KEY] = newFingerprint
            }
            return newFingerprint
        }
        
        return fingerprint
    }
    
    private fun generateDeviceFingerprint(): String {
        return android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: System.currentTimeMillis().toString()
    }
    
    suspend fun getProfileInfo(): Triple<Int, String, String>? {
        val preferences = context.dataStore.data.first()
        val profileId = preferences[PROFILE_ID_KEY]?.toIntOrNull()
        val email = preferences[EMAIL_KEY]
        val displayName = preferences[DISPLAY_NAME_KEY]
        
        return if (profileId != null && email != null && displayName != null) {
            Triple(profileId, email, displayName)
        } else {
            null
        }
    }
}
