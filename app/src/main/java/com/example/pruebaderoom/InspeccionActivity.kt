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
import androidx.room.withTransaction
import androidx.work.*
import com.example.pruebaderoom.data.AppDatabase
import com.example.pruebaderoom.data.ReporteWorker
import com.example.pruebaderoom.data.RetrofitClient
import com.example.pruebaderoom.data.entity.*
import com.example.pruebaderoom.data.SeccionApiData
import com.example.pruebaderoom.data.PreguntaApiData
import com.example.pruebaderoom.data.CampoApiData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class InspeccionActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var rvPreguntas: RecyclerView
    private lateinit var txtTituloFormulario: TextView
    private lateinit var btnEnviar: Button
    private var idTareaRecibido: Long = -1
    
    // Mantenemos una instancia única del adaptador
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
        db = AppDatabase.getDatabase(this)
        
        txtTituloFormulario = findViewById(R.id.txtTituloFormulario)
        rvPreguntas = findViewById(R.id.rvPreguntas)
        val btnVolver = findViewById<ImageButton>(R.id.btnVolverInicio)
        btnEnviar = findViewById(R.id.btnEnviarReporte)
        
        rvPreguntas.layoutManager = LinearLayoutManager(this)
        
        // Inicializamos el adaptador vacío al inicio
        inspeccionAdapter = InspeccionAdapter(mutableListOf(), idTareaRecibido, db)
        rvPreguntas.adapter = inspeccionAdapter
        
        btnVolver?.setOnClickListener { finish() }

        sincronizarFormulario(1L)

        btnEnviar.setOnClickListener {
            programarSincronizacionWorker()
        }
        
        actualizarEstadoBotonEnvio()
        observarProgresoEnvio()
    }

    private fun observarProgresoEnvio() {
        val uniqueWorkName = "sync_tarea_$idTareaRecibido"
        WorkManager.getInstance(this)
            .getWorkInfosForUniqueWorkLiveData(uniqueWorkName)
            .observe(this) { infos ->
                val info = infos?.firstOrNull() ?: return@observe
                
                when (info.state) {
                    WorkInfo.State.RUNNING -> {
                        val progress = info.progress.getInt("PROGRESS", 0)
                        val current = info.progress.getInt("CURRENT_IMG", 0)
                        val total = info.progress.getInt("TOTAL_IMG", 0)
                        val status = info.progress.getString("STATUS") ?: "SUBIENDO"

                        btnEnviar.isEnabled = false
                        btnEnviar.alpha = 0.7f
                        
                        if (status == "UPLOADING") {
                            btnEnviar.text = "SUBIENDO: $progress% (FOTO $current/$total)"
                        } else {
                            btnEnviar.text = "EN ESPERA DE RED..."
                        }
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        Toast.makeText(this, "¡Reporte enviado con éxito!", Toast.LENGTH_SHORT).show()
                        finish() 
                    }
                    WorkInfo.State.FAILED -> {
                        btnEnviar.isEnabled = true
                        btnEnviar.alpha = 1.0f
                        btnEnviar.text = "ERROR: REINTENTAR ENVÍO"
                    }
                    else -> {}
                }
            }
    }

    private fun programarSincronizacionWorker() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                db.tareaDao().getById(idTareaRecibido)?.let { tarea ->
                    db.tareaDao().insert(tarea.copy(estado = EstadoTarea.SUBIENDO))
                }
            }

            val data = Data.Builder()
                .putLong("ID_TAREA", idTareaRecibido)
                .build()

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncWorkRequest = OneTimeWorkRequestBuilder<ReporteWorker>()
                .setInputData(data)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, java.util.concurrent.TimeUnit.SECONDS)
                .addTag("ReporteWorker")
                .build()

            WorkManager.getInstance(this@InspeccionActivity).enqueueUniqueWork(
                "sync_tarea_$idTareaRecibido",
                ExistingWorkPolicy.KEEP,
                syncWorkRequest
            )
        }
    }

    private fun sincronizarFormulario(id: Long) {
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) { RetrofitClient.instance.getFormularioCompleto(id) }

                if (response.success) {
                    val formData = response.data
                    
                    db.withTransaction {
                        db.formularioDao().insert(Formulario(formData.id, formData.nombre, formData.descripcion))
                        db.seccionDao().deleteByFormulario(formData.id)
                        db.campoDao().deleteAll()

                        formData.secciones.forEach { seccionApi ->
                            db.seccionDao().insert(Seccion(seccionApi.id, formData.id, seccionApi.nombre))
                            
                            seccionApi.preguntas.forEach { preguntaApi -> 
                                db.preguntaDao().insert(Pregunta(
                                    preguntaApi.id, 
                                    seccionApi.id, 
                                    preguntaApi.descripcion, 
                                    preguntaApi.minImagenes,
                                    preguntaApi.maxImagenes
                                ))
                                
                                val camposDinamicos = preguntaApi.campos.map { campoApi ->
                                    Campo(campoApi.id, preguntaApi.id, campoApi.tipo, campoApi.label, campoApi.orden)
                                }
                                db.campoDao().insertAll(camposDinamicos)
                            }
                        }
                    }

                    cargarYMostrar()
                    actualizarEstadoBotonEnvio()
                }
            } catch (e: Exception) {
                Log.e("API", "Error al sincronizar formulario: ${e.message}")
                cargarYMostrar()
            }
        }
    }

    private fun actualizarEstadoBotonEnvio() {
        lifecycleScope.launch {
            val estaCompleto = withContext(Dispatchers.IO) {
                val preguntas = db.preguntaDao().getAll()
                if (preguntas.isEmpty()) return@withContext false
                
                preguntas.all { pregunta ->
                    val respuesta = db.respuestaDao().getAll().find { 
                        it.idPregunta == pregunta.idPregunta && it.idTarea == idTareaRecibido 
                    }
                    if (respuesta == null) return@all false
                    val cantImagenes = db.imagenDao().getByRespuesta(respuesta.idRespuesta).size
                    cantImagenes >= pregunta.minImagenes
                }
            }

            withContext(Dispatchers.Main) {
                btnEnviar.isEnabled = estaCompleto
                btnEnviar.alpha = if (estaCompleto) 1.0f else 0.5f
                btnEnviar.text = if (estaCompleto) "ENVIAR REPORTE" else "FALTAN FOTOS"
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
                val secciones = db.seccionDao().getAll()
                secciones.forEach { s ->
                    list.add(InspeccionItem.SeccionHeader(s))
                    val preguntas = db.preguntaDao().getBySeccion(s.idSeccion)
                    preguntas.forEach { p -> list.add(InspeccionItem.PreguntaItem(p)) }
                }
                list
            }
            
            // Actualizamos los datos del adaptador existente en lugar de crear uno nuevo
            inspeccionAdapter.updateData(hibrido)

            if (hibrido.isNotEmpty()) {
                val info = withContext(Dispatchers.IO) { db.formularioDao().getById(1L) }
                txtTituloFormulario.text = info?.nombre ?: "Inspección"
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
                holder.txtDesc.text = item.pregunta.descripcion
                holder.txtCant.text = "Mínimo: ${item.pregunta.minImagenes} fotos"
                
                (holder.itemView.context as InspeccionActivity).lifecycleScope.launch {
                    val respuesta = withContext(Dispatchers.IO) {
                        database.respuestaDao().getAll().find {
                            it.idPregunta == item.pregunta.idPregunta && it.idTarea == idTarea
                        }
                    }

                    when {
                        respuesta?.texto == "Finalizado" -> {
                            holder.txtDesc.setTextColor(Color.parseColor("#43A047"))
                            holder.btnCamara.text = "EDITAR"
                            holder.btnCamara.setTextColor(Color.parseColor("#43A047"))
                        }
                        respuesta?.texto == "En proceso" -> {
                            holder.txtDesc.setTextColor(Color.parseColor("#E53935"))
                            holder.btnCamara.text = "INCOMPLETA"
                            holder.btnCamara.setTextColor(Color.parseColor("#E53935"))
                        }
                        else -> {
                            holder.txtDesc.setTextColor(Color.parseColor("#333333"))
                            holder.btnCamara.text = "TOMAR FOTO"
                            holder.btnCamara.setTextColor(Color.parseColor("#1E2A44"))
                        }
                    }
                }

                holder.btnCamara.setOnClickListener {
                    val intent = Intent(holder.itemView.context, com.example.pruebaderoom.Respuesta::class.java)
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
