package com.streamvault.data.util

import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import android.content.Context

/**
 * Generates a stable device fingerprint for Tavuno authentication.
 * Combines Android ID with Build.FINGERPRINT hashed via SHA-256.
 */
@Singleton
class DeviceFingerprintGenerator @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var cachedFingerprint: String? = null

    fun getFingerprint(): String {
        return cachedFingerprint ?: generateFingerprint().also {
            cachedFingerprint = it
        }
    }

    private fun generateFingerprint(): String {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )

        val buildFingerprint = Build.FINGERPRINT ?: "unknown"

        // Combine and hash
        val combined = "$androidId|$buildFingerprint"
        val hash = sha256(combined)

        return hash
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
