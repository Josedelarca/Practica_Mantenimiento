package com.example.pruebaderoom.data

import com.example.pruebaderoom.data.entity.Sitio
import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface SitioService {
    
    @GET("api/sitios")
    suspend fun getSitios(): SitioResponse

    @GET("api/formularios/{id}")
    suspend fun getFormularioCompleto(@Path("id") id: Long): FormularioFullResponse

    // FASE 1: Crear Tarea y obtener mapeo de IDs (Incluye UUID para Idempotencia)
    @POST("api/tareas")
    suspend fun crearTarea(@Body data: SyncTareaRequest): Response<SyncTareaResponse>

    // FASE 2: Subir imagen individual
    @Multipart
    @POST("api/tareas/{tarea_id}/imagenes")
    suspend fun subirImagen(
        @Path("tarea_id") tareaId: Long,
        @Part("respuesta_id") respuestaId: RequestBody,
        @Part("uuid") uuid: RequestBody,
        @Part imagen: MultipartBody.Part
    ): Response<Unit>

    @Multipart
    @POST("api/tareas")
    suspend fun enviarReporte(
        @Part("data") data: RequestBody,
        @Part imagenes: List<MultipartBody.Part>
    ): Response<Unit>
}

// Data Classes para el flujo de sincronización de 2 pasos
data class SyncTareaRequest(
    val uuid: String, // <--- NUEVO Y OBLIGATORIO PARA IDEMPOTENCIA
    val sitio_id: Long,
    val formulario_id: Long,
    val fecha: String,
    val tipo_mantenimiento: String,
    val respuestas: List<SyncRespuestaRequest>
)

data class SyncRespuestaRequest(
    val pregunta_id: Long,
    val texto_respuesta: String?
)

data class SyncTareaResponse(
    val success: Boolean,
    val data: TareaMappingData
)

data class TareaMappingData(
    val tarea_id: Long,
    val respuestas: List<RespuestaMapping>
)

data class RespuestaMapping(
    val pregunta_id: Long,
    val respuesta_id: Long // ID del servidor
)

data class SitioResponse(
    @SerializedName("data")
    val data: List<Sitio>
)
