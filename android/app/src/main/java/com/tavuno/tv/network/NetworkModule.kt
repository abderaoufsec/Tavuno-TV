package com.tavuno.tv.network

import com.tavuno.tv.BuildConfig
import com.tavuno.tv.data.api.TavunoApiService
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {
    
    private val BASE_URL = BuildConfig.TAVUNO_API_BASE_URL
    
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }
    
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .addInterceptor(AuthInterceptor())
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    val apiService: TavunoApiService = retrofit.create(TavunoApiService::class.java)
    
    fun updateAuthToken(token: String?) {
        AuthInterceptor.token = token
    }
}

class AuthInterceptor : Interceptor {
    companion object {
        @Volatile
        var token: String? = null
    }
    
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val originalRequest = chain.request()
        val requestBuilder = originalRequest.newBuilder()
        
        token?.let {
            requestBuilder.header("Authorization", "Bearer $it")
        }
        
        val request = requestBuilder.build()
        return chain.proceed(request)
    }
}
