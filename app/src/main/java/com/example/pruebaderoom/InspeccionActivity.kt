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
import com.example.pruebaderoom.data.AppDatabase
import com.example.pruebaderoom.data.ReporteManager
import com.example.pruebaderoom.data.RetrofitClient
import com.example.pruebaderoom.data.entity.Formulario
import com.example.pruebaderoom.data.entity.Pregunta
import com.example.pruebaderoom.data.entity.Seccion
import com.example.pruebaderoom.data.entity.Respuesta as RespuestaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class InspeccionActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var rvPreguntas: RecyclerView
    private lateinit var txtTituloFormulario: TextView
    private lateinit var btnEnviar: Button
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
        val btnVolver = findViewById<ImageButton>(R.id.btnVolverInicio)
        btnEnviar = findViewById(R.id.btnEnviarReporte)
        
        rvPreguntas.layoutManager = LinearLayoutManager(this)
        btnVolver?.setOnClickListener { finish() }

        sincronizarFormulario(1L)

        btnEnviar.setOnClickListener {
            enviarTodoAlServidor()
        }
        
        actualizarEstadoBotonEnvio()
    }

    private fun sincronizarFormulario(id: Long) {
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) { RetrofitClient.instance.getFormularioCompleto(id) }

                if (response.success) {
                    val data = response.data
                    
                    val seccionesApi = data.secciones.map { s -> Seccion(s.id, data.id, s.nombre) }
                    val idsSeccionesApi = seccionesApi.map { it.idSeccion }
                    
                    val preguntasApi = data.secciones.flatMap { s -> 
                        s.preguntas.map { p -> Pregunta(p.id, s.id, p.descripcion, p.minImagenes, p.maxImagenes) }
                    }
                    val idsPreguntasApi = preguntasApi.map { it.idPregunta }

                    db.withTransaction {
                        db.formularioDao().insert(Formulario(data.id, data.nombre, data.descripcion))
                        db.seccionDao().deleteOldSecciones(data.id, idsSeccionesApi)
                        db.preguntaDao().deleteOldPreguntas(data.id, idsPreguntasApi)
                        db.seccionDao().upsertAll(seccionesApi)
                        db.preguntaDao().upsertAll(preguntasApi)
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

    private fun enviarTodoAlServidor() {
        lifecycleScope.launch {
            try {
                val tarea = withContext(Dispatchers.IO) { db.tareaDao().getById(idTareaRecibido) }
                if (tarea == null) return@launch

                val respuestasConFotos = withContext(Dispatchers.IO) {
                    val lista = mutableListOf<Pair<ReporteManager.RespuestaJson, List<File>>>()
                    val respuestasDB = db.respuestaDao().getByTarea(idTareaRecibido)
                    
                    respuestasDB.forEach { r ->
                        val fotos = db.imagenDao().getByRespuesta(r.idRespuesta).map { File(it.rutaArchivo) }
                        val respJson = ReporteManager.RespuestaJson(
                            temp_id = "r${r.idRespuesta}",
                            pregunta_id = r.idPregunta,
                            texto_respuesta = if (r.texto == "Finalizado") "Completado correctamente" else r.texto
                        )
                        lista.add(Pair(respJson, fotos))
                    }
                    lista
                }

                if (respuestasConFotos.isEmpty()) {
                    Toast.makeText(this@InspeccionActivity, "No hay respuestas para enviar", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val manager = ReporteManager(RetrofitClient.instance)
                Toast.makeText(this@InspeccionActivity, "Enviando reporte...", Toast.LENGTH_SHORT).show()
                
                val exito = manager.enviarReporteCompleto(
                    sitioId = tarea.idSitio,
                    formularioId = tarea.idFormulario,
                    observaciones = tarea.observacionesGenerales,
                    respuestasConFotos = respuestasConFotos
                )

                if (exito) {
                    withContext(Dispatchers.IO) {
                        // 1. Borrar archivos físicos
                        respuestasConFotos.flatMap { it.second }.forEach { if (it.exists()) it.delete() }
                        
                        // 2. Limpiar base de datos
                        db.imagenDao().deleteByTarea(idTareaRecibido)
                        db.respuestaDao().deleteByTarea(idTareaRecibido)
                        db.tareaDao().delete(tarea)
                    }
                    
                    Toast.makeText(this@InspeccionActivity, "¡Reporte enviado y memoria liberada!", Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    Toast.makeText(this@InspeccionActivity, "Fallo el envío. Datos guardados en el equipo.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@InspeccionActivity, "Error al enviar: ${e.message}", Toast.LENGTH_LONG).show()
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
            rvPreguntas.adapter = InspeccionAdapter(hibrido, idTareaRecibido, db)

            if (hibrido.isNotEmpty()) {
                val info = withContext(Dispatchers.IO) { db.formularioDao().getById(1L) }
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
