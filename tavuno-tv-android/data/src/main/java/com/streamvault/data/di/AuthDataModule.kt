package com.streamvault.data.di

import com.streamvault.data.preferences.TokenStore
import com.streamvault.data.remote.tavuno.AuthRepositoryImpl
import com.streamvault.data.remote.tavuno.TavunoApiService
import com.streamvault.data.remote.tavuno.TavunoAuthInterceptor
import com.streamvault.data.util.DeviceFingerprintGenerator
import com.streamvault.domain.repository.AuthRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TavunoApiUrl

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthDataModule {

    @Binds
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    companion object {

        @Provides
        @Singleton
        @TavunoApiUrl
        fun provideTavunoApiUrl(): String {
            // Use emulator-friendly URL for debug, real URL for release
            return "http://10.0.2.2:8000"
        }

        @Provides
        @Singleton
        fun provideTavunoApiService(
            @TavunoApiUrl baseUrl: String,
            tokenStore: TokenStore,
            deviceFingerprintGenerator: DeviceFingerprintGenerator
        ): TavunoApiService {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val authInterceptor = TavunoAuthInterceptor(
                tokenStore,
                deviceFingerprintGenerator
            )

            val okHttpClient = OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .addInterceptor(authInterceptor)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            return retrofit.create(TavunoApiService::class.java)
        }
    }
}