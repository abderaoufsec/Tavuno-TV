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
import com.tavuno.tv.ui.theme.TavunoAccent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import com.tavuno.tv.network.NetworkModule

@Composable
fun SplashScreen(
    sessionManager: com.tavuno.tv.data.local.SessionManager,
    onNavigateToHome: () -> Unit
) {
    LaunchedEffect(Unit) {
        delay(400)
        // Open access (free launch): no login gate. Seed a cached token when
        // one exists so logged-in users keep their identity; otherwise the
        // backend resolves the anonymous guest.
        val token = sessionManager.accessToken.first()
        NetworkModule.updateAuthToken(token)
        onNavigateToHome()
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
