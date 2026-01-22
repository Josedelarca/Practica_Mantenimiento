package com.example.pruebaderoom

// Aquí traemos todas las herramientas necesarias para que la app funcione (cámara, base de datos, botones, etc.)
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.example.pruebaderoom.data.AppDatabase
import com.example.pruebaderoom.data.entity.Imagen
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.*

class Respuesta : AppCompatActivity() {

    // Esta es nuestra conexión a la base de datos Room
    private lateinit var db: AppDatabase
    
    // Aquí guardamos temporalmente las fotos que vas tomando para que se vean en la pantalla
    private val listaBitmaps = mutableListOf<Bitmap>()
    
    // Esta lista guardará las rutas (direcciones) de donde se guardan las fotos en el celular
    private val listaRutas = mutableListOf<String>()
    
    // Es el "contenedor" visual que muestra la fila de fotos
    private lateinit var rvFotos: RecyclerView
    
    // Es el "traductor" que le dice al contenedor cómo mostrar cada foto
    private lateinit var adapter: FotoAdapter

    // Este es el encargado de recibir la foto cuando terminas de usar la cámara
    private val tomarFotoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        // Si la foto se tomó correctamente...
        if (result.resultCode == RESULT_OK) {
            // Extraemos la imagen de los datos recibidos
            val imageBitmap = result.data?.extras?.get("data") as? Bitmap
            imageBitmap?.let {
                // La guardamos en nuestra lista temporal
                listaBitmaps.add(it)
                // Le avisamos a la pantalla que hay una foto nueva para mostrar
                adapter.notifyItemInserted(listaBitmaps.size - 1)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Hace que la aplicación use toda la pantalla, incluyendo la zona de la batería
        enableEdgeToEdge()
        // Cargamos el diseño visual que hicimos en el archivo XML
        setContentView(R.layout.activity_respuesta)

        // Conectamos con la base de datos Room
        db = AppDatabase.getDatabase(this)
        
        // Configuramos el visor de fotos (RecyclerView)
        rvFotos = findViewById(R.id.rvFotos)
        adapter = FotoAdapter(listaBitmaps)
        rvFotos.adapter = adapter

        // Cuando tocas el botón de "Fotos"...
        findViewById<Button>(R.id.btnSubir).setOnClickListener {
            // Preparamos la orden para abrir la cámara del celular
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            // Abrimos la cámara y esperamos a que tomes la foto
            tomarFotoLauncher.launch(intent)
        }

        // Cuando tocas el botón de "Enviar"...
        findViewById<Button>(R.id.btnEnviar).setOnClickListener {
            // Si no hay fotos, te avisamos que debes tomar al menos una
            if (listaBitmaps.isEmpty()) {
                Toast.makeText(this, "Toma al menos una foto primero", Toast.LENGTH_SHORT).show()
            } else {
                // Si todo está bien, guardamos todo permanentemente
                guardarTodoEnBaseDeDatos()
            }
        }

        // El botón para cerrar esta pantalla y volver a la anterior
        findViewById<Button>(R.id.btnVolver).setOnClickListener {
            finish()
        }
    }

    // Esta función guarda las fotos en el teléfono y sus datos en Room
    private fun guardarTodoEnBaseDeDatos() {
        // Ejecutamos esto en segundo plano para que la app no se trabe
        lifecycleScope.launch {
            try {
                // Por cada foto que  se captura
                listaBitmaps.forEach { bitmap ->
                    // 1. Le inventamos un nombre único para que no se repita
                    val nombre = "inspeccion_${UUID.randomUUID()}.jpg"
                    // 2. Creamos el archivo dentro de la carpeta privada de la app
                    val archivo = File(filesDir, nombre)
                    // 3. Escribimos los datos de la imagen dentro de ese archivo
                    val out = FileOutputStream(archivo)
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    out.close()

                    // 4. Guardamos la información (ruta, fecha, etc.) en la base de datos Room
                    val nuevaImagen = Imagen(
                        idImagen = System.currentTimeMillis() + listaBitmaps.indexOf(bitmap),
                        idRespuesta = 1L, // Usamos un ID temporal de ejemplo
                        rutaArchivo = archivo.absolutePath, // Aquí guardamos la dirección de la foto
                        marcaAgua = "Inspección: ${Date()}",
                        fecha = Date()
                    )
                    // Insertamos el registro en la tabla de imágenes
                    db.imagenDao().insert(nuevaImagen)
                }
                // Avisamos que todo se guardó bien
                Toast.makeText(this@Respuesta, "Se guardaron ${listaBitmaps.size} fotos correctamente", Toast.LENGTH_LONG).show()
                // Cerramos la pantalla
                finish()
            } catch (e: Exception) {
                // Si algo falla, mostramos el error
                Toast.makeText(this@Respuesta, "Error al guardar: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // --- Esta parte interna ayuda a mostrar las fotos en la lista de la pantalla ---
    class FotoAdapter(private val fotos: List<Bitmap>) : RecyclerView.Adapter<FotoAdapter.FotoViewHolder>() {
        
        // Sujeta el diseño de cada foto individual
        class FotoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivFoto: ImageView = view.findViewById(R.id.ivFotoItem)
        }

        // Crea el "molde" visual para cada foto
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FotoViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_foto, parent, false)
            return FotoViewHolder(view)
        }

        // Pone la foto real en el molde visual
        override fun onBindViewHolder(holder: FotoViewHolder, position: Int) {
            holder.ivFoto.setImageBitmap(fotos[position])
        }

        // Dice cuántas fotos hay para mostrar
        override fun getItemCount() = fotos.size
    }
}
