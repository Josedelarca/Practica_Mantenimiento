package com.example.pruebaderoom.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.pruebaderoom.data.entity.Seccion

/**
 * Esta interfaz maneja las "Secciones" del formulario (ej: "Fachada", "Electricidad").
 * Nos permite organizar las preguntas por grupos.
 */
@Dao
interface SeccionDao {
    
    // Trae todas las secciones guardadas
    @Query("SELECT * FROM Seccion")
    suspend fun getAll(): List<Seccion>

    // Guarda una sección. Si ya existe, actualiza su nombre u orden.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(seccion: Seccion)

    // Busca todas las secciones que pertenecen a un formulario específico
    @Query("SELECT * FROM Seccion WHERE id_formulario = :idFormulario")
    suspend fun getByFormulario(idFormulario: Long): List<Seccion>

    // Busca una sección puntual por su ID
    @Query("SELECT * FROM Seccion WHERE id_seccion = :id LIMIT 1")
    suspend fun getById(id: Long): Seccion?
}
