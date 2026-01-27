package com.example.pruebaderoom

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.pruebaderoom.data.AppDatabase
import com.example.pruebaderoom.data.RetrofitClient
import com.example.pruebaderoom.data.entity.EstadoTarea
import com.example.pruebaderoom.data.entity.TipoMantenimiento
import com.example.pruebaderoom.data.entity.Sitio
import com.example.pruebaderoom.data.entity.Tarea
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

/**
 * Esta es nuestra pantalla principal. Aquí es donde el usuario elige el sitio
 * que va a inspeccionar y puede ver su ubicación en el mapa.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var txtInfo: TextView
    private lateinit var autoCompleteSitios: AutoCompleteTextView
    private lateinit var btnVerMapa: Button
    private var listaSitios: List<Sitio> = emptyList()
    private var sitioSeleccionado: Sitio? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Forzamos el modo claro para que la interfaz se vea siempre igual
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        
        // Ajustamos el diseño para que no se oculte detrás de las barras del sistema (notch, botones, etc.)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Inicializamos la base de datos y buscamos los elementos de la interfaz por su ID
        db = AppDatabase.getDatabase(this)
        txtInfo = findViewById(R.id.txtInfo)
        autoCompleteSitios = findViewById(R.id.autoCompleteCiudades)
        btnVerMapa = findViewById(R.id.btnVerMapa)
        val btnpasar = findViewById<Button>(R.id.btnpasar)

        // Al arrancar, mostramos lo que tengamos guardado y tratamos de traer datos nuevos de internet
        actualizarInterfaz()
        sincronizarSitios()

        // Cuando el usuario elige un sitio de la lista desplegable...
        autoCompleteSitios.onItemClickListener = AdapterView.OnItemClickListener { parent, _, position, _ ->
            val nombreSeleccionado = parent.getItemAtPosition(position) as String
            sitioSeleccionado = listaSitios.find { it.nombre == nombreSeleccionado }
            
            // Mostramos los detalles del sitio seleccionado en pantalla
            sitioSeleccionado?.let {
                txtInfo.text = """
                    ID SITIO: ${it.idSitio}
                    NOMBRE: ${it.nombre}
                    TEAM: ${it.teem}
                    VISITAS: ${it.visit}
                """.trimIndent()
                btnVerMapa.visibility = View.VISIBLE // Habilitamos el botón para ver el mapa
            }
        }

        // Si le da al botón de mapa, abrimos Google Maps con las coordenadas del sitio
        btnVerMapa.setOnClickListener {
            sitioSeleccionado?.let { sitio ->
                try {
                    val uri = Uri.parse("geo:${sitio.latitud},${sitio.longitud}?q=${sitio.latitud},${sitio.longitud}(${sitio.nombre})")
                    val intent = Intent(Intent.ACTION_VIEW, uri)
                    intent.setPackage("com.google.android.apps.maps")
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Google Maps no instalado", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Este botón "Iniciar" crea una nueva tarea de inspección en la base de datos y nos lleva a la otra pantalla
        btnpasar.setOnClickListener {
            val sitio = sitioSeleccionado
            if (sitio == null) {
                Toast.makeText(this, "Selecciona un sitio primero", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val nuevaTareaId = System.currentTimeMillis() // Usamos el tiempo actual como un ID único temporal
                
                // Preparamos los datos de la nueva tarea
                val nuevaTarea = Tarea(
                    idTarea = nuevaTareaId,
                    idSitio = sitio.idSitio,
                    idFormulario = 1L,
                    tipoMantenimiento = TipoMantenimiento.PREVENTIVO,
                    fecha = Date(),
                    observacionesGenerales = "Inspección iniciada desde la App",
                    estado = EstadoTarea.EN_PROCESO
                )
                
                // Guardamos la tarea en la base de datos local (en segundo plano)
                withContext(Dispatchers.IO) {
                    db.tareaDao().insert(nuevaTarea)
                }

                // Nos vamos a la pantalla de Inspección pasando el ID de la tarea
                val intent = Intent(this@MainActivity, InspeccionActivity::class.java)
                intent.putExtra("ID_TAREA", nuevaTareaId)
                startActivity(intent)
            }
        }
    }

    /**
     * Esta función se conecta al servidor para bajar la lista de sitios actualizada.
     * Si encuentra datos nuevos, limpia lo viejo y guarda lo nuevo en el celular.
     */
    private fun sincronizarSitios() {
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) { RetrofitClient.instance.getSitios() }
                val sitiosApi = response.data
                if (sitiosApi.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        val sitioDao = db.sitioDao()
                        sitioDao.deleteAll()
                        sitiosApi.forEach { sitioDao.insert(it) }
                    }
                    actualizarInterfaz() // Refrescamos la lista que ve el usuario
                }
            } catch (e: Exception) {
                Log.e("API", "Error al sincronizar: ${e.message}")
            }
        }
    }

    /**
     * Lee los sitios de la base de datos local y los pone en el buscador (Autocomplete).
     */
    private fun actualizarInterfaz() {
        lifecycleScope.launch {
            val sitiosLocal = withContext(Dispatchers.IO) { db.sitioDao().getAll() }
            listaSitios = sitiosLocal
            val nombres = listaSitios.map { it.nombre }
            val adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_dropdown_item_1line, nombres)
            autoCompleteSitios.setAdapter(adapter)
        }
    }
}
