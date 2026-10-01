package com.tavuno.tv.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tavuno.tv.ui.screens.auth.LoginScreen
import com.tavuno.tv.ui.screens.movies.MovieDetailsScreen
import com.tavuno.tv.ui.screens.player.PlayerScreen
import com.tavuno.tv.ui.screens.series.SeasonEpisodesScreen
import com.tavuno.tv.ui.screens.series.SeriesDetailsScreen
import com.tavuno.tv.ui.screens.splash.SplashScreen

/**
 * Routes the shell does not own: splash/login before it, and the detail/player routes pushed
 * full-screen on top of it. The six browsing destinations live inside [TavunoMainScreen] instead,
 * because they must share one persistent menu shell.
 */
sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Login : Screen("login")
    object Main : Screen("main")
    object Player : Screen("player")

    object MovieDetails : Screen("movie/{movieId}") {
        fun createRoute(movieId: Int) = "movie/$movieId"
    }

    object SeriesDetails : Screen("series/{seriesId}") {
        fun createRoute(seriesId: Int) = "series/$seriesId"
    }

    object SeasonEpisodes : Screen("season/{seasonId}") {
        fun createRoute(seasonId: Int) = "season/$seasonId"
    }
}

@Composable
fun TavunoNavigation(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(
                sessionManager = com.tavuno.tv.core.AppModule.sessionManager,
                onNavigateToHome = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        // Login stays registered but is not routed to during free launch
        // (AUTH_OPEN_ACCESS); re-enable by navigating here from splash/logout.
        composable(Screen.Login.route) {
            LoginScreen(
                authRepository = com.tavuno.tv.core.AppModule.authRepository,
                onLoginSuccess = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Main.route) {
            TavunoMainScreen(
                onNavigateToMovieDetails = { movieId ->
                    navController.navigate(Screen.MovieDetails.createRoute(movieId))
                },
                onNavigateToSeriesDetails = { seriesId ->
                    navController.navigate(Screen.SeriesDetails.createRoute(seriesId))
                },
                onNavigateToPlayer = { type, id ->
                    navController.navigate(Screen.Player.route + "/$type/$id")
                },
                onLogout = {
                    // Free launch has no login screen to fall back to:
                    // drop the cached token and rebuild the shell as guest.
                    com.tavuno.tv.network.NetworkModule.updateAuthToken(null)
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Main.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.MovieDetails.route) { backStackEntry ->
            val movieId = backStackEntry.arguments?.getString("movieId")?.toIntOrNull() ?: 0
            MovieDetailsScreen(
                movieId = movieId,
                catalogRepository = com.tavuno.tv.core.AppModule.catalogRepository,
                playbackRepository = com.tavuno.tv.core.AppModule.playbackRepository,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPlayer = { type, id ->
                    navController.navigate(Screen.Player.route + "/$type/$id")
                }
            )
        }

        composable(Screen.SeriesDetails.route) { backStackEntry ->
            val seriesId = backStackEntry.arguments?.getString("seriesId")?.toIntOrNull() ?: 0
            SeriesDetailsScreen(
                seriesId = seriesId,
                catalogRepository = com.tavuno.tv.core.AppModule.catalogRepository,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSeason = { seasonId ->
                    navController.navigate(Screen.SeasonEpisodes.createRoute(seasonId))
                }
            )
        }

        composable(Screen.SeasonEpisodes.route) { backStackEntry ->
            val seasonId = backStackEntry.arguments?.getString("seasonId")?.toIntOrNull() ?: 0
            SeasonEpisodesScreen(
                seasonId = seasonId,
                catalogRepository = com.tavuno.tv.core.AppModule.catalogRepository,
                playbackRepository = com.tavuno.tv.core.AppModule.playbackRepository,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPlayer = { type, id ->
                    navController.navigate(Screen.Player.route + "/$type/$id")
                }
            )
        }

        composable(Screen.Player.route + "/{contentType}/{contentId}") { backStackEntry ->
            val contentType = backStackEntry.arguments?.getString("contentType") ?: "live"
            val contentId = backStackEntry.arguments?.getString("contentId") ?: "0"
            PlayerScreen(
                contentType = contentType,
                contentId = contentId,
                playbackRepository = com.tavuno.tv.core.AppModule.playbackRepository,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}