package com.example.pruebaderoom.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.pruebaderoom.data.entity.Pregunta

@Dao
interface PreguntaDao {
    @Query("SELECT * FROM Preguntas")
    suspend fun getAll(): List<Pregunta>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pregunta: Pregunta)

    @Query("SELECT * FROM Preguntas WHERE id_seccion = :idSeccion")
    suspend fun getBySeccion(idSeccion: Long): List<Pregunta>

    @Query("SELECT * FROM Preguntas WHERE id_pregunta = :id")
    suspend fun getById(id: Long): Pregunta?
}
