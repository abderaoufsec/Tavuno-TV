package com.tavuno.tv.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tavuno.tv.ui.screens.auth.LoginScreen
import com.tavuno.tv.ui.screens.home.HomeScreen
import com.tavuno.tv.ui.screens.live.LiveTvScreen
import com.tavuno.tv.ui.screens.sports.SportsScreen
import com.tavuno.tv.ui.screens.movies.MoviesScreen
import com.tavuno.tv.ui.screens.movies.MovieDetailsScreen
import com.tavuno.tv.ui.screens.series.SeriesScreen
import com.tavuno.tv.ui.screens.series.SeriesDetailsScreen
import com.tavuno.tv.ui.screens.series.SeasonEpisodesScreen
import com.tavuno.tv.ui.screens.player.PlayerScreen
import com.tavuno.tv.ui.screens.settings.SettingsScreen
import com.tavuno.tv.ui.screens.splash.SplashScreen

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Login : Screen("login")
    object Home : Screen("home")
    object LiveTv : Screen("live")
    object Sports : Screen("sports")
    object Movies : Screen("movies")
    object Series : Screen("series")
    object Player : Screen("player")
    object Settings : Screen("settings")
    
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
                onNavigateToLogin = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }
        
        composable(Screen.Login.route) {
            LoginScreen(
                authRepository = com.tavuno.tv.core.AppModule.authRepository,
                onLoginSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }
        
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToLive = { navController.navigate(Screen.LiveTv.route) },
                onNavigateToSports = { navController.navigate(Screen.Sports.route) },
                onNavigateToMovies = { navController.navigate(Screen.Movies.route) },
                onNavigateToSeries = { navController.navigate(Screen.Series.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }
        
        composable(Screen.LiveTv.route) {
            LiveTvScreen(
                catalogRepository = com.tavuno.tv.core.AppModule.catalogRepository,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPlayer = { channelId ->
                    navController.navigate(Screen.Player.route + "/live/$channelId")
                }
            )
        }
        
        composable(Screen.Sports.route) {
            SportsScreen(
                sportsRepository = com.tavuno.tv.core.AppModule.sportsRepository,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPlayer = { channelId ->
                    navController.navigate(Screen.Player.route + "/live/$channelId")
                }
            )
        }
        
        composable(Screen.Movies.route) {
            MoviesScreen(
                catalogRepository = com.tavuno.tv.core.AppModule.catalogRepository,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToMovieDetails = { movieId ->
                    navController.navigate(Screen.MovieDetails.createRoute(movieId))
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
        
        composable(Screen.Series.route) {
            SeriesScreen(
                catalogRepository = com.tavuno.tv.core.AppModule.catalogRepository,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSeriesDetails = { seriesId ->
                    navController.navigate(Screen.SeriesDetails.createRoute(seriesId))
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
        
        composable(Screen.Settings.route) {
            SettingsScreen(
                authRepository = com.tavuno.tv.core.AppModule.authRepository,
                onNavigateBack = { navController.popBackStack() },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }
    }
}
