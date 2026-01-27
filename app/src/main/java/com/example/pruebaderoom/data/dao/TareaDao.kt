package com.example.pruebaderoom.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.pruebaderoom.data.entity.Tarea

/**
 * Esta interfaz nos permite manejar las "Tareas" o inspecciones en la base de datos.
 * Cada vez que inicias un nuevo mantenimiento, se guarda aquí.
 */
@Dao
interface TareaDao {
    
    // Trae todas las inspecciones que se han hecho o están en curso
    @Query("SELECT * FROM Tarea")
    suspend fun getAll(): List<Tarea>

    // Guarda una nueva tarea o actualiza una existente (como cuando cambia de estado)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tarea: Tarea)

    // Busca una inspección específica por su número de ID
    @Query("SELECT * FROM Tarea WHERE id_tarea = :id")
    suspend fun getById(id: Long): Tarea?

    // Busca todas las tareas que se han hecho en un sitio en particular
    @Query("SELECT * FROM Tarea WHERE id_sitio = :idSitio")
    suspend fun getBySitio(idSitio: String): List<Tarea>
}
