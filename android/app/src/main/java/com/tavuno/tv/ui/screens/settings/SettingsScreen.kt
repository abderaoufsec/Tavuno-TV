package com.tavuno.tv.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.tavuno.tv.ui.components.FocusableSurface
import com.tavuno.tv.ui.components.TavunoButton
import com.tavuno.tv.ui.components.TavunoButtonStyle
import com.tavuno.tv.ui.theme.Dimens
import com.tavuno.tv.ui.theme.TavunoTheme
import kotlinx.coroutines.launch

/**
 * The Settings destination, rebuilt on the shell + design system.
 *
 * Rows are full-width focusable surfaces with an MD3 tonal icon tile, so they read consistently
 * with the rest of the app and stay inside the shell's content panel instead of centring a fixed
 * column. Logout is still driven from the composition scope (a bare `Dispatchers.IO` launch would
 * touch Compose state off the main thread and crash).
 */
@Composable
fun SettingsScreen(
    authRepository: com.tavuno.tv.data.repository.AuthRepository,
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit,
) {
    val colors = TavunoTheme.colors
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = Dimens.GapSmall),
        verticalArrangement = Arrangement.spacedBy(Dimens.GapSmall),
    ) {
        item(key = "header") {
            Column {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineLarge,
                    color = colors.textPrimary,
                )
                Text(
                    text = "Preferences for this TV.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
        }

        item(key = "account") {
            SettingsRow("Account", "Manage your account", Icons.Filled.Person) { /* placeholder */ }
        }
        item(key = "playback") {
            SettingsRow("Playback", "Playback preferences", Icons.Filled.PlayArrow) { /* placeholder */ }
        }
        item(key = "appearance") {
            SettingsRow("Appearance", "Theme and focus ring", Icons.Filled.Tune) { /* placeholder */ }
        }
        item(key = "about") {
            SettingsRow("App information", "Version and build", Icons.Filled.Info) { /* placeholder */ }
        }

        item(key = "logout") {
            Spacer(Modifier.height(Dimens.GapMedium))
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.GapMedium)) {
                TavunoButton(
                    label = "Back to Home",
                    onClick = onNavigateBack,
                    style = TavunoButtonStyle.SECONDARY,
                    modifier = Modifier.width(220.dp),
                )
                TavunoButton(
                    label = "Log out",
                    onClick = {
                        scope.launch {
                            authRepository.logout()
                            onLogout()
                        }
                    },
                    modifier = Modifier.width(220.dp),
                )
            }
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val colors = TavunoTheme.colors
    FocusableSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.CornerMedium),
        focusedScale = 1.0f,
        unfocusedContainerColor = colors.surfaceContainerLow,
        focusedContainerColor = colors.surfaceContainerLow,
        contentAlignment = Alignment.CenterStart,
    ) { focused ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.GapMedium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.GapMedium),
        ) {
            // MD3 tonal icon tile — the settings pattern from the baseline design system.
            Box(
                modifier = Modifier
                    .size(Dimens.IconTileSize)
                    .background(
                        color = colors.surfaceContainerHighest,
                        shape = RoundedCornerShape(Dimens.IconTileCorner),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (focused) colors.primary else colors.textSecondary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}