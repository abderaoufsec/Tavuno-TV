package com.tavuno.tv.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.tavuno.tv.core.AppModule
import com.tavuno.tv.ui.screens.customize.CustomizeCategoriesScreen
import com.tavuno.tv.ui.screens.customize.CustomizeChannelsScreen
import com.tavuno.tv.ui.screens.guide.GuideScreen
import com.tavuno.tv.ui.screens.home.HomeScreen
import com.tavuno.tv.ui.screens.live.LiveTvScreen
import com.tavuno.tv.ui.screens.movies.MoviesScreen
import com.tavuno.tv.ui.screens.profiles.ProfilesScreen
import com.tavuno.tv.ui.screens.search.SearchScreen
import com.tavuno.tv.ui.screens.series.SeriesScreen
import com.tavuno.tv.ui.screens.settings.SettingsScreen
import com.tavuno.tv.ui.screens.sports.SportsScreen
import com.tavuno.tv.ui.shell.TavunoShell
import com.tavuno.tv.ui.shell.TavunoTab

/**
 * Screens that live *inside* the shell rather than on the navigation graph.
 *
 * They share the shell's chrome (rail, top bar) with the tab they were opened from, so they
 * are switched by a local state instead of a route push — the same reason the six browsing
 * destinations are not on the NavHost either.
 */
private enum class ShellDestination { PROFILES, CUSTOMIZE_CHANNELS, CUSTOMIZE_CATEGORIES }

/**
 * The shell host: the seven top-level destinations all render inside one persistent [TavunoShell],
 * switched by an internal tab state rather than by navigation.
 *
 * This is what makes the restyle behave like a real TV app — the sidebar keeps its focus and its
 * expand/collapse animation across tab switches, because the shell is never torn down. Detail and
 * player routes are pushed on top of this by [TavunoNavigation], so they get the whole display.
 */
@Composable
fun TavunoMainScreen(
    onNavigateToMovieDetails: (Int) -> Unit,
    onNavigateToSeriesDetails: (Int) -> Unit,
    onNavigateToPlayer: (String, Int) -> Unit,
    onLogout: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(TavunoTab.HOME) }
    var destination by rememberSaveable { mutableStateOf<ShellDestination?>(null) }

    // BACK unwinds one level at a time: an open sub-screen first, then the tab back to Home,
    // and only then out of the app — which is what every TV remote user expects.
    BackHandler(enabled = destination != null) { destination = null }
    BackHandler(enabled = destination == null && tab != TavunoTab.HOME) { tab = TavunoTab.HOME }

    TavunoShell(
        current = tab,
        onSelectTab = {
            tab = it
            // Picking a tab leaves any sub-screen open on the previous one.
            destination = null
        },
        title = titleFor(tab, destination),
        // Focus deliberately lands on the first content item, not the rail — the platform
        // convention. LEFT reaches the rail (which expands on focus), so nothing is unreachable.
        // Pass true here only if a specific flow needs the rail focused on entry.
        requestSidebarFocus = false,
    ) {
        when (destination) {
            ShellDestination.PROFILES -> ProfilesScreen(
                profileRepository = AppModule.profileRepository,
                onNavigateBack = { destination = null },
            )

            ShellDestination.CUSTOMIZE_CHANNELS -> CustomizeChannelsScreen(
                catalogRepository = AppModule.catalogRepository,
                customizeRepository = AppModule.customizeRepository,
                onNavigateBack = { destination = null },
            )

            ShellDestination.CUSTOMIZE_CATEGORIES -> CustomizeCategoriesScreen(
                catalogRepository = AppModule.catalogRepository,
                customizeRepository = AppModule.customizeRepository,
                onNavigateBack = { destination = null },
            )

            null -> when (tab) {
            TavunoTab.HOME -> HomeScreen(
                onNavigateToLive = { tab = TavunoTab.LIVE },
                onNavigateToSports = { tab = TavunoTab.SPORTS },
                onNavigateToMovies = { tab = TavunoTab.MOVIES },
                onNavigateToSeries = { tab = TavunoTab.SERIES },
                onNavigateToSettings = { tab = TavunoTab.SETTINGS },
            )

            TavunoTab.LIVE -> LiveTvScreen(
                catalogRepository = AppModule.catalogRepository,
                onNavigateToPlayer = { channelId -> onNavigateToPlayer("live", channelId) },
            )

            TavunoTab.GUIDE -> GuideScreen(
                catalogRepository = AppModule.catalogRepository,
                onNavigateToPlayer = { channelId -> onNavigateToPlayer("live", channelId) },
            )

            TavunoTab.SPORTS -> SportsScreen(
                sportsRepository = AppModule.sportsRepository,
                onNavigateToPlayer = { channelId -> onNavigateToPlayer("live", channelId) },
            )

            TavunoTab.MOVIES -> MoviesScreen(
                catalogRepository = AppModule.catalogRepository,
                onNavigateToMovieDetails = onNavigateToMovieDetails,
            )

            TavunoTab.SERIES -> SeriesScreen(
                catalogRepository = AppModule.catalogRepository,
                onNavigateToSeriesDetails = onNavigateToSeriesDetails,
            )

            TavunoTab.SEARCH -> SearchScreen(
                catalogRepository = AppModule.catalogRepository,
                onNavigateToPlayer = onNavigateToPlayer,
                onNavigateToMovieDetails = onNavigateToMovieDetails,
                onNavigateToSeriesDetails = onNavigateToSeriesDetails,
            )

            TavunoTab.SETTINGS -> SettingsScreen(
                authRepository = AppModule.authRepository,
                onNavigateBack = { tab = TavunoTab.HOME },
                onOpenProfiles = { destination = ShellDestination.PROFILES },
                onOpenCustomizeChannels = { destination = ShellDestination.CUSTOMIZE_CHANNELS },
                onOpenCustomizeCategories = { destination = ShellDestination.CUSTOMIZE_CATEGORIES },
                onLogout = onLogout,
            )
            }
        }
    }
}

/** Top-bar title: the sub-screen's own name while one is open, otherwise the tab's. */
private fun titleFor(tab: TavunoTab, destination: ShellDestination?): String = when (destination) {
    ShellDestination.PROFILES -> "Who's watching?"
    ShellDestination.CUSTOMIZE_CHANNELS -> "Customize channels"
    ShellDestination.CUSTOMIZE_CATEGORIES -> "Customize categories"
    null -> tab.label
}