package com.example.pruebaderoom.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.pruebaderoom.data.converters.Converters
import com.example.pruebaderoom.data.dao.*
import com.example.pruebaderoom.data.entity.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        Sitio::class,
        Formulario::class,
        Tarea::class,
        Seccion::class,
        Pregunta::class,
        Respuesta::class,
        Imagen::class
    ],
    version = 1
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sitioDao(): SitioDao
    abstract fun formularioDao(): FormularioDao
    abstract fun tareaDao(): TareaDao
    abstract fun seccionDao(): SeccionDao
    abstract fun preguntaDao(): PreguntaDao
    abstract fun respuestaDao(): RespuestaDao
    abstract fun imagenDao(): ImagenDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                )
                .addCallback(DatabaseCallback())
                .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        prepopulateDatabase(database.sitioDao())
                    }
                }
            }

            suspend fun prepopulateDatabase(sitioDao: SitioDao) {
                val sitios = listOf(
                    Sitio("SPS06CL5436", "ACEYDESA_COLON", "TOCOA", "NORMAL", "NORMAL", 1),
                    Sitio("GLS", "AEROPUERTO_GOLOSON", "CBA1", "UVIP", "UVIP", 1),
                    Sitio("SPS04IS5804", "AEROPUERTO_ROATAN", "ROATAN", "UVIP", "NORMAL", 1),
                    Sitio("SPS06CL5435", "AGROPALMA_COLON_MICRO", "TOCOA", "NORMAL", "NORMAL", 1),
                    Sitio("SPS06CL5437", "AGUA_AMARILLA", "COROCITO", "HUB", "NORMAL", 1),
                    Sitio("SPS06GD5901", "AHUAS_GD", "GAD", "HUB", "UVIP", 1),
                    Sitio("SPS06AT5307", "ALDEA_EL_NARANJAL_ATLANTIDA", "CBA1", "NORMAL", "REGULAR", 1),
                    Sitio("SPS06CL5472", "ALDEA_EL_REMOLINO", "TOCOA", "NORMAL", "NORMAL", 1),
                    Sitio("SPS06CB5015", "ALTIPLANO", "CBA2", "NORMAL", "NORMAL", 1),
                    Sitio("SPS06YO5732", "ARENAL", "SABA", "NORMAL", "NORMAL", 1)
                )
                sitios.forEach { sitioDao.insert(it) }
            }
        }
    }
}
