package com.example.pruebaderoom

import android.content.Intent
import android.os.Bundle
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.pruebaderoom.data.AppDatabase
import com.example.pruebaderoom.data.entity.Sitio
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var txtInfo: TextView
    private lateinit var autoCompleteSitios: AutoCompleteTextView
    private var listaSitios: List<Sitio> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Inicializamos la conexión a la base de datos
        db = AppDatabase.getDatabase(this)
        
        // Vinculamos los elementos visuales del XML con el código
        txtInfo = findViewById(R.id.txtInfo)
        autoCompleteSitios = findViewById(R.id.autoCompleteCiudades)
        val btnpasar = findViewById<Button>(R.id.btnpasar)

        // Usamos una corrutina para cargar los datos sin trabar la pantalla
        lifecycleScope.launch {
            val sitioDao = db.sitioDao()
            
            // Intentamos obtener los sitios de la base de datos
            listaSitios = sitioDao.getAll()

            // Si la base de datos está vacía (es la primera vez), inyectamos los datos reales
            if (listaSitios.isEmpty()) {
                val sitiosIniciales = listOf(
                    Sitio("SPS06CL5436", "ACEYDESA_COLON", "TOCOA", "NORMAL", "NORMAL", 1),
                    Sitio("GLS", "AEROPUERTO_GOLOSON", "CBA1", "UVIP", "UVIP", 1),
                    Sitio("SPS04IS5804", "AEROPUERTO_ROATAN", "ROATAN", "UVIP", "NORMAL", 1),
                    Sitio("SPS06CL5435", "AGROPALMA_COLON_MICRO", "TOCOA", "NORMAL", "NORMAL", 1),
                    Sitio("SPS06CL5437", "AGUA_AMARILLA", "COROCITO", "HUB", "NORMAL", 1),
                    Sitio("SPS06GD5901", "AHUAS_GD", "GAD", "HUB", "UVIP", 1),
                    Sitio("SPS06AT5307", "ALDEA_EL_NARANJAL_ATLANTIDA", "CBA1", "NORMAL", "REGULAR", 1),
                    Sitio("SPS06CL5472", "ALDEA_EL_REMOLINO", "TOCOA", "NORMAL", "NORMAL", 1),
                    Sitio("SPS06CB5015", "ALTIPLANO", "CBA2", "NORMAL", "NORMAL", 1),
                    Sitio("SPS06YO5732", "ARENAL", "SABA", "NORMAL", "NORMAL", 1)
                )
                // Guardamos cada sitio en la base de datos
                sitiosIniciales.forEach { sitioDao.insert(it) }
                // Volvemos a consultar para tener la lista actualizada
                listaSitios = sitioDao.getAll()
            }

            // Sacamos solo los nombres para que el buscador los muestre
            val nombres = listaSitios.map { it.nombre }
            
            // Configuramos el adaptador del buscador
            val adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_dropdown_item_1line, nombres)
            autoCompleteSitios.setAdapter(adapter)
            
            // Hacemos que sugiera nombres desde que escribes la primera letra
            autoCompleteSitios.threshold = 1
        }

        // Qué pasa cuando el usuario selecciona un sitio de la lista
        autoCompleteSitios.onItemClickListener = AdapterView.OnItemClickListener { parent, _, position, _ ->
            val nombreSeleccionado = parent.getItemAtPosition(position) as String
            // Buscamos el sitio completo que coincide con ese nombre
            val sitioEncontrado = listaSitios.find { it.nombre == nombreSeleccionado }
            
            sitioEncontrado?.let {
                // Mostramos toda la información detallada en el texto superior
                txtInfo.text = """
                    ID SITIO: ${it.idSitio}
                    NOMBRE: ${it.nombre}
                    TEEM: ${it.teem}
                    MORFOLOGÍA: ${it.siteMorfology}
                    NUEVA MORFOLOGÍA: ${it.newMorfology}
                """.trimIndent()
            }
        }

        // Al presionar el botón, vamos a la pantalla de respuesta
        btnpasar.setOnClickListener {
            val intent = Intent(this, Respuesta::class.java)
            startActivity(intent)
        }
    }
}
