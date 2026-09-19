package com.streamvault.app.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

data class DeviceUiModel(
    val id: Int,
    val displayName: String,
    val platform: String,
    val lastSeenAt: String,
    val isCurrent: Boolean,
    val isRevoked: Boolean
)

sealed interface DeviceListUiState {
    data object Loading : DeviceListUiState
    data class Loaded(val devices: List<DeviceUiModel>) : DeviceListUiState
    data class Error(val message: String) : DeviceListUiState
}

@Composable
fun DeviceListScreen(
    onBack: () -> Unit,
    viewModel: DeviceListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadDevices()
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(48.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Devices",
                    style = MaterialTheme.typography.headlineMedium
                )
                Button(onClick = onBack) {
                    Text("Back")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            when (val state = uiState) {
                is DeviceListUiState.Loading -> {
                    Text("Loading devices...")
                }
                is DeviceListUiState.Loaded -> {
                    if (state.devices.isEmpty()) {
                        Text("No devices registered")
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(state.devices) { device ->
                                DeviceRow(
                                    device = device,
                                    onRevoke = { viewModel.revokeDevice(device.id) }
                                )
                            }
                        }
                    }
                }
                is DeviceListUiState.Error -> {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(
    device: DeviceUiModel,
    onRevoke: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = device.displayName,
                style = MaterialTheme.typography.titleMedium
            )
            if (device.isCurrent) {
                Text(
                    text = "This device",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Text(
            text = "${device.platform} • ${humanizeLastSeen(device.lastSeenAt)}",
            style = MaterialTheme.typography.bodySmall
        )
        if (!device.isCurrent && !device.isRevoked) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onRevoke,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Revoke")
            }
        }
        if (device.isRevoked) {
            Text(
                text = "Revoked",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

private fun humanizeLastSeen(isoString: String): String {
    return try {
        val instant = Instant.parse(isoString)
        val now = Instant.now()
        val minutes = ChronoUnit.MINUTES.between(instant, now)
        val hours = ChronoUnit.HOURS.between(instant, now)
        val days = ChronoUnit.DAYS.between(instant, now)

        when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "$minutes minute${if (minutes > 1) "s" else ""} ago"
            hours < 24 -> "$hours hour${if (hours > 1) "s" else ""} ago"
            days < 7 -> "$days day${if (days > 1) "s" else ""} ago"
            else -> {
                val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
                    .withZone(ZoneId.systemDefault())
                formatter.format(instant)
            }
        }
    } catch (e: Exception) {
        "Unknown"
    }
}
