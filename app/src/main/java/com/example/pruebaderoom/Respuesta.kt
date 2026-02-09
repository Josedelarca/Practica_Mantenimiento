package com.example.pruebaderoom

import android.app.Dialog
import android.graphics.*
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.example.pruebaderoom.data.AppDatabase
import com.example.pruebaderoom.data.entity.*
import com.example.pruebaderoom.data.entity.Respuesta as RespuestaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

data class FotoEvidencia(val bitmap: Bitmap, var rutaExistente: String? = null)

class Respuesta : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private val listaFotos = mutableListOf<FotoEvidencia>() 
    private lateinit var rvFotos: RecyclerView
    private lateinit var adapter: FotoAdapter
    
    private lateinit var txtSeccion: TextView
    private lateinit var txtPregunta: TextView
    private lateinit var txtRequisitos: TextView
    private lateinit var containerCampos: LinearLayout
    
    private var idPreguntaRecibido: Long = -1
    private var idTareaRecibido: Long = -1
    private var idRespuestaActual: Long = -1 
    private var minFotosRequeridas: Int = 0
    private var maxFotosPermitidas: Int = 0
    private var sitioActual: Sitio? = null

    private var photoFile: File? = null
    private var photoUri: Uri? = null
    
    private val mapaVistasCampos = mutableMapOf<Long, View>()

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && photoFile != null) {
            val rawBitmap = BitmapFactory.decodeFile(photoFile!!.absolutePath)
            rawBitmap?.let { bitmap ->
                val bitmapDerecho = corregirRotacion(photoFile!!.absolutePath, bitmap)
                if (listaFotos.size < maxFotosPermitidas) {
                    val bitmapConMarca = agregarMarcaDeAgua(bitmapDerecho)
                    guardarFotoAlInstante(bitmapConMarca)
                    if (photoFile!!.exists()) photoFile!!.delete()
                } else {
                    Toast.makeText(this, "Límite de fotos alcanzado", Toast.LENGTH_SHORT).show()
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
        containerCampos = findViewById(R.id.containerCamposDinamicos)
        rvFotos = findViewById(R.id.rvFotos)
        
        idPreguntaRecibido = intent.getLongExtra("ID_PREGUNTA", -1)
        idTareaRecibido = intent.getLongExtra("ID_TAREA", -1)

        cargarDatosExistentesYsitio()

        adapter = FotoAdapter(
            listaFotos, 
            onEliminar = { posicion -> eliminarFotoFisicamente(posicion) },
            onVerPrevia = { foto -> mostrarVistaPrevia(foto.bitmap) }
        )
        rvFotos.adapter = adapter

        findViewById<Button>(R.id.btnSubir).setOnClickListener {
            if (listaFotos.size >= maxFotosPermitidas) {
                Toast.makeText(this, "Límite alcanzado", Toast.LENGTH_SHORT).show()
            } else {
                lanzarCamaraSegura()
            }
        }

        findViewById<Button>(R.id.btnEnviar).setOnClickListener {
            validarYGuardar()
        }

        findViewById<Button>(R.id.btnVolver).setOnClickListener { finish() }
    }

    private fun cargarDatosExistentesYsitio() {
        lifecycleScope.launch {
            val data = withContext(Dispatchers.IO) {
                val p = db.preguntaDao().getById(idPreguntaRecibido)
                val s = p?.let { db.seccionDao().getById(it.idSeccion) }
                val r = db.respuestaDao().getByTarea(idTareaRecibido).find { it.idPregunta == idPreguntaRecibido }
                
                if (r != null) idRespuestaActual = r.idRespuesta
                
                val im = r?.let { db.imagenDao().getByRespuesta(it.idRespuesta) } ?: emptyList<Imagen>()
                val tarea = db.tareaDao().getById(idTareaRecibido)
                val sit = tarea?.let { db.sitioDao().getById(it.idSitio) }
                
                val camp = db.campoDao().getByPregunta(idPreguntaRecibido)
                val valEx = r?.let { db.valorRespuestaDao().getByRespuesta(it.idRespuesta) } ?: emptyList<ValorRespuesta>()
                
                mapOf(
                    "pregunta" to p,
                    "seccion" to s,
                    "imgs" to im,
                    "sitio" to sit,
                    "campos" to camp,
                    "valores" to valEx
                )
            }

            val p = data["pregunta"] as? Pregunta
            val s = data["seccion"] as? Seccion
            @Suppress("UNCHECKED_CAST")
            val ims = data["imgs"] as List<Imagen>
            sitioActual = data["sitio"] as? Sitio
            @Suppress("UNCHECKED_CAST")
            val listCampos = data["campos"] as List<Campo>
            @Suppress("UNCHECKED_CAST")
            val listValores = data["valores"] as List<ValorRespuesta>

            p?.let {
                minFotosRequeridas = it.minImagenes
                maxFotosPermitidas = it.maxImagenes
                txtPregunta.text = it.descripcion
                txtRequisitos.text = "Mínimo: $minFotosRequeridas fotos"
            }
            s?.let { txtSeccion.text = "SECCIÓN: ${it.nombre.uppercase()}" }

            renderizarCampos(listCampos, listValores)

            if (ims.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    ims.forEach { img ->
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

    private fun renderizarCampos(campos: List<Campo>, valores: List<ValorRespuesta>) {
        containerCampos.removeAllViews()
        mapaVistasCampos.clear()

        campos.forEach { campo ->
            val valorExistente = valores.find { it.idCampo == campo.idCampo }?.valor
            
            val layoutCampo = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 8, 0, 8)
            }

            val label = TextView(this).apply {
                text = campo.label
                textSize = 14f
                setTextColor(Color.parseColor("#1E2A44"))
                setTypeface(null, Typeface.BOLD)
            }
            layoutCampo.addView(label)

            val vistaInput: View = when (campo.tipo.lowercase()) {
                "booleano" -> RadioGroup(this).apply {
                    orientation = RadioGroup.HORIZONTAL
                    val rbSi = RadioButton(this@Respuesta).apply { 
                        text = "SI"
                        id = View.generateViewId() 
                    }
                    val rbNo = RadioButton(this@Respuesta).apply { 
                        text = "NO"
                        id = View.generateViewId() 
                    }
                    
                    addView(rbSi)
                    addView(rbNo)

                    if (valorExistente == "1") rbSi.isChecked = true
                    else if (valorExistente == "0") rbNo.isChecked = true
                }
                "numero" -> EditText(this).apply {
                    inputType = android.text.InputType.TYPE_CLASS_NUMBER
                    hint = "Ingresar número..."
                    setText(valorExistente)
                }
                else -> EditText(this).apply { 
                    hint = "Escribir aquí..."
                    setText(valorExistente)
                }
            }
            
            layoutCampo.addView(vistaInput)
            containerCampos.addView(layoutCampo)
            mapaVistasCampos[campo.idCampo] = vistaInput
        }
    }

    private fun validarYGuardar() {
        // 1. Validar Fotos
        if (listaFotos.size < minFotosRequeridas) {
            Toast.makeText(this, "Faltan fotos ($minFotosRequeridas mín)", Toast.LENGTH_SHORT).show()
            return
        }

        // 2. Validar Campos Dinámicos
        var todosLlenos = true
        mapaVistasCampos.values.forEach { vista ->
            when (vista) {
                is RadioGroup -> {
                    if (vista.checkedRadioButtonId == -1) todosLlenos = false
                }
                is EditText -> {
                    if (vista.text.toString().trim().isEmpty()) todosLlenos = false
                }
            }
        }

        if (!todosLlenos) {
            Toast.makeText(this, "Debes completar todas las preguntas", Toast.LENGTH_SHORT).show()
            return
        }

        // Si todo está bien, guardamos
        guardarTodoYFinalizar()
    }

    private fun guardarTodoYFinalizar() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                // 1. Asegurar que exista la respuesta padre
                if (idRespuestaActual == -1L) {
                    idRespuestaActual = System.currentTimeMillis()
                    db.respuestaDao().insert(RespuestaEntity(idRespuestaActual, idPreguntaRecibido, idTareaRecibido, "Finalizado", Date()))
                } else {
                    val r = db.respuestaDao().getById(idRespuestaActual)
                    if (r != null) {
                        db.respuestaDao().insert(r.copy(texto = "Finalizado"))
                    }
                }

                // 2. Guardar valores de los campos
                val nuevosValores = mutableListOf<ValorRespuesta>()
                mapaVistasCampos.forEach { (idCampo, vista) ->
                    val valorStr = when (vista) {
                        is RadioGroup -> {
                            val rb = vista.findViewById<RadioButton>(vista.checkedRadioButtonId)
                            if (rb != null && rb.text == "SI") "1" else "0"
                        }
                        is EditText -> vista.text.toString()
                        else -> ""
                    }
                    nuevosValores.add(ValorRespuesta(idRespuesta = idRespuestaActual, idCampo = idCampo, valor = valorStr))
                }
                
                db.valorRespuestaDao().deleteByRespuesta(idRespuestaActual)
                db.valorRespuestaDao().insertAll(nuevosValores)
            }
            Toast.makeText(this@Respuesta, "¡Inspección guardada!", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

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
        }
    }

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

    private fun lanzarCamaraSegura() {
        try {
            photoFile = File.createTempFile("CAPTURA_", ".jpg", filesDir)
            photoUri = FileProvider.getUriForFile(this, "${packageName}.provider", photoFile!!)
            photoUri?.let { takePictureLauncher.launch(it) }
        } catch (e: Exception) {
            Toast.makeText(this, "No pudimos abrir la cámara", Toast.LENGTH_SHORT).show()
        }
    }

    private fun mostrarVistaPrevia(bitmap: Bitmap) {
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setContentView(R.layout.dialog_foto_preview)
        val ivPreview = dialog.findViewById<ImageView>(R.id.ivFullPreview)
        val btnClose = dialog.findViewById<Button>(R.id.btnClosePreview)
        ivPreview.setImageBitmap(bitmap)
        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun guardarFotoAlInstante(bitmap: Bitmap) {
        lifecycleScope.launch {
            try {
                val nuevaRuta = withContext(Dispatchers.IO) {
                    if (idRespuestaActual == -1L) {
                        val resp = db.respuestaDao().getByTarea(idTareaRecibido).find { it.idPregunta == idPreguntaRecibido }
                        if (resp == null) {
                            idRespuestaActual = System.currentTimeMillis()
                            db.respuestaDao().insert(RespuestaEntity(idRespuestaActual, idPreguntaRecibido, idTareaRecibido, "En proceso", Date()))
                        } else {
                            idRespuestaActual = resp.idRespuesta
                        }
                    }

                    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    val nombreArchivo = "IMG_${idRespuestaActual}_${timeStamp}.jpg"
                    val file = File(filesDir, nombreArchivo)
                    
                    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }

                    db.imagenDao().insert(Imagen(System.currentTimeMillis(), idRespuestaActual, file.absolutePath, "HD", Date()))
                    file.absolutePath
                }
                listaFotos.add(FotoEvidencia(bitmap, nuevaRuta))
                adapter.notifyItemInserted(listaFotos.size - 1)
            } catch (e: Exception) {
                Toast.makeText(this@Respuesta, "Error al guardar foto", Toast.LENGTH_SHORT).show()
            }
        }
    }

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
