package com.example.pruebaderoom

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.room.withTransaction
import com.example.pruebaderoom.data.AppDatabase
import com.example.pruebaderoom.data.RetrofitClient
import com.example.pruebaderoom.data.entity.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var txtInfo: TextView
    private lateinit var autoCompleteSitios: AutoCompleteTextView
    private lateinit var btnVerMapa: Button
    private var listaSitios: List<Sitio> = emptyList()
    private var sitioSeleccionado: Sitio? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        db = AppDatabase.getDatabase(this)
        txtInfo = findViewById(R.id.txtInfo)
        autoCompleteSitios = findViewById(R.id.autoCompleteCiudades)
        btnVerMapa = findViewById(R.id.btnVerMapa)
        val btnpasar = findViewById<Button>(R.id.btnpasar)
        val btnSync = findViewById<ImageButton>(R.id.btnSync)

        observarDatos()
        
        // Sincronización automática al abrir si hay WiFi
        if (isWifiAvailable()) {
            sincronizarTodo(showToast = false)
        }

        btnSync.setOnClickListener {
            if (isNetworkAvailable()) {
                Toast.makeText(this, "Actualizando Informacion", Toast.LENGTH_SHORT).show()
                sincronizarTodo(showToast = true)
            } else {
                Toast.makeText(this, "Sin conexión para actualizar", Toast.LENGTH_SHORT).show()
            }
        }

        autoCompleteSitios.onItemClickListener = AdapterView.OnItemClickListener { parent, _, position, _ ->
            val nombreSeleccionado = parent.getItemAtPosition(position) as String
            sitioSeleccionado = listaSitios.find { it.nombre == nombreSeleccionado }
            
            sitioSeleccionado?.let {
                txtInfo.text = """
                    ID SITIO: ${it.idSitio}
                    NOMBRE: ${it.nombre}
                    TEAM: ${it.teem}
                    VISITAS: ${it.visit}
                """.trimIndent()
                btnVerMapa.visibility = View.VISIBLE
            }
        }

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

        btnpasar.setOnClickListener {
            val sitio = sitioSeleccionado
            if (sitio == null) {
                Toast.makeText(this, "Selecciona un sitio primero", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                try {
                    val formularioExiste = withContext(Dispatchers.IO) { 
                        db.formularioDao().getById(1L) != null 
                    }

                    if (!formularioExiste) {
                        if (isNetworkAvailable()) {
                            Toast.makeText(this@MainActivity, "Descargando estructura...", Toast.LENGTH_SHORT).show()
                            sincronizarTodo(showToast = true)
                        } else {
                            Toast.makeText(this@MainActivity, "Falta estructura (requiere internet una vez)", Toast.LENGTH_LONG).show()
                        }
                        return@launch
                    }

                    val nuevaTareaId = System.currentTimeMillis()
                    val nuevaTarea = Tarea(
                        idTarea = nuevaTareaId,
                        idSitio = sitio.idSitio,
                        idFormulario = 1L,
                        tipoMantenimiento = TipoMantenimiento.PREVENTIVO,
                        fecha = Date(),
                        observacionesGenerales = "Inspección iniciada",
                        estado = EstadoTarea.EN_PROCESO
                    )

                    withContext(Dispatchers.IO) {
                        db.tareaDao().insert(nuevaTarea)
                    }

                    val intent = Intent(this@MainActivity, InspeccionActivity::class.java)
                    intent.putExtra("ID_TAREA", nuevaTareaId)
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e("DATABASE", "Error al crear tarea: ${e.message}")
                }
            }
        }
    }

    private fun isWifiAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return activeNetwork.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun observarDatos() {
        lifecycleScope.launch {
            db.sitioDao().getAllFlow().collectLatest { sitios ->
                listaSitios = sitios
                val nombres = sitios.map { it.nombre }
                withContext(Dispatchers.Main) {
                    val adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_dropdown_item_1line, nombres)
                    autoCompleteSitios.setAdapter(adapter)
                }
            }
        }
    }

    /**
     * ESTRATEGIA: BORRAR TODO Y REINSERTAR (Solo para catálogos maestros)
     */
    private fun sincronizarTodo(showToast: Boolean = false) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // 1. Obtener datos de la API
                    val resSitios = RetrofitClient.instance.getSitios()
                    val resForm = RetrofitClient.instance.getFormularioCompleto(1L)

                    // 2. Transacción Atómica de Sincronización
                    db.withTransaction {
                        // --- SINCRO DE SITIOS ---
                        if (resSitios.data.isNotEmpty()) {
                            db.sitioDao().deleteAll() // ESTRATEGIA: BORRAR TODO
                            db.sitioDao().insertAll(resSitios.data) // REINSERTAR
                        }

                        // --- SINCRO DE FORMULARIO (Jerarquía) ---
                        if (resForm.success) {
                            val data = resForm.data
                            
                            // Borramos jerarquía anterior (Secciones y Preguntas)
                            // Nota: Formulario se actualiza por REPLACE
                            db.seccionDao().deleteByFormulario(data.id) 

                            db.formularioDao().insert(Formulario(data.id, data.nombre, data.descripcion))
                            
                            data.secciones.forEach { s ->
                                db.seccionDao().insert(Seccion(s.id, data.id, s.nombre))
                                s.preguntas.forEach { p ->
                                    db.preguntaDao().insert(Pregunta(p.id, s.id, p.descripcion, p.minImagenes, p.maxImagenes))
                                }
                            }
                        }
                    }
                }
                if (showToast) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Sincronización Exitosa", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("DEBUG_SYNC", "Error en sincro: ${e.message}")
                if (showToast) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Error al sincronizar catálogos", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}
