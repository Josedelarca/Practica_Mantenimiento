package com.example.pruebaderoom.data

import com.example.pruebaderoom.data.entity.Sitio
import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Path

interface SitioService {
    @GET("api/sitios")
    suspend fun getSitios(): SitioResponse

    @GET("api/formularios/{id}")
    suspend fun getFormularioCompleto(@Path("id") id: Long): FormularioFullResponse
}

data class SitioResponse(
    @SerializedName("data")
    val data: List<Sitio>
)
