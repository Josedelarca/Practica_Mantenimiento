package com.example.pruebaderoom

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.pruebaderoom.data.AppDatabase
import com.example.pruebaderoom.data.FormularioApiShort
import com.example.pruebaderoom.data.RetrofitClient
import com.example.pruebaderoom.data.entity.*
import com.example.pruebaderoom.data.SeccionApiData
import com.example.pruebaderoom.data.PreguntaApiData
import com.example.pruebaderoom.data.CampoApiData
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class SeleccionFormularioActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var rvFormularios: RecyclerView
    private lateinit var progressBar: ProgressBar
    private var idSitioRecibido: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_seleccion_formulario)

        idSitioRecibido = intent.getLongExtra("ID_SITIO", -1)
        db = AppDatabase.getDatabase(this)
        
        rvFormularios = findViewById(R.id.rvFormularios)
        progressBar = findViewById(R.id.pbLoadingForms)
        
        rvFormularios.layoutManager = LinearLayoutManager(this)

        findViewById<View>(R.id.btnVolverAtras)?.setOnClickListener { finish() }

        cargarListaFormularios()
    }

    private fun cargarListaFormularios() {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                // 1. Cargar local primero para rapidez
                val listaLocal = withContext(Dispatchers.IO) {
                    db.formularioDao().getAll().map { FormularioApiShort(it.idFormulario, it.nombre, it.descripcion) }
                }
                if (listaLocal.isNotEmpty()) {
                    mostrarLista(listaLocal)
                    progressBar.visibility = View.GONE
                }

                // 2. Intentar actualizar desde la API
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.instance.getListaFormularios()
                }
                
                val listaApi = response.data // Accedemos al array dentro de "data"
                
                withContext(Dispatchers.IO) {
                    listaApi.forEach { 
                        db.formularioDao().insert(Formulario(it.id, it.nombre, it.descripcion))
                    }
                }
                mostrarLista(listaApi)
            } catch (e: Exception) {
                Log.e("FORM_LOAD", "Error cargando lista: ${e.message}")
                if (rvFormularios.adapter == null) {
                    Toast.makeText(this@SeleccionFormularioActivity, "Error de conexión", Toast.LENGTH_SHORT).show()
                }
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun mostrarLista(lista: List<FormularioApiShort>) {
        rvFormularios.adapter = FormularioAdapter(lista) { formulario ->
            verificarYEntrar(formulario.id)
        }
    }

    private fun verificarYEntrar(idFormulario: Long) {
        lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE
            // Verificamos si ya tenemos la estructura guardada localmente
            val tieneEstructura = withContext(Dispatchers.IO) {
                db.seccionDao().getByFormulario(idFormulario).isNotEmpty()
            }

            if (tieneEstructura) {
                manejarTareaYEntrar(idFormulario)
            } else {
                descargarEstructuraYEntrar(idFormulario)
            }
        }
    }

    private suspend fun descargarEstructuraYEntrar(idFormulario: Long) {
        try {
            val res = withContext(Dispatchers.IO) { RetrofitClient.instance.getFormularioCompleto(idFormulario) }
            if (res.success) {
                val data = res.data
                withContext(Dispatchers.IO) {
                    db.seccionDao().deleteByFormulario(data.id)
                    db.formularioDao().insert(Formulario(data.id, data.nombre, data.descripcion))
                    
                    data.secciones.forEach { s: SeccionApiData ->
                        db.seccionDao().insert(Seccion(s.id, data.id, s.nombre))
                        s.preguntas.forEach { p: PreguntaApiData ->
                            // CORRECCIÓN: Nombres de propiedades minImagenes y maxImagenes
                            db.preguntaDao().insert(Pregunta(p.id, s.id, p.descripcion, p.minImagenes, p.maxImagenes))
                            
                            val campos = p.campos.map { Campo(it.id, p.id, it.tipo, it.label, it.orden) }
                            db.campoDao().insertAll(campos)
                        }
                    }
                }
                manejarTareaYEntrar(idFormulario)
            }
        } catch (e: Exception) {
            Log.e("FORM_SYNC", "Error descargando estructura: ${e.message}")
            withContext(Dispatchers.Main) {
                Toast.makeText(this@SeleccionFormularioActivity, "No se pudo descargar el formulario", Toast.LENGTH_SHORT).show()
                progressBar.visibility = View.GONE
            }
        }
    }

    private suspend fun manejarTareaYEntrar(idFormulario: Long) {
        val tareaExistente = withContext(Dispatchers.IO) {
            db.tareaDao().getTareaActivaPorSitio(idSitioRecibido)
        }

        if (tareaExistente != null && tareaExistente.idFormulario == idFormulario) {
            irAInspeccion(tareaExistente.idTarea)
        } else {
            val nuevaId = System.currentTimeMillis()
            val nueva = Tarea(nuevaId, idSitioRecibido, idFormulario, TipoMantenimiento.PREVENTIVO, Date(), "En curso", EstadoTarea.EN_PROCESO)
            withContext(Dispatchers.IO) { db.tareaDao().insert(nueva) }
            irAInspeccion(nuevaId)
        }
    }

    private fun irAInspeccion(idTarea: Long) {
        startActivity(Intent(this, InspeccionActivity::class.java).putExtra("ID_TAREA", idTarea))
        // No hacemos finish() para permitir regresar y cambiar de formulario si se desea
    }

    class FormularioAdapter(
        private val lista: List<FormularioApiShort>,
        private val onClick: (FormularioApiShort) -> Unit
    ) : RecyclerView.Adapter<FormularioAdapter.VH>() {
        
        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val txtNombre: TextView = v.findViewById(R.id.txtNombreForm)
            val txtDesc: TextView = v.findViewById(R.id.txtDescForm)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            LayoutInflater.from(parent.context).inflate(R.layout.item_formulario, parent, false)
        )

        override fun onBindViewHolder(holder: VH, position: Int) {
            val f = lista[position]
            holder.txtNombre.text = f.nombre
            holder.txtDesc.text = f.descripcion
            holder.itemView.setOnClickListener { onClick(f) }
        }

        override fun getItemCount() = lista.size
    }
}
