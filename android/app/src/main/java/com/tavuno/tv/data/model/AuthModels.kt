package com.tavuno.tv.data.model

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    val email: String,
    val password: String,
    @SerializedName("device_fingerprint")
    val deviceFingerprint: String,
    val platform: String = "android-tv"
)

data class LoginResponse(
    @SerializedName("access_token")
    val accessToken: String,
    @SerializedName("refresh_token")
    val refreshToken: String,
    @SerializedName("access_expires_at")
    val accessExpiresAt: Double,
    @SerializedName("refresh_expires_at")
    val refreshExpiresAt: Double,
    val profile: Profile
)

data class Profile(
    val id: Int,
    val email: String,
    @SerializedName("display_name")
    val displayName: String
)

data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String
)

data class RegisterResponse(
    @SerializedName("profile_id")
    val profileId: Int,
    val email: String,
    @SerializedName("display_name")
    val displayName: String,
    val status: String
)

data class RefreshRequest(
    @SerializedName("refresh_token")
    val refreshToken: String
)

data class LogoutRequest(
    @SerializedName("refresh_token")
    val refreshToken: String
)

data class UserProfile(
    val id: Int,
    val email: String,
    @SerializedName("display_name")
    val displayName: String,
    val role: String
)

data class Subscription(
    val subscription: SubscriptionDetail?,
    val plan: Plan?,
    val entitlements: List<Entitlement>
)

data class SubscriptionDetail(
    val id: Int,
    val status: String,
    @SerializedName("plan_id")
    val planId: Int,
    @SerializedName("starts_at")
    val startsAt: String?,
    @SerializedName("ends_at")
    val endsAt: String?
)

data class Plan(
    val id: Int,
    val name: String,
    @SerializedName("max_concurrent_streams")
    val maxConcurrentStreams: Int,
    @SerializedName("max_devices")
    val maxDevices: Int
)

data class Entitlement(
    @SerializedName("resource_type")
    val resourceType: String,
    @SerializedName("resource_key")
    val resourceKey: String
)
