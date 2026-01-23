package com.example.pruebaderoom

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
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
import com.example.pruebaderoom.data.entity.Pregunta
import com.example.pruebaderoom.data.entity.Seccion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.*

/**
 * Esta pantalla sirve para capturar las evidencias fotográficas de una tarea específica.
 * Hemos diseñado este código para que sea inteligente y le diga al técnico qué debe fotografiar.
 */
class Respuesta : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private val listaBitmaps = mutableListOf<Bitmap>() // Guardamos las fotos tomadas en esta lista temporal
    private lateinit var rvFotos: RecyclerView
    private lateinit var adapter: FotoAdapter
    
    // Variables para los textos dinámicos de la pantalla
    private lateinit var txtSeccion: TextView
    private lateinit var txtPregunta: TextView
    private lateinit var txtRequisitos: TextView
    
    // Aquí guardamos el código de la pregunta que recibimos desde la lista
    private var idPreguntaRecibido: Long = -1

    // Este es el "escuchador" que recibe la foto de la cámara y la pone en la lista visual
    private val tomarFotoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            // Extraemos la imagen de los datos recibidos
            val imageBitmap = result.data?.extras?.get("data") as? Bitmap
            imageBitmap?.let {
                listaBitmaps.add(it)
                // Avisamos a la pantalla que hay una foto nueva para mostrar
                adapter.notifyItemInserted(listaBitmaps.size - 1)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Obligamos a la app a usar modo claro para que los colores azul y blanco se vean siempre profesionales
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_respuesta)

        // 1. Conectamos con la base de datos local y con los elementos del diseño XML
        db = AppDatabase.getDatabase(this)
        txtSeccion = findViewById(R.id.txtSeccionActual)
        txtPregunta = findViewById(R.id.txtPreguntaActual)
        txtRequisitos = findViewById(R.id.txtRequisitosFotos)
        rvFotos = findViewById(R.id.rvFotos)
        
        // 2. RECUPERAMOS EL ID: Leemos qué pregunta seleccionó el técnico anteriormente
        idPreguntaRecibido = intent.getLongExtra("ID_PREGUNTA", -1)

        // 3. CARGA HUMANA: Buscamos en Room los detalles reales de esa pregunta
        cargarDetallesDeLaPregunta()

        // Configuramos la lista horizontal para ver las miniaturas de las fotos tomadas
        adapter = FotoAdapter(listaBitmaps)
        rvFotos.adapter = adapter

        // ACCIÓN: Al pulsar el botón de cámara, abrimos la aplicación de cámara del celular
        findViewById<Button>(R.id.btnSubir).setOnClickListener {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            tomarFotoLauncher.launch(intent)
        }

        // ACCIÓN: Al pulsar "Guardar", guardamos las fotos en el celular y registramos el evento
        findViewById<Button>(R.id.btnEnviar).setOnClickListener {
            if (listaBitmaps.isEmpty()) {
                Toast.makeText(this, "Por favor, toma al menos una foto primero", Toast.LENGTH_SHORT).show()
            } else {
                guardarTodoEnBaseDeDatos()
            }
        }

        // Botón para cancelar y regresar a la lista de preguntas sin guardar nada
        findViewById<Button>(R.id.btnVolver).setOnClickListener { finish() }
    }

    /**
     * Esta función busca en Room la descripción de la pregunta y su sección.
     * Así el técnico sabe exactamente qué está respondiendo (ej: "Sección Torre").
     */
    private fun cargarDetallesDeLaPregunta() {
        if (idPreguntaRecibido == -1L) return

        lifecycleScope.launch {
            // Realizamos la búsqueda en un hilo de fondo (IO) para que la app no se trabe
            val (pregunta, seccion) = withContext(Dispatchers.IO) {
                val preg = db.preguntaDao().getById(idPreguntaRecibido)
                val sec = preg?.let { db.seccionDao().getById(it.idSeccion) }
                Pair(preg, sec)
            }

            // Una vez encontrados los datos, los actualizamos en la pantalla (hilo principal)
            pregunta?.let {
                txtPregunta.text = it.descripcion
                // CAMBIO: Ahora informamos sólo de la cantidad mínima requerida
                txtRequisitos.text = "Esta tarea requiere al menos ${it.minImagenes} fotografías."
            }
            
            seccion?.let {
                txtSeccion.text = "SECCIÓN: ${it.nombre.uppercase()}"
            }
        }
    }

    /**
     * Guarda las fotos físicamente en el almacenamiento del celular y registra sus rutas en Room.
     */
    private fun guardarTodoEnBaseDeDatos() {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    listaBitmaps.forEach { bitmap ->
                        // Generamos un nombre único para cada foto
                        val nombre = "evidencia_preg_${idPreguntaRecibido}_${UUID.randomUUID()}.jpg"
                        val archivo = File(filesDir, nombre)
                        val out = FileOutputStream(archivo)
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                        out.close()

                        // Registramos la foto en Room vinculada a la pregunta
                        val nuevaImagen = Imagen(
                            idImagen = System.currentTimeMillis() + listaBitmaps.indexOf(bitmap),
                            idRespuesta = idPreguntaRecibido, 
                            rutaArchivo = archivo.absolutePath,
                            marcaAgua = "Inspección ID: $idPreguntaRecibido - ${Date()}",
                            fecha = Date()
                        )
                        db.imagenDao().insert(nuevaImagen)
                    }
                }
                Toast.makeText(this@Respuesta, "Evidencias guardadas con éxito", Toast.LENGTH_SHORT).show()
                finish() // Regresamos automáticamente a la lista de preguntas
            } catch (e: Exception) {
                Toast.makeText(this@Respuesta, "Error al guardar: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Adaptador para mostrar las miniaturas de las fotos tomadas en la lista inferior
    class FotoAdapter(private val fotos: List<Bitmap>) : RecyclerView.Adapter<FotoAdapter.FotoViewHolder>() {
        class FotoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivFoto: ImageView = view.findViewById(R.id.ivFotoItem)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FotoViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_foto, parent, false)
            return FotoViewHolder(view)
        }
        override fun onBindViewHolder(holder: FotoViewHolder, position: Int) {
            holder.ivFoto.setImageBitmap(fotos[position])
        }
        override fun getItemCount() = fotos.size
    }
}
