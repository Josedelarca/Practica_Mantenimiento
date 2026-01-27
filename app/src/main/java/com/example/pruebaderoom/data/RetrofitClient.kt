package com.example.pruebaderoom.data

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Este objeto es el "mensajero" que se encarga de hablar con el servidor en internet.
 * Aquí configuramos la dirección (URL) de nuestra API.
 */
object RetrofitClient {
    // La dirección IP del servidor donde está la base de datos central
    private const val BASE_URL = "http://192.168.1.25:8000/"

    val instance: SitioService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create()) // Traduce los datos de la web a código Kotlin
            .build()
        retrofit.create(SitioService::class.java)
    }
}
