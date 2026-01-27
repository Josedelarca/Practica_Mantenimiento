package com.example.pruebaderoom.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.pruebaderoom.data.converters.Converters
import com.example.pruebaderoom.data.dao.*
import com.example.pruebaderoom.data.entity.*

/**
 * Esta es la "bodega" central de datos de nuestra aplicación.
 * Aquí definimos qué tablas tenemos (Sitios, Tareas, Fotos, etc.) y cómo acceder a ellas.
 */
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
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    
    // Estos son los accesos directos (DAOs) para cada tabla
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

        /**
         * Esta función nos da la base de datos. Usamos el patrón "Singleton" para
         * asegurarnos de que toda la app use la misma conexión y no se armen líos.
         */
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                )
                // Si cambiamos algo en las tablas, esta línea ayuda a que la app no explote,
                // aunque borra los datos viejos para adaptarse a los nuevos.
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
