package com.example.pruebaderoom.data

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker
import androidx.work.workDataOf
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

            // Reportamos estado inicial
            setProgress(workDataOf("STATUS" to "WAITING", "PROGRESS" to 0))

            // --- PASO 1: Metadatos ---
            val syncRequest = SyncTareaRequest(
                uuid = tarea.uuid,
                sitio_id = tarea.idSitio,
                formulario_id = tarea.idFormulario,
                fecha = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(tarea.fecha),
                tipo_mantenimiento = "MP",
                respuestas = respuestasLocal.map { SyncRespuestaRequest(it.idPregunta, it.texto) }
            )

            Log.d("CRITICAL_DEBUG", "Enviando Paso 1 (Metadatos) al servidor...")
            val responseTarea = api.crearTarea(syncRequest)
            
            if (!responseTarea.isSuccessful) {
                setProgress(workDataOf("STATUS" to "ERROR"))
                Log.e("CRITICAL_DEBUG", "Fallo Paso 1: Código ${responseTarea.code()}")
                return ListenableWorker.Result.retry()
            }

            val body = responseTarea.body() ?: run {
                return ListenableWorker.Result.failure()
            }

            val mapping = body.data
            val tareaIdServidor = mapping.tarea_id
            val mapPreguntaRespuesta = mapping.respuestas.associate { it.pregunta_id to it.respuesta_id }

            // --- PASO 2: Subida de Imágenes ---
            val todasLasImagenes = mutableListOf<Imagen>()
            for (respLocal in respuestasLocal) {
                val imgs = db.imagenDao().getByRespuesta(respLocal.idRespuesta)
                todasLasImagenes.addAll(imgs)
            }

            val totalFotos = todasLasImagenes.size
            var fotosContadas = 0
            var todasOk = true

            for (img in todasLasImagenes) {
                val respLocal = respuestasLocal.find { it.idRespuesta == img.idRespuesta }
                val serverRespuestaId = mapPreguntaRespuesta[respLocal?.idPregunta ?: -1]

                if (serverRespuestaId == null) continue

                if (img.isSynced) {
                    fotosContadas++
                    continue
                }

                val file = File(img.rutaArchivo)
                if (!file.exists()) {
                    fotosContadas++
                    continue
                }

                val exitoFoto = enviarImagenIndividual(tareaIdServidor, serverRespuestaId, img, file, fotosContadas, totalFotos)

                if (exitoFoto) {
                    db.imagenDao().updateSyncStatus(img.idImagen, true)
                    fotosContadas++
                } else {
                    todasOk = false
                    setProgress(workDataOf("STATUS" to "ERROR"))
                    break 
                }
            }

            if (todasOk) {
                setProgress(workDataOf("STATUS" to "SUCCESS", "PROGRESS" to 100))
                limpiarDispositivo(idTareaLocal, respuestasLocal)
                ListenableWorker.Result.success()
            } else {
                ListenableWorker.Result.retry()
            }

        } catch (e: Exception) {
            setProgress(workDataOf("STATUS" to "ERROR"))
            Log.e("CRITICAL_DEBUG", "EXCEPCIÓN EN WORKER: ${e.message}")
            ListenableWorker.Result.retry()
        }
    }

    private suspend fun enviarImagenIndividual(tId: Long, rId: Long, img: Imagen, file: File, index: Int, total: Int): Boolean {
        return try {
            val requestBody = ProgressRequestBody(file, "image/jpeg") { percent ->
                // Usamos setProgressAsync porque estamos dentro de un callback no-suspend
                setProgressAsync(workDataOf(
                    "PROGRESS" to percent,
                    "CURRENT_IMG" to index + 1,
                    "TOTAL_IMG" to total,
                    "STATUS" to "UPLOADING"
                ))
            }

            val bodyImg = MultipartBody.Part.createFormData("imagen", file.name, requestBody)
            val bodyRId = RequestBody.create(MediaType.parse("text/plain"), rId.toString())
            val bodyUuid = RequestBody.create(MediaType.parse("text/plain"), img.uuid)

            val res = api.subirImagen(tId, bodyRId, bodyUuid, bodyImg)
            res.isSuccessful
        } catch (e: Exception) {
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
        } catch (e: Exception) {
            Log.e("CRITICAL_DEBUG", "Error limpieza: ${e.message}")
        }
    }
}
