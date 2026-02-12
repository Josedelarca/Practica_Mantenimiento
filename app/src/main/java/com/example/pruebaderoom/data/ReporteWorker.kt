package com.example.pruebaderoom.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
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
import java.io.FileOutputStream
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

                val fileOriginal = File(img.rutaArchivo)
                if (fileOriginal.exists()) {
                    // OPTIMIZACIÓN ANTES DE ENVIAR
                    val fileOptimizado = ImageOptimizer.optimize(applicationContext, fileOriginal)
                    val fileParaEnviar = fileOptimizado ?: fileOriginal

                    val exitoFoto = enviarImagenIndividual(tareaIdServidor, serverRespuestaId, img, fileParaEnviar, fotosContadas, totalFotos, nombreSitio)
                    
                    // Borrar el temporal optimizado si se creó
                    if (fileOptimizado != null && fileOptimizado.exists()) {
                        fileOptimizado.delete()
                    }

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

    object ImageOptimizer {
        fun optimize(context: Context, originalFile: File): File? {
            return try {
                val maxSide = 1600
                
                // 1. Obtener dimensiones sin cargar en memoria
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(originalFile.absolutePath, options)
                
                val originalWidth = options.outWidth
                val originalHeight = options.outHeight
                
                if (originalWidth <= 0 || originalHeight <= 0) return null

                // 2. Calcular inSampleSize para decodificación eficiente
                options.inSampleSize = calculateInSampleSize(originalWidth, originalHeight, maxSide)
                options.inJustDecodeBounds = false
                
                // 3. Decodificar imagen (ya reducida por inSampleSize)
                var bitmap = BitmapFactory.decodeFile(originalFile.absolutePath, options) ?: return null
                
                // 4. Redimensionar exactamente si supera los 1600px
                if (bitmap.width > maxSide || bitmap.height > maxSide) {
                    bitmap = scaleBitmap(bitmap, maxSide)
                }
                
                // 5. Corregir rotación EXIF
                bitmap = rotateIfRequired(bitmap, originalFile.absolutePath)
                
                // 6. Guardar en archivo temporal JPEG con calidad 80%
                val tempFile = File(context.cacheDir, "OPT_${UUID.randomUUID()}.jpg")
                FileOutputStream(tempFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                }
                
                bitmap.recycle()
                tempFile
            } catch (e: Exception) {
                Log.e("ImageOptimizer", "Error optimizando imagen: ${e.message}")
                null
            }
        }

        private fun calculateInSampleSize(width: Int, height: Int, maxSide: Int): Int {
            var inSampleSize = 1
            if (width > maxSide || height > maxSide) {
                val halfWidth = width / 2
                val halfHeight = height / 2
                while (halfWidth / inSampleSize >= maxSide && halfHeight / inSampleSize >= maxSide) {
                    inSampleSize *= 2
                }
            }
            return inSampleSize
        }

        private fun scaleBitmap(bitmap: Bitmap, maxSide: Int): Bitmap {
            val width = bitmap.width
            val height = bitmap.height
            val scale = maxSide.toFloat() / Math.max(width, height)
            
            val matrix = Matrix()
            matrix.postScale(scale, scale)
            
            val scaledBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)
            if (scaledBitmap != bitmap) bitmap.recycle()
            return scaledBitmap
        }

        private fun rotateIfRequired(bitmap: Bitmap, path: String): Bitmap {
            val exif = ExifInterface(path)
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            
            val angle = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            
            if (angle == 0f) return bitmap
            
            val matrix = Matrix()
            matrix.postRotate(angle)
            val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            bitmap.recycle()
            return rotatedBitmap
        }
    }
}
