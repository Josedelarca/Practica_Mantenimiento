package com.example.pruebaderoom.data

import com.example.pruebaderoom.data.entity.Sitio
import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

/**
 * Esta interfaz define las "preguntas" que le podemos hacer al servidor.
 */
interface SitioService {
    
    @GET("api/sitios")
    suspend fun getSitios(): SitioResponse

    @GET("api/formularios/{id}")
    suspend fun getFormularioCompleto(@Path("id") id: Long): FormularioFullResponse

    /**
     * Envía el reporte completo al servidor Laravel.
     * 1. Un campo "data" con el JSON de la inspección.
     * 2. Múltiples partes para las imágenes usando el formato imagenes_{temp_id}[]
     */
    @Multipart
    @POST("api/tareas")
    suspend fun enviarReporte(
        @Part("data") data: RequestBody,
        @Part imagenes: List<MultipartBody.Part>
    ): Response<Unit>
}

data class SitioResponse(
    @SerializedName("data")
    val data: List<Sitio>
)
