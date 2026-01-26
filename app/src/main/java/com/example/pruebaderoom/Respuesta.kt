package com.example.pruebaderoom

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.example.pruebaderoom.data.AppDatabase
import com.example.pruebaderoom.data.entity.Imagen
import com.example.pruebaderoom.data.entity.Respuesta as RespuestaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.*

/**
 * Pantalla de captura de fotos. 
 * El estado VERDE en la lista solo se activa cuando el usuario pulsa "GUARDAR TODO".
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

    private val tomarFotoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val imageBitmap = result.data?.extras?.get("data") as? Bitmap
            imageBitmap?.let { bitmap ->
                if (listaFotos.size < maxFotosPermitidas) {
                    guardarFotoAlInstante(bitmap)
                } else {
                    Toast.makeText(this, "Límite máximo alcanzado", Toast.LENGTH_SHORT).show()
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

        cargarDatosYFotosExistentes()

        adapter = FotoAdapter(listaFotos) { posicion ->
            eliminarFotoFisicamente(posicion)
        }
        rvFotos.adapter = adapter

        findViewById<Button>(R.id.btnSubir).setOnClickListener {
            if (listaFotos.size >= maxFotosPermitidas) {
                Toast.makeText(this, "Límite máximo alcanzado", Toast.LENGTH_SHORT).show()
            } else {
                val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                tomarFotoLauncher.launch(intent)
            }
        }

        // CAMBIO: Solo al pulsar este botón marcamos la tarea como "Finalizado" (se pondrá VERDE)
        findViewById<Button>(R.id.btnEnviar).setOnClickListener {
            if (listaFotos.size < minFotosRequeridas) {
                Toast.makeText(this, "Faltan fotos para el mínimo ($minFotosRequeridas)", Toast.LENGTH_SHORT).show()
            } else {
                marcarComoFinalizado()
            }
        }

        findViewById<Button>(R.id.btnVolver).setOnClickListener { finish() }
    }

    /**
     * Cambia el estado de la respuesta a "Finalizado" para que se vea verde en la lista.
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
            Toast.makeText(this@Respuesta, "Inspección completada con éxito", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

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
                            // Guardamos inicialmente como "En proceso" (Se verá ROJO)
                            db.respuestaDao().insert(RespuestaEntity(idRespuestaActual, idPreguntaRecibido, idTareaRecibido, "En proceso", Date()))
                        } else {
                            idRespuestaActual = respuestaExistente.idRespuesta
                        }
                    }

                    val file = File(filesDir, "img_${idRespuestaActual}_${UUID.randomUUID()}.jpg")
                    val out = FileOutputStream(file)
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    out.close()
                    
                    db.imagenDao().insert(Imagen(
                        idImagen = System.currentTimeMillis(),
                        idRespuesta = idRespuestaActual, 
                        rutaArchivo = file.absolutePath,
                        marcaAgua = "Autoguardado",
                        fecha = Date()
                    ))
                    file.absolutePath
                }

                listaFotos.add(FotoEvidencia(bitmap, nuevaRuta))
                adapter.notifyItemInserted(listaFotos.size - 1)
                
            } catch (e: Exception) {
                Toast.makeText(this@Respuesta, "Error al autoguardar", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun eliminarFotoFisicamente(posicion: Int) {
        val foto = listaFotos[posicion]
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                foto.rutaExistente?.let { path ->
                    val file = File(path)
                    if (file.exists()) file.delete()
                }
            }
            listaFotos.removeAt(posicion)
            adapter.notifyItemRemoved(posicion)
        }
    }

    private fun cargarDatosYFotosExistentes() {
        lifecycleScope.launch {
            val (preg, sec, fotosGuardadas) = withContext(Dispatchers.IO) {
                val p = db.preguntaDao().getById(idPreguntaRecibido)
                val s = p?.let { db.seccionDao().getById(it.idSeccion) }
                val r = db.respuestaDao().getAll().find { it.idPregunta == idPreguntaRecibido && it.idTarea == idTareaRecibido }
                r?.let { idRespuestaActual = it.idRespuesta }
                val imgs = r?.let { db.imagenDao().getByRespuesta(it.idRespuesta) } ?: emptyList()
                Triple(p, s, imgs)
            }

            preg?.let {
                minFotosRequeridas = it.minImagenes
                maxFotosPermitidas = it.maxImagenes
                txtPregunta.text = it.descripcion
                txtRequisitos.text = "Mínimo: $minFotosRequeridas fotos"
            }
            sec?.let { txtSeccion.text = "SECCIÓN: ${it.nombre.uppercase()}" }

            if (fotosGuardadas.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    fotosGuardadas.forEach { img ->
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

    class FotoAdapter(private val fotos: List<FotoEvidencia>, private val onEliminar: (Int) -> Unit) : RecyclerView.Adapter<FotoAdapter.FotoViewHolder>() {
        class FotoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivFoto: ImageView = view.findViewById(R.id.ivFotoItem)
            val btnEliminar: ImageButton = view.findViewById(R.id.btnEliminarFoto)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FotoViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_foto, parent, false)
            return FotoViewHolder(v)
        }
        override fun onBindViewHolder(holder: FotoViewHolder, position: Int) {
            holder.ivFoto.setImageBitmap(fotos[position].bitmap)
            holder.btnEliminar.setOnClickListener { onEliminar(position) }
        }
        override fun getItemCount() = fotos.size
    }
}
