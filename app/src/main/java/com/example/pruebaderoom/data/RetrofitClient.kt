package com.example.pruebaderoom.data

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    // IMPORTANTE: Asegúrate de correr Laravel con: php artisan serve --host=0.0.0.0
    private const val BASE_URL = "http://192.168.1.25:8000/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS) // Aumentamos a 20s
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true) // Reintento automático
        .build()

    val instance: SitioService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        retrofit.create(SitioService::class.java)
    }
}
