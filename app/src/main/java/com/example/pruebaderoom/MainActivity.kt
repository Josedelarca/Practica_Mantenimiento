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
import com.example.pruebaderoom.data.entity.Sitio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Esta es la pantalla principal, donde el técnico busca el lugar (sitio) donde trabajará.
 */
class MainActivity : AppCompatActivity() {

    // Declaramos las herramientas que usaremos
    private lateinit var db: AppDatabase // La base de datos local
    private lateinit var txtInfo: TextView // El panel donde se ven los detalles del sitio
    private lateinit var autoCompleteSitios: AutoCompleteTextView // El buscador de sitios
    private lateinit var btnVerMapa: Button // El botón para abrir Google Maps
    private var listaSitios: List<Sitio> = emptyList() // Aquí guardamos la lista de sitios temporalmente
    private var sitioSeleccionado: Sitio? = null // El sitio que el usuario eligió

    override fun onCreate(savedInstanceState: Bundle?) {
        // Obligamos a la app a usar modo claro para mantener nuestros colores azul y blanco
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        
        // Configuramos los márgenes de la pantalla para que no se corten con la barra de la batería
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Conectamos con la base de datos y la interfaz visual
        db = AppDatabase.getDatabase(this)
        txtInfo = findViewById(R.id.txtInfo)
        autoCompleteSitios = findViewById(R.id.autoCompleteCiudades)
        btnVerMapa = findViewById(R.id.btnVerMapa)
        val btnpasar = findViewById<Button>(R.id.btnpasar)

        // PASO 1: Cargamos de inmediato lo que ya está guardado en el teléfono
        actualizarInterfaz()
        
        // PASO 2: Intentamos traer sitios nuevos desde internet (API)
        sincronizarSitios()

        // ¿Qué pasa cuando el usuario elige un sitio del buscador?
        autoCompleteSitios.onItemClickListener = AdapterView.OnItemClickListener { parent, _, position, _ ->
            val nombreSeleccionado = parent.getItemAtPosition(position) as String
            // Buscamos el sitio completo usando el nombre que el usuario seleccionó
            sitioSeleccionado = listaSitios.find { it.nombre == nombreSeleccionado }
            
            sitioSeleccionado?.let {
                // Dibujamos toda la información técnica en la tarjeta blanca
                txtInfo.text = """
                    ID SITIO: ${it.idSitio}
                    NOMBRE: ${it.nombre}
                    TEAM: ${it.teem}
                    VISITAS: ${it.visit}
                """.trimIndent()
                
                // Si seleccionó un sitio, hacemos que el botón de Google Maps aparezca
                btnVerMapa.visibility = View.VISIBLE
            }
        }

        // Acción para abrir Google Maps externo
        btnVerMapa.setOnClickListener {
            sitioSeleccionado?.let { sitio ->
                try {
                    // Creamos un enlace especial con las coordenadas GPS del sitio
                    val uri = Uri.parse("geo:${sitio.latitud},${sitio.longitud}?q=${sitio.latitud},${sitio.longitud}(${sitio.nombre})")
                    val intent = Intent(Intent.ACTION_VIEW, uri)
                    intent.setPackage("com.google.android.apps.maps")
                    startActivity(intent) // Mandamos al técnico directamente a la app de Google Maps
                } catch (e: Exception) {
                    Toast.makeText(this, "No se pudo abrir Google Maps", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Al presionar el botón de "Empezar", viajamos a la pantalla de Preguntas
        btnpasar.setOnClickListener {
            val intent = Intent(this, InspeccionActivity::class.java)
            startActivity(intent)
        }
    }

    /**
     * Esta función va a Laravel, baja los sitios nuevos y los guarda en Room.
     */
    private fun sincronizarSitios() {
        lifecycleScope.launch {
            try {
                // Pedimos los sitios a la red (hilo IO)
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.instance.getSitios()
                }
                val sitiosApi = response.data
                
                if (sitiosApi.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        val sitioDao = db.sitioDao()
                        // Borramos los viejos y guardamos los nuevos para estar al día
                        sitioDao.deleteAll()
                        sitiosApi.forEach { sitio ->
                            sitioDao.insert(sitio)
                        }
                    }
                    // Refrescamos la lista del buscador con los datos recién bajados
                    actualizarInterfaz()
                }
            } catch (e: Exception) {
                Log.e("API_SYNC", "No hay internet, usando los sitios guardados en el celular.")
            }
        }
    }

    /**
     * Esta función lee la base de datos local y llena el buscador de la pantalla.
     */
    private fun actualizarInterfaz() {
        lifecycleScope.launch {
            // Buscamos los sitios en Room (hilo IO)
            val sitiosLocal = withContext(Dispatchers.IO) { db.sitioDao().getAll() }
            listaSitios = sitiosLocal
            
            // Creamos una lista solo con los nombres para que el buscador la use
            val nombres = listaSitios.map { it.nombre }
            val adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_dropdown_item_1line, nombres)
            autoCompleteSitios.setAdapter(adapter)
        }
    }
}
