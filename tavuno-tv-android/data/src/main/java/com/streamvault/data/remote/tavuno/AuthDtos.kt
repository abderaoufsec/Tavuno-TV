package com.streamvault.data.remote.tavuno

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
    @SerialName("device_fingerprint")
    val deviceFingerprint: String,
    val platform: String
)

@Serializable
data class LoginResponse(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("refresh_token")
    val refreshToken: String,
    @SerialName("access_expires_at")
    val accessExpiresAt: Double,
    @SerialName("refresh_expires_at")
    val refreshExpiresAt: Double,
    val profile: Profile
)

@Serializable
data class Profile(
    val id: Int,
    val email: String,
    @SerialName("display_name")
    val displayName: String
)

@Serializable
data class RefreshRequest(
    @SerialName("refresh_token")
    val refreshToken: String
)

@Serializable
data class DeviceDto(
    val id: Int,
    @SerialName("display_name")
    val displayName: String,
    val platform: String,
    @SerialName("last_seen_at")
    val lastSeenAt: String? = null,
    @SerialName("is_current")
    val isCurrent: Boolean = false,
    @SerialName("is_revoked")
    val isRevoked: Boolean = false
)
