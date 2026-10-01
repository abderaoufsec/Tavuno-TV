package com.tavuno.tv.ui.shell

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Tv
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The shell's top-level destinations, in nav-rail order, ported from the OwnTV-Baseline shell
 * model. The rail is the first of the shell's four layers, so these are the only screens that get
 * a persistent sidebar; detail and player routes are pushed full-screen on top.
 */
enum class TavunoTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Filled.Home),
    LIVE("Live TV", Icons.Filled.LiveTv),
    SPORTS("Sports", Icons.Filled.SportsSoccer),
    MOVIES("Movies", Icons.Filled.Movie),
    SERIES("Series", Icons.Filled.Tv),
    SETTINGS("Settings", Icons.Filled.Settings),
}