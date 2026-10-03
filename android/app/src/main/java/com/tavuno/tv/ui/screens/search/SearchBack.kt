package com.tavuno.tv.ui.screens.search

/**
 * What a BACK press belongs to while Search is on screen.
 *
 * BACK is a ladder: the soft keyboard is the lowest rung, the Search tab is the next, and the shell
 * owns everything above that (see `TavunoMainScreen`). Only that lowest rung is Search's business —
 * the rest of the ladder is left alone so it keeps behaving exactly as it did before this screen
 * grew a keyboard.
 */
internal enum class SearchBackAction {

    /**
     * The keyboard is on screen: put it away, swallow the press, and stay on Search.
     *
     * The viewer is mid-query here, so a press that reached the shell would throw them off the tab
     * they are typing on.
     */
    DismissIme,

    /**
     * The keyboard is down: Search has nothing of its own open.
     *
     * The press is deliberately *not* consumed, which leaves it to the shell and lets the shell's
     * own ladder (sub-screen, then tab, then out of the app) run untrammelled.
     */
    FallThroughToShell,
}

/**
 * Resolve a BACK press against the keyboard's real state.
 *
 * [imeVisible] has to be read live, at the moment of the press, from the window insets — it must not
 * be remembered between presses. Two properties of this TV build make a remembered flag wrong:
 *
 *  1. The keyboard is a floating window that resizes nothing, so focus never moves when the keyboard
 *     comes and goes. A flag set from focus therefore never clears.
 *  2. The IME window consumes the first BACK itself to hide the keyboard, and the app is never told
 *     it went away. A remembered `true` then outlives the keyboard and eats the *next* press, so the
 *     viewer has to press BACK twice to leave Search.
 *
 * Read at press time the question is exact — the keyboard is either on screen or it is not — and no
 * state has to survive from one press to the next.
 */
internal fun resolveSearchBack(imeVisible: Boolean): SearchBackAction = when {
    imeVisible -> SearchBackAction.DismissIme
    else -> SearchBackAction.FallThroughToShell
}
