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
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.room.withTransaction
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.pruebaderoom.data.AppDatabase
import com.example.pruebaderoom.data.RetrofitClient
import com.example.pruebaderoom.data.SessionManager
import com.example.pruebaderoom.data.entity.*
import com.google.android.material.button.MaterialButton
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var sessionManager: SessionManager
    private lateinit var txtInfo: TextView
    private lateinit var autoCompleteSitios: AutoCompleteTextView
    private lateinit var btnVerMapa: Button
    private lateinit var layoutPendientes: LinearLayout
    private lateinit var containerPendientes: LinearLayout
    private lateinit var cardSyncStatus: View
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var btnSyncMain: ImageButton
    
    private lateinit var txtSyncStatus: TextView
    private lateinit var txtSyncDetail: TextView
    private lateinit var progressBarHorizontal: ProgressBar
    
    private var listaSitios: List<Sitio> = emptyList()
    private var sitioSeleccionado: Sitio? = null
    private var isUploadingNow: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        
        sessionManager = SessionManager(this)
        RetrofitClient.init(this)

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.drawerLayout)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        db = AppDatabase.getDatabase(this)
        
        drawerLayout = findViewById(R.id.drawerLayout)
        navView = findViewById(R.id.navigationView)
        val btnMenuToggle = findViewById<ImageButton>(R.id.btnMenuToggle)
        btnSyncMain = findViewById(R.id.btnSyncMain)
        
        txtInfo = findViewById(R.id.txtInfo)
        autoCompleteSitios = findViewById(R.id.autoCompleteCiudades)
        btnVerMapa = findViewById(R.id.btnVerMapa)
        layoutPendientes = findViewById(R.id.layoutPendientes)
        containerPendientes = findViewById(R.id.containerBotonesPendientes)
        cardSyncStatus = findViewById(R.id.cardSyncStatus)

        txtSyncStatus = findViewById(R.id.txtSyncStatus)
        txtSyncDetail = findViewById(R.id.txtSyncDetail)
        progressBarHorizontal = findViewById(R.id.progressBarHorizontal)
        
        val btnpasar = findViewById<Button>(R.id.btnpasar)

        btnMenuToggle.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_inicio -> drawerLayout.closeDrawers()
                R.id.nav_historial -> startActivity(Intent(this, HistorialActivity::class.java))
                R.id.nav_estado -> startActivity(Intent(this, EstadoProyectoActivity::class.java))
                R.id.nav_logout -> confirmarCerrarSesion()
            }
            drawerLayout.closeDrawers()
            true
        }

        observarDatos()
        observarSincronizacionGlobal()
        
        if (isWifiAvailable()) {
            sincronizarTodo(showToast = false)
        }

        btnSyncMain.setOnClickListener {
            if (isUploadingNow) {
                Toast.makeText(this, "Espera a que termine el envío para actualizar", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (isNetworkAvailable()) {
                Toast.makeText(this, "Actualizando Información...", Toast.LENGTH_SHORT).show()
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
                val intent = Intent(this, SeleccionFormularioActivity::class.java)
                intent.putExtra("ID_SITIO", sitio.idSitio)
                startActivity(intent)
            } ?: Toast.makeText(this, "Selecciona un sitio primero", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmarCerrarSesion() {
        AlertDialog.Builder(this)
            .setTitle("Cerrar Sesión")
            .setMessage("¿Estás seguro que deseas cerrar sesión?")
            .setPositiveButton("SÍ") { _, _ ->
                sessionManager.clearSession()
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .setNegativeButton("NO", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        cargarTareasPendientes() 
        navView.setCheckedItem(R.id.nav_inicio)
    }

    private fun cargarTareasPendientes() {
        lifecycleScope.launch {
            val tareasEnProceso = withContext(Dispatchers.IO) {
                db.tareaDao().getTareasPorEstado(EstadoTarea.EN_PROCESO)
            }

            val pendientesReales = withContext(Dispatchers.IO) {
                tareasEnProceso.filter { tarea ->
                    db.respuestaDao().getByTarea(tarea.idTarea).any { respuesta ->
                        db.imagenDao().getByRespuesta(respuesta.idRespuesta).isNotEmpty()
                    }
                }
            }

            containerPendientes.removeAllViews()
            if (pendientesReales.isEmpty()) {
                layoutPendientes.visibility = View.GONE
            } else {
                layoutPendientes.visibility = View.VISIBLE
                pendientesReales.forEach { tarea ->
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

                        val btnRetomar = MaterialButton(this@MainActivity).apply {
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
                    db.valorRespuestaDao().deleteByRespuesta(r.idRespuesta)
                    db.imagenDao().deleteByRespuesta(r.idRespuesta)
                }
                db.respuestaDao().deleteByTarea(tarea.idTarea)
                db.tareaDao().delete(tarea)
            }
            Toast.makeText(this@MainActivity, "Inspección eliminada", Toast.LENGTH_SHORT).show()
            cargarTareasPendientes()
        }
    }

    private fun actualizarInformacionSitio(sitio: Sitio) {
        lifecycleScope.launch {
            val tareaActiva = withContext(Dispatchers.IO) { db.tareaDao().getTareaActivaPorSitio(sitio.idSitio) }
            
            var esPendienteConFotos = false
            if (tareaActiva != null) {
                esPendienteConFotos = withContext(Dispatchers.IO) {
                    db.respuestaDao().getByTarea(tareaActiva.idTarea).any { r ->
                        db.imagenDao().getByRespuesta(r.idRespuesta).isNotEmpty()
                    }
                }
            }
        
            val status = if (esPendienteConFotos) "PENDIENTE" else "LIBRE"

            txtInfo.text = """
                ID SITIO: ${sitio.idSitio}
                NOMBRE: ${sitio.nombre}
                TEAM: ${sitio.teem}
                VISITAS: ${sitio.visit}
                MORFOLOGÍA: ${sitio.siteMorfology}
                NUEVA MORFOLOGÍA: ${sitio.newMorfology}
            """.trimIndent()
        }
    }

    private fun observarSincronizacionGlobal() {
        WorkManager.getInstance(this).getWorkInfosByTagLiveData("ReporteWorker").observe(this) { infos ->
            if (infos.isNullOrEmpty()) {
                cardSyncStatus.visibility = View.GONE
                isUploadingNow = false
                btnSyncMain.alpha = 1.0f
                return@observe
            }

            val infoActiva = infos.firstOrNull { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
            val infoExitosa = infos.firstOrNull { it.state == WorkInfo.State.SUCCEEDED }

            if (infoActiva != null) {
                isUploadingNow = true
                btnSyncMain.alpha = 0.5f
                cardSyncStatus.visibility = View.VISIBLE
                val progress = infoActiva.progress.getInt("PROGRESS", 0)
                val current = infoActiva.progress.getInt("CURRENT_IMG", 0)
                val total = infoActiva.progress.getInt("TOTAL_IMG", 0)
                val status = infoActiva.progress.getString("STATUS") ?: "WAITING"
                val sitio = infoActiva.progress.getString("SITIO_NOMBRE") ?: "Sincronizando"

                when (status) {
                    "UPLOADING" -> {
                        txtSyncStatus.text = "Estado: Subiendo $sitio..."
                        progressBarHorizontal.isIndeterminate = false
                        progressBarHorizontal.progress = progress
                        txtSyncDetail.text = "$progress% - Imagen $current de $total"
                    }
                    "WAITING" -> {
                        txtSyncStatus.text = "Estado: Esperando conexión..."
                        progressBarHorizontal.isIndeterminate = true
                        txtSyncDetail.text = "La subida se reanudará al tener internet"
                    }
                    "ERROR" -> {
                        txtSyncStatus.text = "Estado: Error al subir"
                        txtSyncDetail.text = "Se reintentará automáticamente"
                    }
                }
            } else if (infoExitosa != null) {
                isUploadingNow = false
                btnSyncMain.alpha = 1.0f
                txtSyncStatus.text = "Estado: ¡Subido correctamente!"
                progressBarHorizontal.isIndeterminate = false
                progressBarHorizontal.progress = 100
                txtSyncDetail.text = "Todos los datos están en el servidor"
                
                cardSyncStatus.postDelayed({ 
                    cardSyncStatus.visibility = View.GONE 
                    WorkManager.getInstance(this).pruneWork()
                }, 3000)
            } else {
                isUploadingNow = false
                btnSyncMain.alpha = 1.0f
                cardSyncStatus.visibility = View.GONE
            }
            
            cargarTareasPendientes()
        }
    }

    private fun irAInspeccion(idTarea: Long) {
        val intent = Intent(this, InspeccionActivity::class.java).apply {
            putExtra("ID_TAREA", idTarea)
        }
        startActivity(intent)
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
        if (isUploadingNow) return

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val resSitios = RetrofitClient.instance.getSitios()
                    val resFormsList = RetrofitClient.instance.getListaFormularios()

                    db.withTransaction {
                        if (resSitios.data.isNotEmpty()) {
                            // En lugar de borrar todo, podrías usar un UPSERT si el DAO lo permite.
                            // Por ahora, mantenemos la lógica pero protegida por isUploadingNow
                            db.sitioDao().deleteAll()
                            db.sitioDao().insertAll(resSitios.data)
                        }
                        
                        if (resFormsList.data.isNotEmpty()) {
                            db.formularioDao().deleteAll()
                            db.seccionDao().deleteAll()
                            db.preguntaDao().deleteAll()
                            db.campoDao().deleteAll()

                            resFormsList.data.forEach { formShort ->
                                val resFull = RetrofitClient.instance.getFormularioCompleto(formShort.id)
                                if (resFull.success) {
                                    val apiForm = resFull.data
                                    db.formularioDao().insert(Formulario(apiForm.id, apiForm.nombre, apiForm.descripcion))
                                    
                                    apiForm.secciones.forEach { apiSec: com.example.pruebaderoom.data.SeccionApiData ->
                                        db.seccionDao().insert(Seccion(apiSec.id, apiForm.id, apiSec.nombre, apiSec.zona))
                                        
                                        apiSec.preguntas.forEach { apiPreg: com.example.pruebaderoom.data.PreguntaApiData ->
                                            db.preguntaDao().insert(Pregunta(
                                                apiPreg.id, 
                                                apiSec.id, 
                                                apiPreg.descripcion, 
                                                apiPreg.minImagenes, 
                                                apiPreg.maxImagenes
                                            ))
                                            
                                            val camposDb = apiPreg.campos.map { apiCampo: com.example.pruebaderoom.data.CampoApiData ->
                                                Campo(apiCampo.id, apiPreg.id, apiCampo.tipo, apiCampo.label, apiCampo.orden)
                                            }
                                            db.campoDao().insertAll(camposDb)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (showToast) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Actualización exitosa", Toast.LENGTH_SHORT).show()
                    }
                }
                cargarTareasPendientes()
            } catch (e: Exception) { 
                Log.e("SYNC", e.message ?: "Error desconocido")
            }
        }
    }
}
