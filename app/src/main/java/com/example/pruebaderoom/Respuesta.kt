package com.example.pruebaderoom

import android.app.Dialog
import android.content.Intent
import android.graphics.*
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.example.pruebaderoom.data.AppDatabase
import com.example.pruebaderoom.data.entity.Imagen
import com.example.pruebaderoom.data.entity.Respuesta as RespuestaEntity
import com.example.pruebaderoom.data.entity.Sitio
import com.example.pruebaderoom.data.entity.Pregunta
import com.example.pruebaderoom.data.entity.Seccion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Esta pantalla se encarga de lo más importante: la captura de fotos.
 * Aquí el usuario toma la evidencia, se le pone una marca de agua (GPS, fecha, sitio)
 * y se asegura de que cumpla con el mínimo de fotos antes de guardar.
 */
data class FotoEvidencia(val bitmap: Bitmap, var rutaExistente: String? = null)

class Respuesta : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private val listaFotos = mutableListOf<FotoEvidencia>() 
    private lateinit var rvFotos: RecyclerView
    private lateinit var adapter: FotoAdapter
    
    private lateinit var txtSeccion: TextView
    private lateinit var txtPregunta: TextView
    private lateinit var txtRequisitos: TextView
    
    private var idPreguntaRecibido: Long = -1
    private var idTareaRecibido: Long = -1
    private var idRespuestaActual: Long = -1 
    private var minFotosRequeridas: Int = 0
    private var maxFotosPermitidas: Int = 0
    private var sitioActual: Sitio? = null

    private var photoFile: File? = null
    private var photoUri: Uri? = null

    // Lanzador para abrir la cámara del celular
    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && photoFile != null) {
            val rawBitmap = BitmapFactory.decodeFile(photoFile!!.absolutePath)
            rawBitmap?.let { bitmap ->
                // Corregimos la orientación porque a veces salen acostadas
                val bitmapDerecho = corregirRotacion(photoFile!!.absolutePath, bitmap)
                if (listaFotos.size < maxFotosPermitidas) {
                    // Le ponemos los datos de GPS y Fecha directamente a la imagen
                    val bitmapConMarca = agregarMarcaDeAgua(bitmapDerecho)
                    // Guardamos el archivo final en la memoria del celular
                    guardarFotoAlInstante(bitmapConMarca)
                    // Borramos la foto temporal sin marca de agua
                    if (photoFile!!.exists()) photoFile!!.delete()
                } else {
                    Toast.makeText(this, "Ya no caben más fotos", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_respuesta)

        db = AppDatabase.getDatabase(this)
        txtSeccion = findViewById(R.id.txtSeccionActual)
        txtPregunta = findViewById(R.id.txtPreguntaActual)
        txtRequisitos = findViewById(R.id.txtRequisitosFotos)
        rvFotos = findViewById(R.id.rvFotos)
        
        idPreguntaRecibido = intent.getLongExtra("ID_PREGUNTA", -1)
        idTareaRecibido = intent.getLongExtra("ID_TAREA", -1)

        // Cargamos la pregunta y si ya había fotos tomadas antes, las mostramos
        cargarDatosExistentesYsitio()

        adapter = FotoAdapter(
            listaFotos, 
            onEliminar = { posicion -> eliminarFotoFisicamente(posicion) },
            onVerPrevia = { foto -> mostrarVistaPrevia(foto.bitmap) }
        )
        rvFotos.adapter = adapter

        // Botón para tomar una nueva foto
        findViewById<Button>(R.id.btnSubir).setOnClickListener {
            if (listaFotos.size >= maxFotosPermitidas) {
                Toast.makeText(this, "Límite de fotos alcanzado", Toast.LENGTH_SHORT).show()
            } else {
                lanzarCamaraSegura()
            }
        }

        // Botón "GUARDAR TODO" para terminar esta pregunta
        findViewById<Button>(R.id.btnEnviar).setOnClickListener {
            if (listaFotos.size < minFotosRequeridas) {
                Toast.makeText(this, "Debes tomar al menos $minFotosRequeridas fotos", Toast.LENGTH_SHORT).show()
            } else {
                marcarComoFinalizado()
            }
        }

        findViewById<Button>(R.id.btnVolver).setOnClickListener { finish() }
    }

    /**
     * Borra la foto no solo de la pantalla, sino también del archivo real en el celular
     * y de la base de datos para no dejar basura.
     */
    private fun eliminarFotoFisicamente(posicion: Int) {
        val foto = listaFotos[posicion]
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                foto.rutaExistente?.let { path ->
                    val file = File(path)
                    if (file.exists()) file.delete()
                    db.imagenDao().deleteByPath(path)
                }
            }
            listaFotos.removeAt(posicion)
            adapter.notifyItemRemoved(posicion)

            // Si el usuario borra todas las fotos, la pregunta vuelve a estar "Incompleta"
            if (listaFotos.isEmpty()) {
                withContext(Dispatchers.IO) {
                    val respuesta = db.respuestaDao().getAll().find { it.idRespuesta == idRespuestaActual }
                    respuesta?.let {
                        db.respuestaDao().insert(it.copy(texto = "En proceso"))
                    }
                }
                Toast.makeText(this@Respuesta, "Pregunta marcada como incompleta", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Revisa la información EXIF de la foto para ver si hay que rotarla
     * (así evitamos que las fotos salgan de lado).
     */
    private fun corregirRotacion(path: String, source: Bitmap): Bitmap {
        val exif = ExifInterface(path)
        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return source
        }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    /**
     * Dibuja un rectángulo oscuro abajo de la foto y escribe:
     * El nombre del sitio, las coordenadas GPS y la fecha/hora actual.
     */
    private fun agregarMarcaDeAgua(bitmap: Bitmap): Bitmap {
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        val paint = Paint().apply {
            color = Color.WHITE
            textSize = mutableBitmap.height / 45f
            isAntiAlias = true
            setShadowLayer(3f, 2f, 2f, Color.BLACK)
        }
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        val fechaHora = sdf.format(Date())
        val nombre = sitioActual?.nombre ?: "Sitio"
        val gps = "Lat: ${sitioActual?.latitud ?: 0.0}, Lng: ${sitioActual?.longitud ?: 0.0}"

        val bgPaint = Paint().apply { color = Color.BLACK; alpha = 140 }
        canvas.drawRect(0f, mutableBitmap.height * 0.85f, mutableBitmap.width.toFloat(), mutableBitmap.height.toFloat(), bgPaint)

        canvas.drawText("SITIO: $nombre", 40f, mutableBitmap.height * 0.89f, paint)
        canvas.drawText("GPS: $gps | $fechaHora", 40f, mutableBitmap.height * 0.89f + paint.textSize + 10f, paint)
        return mutableBitmap
    }

    /**
     * Prepara el archivo donde se va a guardar la foto temporalmente y abre la cámara.
     */
    private fun lanzarCamaraSegura() {
        try {
            photoFile = File.createTempFile("CAPTURA_", ".jpg", filesDir)
            photoUri = FileProvider.getUriForFile(this, "${packageName}.provider", photoFile!!)
            photoUri?.let { takePictureLauncher.launch(it) }
        } catch (e: Exception) {
            Toast.makeText(this, "No pudimos abrir la cámara", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Muestra la foto en pantalla completa cuando el usuario la toca.
     */
    private fun mostrarVistaPrevia(bitmap: Bitmap) {
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setContentView(R.layout.dialog_foto_preview)
        val ivPreview = dialog.findViewById<ImageView>(R.id.ivFullPreview)
        val btnClose = dialog.findViewById<Button>(R.id.btnClosePreview)
        ivPreview.setImageBitmap(bitmap)
        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    /**
     * Guarda el estado "Finalizado" en la base de datos para que en la lista principal salga VERDE.
     */
    private fun marcarComoFinalizado() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                if (idRespuestaActual != -1L) {
                    val respuesta = db.respuestaDao().getAll().find { it.idRespuesta == idRespuestaActual }
                    respuesta?.let {
                        val finalizada = it.copy(texto = "Finalizado")
                        db.respuestaDao().insert(finalizada)
                    }
                }
            }
            Toast.makeText(this@Respuesta, "¡Todo listo!", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    /**
     * Convierte el bitmap procesado en un archivo JPG real y lo guarda en la carpeta privada de la app.
     */
    private fun guardarFotoAlInstante(bitmap: Bitmap) {
        lifecycleScope.launch {
            try {
                val nuevaRuta = withContext(Dispatchers.IO) {
                    if (idRespuestaActual == -1L) {
                        val respuestaExistente = db.respuestaDao().getAll().find {
                            it.idPregunta == idPreguntaRecibido && it.idTarea == idTareaRecibido
                        }
                        if (respuestaExistente == null) {
                            idRespuestaActual = System.currentTimeMillis()
                            db.respuestaDao().insert(RespuestaEntity(idRespuestaActual, idPreguntaRecibido, idTareaRecibido, "En proceso", Date()))
                        } else {
                            idRespuestaActual = respuestaExistente.idRespuesta
                        }
                    }

                    // El nombre del archivo lleva el sitio y la hora para que sea fácil identificarlo
                    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    val nombreSitioLimpio = (sitioActual?.nombre ?: "SITIO").replace(" ", "_")
                    val nombreArchivo = "${nombreSitioLimpio}_${timeStamp}_${UUID.randomUUID().toString().take(4)}.jpg"

                    val file = File(filesDir, nombreArchivo)
                    val out = FileOutputStream(file)
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    out.close()

                    db.imagenDao().insert(Imagen(System.currentTimeMillis(), idRespuestaActual, file.absolutePath, "HD", Date()))
                    file.absolutePath
                }
                listaFotos.add(FotoEvidencia(bitmap, nuevaRuta))
                adapter.notifyItemInserted(listaFotos.size - 1)
            } catch (e: Exception) {
                Toast.makeText(this@Respuesta, "Error al guardar la foto", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Busca en la base de datos local toda la info de la pregunta y si ya había fotos guardadas.
     */
    private fun cargarDatosExistentesYsitio() {
        lifecycleScope.launch {
            val data: List<Any?> = withContext(Dispatchers.IO) {
                val p = db.preguntaDao().getById(idPreguntaRecibido)
                val s = p?.let { db.seccionDao().getById(it.idSeccion) }
                val r = db.respuestaDao().getAll().find { it.idPregunta == idPreguntaRecibido && it.idTarea == idTareaRecibido }
                r?.let { idRespuestaActual = it.idRespuesta }
                val imgs = r?.let { db.imagenDao().getByRespuesta(it.idRespuesta) } ?: emptyList<Imagen>()
                val tarea = db.tareaDao().getById(idTareaRecibido)
                val sit = tarea?.let { db.sitioDao().getById(it.idSitio) }
                listOf(p, s, imgs, sit)
            }
            val p = data[0] as? Pregunta
            val s = data[1] as? Seccion
            @Suppress("UNCHECKED_CAST")
            val imgs = data[2] as List<Imagen>
            sitioActual = data[3] as? Sitio
            p?.let {
                minFotosRequeridas = it.minImagenes
                maxFotosPermitidas = it.maxImagenes
                txtPregunta.text = it.descripcion
                txtRequisitos.text = "Se necesitan al menos $minFotosRequeridas fotos"
            }
            s?.let { txtSeccion.text = "SECCIÓN: ${it.nombre.uppercase()}" }
            if (imgs.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    imgs.forEach { img ->
                        val bitmap = BitmapFactory.decodeFile(img.rutaArchivo)
                        if (bitmap != null) {
                            withContext(Dispatchers.Main) {
                                listaFotos.add(FotoEvidencia(bitmap, img.rutaArchivo))
                                adapter.notifyItemInserted(listaFotos.size - 1)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Adaptador para mostrar las miniaturas de las fotos que vamos tomando.
     */
    class FotoAdapter(
        private val fotos: List<FotoEvidencia>, 
        private val onEliminar: (Int) -> Unit,
        private val onVerPrevia: (FotoEvidencia) -> Unit
    ) : RecyclerView.Adapter<FotoAdapter.FotoViewHolder>() {
        class FotoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivFoto: ImageView = view.findViewById(R.id.ivFotoItem)
            val btnEliminar: ImageButton = view.findViewById(R.id.btnEliminarFoto)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FotoViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_foto, parent, false)
            return FotoViewHolder(v)
        }
        override fun onBindViewHolder(holder: FotoViewHolder, position: Int) {
            val foto = fotos[position]
            holder.ivFoto.setImageBitmap(foto.bitmap)
            holder.btnEliminar.setOnClickListener { onEliminar(position) }
            holder.ivFoto.setOnClickListener { onVerPrevia(foto) }
        }
        override fun getItemCount() = fotos.size
    }
}
