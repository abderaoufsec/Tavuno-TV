package com.tavuno.tv.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.ext.junit.rules.ActivityScenarioRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.tavuno.tv.MainActivity

/**
 * Instrumented smoke test for app launch.
 *
 * This test verifies the app can launch without crashing.
 * Full integration testing requires a running backend.
 */
@RunWith(AndroidJUnit4::class)
class DPadNavigationTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun appLaunchesSuccessfully() {
        // If we reach here, the app launched successfully
        // This is a smoke test to verify basic app functionality
    }
}
