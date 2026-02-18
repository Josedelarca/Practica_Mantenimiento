package com.example.pruebaderoom.data

import com.example.pruebaderoom.data.entity.Sitio
import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface SitioService {
    
    @POST("api/login")
    suspend fun login(@Body credentials: LoginRequest): Response<LoginResponse>

    @GET("api/tareas/pendientes")
    suspend fun getTareasPendientes(): TareasPendientesResponse

    @GET("api/sitios")
    suspend fun getSitios(): SitioResponse

    @GET("api/formularios")
    suspend fun getListaFormularios(): FormularioListResponse

    @GET("api/formularios/{id}")
    suspend fun getFormularioCompleto(@Path("id") id: Long): FormularioFullResponse

    @POST("api/tareas")
    suspend fun crearTarea(@Body data: SyncTareaRequest): Response<SyncTareaResponse>

    @Multipart
    @POST("api/tareas/{tarea_id}/imagenes")
    suspend fun subirImagen(
        @Path("tarea_id") tareaId: Long,
        @Part("respuesta_id") respuestaId: RequestBody,
        @Part("uuid") uuid: RequestBody,
        @Part imagen: MultipartBody.Part
    ): Response<ImageUploadResponse>

    @Multipart
    @POST("api/tareas/bulk")
    suspend fun enviarReporte(
        @Part("data") data: RequestBody,
        @Part imagenes: List<MultipartBody.Part>
    ): Response<Unit>
}

// --- DATA CLASSES PARA IMAGENES ---
data class ImageUploadResponse(
    val success: Boolean?,
    val mensaje: String?,
    val data: Any?
)

// --- DATA CLASSES PARA TAREAS ASIGNADAS ---
data class TareasPendientesResponse(
    val success: Boolean,
    val data: List<TareaAsignadaApi>
)

data class TareaAsignadaApi(
    val id: Long,
    val uuid: String,
    val fecha: String,
    val tipo_mantenimiento: String,
    val sitio: Sitio,
    val formulario: FormularioApiData,
    val secciones_completadas: List<Long>
)

// --- LOGIN DATA CLASSES ---
data class LoginRequest(
    val email: String,
    val password: String
)

data class LoginResponse(
    val success: Boolean,
    val data: LoginData
)

data class LoginData(
    val token: String,
    val user: UserData?
)

data class UserData(
    val id: Long,
    val name: String,
    val email: String
)

// --- DATA CLASSES PARA MÚLTIPLES FORMULARIOS ---

data class FormularioListResponse(
    @SerializedName("data")
    val data: List<FormularioApiShort>
)

data class FormularioApiShort(
    val id: Long,
    val nombre: String,
    val descripcion: String
)

// --- DATA CLASSES PARA SINCRONIZACIÓN ---

data class SyncTareaRequest(
    val uuid: String,
    val sitio_id: Long,
    val formulario_id: Long,
    val fecha: String,
    val tipo_mantenimiento: String,
    val respuestas: List<SyncRespuestaRequest>,
    val secciones_completadas: List<Long> = emptyList()
)

data class SyncRespuestaRequest(
    val pregunta_id: Long,
    val valores: List<SyncValorRequest>
)

data class SyncValorRequest(
    val campo_id: Long,
    val valor: String
)

data class SyncTareaResponse(
    val success: Boolean,
    val data: TareaMappingData
)

data class TareaMappingData(
    val tarea_id: Long,
    @SerializedName("estado_actual") val estado_actual: String?,
    val mapa_respuestas: List<RespuestaMapping>
)

data class RespuestaMapping(
    val pregunta_id: Long,
    val respuesta_id: Long
)

data class SitioResponse(
    @SerializedName("data")
    val data: List<Sitio>
)
