package com.example.pruebaderoom

import android.content.Intent
import android.os.Bundle
import android.util.Log
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

class MainActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var txtInfo: TextView
    private lateinit var autoCompleteSitios: AutoCompleteTextView
    private var listaSitios: List<Sitio> = emptyList()

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
        val btnpasar = findViewById<Button>(R.id.btnpasar)

        // 1. CARGA INICIAL: Mostramos lo que ya existe en la base de datos local
        actualizarInterfaz()

        // 2. SINCRONIZACIÓN: Buscamos nuevos datos en la API
        sincronizarSitios()

        autoCompleteSitios.onItemClickListener = AdapterView.OnItemClickListener { parent, _, position, _ ->
            val nombreSeleccionado = parent.getItemAtPosition(position) as String
            val sitioEncontrado = listaSitios.find { it.nombre == nombreSeleccionado }
            
            sitioEncontrado?.let {
                txtInfo.text = """
                    ID SITIO: ${it.idSitio}
                    NOMBRE: ${it.nombre}
                    TEEM: ${it.teem}
                    MORFOLOGÍA: ${it.siteMorfology}
                    NUEVA MORFOLOGÍA: ${it.newMorfology}
                """.trimIndent()
            }
        }

        btnpasar.setOnClickListener {
            val intent = Intent(this, Respuesta::class.java)
            startActivity(intent)
        }
    }

    private fun sincronizarSitios() {
        lifecycleScope.launch {
            try {
                // Llamada a la API
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.instance.getSitios()
                }
                
                val sitiosApi = response.data
                
                if (sitiosApi.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        val sitioDao = db.sitioDao()
                        // Actualizamos la base de datos local con los datos de la API
                        sitioDao.deleteAll()
                        sitiosApi.forEach { sitio ->
                            sitioDao.insert(sitio)
                        }
                    }
                    Log.d("API_SYNC", "Sincronización exitosa")
                    // Volvemos a actualizar la interfaz con los nuevos datos recibidos
                    actualizarInterfaz()
                }
            } catch (e: Exception) {
                Log.e("API_SYNC", "No se pudo sincronizar, usando datos locales: ${e.message}")
            }
        }
    }

    private fun actualizarInterfaz() {
        lifecycleScope.launch {
            // Obtenemos los sitios directamente de Room
            val sitiosLocal = withContext(Dispatchers.IO) {
                db.sitioDao().getAll()
            }
            listaSitios = sitiosLocal

            val nombres = listaSitios.map { it.nombre }
            val adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_dropdown_item_1line, nombres)
            autoCompleteSitios.setAdapter(adapter)
            autoCompleteSitios.threshold = 1
            
            // Si la lista local está vacía, mostramos un aviso
            if (listaSitios.isEmpty()) {
                Log.d("UI_UPDATE", "La base de datos local está vacía.")
            }
        }
    }
}
