package com.tavuno.tv.ui

import android.os.SystemClock
import android.view.KeyEvent
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

/**
 * Real D-pad navigation through the real app: every step is a genuine `KEYCODE_DPAD_*` / BACK key
 * event injected through [android.app.Instrumentation.getUiAutomation] (the same input path a
 * remote's IR blaster feeds), and every assertion reads focus back out of the Compose semantics
 * tree. Nothing here calls `performClick`, and no test-only tags exist in the product code.
 *
 * Two properties of the app shape the helpers below, and both were measured on device rather than
 * assumed:
 *
 *  1. **A cold start has no focus owner.** `TavunoMainScreen` passes `requestSidebarFocus = false`
 *     and nothing else requests focus, so the shell legitimately starts with no `Focused` semantics
 *     anywhere; the first D-pad press is what hands focus to the rail's current destination
 *     (`TavunoSidebar` holds that item's `FocusRequester`).
 *  2. **A `FocusableSurface` never merges its label into the focused node.** The focus owner is the
 *     surface itself; the label is a child `Text` (or an icon's `contentDescription`). So "what is
 *     focused" is read geometrically: the labels whose centre falls inside the focused node's
 *     bounds belong to it. `focusOwnerLabels()` takes the *smallest* focused node, because the
 *     in-player video surface is a full-screen focusable that would otherwise swallow every label.
 *
 * Preconditions: the local stack (tavuno-infra) is up and the emulator is running. Home renders
 * without a backend, but the Live TV assertions need `GET /v1/channels` to answer on
 * `10.0.2.2:8000` - a channel row cannot exist without the catalog.
 */
@RunWith(AndroidJUnit4::class)
class DPadNavigationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    // --- cold start ------------------------------------------------------------------------

    @Test
    fun coldStartFocus_isEmptyUntilTheFirstPressThenWalksRailAndHomeRails() {
        waitTextExists("Welcome to Tavuno")

        // Real cold-start behaviour: nothing owns D-pad focus yet.
        assertTrue(
            "expected the shell to start with no focus owner, but focus owner read " +
                "${focusOwnerLabels()}",
            focusOwnerLabels().isEmpty(),
        )

        // The first press sends focus to the rail's current destination (Home) and expands the
        // rail; a rail item is labelled with the tab name only (icon description + expanded label).
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        waitFocusOwner("Home")
        assertRailItemFocused("Home")

        // Vertical travel inside the rail.
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        waitFocusOwner("Live TV")
        assertRailItemFocused("Live TV")
        press(KeyEvent.KEYCODE_DPAD_UP)
        waitFocusOwner("Home")

        // RIGHT enters the content pane and lands on the Browse rail's first tile.
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        waitFocusOwner("Watch live channels")
        assertFocusOwnerContains("Live TV")

        // ... and horizontal travel moves through the tiles.
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        waitFocusOwner("Live sports events")
        assertFocusOwnerContains("Sports")
        press(KeyEvent.KEYCODE_DPAD_LEFT)
        waitFocusOwner("Watch live channels")
        assertFocusOwnerContains("Live TV")
    }

    // --- sidebar rail + channel rows --------------------------------------------------------

    @Test
    fun sidebarRailSwitchesTabsAndChannelRowsTakeFocus() {
        openLiveTabFromHome()

        // The rail item that was activated keeps focus across the tab switch (the shell is never
        // torn down), and the tab body loaded: the "All" filter chip only exists once the
        // categories call answered.
        assertRailItemFocused("Live TV")
        waitTextExists("All")

        // Enter the channel list: a row only composes once `GET /v1/channels` answered, and its
        // "▶" play affordance distinguishes it from a filter chip (enterChannelList retries -
        // the first directional press after the tab switch can be swallowed).
        enterChannelList()
        val firstRow = focusOwnerLabels()

        // Vertical travel must move to a different row (and scroll the LazyColumn with it).
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        composeRule.waitUntil(10_000) { focusOwnerLabels().toSet() != firstRow.toSet() }
        assertFocusOwnerContains("▶")
        val secondRow = focusOwnerLabels()
        assertTrue(
            "DOWN did not move to another channel row: focus is still $secondRow",
            firstRow.toSet() != secondRow.toSet(),
        )
    }

    // --- BACK stack ------------------------------------------------------------------------

    @Test
    fun backFromLiveTabReturnsToHome() {
        openLiveTabFromHome()
        waitTextExists("All")

        press(KeyEvent.KEYCODE_BACK)

        // TavunoMainScreen's BackHandler unwinds the tab to Home instead of finishing the activity.
        waitTextExists("Welcome to Tavuno")
        waitTextExists("Pick a section to start watching.")
    }

    // --- CENTER + BACK ladder --------------------------------------------------------------

    @Test
    fun centerTunesChannelIntoPlayerAndBackLadderReturnsToShell() {
        openLiveTabFromHome()
        focusChannelRow("M10 Test Channel")

        // CENTER tunes: publishes the zap list and pushes the player route.
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        revealHud(60_000)

        // The player is full-screen, so none of the shell is composed behind it.
        waitTextAbsent("All")
        waitTextAbsent("Welcome to Tavuno")

        // A second OK enters the HUD: PlayerScreen resolves it to FocusHud on key-down (focus
        // lands on the strip's first control, "Back") and swallows the paired key-up. The strip's
        // reveal and its first focus can race, so enterHudStrip waits for the owner after each
        // press instead of blindly pressing - a press while Back already owns focus would
        // activate it and pop the player.
        enterHudStrip()

        press(KeyEvent.KEYCODE_BACK)
        // First BACK closes the HUD (PlayerRemote: hudVisible -> ToggleHud) ...
        composeRule.waitUntil(10_000) { !hudVisible() }
        // ... and we are still inside the player.
        waitTextAbsent("Welcome to Tavuno")

        // Second BACK leaves the player (PlayerKey.Back -> nav.popBackStack); the shell is still
        // parked on the Live TV tab it tuned from - and, exactly as on a cold start, popping the
        // player does not restore focus, so nothing owns focus again.
        press(KeyEvent.KEYCODE_BACK)
        waitTextExists("All")
        waitTextExists("Live TV")
        assertTrue(
            "expected the restored shell to have no focus owner, but focus owner read " +
                "${focusOwnerLabels()}",
            focusOwnerLabels().isEmpty(),
        )
    }

    // --- navigation helpers ----------------------------------------------------------------

    /**
     * Home -> rail -> the "Live TV" rail item -> CENTER, then waits for the tab body.
     *
     * The first press is what establishes a focus owner (see the class comment) and it always
     * lands on the rail's current destination, so this walk is stable from a cold start.
     */
    private fun openLiveTabFromHome() {
        waitTextExists("Welcome to Tavuno")
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        focusRailItem("Live TV")
        assertRailItemFocused("Live TV")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        waitTextExists("All")
    }

    /**
     * Parks D-pad focus on [label] inside the sidebar rail. Entering the rail (LEFT) lands on
     * whichever rail item is geometrically closest to the tile that had focus, so the walk first
     * climbs to the rail's first item and then steps down - both directions are real key events.
     */
    private fun focusRailItem(label: String) {
        if (!railHasFocus()) press(KeyEvent.KEYCODE_DPAD_LEFT)
        waitAnyFocus()

        // Climb to the top of the rail: keep pressing UP until the focus owner stops changing.
        var previous = focusOwnerLabels().toSet()
        var climbs = 0
        while (climbs < RAIL_LENGTH) {
            press(KeyEvent.KEYCODE_DPAD_UP)
            settle()
            val current = focusOwnerLabels().toSet()
            climbs++
            if (current == previous) break
            previous = current
        }

        var steps = 0
        while (steps < RAIL_LENGTH && label !in focusOwnerLabels()) {
            press(KeyEvent.KEYCODE_DPAD_DOWN)
            settle()
            steps++
        }
        assertTrue(
            "could not reach the '$label' rail item: focus owner read ${focusOwnerLabels()}",
            label in focusOwnerLabels(),
        )
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
     * The HUD starts hidden and only composes once the player is out of its loading state, so a
     * single CENTER can race the authorize hop. Keep pressing OK the way a viewer would: the first
     * press that lands after loading reveals the controls, and a later press just enters them
     * (harmless here - this helper only cares that the strip is up).
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
     * video surface is a full-screen focusable. Empty when nothing in the tree is focused.
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

    /** True when the focus owner is a sidebar item rather than a content tile. */
    private fun railHasFocus(): Boolean {
        val labels = focusOwnerLabels().toSet()
        return labels.size == 1 && labels.first() in RAIL_LABELS
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

    private fun assertFocusOwnerContains(label: String) {
        val labels = focusOwnerLabels()
        assertTrue(
            "expected the focused element to contain '$label' but it read $labels",
            labels.any { it.contains(label) },
        )
    }

    /** A rail item carries the tab name twice (icon description + label) and nothing else. */
    private fun assertRailItemFocused(label: String) {
        val labels = focusOwnerLabels()
        assertTrue(
            "expected the '$label' rail item to be focused but focus owner read $labels",
            labels.toSet() == setOf(label),
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

    private fun waitAnyFocus(timeoutMs: Long = 15_000) {
        composeRule.waitUntil(timeoutMs) { focusOwnerLabels().isNotEmpty() }
    }

    private fun waitTextExists(text: String, timeoutMs: Long = 20_000, substring: Boolean = false) {
        composeRule.waitUntil(timeoutMs) {
            composeRule.onAllNodesWithText(text, substring = substring)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitTextAbsent(text: String, timeoutMs: Long = 10_000) {
        composeRule.waitUntil(timeoutMs) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()
        }
    }

    private companion object {
        /** The eight rail destinations, in TavunoTab order. */
        val RAIL_LABELS = setOf(
            "Home", "Live TV", "Guide", "Sports", "Movies", "Series", "Search", "Settings",
        )

        /** Every rail item is reachable in fewer steps than this from either end. */
        const val RAIL_LENGTH = 8

        /** The catalog carries 11 channels today; leave headroom for a longer list. */
        const val CHANNEL_WALK_LIMIT = 16

        /** Directional retries before giving up on reaching the grid (first presses can be swallowed). */
        const val ENTER_ROW_ATTEMPTS = 6
    }
}

