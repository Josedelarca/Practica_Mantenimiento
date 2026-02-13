package com.example.pruebaderoom.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
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
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val channelId = "sync_channel"
    private val notificationId = 101

    override suspend fun doWork(): ListenableWorker.Result {
        val idTareaLocal = inputData.getLong("ID_TAREA", -1L)
        if (idTareaLocal == -1L) return ListenableWorker.Result.failure()

        crearCanalNotificacion()
        
        // Iniciamos con el estado de red actual
        val initialStatus = if (isNetworkAvailable()) "Preparando envío..." else "Esperando conexión..."
        try {
            setForeground(getForegroundInfoCustom(0, 0, 0, initialStatus))
        } catch (e: Exception) {
            Log.e("ReporteWorker", "Error al establecer Foreground")
        }

        return try {
            val tarea = db.tareaDao().getById(idTareaLocal) ?: return ListenableWorker.Result.failure()
            val sitio = db.sitioDao().getById(tarea.idSitio)
            val form = db.formularioDao().getById(tarea.idFormulario)
            val nombreSitio = sitio?.nombre ?: "Sitio"

            val respuestasLocal = db.respuestaDao().getByTarea(idTareaLocal)
            if (respuestasLocal.isEmpty()) return ListenableWorker.Result.success()

            // Si no hay red al inicio, notificamos y esperamos reintento de WorkManager
            if (!isNetworkAvailable()) {
                updateSyncProgress(0, 0, 0, "Esperando conexión para $nombreSitio...")
                return ListenableWorker.Result.retry()
            }

            updateSyncProgress(0, 0, 0, "Enviando datos de $nombreSitio...")

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
                // Verificar red antes de cada subida
                if (!isNetworkAvailable()) {
                    updateSyncProgress(fotosContadas, totalFotos, 0, "Conexión perdida. Esperando...")
                    return ListenableWorker.Result.retry()
                }

                val respLocal = respuestasLocal.find { it.idRespuesta == img.idRespuesta }
                val serverRespuestaId = mapPreguntaRespuesta[respLocal?.idPregunta ?: -1]

                if (serverRespuestaId == null || img.isSynced) {
                    fotosContadas++
                    continue
                }

                val fileOriginal = File(img.rutaArchivo)
                if (fileOriginal.exists()) {
                    val fileOptimizado = ImageOptimizerHelper.optimize(applicationContext, fileOriginal)
                    val fileParaEnviar = fileOptimizado ?: fileOriginal

                    updateSyncProgress(fotosContadas, totalFotos, 0, "Subiendo foto ${fotosContadas + 1} de $totalFotos")

                    val exitoFoto = enviarImagenIndividual(tareaIdServidor, serverRespuestaId, img, fileParaEnviar, fotosContadas, totalFotos)
                    
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
                showFinalNotification("¡Éxito!", "Reporte de $nombreSitio enviado correctamente.")
                limpiarDispositivo(idTareaLocal, respuestasLocal)
                ListenableWorker.Result.success()
            } else {
                showFinalNotification("Error", "No se pudo completar el envío de $nombreSitio.")
                ListenableWorker.Result.retry()
            }

        } catch (e: Exception) {
            if (!isNetworkAvailable()) {
                updateSyncProgress(0, 0, 0, "Esperando conexión...")
            } else {
                showFinalNotification("Error", "Error inesperado al sincronizar.")
            }
            ListenableWorker.Result.retry()
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        return caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    private fun crearCanalNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Sincronización de Reportes"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(channelId, name, importance)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun getForegroundInfoCustom(current: Int, total: Int, progress: Int, message: String): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Gestión de Inspección")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .setProgress(100, progress, progress == 0 && total > 0)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private suspend fun updateSyncProgress(current: Int, total: Int, progress: Int, message: String) {
        val status = if (message.contains("Esperando")) "WAITING" else "UPLOADING"
        setProgress(workDataOf(
            "PROGRESS" to progress,
            "CURRENT_IMG" to current + 1,
            "TOTAL_IMG" to total,
            "STATUS" to status
        ))
        notificationManager.notify(notificationId, getForegroundInfoCustom(current, total, progress, message).notification)
    }

    private fun showFinalNotification(title: String, message: String) {
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(notificationId + 1, notification)
    }

    private suspend fun enviarImagenIndividual(tId: Long, rId: Long, img: Imagen, file: File, index: Int, total: Int): Boolean {
        return try {
            val requestBody = ProgressRequestBody(file, "image/jpeg") { percent ->
                val notification = NotificationCompat.Builder(applicationContext, channelId)
                    .setContentTitle("Enviando evidencia")
                    .setContentText("Subiendo foto ${index + 1} de $total ($percent%)")
                    .setSmallIcon(android.R.drawable.stat_sys_upload)
                    .setOngoing(true)
                    .setProgress(100, percent, false)
                    .build()
                notificationManager.notify(notificationId, notification)
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

object ImageOptimizerHelper {
    fun optimize(context: Context, originalFile: File): File? {
        return try {
            val maxSide = 1600
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(originalFile.absolutePath, options)
            
            options.inSampleSize = calculateInSampleSize(options.outWidth, options.outHeight, maxSide)
            options.inJustDecodeBounds = false
            
            var bitmap = BitmapFactory.decodeFile(originalFile.absolutePath, options) ?: return null
            
            if (bitmap.width > maxSide || bitmap.height > maxSide) {
                val scale = maxSide.toFloat() / Math.max(bitmap.width, bitmap.height)
                val matrix = Matrix().apply { postScale(scale, scale) }
                val scaledBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (scaledBitmap != bitmap) bitmap.recycle()
                bitmap = scaledBitmap
            }
            
            bitmap = rotateIfRequired(bitmap, originalFile.absolutePath)
            
            val tempFile = File(context.cacheDir, "OPT_${UUID.randomUUID()}.jpg")
            FileOutputStream(tempFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }
            
            bitmap.recycle()
            tempFile
        } catch (e: Exception) {
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
        val matrix = Matrix().apply { postRotate(angle) }
        val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        bitmap.recycle()
        return rotatedBitmap
    }
}
