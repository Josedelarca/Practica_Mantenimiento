package com.example.pruebaderoom.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.pruebaderoom.data.converters.Converters
import com.example.pruebaderoom.data.dao.*
import com.example.pruebaderoom.data.entity.*

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
    version = 8, // Subimos a 7
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
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
