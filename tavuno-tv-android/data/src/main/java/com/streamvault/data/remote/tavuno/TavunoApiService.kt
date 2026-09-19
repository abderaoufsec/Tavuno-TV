package com.streamvault.data.remote.tavuno

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Tavuno Control API abstraction for authentication and device management.
 */
interface TavunoApiService {
    @POST("v1/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("v1/auth/refresh")
    suspend fun refresh(@Body request: RefreshRequest): Response<LoginResponse>

    @POST("v1/auth/logout")
    suspend fun logout(@Body request: RefreshRequest): Response<Unit>

    @GET("v1/devices")
    suspend fun listDevices(
        @Header("X-Device-Fingerprint") deviceFingerprint: String,
        @Header("Authorization") authorization: String
    ): Response<List<DeviceDto>>

    @DELETE("v1/devices/{id}")
    suspend fun revokeDevice(
        @Path("id") deviceId: Int,
        @Header("X-Device-Fingerprint") deviceFingerprint: String,
        @Header("Authorization") authorization: String
    ): Response<Unit>
}
