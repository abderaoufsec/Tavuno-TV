package com.tavuno.tv.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.coEvery
import io.mockk.mockk
import com.tavuno.tv.data.model.PlaybackAuthorization
import com.tavuno.tv.data.model.PlaybackInfo
import com.tavuno.tv.data.repository.PlaybackRepository
import com.tavuno.tv.ui.screens.player.PlayerScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DvrRewindTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun playerScreen_showsRewindUi_whenDvrEnabled() {
        val mockRepository = mockk<PlaybackRepository>()
        
        coEvery {
            mockRepository.authorizeLivePlayback(any())
        } returns Result.success(
            PlaybackAuthorization(
                sessionId = 101,
                channelId = 1,
                channelName = "Test Channel",
                expiresAt = "2099-12-31T23:59:59",
                playback = PlaybackInfo(
                    protocol = "hls",
                    url = "http://test/stream.m3u8",
                    streamName = "channel_1",
                    dvrEnabled = true,
                    maxRewindSeconds = 3600
                )
            )
        )

        composeTestRule.setContent {
            PlayerScreen(
                contentType = "live",
                contentId = "1",
                playbackRepository = mockRepository,
                onNavigateBack = {}
            )
        }

        // Wait for async operations
        Thread.sleep(500)

        // Verify rewind buttons are displayed
        composeTestRule.onNodeWithText("-30s").assertIsDisplayed()
        composeTestRule.onNodeWithText("-10s").assertIsDisplayed()
    }

    @Test
    fun playerScreen_doesNotShowRewindUi_whenDvrDisabled() {
        val mockRepository = mockk<PlaybackRepository>()
        
        coEvery {
            mockRepository.authorizeLivePlayback(any())
        } returns Result.success(
            PlaybackAuthorization(
                sessionId = 101,
                channelId = 1,
                channelName = "Test Channel",
                expiresAt = "2099-12-31T23:59:59",
                playback = PlaybackInfo(
                    protocol = "hls",
                    url = "http://test/stream.m3u8",
                    streamName = "channel_1",
                    dvrEnabled = false,
                    maxRewindSeconds = 0
                )
            )
        )

        composeTestRule.setContent {
            PlayerScreen(
                contentType = "live",
                contentId = "1",
                playbackRepository = mockRepository,
                onNavigateBack = {}
            )
        }

        // Wait for async operations
        Thread.sleep(500)

        // Verify rewind buttons are NOT displayed
        composeTestRule.onNodeWithText("-30s").assertDoesNotExist()
        composeTestRule.onNodeWithText("-10s").assertDoesNotExist()
    }

    @Test
    fun playerScreen_doesNotShowRewindUi_forVodContent() {
        val mockRepository = mockk<PlaybackRepository>()
        
        coEvery {
            mockRepository.authorizeMoviePlayback(any())
        } returns Result.success(
            PlaybackAuthorization(
                sessionId = 101,
                channelId = 0,
                channelName = "",
                expiresAt = "2099-12-31T23:59:59",
                playback = PlaybackInfo(
                    protocol = "hls",
                    url = "http://test/movie.m3u8",
                    streamName = "movie_1",
                    dvrEnabled = true,
                    maxRewindSeconds = 3600
                )
            )
        )

        composeTestRule.setContent {
            PlayerScreen(
                contentType = "movie",
                contentId = "1",
                playbackRepository = mockRepository,
                onNavigateBack = {}
            )
        }

        // Wait for async operations
        Thread.sleep(500)

        // Verify rewind buttons are NOT displayed for VOD
        composeTestRule.onNodeWithText("-30s").assertDoesNotExist()
        composeTestRule.onNodeWithText("-10s").assertDoesNotExist()
    }
}
