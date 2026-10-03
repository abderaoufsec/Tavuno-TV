package com.tavuno.tv.ui.screens.search

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the shape of the BACK ladder while Search is on screen.
 *
 * The two branches are not two implementations of one thing — they are the whole contract. With the
 * keyboard up the press belongs to Search and must be swallowed so the viewer keeps typing; with the
 * keyboard down Search has nothing of its own open, and a press that swallowed there would be one
 * more press than the shell's ladder expects, i.e. the viewer would need an extra BACK to leave.
 */
class SearchBackTest {

    @Test
    fun `keyboard on screen - BACK dismisses the IME and stays on Search`() {
        assertEquals(SearchBackAction.DismissIme, resolveSearchBack(imeVisible = true))
    }

    @Test
    fun `keyboard down - BACK falls through to the shell's own ladder`() {
        assertEquals(SearchBackAction.FallThroughToShell, resolveSearchBack(imeVisible = false))
    }

    /**
     * The resolver is a pure function of the press-time reading, which is the point of it: the
     * keyboard's state is re-read on every press rather than remembered, so the same sequence of
     * readings gives the same ladder no matter what happened before it.
     */
    @Test
    fun `the ladder follows the keyboard, not the press order`() {
        val presses = listOf(true, false, false, true, false)
        val actions = presses.map { resolveSearchBack(imeVisible = it) }
        assertEquals(
            listOf(
                SearchBackAction.DismissIme,
                SearchBackAction.FallThroughToShell,
                SearchBackAction.FallThroughToShell,
                SearchBackAction.DismissIme,
                SearchBackAction.FallThroughToShell,
            ),
            actions,
        )
    }
}
