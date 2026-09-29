package com.tavuno.tv.playback

/**
 * Android emulators cannot reach the host via localhost; 10.0.2.2 maps to the host loopback.
 */
object PlaybackUrls {
    fun rewriteLoopbackForEmulator(url: String): String {
        return url
            .replace("://localhost", "://10.0.2.2")
            .replace("://127.0.0.1", "://10.0.2.2")
    }
}
