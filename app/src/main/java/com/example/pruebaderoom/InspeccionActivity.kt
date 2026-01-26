package com.example.pruebaderoom

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.pruebaderoom.data.AppDatabase
import com.example.pruebaderoom.data.RetrofitClient
import com.example.pruebaderoom.data.entity.Formulario
import com.example.pruebaderoom.data.entity.Pregunta
import com.example.pruebaderoom.data.entity.Seccion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Pantalla que muestra el formulario con colores inteligentes:
 * - Negro: No empezada.
 * - Rojo: Empezada (con fotos) pero no finalizada.
 * - Verde: Finalizada correctamente (cuando pulsó GUARDAR TODO).
 */
class InspeccionActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var rvPreguntas: RecyclerView
    private lateinit var txtTituloFormulario: TextView
    private var btnVolver: ImageButton? = null
    private var idTareaRecibido: Long = -1

    sealed class InspeccionItem {
        data class SeccionHeader(val seccion: Seccion) : InspeccionItem()
        data class PreguntaItem(val pregunta: Pregunta) : InspeccionItem()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_inspeccion)

        idTareaRecibido = intent.getLongExtra("ID_TAREA", -1L)
        db = AppDatabase.getDatabase(this)
        txtTituloFormulario = findViewById(R.id.txtTituloFormulario)
        rvPreguntas = findViewById(R.id.rvPreguntas)
        btnVolver = findViewById(R.id.btnVolverInicio)
        
        rvPreguntas.layoutManager = LinearLayoutManager(this)
        btnVolver?.setOnClickListener { finish() }

        sincronizarFormulario(1L)
    }

    override fun onResume() {
        super.onResume()
        // Refrescamos la lista al volver para que los colores se actualicen
        cargarYMostrar()
    }

    private fun sincronizarFormulario(id: Long) {
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) { RetrofitClient.instance.getFormularioCompleto(id) }
                if (response.success) {
                    withContext(Dispatchers.IO) {
                        val data = response.data
                        db.formularioDao().insert(Formulario(data.id, data.nombre, data.descripcion))
                        data.secciones.forEach { s ->
                            db.seccionDao().insert(Seccion(s.id, data.id, s.nombre))
                            s.preguntas.forEach { p ->
                                db.preguntaDao().insert(Pregunta(p.id, s.id, p.descripcion, p.minImagenes, p.maxImagenes))
                            }
                        }
                    }
                    cargarYMostrar()
                }
            } catch (e: Exception) {
                cargarYMostrar()
            }
        }
    }

    private fun cargarYMostrar() {
        lifecycleScope.launch {
            val hibrido = withContext(Dispatchers.IO) {
                val list = mutableListOf<InspeccionItem>()
                val secciones = db.seccionDao().getAll()
                secciones.forEach { s ->
                    list.add(InspeccionItem.SeccionHeader(s))
                    val preguntas = db.preguntaDao().getBySeccion(s.idSeccion)
                    preguntas.forEach { p -> list.add(InspeccionItem.PreguntaItem(p)) }
                }
                list
            }
            rvPreguntas.adapter = InspeccionAdapter(hibrido, idTareaRecibido, db)
            
            if (hibrido.isNotEmpty()) {
                val info = withContext(Dispatchers.IO) { db.formularioDao().getAll().firstOrNull() }
                txtTituloFormulario.text = info?.nombre ?: "Inspección"
            }
        }
    }

    class InspeccionAdapter(
        private val items: List<InspeccionItem>,
        private val idTarea: Long,
        private val database: AppDatabase
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        
        override fun getItemViewType(position: Int) = if (items[position] is InspeccionItem.SeccionHeader) 0 else 1
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == 0) {
                SeccionViewHolder(inflater.inflate(R.layout.item_seccion_header, parent, false))
            } else {
                PreguntaViewHolder(inflater.inflate(R.layout.item_pregunta, parent, false))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = items[position]
            if (holder is SeccionViewHolder && item is InspeccionItem.SeccionHeader) {
                holder.txtNombre.text = item.seccion.nombre
            } else if (holder is PreguntaViewHolder && item is InspeccionItem.PreguntaItem) {
                holder.txtDesc.text = item.pregunta.descripcion
                holder.txtCant.text = "Mínimo: ${item.pregunta.minImagenes} fotos"
                
                // LÓGICA DE COLORES BASADA EN EL BOTÓN "GUARDAR TODO"
                (holder.itemView.context as InspeccionActivity).lifecycleScope.launch {
                    val respuesta = withContext(Dispatchers.IO) {
                        database.respuestaDao().getAll().find { 
                            it.idPregunta == item.pregunta.idPregunta && it.idTarea == idTarea 
                        }
                    }

                    when {
                        // CASO 1: YA SE PULSÓ EL BOTÓN "GUARDAR TODO" -> VERDE
                        respuesta?.texto == "Finalizado" -> {
                            holder.txtDesc.setTextColor(Color.parseColor("#43A047")) 
                            holder.btnCamara.text = "EDITAR"
                            holder.btnCamara.setTextColor(Color.parseColor("#43A047"))
                        }
                        // CASO 2: TIENE FOTOS PERO NO HA FINALIZADO -> ROJO
                        respuesta?.texto == "En proceso" -> {
                            holder.txtDesc.setTextColor(Color.parseColor("#E53935")) 
                            holder.btnCamara.text = "INCOMPLETA"
                            holder.btnCamara.setTextColor(Color.parseColor("#E53935"))
                        }
                        // CASO 3: NO SE HA EMPEZADO -> NEGRO
                        else -> {
                            holder.txtDesc.setTextColor(Color.parseColor("#333333"))
                            holder.btnCamara.text = "TOMAR FOTO"
                            holder.btnCamara.setTextColor(Color.parseColor("#1E2A44"))
                        }
                    }
                }

                holder.btnCamara.setOnClickListener {
                    val intent = Intent(holder.itemView.context, Respuesta::class.java)
                    intent.putExtra("ID_PREGUNTA", item.pregunta.idPregunta)
                    intent.putExtra("ID_TAREA", idTarea) 
                    holder.itemView.context.startActivity(intent)
                }
            }
        }

        override fun getItemCount() = items.size

        class SeccionViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val txtNombre: TextView = v.findViewById(R.id.txtSeccionNombre)
        }
        class PreguntaViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val txtDesc: TextView = v.findViewById(R.id.txtPreguntaDescripcion)
            val txtCant: TextView = v.findViewById(R.id.txtPreguntaTipo)
            val btnCamara: Button = v.findViewById(R.id.btnAccionPregunta)
        }
    }
}
