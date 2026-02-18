package com.example.pruebaderoom

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.pruebaderoom.data.AppDatabase
import com.example.pruebaderoom.data.entity.EstadoTarea
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SeleccionFormularioActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private var idTareaRecibido: Long = -1
    
    private lateinit var cardSuelo: MaterialCardView
    private lateinit var cardTorre: MaterialCardView
    private lateinit var txtStatusSuelo: TextView
    private lateinit var txtStatusTorre: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_seleccion_formulario)

        idTareaRecibido = intent.getLongExtra("ID_TAREA", -1)
        db = AppDatabase.getDatabase(this)

        cardSuelo = findViewById(R.id.cardSuelo)
        cardTorre = findViewById(R.id.cardTorre)
        txtStatusSuelo = findViewById(R.id.txtStatusSuelo)
        txtStatusTorre = findViewById(R.id.txtStatusTorre)

        findViewById<View>(R.id.btnVolverAtras).setOnClickListener { finish() }

        cardSuelo.setOnClickListener { irAInspeccion("suelo") }
        cardTorre.setOnClickListener { irAInspeccion("altura") }

        observarEstadoSecciones()
        observarSincronizacion()
    }

    private fun observarEstadoSecciones() {
        lifecycleScope.launch {
            val tarea = withContext(Dispatchers.IO) { db.tareaDao().getById(idTareaRecibido) }
            if (tarea == null) return@launch

            val secciones = withContext(Dispatchers.IO) { db.seccionDao().getByFormulario(tarea.idFormulario) }
            
            val sueloCompletado = secciones.filter { it.zona == "suelo" }.all { it.isCompletada } && secciones.any { it.zona == "suelo" }
            val torreCompletada = secciones.filter { it.zona == "altura" }.all { it.isCompletada } && secciones.any { it.zona == "altura" }

            if (sueloCompletado) {
                bloquearCard(cardSuelo, txtStatusSuelo, "COMPLETADA", "#43A047")
            }
            if (torreCompletada) {
                bloquearCard(cardTorre, txtStatusTorre, "COMPLETADA", "#43A047")
            }
        }
    }

    private fun observarSincronizacion() {
        WorkManager.getInstance(this).getWorkInfosByTagLiveData("ReporteWorker").observe(this) { infos ->
            val info = infos?.find { it.progress.getLong("TAREA_ID", -1L) == idTareaRecibido && it.state == WorkInfo.State.RUNNING }
            
            if (info != null) {
                val zona = info.progress.getString("ZONA_TRABAJADA") ?: ""
                val message = info.progress.getString("MESSAGE") ?: "Sincronizando..."
                
                if (zona == "suelo") {
                    bloquearCard(cardSuelo, txtStatusSuelo, message, "#FF9800")
                } else if (zona == "altura") {
                    bloquearCard(cardTorre, txtStatusTorre, message, "#FF9800")
                }
            }
        }
    }

    private fun bloquearCard(card: MaterialCardView, textView: TextView, mensaje: String, colorHex: String) {
        card.isEnabled = false
        card.alpha = 0.8f
        textView.text = mensaje
        textView.setTextColor(Color.parseColor(colorHex))
    }

    private fun irAInspeccion(zona: String) {
        val intent = Intent(this, InspeccionActivity::class.java).apply {
            putExtra("ID_TAREA", idTareaRecibido)
            putExtra("ZONA_ELEGIDA", zona)
        }
        startActivity(intent)
    }
}