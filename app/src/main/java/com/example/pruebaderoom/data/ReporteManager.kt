package com.example.pruebaderoom.data

import com.google.gson.Gson
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Nota: El ReporteManager ahora se integra con el flujo de Pregunta Compuesta.
 * Aunque el envío masivo se mantiene, se recomienda usar el ReporteWorker 
 * para aprovechar la idempotencia con UUID y la subida segmentada de fotos.
 */
class ReporteManager(private val apiService: SitioService) {

    data class ReporteJson(
        val uuid: String,
        val sitio_id: Long,
        val formulario_id: Long,
        val fecha: String,
        val tipo_mantenimiento: String,
        val respuestas: List<SyncRespuestaRequest>
    )

    /**
     * Función para envío manual (No recomendado para inspecciones grandes).
     */
    suspend fun enviarReporteCompleto(
        sitioId: Long,
        formularioId: Long,
        observaciones: String,
        respuestasConFotos: List<Pair<SyncRespuestaRequest, List<File>>>
    ): Boolean {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val reporte = ReporteJson(
                uuid = UUID.randomUUID().toString(),
                sitio_id = sitioId,
                formulario_id = formularioId,
                fecha = sdf.format(Date()),
                tipo_mantenimiento = "MP",
                respuestas = respuestasConFotos.map { it.first }
            )

            val jsonString = Gson().toJson(reporte)
            val dataBody = RequestBody.create(MediaType.parse("text/plain"), jsonString)

            val partesImagenes = mutableListOf<MultipartBody.Part>()
            respuestasConFotos.forEach { (_, archivos) ->
                archivos.forEach { archivo ->
                    if (archivo.exists()) {
                        val requestFile = RequestBody.create(MediaType.parse("image/jpeg"), archivo)
                        val part = MultipartBody.Part.createFormData("imagenes[]", archivo.name, requestFile)
                        partesImagenes.add(part)
                    }
                }
            }

            val response = apiService.enviarReporte(dataBody, partesImagenes)
            response.isSuccessful
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
