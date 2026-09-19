package com.streamvault.feature.provider.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.streamvault.core.ui.interaction.TvButton
import com.streamvault.core.ui.theme.AccentCyan
import com.streamvault.core.ui.theme.ErrorColor
import com.streamvault.core.ui.theme.TextPrimary
import com.streamvault.core.ui.theme.TextSecondary
import com.streamvault.feature.provider.R

@Composable
internal fun TavunoProviderForm(
    serverUrl: String,
    onServerUrlChange: (String) -> Unit,
    onTestConnection: () -> Unit,
    testResult: String?,
    isEditing: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = "Tavuno",
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary
        )

        ProviderTextField(
            value = serverUrl,
            onValueChange = onServerUrlChange,
            placeholder = "http://10.0.2.2:8000"
        )

        if (!isEditing) {
            TvButton(
                onClick = onTestConnection,
                colors = ButtonDefaults.colors(containerColor = AccentCyan, contentColor = androidx.compose.ui.graphics.Color.Black)
            ) {
                Text("Test Connection")
            }
        }

        testResult?.let { result ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = result,
                style = MaterialTheme.typography.bodyMedium,
                color = if (result.contains("Success", ignoreCase = true)) TextPrimary else ErrorColor
            )
        }
    }
}
