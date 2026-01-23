package com.example.pruebaderoom.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.pruebaderoom.data.entity.Formulario

/**
 * Interfaz para el acceso a datos (DAO) de la tabla Formulario.
 * Define las operaciones permitidas sobre la base de datos local.
 */
@Dao
interface FormularioDao {
    
    /**
     * Obtiene todos los formularios guardados en la base de datos local.
     */
    @Query("SELECT * FROM Formulario")
    suspend fun getAll(): List<Formulario>

    /**
     * Inserta un nuevo formulario o lo reemplaza si ya existe uno con el mismo ID.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(formulario: Formulario)

    /**
     * Busca un formulario específico mediante su identificador único.
     */
    @Query("SELECT * FROM Formulario WHERE id_formulario = :id")
    suspend fun getById(id: Long): Formulario?

    /**
     * Elimina todos los registros de la tabla Formulario.
     * Útil antes de realizar una sincronización completa con la API.
     */
    @Query("DELETE FROM Formulario")
    suspend fun deleteAll()
}
