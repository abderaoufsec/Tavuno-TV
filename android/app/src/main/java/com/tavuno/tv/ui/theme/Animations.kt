package com.tavuno.tv.ui.theme

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Whether the user has left animations on (Settings → Animations), ported from the OwnTV-Baseline
 * design system. Provided at the theme root; consumers gate whole transitions on it.
 */
val LocalAnimationsEnabled = staticCompositionLocalOf { true }

/** True unless the user has turned animations fully Off — for spots that gate a transition entirely. */
val animationsOn: Boolean
    @Composable @ReadOnlyComposable get() = LocalAnimationsEnabled.current

/**
 * A tween whose duration follows the user's Animations setting (Off → an instant 0 ms snap).
 *
 * **Never pass this to `infiniteRepeatable`.** Compose divides the play time by the iteration
 * duration to work out which repeat it is in, so a 0 ms iteration is a divide-by-zero on the main
 * thread one frame after the animation starts. Gate the whole transition on [animationsOn] instead
 * and hand `infiniteRepeatable` a plain fixed-duration `tween`.
 */
@Composable
@ReadOnlyComposable
fun <T> tavunoTween(durationMs: Int = 200, easing: Easing = FastOutSlowInEasing): TweenSpec<T> =
    tween(if (animationsOn) durationMs else 0, easing = easing)