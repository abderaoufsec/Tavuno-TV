package com.streamvault.data.remote.tavuno

import com.streamvault.data.preferences.TokenStore
import com.streamvault.data.util.DeviceFingerprintGenerator
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp interceptor that adds authentication headers to Tavuno API requests.
 * Adds Authorization: Bearer <access_token> and X-Device-Fingerprint headers.
 * Token refresh is handled by the repository when it receives 401 responses.
 */
@Singleton
class TavunoAuthInterceptor @Inject constructor(
    private val tokenStore: TokenStore,
    private val deviceFingerprintGenerator: DeviceFingerprintGenerator
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Add headers
        val requestBuilder = originalRequest.newBuilder()
            .header("X-Device-Fingerprint", deviceFingerprintGenerator.getFingerprint())

        val accessToken = tokenStore.getAccessToken()
        if (accessToken != null) {
            requestBuilder.header("Authorization", "Bearer $accessToken")
        }

        return chain.proceed(requestBuilder.build())
    }
}
