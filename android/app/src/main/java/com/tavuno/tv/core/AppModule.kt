package com.tavuno.tv.core

import android.content.Context
import com.tavuno.tv.data.api.TavunoApiService
import com.tavuno.tv.data.local.SessionManager
import com.tavuno.tv.data.repository.AuthRepository
import com.tavuno.tv.data.repository.CatalogRepository
import com.tavuno.tv.data.repository.PlaybackRepository
import com.tavuno.tv.data.repository.SportsRepository
import com.tavuno.tv.network.NetworkModule

object AppModule {
    
    private lateinit var context: Context
    
    fun init(context: Context) {
        this.context = context.applicationContext
    }
    
    val sessionManager: SessionManager by lazy {
        SessionManager(context)
    }
    
    val authRepository: AuthRepository by lazy {
        AuthRepository(NetworkModule.apiService, sessionManager)
    }
    
    val catalogRepository: CatalogRepository by lazy {
        CatalogRepository(NetworkModule.apiService)
    }
    
    val sportsRepository: SportsRepository by lazy {
        SportsRepository(NetworkModule.apiService)
    }
    
    val playbackRepository: PlaybackRepository by lazy {
        PlaybackRepository(NetworkModule.apiService, sessionManager)
    }
}
