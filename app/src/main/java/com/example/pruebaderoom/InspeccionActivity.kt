package com.example.pruebaderoom

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
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
import androidx.work.*
import com.example.pruebaderoom.data.AppDatabase
import com.example.pruebaderoom.data.ReporteWorker
import com.example.pruebaderoom.data.entity.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class InspeccionActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var rvPreguntas: RecyclerView
    private lateinit var txtTituloFormulario: TextView
    private lateinit var btnEnviar: Button
    private var idTareaRecibido: Long = -1
    private var zonaElegida: String = "ambos"
    
    private lateinit var inspeccionAdapter: InspeccionAdapter

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
        zonaElegida = intent.getStringExtra("ZONA_ELEGIDA") ?: "ambos"
        
        db = AppDatabase.getDatabase(this)
        
        txtTituloFormulario = findViewById(R.id.txtTituloFormulario)
        rvPreguntas = findViewById(R.id.rvPreguntas)
        val btnVolver = findViewById<ImageButton>(R.id.btnVolverInicio)
        btnEnviar = findViewById(R.id.btnEnviarReporte)
        
        rvPreguntas.layoutManager = LinearLayoutManager(this)
        
        inspeccionAdapter = InspeccionAdapter(mutableListOf(), idTareaRecibido, db)
        rvPreguntas.adapter = inspeccionAdapter
        
        btnVolver?.setOnClickListener { finish() }

        btnEnviar.setOnClickListener {
            enviarYSalir()
        }
        
        actualizarEstadoBotonEnvio()
        cargarYMostrar()
    }

    private fun enviarYSalir() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val tarea = db.tareaDao().getById(idTareaRecibido)
                tarea?.let {
                    db.seccionDao().updateEnviandoPorZona(it.idFormulario, zonaElegida, true)
                    db.tareaDao().insert(it.copy(estado = EstadoTarea.SUBIENDO))
                }
            }

            val data = Data.Builder()
                .putLong("ID_TAREA", idTareaRecibido)
                .putString("ZONA_TRABAJADA", zonaElegida)
                .build()

            // IMPORTANTE: Nombre único incluyendo la zona para evitar colisiones offline
            val workName = "sync_${idTareaRecibido}_${zonaElegida}"

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncWorkRequest = OneTimeWorkRequestBuilder<ReporteWorker>()
                .setInputData(data)
                .setConstraints(constraints)
                .addTag("ReporteWorker")
                .build()

            WorkManager.getInstance(this@InspeccionActivity).enqueueUniqueWork(
                workName,
                ExistingWorkPolicy.REPLACE, // Reemplazamos para asegurar que los últimos datos offline sean los que se envíen
                syncWorkRequest
            )

            Toast.makeText(this@InspeccionActivity, "Envío programado para zona $zonaElegida", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun actualizarEstadoBotonEnvio() {
        lifecycleScope.launch {
            val tieneAlgoQueEnviar = withContext(Dispatchers.IO) {
                val respuestas = db.respuestaDao().getByTarea(idTareaRecibido)
                val tieneImagenesPendientes = respuestas.any { r ->
                    db.imagenDao().getByRespuesta(r.idRespuesta).any { !it.isSynced }
                }
                respuestas.isNotEmpty() || tieneImagenesPendientes
            }

            withContext(Dispatchers.Main) {
                btnEnviar.isEnabled = tieneAlgoQueEnviar
                btnEnviar.alpha = if (tieneAlgoQueEnviar) 1.0f else 0.5f
                btnEnviar.text = if (tieneAlgoQueEnviar) "ENVIAR PARTE (${zonaElegida.uppercase()})" else "SIN CAMBIOS"
            }
        }
    }

    override fun onResume() {
        super.onResume()
        cargarYMostrar()
        actualizarEstadoBotonEnvio()
    }

    private fun cargarYMostrar() {
        lifecycleScope.launch {
            val hibrido = withContext(Dispatchers.IO) {
                val list = mutableListOf<InspeccionItem>()
                val tarea = db.tareaDao().getById(idTareaRecibido) ?: return@withContext list

                val secciones = db.seccionDao().getByFormulario(tarea.idFormulario)
                    .filter { it.zona == zonaElegida || it.zona == "ambos" }
                
                secciones.forEach { s ->
                    list.add(InspeccionItem.SeccionHeader(s))
                    val preguntas = db.preguntaDao().getBySeccion(s.idSeccion)
                    preguntas.forEach { p -> list.add(InspeccionItem.PreguntaItem(p)) }
                }
                list
            }
            
            inspeccionAdapter.updateData(hibrido)

            withContext(Dispatchers.IO) {
                val tarea = db.tareaDao().getById(idTareaRecibido)
                val formInfo = tarea?.let { db.formularioDao().getById(it.idFormulario) }
                withContext(Dispatchers.Main) {
                    txtTituloFormulario.text = "${formInfo?.nombre ?: "Inspección"} ($zonaElegida)"
                }
            }
        }
    }

    class InspeccionAdapter(
        private val items: MutableList<InspeccionItem>,
        private val idTarea: Long,
        private val database: AppDatabase
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        
        fun updateData(newItems: List<InspeccionItem>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

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
                val pregunta = item.pregunta
                holder.txtDesc.text = pregunta.descripcion
                
                (holder.itemView.context as InspeccionActivity).lifecycleScope.launch {
                    val (respuesta, cantFotos) = withContext(Dispatchers.IO) {
                        val resp = database.respuestaDao().getAll().find {
                            it.idPregunta == pregunta.idPregunta && it.idTarea == idTarea
                        }
                        val count = resp?.let { database.imagenDao().getByRespuesta(it.idRespuesta).size } ?: 0
                        Pair(resp, count)
                    }

                    holder.txtCant.text = "Fotos: $cantFotos / Mínimo: ${pregunta.minImagenes}"
                    
                    val colorTexto = if (cantFotos >= pregunta.minImagenes && cantFotos > 0) "#43A047" else "#757575"
                    holder.txtCant.setTextColor(Color.parseColor(colorTexto))

                    if (cantFotos > 0) {
                        holder.btnCamara.text = "EDITAR"
                        holder.btnCamara.setTextColor(Color.parseColor("#43A047"))
                    } else {
                        holder.btnCamara.text = "TOMAR EVIDENCIA"
                        holder.btnCamara.setTextColor(Color.parseColor("#1E2A44"))
                    }
                }

                holder.btnCamara.setOnClickListener {
                    val intent = Intent(holder.itemView.context, com.example.pruebaderoom.Respuesta::class.java)
                    intent.putExtra("ID_PREGUNTA", pregunta.idPregunta)
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
