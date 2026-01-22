package com.example.pruebaderoom.data

import com.example.pruebaderoom.data.entity.Sitio
import com.google.gson.annotations.SerializedName
import retrofit2.http.GET

interface SitioService {
    // CAMBIADO: Ahora devuelve SitioResponse para que Laravel funcione
    @GET("api/sitios")
    suspend fun getSitios(): SitioResponse
}

// SOLO UNA VEZ: Definición de la respuesta
data class SitioResponse(
    @SerializedName("data")
    val data: List<Sitio>
)
