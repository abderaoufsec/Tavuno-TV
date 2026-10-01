package com.tavuno.tv.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.tavuno.tv.core.AppModule
import com.tavuno.tv.ui.screens.home.HomeScreen
import com.tavuno.tv.ui.screens.live.LiveTvScreen
import com.tavuno.tv.ui.screens.movies.MoviesScreen
import com.tavuno.tv.ui.screens.search.SearchScreen
import com.tavuno.tv.ui.screens.series.SeriesScreen
import com.tavuno.tv.ui.screens.settings.SettingsScreen
import com.tavuno.tv.ui.screens.sports.SportsScreen
import com.tavuno.tv.ui.shell.TavunoShell
import com.tavuno.tv.ui.shell.TavunoTab

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

    // System BACK returns to Home before it leaves the app — the behaviour every TV remote user
    // expects. On Home itself the handler is disabled, so BACK exits normally.
    BackHandler(enabled = tab != TavunoTab.HOME) { tab = TavunoTab.HOME }

    TavunoShell(
        current = tab,
        onSelectTab = { tab = it },
        title = tab.label,
        // Focus deliberately lands on the first content item, not the rail — the platform
        // convention. LEFT reaches the rail (which expands on focus), so nothing is unreachable.
        // Pass true here only if a specific flow needs the rail focused on entry.
        requestSidebarFocus = false,
    ) {
        when (tab) {
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
                onLogout = onLogout,
            )
        }
    }
}