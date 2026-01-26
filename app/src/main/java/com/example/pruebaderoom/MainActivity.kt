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

        actualizarInterfaz()
        sincronizarSitios()

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
                val nuevaTareaId = System.currentTimeMillis()
                
                // USAMOS SÓLO LOS CAMPOS QUE EXISTEN EN TU TABLA TAREA
                val nuevaTarea = Tarea(
                    idTarea = nuevaTareaId,
                    idSitio = sitio.idSitio,
                    idFormulario = 1L,
                    tipoMantenimiento = TipoMantenimiento.PREVENTIVO,
                    fecha = Date(),
                    observacionesGenerales = "Inspección iniciada desde la App",
                    estado = EstadoTarea.EN_PROCESO // Usamos el nuevo estado solicitado
                )
                
                withContext(Dispatchers.IO) {
                    db.tareaDao().insert(nuevaTarea)
                }

                val intent = Intent(this@MainActivity, InspeccionActivity::class.java)
                intent.putExtra("ID_TAREA", nuevaTareaId)
                startActivity(intent)
            }
        }
    }

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
                    actualizarInterfaz()
                }
            } catch (e: Exception) {
                Log.e("API", "Error: ${e.message}")
            }
        }
    }

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
