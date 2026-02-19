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
import com.example.pruebaderoom.data.entity.EstadoTarea
import com.example.pruebaderoom.data.entity.Respuesta as RespuestaEntity
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
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
        val zonaActual = inputData.getString("ZONA_TRABAJADA") ?: "ambos"
        
        if (idTareaLocal == -1L) return ListenableWorker.Result.failure()

        crearCanalNotificacion()
        
        try {
            setForeground(getForegroundInfoCustom(0, 0, 0, "Preparando sincronización..."))
        } catch (e: Exception) {
            Log.e("ReporteWorker", "Error al establecer Foreground")
        }

        return try {
            val tarea = db.tareaDao().getById(idTareaLocal) ?: return ListenableWorker.Result.failure()
            val sitio = db.sitioDao().getById(tarea.idSitio)
            val form = db.formularioDao().getById(tarea.idFormulario)
            val nombreSitio = sitio?.nombre ?: "Sitio"

            val todasLasSecciones = db.seccionDao().getByFormulario(tarea.idFormulario)
            val seccionesDeEstaZona = todasLasSecciones.filter { it.zona == zonaActual || it.zona == "ambos" }
            
            val respuestasLocal = db.respuestaDao().getByTarea(idTareaLocal)
            val respuestasAEnviar = respuestasLocal.filter { resp ->
                val preg = db.preguntaDao().getById(resp.idPregunta)
                seccionesDeEstaZona.any { it.idSeccion == preg?.idSeccion }
            }

            if (!isNetworkAvailable()) {
                resetEstadoEnviando(idTareaLocal, zonaActual)
                return ListenableWorker.Result.retry()
            }

            // PASO 1: Registro Inicial (Obtener IDs del servidor)
            updateSyncProgress(idTareaLocal, zonaActual, -1, 10, "Registrando respuestas...")
            
            val listaRespuestasSync = respuestasAEnviar.map { resp ->
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
                respuestas = listaRespuestasSync,
                secciones_completadas = emptyList() 
            )

            val responseTarea = api.crearTarea(syncRequest)
            if (!responseTarea.isSuccessful) {
                resetEstadoEnviando(idTareaLocal, zonaActual)
                return ListenableWorker.Result.retry()
            }

            val mapping = responseTarea.body()?.data ?: return ListenableWorker.Result.failure()
            val tareaIdServidor = mapping.tarea_id
            val mapPreguntaRespuesta = mapping.mapa_respuestas.associate { it.pregunta_id to it.respuesta_id }

            // PASO 2: Subida de Imágenes
            val seccionesCompletadasOk = mutableListOf<Long>()
            var huboFalloEnFotos = false

            for (seccion in seccionesDeEstaZona) {
                var seccionTotalmenteSubida = true
                val preguntasDeSeccion = db.preguntaDao().getBySeccion(seccion.idSeccion)
                
                // Contar total de imágenes de esta sección para el mensaje de progreso
                val imagenesDeSeccion = mutableListOf<Imagen>()
                for (preg in preguntasDeSeccion) {
                    val resp = respuestasAEnviar.find { it.idPregunta == preg.idPregunta }
                    if (resp != null) {
                        imagenesDeSeccion.addAll(db.imagenDao().getByRespuesta(resp.idRespuesta))
                    }
                }
                
                val totalImgs = imagenesDeSeccion.size
                var imgsContadas = 0

                for (img in imagenesDeSeccion) {
                    imgsContadas++
                    if (img.isSynced) continue
                    
                    val file = File(img.rutaArchivo)
                    if (file.exists()) {
                        val optimized = ImageOptimizerHelper.optimize(applicationContext, file)
                        
                        // NOTIFICAR PROGRESO FOTO POR FOTO
                        updateSyncProgress(idTareaLocal, zonaActual, seccion.idSeccion, 50, 
                            "Subiendo foto $imgsContadas de $totalImgs...")
                        
                        // Buscar ID de pregunta para esta imagen
                        val pregId = db.respuestaDao().getById(img.idRespuesta)?.idPregunta ?: -1L
                        val serverRespuestaId = mapPreguntaRespuesta[pregId]
                        
                        if (serverRespuestaId != null) {
                            val response = enviarImagenIndividual(tareaIdServidor, serverRespuestaId, img, optimized ?: file)
                            val httpCode = response.code()
                            val bodyText = response.body()?.toString() ?: response.errorBody()?.string() ?: ""
                            
                            optimized?.delete()

                            if (httpCode == 201 || (httpCode == 200 && bodyText.contains("Imagen ya existía", true))) {
                                db.imagenDao().updateSyncStatus(img.idImagen, true)
                            } else {
                                seccionTotalmenteSubida = false
                                huboFalloEnFotos = true
                            }
                        } else {
                            seccionTotalmenteSubida = false
                        }
                    }
                }

                if (seccionTotalmenteSubida) {
                    seccionesCompletadasOk.add(seccion.idSeccion)
                }
            }

            // PASO 3: Confirmación Final de Secciones
            if (seccionesCompletadasOk.isNotEmpty()) {
                updateSyncProgress(idTareaLocal, zonaActual, -1, 90, "Finalizando...")
                val updateSectionsRequest = syncRequest.copy(
                    respuestas = emptyList(),
                    secciones_completadas = seccionesCompletadasOk
                )
                val finalResponse = api.crearTarea(updateSectionsRequest)
                
                if (finalResponse.isSuccessful) {
                    // Marcamos como completadas permanentemente
                    seccionesCompletadasOk.forEach { id ->
                        db.seccionDao().updateCompletada(id, true)
                        db.seccionDao().updateEnviando(id, false)
                    }
                    
                    if (finalResponse.body()?.data?.estado_actual == "sincronizado") {
                        limpiarTareaCompleta(idTareaLocal)
                    }
                }
            }

            // Limpiamos el estado "enviando" de las que fallaron para que el usuario pueda reintentar
            seccionesDeEstaZona.filter { !seccionesCompletadasOk.contains(it.idSeccion) }.forEach {
                db.seccionDao().updateEnviando(it.idSeccion, false)
            }
            
            // Volver la tarea a EN_PROCESO si no se cerró globalmente
            val checkTarea = db.tareaDao().getById(idTareaLocal)
            if (checkTarea != null) {
                db.tareaDao().insert(checkTarea.copy(estado = EstadoTarea.EN_PROCESO))
            }

            if (huboFalloEnFotos) ListenableWorker.Result.retry() else ListenableWorker.Result.success()

        } catch (e: Exception) {
            Log.e("SYNC_ERROR", "Error: ${e.message}")
            resetEstadoEnviando(idTareaLocal, zonaActual)
            ListenableWorker.Result.retry()
        }
    }

    private suspend fun resetEstadoEnviando(tareaId: Long, zona: String) {
        val tarea = db.tareaDao().getById(tareaId)
        tarea?.let {
            db.seccionDao().updateEnviandoPorZona(it.idFormulario, zona, false)
            db.tareaDao().insert(it.copy(estado = EstadoTarea.EN_PROCESO))
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        return caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    private fun crearCanalNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Sincronización", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun getForegroundInfoCustom(current: Int, total: Int, progress: Int, message: String): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Sincronizando Reporte")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .setProgress(100, progress, total > 0 && progress == 0)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private suspend fun updateSyncProgress(tareaId: Long, zona: String, seccionId: Long, progress: Int, message: String) {
        setProgress(workDataOf(
            "PROGRESS" to progress, 
            "STATUS" to "UPLOADING",
            "TAREA_ID" to tareaId,
            "ZONA_TRABAJADA" to zona,
            "SECCION_ID" to seccionId,
            "MESSAGE" to message
        ))
        notificationManager.notify(notificationId, getForegroundInfoCustom(0, 0, progress, message).notification)
    }

    private suspend fun enviarImagenIndividual(tId: Long, rId: Long, img: Imagen, file: File): Response<ImageUploadResponse> {
        val requestBody = ProgressRequestBody(file, "image/jpeg") { /* progress */ }
        val bodyImg = MultipartBody.Part.createFormData("imagen", file.name, requestBody)
        val bodyRId = RequestBody.create(MediaType.parse("text/plain"), rId.toString())
        val bodyUuid = RequestBody.create(MediaType.parse("text/plain"), img.uuid)
        return api.subirImagen(tId, bodyRId, bodyUuid, bodyImg)
    }

    private suspend fun limpiarTareaCompleta(idTarea: Long) {
        val respuestas = db.respuestaDao().getByTarea(idTarea)
        respuestas.forEach { r ->
            db.imagenDao().getByRespuesta(r.idRespuesta).forEach { img ->
                File(img.rutaArchivo).delete()
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
            FileOutputStream(tempFile).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out) }
            bitmap.recycle()
            tempFile
        } catch (e: Exception) { null }
    }
    private fun calculateInSampleSize(width: Int, height: Int, maxSide: Int): Int {
        var inSampleSize = 1
        if (width > maxSide || height > maxSide) {
            val halfWidth = width / 2
            val halfHeight = height / 2
            while (halfWidth / inSampleSize >= maxSide && halfHeight / inSampleSize >= maxSide) { inSampleSize *= 2 }
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
