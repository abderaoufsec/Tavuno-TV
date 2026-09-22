package com.tavuno.tv.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.tv.material3.darkColorScheme

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

val TavunoDark = Color(0xFF1A1A1A)
val TavunoDarker = Color(0xFF0D0D0D)
val TavunoAccent = Color(0xFFE50914) // Netflix-like red
val TavunoSecondary = Color(0xFF2A2A2A)
val TavunoText = Color(0xFFFFFFFF)
val TavunoTextSecondary = Color(0xFFB3B3B3)

val TavunoColorScheme = darkColorScheme(
    primary = TavunoAccent,
    onPrimary = Color.White,
    primaryContainer = TavunoSecondary,
    onPrimaryContainer = Color.White,
    secondary = TavunoSecondary,
    onSecondary = Color.White,
    secondaryContainer = TavunoDarker,
    onSecondaryContainer = Color.White,
    tertiary = Purple80,
    onTertiary = Color.White,
    background = TavunoDark,
    onBackground = TavunoText,
    surface = TavunoSecondary,
    onSurface = TavunoText,
    surfaceVariant = TavunoDarker,
    onSurfaceVariant = TavunoTextSecondary,
    error = Color(0xFFCF6679),
    onError = Color.White,
)
