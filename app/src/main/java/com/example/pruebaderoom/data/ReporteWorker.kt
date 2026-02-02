package com.example.pruebaderoom.data

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker
import com.example.pruebaderoom.data.entity.Imagen
import com.example.pruebaderoom.data.entity.Respuesta as RespuestaEntity
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ReporteWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val db = AppDatabase.getDatabase(applicationContext)
    private val api = RetrofitClient.instance

    override suspend fun doWork(): ListenableWorker.Result {
        val idTareaLocal = inputData.getLong("ID_TAREA", -1L)
        Log.i("CRITICAL_DEBUG", ">>> [WORKER START] Tarea Local ID: $idTareaLocal")

        if (idTareaLocal == -1L) {
            Log.e("CRITICAL_DEBUG", "ID de tarea inválido recibido en el Worker")
            return ListenableWorker.Result.failure()
        }

        return try {
            val tarea = db.tareaDao().getById(idTareaLocal) 
            if (tarea == null) {
                Log.e("CRITICAL_DEBUG", "No se encontró la tarea $idTareaLocal en la DB")
                return ListenableWorker.Result.failure()
            }

            val respuestasLocal = db.respuestaDao().getByTarea(idTareaLocal)
            Log.i("CRITICAL_DEBUG", "Respuestas locales encontradas: ${respuestasLocal.size}")

            if (respuestasLocal.isEmpty()) {
                Log.w("CRITICAL_DEBUG", "La tarea no tiene respuestas asociadas. Nada que sincronizar.")
                return ListenableWorker.Result.success()
            }

            // --- PASO 1: Metadatos ---
            val syncRequest = SyncTareaRequest(
                sitio_id = tarea.idSitio,
                formulario_id = tarea.idFormulario,
                fecha = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(tarea.fecha),
                tipo_mantenimiento = "MP",
                respuestas = respuestasLocal.map { SyncRespuestaRequest(it.idPregunta, it.texto) }
            )

            Log.d("CRITICAL_DEBUG", "Enviando Paso 1 (Metadatos) al servidor...")
            val responseTarea = api.crearTarea(syncRequest)
            
            if (!responseTarea.isSuccessful) {
                Log.e("CRITICAL_DEBUG", "Fallo Paso 1: Código ${responseTarea.code()} - ${responseTarea.errorBody()?.string()}")
                return ListenableWorker.Result.retry()
            }

            val body = responseTarea.body() ?: run {
                Log.e("CRITICAL_DEBUG", "El servidor respondió con éxito pero el body está vacío")
                return ListenableWorker.Result.failure()
            }

            val mapping = body.data
            val tareaIdServidor = mapping.tarea_id
            
            // Lógica de mapeo: pregunta_id -> respuesta_id_servidor
            val mapPreguntaRespuesta = mapping.respuestas.associate { it.pregunta_id to it.respuesta_id }
            Log.i("CRITICAL_DEBUG", "Paso 1 EXITOSO. Tarea Server ID: $tareaIdServidor. Mapeo: $mapPreguntaRespuesta")

            // --- PASO 2: Subida de Imágenes ---
            var todasOk = true
            var fotosContadas = 0

            for (respLocal in respuestasLocal) {
                val serverRespuestaId = mapPreguntaRespuesta[respLocal.idPregunta]
                
                if (serverRespuestaId == null) {
                    Log.e("CRITICAL_DEBUG", "ERROR MAPEO: Pregunta Local ${respLocal.idPregunta} no existe en el mapping del servidor.")
                    continue
                }

                val imagenes = db.imagenDao().getByRespuesta(respLocal.idRespuesta)
                Log.d("CRITICAL_DEBUG", "Pregunta ${respLocal.idPregunta} (Server ID: $serverRespuestaId) tiene ${imagenes.size} fotos.")

                for (img in imagenes) {
                    if (img.isSynced) {
                        Log.d("CRITICAL_DEBUG", "Foto ${img.uuid} ya estaba sincronizada. Saltando.")
                        continue
                    }

                    val file = File(img.rutaArchivo)
                    if (!file.exists()) {
                        Log.e("CRITICAL_DEBUG", "ARCHIVO NO ENCONTRADO: ${img.rutaArchivo}")
                        continue
                    }

                    Log.i("CRITICAL_DEBUG", "Iniciando envío de imagen: ${file.name}")
                    val exitoFoto = enviarImagenIndividual(tareaIdServidor, serverRespuestaId, img, file)

                    if (exitoFoto) {
                        db.imagenDao().updateSyncStatus(img.idImagen, true)
                        fotosContadas++
                    } else {
                        todasOk = false
                        Log.e("CRITICAL_DEBUG", "Error al subir la imagen ${img.uuid}")
                    }
                }
            }

            if (todasOk) {
                Log.i("CRITICAL_DEBUG", ">>> SINCRO TOTAL EXITOSA ($fotosContadas fotos). Limpiando...")
                limpiarDispositivo(idTareaLocal, respuestasLocal)
                ListenableWorker.Result.success()
            } else {
                Log.w("CRITICAL_DEBUG", "Algunas fotos fallaron. El Worker se reintentará.")
                ListenableWorker.Result.retry()
            }

        } catch (e: Exception) {
            Log.e("CRITICAL_DEBUG", "EXCEPCIÓN EN WORKER: ${e.message}")
            e.printStackTrace()
            ListenableWorker.Result.retry()
        }
    }

    private suspend fun enviarImagenIndividual(tId: Long, rId: Long, img: Imagen, file: File): Boolean {
        return try {
            val reqFile = RequestBody.create(MediaType.parse("image/jpeg"), file)
            val bodyImg = MultipartBody.Part.createFormData("imagen", file.name, reqFile)

            // Usamos RequestBody.create(MediaType.parse("text/plain"), ...) para asegurar compatibilidad
            val bodyRId = RequestBody.create(MediaType.parse("text/plain"), rId.toString())
            val bodyUuid = RequestBody.create(MediaType.parse("text/plain"), img.uuid)

            Log.d("CRITICAL_DEBUG", "Request URL: api/tareas/$tId/imagenes | Part r_id: $rId | UUID: ${img.uuid}")

            val res = api.subirImagen(tId, bodyRId, bodyUuid, bodyImg)
            
            if (res.isSuccessful) {
                Log.i("CRITICAL_DEBUG", "Servidor aceptó la imagen: ${img.uuid}")
                true
            } else {
                val errorStr = res.errorBody()?.string()
                Log.e("CRITICAL_DEBUG", "SERVIDOR RECHAZÓ FOTO: ${res.code()} - $errorStr")
                false
            }
        } catch (e: Exception) {
            Log.e("CRITICAL_DEBUG", "ERROR DE RED (Retrofit): ${e.message}")
            false
        }
    }

    private suspend fun limpiarDispositivo(idTarea: Long, respuestas: List<RespuestaEntity>) {
        try {
            respuestas.forEach { r ->
                val fotos = db.imagenDao().getByRespuesta(r.idRespuesta)
                fotos.forEach { 
                    val f = File(it.rutaArchivo)
                    if (f.exists()) f.delete() 
                }
                db.imagenDao().deleteByRespuesta(r.idRespuesta)
            }
            db.respuestaDao().deleteByTarea(idTarea)
            db.tareaDao().getById(idTarea)?.let { db.tareaDao().delete(it) }
            Log.d("CRITICAL_DEBUG", "Limpieza de base de datos local completada.")
        } catch (e: Exception) {
            Log.e("CRITICAL_DEBUG", "Error durante la limpieza: ${e.message}")
        }
    }
}
