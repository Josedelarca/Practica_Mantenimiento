package com.example.pruebaderoom

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.room.withTransaction
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.pruebaderoom.data.AppDatabase
import com.example.pruebaderoom.data.RetrofitClient
import com.example.pruebaderoom.data.SessionManager
import com.example.pruebaderoom.data.TareaAsignadaApi
import com.example.pruebaderoom.data.entity.*
import com.google.android.material.navigation.NavigationView
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var sessionManager: SessionManager
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var rvTareas: RecyclerView
    private lateinit var pbLoading: ProgressBar
    private lateinit var layoutNoTareas: View
    private lateinit var btnSyncMain: ImageButton
    private var adapter: TareaAdapter? = null

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
        rvTareas = findViewById(R.id.rvTareasAsignadas)
        pbLoading = findViewById(R.id.pbLoadingTasks)
        layoutNoTareas = findViewById(R.id.txtNoTareas)
        btnSyncMain = findViewById(R.id.btnSyncMain)
        
        val headerView = navView.getHeaderView(0)
        val txtHeaderName = headerView.findViewById<TextView>(R.id.txtHeaderUserName)
        txtHeaderName.text = sessionManager.getUserName()

        rvTareas.layoutManager = LinearLayoutManager(this)

        val btnMenuToggle = findViewById<ImageButton>(R.id.btnMenuToggle)
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

        btnSyncMain.setOnClickListener {
            if (isNetworkAvailable()) {
                descargarTareas()
            } else {
                Toast.makeText(this, "Sin conexión a internet", Toast.LENGTH_SHORT).show()
            }
        }

        cargarTareasLocales()
        if (isNetworkAvailable()) {
            descargarTareas()
        }

        observarSincronizacionGlobal()
    }

    private fun observarSincronizacionGlobal() {
        WorkManager.getInstance(this).getWorkInfosByTagLiveData("ReporteWorker").observe(this) { infos ->
            if (infos.isNullOrEmpty()) return@observe
            
            infos.forEach { info ->
                val tareaId = info.progress.getLong("TAREA_ID", -1L)
                if (tareaId != -1L) {
                    val progress = info.progress.getInt("PROGRESS", 0)
                    val message = info.progress.getString("MESSAGE") ?: ""
                    val isRunning = info.state == WorkInfo.State.RUNNING
                    
                    adapter?.updateProgress(tareaId, progress, message, isRunning)
                }
            }
        }
    }

    private fun cargarTareasLocales() {
        lifecycleScope.launch {
            val tareas = withContext(Dispatchers.IO) {
                db.tareaDao().getTareasPorEstado(EstadoTarea.EN_PROCESO) +
                db.tareaDao().getTareasPorEstado(EstadoTarea.SUBIENDO)
            }
            mostrarTareas(tareas)
        }
    }

    private fun descargarTareas() {
        lifecycleScope.launch {
            pbLoading.visibility = View.VISIBLE
            try {
                Log.d("DEBUG_SYNC", "--- INICIANDO DESCARGA DE TAREAS ---")
                val response = withContext(Dispatchers.IO) { RetrofitClient.instance.getTareasPendientes() }
                
                val jsonString = Gson().toJson(response)
                Log.d("DEBUG_SYNC", "JSON RECIBIDO: $jsonString")

                if (response.success) {
                    Log.d("DEBUG_SYNC", "Tareas encontradas: ${response.data.size}")
                    guardarTareasEnBaseDeDatos(response.data)
                    cargarTareasLocales()
                } else {
                    Log.e("DEBUG_SYNC", "Respuesta API fallida")
                }
            } catch (e: Exception) {
                Log.e("DEBUG_SYNC", "ERROR CRÍTICO: ${e.message}")
                e.printStackTrace()
                Toast.makeText(this@MainActivity, "Error al actualizar tareas", Toast.LENGTH_SHORT).show()
            } finally {
                pbLoading.visibility = View.GONE
            }
        }
    }

    private suspend fun guardarTareasEnBaseDeDatos(listaApi: List<TareaAsignadaApi>) {
        withContext(Dispatchers.IO) {
            db.withTransaction {
                db.tareaDao().deleteByEstado(EstadoTarea.EN_PROCESO)
                
                listaApi.forEach { item ->
                    Log.d("DEBUG_SYNC", "Guardando Tarea -> ID: ${item.id}, UUID: ${item.uuid}, Sitio: ${item.sitio.nombre}")
                    
                    db.sitioDao().insert(item.sitio)
                    val apiForm = item.formulario
                    db.formularioDao().insert(Formulario(apiForm.id, apiForm.nombre, apiForm.descripcion))
                    
                    apiForm.secciones.forEach { apiSec ->
                        // Verificamos si esta sección ya viene marcada como completada por la API
                        val completada = item.secciones_completadas.contains(apiSec.id)
                        db.seccionDao().insert(Seccion(apiSec.id, apiForm.id, apiSec.nombre, apiSec.zona, completada))

                        apiSec.preguntas.forEach { apiPreg ->
                            db.preguntaDao().insert(Pregunta(
                                apiPreg.id, 
                                apiSec.id, 
                                apiPreg.descripcion, 
                                apiPreg.minImagenes, 
                                apiPreg.maxImagenes
                            ))
                            val camposDb = apiPreg.campos.map { apiCampo ->
                                Campo(apiCampo.id, apiPreg.id, apiCampo.tipo, apiCampo.label, apiCampo.orden)
                            }
                            db.campoDao().insertAll(camposDb)
                        }
                    }

                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val fecha = try { sdf.parse(item.fecha) } catch (e: Exception) { Date() }
                    
                    val tipo = when(item.tipo_mantenimiento.lowercase()) {
                        "preventivo" -> TipoMantenimiento.PREVENTIVO
                        "correctivo" -> TipoMantenimiento.CORRECTIVO
                        else -> TipoMantenimiento.PREVENTIVO
                    }

                    val tareaDb = Tarea(
                        idTarea = item.id,
                        idSitio = item.sitio.idSitio,
                        idFormulario = apiForm.id,
                        tipoMantenimiento = tipo,
                        fecha = fecha ?: Date(),
                        observacionesGenerales = "",
                        estado = EstadoTarea.EN_PROCESO,
                        uuid = item.uuid
                    )
                    db.tareaDao().insert(tareaDb)
                }
            }
        }
    }

    private fun mostrarTareas(tareas: List<Tarea>) {
        if (tareas.isEmpty()) {
            layoutNoTareas.visibility = View.VISIBLE
            rvTareas.visibility = View.GONE
        } else {
            layoutNoTareas.visibility = View.GONE
            rvTareas.visibility = View.VISIBLE
            lifecycleScope.launch {
                val items = withContext(Dispatchers.IO) {
                    tareas.map { t ->
                        val sitio = db.sitioDao().getById(t.idSitio)
                        val form = db.formularioDao().getById(t.idFormulario)
                        TareaUIItem(t, sitio, form)
                    }.toMutableList()
                }
                adapter = TareaAdapter(items) { item ->
                    val intent = Intent(this@MainActivity, SeleccionFormularioActivity::class.java).apply {
                        putExtra("ID_TAREA", item.tarea.idTarea)
                    }
                    startActivity(intent)
                }
                rvTareas.adapter = adapter
            }
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

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        return caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    data class TareaUIItem(
        val tarea: Tarea, 
        val sitio: Sitio?, 
        val formulario: Formulario?,
        var syncProgress: Int = 0,
        var syncMessage: String = "",
        var isSyncing: Boolean = false
    )

    class TareaAdapter(
        private val lista: MutableList<TareaUIItem>,
        private val onClick: (TareaUIItem) -> Unit
    ) : RecyclerView.Adapter<TareaAdapter.VH>() {
        
        fun updateProgress(tareaId: Long, progress: Int, message: String, isSyncing: Boolean) {
            val index = lista.indexOfFirst { it.tarea.idTarea == tareaId }
            if (index != -1) {
                lista[index].syncProgress = progress
                lista[index].syncMessage = message
                lista[index].isSyncing = isSyncing
                notifyItemChanged(index)
            }
        }

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val txtSitio: TextView = v.findViewById(R.id.txtSitioNombre)
            val txtForm: TextView = v.findViewById(R.id.txtFormularioNombre)
            val txtFecha: TextView = v.findViewById(R.id.txtFecha)
            val txtTipo: TextView = v.findViewById(R.id.txtTipoMaint)
            val txtBadge: TextView = v.findViewById(R.id.txtBadgeEstado)
            val viewIndicator: View = v.findViewById(R.id.viewStatusIndicator)
            
            val layoutProgress: View = v.findViewById(R.id.layoutSyncProgress)
            val pbSync: ProgressBar = v.findViewById(R.id.pbSyncTask)
            val txtSyncDetail: TextView = v.findViewById(R.id.txtSyncDetailTask)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            LayoutInflater.from(parent.context).inflate(R.layout.item_tarea_asignada, parent, false)
        )

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = lista[position]
            holder.txtSitio.text = item.sitio?.nombre ?: "Sitio ID: ${item.tarea.idSitio}"
            holder.txtForm.text = item.formulario?.nombre ?: "Formulario"
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            holder.txtFecha.text = "Fecha: ${sdf.format(item.tarea.fecha)}"
            holder.txtTipo.text = item.tarea.tipoMantenimiento.name

            // Lógica de Estado
            if (item.isSyncing) {
                holder.txtBadge.text = "ENVIANDO"
                holder.txtBadge.setBackgroundColor(Color.parseColor("#FF9800"))
                holder.viewIndicator.setBackgroundColor(Color.parseColor("#FF9800"))
                holder.layoutProgress.visibility = View.VISIBLE
                holder.pbSync.progress = item.syncProgress
                holder.txtSyncDetail.text = item.syncMessage
            } else {
                holder.layoutProgress.visibility = View.GONE
                when (item.tarea.estado) {
                    EstadoTarea.EN_PROCESO -> {
                        holder.txtBadge.text = "PENDIENTE"
                        holder.txtBadge.setBackgroundColor(Color.parseColor("#757575"))
                        holder.viewIndicator.setBackgroundColor(Color.parseColor("#1E2A44"))
                    }
                    EstadoTarea.SUBIENDO -> {
                        holder.txtBadge.text = "EN COLA"
                        holder.txtBadge.setBackgroundColor(Color.parseColor("#FF9800"))
                        holder.viewIndicator.setBackgroundColor(Color.parseColor("#FF9800"))
                    }
                    EstadoTarea.FINALIZADA -> {
                        holder.txtBadge.text = "LISTO"
                        holder.txtBadge.setBackgroundColor(Color.parseColor("#43A047"))
                        holder.viewIndicator.setBackgroundColor(Color.parseColor("#43A047"))
                    }
                }
            }

            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount() = lista.size
    }
}