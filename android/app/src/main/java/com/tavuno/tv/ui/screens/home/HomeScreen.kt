package com.tavuno.tv.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.tavuno.tv.ui.theme.TavunoAccent
import com.tavuno.tv.ui.theme.TavunoSecondary

@Composable
fun HomeScreen(
    onNavigateToLive: () -> Unit,
    onNavigateToSports: () -> Unit,
    onNavigateToMovies: () -> Unit,
    onNavigateToSeries: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "TAVUNO TV",
            style = MaterialTheme.typography.displayLarge,
            color = TavunoAccent
        )
        
        Spacer(modifier = Modifier.height(64.dp))
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HomeCard(
                title = "LIVE TV",
                subtitle = "Watch live channels",
                onClick = onNavigateToLive
            )
            
            HomeCard(
                title = "SPORTS",
                subtitle = "Live sports events",
                onClick = onNavigateToSports
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HomeCard(
                title = "MOVIES",
                subtitle = "Watch movies",
                onClick = onNavigateToMovies
            )
            
            HomeCard(
                title = "SERIES",
                subtitle = "Watch series",
                onClick = onNavigateToSeries
            )
        }
        
        Spacer(modifier = Modifier.height(64.dp))
        
        Button(
            onClick = onNavigateToSettings,
            modifier = Modifier.width(200.dp),
            colors = ButtonDefaults.colors(
                containerColor = TavunoSecondary
            )
        ) {
            Text("Settings")
        }
    }
}

@Composable
fun HomeCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    com.tavuno.tv.ui.components.FocusableCard(
        onClick = onClick,
        modifier = Modifier
            .width(300.dp)
            .height(200.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
