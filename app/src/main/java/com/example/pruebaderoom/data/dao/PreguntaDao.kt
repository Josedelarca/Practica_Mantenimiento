package com.example.pruebaderoom.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.pruebaderoom.data.entity.Pregunta

/**
 * Esta interfaz define las operaciones para la tabla de "Preguntas".
 * Aquí controlamos qué se pregunta en cada sección del formulario.
 */
@Dao
interface PreguntaDao {
    
    // Obtiene todas las preguntas configuradas en el sistema
    @Query("SELECT * FROM Preguntas")
    suspend fun getAll(): List<Pregunta>

    // Guarda una nueva pregunta o actualiza una existente
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pregunta: Pregunta)

    // Trae únicamente las preguntas que pertenecen a una sección específica
    @Query("SELECT * FROM Preguntas WHERE id_seccion = :idSeccion")
    suspend fun getBySeccion(idSeccion: Long): List<Pregunta>

    // Busca el detalle de una pregunta usando su ID único
    @Query("SELECT * FROM Preguntas WHERE id_pregunta = :id")
    suspend fun getById(id: Long): Pregunta?
}
