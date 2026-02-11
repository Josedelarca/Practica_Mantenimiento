package com.example.pruebaderoom

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.pruebaderoom.data.AppDatabase
import com.example.pruebaderoom.data.entity.HistorialEnvio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class HistorialActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var rvHistorial: RecyclerView
    private lateinit var txtVacio: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_historial)

        db = AppDatabase.getDatabase(this)
        rvHistorial = findViewById(R.id.rvHistorial)
        txtVacio = findViewById(R.id.txtVacio)
        
        rvHistorial.layoutManager = LinearLayoutManager(this)
        
        findViewById<View>(R.id.btnVolverHistorial).setOnClickListener { finish() }
        
        // BOTÓN PARA BORRAR TODO EL HISTORIAL
        findViewById<View>(R.id.btnBorrarHistorial).setOnClickListener {
            mostrarDialogoBorrar()
        }

        cargarHistorial()
    }

    private fun mostrarDialogoBorrar() {
        AlertDialog.Builder(this)
            .setTitle("Limpiar Historial")
            .setMessage("¿Estás seguro de que quieres borrar todos los registros de envío?")
            .setPositiveButton("BORRAR TODO") { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        db.historialEnvioDao().deleteAll()
                    }
                    Toast.makeText(this@HistorialActivity, "Historial limpio", Toast.LENGTH_SHORT).show()
                    cargarHistorial()
                }
            }
            .setNegativeButton("CANCELAR", null)
            .show()
    }

    private fun cargarHistorial() {
        lifecycleScope.launch {
            val lista = withContext(Dispatchers.IO) {
                db.historialEnvioDao().getAll()
            }

            if (lista.isEmpty()) {
                txtVacio.visibility = View.VISIBLE
                rvHistorial.visibility = View.GONE
            } else {
                txtVacio.visibility = View.GONE
                rvHistorial.visibility = View.VISIBLE
                rvHistorial.adapter = HistorialAdapter(lista)
            }
        }
    }

    class HistorialAdapter(private val lista: List<HistorialEnvio>) : 
        RecyclerView.Adapter<HistorialAdapter.VH>() {
        
        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val t1: TextView = v.findViewById(android.R.id.text1)
            val t2: TextView = v.findViewById(android.R.id.text2)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_2, parent, false)
        )

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = lista[position]
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
            
            holder.t1.text = "SITIO: ${item.sitioNombre}"
            holder.t2.text = "Enviado: ${sdf.format(item.fechaEnvio)}\nFormulario: ${item.formularioNombre}"
            
            holder.t1.setTextColor(android.graphics.Color.parseColor("#1E2A44"))
            holder.t1.textSize = 16f
            holder.t1.setTypeface(null, android.graphics.Typeface.BOLD)
        }

        override fun getItemCount() = lista.size
    }
}
