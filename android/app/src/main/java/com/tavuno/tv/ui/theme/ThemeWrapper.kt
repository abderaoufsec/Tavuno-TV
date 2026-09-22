package com.tavuno.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

@Composable
fun TavunoTVTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TavunoColorScheme,
        typography = TavunoTypography,
        content = content
    )
}
