package com.example.pruebaderoom.data

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker
import androidx.work.workDataOf
import com.example.pruebaderoom.data.entity.Imagen
import com.example.pruebaderoom.data.entity.HistorialEnvio
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
        if (idTareaLocal == -1L) return ListenableWorker.Result.failure()

        return try {
            val tarea = db.tareaDao().getById(idTareaLocal) ?: return ListenableWorker.Result.failure()
            val sitio = db.sitioDao().getById(tarea.idSitio)
            val form = db.formularioDao().getById(tarea.idFormulario)
            val nombreSitio = sitio?.nombre ?: "Sitio"

            val respuestasLocal = db.respuestaDao().getByTarea(idTareaLocal)
            if (respuestasLocal.isEmpty()) return ListenableWorker.Result.success()

            setProgress(workDataOf("STATUS" to "WAITING", "PROGRESS" to 0, "SITIO_NOMBRE" to nombreSitio))

            // PASO 1: Metadatos
            val listaRespuestasSync = respuestasLocal.map { resp ->
                val valores = db.valorRespuestaDao().getByRespuesta(resp.idRespuesta).map { v ->
                    SyncValorRequest(v.idCampo, v.valor)
                }
                SyncRespuestaRequest(resp.idPregunta, valores)
            }

            val syncRequest = SyncTareaRequest(
                uuid = tarea.uuid,
                sitio_id = tarea.idSitio,
                formulario_id = tarea.idFormulario,
                fecha = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(tarea.fecha),
                tipo_mantenimiento = "MP",
                respuestas = listaRespuestasSync
            )

            val responseTarea = api.crearTarea(syncRequest)
            if (!responseTarea.isSuccessful) return ListenableWorker.Result.retry()

            val mapping = responseTarea.body()?.data ?: return ListenableWorker.Result.failure()
            val tareaIdServidor = mapping.tarea_id
            val mapPreguntaRespuesta = mapping.mapa_respuestas.associate { it.pregunta_id to it.respuesta_id }

            // PASO 2: Imágenes
            val todasLasImagenes = mutableListOf<Imagen>()
            for (respLocal in respuestasLocal) {
                todasLasImagenes.addAll(db.imagenDao().getByRespuesta(respLocal.idRespuesta))
            }

            val totalFotos = todasLasImagenes.size
            var fotosContadas = 0
            var todasOk = true

            for (img in todasLasImagenes) {
                val respLocal = respuestasLocal.find { it.idRespuesta == img.idRespuesta }
                val serverRespuestaId = mapPreguntaRespuesta[respLocal?.idPregunta ?: -1]

                if (serverRespuestaId == null || img.isSynced) {
                    fotosContadas++
                    continue
                }

                val file = File(img.rutaArchivo)
                if (file.exists()) {
                    val exitoFoto = enviarImagenIndividual(tareaIdServidor, serverRespuestaId, img, file, fotosContadas, totalFotos, nombreSitio)
                    if (exitoFoto) {
                        db.imagenDao().updateSyncStatus(img.idImagen, true)
                        fotosContadas++
                    } else {
                        todasOk = false
                        break 
                    }
                } else {
                    fotosContadas++
                }
            }

            if (todasOk) {
                // GUARDAR EN HISTORIAL ANTES DE LIMPIAR
                db.historialEnvioDao().insert(HistorialEnvio(
                    sitioNombre = nombreSitio,
                    formularioNombre = form?.nombre ?: "Inspección",
                    fechaEnvio = Date()
                ))

                setProgress(workDataOf("STATUS" to "SUCCESS", "PROGRESS" to 100, "SITIO_NOMBRE" to nombreSitio))
                limpiarDispositivo(idTareaLocal, respuestasLocal)
                ListenableWorker.Result.success()
            } else {
                ListenableWorker.Result.retry()
            }

        } catch (e: Exception) {
            ListenableWorker.Result.retry()
        }
    }

    private suspend fun enviarImagenIndividual(tId: Long, rId: Long, img: Imagen, file: File, index: Int, total: Int, sitio: String): Boolean {
        return try {
            val requestBody = ProgressRequestBody(file, "image/jpeg") { percent ->
                setProgressAsync(workDataOf(
                    "PROGRESS" to percent,
                    "CURRENT_IMG" to index + 1,
                    "TOTAL_IMG" to total,
                    "STATUS" to "UPLOADING",
                    "SITIO_NOMBRE" to sitio
                ))
            }
            val bodyImg = MultipartBody.Part.createFormData("imagen", file.name, requestBody)
            val bodyRId = RequestBody.create(MediaType.parse("text/plain"), rId.toString())
            val bodyUuid = RequestBody.create(MediaType.parse("text/plain"), img.uuid)
            api.subirImagen(tId, bodyRId, bodyUuid, bodyImg).isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun limpiarDispositivo(idTarea: Long, respuestas: List<RespuestaEntity>) {
        respuestas.forEach { r ->
            db.imagenDao().getByRespuesta(r.idRespuesta).forEach { img ->
                val f = File(img.rutaArchivo)
                if (f.exists()) f.delete() 
            }
            db.valorRespuestaDao().deleteByRespuesta(r.idRespuesta)
            db.imagenDao().deleteByRespuesta(r.idRespuesta)
        }
        db.respuestaDao().deleteByTarea(idTarea)
        db.tareaDao().getById(idTarea)?.let { db.tareaDao().delete(it) }
    }
}
