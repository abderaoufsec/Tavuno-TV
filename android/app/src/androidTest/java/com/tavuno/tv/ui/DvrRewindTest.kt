package com.tavuno.tv.ui

import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tavuno.tv.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL

/**
 * Real DVR rewind behaviour against the live LL-HLS test stream - all observed, none mocked:
 *
 *  1. **Environment precondition, measured from the device.** The test fetches the OME master
 *     playlist and its chunklist over `http://10.0.2.2:3333` - the exact host:port
 *     `PlaybackUrls.rewriteLoopbackForEmulator` hands ExoPlayer - and sums the `#EXTINF`
 *     durations. A window smaller than 50s cannot hold a −30s rewind, so the test fails fast
 *     with instructions (`tavuno-infra\start_dvr_test_stream.ps1`, let it run ~60s).
 *  2. **A real D-pad journey** tunes channel 8814 ("M10 Test Channel"): the first press
 *     establishes the rail's focus owner, CENTER opens the Live tab, focus walks the rows (an
 *     off-screen row never gets focus, so the walk cannot tune a channel the viewer cannot see),
 *     and CENTER tunes.
 *  3. **HUD interaction by real presses**: the first OK reveals the control strip, the second OK
 *     enters it (PlayerScreen resolves `FocusHud` on key-down and swallows the paired key-up),
 *     and RIGHT walks the strip to the `−30s` pill. Those controls only compose when the
 *     authorize response carried `dvr_enabled=true` and `max_rewind_seconds>0`.
 *  4. **The seek itself is observed on the real ExoPlayer instance**: the media3 `PlayerView`
 *     sits in the activity's view tree, so the test reads `getCurrentPosition()` before and
 *     after activating `−30s` with a CENTER press, requires the position to drop by (almost
 *     exactly) 30 seconds, then that playback resumes and keeps advancing.
 *
 * Preconditions: local backend stack up, ATV emulator running, DVR stream publishing.
 */
@RunWith(AndroidJUnit4::class)
class DvrRewindTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun rewindThirtySecondsSeeksBackWithinDvrWindow() {
        // 1. Environment precondition - measured FROM THE DEVICE over the path ExoPlayer uses.
        val windowSec = requireDvrWindowOnDevice(minSeconds = 50.0)

        // 2. Real D-pad journey to the DVR channel.
        openLiveTabFromHome()
        focusChannelRow("M10 Test Channel")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        revealHud(60_000)

        // 3. The authorize response gates these controls: dvr_enabled=true and
        //    max_rewind_seconds>0 only for channel 8814, and the HUD header names the channel.
        waitTextExists("M10 Test Channel")
        waitTextExists("Go back to…")
        waitTextExists("−30s")

        // 4. Second OK enters the HUD - focus lands on the strip's first control ("Back"). The
        //    reveal and the strip's first focus can race, so enterHudStrip waits for the owner
        //    after each press rather than blindly pressing (a press while Back already owns
        //    focus would activate it and pop the player).
        enterHudStrip()
        // ... and RIGHT walks the strip the way a sofa viewer does, up to the rewind pill.
        var steps = 0
        while (steps < 8 && "−30s" !in focusOwnerLabels()) {
            press(KeyEvent.KEYCODE_DPAD_RIGHT)
            settle()
            steps++
        }
        val pillOwner = focusOwnerLabels()
        assertTrue(
            "expected the '−30s' pill to own focus but focus owner read $pillOwner " +
                "(after $steps RIGHT presses from the strip's first control)",
            "−30s" in pillOwner && pillOwner.size <= 3,
        )

        // 5. The stream must actually be decoding, and the position must sit deep enough into
        //    the DVR window for a 30s jump to be meaningful.
        composeRule.waitUntil(30_000) { playerIsPlaying() == true }
        val beforeMs = requirePosition()
        assertTrue(
            "Live position ${beforeMs}ms is too shallow for a 30s rewind " +
                "(DVR window measured on device = ${windowSec}s) - let " +
                "start_dvr_test_stream.ps1 run for at least a minute first",
            beforeMs >= 31_000L,
        )

        // 6. CENTER activates the focused −30s button -> PlayerHud.onRewind(30_000) ->
        //    exoPlayer.seekTo(currentPosition - 30_000). This is the behaviour under test.
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        composeRule.waitUntil(10_000) {
            val pos = playerPositionMs()
            pos != null && pos <= beforeMs - 27_000L
        }
        val afterMs = playerPositionMs() ?: -1L
        assertTrue(
            "Rewind did not seek back: position went from ${beforeMs}ms to ${afterMs}ms",
            beforeMs - afterMs >= 27_000L,
        )

        // 7. Playback recovers from the seek and keeps advancing - a rewound stream that stalls
        //    or errors would fail here.
        composeRule.waitUntil(15_000) { playerIsPlaying() == true }
        val resumedMs = requirePosition()
        composeRule.waitUntil(10_000) {
            val pos = playerPositionMs()
            pos != null && pos > resumedMs + 500L
        }
    }

    // --- ExoPlayer observation (no product hooks: PlayerView is in the activity view tree) ---

    /** Position in ms, or null while no player is attached yet / it is being recreated. */
    private fun playerPositionMs(): Long? = playerCall("getCurrentPosition") as? Long

    /** `Player.isPlaying`: true only while playback is actually advancing. */
    private fun playerIsPlaying(): Boolean? = playerCall("isPlaying") as? Boolean

    private fun playerCall(methodName: String): Any? = composeRule.runOnUiThread<Any?> {
        val playerView = findPlayerView(composeRule.activity.window.decorView)
            ?: return@runOnUiThread null
        val player = playerView.javaClass.getMethod("getPlayer").invoke(playerView)
            ?: return@runOnUiThread null
        runCatching { player.javaClass.getMethod(methodName).invoke(player) }.getOrNull()
    }

    private fun findPlayerView(view: View): View? {
        if (view.javaClass.name == "androidx.media3.ui.PlayerView") return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findPlayerView(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    private fun requirePosition(): Long {
        composeRule.waitUntil(10_000) {
            val pos = playerPositionMs()
            pos != null && pos > 0L
        }
        val pos = playerPositionMs()
        if (pos == null) {
            fail("ExoPlayer position could not be read from the PlayerView in the view tree")
            return 0L
        }
        return pos
    }

    // --- environment precondition -----------------------------------------------------------

    /**
     * Fetches the LL-HLS playlists **on the device** over 10.0.2.2:3333 (the same rewritten
     * origin ExoPlayer plays from) and returns the summed media-playlist duration in seconds.
     * Fails with fixture instructions when the stream is missing or the DVR window is too
     * young to hold a −30s rewind.
     */
    private fun requireDvrWindowOnDevice(minSeconds: Double): Double {
        val instructions = "Start the fixture first: run tavuno-infra\\start_dvr_test_stream.ps1 " +
            "and let it publish for ~60s so the DVR window covers a -30s rewind."

        val master = readUrl("http://10.0.2.2:3333/tavuno/channel_8814/llhls.m3u8")
        assertTrue(
            "Device could not fetch the LL-HLS master playlist from 10.0.2.2:3333. $instructions",
            master.contains("#EXTM3U"),
        )
        val chunkPath = Regex("/tavuno/\\S+").find(master)?.value
        if (chunkPath == null) {
            fail("No chunklist URI inside the master playlist. $instructions")
            return 0.0
        }

        val chunklist = readUrl("http://10.0.2.2:3333$chunkPath")
        val windowSec = Regex("#EXTINF:([0-9]+(?:\\.[0-9]+)?)")
            .findAll(chunklist)
            .sumOf { it.groupValues[1].toDouble() }
        assertTrue(
            "DVR window measured on the device is only ${windowSec}s (< ${minSeconds}s). $instructions",
            windowSec >= minSeconds,
        )
        return windowSec
    }

    private fun readUrl(url: String): String = try {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        try {
            if (connection.responseCode != 200) {
                fail("GET $url returned HTTP ${connection.responseCode} on the device")
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    } catch (error: Exception) {
        fail("Could not fetch $url from the device: ${error.message}")
        throw error // unreachable: fail() throws; keeps the expression typed as String
    }

    // --- navigation helpers (same measured shape as DPadNavigationTest) ---------------------

    /**
     * Home -> rail -> "Live TV" -> CENTER; waits for the tab body. The first press establishes
     * the focus owner (a cold start has none) and it always lands on the rail's current
     * destination, so a second DOWN steps from Home to Live TV.
     */
    private fun openLiveTabFromHome() {
        waitTextExists("Welcome to Tavuno")
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        waitFocusOwner("Home")
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        waitFocusOwner("Live TV")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        waitTextExists("All")
    }

    /** Walks DOWN the channel list until [name] owns focus (the LazyColumn scrolls with focus). */
    private fun focusChannelRow(name: String) {
        enterChannelList()
        var steps = 0
        while (steps < CHANNEL_WALK_LIMIT && name !in focusOwnerLabels()) {
            press(KeyEvent.KEYCODE_DPAD_DOWN)
            settle()
            steps++
        }
        assertFocusOwnerContains(name)
    }

    /**
     * Enters the channel grid from wherever the tab activation left focus. Rows only compose once
     * `GET /v1/channels` answered, and the first directional press after the switch can be
     * swallowed (measured: the owner still read [Live TV] through a RIGHT and a DOWN before a
     * press took), so the entry alternates RIGHT (rail -> first row) and DOWN (top bar -> first
     * row) until a row's "▶" play affordance proves a row owns focus.
     */
    private fun enterChannelList() {
        val rowsReady = runCatching {
            composeRule.waitUntil(20_000) {
                labelNodes().any { (_, label) -> label.contains("▶") }
            }
        }
        assertTrue(
            "channel rows never composed - GET /v1/channels did not answer on the device" +
                (rowsReady.exceptionOrNull()?.let { " (wait threw: $it)" } ?: ""),
            rowsReady.isSuccess,
        )
        var attempts = 0
        while (attempts < ENTER_ROW_ATTEMPTS && "▶" !in focusOwnerLabels()) {
            press(if (attempts % 2 == 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_DOWN)
            settle(250)
            attempts++
        }
        assertTrue(
            "could not enter the channel grid: focus owner read ${focusOwnerLabels()} after " +
                "$ENTER_ROW_ATTEMPTS directional attempts",
            "▶" in focusOwnerLabels(),
        )
    }

    /**
     * Enters the HUD control strip with OK presses that respect the reveal/first-focus race:
     * after each press the owner gets 3s to become the strip's "Back" control before another
     * press is sent - pressing while Back already owns focus would ACTIVATE it (and pop the
     * player), and the label-less video surface keeps ownership while nothing in the strip is
     * focusable yet.
     */
    private fun enterHudStrip() {
        var presses = 0
        while (presses < 4) {
            val landed = runCatching {
                composeRule.waitUntil(3_000) { focusOwnerLabels().toSet() == setOf("Back") }
            }.isSuccess
            if (landed) return
            press(KeyEvent.KEYCODE_DPAD_CENTER)
            presses++
        }
        waitFocusOwnerExactly("Back")
    }

    /**
     * The HUD only composes once the player is out of its loading state, so one blind CENTER can
     * race the authorize hop. Retry until the strip is up: presses during loading are harmless
     * (ToggleHud just flips hudVisible while the loading indicator renders), and the check runs
     * before every press so the helper returns before an extra press could resolve to FocusHud.
     */
    private fun revealHud(timeoutMs: Long) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (hudVisible()) return
            press(KeyEvent.KEYCODE_DPAD_CENTER)
            runCatching { composeRule.waitUntil(2_000) { hudVisible() } }
        }
        fail(
            "Player HUD never appeared within ${timeoutMs}ms - playback authorization or the live " +
                "stream did not become ready on the device",
        )
    }

    /** True while the player's control strip is composed (help line or a DVR control visible). */
    private fun hudVisible(): Boolean =
        composeRule.onAllNodesWithText("BACK returns to the menu", substring = true)
            .fetchSemanticsNodes().isNotEmpty() ||
            composeRule.onAllNodesWithText("Go back to…").fetchSemanticsNodes().isNotEmpty()

    // --- semantics helpers -----------------------------------------------------------------

    /**
     * Injects a real KEYCODE_* press through [android.app.Instrumentation.getUiAutomation] -
     * shell-level input injection, the same path a remote's key event takes into the window, so
     * focus traversal, click activation and the player's key map all see a genuine event.
     */
    private fun press(keyCode: Int) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val eventTime = SystemClock.uptimeMillis()
        val down = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0)
        val up = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0)
        check(instrumentation.uiAutomation.injectInputEvent(down, true)) {
            "Injecting KEYCODE_$keyCode (down) was rejected"
        }
        check(instrumentation.uiAutomation.injectInputEvent(up, true)) {
            "Injecting KEYCODE_$keyCode (up) was rejected"
        }
    }

    /** Lets the key event and the resulting recomposition/focus move settle. */
    private fun settle(sleepMs: Long = 500) {
        composeRule.waitForIdle()
        SystemClock.sleep(sleepMs)
    }

    /**
     * Labels of the node that owns D-pad focus: the *smallest* focused node, because the player's
     * video surface is a full-screen focusable. Empty when nothing in the tree is focused. A
     * `FocusableSurface` never merges its label, so ownership is read geometrically - the labels
     * whose centre falls inside the owner's bounds belong to it.
     */
    private fun focusOwnerLabels(): List<String> {
        val labels = labelNodes()
        val owner = composeRule.onAllNodes(isFocused(), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .minByOrNull { node ->
                val rect = node.boundsInRoot
                (rect.right - rect.left) * (rect.bottom - rect.top)
            } ?: return emptyList()
        val rect = owner.boundsInRoot
        return labels.filter { rect.contains(Offset(it.first.center.x, it.first.center.y)) }
            .map { it.second }
    }

    private fun assertFocusOwnerContains(label: String) {
        val labels = focusOwnerLabels()
        assertTrue(
            "expected the focused element to contain '$label' but focus owner read $labels",
            labels.any { it.contains(label) },
        )
    }

    private fun waitFocusOwner(label: String, timeoutMs: Long = 15_000) {
        composeRule.waitUntil(timeoutMs) { focusOwnerLabels().any { it.contains(label) } }
    }

    /**
     * Waits until [label] is the *only* label inside the focus owner - needed for the HUD strip,
     * where "must contain Back" would also pass while the full-screen video surface owns focus
     * (its bounds geometrically contain every label on screen).
     */
    private fun waitFocusOwnerExactly(label: String, timeoutMs: Long = 15_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (focusOwnerLabels().toSet() == setOf(label)) return
            composeRule.waitForIdle()
            SystemClock.sleep(200)
        }
        fail("expected only '$label' to own D-pad focus but focus owner read ${focusOwnerLabels()}")
    }

    /** Every label-bearing node as (bounds, label); a label is a Text or a contentDescription. */
    private fun labelNodes(): List<Pair<Rect, String>> {
        val matcher = hasText("", substring = true) or hasContentDescription("", substring = true)
        return composeRule.onAllNodes(matcher, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .flatMap { node ->
                val texts = node.config.getOrElseNullable(SemanticsProperties.Text) { null }
                    .orEmpty().map { it.text }
                val descriptions = node.config
                    .getOrElseNullable(SemanticsProperties.ContentDescription) { null }.orEmpty()
                (texts + descriptions).map { node.boundsInRoot to it }
            }
    }

    private fun waitTextExists(text: String, timeoutMs: Long = 20_000, substring: Boolean = false) {
        composeRule.waitUntil(timeoutMs) {
            composeRule.onAllNodesWithText(text, substring = substring)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        /** The catalog carries 11 channels today; leave headroom for a longer list. */
        const val CHANNEL_WALK_LIMIT = 16

        /** Directional retries before giving up on reaching the grid (first presses can be swallowed). */
        const val ENTER_ROW_ATTEMPTS = 6
    }
}
