package com.tavuno.tv.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackUrlsTest {
    @Test
    fun `rewrites localhost media URLs for emulator`() {
        val url = "http://localhost:8080/media/tavuno/test/llhls.m3u8?token=abc"
        val rewritten = PlaybackUrls.rewriteLoopbackForEmulator(url)
        assertEquals("http://10.0.2.2:8080/media/tavuno/test/llhls.m3u8?token=abc", rewritten)
    }

    @Test
    fun `leaves remote HLS URLs unchanged`() {
        val url = "https://cdn.example.com/live/playlist.m3u8"
        assertEquals(url, PlaybackUrls.rewriteLoopbackForEmulator(url))
    }
}
