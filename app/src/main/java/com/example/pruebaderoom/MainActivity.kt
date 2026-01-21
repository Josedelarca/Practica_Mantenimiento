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

        // Inicializar vistas
        db = AppDatabase.getDatabase(this)
        txtInfo = findViewById(R.id.txtInfo)
        autoCompleteSitios = findViewById(R.id.autoCompleteCiudades)
        val btnpasar = findViewById<Button>(R.id.btnpasar)

        // Cargar nombres de sitios desde la base de datos Room
        lifecycleScope.launch {
            val sitioDao = db.sitioDao()
            listaSitios = sitioDao.getAll()

            if (listaSitios.isNotEmpty()) {
                // Solo obtenemos los nombres de los sitios
                val nombres = listaSitios.map { it.nombre }
                
                val adapter = ArrayAdapter(
                    this@MainActivity, 
                    android.R.layout.simple_dropdown_item_1line, 
                    nombres
                )
                autoCompleteSitios.setAdapter(adapter)
            }
        }

        // Mostrar información al seleccionar un nombre del buscador
        autoCompleteSitios.onItemClickListener = AdapterView.OnItemClickListener { parent, _, position, _ ->
            val nombreSeleccionado = parent.getItemAtPosition(position) as String
            val sitioEncontrado = listaSitios.find { it.nombre == nombreSeleccionado }
            
            sitioEncontrado?.let {
                txtInfo.text = """
                    ID SITIO: ${it.idSitio}
                    NOMBRE: ${it.nombre}
                    TEEM: ${it.teem}
                    MORFOLOGÍA: ${it.siteMorfology}
                """.trimIndent()
            }
        }

        btnpasar.setOnClickListener {
            val intent = Intent(this, Respuesta::class.java)
            startActivity(intent)
        }
    }
}
