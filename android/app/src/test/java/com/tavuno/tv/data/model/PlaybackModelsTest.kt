package com.tavuno.tv.data.model

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for PlaybackModels serialization and dual playback mode support.
 */
class PlaybackModelsTest {

    private val gson = Gson()

    @Test
    fun `parse OME DVR playback response`() {
        val json = """
        {
            "session_id": 101,
            "channel_id": 1,
            "channel_name": "Test Channel",
            "expires_at": "2099-12-31T23:59:59Z",
            "playback": {
                "protocol": "hls",
                "url": "http://localhost:8080/media/tavuno/channel_1/llhls.m3u8?token=101.xxx",
                "stream_name": "channel_1",
                "dvr_enabled": true,
                "max_rewind_seconds": 3600
            }
        }
        """.trimIndent()

        val auth = gson.fromJson(json, PlaybackAuthorization::class.java)

        assertEquals(101, auth.sessionId)
        assertEquals(1, auth.channelId)
        assertEquals("Test Channel", auth.channelName)
        assertEquals("hls", auth.playback.protocol)
        assertTrue(auth.playback.dvrEnabled)
        assertEquals(3600, auth.playback.maxRewindSeconds)
        assertTrue(auth.playback.url.contains("token="))
    }

    @Test
    fun `parse direct HLS playback response`() {
        val json = """
        {
            "session_id": 102,
            "channel_id": 2,
            "channel_name": "Direct Stream",
            "expires_at": "2099-12-31T23:59:59Z",
            "playback": {
                "protocol": "http_hls",
                "url": "https://example.com/stream.m3u8",
                "stream_name": "channel_2",
                "dvr_enabled": false,
                "max_rewind_seconds": 0
            }
        }
        """.trimIndent()

        val auth = gson.fromJson(json, PlaybackAuthorization::class.java)

        assertEquals(102, auth.sessionId)
        assertEquals(2, auth.channelId)
        assertEquals("Direct Stream", auth.channelName)
        assertEquals("http_hls", auth.playback.protocol)
        assertFalse(auth.playback.dvrEnabled)
        assertEquals(0, auth.playback.maxRewindSeconds)
        assertFalse(auth.playback.url.contains("token="))
        assertTrue(auth.playback.url.startsWith("https://"))
    }

    @Test
    fun `PlaybackInfo default values`() {
        val playbackInfo = PlaybackInfo(
            protocol = "hls",
            url = "http://example.com/stream.m3u8",
            streamName = "test"
        )

        // Default values should be false/0 for DVR
        assertFalse(playbackInfo.dvrEnabled)
        assertEquals(0, playbackInfo.maxRewindSeconds)
    }
}
