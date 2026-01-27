package com.example.pruebaderoom.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.pruebaderoom.data.entity.Sitio

/**
 * Esta interfaz define las acciones que podemos hacer sobre la tabla de Sitios
 * en la base de datos local (como buscar, guardar o borrar sitios).
 */
@Dao
interface SitioDao {
    
    // Obtiene la lista completa de todos los sitios guardados en el celular
    @Query("SELECT * FROM Sitio")
    suspend fun getAll(): List<Sitio>

    // Guarda un sitio. Si ya existe uno con el mismo ID, lo actualiza con la nueva info
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sitio: Sitio)

    // Busca un sitio específico usando su identificador único
    @Query("SELECT * FROM Sitio WHERE id_sitio = :id")
    suspend fun getById(id: String): Sitio?

    // Borra absolutamente todos los sitios de la tabla (útil para limpiar antes de sincronizar)
    @Query("DELETE FROM Sitio")
    suspend fun deleteAll()
}
