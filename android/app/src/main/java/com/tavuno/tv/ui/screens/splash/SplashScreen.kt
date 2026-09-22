package com.tavuno.tv.ui.screens.splash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.tavuno.tv.data.local.SessionManager
import com.tavuno.tv.ui.theme.TavunoAccent
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    sessionManager: com.tavuno.tv.data.local.SessionManager,
    onNavigateToLogin: () -> Unit,
    onNavigateToHome: () -> Unit
) {
    LaunchedEffect(Unit) {
        delay(1000) // Show splash for 1 second
        
        // Check if user is authenticated
        var isAuthenticated = false
        try {
            sessionManager.isAuthenticated.collect { authenticated ->
                isAuthenticated = authenticated
                throw kotlinx.coroutines.CancellationException()
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Expected
        }
        
        if (isAuthenticated) {
            onNavigateToHome()
        } else {
            onNavigateToLogin()
        }
    }
    
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "TAVUNO",
                style = androidx.tv.material3.MaterialTheme.typography.displayLarge,
                color = TavunoAccent
            )
            Text(
                text = "TV",
                style = androidx.tv.material3.MaterialTheme.typography.displayMedium,
                color = androidx.tv.material3.MaterialTheme.colorScheme.onBackground
            )
            
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = TavunoAccent
            )
        }
    }
}
