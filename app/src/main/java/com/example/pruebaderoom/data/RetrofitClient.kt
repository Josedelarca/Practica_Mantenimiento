package com.example.pruebaderoom.data

import android.content.Context
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private const val BASE_URL = "http://192.168.1.25:8000/"
    private var instanceService: SitioService? = null
    private var sessionManager: SessionManager? = null

    fun init(context: Context) {
        sessionManager = SessionManager(context)
    }

    private fun getOkHttpClient(): OkHttpClient {
        val authInterceptor = Interceptor { chain ->
            val original = chain.request()
            val token = sessionManager?.getToken()
            
            val requestBuilder = original.newBuilder()
            if (token != null) {
                requestBuilder.header("Authorization", "Bearer $token")
            }
            requestBuilder.header("Accept", "application/json")
            
            chain.proceed(requestBuilder.build())
        }

        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    val instance: SitioService
        get() {
            if (instanceService == null) {
                val retrofit = Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(getOkHttpClient())
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                instanceService = retrofit.create(SitioService::class.java)
            }
            return instanceService!!
        }
}
