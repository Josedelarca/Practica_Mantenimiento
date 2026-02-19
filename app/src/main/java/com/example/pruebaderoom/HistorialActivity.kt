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
                rvHistorial.adapter = HistorialAdapter(lista.sortedByDescending { it.fechaEnvio })
            }
        }
    }

    class HistorialAdapter(private val lista: List<HistorialEnvio>) : 
        RecyclerView.Adapter<HistorialAdapter.VH>() {
        
        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val txtSitio: TextView = v.findViewById(R.id.txtHistorialSitio)
            val txtForm: TextView = v.findViewById(R.id.txtHistorialForm)
            val txtHora: TextView = v.findViewById(R.id.txtHistorialHora)
            val txtFecha: TextView = v.findViewById(R.id.txtHistorialFecha)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            LayoutInflater.from(parent.context).inflate(R.layout.item_historial, parent, false)
        )

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = lista[position]
            
            val sdfHora = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            val sdfFecha = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            
            holder.txtSitio.text = item.sitioNombre
            holder.txtForm.text = item.formularioNombre
            holder.txtHora.text = sdfHora.format(item.fechaEnvio)
            holder.txtFecha.text = sdfFecha.format(item.fechaEnvio)
        }

        override fun getItemCount() = lista.size
    }
}
