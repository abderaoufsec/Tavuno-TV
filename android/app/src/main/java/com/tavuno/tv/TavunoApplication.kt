package com.tavuno.tv

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.tavuno.tv.core.AppModule
import com.tavuno.tv.network.NetworkModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TavunoApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        AppModule.init(this)
        CoroutineScope(Dispatchers.IO).launch {
            val token = AppModule.sessionManager.accessToken.first()
            NetworkModule.updateAuthToken(token)
        }
        createNotificationChannel()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "tavuno_playback_channel"
            val channelName = "Playback Notifications"
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications for playback status"
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }
}
