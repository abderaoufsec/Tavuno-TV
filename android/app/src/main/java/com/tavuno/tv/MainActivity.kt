package com.tavuno.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.darkColorScheme
import com.tavuno.tv.ui.navigation.TavunoNavigation
import com.tavuno.tv.ui.theme.TavunoTVTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TavunoTVTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    TavunoNavigation()
                }
            }
        }
    }
}
