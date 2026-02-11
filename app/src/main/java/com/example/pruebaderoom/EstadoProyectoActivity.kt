package com.example.pruebaderoom

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.*
import com.example.pruebaderoom.data.AppDatabase
import com.example.pruebaderoom.data.ReporteWorker
import com.example.pruebaderoom.data.entity.EstadoTarea
import com.example.pruebaderoom.data.entity.Tarea
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*

class EstadoProyectoActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var rvEstado: RecyclerView
    private lateinit var txtNoActivas: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_estado_proyecto)

        db = AppDatabase.getDatabase(this)
        rvEstado = findViewById(R.id.rvEstadoTareas)
        txtNoActivas = findViewById(R.id.txtNoActivas)
        
        rvEstado.layoutManager = LinearLayoutManager(this)
        
        findViewById<View>(R.id.btnVolverEstado).setOnClickListener { finish() }

        cargarTareasActivas()
    }

    private fun cargarTareasActivas() {
        lifecycleScope.launch {
            val enEnvio = withContext(Dispatchers.IO) {
                // CORRECCIÓN: Solo mostramos las tareas que están en estado SUBIENDO
                db.tareaDao().getTareasPorEstado(EstadoTarea.SUBIENDO)
            }

            if (enEnvio.isEmpty()) {
                txtNoActivas.visibility = View.VISIBLE
                rvEstado.visibility = View.GONE
            } else {
                txtNoActivas.visibility = View.GONE
                rvEstado.visibility = View.VISIBLE
                rvEstado.adapter = EstadoTareaAdapter(enEnvio)
            }
        }
    }

    inner class EstadoTareaAdapter(private val lista: List<Tarea>) : 
        RecyclerView.Adapter<EstadoTareaAdapter.TareaViewHolder>() {
        
        inner class TareaViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val txtSitio: TextView = v.findViewById(R.id.txtSitioNombre)
            val txtEstado: TextView = v.findViewById(R.id.txtTareaEstado)
            val progressBar: ProgressBar = v.findViewById(R.id.pbTareaProgreso)
            val btnControl: MaterialButton = v.findViewById(R.id.btnPausarReanudar)
            val btnEliminar: MaterialButton = v.findViewById(R.id.btnEliminarTarea)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TareaViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_estado_tarea, parent, false)
            return TareaViewHolder(v)
        }

        override fun onBindViewHolder(holder: TareaViewHolder, position: Int) {
            val tarea = lista[position]
            
            lifecycleScope.launch {
                val sitio = withContext(Dispatchers.IO) { db.sitioDao().getById(tarea.idSitio) }
                holder.txtSitio.text = sitio?.nombre ?: "Sitio ID: ${tarea.idSitio}"
            }

            val workName = "sync_tarea_${tarea.idTarea}"
            WorkManager.getInstance(this@EstadoProyectoActivity)
                .getWorkInfosForUniqueWorkLiveData(workName)
                .observe(this@EstadoProyectoActivity) { infos ->
                    val info = infos?.firstOrNull()
                    
                    if (info != null) {
                        when (info.state) {
                            WorkInfo.State.RUNNING -> {
                                val progress = info.progress.getInt("PROGRESS", 0)
                                holder.txtEstado.text = "Estado: Enviando... ($progress%)"
                                holder.progressBar.isIndeterminate = false
                                holder.progressBar.progress = progress
                                holder.btnControl.text = "PAUSAR"
                                holder.btnControl.setOnClickListener {
                                    WorkManager.getInstance(this@EstadoProyectoActivity).cancelUniqueWork(workName)
                                }
                            }
                            WorkInfo.State.ENQUEUED -> {
                                holder.txtEstado.text = "Estado: En cola / Esperando red"
                                holder.progressBar.isIndeterminate = true
                                holder.btnControl.text = "PAUSAR"
                                holder.btnControl.setOnClickListener {
                                    WorkManager.getInstance(this@EstadoProyectoActivity).cancelUniqueWork(workName)
                                }
                            }
                            WorkInfo.State.SUCCEEDED -> {
                                cargarTareasActivas()
                            }
                            else -> {
                                holder.txtEstado.text = "Estado: Pausado"
                                holder.progressBar.isIndeterminate = false
                                holder.progressBar.progress = 0
                                holder.btnControl.text = "REANUDAR"
                                holder.btnControl.setOnClickListener {
                                    reanudarSubida(tarea)
                                }
                            }
                        }
                    }
                }

            holder.btnEliminar.setOnClickListener {
                confirmarEliminacion(tarea)
            }
        }

        override fun getItemCount() = lista.size
    }

    private fun confirmarEliminacion(tarea: Tarea) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar Envío")
            .setMessage("¿Estás seguro de cancelar y eliminar este envío? Se borrarán todos los datos locales.")
            .setPositiveButton("ELIMINAR") { _, _ -> eliminarTareaTotalmente(tarea) }
            .setNegativeButton("CANCELAR", null)
            .show()
    }

    private fun eliminarTareaTotalmente(tarea: Tarea) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                WorkManager.getInstance(this@EstadoProyectoActivity).cancelUniqueWork("sync_tarea_${tarea.idTarea}")
                val respuestas = db.respuestaDao().getByTarea(tarea.idTarea)
                respuestas.forEach { r ->
                    db.imagenDao().getByRespuesta(r.idRespuesta).forEach { img ->
                        val file = File(img.rutaArchivo)
                        if (file.exists()) file.delete()
                    }
                    db.valorRespuestaDao().deleteByRespuesta(r.idRespuesta)
                    db.imagenDao().deleteByRespuesta(r.idRespuesta)
                }
                db.respuestaDao().deleteByTarea(tarea.idTarea)
                db.tareaDao().delete(tarea)
            }
            Toast.makeText(this@EstadoProyectoActivity, "Envío eliminado", Toast.LENGTH_SHORT).show()
            cargarTareasActivas()
        }
    }

    private fun reanudarSubida(tarea: Tarea) {
        val data = Data.Builder()
            .putLong("ID_TAREA", tarea.idTarea)
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

        WorkManager.getInstance(this).enqueueUniqueWork(
            "sync_tarea_${tarea.idTarea}",
            ExistingWorkPolicy.REPLACE,
            syncWorkRequest
        )
    }
}
