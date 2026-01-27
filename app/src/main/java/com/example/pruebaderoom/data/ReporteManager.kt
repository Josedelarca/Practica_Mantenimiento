package com.example.pruebaderoom.data

import com.google.gson.Gson
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Esta clase es el "corazón" del envío de reportes.
 * Se encarga de transformar toda la información de la inspección en un formato
 * que Laravel entienda perfectamente (Multipart con JSON y archivos reales).
 */
class ReporteManager(private val apiService: SitioService) {

    /**
     * Esta es la estructura del JSON principal que viajará en el campo "data".
     * Usamos nombres con guiones bajos para que coincidan con las columnas de tu base de datos en Laravel.
     */
    data class ReporteJson(
        val sitio_id: Long,
        val formulario_id: Long,
        val fecha: String,
        val tipo_mantenimiento: String,
        val observaciones_generales: String?,
        val respuestas: List<RespuestaJson>
    )

    /**
     * Modelo para cada respuesta individual.
     * El 'temp_id' es clave para que Laravel sepa qué fotos pertenecen a qué pregunta.
     */
    data class RespuestaJson(
        val pregunta_id: Long,
        val temp_id: String,
        val texto_respuesta: String?
    )

    /**
     * Función principal que prepara el paquete y lo envía por internet.
     */
    suspend fun enviarReporteCompleto(
        sitioId: Long,
        formularioId: Long,
        observaciones: String,
        respuestasConFotos: List<Pair<RespuestaJson, List<File>>>
    ): Boolean {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val fechaActual = sdf.format(Date())

            // 1. Preparamos la lista de respuestas (solo el texto y IDs) para el JSON
            val listaRespuestasJson = respuestasConFotos.map { it.first }

            // 2. Creamos el objeto del reporte completo
            val reporte = ReporteJson(
                sitio_id = sitioId,
                formulario_id = formularioId,
                fecha = fechaActual,
                tipo_mantenimiento = "MP",
                observaciones_generales = observaciones,
                respuestas = listaRespuestasJson
            )

            // 3. Convertimos el objeto a un String JSON y lo preparamos como un campo de texto plano
            val jsonString = Gson().toJson(reporte)
            val mediaType = MediaType.parse("text/plain")
            val dataBody = RequestBody.create(mediaType, jsonString)

            // 4. Preparamos las imágenes una por una
            val partesImagenes = mutableListOf<MultipartBody.Part>()
            
            respuestasConFotos.forEach { (respuesta, archivos) ->
                archivos.forEach { archivo ->
                    if (archivo.exists()) {
                        val imageMediaType = MediaType.parse("image/jpeg")
                        val requestFile = RequestBody.create(imageMediaType, archivo)

                        /**
                         * IMPORTANTE: Aquí generamos la "key" que Laravel espera.
                         * Al usar 'imagenes[temp_id][]', Laravel lo recibe como un arreglo asociativo.
                         * Así podrás usar: $request->file('imagenes.resp_1') en tu controlador.
                         */
                        val keyName = "imagenes[${respuesta.temp_id}][]"
                        
                        val part = MultipartBody.Part.createFormData(
                            keyName,
                            archivo.name,
                            requestFile
                        )
                        partesImagenes.add(part)
                    }
                }
            }

            // 5. Enviamos todo (JSON + Fotos) en una sola petición al servidor
            val response = apiService.enviarReporte(dataBody, partesImagenes)
            
            // Si el servidor nos dice que todo OK (código 200), devolvemos true
            response.isSuccessful
        } catch (e: Exception) {
            // Si algo sale mal (ej: no hay internet), imprimimos el error y devolvemos false
            e.printStackTrace()
            false
        }
    }
}
