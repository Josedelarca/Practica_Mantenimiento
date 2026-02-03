package com.example.pruebaderoom

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.room.withTransaction
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.pruebaderoom.data.AppDatabase
import com.example.pruebaderoom.data.RetrofitClient
import com.example.pruebaderoom.data.entity.*
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var txtInfo: TextView
    private lateinit var autoCompleteSitios: AutoCompleteTextView
    private lateinit var btnVerMapa: Button
    private lateinit var layoutPendientes: LinearLayout
    private lateinit var containerPendientes: LinearLayout
    private lateinit var cardSyncStatus: View
    
    private var listaSitios: List<Sitio> = emptyList()
    private var sitioSeleccionado: Sitio? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        db = AppDatabase.getDatabase(this)
        txtInfo = findViewById(R.id.txtInfo)
        autoCompleteSitios = findViewById(R.id.autoCompleteCiudades)
        btnVerMapa = findViewById(R.id.btnVerMapa)
        layoutPendientes = findViewById(R.id.layoutPendientes)
        containerPendientes = findViewById(R.id.containerBotonesPendientes)
        cardSyncStatus = findViewById(R.id.cardSyncStatus)
        
        val btnpasar = findViewById<Button>(R.id.btnpasar)
        val btnSync = findViewById<ImageButton>(R.id.btnSync)

        observarDatos()
        observarSincronizacionGlobal()
        
        if (isWifiAvailable()) {
            sincronizarTodo(showToast = false)
        }

        btnSync.setOnClickListener {
            if (isNetworkAvailable()) {
                Toast.makeText(this, "Actualizando Informacion", Toast.LENGTH_SHORT).show()
                sincronizarTodo(showToast = true)
            }
        }

        autoCompleteSitios.onItemClickListener = AdapterView.OnItemClickListener { parent, _, position, _ ->
            val nombreSeleccionado = parent.getItemAtPosition(position) as String
            sitioSeleccionado = listaSitios.find { it.nombre == nombreSeleccionado }
            sitioSeleccionado?.let {
                actualizarInformacionSitio(it)
                btnVerMapa.visibility = View.VISIBLE
            }
        }

        btnVerMapa.setOnClickListener {
            sitioSeleccionado?.let { sitio ->
                try {
                    val uri = Uri.parse("geo:${sitio.latitud},${sitio.longitud}?q=${sitio.latitud},${sitio.longitud}(${sitio.nombre})")
                    startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.apps.maps"))
                } catch (e: Exception) {
                    Toast.makeText(this, "Google Maps no instalado", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnpasar.setOnClickListener {
            sitioSeleccionado?.let { sitio ->
                lifecycleScope.launch {
                    val tareaExistente = withContext(Dispatchers.IO) {
                        db.tareaDao().getTareaActivaPorSitio(sitio.idSitio)
                    }
                    if (tareaExistente != null) {
                        irAInspeccion(tareaExistente.idTarea)
                    } else {
                        crearNuevaTarea(sitio)
                    }
                }
            } ?: Toast.makeText(this, "Selecciona un sitio", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        cargarTareasPendientes() 
    }

    private fun cargarTareasPendientes() {
        lifecycleScope.launch {
            val pendientes = withContext(Dispatchers.IO) {
                db.tareaDao().getTareasPorEstado(EstadoTarea.EN_PROCESO)
            }

            containerPendientes.removeAllViews()
            if (pendientes.isEmpty()) {
                layoutPendientes.visibility = View.GONE
            } else {
                layoutPendientes.visibility = View.VISIBLE
                pendientes.forEach { tarea ->
                    val sitio = withContext(Dispatchers.IO) { db.sitioDao().getById(tarea.idSitio) }
                    sitio?.let { s ->
                        val row = LinearLayout(this@MainActivity).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            setPadding(16, 8, 16, 8)
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                        }

                        val btnRetomar = MaterialButton(this@MainActivity, null, com.google.android.material.R.attr.materialButtonStyle).apply {
                            text = "RETOMAR: ${s.nombre}"
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                            setIconResource(android.R.drawable.ic_menu_edit)
                            setOnClickListener { irAInspeccion(tarea.idTarea) }
                        }

                        val btnEliminar = ImageButton(this@MainActivity).apply {
                            setImageResource(android.R.drawable.ic_menu_delete)
                            background = null
                            contentDescription = "Eliminar tarea"
                            setOnClickListener { confirmarEliminacion(tarea, s.nombre) }
                        }

                        row.addView(btnRetomar)
                        row.addView(btnEliminar)
                        containerPendientes.addView(row)
                    }
                }
            }
        }
    }

    private fun confirmarEliminacion(tarea: Tarea, nombreSitio: String) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar Inspección")
            .setMessage("¿Estás seguro de eliminar la inspección de $nombreSitio? Se borrarán todas las fotos tomadas.")
            .setPositiveButton("ELIMINAR") { _, _ -> eliminarTareaFisicamente(tarea) }
            .setNegativeButton("CANCELAR", null)
            .show()
    }

    private fun eliminarTareaFisicamente(tarea: Tarea) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val respuestas = db.respuestaDao().getByTarea(tarea.idTarea)
                respuestas.forEach { r ->
                    val fotos = db.imagenDao().getByRespuesta(r.idRespuesta)
                    fotos.forEach { img ->
                        val file = File(img.rutaArchivo)
                        if (file.exists()) file.delete()
                    }
                    db.imagenDao().deleteByRespuesta(r.idRespuesta)
                }
                db.respuestaDao().deleteByTarea(tarea.idTarea)
                db.tareaDao().delete(tarea)
            }
            Toast.makeText(this@MainActivity, "Inspección eliminada", Toast.LENGTH_SHORT).show()
            cargarTareasPendientes()
        }
    }

    private fun crearNuevaTarea(sitio: Sitio) {
        lifecycleScope.launch {
            val nuevaTareaId = System.currentTimeMillis()
            val nuevaTarea = Tarea(nuevaTareaId, sitio.idSitio, 1L, TipoMantenimiento.PREVENTIVO, Date(), "En curso", EstadoTarea.EN_PROCESO)
            withContext(Dispatchers.IO) { db.tareaDao().insert(nuevaTarea) }
            irAInspeccion(nuevaTareaId)
        }
    }

    private fun actualizarInformacionSitio(sitio: Sitio) {
        lifecycleScope.launch {
            val tareaActiva = withContext(Dispatchers.IO) { db.tareaDao().getTareaActivaPorSitio(sitio.idSitio) }
            val status = if (tareaActiva != null) "PENDIENTE" else "LIBRE"
            txtInfo.text = """
                ID SITIO: ${sitio.idSitio}
                NOMBRE: ${sitio.nombre}
                TEAM: ${sitio.teem}
                VISITAS: ${sitio.visit}
            """.trimIndent()
        }
    }

    private fun observarSincronizacionGlobal() {
        WorkManager.getInstance(this).getWorkInfosByTagLiveData("ReporteWorker").observe(this) { infos ->
            val subiendo = infos != null && infos.any { it.state == WorkInfo.State.RUNNING }
            cardSyncStatus.visibility = if (subiendo) View.VISIBLE else View.GONE
            if (subiendo) {
                cargarTareasPendientes()
            }
        }
    }

    private fun irAInspeccion(idTarea: Long) {
        startActivity(Intent(this, InspeccionActivity::class.java).putExtra("ID_TAREA", idTarea))
    }

    private fun isWifiAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        return caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        return caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    private fun observarDatos() {
        lifecycleScope.launch {
            db.sitioDao().getAllFlow().collectLatest { sitios ->
                listaSitios = sitios
                autoCompleteSitios.setAdapter(ArrayAdapter(this@MainActivity, android.R.layout.simple_dropdown_item_1line, sitios.map { it.nombre }))
            }
        }
    }

    private fun sincronizarTodo(showToast: Boolean) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val resSitios = RetrofitClient.instance.getSitios()
                    val resForm = RetrofitClient.instance.getFormularioCompleto(1L)
                    db.withTransaction {
                        if (resSitios.data.isNotEmpty()) {
                            db.sitioDao().deleteAll()
                            db.sitioDao().insertAll(resSitios.data)
                        }
                        if (resForm.success) {
                            val data = resForm.data
                            db.seccionDao().deleteByFormulario(data.id)
                            db.formularioDao().insert(Formulario(data.id, data.nombre, data.descripcion))
                            data.secciones.forEach { s ->
                                db.seccionDao().insert(Seccion(s.id, data.id, s.nombre))
                                s.preguntas.forEach { p -> db.preguntaDao().insert(Pregunta(p.id, s.id, p.descripcion, p.minImagenes, p.maxImagenes)) }
                            }
                        }
                    }
                }
                if (showToast) Toast.makeText(this@MainActivity, "Datos actualizados", Toast.LENGTH_SHORT).show()
                cargarTareasPendientes()
            } catch (e: Exception) { Log.e("SYNC", e.message ?: "") }
        }
    }
}
