package com.example.pruebaderoom

import android.content.Intent
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

class InspeccionActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var rvPreguntas: RecyclerView
    private lateinit var txtTituloFormulario: TextView
    private lateinit var btnVolverInicio: ImageButton

    // Clase sellada para representar los dos tipos de filas en nuestra lista
    sealed class InspeccionItem {
        data class SeccionHeader(val seccion: Seccion) : InspeccionItem() // Fila de Título de Sección
        data class PreguntaItem(val pregunta: Pregunta) : InspeccionItem() // Fila de Pregunta individual
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_inspeccion)

        db = AppDatabase.getDatabase(this)
        txtTituloFormulario = findViewById(R.id.txtTituloFormulario)
        rvPreguntas = findViewById(R.id.rvPreguntas)
        btnVolverInicio = findViewById(R.id.btnVolverInicio)
        
        rvPreguntas.layoutManager = LinearLayoutManager(this)

        // Acción para regresar a la pantalla principal
        btnVolverInicio.setOnClickListener {
            finish()
        }

        // Cargamos los datos del formulario 1
        sincronizarFormulario(1L)
    }

    private fun sincronizarFormulario(idFormulario: Long) {
        lifecycleScope.launch {
            try {
                // Descargamos el formulario detallado de la API
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.instance.getFormularioCompleto(idFormulario)
                }

                if (response.success) {
                    val data = response.data
                    txtTituloFormulario.text = data.nombre

                    // Guardamos todo en Room (Formulario, Secciones y Preguntas)
                    withContext(Dispatchers.IO) {
                        db.formularioDao().insert(Formulario(data.id, data.nombre, data.descripcion))
                        data.secciones.forEach { secApi ->
                            // Insertamos la sección
                            db.seccionDao().insert(Seccion(secApi.id, data.id, secApi.nombre))
                            
                            // Insertamos sus preguntas
                            secApi.preguntas.forEach { pregApi ->
                                db.preguntaDao().insert(Pregunta(
                                    pregApi.id, secApi.id, pregApi.descripcion, pregApi.minImagenes, pregApi.maxImagenes
                                ))
                            }
                        }
                    }
                    // Después de guardar, cargamos la lista combinada
                    cargarDatosCombinados()
                }
            } catch (e: Exception) {
                Log.e("INSPECCION", "Error al sincronizar: ${e.message}")
                // Si no hay internet, cargamos lo que ya esté guardado localmente
                cargarDatosCombinados()
            }
        }
    }

    // Esta función organiza las secciones y preguntas en una sola lista para el adaptador
    private fun cargarDatosCombinados() {
        lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) {
                val listaHibrida = mutableListOf<InspeccionItem>()
                
                // 1. Obtenemos todas las secciones de la base de datos
                val secciones = db.seccionDao().getAll()
                
                secciones.forEach { seccion ->
                    // 2. Por cada sección, agregamos una "Cabecera" a la lista
                    listaHibrida.add(InspeccionItem.SeccionHeader(seccion))
                    
                    // 3. Buscamos las preguntas que pertenecen a esta sección
                    val preguntas = db.preguntaDao().getBySeccion(seccion.idSeccion)
                    preguntas.forEach { pregunta ->
                        // 4. Agregamos cada pregunta debajo de su cabecera
                        listaHibrida.add(InspeccionItem.PreguntaItem(pregunta))
                    }
                }
                listaHibrida
            }
            // Enviamos la lista final (Cabeceras + Preguntas) al adaptador
            rvPreguntas.adapter = InspeccionAdapter(items)
        }
    }

    // Adaptador que maneja múltiples tipos de vista (Sección vs Pregunta)
    class InspeccionAdapter(private val items: List<InspeccionItem>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        companion object {
            private const val TYPE_SECTION = 0 // Identificador para Secciones
            private const val TYPE_QUESTION = 1 // Identificador para Preguntas
        }

        // Determina qué tipo de diseño usar según el elemento en esa posición
        override fun getItemViewType(position: Int): Int {
            return when (items[position]) {
                is InspeccionItem.SeccionHeader -> TYPE_SECTION
                is InspeccionItem.PreguntaItem -> TYPE_QUESTION
            }
        }

        // Infla el XML correcto según el tipo (item_seccion_header o item_pregunta)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == TYPE_SECTION) {
                val v = LayoutInflater.from(parent.context).inflate(R.layout.item_seccion_header, parent, false)
                SeccionViewHolder(v)
            } else {
                val v = LayoutInflater.from(parent.context).inflate(R.layout.item_pregunta, parent, false)
                PreguntaViewHolder(v)
            }
        }

        // Pone los datos reales en los campos de texto
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = items[position]
            
            // Si la fila es una SECCIÓN
            if (holder is SeccionViewHolder && item is InspeccionItem.SeccionHeader) {
                holder.txtNombre.text = item.seccion.nombre
            } 
            // Si la fila es una PREGUNTA
            else if (holder is PreguntaViewHolder && item is InspeccionItem.PreguntaItem) {
                holder.txtDesc.text = item.pregunta.descripcion
                holder.txtTipo.text = "Fotos: Mín ${item.pregunta.minImagenes} / Máx ${item.pregunta.maxImagenes}"
                
                // Botón para ir a tomar fotos
                holder.btnAccion.setOnClickListener {
                    val intent = Intent(holder.itemView.context, Respuesta::class.java)
                    intent.putExtra("ID_PREGUNTA", item.pregunta.idPregunta)
                    holder.itemView.context.startActivity(intent)
                }
            }
        }

        override fun getItemCount() = items.size

        // Sujeta la vista de la sección
        class SeccionViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val txtNombre: TextView = v.findViewById(R.id.txtSeccionNombre)
        }

        // Sujeta la vista de la pregunta
        class PreguntaViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val txtDesc: TextView = v.findViewById(R.id.txtPreguntaDescripcion)
            val txtTipo: TextView = v.findViewById(R.id.txtPreguntaTipo)
            val btnAccion: Button = v.findViewById(R.id.btnAccionPregunta)
        }
    }
}
