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
    version = 2, // SUBIMOS LA VERSIÓN DE 1 A 2
    exportSchema = false
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
                    .fallbackToDestructiveMigration() // ESTO EVITA QUE LA APP SE CIERRE SI HAY CAMBIOS
                    .addCallback(DatabaseCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
            }
        }
    }
}
